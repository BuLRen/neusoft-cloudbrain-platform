package com.xikang.registration.controller;

import com.xikang.common.result.Result;
import com.xikang.registration.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Registration Controller
 */
@RestController
@RequestMapping("/api/registration")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    /**
     * Create registration
     */
    @PostMapping
    public Result<Map<String, Object>> createRegistration(@RequestBody Map<String, Object> registrationRequest) {
        Map<String, Object> result = registrationService.createRegistration(registrationRequest);
        return Result.success(result);
    }

    /**
     * Get registration by ID
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getRegistration(@PathVariable Long id) {
        Map<String, Object> registration = registrationService.getRegistration(id);
        return Result.success(registration);
    }

    /**
     * List registrations by patient ID
     */
    @GetMapping("/patient/{patientId}")
    public Result<Object> listRegistrationsByPatient(@PathVariable Long patientId) {
        return Result.success(registrationService.listRegistrationsByPatient(patientId));
    }

    /**
     * Cancel registration
     */
    @PutMapping("/{id}/cancel")
    public Result<Void> cancelRegistration(@PathVariable Long id) {
        registrationService.cancelRegistration(id);
        return Result.success();
    }
}
