# 人员A - 业务服务开发指南

## 开发概述

人员A负责除AI服务外的所有业务服务开发，包括公共模块、基础设施服务和核心业务服务。

> **工作量分配说明**：人员A负责更多服务是正常的，因为业务服务逻辑更复杂，涉及门诊医生工作站、医技检查、药房管理等核心业务模块。人员B专注于AI能力的实现与优化。两者的工作量评估标准不同：业务服务侧重业务逻辑完整性，AI服务侧重模型效果与Prompt优化。

### 负责模块清单

| 服务 | 端口 | 职责 |
|------|------|------|
| common | - | 公共模块，提供通用工具类、常量、配置 |
| gateway-service | 8080 | API网关，统一入口，路由转发 |
| auth-service | 8081 | 认证服务，JWT token管理，用户身份验证 |
| registration-service | 8091 | 挂号收费管理 |
| physician-service | 8092 | 门诊医生工作站 |
| medtech-service | 8093 | 医技检查管理 |
| pharmacy-service | 8094 | 药房管理 |

---

## 服务职责与API

### 1. common 模块

**职责**：提供项目级公共组件，避免代码重复。

**包含内容**：
- `Result<T>` - 统一响应包装类
- `Constants` - 全局常量定义
- `DateUtil` - 日期工具类
- `IdGenerator` - ID生成器
- `BaseEntity` - 实体基类（包含id、createTime、updateTime）
- 全局异常处理器配置
- Spring Boot通用配置类

