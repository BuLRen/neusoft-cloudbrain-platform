package com.xikang.registration.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.registration.feign.AuthPatientFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminPatientService {

    private final AuthPatientFeignClient authPatientFeignClient;

    public Map<String, Object> listPatients(String keyword, Boolean includeDisabled, Integer page, Integer size) {
        int currentPage = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 20 : size;
        return unwrapData(authPatientFeignClient.listAdminPatients(keyword, includeDisabled, currentPage, pageSize));
    }

    public Map<String, Object> getPatient(Integer id) {
        if (id == null) {
            throw new BusinessException(400, "患者 ID 不能为空");
        }
        return unwrapData(authPatientFeignClient.getAdminPatient(id));
    }

    public Map<String, Object> createPatient(Map<String, Object> request) {
        return unwrapData(authPatientFeignClient.createAdminPatient(request));
    }

    public Map<String, Object> updatePatient(Integer id, Map<String, Object> request) {
        if (id == null) {
            throw new BusinessException(400, "患者 ID 不能为空");
        }
        return unwrapData(authPatientFeignClient.updateAdminPatient(id, request));
    }

    public Map<String, Object> updateStatus(Integer id, Integer delmark) {
        if (id == null) {
            throw new BusinessException(400, "患者 ID 不能为空");
        }
        return unwrapData(authPatientFeignClient.updateAdminPatientStatus(id, Map.of("delmark", delmark)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapData(Map<String, Object> resp) {
        if (resp == null) {
            throw new BusinessException(502, "患者服务无响应");
        }
        Object code = resp.get("code");
        if (code instanceof Number n && n.intValue() != 200) {
            Object message = resp.get("message");
            throw new BusinessException(n.intValue(), message != null ? message.toString() : "患者服务调用失败");
        }
        Object data = resp.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new BusinessException(502, "患者服务返回格式异常");
    }
}
