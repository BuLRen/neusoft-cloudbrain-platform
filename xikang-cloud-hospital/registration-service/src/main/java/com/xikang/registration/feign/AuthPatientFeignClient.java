package com.xikang.registration.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 患者账户 Feign 客户端
 */
@FeignClient(name = "auth-service")
public interface AuthPatientFeignClient {

    @GetMapping("/api/patient/search")
    Map<String, Object> searchPatients(@RequestParam("keyword") String keyword,
                                       @RequestParam(value = "limit", defaultValue = "20") int limit);

    @GetMapping("/api/patient/{patientId}")
    Map<String, Object> getPatient(@PathVariable("patientId") Integer patientId);

    @GetMapping("/api/patient/{patientId}/balance")
    Map<String, Object> getBalance(@PathVariable("patientId") Integer patientId);

    @PostMapping("/api/patient/{patientId}/balance/recharge")
    Map<String, Object> rechargeBalance(@PathVariable("patientId") Integer patientId, @RequestBody Map<String, Object> body);

    @PostMapping("/api/patient/{patientId}/balance/deduct")
    Map<String, Object> deductBalance(@PathVariable("patientId") Integer patientId, @RequestBody Map<String, Object> body);

    @PostMapping("/api/patient/{patientId}/balance/refund")
    Map<String, Object> refundBalance(@PathVariable("patientId") Integer patientId, @RequestBody Map<String, Object> body);

    @GetMapping("/api/patient/admin/patients")
    Map<String, Object> listAdminPatients(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "includeDisabled", required = false) Boolean includeDisabled,
        @RequestParam(value = "page", defaultValue = "1") Integer page,
        @RequestParam(value = "size", defaultValue = "20") Integer size
    );

    @GetMapping("/api/patient/admin/patients/{id}")
    Map<String, Object> getAdminPatient(@PathVariable("id") Integer id);

    @PostMapping("/api/patient/admin/patients")
    Map<String, Object> createAdminPatient(@RequestBody Map<String, Object> body);

    @PutMapping("/api/patient/admin/patients/{id}")
    Map<String, Object> updateAdminPatient(@PathVariable("id") Integer id, @RequestBody Map<String, Object> body);

    @PostMapping("/api/patient/admin/patients/{id}/status")
    Map<String, Object> updateAdminPatientStatus(@PathVariable("id") Integer id, @RequestBody Map<String, Object> body);
}