**Maven依赖引入**：
```xml
<dependency>
    <groupId>com.xikang</groupId>
    <artifactId>common</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

### 2. gateway-service (:8080)

**职责**：
- 路由转发：将请求路由到对应的业务服务
- 鉴权前置：调用auth-service验证token
- 限流熔断：保护下游服务
- 日志记录：记录请求日志

**主要端点**：
```
GET  /api/health              - 健康检查
GET  /api/registration/**     - 路由到registration-service
GET  /api/physician/**        - 路由到physician-service
GET  /api/medtech/**          - 路由到medtech-service
GET  /api/pharmacy/**         - 路由到pharmacy-service
POST /api/ai/**               - 路由到ai-gateway-service
```

**配置文件示例** (`application.yml`)：
```yaml
server:
  port: 8080

spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: lb://auth-service
          predicates:
            - Path=/api/auth/**
        - id: registration-service
          uri: lb://registration-service
          predicates:
            - Path=/api/registration/**
        - id: physician-service
          uri: lb://physician-service
          predicates:
            - Path=/api/physician/**
        - id: medtech-service
          uri: lb://medtech-service
          predicates:
            - Path=/api/medtech/**
        - id: pharmacy-service
          uri: lb://pharmacy-service
          predicates:
            - Path=/api/pharmacy/**
        - id: ai-gateway-service
          uri: lb://ai-gateway-service
          predicates:
            - Path=/api/ai/**
```

---

### 3. auth-service (:8081)

**职责**：
- 用户登录/登出
- JWT Token生成与验证
- 用户信息管理
- 权限校验

**API接口**：

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /api/auth/login | 用户登录，返回token |
| POST | /api/auth/logout | 用户登出 |
| GET | /api/auth/validate | 验证token有效性 |
| GET | /api/auth/userinfo | 获取当前用户信息 |

**登录请求/响应示例**：
```json
// POST /api/auth/login
// Request
{
    "username": "doctor001",
    "password": "encrypted_password",
    "hospitalId": "H001"
}

// Response
{
    "code": 200,
    "message": "success",
    "data": {
        "token": "eyJhbGciOiJIUzI1NiIs...",
        "expiresIn": 7200,
        "userInfo": {
            "userId": "U001",
            "username": "doctor001",
            "name": "张医生",
            "role": "PHYSICIAN",
            "departmentId": "D001"
        }
    }
}
```

**Token验证响应示例**：
```json
// GET /api/auth/validate?token=xxx
{
    "code": 200,
    "data": {
        "valid": true,
        "userId": "U001",
        "username": "doctor001",
        "role": "PHYSICIAN"
    }
}
```

---

### 4. registration-service (:8091)

**职责**：
- 患者挂号
- 收费管理
- 发票管理
- 退号退费处理

**API接口**：

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /api/registration/register | 患者挂号 |
| PUT | /api/registration/register/{id}/cancel | 取消挂号（退号） |
| GET | /api/registration/patient-charges | 查询患者待缴费项目 |
| POST | /api/registration/charge | 收费 |
| POST | /api/registration/refund | 退费 |
| GET | /api/registration/expense-records | 查询患者费用记录 |

**挂号请求/响应示例**：
```json
// POST /api/registration/register
{
    "patientId": "P001",
    "departmentId": "D001",
    "physicianId": "PH001",
    "scheduleId": "S001",
    "visitDate": "2026-05-26",
    "visitTime": "09:00",
    "chiefComplaint": "头痛发热"
}

// Response
{
    "code": 200,
    "data": {
        "registrationId": "R001",
        "regNo": "20260526001",
        "status": "REGISTERED",
        "queueNo": 5,
        "estimatedWaitTime": 30
    }
}
```

---

### 5. physician-service (:8092)

**职责**：
- 门诊医生工作站
- 电子病历书写
- 开具处方
- 开具检查检验申请
- 查看患者历史就诊记录

**API接口**：

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /api/physician/patients | 获取当前医生待诊患者列表 |
| GET | /api/physician/patient-stats | 获取看诊统计 |
| POST | /api/physician/medical-record | 创建病历 |
| PUT | /api/physician/medical-record/{id} | 更新病历 |
| GET | /api/physician/medical-record | 获取病历详情（按registerId查询） |
| GET | /api/physician/medical-technologies | 查询医技项目列表 |
| POST | /api/physician/check-request | 提交检查申请 |
| POST | /api/physician/inspection-request | 提交检验申请 |
| POST | /api/physician/disposal-request | 提交处置申请 |
| GET | /api/physician/history-patients | 获取已看诊患者列表 |
| GET | /api/physician/check-results | 获取患者检查结果列表 |
| GET | /api/physician/inspection-results | 获取患者检验结果列表 |
| POST | /api/physician/diagnosis | 提交确诊结果 |
| GET | /api/physician/drugs | 查询药品列表 |
| GET | /api/physician/drugs/{id} | 获取药品详情 |
| POST | /api/physician/prescription | 开具处方 |
| DELETE | /api/physician/prescription/{id} | 删除处方药品 |

**接诊请求示例**：
```json
// POST /api/physician/emr
{
    "registrationId": "R001",
    "chiefComplaint": "头痛、发热3天",
    "presentIllness": "患者3天前受凉后出现头痛，伴发热，体温最高38.5度...",
    "historyPresent": "平素体健，否认高血压、糖尿病史...",
    "physicalExamination": "神志清楚，体温38.2度...",
    "preliminaryDiagnosis": ["J06.901 急性上呼吸道感染"],
    "aiDiagnosisAid": {}  // 可选的AI诊断辅助结果
}
```

**调用AI能力示例**（调用ai-gateway-service）：
```json
// POST http://localhost:8100/api/ai/diagnosis/suggest
{
    "symptoms": ["头痛", "发热", "咽痛"],
    "history": "3天前受凉，否认高血压糖尿病史",
    "physicalExam": "体温38.2度，咽部充血",
    "labResults": {}
}

// Response
{
    "code": 200,
    "data": {
        "suggestedDiagnoses": [
            {"code": "J06.901", "name": "急性上呼吸道感染", "confidence": 0.85},
            {"code": "J02.901", "name": "急性咽炎", "confidence": 0.72}
        ],
        "suggestedExams": ["血常规", "CRP", "咽拭子培养"]
    }
}
```

---

### 6. medtech-service (:8093)

**职责**：
- 检查检验项目管理
- 申请接收与预约
- 报告书写与发布
- 检查检验结果查询

**API接口**：

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /api/medtech/check/applications | 获取待检查患者列表 |
| GET | /api/medtech/check/stats | 获取待检查统计 |
| PUT | /api/medtech/check/start/{id} | 开始检查（登记） |
| GET | /api/medtech/check/completed | 获取已完成检查的患者列表 |
| PUT | /api/medtech/check/result/{id} | 录入检查结果 |
| GET | /api/medtech/inspection/applications | 获取待检验患者列表 |
| PUT | /api/medtech/inspection/start/{id} | 开始检验（登记） |
| GET | /api/medtech/inspection/completed | 获取已完成检验的患者列表 |
| PUT | /api/medtech/inspection/result/{id} | 录入检验结果 |
| GET | /api/medtech/disposal/applications | 获取待处置患者列表 |
| PUT | /api/medtech/disposal/start/{id} | 开始处置（登记） |
| GET | /api/medtech/disposal/completed | 获取已完成处置的患者列表 |
| PUT | /api/medtech/disposal/result/{id} | 录入处置结果 |

**报告书写请求示例**：
```json
// POST /api/medtech/report
{
    "orderId": "ORD001",
    "finding": "双肺纹理清晰，未见明显实质病变...",
    "impression": "胸部平片未见明显异常",
    "aiAnalysis": {}  // 可选的AI分析结果
}
```

---

### 7. pharmacy-service (:8094)

**职责**：
- 处方审核
- 处方发药
- 药品库存管理
- 用药指导

**API接口**：

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /api/pharmacy/drugs | 获取药品列表 |
| POST | /api/pharmacy/drugs | 新增药品 |
| PUT | /api/pharmacy/drugs/{id} | 更新药品信息 |
| DELETE | /api/pharmacy/drugs/{id} | 删除药品 |
| GET | /api/pharmacy/pending | 获取待发药患者列表 |
| PUT | /api/pharmacy/dispense/{registerId} | 确认发药 |
| PUT | /api/pharmacy/return/{registerId} | 退药 |
| GET | /api/pharmacy/transactions | 获取交易记录列表 |

**处方审核请求示例**：
```json
// POST /api/pharmacy/prescription/verify
{
    "prescriptionId": "RX001",
    "patientId": "P001",
    "diagnosis": "急性上呼吸道感染",
    "items": [
        {"drugId": "D001", "drugName": "阿莫西林胶囊", "dosage": "0.5g", "frequency": "tid", "duration": 5},
        {"drugId": "D002", "drugName": "布洛芬片", "dosage": "0.2g", "frequency": "tid", "duration": 3}
    ]
}

// Response
{
    "code": 200,
    "data": {
        "verified": true,
        "checkResult": "PASS",
        "warnings": [],
        "dispenseStatus": "READY"
    }
}
```

---

## Nacos 服务发现配置

### 1. 服务注册

每个服务通过 `bootstrap.yml` 配置自动注册到 Nacos：

```yaml
spring:
  application:
    name: gateway-service
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: dev
      config:
        server-addr: localhost:8848
        file-extension: yaml
```

**AI 服务额外配置**：
```yaml
spring:
  ai:
    openai:
      api-key: ${AI_API_KEY:your-api-key}
      base-url: ${AI_BASE_URL:https://api.openai.com}
```

### 2. 网关路由配置

网关使用 `lb://` 前缀进行服务名路由：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: lb://auth-service
          predicates:
            - Path=/api/auth/**
        - id: registration-service
          uri: lb://registration-service
          predicates:
            - Path=/api/registration/**
```

### 3. 服务间调用

使用 `@LoadBalanced` 的 RestTemplate 或 WebClient 进行服务调用：

**RestTemplate 配置**：
```java
@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**WebClient 配置**：
```java
@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    }
}
```

**服务调用示例**：
```java
@Service
public class AuthServiceClient {

    @Autowired
    private RestTemplate restTemplate;

    public UserInfo validateToken(String token) {
        // 使用服务名而非IP地址
        String url = "http://auth-service/api/auth/validate?token=" + token;
        ResponseEntity<Result> response = restTemplate.getForEntity(url, Result.class);
        // ...
    }
}
```

### 4. 公共配置

公共配置通过 `common.yaml` 共享：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/xikang_hospital
    username: postgres
    password: postgres
```

### 5. 启动 Nacos

本地开发需要启动 Nacos Server：

```bash
# 下载 Nacos Server
# 解压后运行
cd nacos/bin
./startup.sh -m standalone  # Linux/Mac
./startup.cmd -m standalone  # Windows
```

访问 Nacos 控制台：`http://localhost:8848/nacos`
默认账号密码：`nacos / nacos`

---

## 服务间调用规范

### 1. 调用auth-service验证身份

所有需要身份验证的业务服务，都应通过auth-service验证token。

**方式一：使用RestTemplate（同步调用）**

```java
@Service
public class AuthServiceClient {

    @Autowired
    private RestTemplate restTemplate;

    private static final String AUTH_SERVICE_URL = "http://localhost:8081";

    /**
     * 验证Token有效性
     * @param token JWT token
     * @return UserInfo 用户信息
     */
    public UserInfo validateToken(String token) {
        String url = AUTH_SERVICE_URL + "/api/auth/validate?token=" + token;
        try {
            ResponseEntity<Result> response = restTemplate.getForEntity(url, Result.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Result body = response.getBody();
                if (body.getCode() == 200) {
                    return JSON.parseObject(JSON.toJSONString(body.getData()), UserInfo.class);
                }
            }
        } catch (Exception e) {
            log.error("调用auth-service验证token失败", e);
        }
        return null;
    }

    /**
     * 获取用户信息
     * @param token JWT token
     * @return UserInfo
     */
    public UserInfo getUserInfo(String token) {
        String url = AUTH_SERVICE_URL + "/api/auth/userinfo";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Result> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Result.class
            );
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Result body = response.getBody();
                if (body.getCode() == 200) {
                    return JSON.parseObject(JSON.toJSONString(body.getData()), UserInfo.class);
                }
            }
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
        }
        return null;
    }
}
```

**方式二：使用WebClient（响应式，推荐）**

```java
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
            .baseUrl("http://localhost:8081")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}

