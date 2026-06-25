# 熙康云医院 - 微服务框架文档

> 版本：v1.0
> 日期：2026-05-25
> 状态：框架搭建完成

---

## 1. 项目概述

本项目是基于 Spring Boot 3.2.4 + JDK 17 的微服务架构医院管理系统，采用 Maven 多模块结构，包含 12 个微服务。

---

## 2. 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | LTS 版本 |
| Spring Boot | 3.2.4 | 应用框架 |
| Spring Cloud | 2023.0.0 | 微服务生态 |
| MyBatis | 3.5.16 | ORM 框架 |
| PostgreSQL | 42.7.3 | 数据库驱动 |
| JWT | 0.12.5 | 认证 Token |
| Spring AI | 1.0.0 | AI 能力集成 |

---

## 3. 项目结构

```
xikang-cloud-hospital/                    # 父工程 (pom)
├── pom.xml                               # 统一依赖版本管理
│
├── common/                               # 公共模块 (jar)
│   ├── pom.xml
│   └── src/main/java/com/xikang/common/
│       ├── result/                       # 统一响应封装 (Result<T>)
│       │   └── Result.java              # 返回格式: {code, message, data}
│       ├── exception/                    # 全局异常处理
│       │   ├── GlobalExceptionHandler.java
│       │   └── BusinessException.java
│       ├── utils/                        # 工具类
│       │   ├── JwtUtils.java             # JWT 签发/解析
│       │   └── StringUtils.java
│       └── constants/                    # 常量定义
│           └── HttpStatus.java           # 状态码常量 (200/400/401/403/500)
│
├── gateway-service/                      # API 网关 (:8080)
│   ├── pom.xml
│   └── src/main/java/com/xikang/gateway/
│       ├── GatewayApplication.java      # 启动类
│       ├── config/
│       │   └── RouteConfig.java         # 路由配置 (路由到各微服务)
│       └── filter/
│           └── JwtAuthFilter.java       # JWT 鉴权过滤器
│   └── src/main/resources/
│       └── application.yml               # 路由规则、限流、跨域配置
│
├── auth-service/                         # 认证鉴权服务 (:8081)
│   ├── pom.xml
│   └── src/main/java/com/xikang/auth/
│       ├── AuthApplication.java         # 启动类
│       ├── controller/
│       │   └── AuthController.java      # 登录/登出/Token刷新
│       ├── service/
│       │   └── AuthService.java         # 认证业务逻辑
│       ├── mapper/
│       │   └── EmployeeMapper.java      # 员工数据访问
│       └── entity/
│           └── Employee.java             # 员工实体
│   └── src/main/resources/
│       ├── application.yml               # JWT秘钥、过期时间配置
│       └── mapper/                       # MyBatis XML (EmployeeMapper.xml)
│
├── registration-service/                 # 挂号收费服务 (:8091)
│   ├── pom.xml
│   └── src/main/java/com/xikang/registration/
│       ├── RegistrationApplication.java # 启动类
│       ├── controller/                   # REST API (挂号/退号/收费/退费)
│       ├── service/                      # 业务逻辑
│       ├── mapper/                       # 数据访问
│       └── entity/                       # 实体类 (Register, SettleCategory等)
│   └── src/main/resources/
│       ├── application.yml
│       └── mapper/                       # MyBatis XML
│
├── physician-service/                    # 门诊医生服务 (:8092)
│   ├── pom.xml
│   └── src/main/java/com/xikang/physician/
│       ├── PhysicianApplication.java    # 启动类
│       ├── controller/                   # REST API (病历/处方/检查申请)
│       ├── service/
│       ├── mapper/
│       └── entity/                       # 实体类 (MedicalRecord, Prescription等)
│   └── src/main/resources/
│       ├── application.yml
│       └── mapper/
│
├── medtech-service/                      # 医技检查服务 (:8093)
│   ├── pom.xml
│   └── src/main/java/com/xikang/medtech/
│       ├── MedtechApplication.java      # 启动类
│       ├── controller/                   # REST API (检查/检验/处置执行)
│       ├── service/
│       ├── mapper/
│       └── entity/                       # 实体类 (CheckRequest, InspectionRequest等)
│   └── src/main/resources/
│       ├── application.yml
│       └── mapper/
│
├── pharmacy-service/                     # 药房管理服务 (:8094)
│   ├── pom.xml
│   └── src/main/java/com/xikang/pharmacy/
│       ├── PharmacyApplication.java      # 启动类
│       ├── controller/                   # REST API (发药/退药/药库管理)
│       ├── service/
│       ├── mapper/
│       └── entity/                       # 实体类 (DrugInfo, Prescription等)
│   └── src/main/resources/
│       ├── application.yml
│       └── mapper/
│
├── ai-gateway-service/                   # AI 服务内部网关 (:8100)
│   ├── pom.xml
│   └── src/main/java/com/xikang/ai/gateway/
│       ├── AiGatewayApplication.java    # 启动类
│       ├── controller/                   # AI 统一调用入口
│       │   └── AiController.java       # 路由到各 AI 子服务
│       ├── service/
│       │   └── AiCallService.java       # Spring AI ChatClient 封装
│       │                                   # 统一调用入口、超时控制、重试、日志
│       └── config/
│           └── SpringAiConfig.java       # AI 模型配置 (API Key, Base URL)
│   └── src/main/resources/
│       ├── application.yml               # AI 模型配置 (通义千问/智谱)
│       └── prompts/                      # Prompt 模板目录
│
├── ai-triage-service/                    # AI 导诊服务 (:8101)
│   ├── pom.xml
│   └── src/main/java/com/xikang/ai/triage/
│       ├── AiTriageApplication.java     # 启动类
│       ├── controller/
│       │   └── AiTriageController.java # /api/ai/triage/analyze
│       ├── service/
│       │   └── AiTriageService.java     # 导诊业务逻辑
│       ├── mapper/
│       └── entity/
│           └── AiTriageRecord.java       # AI 导诊记录
│   └── src/main/resources/
│       ├── application.yml
│       ├── mapper/
│       └── prompts/
│           └── triage_prompt.st          # 导诊提示词模板
│
├── ai-consult-service/                   # AI 问诊服务 (:8102)
│   ├── pom.xml
│   └── src/main/java/com/xikang/ai/consult/
│       ├── AiConsultApplication.java    # 启动类
│       ├── controller/
│       │   └── AiConsultController.java # /api/ai/consult/chat, /summary
│       ├── service/
│       │   └── AiConsultService.java    # 预问诊/病历生成
│       ├── mapper/
│       └── entity/
│           └── AiConsultRecord.java      # AI 问诊记录
│   └── src/main/resources/
│       ├── application.yml
│       ├── mapper/
│       └── prompts/
│           ├── consultation_prompt.st    # 预问诊模板
│           └── medical_record_prompt.st  # 病历生成模板
│
├── ai-diagnosis-service/                 # AI 诊断服务 (:8103)
│   ├── pom.xml
│   └── src/main/java/com/xikang/ai/diagnosis/
│       ├── AiDiagnosisApplication.java  # 启动类
│       ├── controller/
│       │   └── AiDiagnosisController.java # /api/ai/diagnosis/analyze, /exam-analyze
│       ├── service/
│       │   └── AiDiagnosisService.java  # 辅助诊断/检查分析
│       ├── mapper/
│       └── entity/
│           └── AiDiagnosisSuggestion.java
│   && src/main/resources/
│       ├── application.yml
│       ├── mapper/
│       └── prompts/
│           ├── diagnosis_prompt.st       # 诊断推荐模板
│           └── exam_analysis_prompt.st   # 检查分析模板
│
└── ai-pharmacy-service/                  # AI 药学服务 (:8104)
    ├── pom.xml
    └── src/main/java/com/xikang/ai/pharmacy/
        ├── AiPharmacyApplication.java   # 启动类
        ├── controller/
        │   └── AiPharmacyController.java # /api/ai/pharmacy/review, /follow-up-plan
        ├── service/
        │   └── AiPharmacyService.java   # 处方审核/用药随访
        ├── mapper/
        └── entity/
            └── AiPrescriptionReview.java
    └── src/main/resources/
        ├── application.yml
        ├── mapper/
        └── prompts/
            ├── prescription_review_prompt.st  # 处方审核模板
            └── follow_up_prompt.st            # 随访计划模板
```

