package com.xikang.pharmacy.controller;

import com.xikang.common.result.Result;
import com.xikang.pharmacy.service.PharmacyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Pharmacy Controller
 */
@RestController
@RequestMapping("/api/pharmacy")
@RequiredArgsConstructor
public class PharmacyController {

    private final PharmacyService pharmacyService;

    /**
     * Get prescription dispensing list
     */
    @GetMapping("/dispensing/{registrationId}")
    public Result<Object> getDispensingList(@PathVariable Long registrationId) {
        return Result.success(pharmacyService.getDispensingList(registrationId));
    }

    /**
     * Create dispensing record
     */
    @PostMapping("/dispensing")
    public Result<Map<String, Object>> createDispensing(@RequestBody Map<String, Object> dispensingRequest) {
        Map<String, Object> result = pharmacyService.createDispensing(dispensingRequest);
        return Result.success(result);
    }

    /**
     * Complete dispensing
     */
    @PutMapping("/dispensing/{id}/complete")
    public Result<Void> completeDispensing(@PathVariable Long id) {
        pharmacyService.completeDispensing(id);
        return Result.success();
    }

    /**
     * Get medication inventory
     */
    @GetMapping("/inventory")
    public Result<Object> getInventory() {
        return Result.success(pharmacyService.getInventory());
    }
}