@Service
public class AuthServiceClient {

    @Autowired
    private WebClient webClient;

    public Mono<UserInfo> validateToken(String token) {
        return webClient.get()
            .uri("/api/auth/validate?token={token}", token)
            .retrieve()
            .bodyToMono(Result.class)
            .map(result -> {
                if (result.getCode() == 200) {
                    return JSON.parseObject(JSON.toJSONString(result.getData()), UserInfo.class);
                }
                return null;
            })
            .onErrorReturn(null);
    }
}
```

---

### 2. 通过ai-gateway-service调用AI能力

所有AI能力必须通过ai-gateway-service统一入口调用。

**HTTP调用模板**：

```java
@Service
public class AiServiceClient {

    @Autowired
    private RestTemplate restTemplate;

    private static final String AI_GATEWAY_URL = "http://localhost:8100";

    /**
     * 调用AI导诊
     */
    public Map<String, Object> callAiTriage(Map<String, Object> request) {
        String url = AI_GATEWAY_URL + "/api/ai/triage/analyze";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Result> response = restTemplate.postForEntity(url, entity, Result.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody().getData();
            }
        } catch (Exception e) {
            log.error("调用AI导诊失败", e);
        }
        return null;
    }

    /**
     * 调用AI预问诊
     */
    public Map<String, Object> callAiConsult(Map<String, Object> request) {
        String url = AI_GATEWAY_URL + "/api/ai/consult/previsit";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Result> response = restTemplate.postForEntity(url, entity, Result.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody().getData();
            }
        } catch (Exception e) {
            log.error("调用AI预问诊失败", e);
        }
        return null;
    }

    /**
     * 调用AI诊断推荐
     */
    public Map<String, Object> callAiDiagnosis(Map<String, Object> request) {
        String url = AI_GATEWAY_URL + "/api/ai/diagnosis/suggest";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Result> response = restTemplate.postForEntity(url, entity, Result.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody().getData();
            }
        } catch (Exception e) {
            log.error("调用AI诊断失败", e);
        }
        return null;
    }

    /**
     * 调用AI处方审核
     */
    public Map<String, Object> callAiPharmacyReview(Map<String, Object> request) {
        String url = AI_GATEWAY_URL + "/api/ai/pharmacy/review";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Result> response = restTemplate.postForEntity(url, entity, Result.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody().getData();
            }
        } catch (Exception e) {
            log.error("调用AI处方审核失败", e);
        }
        return null;
    }
}
```

**调用示例（以physician-service为例）**：

```java
@Service
public class DiagnosisService {

