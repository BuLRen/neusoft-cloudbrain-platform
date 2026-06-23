package com.xikang.schedule.client;

import com.xikang.common.result.Result;
import com.xikang.schedule.client.dto.DepartmentDTO;
import com.xikang.schedule.client.dto.EmployeeDTO;
import com.xikang.schedule.client.dto.RegistLevelDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "registration-service", path = "/api/registration")
public interface RegistrationClient {

    @GetMapping("/departments")
    Result<List<DepartmentDTO>> getDepartments();

    @GetMapping("/doctors/department/{departmentId}")
    Result<List<EmployeeDTO>> getDoctorsByDepartment(@PathVariable("departmentId") Long departmentId);

    @GetMapping("/regist-levels")
    Result<List<RegistLevelDTO>> getRegistLevels();
}
