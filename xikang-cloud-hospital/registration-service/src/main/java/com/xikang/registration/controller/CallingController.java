package com.xikang.registration.controller;

import com.xikang.common.result.Result;
import com.xikang.registration.entity.Department;
import com.xikang.registration.entity.Employee;
import com.xikang.registration.entity.Register;
import com.xikang.registration.mapper.RegistrationMapper;
import com.xikang.registration.service.CallingService;
import com.xikang.registration.service.DepartmentService;
import com.xikang.registration.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 叫号系统 Controller。
 *
 * 两类接口：
 *   1. 公共查询接口（大屏用）：GET /api/registration/calling/...
 *   2. 内部动作接口（Feign 给 physician-service 调）：POST /api/registration/calling/internal/...
 *      - 设计文档 §5.4：physician-service 通过 Feign 调 registration-service 触发叫号
 *
 * 内部接口路径前缀 /internal，由 physician-service 走 lb 直连，不经 gateway；
 * 当前阶段为简化联调，也允许直接走 gateway 调用（鉴权按普通医生身份走）。
 *
 * 设计文档：task_requirements/设计文档/04_叫号系统设计文档.md §4
 */
@Slf4j
@RestController
@RequestMapping("/api/registration/calling")
@RequiredArgsConstructor
public class CallingController {

    private final CallingService callingService;
    private final RegistrationMapper registrationMapper;
    private final DepartmentService departmentService;
    private final EmployeeService employeeService;

    // ==================== 公共查询接口（大屏 / 医生工作站查）====================

    /**
     * 当前医生的"已叫未应答"号。
     */
    @GetMapping("/current")
    public Result<Map<String, Object>> current(@RequestParam(required = false) Long employeeId) {
        return Result.success(callingService.currentCalling(employeeId));
    }

    /**
     * 科室叫号板：返回该科室今天所有"已叫"的号 + 候诊队列。
     */
    @GetMapping("/board/{departmentId}")
    public Result<Map<String, Object>> board(@PathVariable Long departmentId) {
        LocalDate today = LocalDate.now();
        List<Register> calling = registrationMapper.selectCallingByDepartment(departmentId, today);
        List<Register> waiting = registrationMapper.selectWaitingByDepartment(departmentId, today);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("calling", toViewList(calling));
        result.put("waiting", toViewList(waiting));
        result.put("callingCount", calling.size());
        result.put("waitingCount", waiting.size());
        return Result.success(result);
    }

    /**
     * 全院叫号板：返回所有科室今天"已叫"的号 + 各科室的候诊人数。
     * 用于大厅全院大屏的卡片墙。
     *
     * 数据结构：
     *   departments: [{ departmentId, departmentName, calling: [...], waitingCount }]
     *   recent: 最近 N 条叫号记录（用于顶部滚动播报），按 called_time desc 取前 5
     */
    @GetMapping("/board/all")
    public Result<Map<String, Object>> boardAll() {
        LocalDate today = LocalDate.now();
        List<Department> allDepts = departmentService.getAllDepartments();

        List<Map<String, Object>> deptViews = new ArrayList<>(allDepts.size());
        List<Map<String, Object>> recentCalls = new ArrayList<>();

        for (Department dept : allDepts) {
            List<Register> calling = registrationMapper.selectCallingByDepartment(dept.getId(), today);
            List<Register> waiting = registrationMapper.selectWaitingByDepartment(dept.getId(), today);

            Map<String, Object> deptView = new LinkedHashMap<>();
            deptView.put("departmentId", dept.getId());
            deptView.put("departmentName", dept.getName());
            deptView.put("calling", toViewList(calling));
            deptView.put("callingCount", calling.size());
            deptView.put("waitingCount", waiting.size());
            deptViews.add(deptView);

            // 收集每个科室的叫号，后面统一排序取最近 N 条
            for (Register r : calling) {
                Map<String, Object> item = toViewList(List.of(r)).get(0);
                item.put("departmentName", dept.getName());
                recentCalls.add(item);
            }
        }

        // recent 按 called_time desc 排序，取前 5 条
        recentCalls.sort((a, b) -> {
            Object ta = a.get("calledTime");
            Object tb = b.get("calledTime");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.toString().compareTo(ta.toString());
        });
        if (recentCalls.size() > 5) {
            recentCalls = recentCalls.subList(0, 5);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("departments", deptViews);
        result.put("recent", recentCalls);
        return Result.success(result);
    }

    // ==================== 内部动作接口（Feign 给 physician-service 调用）====================

    /**
     * 叫下一个。physician-service 从 token 拿 employeeId 后透传过来。
     */
    @PostMapping("/internal/call-next")
    public Result<Map<String, Object>> callNext(@RequestBody Map<String, Object> body) {
        Long employeeId = asLong(body.get("employeeId"));
        try {
            return Result.success(callingService.callNext(employeeId));
        } catch (IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 叫指定号（重叫过号常用）。
     */
    @PostMapping("/internal/call/{registerId}")
    public Result<Map<String, Object>> callSpecific(@PathVariable Long registerId,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        Long operatorId = body == null ? null : asLong(body.get("employeeId"));
        try {
            return Result.success(callingService.callSpecific(registerId, operatorId));
        } catch (IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 患者应答（进诊室）。
     */
    @PostMapping("/internal/answer/{registerId}")
    public Result<Map<String, Object>> answer(@PathVariable Long registerId) {
        try {
            return Result.success(callingService.answer(registerId));
        } catch (IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 标记过号。
     */
    @PostMapping("/internal/pass/{registerId}")
    public Result<Map<String, Object>> pass(@PathVariable Long registerId) {
        try {
            return Result.success(callingService.pass(registerId));
        } catch (IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }

    // ==================== 辅助 ====================

    /** Register 视图对象（带科室名 / 医生名 / 号序） */
    private List<Map<String, Object>> toViewList(List<Register> list) {
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Register r : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("registerId", r.getId());
            m.put("patientName", r.getRealName());
            m.put("caseNumber", r.getCaseNumber());
            m.put("callStatus", r.getCallStatus());
            m.put("callRound", r.getCallRound());
            m.put("calledTime", r.getCalledTime());
            m.put("answeredTime", r.getAnsweredTime());
            m.put("checkInTime", r.getCheckInTime());
            m.put("departmentId", r.getDeptmentId());
            m.put("doctorId", r.getEmployeeId());

            if (r.getDeptmentId() != null) {
                Department dept = departmentService.getDepartment(r.getDeptmentId());
                if (dept != null) m.put("departmentName", dept.getName());
            }
            if (r.getEmployeeId() != null) {
                Employee doc = employeeService.getDoctor(r.getEmployeeId());
                if (doc != null) m.put("doctorName", doc.getRealname());
            }
            // 号序
            if (r.getId() != null) {
                int before = registrationMapper.countWaitingBefore(r.getId());
                m.put("queueNumber", before + 1);
            }
            result.add(m);
        }
        return result;
    }

    private Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