    @Autowired
    private AiServiceClient aiServiceClient;

    /**
     * 获取AI诊断辅助
     */
    public DiagnosisAidResult getAiDiagnosisAid(DiagnosisAidRequest request) {
        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("symptoms", request.getSymptoms());
        aiRequest.put("history", request.getHistory());
        aiRequest.put("physicalExam", request.getPhysicalExam());
        aiRequest.put("labResults", request.getLabResults());

        Map<String, Object> aiResponse = aiServiceClient.callAiDiagnosis(aiRequest);
        if (aiResponse == null) {
            return DiagnosisAidResult.builder()
                .success(false)
                .message("AI服务调用失败")
                .build();
        }

        return DiagnosisAidResult.builder()
            .success(true)
            .suggestedDiagnoses((List<Map<String, Object>>) aiResponse.get("suggestedDiagnoses"))
            .suggestedExams((List<String>) aiResponse.get("suggestedExams"))
            .build();
    }
}
```

---

### 3. physician-service调用registration-service

门诊医生需要获取患者的挂号信息来开展诊疗工作。

**调用示例**：

```java
@Service
public class RegistrationServiceClient {

    @Autowired
    private RestTemplate restTemplate;

    private static final String REGISTRATION_SERVICE_URL = "http://localhost:8091";

    /**
     * 根据挂号ID获取挂号详情
     */
    public RegistrationInfo getRegistration(String regId) {
        String url = REGISTRATION_SERVICE_URL + "/api/registration/" + regId;
        try {
            ResponseEntity<Result> response = restTemplate.getForEntity(url, Result.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Result body = response.getBody();
                if (body.getCode() == 200) {
                    return JSON.parseObject(JSON.toJSONString(body.getData()), RegistrationInfo.class);
                }
            }
        } catch (Exception e) {
            log.error("获取挂号信息失败, regId={}", regId, e);
        }
        return null;
    }

    /**
     * 查询患者的所有挂号记录
     */
    public List<RegistrationInfo> getPatientRegistrations(String patientId) {
        String url = REGISTRATION_SERVICE_URL + "/api/registration/patient/" + patientId;
        try {
            ResponseEntity<Result> response = restTemplate.getForEntity(url, Result.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Result body = response.getBody();
                if (body.getCode() == 200) {
                    String jsonStr = JSON.toJSONString(body.getData());
                    return JSON.parseArray(jsonStr, RegistrationInfo.class);
                }
            }
        } catch (Exception e) {
            log.error("查询患者挂号记录失败, patientId={}", patientId, e);
        }
        return Collections.emptyList();
    }