---

## 4. 模块职责说明

### 4.1 基础设施服务

| 服务 | 端口 | 职责 |
|------|------|------|
| `gateway-service` | 8080 | 路由转发、JWT 鉴权、限流熔断、统一跨域 |
| `auth-service` | 8081 | 用户登录/登出、JWT 签发与校验、角色权限管理 |

### 4.2 核心业务服务

| 服务 | 端口 | 职责 | 核心能力 |
|------|------|------|----------|
| `registration-service` | 8091 | 挂号收费 | 窗口挂号、退号、收费结算、退费、费用查询、日结管理 |
| `physician-service` | 8092 | 门诊医生 | 患者查看、病历管理、检查/检验申请、门诊确诊、处方开立 |
| `medtech-service` | 8093 | 医技检查 | 检查管理、检验管理、处置管理、结果录入 |
| `pharmacy-service` | 8094 | 药房管理 | 药品信息管理、发药管理、退药管理、交易记录 |

### 4.3 AI 智能服务

| 服务 | 端口 | 职责 | 核心能力 |
|------|------|------|----------|
| `ai-gateway-service` | 8100 | AI 统一入口 | Prompt 模板管理、大模型调用封装、日志计费 |
| `ai-triage-service` | 8101 | AI 导诊 | 智能导诊、科室推荐、风险评估 |
| `ai-consult-service` | 8102 | AI 问诊 | 预问诊对话、问诊摘要、病历草稿生成 |
| `ai-diagnosis-service` | 8103 | AI 诊断 | 辅助诊断(疾病+ICD)、检查结果分析 |
| `ai-pharmacy-service` | 8104 | AI 药学 | 处方审核(药物冲突/过敏)、用药随访计划 |

