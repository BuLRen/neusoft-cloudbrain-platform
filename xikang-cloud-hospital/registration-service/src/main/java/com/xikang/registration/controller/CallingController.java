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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public Result<Map<String, Object>> boardAll(
            @RequestParam(defaultValue = "false") boolean includeIdle) {
        LocalDate today = LocalDate.now();
        List<Department> allDepts = departmentService.getAllDepartments();

        List<Map<String, Object>> deptViews = new ArrayList<>(allDepts.size());
        List<Map<String, Object>> recentCalls = new ArrayList<>();

        for (Department dept : allDepts) {
            List<Register> calling = registrationMapper.selectCallingByDepartment(dept.getId(), today);
            List<Register> waiting = registrationMapper.selectWaitingByDepartment(dept.getId(), today);

            if (!includeIdle && calling.isEmpty() && waiting.isEmpty()) {
                continue;
            }

            Map<String, Object> deptView = new LinkedHashMap<>();
            deptView.put("departmentId", dept.getId());
            deptView.put("departmentName", dept.getName());
            deptView.put("calling", toViewList(calling));
            deptView.put("callingCount", calling.size());
            deptView.put("waitingCount", waiting.size());
            deptView.put("currentCalling", calling.isEmpty() ? null : toViewList(List.of(calling.get(0))).get(0));

            Register nextWaiting = null;
            for (Register w : waiting) {
                Integer status = w.getCallStatus();
                if (status != null && (status == 0 || status == 3)) {
                    nextWaiting = w;
                    break;
                }
            }
            if (nextWaiting != null && nextWaiting.getId() != null) {
                Map<String, Object> nw = new LinkedHashMap<>();
                int before = registrationMapper.countWaitingBefore(nextWaiting.getId());
                nw.put("queueNumber", before + 1);
                nw.put("patientName", nextWaiting.getRealName());
                deptView.put("nextWaiting", nw);
            } else {
                deptView.put("nextWaiting", null);
            }

            deptViews.add(deptView);

            for (Register r : calling) {
                Map<String, Object> item = toViewList(List.of(r)).get(0);
                item.put("departmentName", dept.getName());
                recentCalls.add(item);
            }
        }

        recentCalls.sort((a, b) -> compareCalledTimeDesc(a, b));
        if (recentCalls.size() > 5) {
            recentCalls = recentCalls.subList(0, 5);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("departments", deptViews);
        result.put("recent", recentCalls);
        return Result.success(result);
    }

    /**
     * 扁平活跃叫号板：全院 call_status=1，按医生附带候诊人数与下一位号序。
     * 用于候诊大屏表格区。
     */
    @GetMapping("/board/active")
    public Result<Map<String, Object>> boardActive(
            @RequestParam(required = false) String deptIds,
            @RequestParam(defaultValue = "50") int limit) {
        LocalDate today = LocalDate.now();
        Set<Long> deptIdSet = parseDeptIds(deptIds);

        List<Register> allCalling = registrationMapper.selectAllActiveCalling(today);
        List<Map<String, Object>> active = new ArrayList<>();
        List<Map<String, Object>> recentCalls = new ArrayList<>();
        int totalWaiting = 0;

        List<Department> scopeDepts = departmentService.getAllDepartments();
        for (Department dept : scopeDepts) {
            if (!deptIdSet.isEmpty() && !deptIdSet.contains(dept.getId())) {
                continue;
            }
            totalWaiting += registrationMapper.selectWaitingByDepartment(dept.getId(), today).size();
        }

        for (Register r : allCalling) {
            if (!deptIdSet.isEmpty() && (r.getDeptmentId() == null || !deptIdSet.contains(r.getDeptmentId()))) {
                continue;
            }
            Map<String, Object> item = enrichActiveItem(r, today);
            active.add(item);
            recentCalls.add(new LinkedHashMap<>(item));
            if (active.size() >= limit) {
                break;
            }
        }

        recentCalls.sort((a, b) -> compareCalledTimeDesc(a, b));
        if (recentCalls.size() > 5) {
            recentCalls = recentCalls.subList(0, 5);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalWaiting", totalWaiting);
        stats.put("activeCalling", active.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active", active);
        result.put("recent", recentCalls);
        result.put("stats", stats);
        return Result.success(result);
    }

    /**
     * 医生今日候诊队列（含号序、叫号状态、是否可调序）。
     */
    @GetMapping("/queue/doctor")
    public Result<List<Map<String, Object>>> doctorQueue(@RequestParam Long employeeId) {
        return Result.success(callingService.doctorWaitingQueue(employeeId));
    }

    /**
     * 调整医生候诊队列顺序。
     */
    @PutMapping("/queue/reorder")
    public Result<Void> reorderQueue(@RequestBody Map<String, Object> body) {
        Long employeeId = asLong(body.get("employeeId"));
        List<Long> registerIds = parseRegisterIds(body.get("registerIds"));
        try {
            callingService.reorderQueue(employeeId, registerIds);
            return Result.success();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.error(e.getMessage());
        }
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

    /** 医生候诊队列（internal，供 physician-service 代理） */
    @GetMapping("/internal/queue/doctor")
    public Result<List<Map<String, Object>>> internalDoctorQueue(@RequestParam Long employeeId) {
        return Result.success(callingService.doctorWaitingQueue(employeeId));
    }

    /** 调整候诊队列（internal） */
    @PutMapping("/internal/queue/reorder")
    public Result<Void> internalReorderQueue(@RequestBody Map<String, Object> body) {
        Long employeeId = asLong(body.get("employeeId"));
        List<Long> registerIds = parseRegisterIds(body.get("registerIds"));
        try {
            callingService.reorderQueue(employeeId, registerIds);
            return Result.success();
        } catch (IllegalArgumentException | IllegalStateException e) {
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
                if (doc != null) {
                    m.put("doctorName", doc.getRealname());
                    m.put("clinicRoom", doc.getClinicRoom());
                }
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

    private Map<String, Object> enrichActiveItem(Register r, LocalDate today) {
        Map<String, Object> m = toViewList(List.of(r)).get(0);
        if (r.getDeptmentId() != null) {
            Department dept = departmentService.getDepartment(r.getDeptmentId());
            if (dept != null) {
                m.put("departmentName", dept.getName());
            }
        }
        if (r.getEmployeeId() != null) {
            List<Register> waiting = registrationMapper.selectWaitingByDoctor(r.getEmployeeId(), today);
            int waitingCount = 0;
            Register next = null;
            for (Register w : waiting) {
                Integer status = w.getCallStatus();
                if (status != null && (status == 0 || status == 3)) {
                    waitingCount++;
                    if (next == null) {
                        next = w;
                    }
                }
            }
            m.put("waitingCount", waitingCount);
            if (next != null && next.getId() != null) {
                int before = registrationMapper.countWaitingBefore(next.getId());
                m.put("nextQueueNumber", before + 1);
            }
        }
        return m;
    }

    private int compareCalledTimeDesc(Map<String, Object> a, Map<String, Object> b) {
        Object ta = a.get("calledTime");
        Object tb = b.get("calledTime");
        if (ta == null && tb == null) return 0;
        if (ta == null) return 1;
        if (tb == null) return -1;
        return tb.toString().compareTo(ta.toString());
    }

    private Set<Long> parseDeptIds(String deptIds) {
        Set<Long> set = new HashSet<>();
        if (deptIds == null || deptIds.isBlank()) {
            return set;
        }
        for (String part : deptIds.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                set.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // skip invalid id
            }
        }
        return set;
    }

    @SuppressWarnings("unchecked")
    private List<Long> parseRegisterIds(Object raw) {
        if (raw == null) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                Long id = asLong(item);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
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