    /**
     * 更新挂号状态（医生接诊后更新）
     */
    public boolean updateRegistrationStatus(String regId, String status) {
        String url = REGISTRATION_SERVICE_URL + "/api/registration/" + regId + "/status";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = new HashMap<>();
        body.put("status", status);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Result> response = restTemplate.exchange(
                url, HttpMethod.PUT, entity, Result.class
            );
            return response.getStatusCode() == HttpStatus.OK
                && response.getBody() != null
                && response.getBody().getCode() == 200;
        } catch (Exception e) {
            log.error("更新挂号状态失败, regId={}, status={}", regId, status, e);
        }
        return false;
    }
}
```

**在physician-service中的使用**：

```java
@Service
public class PhysicianServiceImpl implements PhysicianService {

    @Autowired
    private RegistrationServiceClient registrationServiceClient;

    @Autowired
    private AiServiceClient aiServiceClient;

    @Override
    public PatientVisitInfo startVisit(String regId, String physicianId) {
        // 1. 获取挂号信息
        RegistrationInfo registration = registrationServiceClient.getRegistration(regId);
        if (registration == null) {
            throw new BusinessException("挂号信息不存在");
        }

        // 2. 验证医生是否有权限接诊
        if (!registration.getPhysicianId().equals(physicianId)) {
            throw new BusinessException("无权接诊此患者");
        }

        // 3. 更新挂号状态为接诊中
        registrationServiceClient.updateRegistrationStatus(regId, "IN_TREATMENT");

        // 4. 获取患者基本信息，构建接诊信息
        PatientVisitInfo visitInfo = new PatientVisitInfo();
        visitInfo.setRegistration(registration);
        visitInfo.setPatient(getPatientInfo(registration.getPatientId()));
        visitInfo.setHistoryRecords(getHistoryRecords(registration.getPatientId()));

        return visitInfo;
    }
}
```

---

### 4. RestTemplate 配置类

```java
package com.xikang.common.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 服务通信配置 - 使用 Nacos 负载均衡
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

### 5. 服务调用工具类

```java
package com.xikang.common.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 服务间调用工具类
 * 使用 Nacos 服务名进行调用，无需硬编码地址
 */
@Component
public class ServiceCaller {

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 调用 auth-service 验证 Token
     */
    public Map<String, Object> validateToken(String token) {
        return restTemplate.getForObject(
            "http://auth-service/api/auth/validate?token={token}",
            Map.class,
            token
        );
    }

    /**
     * 调用 ai-gateway-service 的 AI 服务
     */
    public Map<String, Object> callAiTriage(Map<String, Object> request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        return restTemplate.postForObject(
            "http://ai-gateway-service/api/ai/triage/analyze",
            entity,
            Map.class
        );
    }

    /**
     * 调用 registration-service 查询挂号信息
     */
    public Map<String, Object> getRegistration(Long registerId) {
        return restTemplate.getForObject(
            "http://registration-service/api/registration/register/{id}",
            Map.class,
            registerId
        );
    }
}
```

---

## 数据库表归属

### 数据库表归属总览

#### 公共表（各服务只读访问，通过API调用）

| 表名 | 说明 | 管理服务 |
|------|------|----------|
| department | 科室表 | registration-service |
| scheduling | 排班表 | registration-service |

#### 按服务划分的表

| 服务 | 拥有的表 | 说明 |
|------|----------|------|
| auth-service | employee | 员工信息 |
| registration-service | register, regist_level, settle_category | 挂号相关 |
| physician-service | medical_record, medical_record_disease, disease, prescription | 病历、疾病、处方 |
| medtech-service | check_request, inspection_request, disposal_request, medical_technology | 检查/检验/处置（读写） |
| pharmacy-service | drug_info, ai_follow_up_plan, ai_follow_up_record | 药品、随访 |
| ai-triage-service | ai_triage_record | AI导诊记录 |
| ai-consult-service | ai_consultation_record, ai_medical_record_log | AI问诊记录 |
| ai-diagnosis-service | ai_exam_suggestion, ai_exam_analysis, ai_diagnosis_suggestion | AI诊断记录 |
| ai-pharmacy-service | ai_prescription_review | AI处方审核 |

---

### 重要说明

#### 1. 检查/检验/处置申请单的特殊处理

`check_request`（检查申请）、`inspection_request`（检验申请）、`disposal_request`（处置申请）三张表的数据流转：

- **INSERT**：由 `physician-service` 创建（医生开具申请）
- **UPDATE**：由 `medtech-service` 执行和结果录入（医技科室完成检查后填写报告）

