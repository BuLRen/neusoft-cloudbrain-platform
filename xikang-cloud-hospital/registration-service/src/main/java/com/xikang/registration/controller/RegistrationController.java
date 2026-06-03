package com.xikang.registration.controller;

import com.xikang.common.result.Result;
import com.xikang.registration.entity.Department;
import com.xikang.registration.entity.Employee;
import com.xikang.registration.entity.RegistLevel;
import com.xikang.registration.entity.SettleCategory;
import com.xikang.registration.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Registration Controller - 挂号收费控制器
 */
@RestController
@RequestMapping("/api/registration")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;
    private final ChargeService chargeService;
    private final RefundService refundService;
    private final DepartmentService departmentService;
    private final RegistLevelService registLevelService;
    private final SettleCategoryService settleCategoryService;
    private final ExpenseRecordService expenseRecordService;
    private final EmployeeService employeeService;

    // ==================== 挂号相关接口 ====================

    /**
     * 患者挂号
     */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = registrationService.createRegistration(request);
        return Result.success(result);
    }

    /**
     * 取消挂号（退号）
     */
    @PutMapping("/{id}/cancel")
    public Result<Void> cancelRegister(@PathVariable Long id) {
        registrationService.cancelRegistration(id);
        return Result.success();
    }

    /**
     * 获取挂号详情
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getRegistration(@PathVariable Long id) {
        Map<String, Object> registration = registrationService.getRegistration(id);
        return Result.success(registration);
    }

    /**
     * 获取患者的挂号列表
     */
    @GetMapping("/patient/{patientId}")
    public Result<List<Map<String, Object>>> getPatientRegistrations(@PathVariable Long patientId) {
        List<Map<String, Object>> registrations = registrationService.listRegistrationsByPatient(patientId);
        return Result.success(registrations);
    }

    /**
     * 按日期查询挂号列表
     */
    @GetMapping("/date/{date}")
    public Result<List<Map<String, Object>>> getRegistrationsByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<Map<String, Object>> registrations = registrationService.listRegistrationsByDate(date);
        return Result.success(registrations);
    }

    // ==================== 收费相关接口 ====================

    /**
     * 收费
     */
    @PostMapping("/charge")
    public Result<Map<String, Object>> charge(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = chargeService.charge(request);
        return Result.success("收费成功", result);
    }

    /**
     * 退费
     */
    @PostMapping("/refund")
    public Result<Void> refund(@RequestBody Map<String, Object> request) {
        Long expenseRecordId = ((Number) request.get("expenseRecordId")).longValue();
        Long operatorId = request.get("operatorId") != null
            ? ((Number) request.get("operatorId")).longValue()
            : null;
        String operatorName = (String) request.get("operatorName");
        String reason = (String) request.getOrDefault("reason", "用户申请退费");

        refundService.refund(expenseRecordId, operatorId, operatorName, reason);
        return Result.success();
    }

    /**
     * 按挂号ID退费（全部退费）
     */
    @PostMapping("/refund/register/{registerId}")
    public Result<Void> refundByRegisterId(
            @PathVariable Long registerId,
            @RequestBody Map<String, Object> request) {
        Long operatorId = request.get("operatorId") != null
            ? ((Number) request.get("operatorId")).longValue()
            : null;
        String operatorName = (String) request.get("operatorName");
        String reason = (String) request.getOrDefault("reason", "用户申请退费");

        refundService.refundByRegisterId(registerId, operatorId, operatorName, reason);
        return Result.success();
    }

    /**
     * 查询费用记录
     */
    @GetMapping("/expense-records")
    public Result<List<Map<String, Object>>> getExpenseRecords(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long registerId,
            @RequestParam(required = false) Integer status) {
        List<Map<String, Object>> records = expenseRecordService.queryExpenseRecords(patientId, registerId, status);
        return Result.success(records);
    }

    /**
     * 获取患者待缴费项目
     */
    @GetMapping("/pending-charges/{patientId}")
    public Result<List<Map<String, Object>>> getPendingCharges(@PathVariable Long patientId) {
        List<Map<String, Object>> pendingCharges = chargeService.getPendingChargesByPatient(patientId);
        return Result.success(pendingCharges);
    }

    /**
     * 获取挂号待缴费项目
     */
    @GetMapping("/pending-charges/register/{registerId}")
    public Result<List<Map<String, Object>>> getPendingChargesByRegister(@PathVariable Long registerId) {
        List<Map<String, Object>> pendingCharges = chargeService.getPendingCharges(registerId);
        return Result.success(pendingCharges);
    }

    // ==================== 基础数据接口 ====================

    /**
     * 获取科室列表
     */
    @GetMapping("/departments")
    public Result<List<Department>> getDepartments() {
        List<Department> departments = departmentService.getAllDepartments();
        return Result.success(departments);
    }

    /**
     * 按类型获取科室
     */
    @GetMapping("/departments/type/{type}")
    public Result<List<Department>> getDepartmentsByType(@PathVariable String type) {
        List<Department> departments = departmentService.getDepartmentsByType(type);
        return Result.success(departments);
    }

    /**
     * 获取科室可用排班
     */
    @GetMapping("/scheduling/{departmentId}/{date}")
    public Result<List<Map<String, Object>>> getScheduling(
            @PathVariable Long departmentId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<Map<String, Object>> scheduling = registrationService.getAvailableScheduling(departmentId, date);
        return Result.success(scheduling);
    }

    /**
     * 获取排班详情
     */
    @GetMapping("/scheduling/{schedulingId}/detail")
    public Result<Map<String, Object>> getSchedulingDetail(@PathVariable Long schedulingId) {
        Map<String, Object> detail = registrationService.getSchedulingAvailable(schedulingId);
        return Result.success(detail);
    }

    /**
     * 获取挂号级别列表
     */
    @GetMapping("/regist-levels")
    public Result<List<RegistLevel>> getRegistLevels() {
        List<RegistLevel> levels = registLevelService.getAllLevels();
        return Result.success(levels);
    }

    /**
     * 获取结算类别列表
     */
    @GetMapping("/settle-categories")
    public Result<List<SettleCategory>> getSettleCategories() {
        List<SettleCategory> categories = settleCategoryService.getAllCategories();
        return Result.success(categories);
    }

    // ==================== 管理员接口 ====================

    /**
     * 创建科室
     */
    @PostMapping("/departments")
    public Result<Department> createDepartment(@RequestBody Department department) {
        Department created = departmentService.createDepartment(department);
        return Result.success(created);
    }

    /**
     * 更新科室
     */
    @PutMapping("/departments/{id}")
    public Result<Void> updateDepartment(@PathVariable Long id, @RequestBody Department department) {
        department.setId(id);
        departmentService.updateDepartment(department);
        return Result.success();
    }

    /**
     * 删除科室
     */
    @DeleteMapping("/departments/{id}")
    public Result<Void> deleteDepartment(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return Result.success();
    }

    /**
     * 创建挂号级别
     */
    @PostMapping("/regist-levels")
    public Result<RegistLevel> createRegistLevel(@RequestBody RegistLevel level) {
        RegistLevel created = registLevelService.createLevel(level);
        return Result.success(created);
    }

    /**
     * 更新挂号级别
     */
    @PutMapping("/regist-levels/{id}")
    public Result<Void> updateRegistLevel(@PathVariable Long id, @RequestBody RegistLevel level) {
        registLevelService.updateLevel(id, level);
        return Result.success();
    }

