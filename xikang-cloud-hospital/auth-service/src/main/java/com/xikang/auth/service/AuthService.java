package com.xikang.auth.service;

import com.xikang.auth.entity.Patient;
import com.xikang.auth.entity.User;
import com.xikang.auth.mapper.UserMapper;
import com.xikang.auth.mapper.PatientMapper;
import com.xikang.auth.mapper.UserPatientManagedMapper;
import com.xikang.auth.dto.UserInfoResponse.PatientInfo;
import com.xikang.auth.dto.LoginRequest;
import com.xikang.common.exception.BusinessException;
import com.xikang.common.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Authentication Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PatientMapper patientMapper;
    private final PatientService patientService;
    private final UserPatientManagedMapper userPatientManagedMapper;
    private final CaptchaService captchaService;

    @Value("${jwt.accessExpirationMs:900000}")
    private long accessExpirationMs;

    @Value("${jwt.refreshExpirationMs:604800000}")
    private long refreshExpirationMs;

    /**
     * User login
     */
    public Map<String, String> login(LoginRequest request) {
        captchaService.validateAndConsume(request.getCaptchaId(), request.getCaptchaCode());

        String username = request.getUsername();
        String password = request.getPassword();
        log.info("User login attempt: {}", username);

        if (username == null || username.isBlank()) {
            throw new BusinessException(400, "用户名不能为空");
        }
        if (password == null || password.isBlank()) {
            throw new BusinessException(400, "密码不能为空");
        }

        username = username.trim();
        password = password.trim();

        // Query user from database
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // Verify password (plain text for dev, use BCrypt in production)
        if (!user.getPassword().equals(password)) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // Check user status (1 = active)
        if (user.getStatus() != 1) {
            throw new BusinessException(401, "账号已被禁用");
        }

        // Convert userType to role string
        // 1: admin, 2: physician, 3: registration, 4: medtech, 5: pharmacy, 6: patient
        String role = convertUserTypeToRole(user.getUserType());

        // Generate JWT tokens
        Map<String, Object> claims = buildTokenClaims(user);

        String accessToken = JwtUtils.generateToken(username, claims, accessExpirationMs);
        String refreshToken = JwtUtils.generateToken(username, claims, refreshExpirationMs);

        log.info("User login success: {} with role: {}", username, role);

        Map<String, String> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("userId", String.valueOf(user.getId()));
        result.put("role", role);
        result.put("realName", user.getRealName() != null ? user.getRealName() : username);
        if (user.getEmployeeId() != null) {
            result.put("employeeId", String.valueOf(user.getEmployeeId()));
        }
        return result;
    }

    /**
     * User logout
     */
    public void logout(String token) {
        log.info("User logout, token prefix: {}",
                token != null && token.length() > 20 ? token.substring(0, 20) + "..." : token);
    }

    /**
     * Refresh access token
     */
    public Map<String, String> refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(401, "Refresh token 无效或已过期");
        }
        if (!JwtUtils.validateToken(refreshToken)) {
            throw new BusinessException(401, "Refresh token 无效或已过期");
        }

        String username = JwtUtils.getSubject(refreshToken);
        if (username == null || username.isBlank()) {
            throw new BusinessException(401, "Refresh token 无效");
        }

        // Re-validate user exists and is active
        User user = userMapper.selectByUsername(username);
        if (user == null || user.getStatus() != 1) {
            throw new BusinessException(401, "用户不存在或已禁用");
        }

        String role = convertUserTypeToRole(user.getUserType());

        Map<String, Object> claims = buildTokenClaims(user);

        String accessToken = JwtUtils.generateToken(username, claims, accessExpirationMs);

        Map<String, String> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("userId", String.valueOf(user.getId()));
        result.put("role", role);
        if (user.getEmployeeId() != null) {
            result.put("employeeId", String.valueOf(user.getEmployeeId()));
        }
        return result;
    }

    /**
     * Get current user info from token
     */
    public Map<String, Object> me(String accessToken) {
        if (!JwtUtils.validateToken(accessToken)) {
            throw new BusinessException(401, "Access token 无效或已过期");
        }

        String username = JwtUtils.getSubject(accessToken);
        if (username == null || username.isBlank()) {
            throw new BusinessException(401, "Access token 无效");
        }

        // Get userId from token claims
        var claims = JwtUtils.parseToken(accessToken);
        String userIdStr = claims != null ? String.valueOf(claims.get("userId")) : null;
        String role = claims != null ? (String) claims.get("role") : "admin";

        Long userId = null;
        String realName = null;
        List<PatientInfo> patients = new ArrayList<>();

        User user = userMapper.selectByUsername(username);
        if (user != null) {
            realName = user.getRealName();
            userId = user.getId();

            // 获取患者列表
            List<Patient> patientList = patientService.getPatientsByUserId(userId);
            patients = patientList.stream()
                    .map(p -> PatientInfo.builder()
                            .patientId(p.getId())
                            .realName(p.getRealName())
                            .gender(p.getGender())
                            .relation(p.getRelation())
                            .isPrimary(p.getIsPrimary())
                            .accountBalance(p.getAccountBalance())
                            .allergyHistory(p.getAllergyHistory())
                            .build())
                    .collect(Collectors.toList());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("userId", userIdStr != null ? userIdStr : username);
        result.put("username", username);  // 新增：返回登录用户名
        result.put("role", role);
        result.put("realName", realName != null ? realName : username);
        result.put("patients", patients);
        if (user != null && user.getEmployeeId() != null) {
            result.put("employeeId", user.getEmployeeId());
        }
        return result;
    }

    /**
     * User register (for patients and staff)
     * 患者注册时自动创建 patient 档案并建立关联
     */
    @Transactional
    public void register(Map<String, String> registerRequest) {
        String username = registerRequest.get("username");
        String password = registerRequest.get("password");
        String realName = registerRequest.get("realName");
        String phone = registerRequest.get("phone");
        String idCard = registerRequest.get("idCard");
        String gender = registerRequest.get("gender");
        String birthdateStr = registerRequest.get("birthdate");
        String userTypeStr = registerRequest.get("userType");

        username = username == null ? null : username.trim();
        realName = realName == null ? null : realName.trim();
        phone = phone == null ? null : phone.trim();
        idCard = idCard == null ? null : idCard.trim().toUpperCase();

        if (username == null || username.isBlank()) {
            throw new BusinessException(400, "用户名不能为空");
        }
        if (password == null || password.isBlank()) {
            throw new BusinessException(400, "密码不能为空");
        }
        if (password.length() < 6) {
            throw new BusinessException(400, "密码长度不能少于6位");
        }
        if (realName == null || realName.isBlank()) {
            throw new BusinessException(400, "真实姓名不能为空");
        }
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(400, "请输入正确的11位手机号");
        }
        if (!isValidChineseIdCard(idCard)) {
            throw new BusinessException(400, "请输入正确的18位身份证号");
        }

        // 患者的出生日期和性别以已校验的身份证为唯一数据源，不信任前端传值。
        LocalDate idCardBirthdate = parseBirthdateFromIdCard(idCard);
        String idCardGender = parseGenderFromIdCard(idCard);

        // Check if username already exists
        User existing = userMapper.selectByUsername(username);
        if (existing != null) {
            throw new BusinessException(409, "用户名已存在");
        }

        // 检查身份证号是否已存在（已有则关联，不再新建）
        Patient existingPatient = patientMapper.selectByIdCard(idCard);
        Integer patientId;

        if (existingPatient != null) {
            if (existingPatient.getRealName() != null && !existingPatient.getRealName().equals(realName)) {
                throw new BusinessException(409, "身份证号与姓名不匹配");
            }
            // 身份证号已存在，直接关联
            patientId = existingPatient.getId();
            log.info("Patient already exists for idCard: {}, patientId: {}", idCard, patientId);
        } else {
            // 创建患者档案
            Patient patient = new Patient();
            patient.setRealName(realName != null ? realName : username);
            patient.setIdCard(idCard);
            patient.setGender(idCardGender);
            patient.setBirthdate(idCardBirthdate);

            patient.setPhone(phone != null ? phone : "");
            patient.setDelmark(1);
            patient.setCreateTime(LocalDateTime.now());
            patient.setUpdateTime(LocalDateTime.now());

            patientMapper.insert(patient);
            patientId = patient.getId();
            log.info("Patient profile created for user: {}, patientId: {}", username, patientId);
        }

        // 创建用户
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setRealName(realName != null ? realName : username);
        user.setPhone(phone);
        user.setUserType(userTypeStr != null ? Integer.parseInt(userTypeStr) : 6);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        userMapper.insert(user);
        Long userId = user.getId();
        log.info("User registered successfully: {}, userId: {}", username, userId);

        // 建立关联关系（本人）
        userPatientManagedMapper.insert(userId, patientId, "本人");
        log.info("User-Patient relation created: userId={}, patientId={}, relation=本人", userId, patientId);
    }

    /**
     * Validate token
     */
    public Map<String, Object> validateToken(String token) {
        Map<String, Object> result = new HashMap<>();
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        boolean valid = JwtUtils.validateToken(token);
        result.put("valid", valid);
        if (valid) {
            var claims = JwtUtils.parseToken(token);
            result.put("userId", claims != null ? claims.get("userId") : JwtUtils.getSubject(token));
            result.put("role", claims != null ? claims.get("role") : "admin");
        }
        return result;
    }

    private Map<String, Object> buildTokenClaims(User user) {
        String role = convertUserTypeToRole(user.getUserType());
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("role", role);
        if (user.getEmployeeId() != null) {
            claims.put("employeeId", user.getEmployeeId());
        }
        return claims;
    }

    /**
     * Convert userType to role string
     * 1: admin, 2: physician, 3: registration, 4: medtech, 5: pharmacy, 6: patient, 7: followup
     */
    private String convertUserTypeToRole(Integer userType) {
        if (userType == null) {
            return "patient";
        }
        return switch (userType) {
            case 1 -> "admin";
            case 2 -> "physician";
            case 3 -> "registration";
            case 4 -> "medtech";
            case 5 -> "pharmacy";
            case 7 -> "followup";
            default -> "patient";
        };
    }

    /**
     * Change user password
     */
    @Transactional
    public void changePassword(String username, String oldPassword, String newPassword) {
        if (username == null || username.isBlank()) {
            throw new BusinessException(400, "用户名不能为空");
        }
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new BusinessException(400, "旧密码不能为空");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new BusinessException(400, "新密码不能为空");
        }
        if (newPassword.length() < 6) {
            throw new BusinessException(400, "新密码长度不能少于6位");
        }
        if (oldPassword.equals(newPassword)) {
            throw new BusinessException(400, "新密码不能与旧密码相同");
        }

        // Query user from database
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BusinessException(401, "用户不存在");
        }

        // Verify old password
        if (!user.getPassword().equals(oldPassword)) {
            throw new BusinessException(401, "旧密码错误");
        }

        // Update password
        int rows = userMapper.updatePassword(user.getId(), newPassword);
        if (rows == 0) {
            throw new BusinessException(500, "密码更新失败");
        }

        log.info("User password changed successfully: {}", username);
    }

    /** 校验18位中国居民身份证：格式、出生日期和 MOD 11-2 校验位。 */
    private boolean isValidChineseIdCard(String idCard) {
        if (idCard == null || !idCard.matches("^\\d{17}[0-9X]$")) {
            return false;
        }
        LocalDate birthdate = parseBirthdateFromIdCard(idCard);
        if (birthdate == null || birthdate.isAfter(LocalDate.now()) || birthdate.isBefore(LocalDate.of(1900, 1, 1))) {
            return false;
        }
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] checks = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (idCard.charAt(i) - '0') * weights[i];
        }
        return idCard.charAt(17) == checks[sum % 11];
    }

    private String resolveGender(String gender, String idCard) {
        if (gender != null && !gender.isBlank() && ("男".equals(gender) || "女".equals(gender))) {
            return gender;
        }
        return parseGenderFromIdCard(idCard);
    }

    private String parseGenderFromIdCard(String idCard) {
        if (idCard == null || idCard.length() != 18) {
            return null;
        }
        char seq = idCard.charAt(16);
        if (!Character.isDigit(seq)) {
            return null;
        }
        return ((seq - '0') % 2 == 1) ? "男" : "女";
    }

    private LocalDate parseBirthdateFromIdCard(String idCard) {
        if (idCard == null || idCard.length() != 18) {
            return null;
        }
        try {
            int year = Integer.parseInt(idCard.substring(6, 10));
            int month = Integer.parseInt(idCard.substring(10, 12));
            int day = Integer.parseInt(idCard.substring(12, 14));
            return LocalDate.of(year, month, day);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