```java
// physician-service 创建检查申请
@PostMapping("/api/physician/order")
public Result<String> createCheckRequest(@RequestBody CheckRequest request) {
    // INSERT into check_request
    return Result.success(checkRequestService.create(request));
}

// medtech-service 更新检查结果
@PostMapping("/api/medtech/report")
public Result<String> submitReport(@RequestBody ReportRequest request) {
    // UPDATE check_request SET status='COMPLETED', finding=...
    return Result.success(reportService.submit(request));
}
```

#### 2. 处方表访问规则

`prescription` 表的特殊说明：

- **INSERT/UPDATE**：由 `physician-service` 写（医生开具处方）
- **SELECT**：由 `pharmacy-service` 只读（药房审核发药）

```java
// physician-service 开具处方
@PostMapping("/api/physician/prescription")
public Result<String> createPrescription(@RequestBody PrescriptionRequest request) {
    // INSERT into prescription
    return Result.success(prescriptionService.create(request));
}

// pharmacy-service 只读查询处方
@GetMapping("/api/pharmacy/prescription/{prescriptionId}")
public Result<Prescription> getPrescription(@PathVariable String prescriptionId) {
    // SELECT * FROM prescription WHERE id = ?
    return Result.success(prescriptionService.getById(prescriptionId));
}
```

#### 3. 服务间访问规则

**各服务通过 HTTP API 访问其他服务的表，不直接跨库访问。**

示例：medtech-service 需要获取检查申请信息时，通过 API 调用 physician-service：

```java
// medtech-service 通过 API 获取检查申请
@GetMapping("/api/medtech/order/{orderId}")
public Result<CheckRequest> getOrder(@PathVariable String orderId) {
    // 调用 physician-service 获取申请详情
    return Result.success(restTemplate.getForObject(
        "http://physician-service/api/physician/order/" + orderId,
        CheckRequest.class
    ));
}
```

---

### auth-service (8081)

| 表名 | 说明 |
|------|------|
| employee | 员工信息表 |

### registration-service (8091)

| 表名 | 说明 |
|------|------|
| register | 挂号记录表 |
| regist_level | 挂号级别表 |
| settle_category | 结算类别表 |

### physician-service (8092)

| 表名 | 说明 |
|------|------|
| medical_record | 电子病历表 |
| medical_record_disease | 病历诊断关联表 |
| disease | 疾病字典表 |
| prescription | 处方表 |

### medtech-service (8093)

| 表名 | 说明 |
|------|------|
| check_request | 检查申请单表 |
| inspection_request | 检验申请单表 |
| disposal_request | 处置申请单表 |
| medical_technology | 医疗技术项目表 |

### pharmacy-service (8094)

| 表名 | 说明 |
|------|------|
| drug_info | 药品信息表 |
| ai_follow_up_plan | AI随访计划表 |
| ai_follow_up_record | AI随访记录表 |

---

## 共享表访问说明

### 跨服务共享表访问规则

系统中存在部分表需要被多个服务访问，采用"共享表 + 直接SQL访问（同一数据库）"的方式实现，无需通过API调用获取。

#### 1. 检查/检验/处置申请单（check_request / inspection_request / disposal_request）

| 操作 | 服务 | 说明 |
|------|------|------|
| INSERT | physician-service | 医生开具检查/检验/处置申请时创建 |
| SELECT | medtech-service | 医技科室查询待执行的申请 |
| UPDATE | medtech-service | 医技科室录入检查/检验结果 |

```java
// physician-service 创建检查申请
@PostMapping("/api/physician/check-request")
public Result<String> createCheckRequest(@RequestBody CheckRequest request) {
    // INSERT into check_request
    return Result.success(checkRequestService.create(request));
}

// medtech-service 更新检查结果
@PutMapping("/api/medtech/check/result/{id}")
public Result<String> submitCheckResult(@PathVariable Long id, @RequestBody CheckResultRequest request) {
    // UPDATE check_request SET check_result=..., check_state='COMPLETED' WHERE id=?
    return Result.success(checkResultService.update(id, request));
}
```

#### 2. 处方表（prescription）

| 操作 | 服务 | 说明 |
|------|------|------|
| INSERT | physician-service | 医生开立处方时创建 |
| SELECT | pharmacy-service | 药房审核发药时读取处方详情 |

```java
// physician-service 开具处方
@PostMapping("/api/physician/prescription")
public Result<String> createPrescription(@RequestBody PrescriptionRequest request) {
    // INSERT into prescription
    return Result.success(prescriptionService.create(request));
}

// pharmacy-service 读取处方（发药时）
@GetMapping("/api/pharmacy/pending")
public Result<List<PendingPrescription>> getPendingPrescriptions() {
    // SELECT p.*, r.case_number, r.real_name
    // FROM prescription p
    // JOIN register r ON p.register_id = r.id
    // WHERE p.prescription_state = 'PENDING'
    return Result.success(prescriptionService.getPendingList());
}
```