/**
     * 删除挂号级别
     */
    @DeleteMapping("/regist-levels/{id}")
    public Result<Void> deleteRegistLevel(@PathVariable Long id) {
        registLevelService.deleteLevel(id);
        return Result.success();
    }

    // ==================== 医生相关接口 ====================

    /**
     * 获取科室医生列表
     */
    @GetMapping("/doctors/department/{departmentId}")
    public Result<List<Employee>> getDoctorsByDepartment(@PathVariable Long departmentId) {
        List<Employee> doctors = employeeService.getDoctorsByDepartment(departmentId);
        return Result.success(doctors);
    }

    /**
     * 根据科室和挂号级别获取医生列表
     */
    @GetMapping("/doctors/department/{departmentId}/level/{registLevelId}")
    public Result<List<Employee>> getDoctorsByDepartmentAndLevel(
            @PathVariable Long departmentId,
            @PathVariable Long registLevelId) {
        List<Employee> doctors = employeeService.getDoctorsByDepartmentAndLevel(departmentId, registLevelId);
        return Result.success(doctors);
    }

    /**
     * 获取医生详情
     */
    @GetMapping("/doctors/{id}")
    public Result<Employee> getDoctor(@PathVariable Long id) {
        Employee doctor = employeeService.getDoctor(id);
        return Result.success(doctor);
    }
}