---

## 5. 各层文件夹说明

### 5.1 Controller 层 (`controller/`)

- 接收 HTTP 请求，参数校验
- 调用 Service 层处理业务
- 返回统一响应格式 `Result<T>`

```java
@RestController
@RequestMapping("/api/xxx")
public class XxxController {

    @PostMapping("/save")
    public Result<Long> save(@RequestBody @Valid XxxRequest request) {
        Long id = xxxService.save(request);
        return Result.success(id);
    }
}
```

### 5.2 Service 层 (`service/`)

- 业务逻辑处理
- 事务管理
- 调用 Mapper 层操作数据库

```java
@Service
public class XxxService {

    @Transactional
    public Long save(XxxRequest request) {
        // 业务逻辑
        return xxxMapper.insert(entity);
    }
}
```

### 5.3 Mapper 层 (`mapper/`)

- 数据访问层
- MyBatis XML 或 Mapper 接口

```java
@Mapper
public interface XxxMapper {
    void insert(XxxEntity entity);
    XxxEntity selectById(Long id);
    List<XxxEntity> selectList(XxxQuery query);
}
```

### 5.4 Entity 层 (`entity/`)

- 数据库表对应的 Java 对象
- 与数据库表结构一一对应

```java
@Data
@TableName("xxx")
public class XxxEntity {
    private Long id;
    private String name;
    private LocalDateTime createTime;
}
```

### 5.5 Config 层 (`config/`)

- Spring 配置类
- 路由规则、CORS、Security 等

---

## 6. 统一响应格式

```json
{
    "code": 200,
    "message": "操作成功",
    "data": { ... }
}
```

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务异常 |
| 503 | AI 服务不可用 |

---

## 7. 端口分配

| 端口 | 服务 |
|------|------|
| 8080 | gateway-service |
| 8081 | auth-service |
| 8091 | registration-service |
| 8092 | physician-service |
| 8093 | medtech-service |
| 8094 | pharmacy-service |
| 8100 | ai-gateway-service |
| 8101 | ai-triage-service |
| 8102 | ai-consult-service |
| 8103 | ai-diagnosis-service |
| 8104 | ai-pharmacy-service |

---

## 8. 服务启动顺序

```
1. PostgreSQL 数据库       (5432)
2. auth-service           (8081)  ← 其他服务依赖
3. gateway-service        (8080)  ← 前端请求入口
4. registration-service   (8091)
5. physician-service     (8092)
6. medtech-service        (8093)
7. pharmacy-service      (8094)
8. ai-gateway-service    (8100)  ← AI 服务入口
9. ai-triage-service     (8101)
10. ai-consult-service   (8102)
11. ai-diagnosis-service (8103)
12. ai-pharmacy-service  (8104)
```

---

## 9. 常用命令

```bash
# 编译整个项目
mvn clean compile

# 安装到本地仓库
mvn clean install

# 跳过测试
mvn clean install -DskipTests

# 指定模块编译
mvn clean compile -pl registration-service -am

# 从指定模块恢复编译
mvn clean compile -rf :registration-service
```

---

## 10. 注意事项

1. **AI 服务业务实现**: AI 服务的 Service 层方法仅为占位实现，具体业务逻辑由 AI 模块开发人员完成
2. **数据库配置**: 各服务的 `application.yml` 中的数据库配置需要根据实际环境修改
3. **JWT 密钥**: `auth-service` 的 JWT 密钥需要配置生产环境的随机字符串
4. **AI API Key**: `ai-gateway-service` 需要配置通义千问或智谱的 API Key