#### 3. 访问方式说明

共享表通过以下方式访问：
- **数据库视图**：可创建视图提供统一的数据访问接口
- **直接SQL**：各服务直接访问同一数据库的共享表
- **统一事务**：涉及多表的业务操作在服务内部完成，不跨服务分布式事务

> **注意**：共享表的INSERT和UPDATE操作分别由不同服务负责，通过业务约定协调，不引入分布式事务。

---

## 公共表访问约定

### 1. department (科室表)

科室信息由 registration-service 管理，其他服务如需使用，应通过 API 调用获取，严禁直接操作数据库。

```java
// 获取科室列表
@GetMapping("/api/registration/departments")
public Result<List<Department>> getDepartments() {
    return Result.success(departmentService.list());
}

// 获取科室详情
@GetMapping("/api/registration/departments/{deptId}")
public Result<Department> getDepartment(@PathVariable String deptId) {
    return Result.success(departmentService.getById(deptId));
}
```

### 2. scheduling (排班表)

排班信息由 registration-service 管理，医生排班、号源管理均在此服务。

```java
// 查询科室排班
@GetMapping("/api/registration/schedules")
public Result<List<Schedule>> getSchedules(
    @RequestParam String departmentId,
    @RequestParam String date) {
    return Result.success(scheduleService.getByDepartmentAndDate(departmentId, date));
}

// 预约号源
@PostMapping("/api/registration/schedules/{scheduleId}/reserve")
public Result<Reservation> reserveSchedule(
    @PathVariable String scheduleId,
    @RequestBody ReservationRequest request) {
    return Result.success(scheduleService.reserve(scheduleId, request));
}
```

---

## HTTP调用示例代码

### 使用RestTemplate调用其他服务

**配置类**：

```java
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }
}
```

**服务调用工具类**：

```java
@Component
public class ServiceCaller {

    @Autowired
    private RestTemplate restTemplate;

    /**
     * GET请求
     */
    public <T> T get(String url, Class<T> responseType, Object... uriVariables) {
        try {
            ResponseEntity<Result> response = restTemplate.getForEntity(url, Result.class, uriVariables);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Result body = response.getBody();
                if (body.getCode() == 200) {
                    return JSON.parseObject(JSON.toJSONString(body.getData()), responseType);
                }
            }
        } catch (Exception e) {
            log.error("GET请求失败: {}", url, e);
        }
        return null;
    }

    /**
     * POST请求
     */
    public <T, R> T post(String url, R request, Class<T> responseType) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<R> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Result> response = restTemplate.postForEntity(url, entity, Result.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Result body = response.getBody();
                if (body.getCode() == 200) {
                    return JSON.parseObject(JSON.toJSONString(body.getData()), responseType);
                }
            }
        } catch (Exception e) {
            log.error("POST请求失败: {}", url, e);
        }
        return null;
    }

    /**
     * PUT请求
     */
    public <T, R> boolean put(String url, R request, Class<T> responseType) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<R> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Result> response = restTemplate.exchange(
                url, HttpMethod.PUT, entity, Result.class
            );
            return response.getStatusCode() == HttpStatus.OK
                && response.getBody() != null
                && response.getBody().getCode() == 200;
        } catch (Exception e) {
            log.error("PUT请求失败: {}", url, e);
        }
        return false;
    }

    /**
     * DELETE请求
     */
    public boolean delete(String url, Object... uriVariables) {
        try {
            ResponseEntity<Result> response = restTemplate.exchange(
                url, HttpMethod.DELETE, null, Result.class, uriVariables
            );
            return response.getStatusCode() == HttpStatus.OK
                && response.getBody() != null
                && response.getBody().getCode() == 200;
        } catch (Exception e) {
            log.error("DELETE请求失败: {}", url, e);
        }
        return false;
    }
}
```

### 使用WebClient调用其他服务

**配置类**：

```java
@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    }
}
```

**服务调用示例**：

```java
@Service
public class AsyncServiceCaller {

    @Autowired
    private WebClient.Builder webClientBuilder;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        this.webClient = webClientBuilder
            .baseUrl("http://localhost:8091")
            .build();
    }

    /**
     * 异步POST请求
     */
    public Mono<Map> callAiService(Map<String, Object> request) {
        return webClient.post()
            .uri("/api/ai/diagnosis/suggest")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(Result.class)
            .map(result -> {
                if (result.getCode() == 200) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) result.getData();
                    return data;
                }
                return Collections.emptyMap();
            })
            .onErrorResume(e -> {
                log.error("AI服务调用失败", e);
                return Mono.just(Collections.emptyMap());
            });
    }
}
```

---

## 接口联调说明

### 与AI服务联调

#### 1. 接口格式约定

所有AI服务接口统一使用以下响应格式：

```json
{
    "code": 200,
    "message": "success",
    "data": {
        // 业务数据
    }
}
```

