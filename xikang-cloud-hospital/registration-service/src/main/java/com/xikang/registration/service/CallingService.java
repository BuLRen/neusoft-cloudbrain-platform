package com.xikang.registration.service;

import com.xikang.registration.entity.Department;
import com.xikang.registration.entity.Employee;
import com.xikang.registration.entity.Register;
import com.xikang.registration.mapper.RegistrationMapper;
import com.xikang.registration.sse.CallingEventBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 叫号系统业务服务（设计文档 §4.1）。
 *
 * 五个核心动作：
 *   1. callNext(doctorId)           —— 自动选下一个号 → 已叫
 *   2. callSpecific(registerId)     —— 叫指定号（重叫过号常用）
 *   3. answer(registerId)           —— 患者进诊室 → 已应答，visit_state 同步改 2
 *   4. pass(registerId)             —— 没人来 → 过号
 *   5. currentCalling(doctorId)     —— 查当前医生的"已叫未应答"
 *
 * 关键业务规则（设计文档 §3.3, §9.2）：
 *   - call_round >= 2 后，过号为终态，禁止重叫（CallingService 拒绝并返回 400）
 *   - call_status=1 已叫 不改 visit_state；call_status=2 已应答 时 visit_state 改 2
 *   - 过号不改 call_round（call_round 只在"叫号"时累加）
 *
 * 所有动作成功后会调 CallingEventBroadcaster 推 SSE 事件给订阅端。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallingService {

    /** 过号上限：同一号最多叫 2 次，第 2 次仍无人应答则锁定为过号终态（设计文档 §9.2） */
    public static final int MAX_CALL_ROUND = 2;

    private final RegistrationMapper registrationMapper;
    private final DepartmentService departmentService;
    private final EmployeeService employeeService;
    private final CallingEventBroadcaster broadcaster;

    // ==================== 叫号 ====================

    /**
     * 叫下一个：自动选取该医生名下报到的、未叫/过号的第一条，置为已叫。
     *
     * @param employeeId 当前医生的 employeeId
     * @return 包含 registerId / patientName / queueNumber / callRound / calledTime 的 Map
     * @throws IllegalStateException 没有可叫的号时抛
     */
    @Transactional
    public Map<String, Object> callNext(Long employeeId) {
        if (employeeId == null) {
            throw new IllegalArgumentException("缺少医生身份");
        }
        LocalDate today = LocalDate.now();
        // FOR UPDATE 锁住候选号
        Register next = registrationMapper.selectNextCallableForUpdate(employeeId, today);
        if (next == null) {
            throw new IllegalStateException("当前没有待叫的号");
        }

        // 防御性：过号终态禁止重叫（虽然 SQL 已用 call_status IN (0,3) 过滤，
        // 但 call_round>=2 时 call_status 仍然是 3，这里再判一次）
        if (next.getCallRound() != null && next.getCallRound() >= MAX_CALL_ROUND
                && Integer.valueOf(3).equals(next.getCallStatus())) {
            throw new IllegalStateException("该号已过号 " + MAX_CALL_ROUND
                    + " 次，请转人工处理（registerId=" + next.getId() + "）");
        }

        return doMarkCalled(next);
    }

    /**
     * 叫指定号（重呼当前 / 重叫过号常用）。
     *
     * <p>设计选择：不强制校验 {@code operatorEmployeeId == reg.employeeId}。
     * 理由：
     * <ul>
     *   <li>换班场景：交班后新医生重叫旧医生遗留的号是合理的；</li>
     *   <li>重呼场景：传进来的 registerId 通常就是该医生刚叫出来的号，
     *       employee_id 天然吻合，校验只是冗余；</li>
     *   <li>真正的"乱叫别人科室的号"在生产部署后由 controller 侧审计日志兜底，
     *       不在业务层强约束（强约束反而挡住合理用例）。</li>
     * </ul>
     * 这是经过 review 的有意识决定，不是漏加。请勿当作 bug 修复。
     *
     * @throws IllegalStateException 该号不存在 / 已应答 / 过号终态
     */
    @Transactional
    public Map<String, Object> callSpecific(Long registerId, Long operatorEmployeeId) {
        Register reg = registrationMapper.selectById(registerId);
        if (reg == null) {
            throw new IllegalStateException("挂号记录不存在：id=" + registerId);
        }
        if (Integer.valueOf(2).equals(reg.getCallStatus())) {
            throw new IllegalStateException("该号已应答（进诊室），不能重叫");
        }
        if (Integer.valueOf(1).equals(reg.getCallStatus())) {
            // 已经在叫了，幂等返回当前状态
            log.info("registerId={} 已是 call_status=1（已叫），幂等返回", registerId);
            return toCallResult(reg, false);
        }
        if (reg.getCallRound() != null && reg.getCallRound() >= MAX_CALL_ROUND
                && Integer.valueOf(3).equals(reg.getCallStatus())) {
            throw new IllegalStateException("该号已过号 " + MAX_CALL_ROUND
                    + " 次，请转人工处理");
        }
        return doMarkCalled(reg);
    }

    /** 把 register 置为已叫，并广播 CALLED 事件 */
    private Map<String, Object> doMarkCalled(Register reg) {
        LocalDateTime now = LocalDateTime.now();
        int affected = registrationMapper.markCalled(reg.getId(), now);
        if (affected == 0) {
            // 乐观锁失败：状态已被别人改了
            throw new IllegalStateException("该号状态已变更，请刷新后重试（registerId="
                    + reg.getId() + "）");
        }
        // 重新查最新数据（call_status / call_round / called_time 已更新）
        Register latest = registrationMapper.selectById(reg.getId());
        Map<String, Object> result = toCallResult(latest, true);

        // 广播 CALLED 事件
        broadcaster.broadcastCalled(latest);
        log.info("[CALL] 叫号 registerId={}, patientName={}, callRound={}",
                latest.getId(), latest.getRealName(), latest.getCallRound());
        return result;
    }

    // ==================== 应答 ====================

    /**
     * 患者应答（进诊室）：call_status=2, visit_state=2, answered_time=now。
     */
    @Transactional
    public Map<String, Object> answer(Long registerId) {
        Register reg = registrationMapper.selectById(registerId);
        if (reg == null) {
            throw new IllegalStateException("挂号记录不存在：id=" + registerId);
        }
        if (Integer.valueOf(2).equals(reg.getCallStatus())) {
            // 已应答，幂等返回
            log.info("registerId={} 已应答，幂等返回", registerId);
            return toCallResult(reg, false);
        }
        if (!Integer.valueOf(1).equals(reg.getCallStatus())) {
            throw new IllegalStateException("当前状态不允许应答（call_status="
                    + reg.getCallStatus() + "），只有已叫(1)才能应答");
        }
        LocalDateTime now = LocalDateTime.now();
        int affected = registrationMapper.markAnswered(registerId, now);
        if (affected == 0) {
            throw new IllegalStateException("状态已变更，请刷新后重试");
        }
        Register latest = registrationMapper.selectById(registerId);
        Map<String, Object> result = toCallResult(latest, true);

        broadcaster.broadcastAnswered(latest);
        log.info("[ANSWER] 患者应答 registerId={}, patientName={}",
                registerId, latest.getRealName());
        return result;
    }

    // ==================== 过号 ====================

    /**
     * 标记过号：call_status=3。
     */
    @Transactional
    public Map<String, Object> pass(Long registerId) {
        Register reg = registrationMapper.selectById(registerId);
        if (reg == null) {
            throw new IllegalStateException("挂号记录不存在：id=" + registerId);
        }
        if (Integer.valueOf(3).equals(reg.getCallStatus())) {
            // 已过号，幂等返回（注意区分是不是终态）
            log.info("registerId={} 已过号，幂等返回", registerId);
            return toCallResult(reg, false);
        }
        if (Integer.valueOf(2).equals(reg.getCallStatus())) {
            throw new IllegalStateException("该号已应答（进诊室），不能过号");
        }
        int affected = registrationMapper.markPassed(registerId);
        if (affected == 0) {
            throw new IllegalStateException("状态已变更，请刷新后重试");
        }
        Register latest = registrationMapper.selectById(registerId);
        Map<String, Object> result = toCallResult(latest, true);

        broadcaster.broadcastPassed(latest);
        log.info("[PASS] 过号 registerId={}, patientName={}, callRound={}",
                registerId, latest.getRealName(), latest.getCallRound());
        return result;
    }

    // ==================== 查询 ====================

    /**
     * 查当前医生的"已叫未应答"号（医生工作站"当前叫号"用）。
     */
    public Map<String, Object> currentCalling(Long employeeId) {
        if (employeeId == null) return Map.of("hasCalling", false);
        Register reg = registrationMapper.selectCurrentCallingByDoctor(employeeId, LocalDate.now());
        if (reg == null) return Map.of("hasCalling", false);
        Map<String, Object> result = toCallResult(reg, false);
        result.put("hasCalling", true);
        return result;
    }

    /**
     * 医生今日候诊队列（含号序与是否可调序）。
     */
    public List<Map<String, Object>> doctorWaitingQueue(Long employeeId) {
        if (employeeId == null) {
            return List.of();
        }
        List<Register> list = registrationMapper.selectWaitingByDoctor(employeeId, LocalDate.now());
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Register reg : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("registerId", reg.getId());
            item.put("realName", reg.getRealName());
            item.put("caseNumber", reg.getCaseNumber());
            item.put("callStatus", reg.getCallStatus());
            item.put("callRound", reg.getCallRound());
            item.put("checkInTime", reg.getCheckInTime());
            item.put("queuePosition", reg.getQueuePosition());
            int before = registrationMapper.countWaitingBefore(reg.getId());
            item.put("queueNumber", before + 1);
            item.put("canReorder", canReorder(reg));
            result.add(item);
        }
        return result;
    }

    /**
     * 调整医生候诊队列顺序。
     * registerIds 为完整队列的新顺序（含锁定中的 call_status=1 患者，且其位置不可变）。
     */
    @Transactional
    public void reorderQueue(Long employeeId, List<Long> registerIds) {
        if (employeeId == null) {
            throw new IllegalArgumentException("缺少医生身份");
        }
        if (registerIds == null || registerIds.isEmpty()) {
            throw new IllegalArgumentException("队列不能为空");
        }
        LocalDate today = LocalDate.now();
        List<Register> current = registrationMapper.selectWaitingByDoctor(employeeId, today);
        if (current.size() != registerIds.size()) {
            throw new IllegalStateException("队列成员已变化，请刷新后重试");
        }

        Set<Long> currentIds = new HashSet<>();
        Map<Long, Integer> oldIndex = new HashMap<>();
        for (int i = 0; i < current.size(); i++) {
            Register reg = current.get(i);
            currentIds.add(reg.getId());
            oldIndex.put(reg.getId(), i);
        }
        Set<Long> requestIds = new HashSet<>(registerIds);
        if (!currentIds.equals(requestIds)) {
            throw new IllegalStateException("队列成员与请求不一致，请刷新后重试");
        }

        for (int i = 0; i < registerIds.size(); i++) {
            Long id = registerIds.get(i);
            Register reg = current.stream()
                    .filter(r -> r.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("挂号记录不存在：" + id));

            if (Integer.valueOf(1).equals(reg.getCallStatus())) {
                if (!oldIndex.get(id).equals(i)) {
                    throw new IllegalStateException("正在叫号的患者不能调整顺序");
                }
            } else if (!canReorder(reg)) {
                throw new IllegalStateException("该患者不允许调整顺序（registerId=" + id + "）");
            }
        }

        for (int i = 0; i < registerIds.size(); i++) {
            registrationMapper.updateQueuePosition(registerIds.get(i), i + 1);
        }
        log.info("[QUEUE] 医生 {} 调整候诊队列，共 {} 人", employeeId, registerIds.size());
    }

    private boolean canReorder(Register reg) {
        if (Integer.valueOf(1).equals(reg.getCallStatus())) {
            return false;
        }
        if (Integer.valueOf(3).equals(reg.getCallStatus())
                && reg.getCallRound() != null
                && reg.getCallRound() >= MAX_CALL_ROUND) {
            return false;
        }
        return Integer.valueOf(0).equals(reg.getCallStatus())
                || Integer.valueOf(3).equals(reg.getCallStatus());
    }

    // ==================== 内部辅助 ====================

    /**
     * 组装叫号结果 Map，附带科室名 / 医生名 / 候诊人数。
     * @param includeWaitingCount 是否计算"前面还有 X 人"（查接口需要，叫号动作可省）
     */
    private Map<String, Object> toCallResult(Register reg, boolean includeWaitingCount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("registerId", reg.getId());
        m.put("patientName", reg.getRealName());
        m.put("caseNumber", reg.getCaseNumber());
        m.put("callStatus", reg.getCallStatus());
        m.put("callRound", reg.getCallRound());
        m.put("calledTime", reg.getCalledTime());
        m.put("answeredTime", reg.getAnsweredTime());
        m.put("departmentId", reg.getDeptmentId());
        m.put("doctorId", reg.getEmployeeId());

        // 补充展示字段
        if (reg.getDeptmentId() != null) {
            Department dept = departmentService.getDepartment(reg.getDeptmentId());
            if (dept != null) m.put("departmentName", dept.getName());
        }
        if (reg.getEmployeeId() != null) {
            Employee doc = employeeService.getDoctor(reg.getEmployeeId());
            if (doc != null) m.put("doctorName", doc.getRealname());
        }

        // 号序 / 前面还有几人（基于 check_in_time 排序）
        if (includeWaitingCount && reg.getId() != null) {
            int before = registrationMapper.countWaitingBefore(reg.getId());
            m.put("queueNumber", before + 1);
            m.put("waitingBefore", before);
        }
        return m;
    }
}
