package com.xikang.auth.service;

import com.xikang.auth.entity.Patient;
import com.xikang.auth.mapper.PatientMapper;
import com.xikang.auth.mapper.UserPatientManagedMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Patient Service - 患者管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientMapper patientMapper;
    private final UserPatientManagedMapper userPatientManagedMapper;

    /**
     * 根据用户ID获取该用户管理的患者列表（本人+家人）
     */
    public List<Patient> getPatientsByUserId(Long userId) {
        log.info("Get patients for userId: {}", userId);
        return patientMapper.selectByUserId(userId);
    }

    /**
     * 根据ID获取患者信息
     */
    public Patient getPatientById(Integer patientId) {
        return patientMapper.selectById(patientId);
    }

    /**
     * 根据身份证号获取患者信息
     */
    public Patient getPatientByIdCard(String idCard) {
        return patientMapper.selectByIdCard(idCard);
    }

    /**
     * 新增患者档案
     */
    public Patient createPatient(Patient patient) {
        // 检查身份证号是否已存在
        Patient existing = patientMapper.selectByIdCard(patient.getIdCard());
        if (existing != null) {
            throw new com.xikang.common.exception.BusinessException(409, "该身份证号已存在");
        }

        patientMapper.insert(patient);
        log.info("Patient created: id={}, name={}", patient.getId(), patient.getRealName());
        return patient;
    }

    /**
     * 更新患者档案
     */
    public void updatePatient(Patient patient) {
        patientMapper.update(patient);
        log.info("Patient updated: id={}", patient.getId());
    }

    /**
     * 删除患者档案
     */
    public void deletePatient(Integer patientId) {
        patientMapper.deleteById(patientId);
        log.info("Patient deleted: id={}", patientId);
    }
}