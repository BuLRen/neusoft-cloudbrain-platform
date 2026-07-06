package com.xikang.auth.service;

import com.xikang.auth.dto.PatientAdminView;
import com.xikang.auth.dto.PatientManagedUserRow;
import com.xikang.auth.entity.Patient;
import com.xikang.auth.mapper.PatientMapper;
import com.xikang.auth.mapper.UserPatientManagedMapper;
import com.xikang.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatientAdminService {

    private final PatientMapper patientMapper;
    private final UserPatientManagedMapper userPatientManagedMapper;

    public Map<String, Object> listPatients(String keyword, Boolean includeDisabled, Integer page, Integer size) {
        int currentPage = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        int offset = (currentPage - 1) * pageSize;
        String trimmedKeyword = keyword != null && !keyword.isBlank() ? keyword.trim() : null;

        long total = patientMapper.countForAdmin(trimmedKeyword, includeDisabled);
        List<PatientAdminView> records = patientMapper.selectPageForAdmin(
            trimmedKeyword, includeDisabled, offset, pageSize
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", currentPage);
        result.put("size", pageSize);
        result.put("totalPages", (long) Math.ceil(total / (double) pageSize));
        return result;
    }

    public PatientAdminView getPatient(Integer id) {
        PatientAdminView view = patientMapper.selectAdminViewById(id);
        if (view == null) {
            throw new BusinessException(404, "患者档案不存在");
        }
        List<PatientManagedUserRow> managers = userPatientManagedMapper.selectManagersByPatientId(id);
        view.setManagedUsers(managers.stream().map(this::toManagedUser).collect(Collectors.toList()));
        return view;
    }

    @Transactional
    public PatientAdminView createPatient(Map<String, Object> request) {
        String realName = stringValue(request.get("realName"));
        String idCard = stringValue(request.get("idCard"));
        String gender = stringValue(request.get("gender"));
        LocalDate birthdate = parseBirthdate(request.get("birthdate"), idCard);

        validateRequired(realName, idCard, gender, birthdate);

        Patient existing = patientMapper.selectByIdCard(idCard);
        if (existing != null) {
            throw new BusinessException(400, "该身份证号已存在患者档案");
        }

        Patient patient = new Patient();
        patient.setRealName(realName.trim());
        patient.setIdCard(idCard.trim().toUpperCase());
        patient.setGender(gender.trim());
        patient.setBirthdate(birthdate);
        patient.setPhone(stringValue(request.get("phone")));
        patient.setHomeAddress(stringValue(request.get("homeAddress")));
        patient.setAllergyHistory(stringValue(request.get("allergyHistory")));
        patient.setCreateTime(LocalDateTime.now());
        patient.setUpdateTime(LocalDateTime.now());
        patientMapper.insert(patient);

        return getPatient(patient.getId());
    }

    @Transactional
    public PatientAdminView updatePatient(Integer id, Map<String, Object> request) {
        PatientAdminView existing = patientMapper.selectAdminViewById(id);
        if (existing == null) {
            throw new BusinessException(404, "患者档案不存在");
        }

        Patient patient = new Patient();
        patient.setId(id);

        String realName = stringValue(request.get("realName"));
        if (realName != null && !realName.isBlank()) {
            patient.setRealName(realName.trim());
        }
        String gender = stringValue(request.get("gender"));
        if (gender != null && !gender.isBlank()) {
            patient.setGender(gender.trim());
        }
        if (request.containsKey("birthdate")) {
            LocalDate birthdate = parseBirthdate(request.get("birthdate"), existing.getIdCard());
            if (birthdate != null) {
                patient.setBirthdate(birthdate);
            }
        }
        if (request.containsKey("phone")) {
            patient.setPhone(stringValue(request.get("phone")));
        }
        if (request.containsKey("homeAddress")) {
            patient.setHomeAddress(stringValue(request.get("homeAddress")));
        }
        if (request.containsKey("allergyHistory")) {
            patient.setAllergyHistory(stringValue(request.get("allergyHistory")));
        }

        patientMapper.update(patient);
        return getPatient(id);
    }

    @Transactional
    public PatientAdminView updateStatus(Integer id, Integer delmark) {
        PatientAdminView existing = patientMapper.selectAdminViewById(id);
        if (existing == null) {
            throw new BusinessException(404, "患者档案不存在");
        }
        if (delmark == null || (delmark != 0 && delmark != 1)) {
            throw new BusinessException(400, "状态值无效");
        }
        if (delmark == 1) {
            patientMapper.deleteById(id);
        } else {
            patientMapper.restoreById(id);
        }
        return getPatient(id);
    }

    private PatientAdminView.ManagedUser toManagedUser(PatientManagedUserRow row) {
        PatientAdminView.ManagedUser user = new PatientAdminView.ManagedUser();
        user.setUserId(row.getUserId());
        user.setUsername(row.getUsername());
        user.setRelation(row.getRelation());
        return user;
    }

    private void validateRequired(String realName, String idCard, String gender, LocalDate birthdate) {
        if (realName == null || realName.isBlank()) {
            throw new BusinessException(400, "患者姓名不能为空");
        }
        if (idCard == null || idCard.isBlank()) {
            throw new BusinessException(400, "身份证号不能为空");
        }
        if (idCard.trim().length() != 18) {
            throw new BusinessException(400, "身份证号格式不正确");
        }
        if (gender == null || gender.isBlank()) {
            throw new BusinessException(400, "性别不能为空");
        }
        if (!"男".equals(gender.trim()) && !"女".equals(gender.trim())) {
            throw new BusinessException(400, "性别必须为「男」或「女」");
        }
        if (birthdate == null) {
            throw new BusinessException(400, "出生日期不能为空");
        }
    }

    private LocalDate parseBirthdate(Object value, String idCard) {
        if (value != null) {
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                try {
                    return LocalDate.parse(text);
                } catch (DateTimeParseException e) {
                    throw new BusinessException(400, "出生日期格式不正确");
                }
            }
        }
        if (idCard != null && idCard.length() == 18) {
            try {
                String datePart = idCard.substring(6, 14);
                int year = Integer.parseInt(datePart.substring(0, 4));
                int month = Integer.parseInt(datePart.substring(4, 6));
                int day = Integer.parseInt(datePart.substring(6, 8));
                return LocalDate.of(year, month, day);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