错误响应格式：
```json
{
    "code": 500,
    "message": "AI服务调用失败: xxx",
    "data": null
}
```

#### 2. 联调步骤

**步骤1：验证AI服务可用性**

```bash
# 检查ai-gateway-service健康状态
curl http://localhost:8100/actuator/health

# 检查各AI服务健康状态
curl http://localhost:8101/actuator/health
curl http://localhost:8102/actuator/health
curl http://localhost:8103/actuator/health
curl http://localhost:8104/actuator/health
```

**步骤2：手动测试AI接口**

```bash
# 测试AI导诊
curl -X POST http://localhost:8100/api/ai/triage/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "symptoms": ["头痛", "发热", "咽痛"],
    "age": 35,
    "gender": "M"
  }'

# 测试AI诊断
curl -X POST http://localhost:8100/api/ai/diagnosis/suggest \
  -H "Content-Type: application/json" \
  -d '{
    "symptoms": ["头痛", "发热", "咽痛"],
    "history": "3天前受凉",
    "physicalExam": "体温38.2度，咽部充血",
    "labResults": {}
  }'
```

**步骤3：在业务服务中集成调用**

```java
@Service
@Slf4j
public class DiagnosisIntegrationService {

    @Autowired
    private AiServiceClient aiServiceClient;

    /**
     * 诊断辅助
     * @param patientInfo 患者信息
     * @param symptoms 主诉症状
     * @return AI诊断建议
     */
    public DiagnosisSuggestion assistDiagnosis(PatientInfo patientInfo, List<String> symptoms) {
        // 构建请求
        Map<String, Object> request = new HashMap<>();
        request.put("patientId", patientInfo.getPatientId());
        request.put("age", patientInfo.getAge());
        request.put("gender", patientInfo.getGender());
        request.put("symptoms", symptoms);

        try {
            // 调用AI服务
            Map<String, Object> response = aiServiceClient.callAiDiagnosis(request);
            if (response == null || response.isEmpty()) {
                log.warn("AI诊断返回为空");
                return DiagnosisSuggestion.empty();
            }

            // 解析响应
            return DiagnosisSuggestion.builder()
                .success(true)
                .diagnoses(parseDiagnoses(response))
                .exams(parseExams(response))
                .build();

        } catch (Exception e) {
            log.error("AI诊断辅助失败", e);
            return DiagnosisSuggestion.builder()
                .success(false)
                .message("AI服务暂时不可用")
                .build();
        }
    }
}
```

#### 3. 调试方法

**启用详细日志**：

```yaml
logging:
  level:
    com.xikang: DEBUG
    org.springframework.web.client: DEBUG
```

**使用tracing ID追踪请求**：

```java
// 在gateway添加traceId
public class TraceFilter implements GlobalFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = UUID.randomUUID().toString();
        exchange.getAttributes().put("traceId", traceId);
        exchange.getResponse().getHeaders().add("X-Trace-Id", traceId);
        return chain.filter(exchange);
    }
}
```

---

## 开发顺序建议

### 推荐开发顺序

```
第一阶段：基础设施（1-2周）
├── 1. common 模块
│   ├── 定义Result统一响应
│   ├── 定义Constants常量
│   ├── 定义BaseEntity基类
│   ├── 异常处理配置
│   └── 通用工具类
│
├── 2. auth-service (:8081)
│   ├── 用户登录/登出
│   ├── Token管理
│   ├── 权限验证
│   └── 用户CRUD
│
└── 3. gateway-service (:8080)
    ├── 路由配置
    ├── 鉴权过滤器（调用auth-service）
    └── 全局异常处理

第二阶段：核心业务服务（3-4周）
├── 4. registration-service (:8091)
│   ├── 患者管理
│   ├── 挂号管理
│   ├── 收费管理
│   └── 发票管理
│
├── 5. physician-service (:8092)
│   ├── 接诊队列
│   ├── 病历书写
│   ├── 处方开具
│   ├── 检查申请
│   └── AI能力集成
│
├── 6. medtech-service (:8093)
│   ├── 检查项目管理
│   ├── 报告管理
│   └── AI能力集成
│
└── 7. pharmacy-service (:8094)
    ├── 处方审核
    ├── 发药管理
    └── AI能力集成

第三阶段：联调测试（1周）
├── 服务间接口联调
├── 性能测试
└── 文档完善
```

### 注意事项

1. **先独立后集成**：每个服务先完成自身功能，再进行服务间调用集成
2. **接口先行**：在开发联调前，先定义好接口契约（接口文档）
3. **公共表只读**：如需访问公共表，通过API调用，禁止直接操作数据库
4. **异常处理**：服务间调用要做好异常处理，避免级联失败
5. **配置外置**：数据库配置、URL配置要放到application.yml中，避免硬编码
