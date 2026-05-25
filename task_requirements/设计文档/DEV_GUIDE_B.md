# 人员B - AI服务开发指南

## 开发概述

人员B负责所有AI服务的开发，包括AI统一入口网关和各AI业务服务。

### 负责模块清单

| 服务 | 端口 | 职责 |
|------|------|------|
| ai-gateway-service | 8100 | AI统一入口，Prompt管理，ChatClient封装 |
| ai-triage-service | 8101 | AI导诊，症状分析，科室推荐 |
| ai-consult-service | 8102 | AI预问诊，病历生成 |
| ai-diagnosis-service | 8103 | AI诊断推荐，检查检验建议 |
| ai-pharmacy-service | 8104 | AI处方审核，用药指导 |

---

## 服务架构

### AI服务整体架构

```
                                    ┌─────────────────┐
                                    │  Spring AI      │
                                    │  ChatClient     │
                                    └────────┬────────┘
                                             │
┌──────────┐    ┌──────────────┐    ┌───────┴────────┐    ┌────────────────┐
│ Gateway  │───>│ai-gateway    │───>│ ai-triage      │    │                │
│ Service  │    │service       │    │ service        │    │   AI Model     │
│ (:8080)  │    │   (:8100)    │    │    (:8101)     │    │   Provider     │
└──────────┘    └──────────────┘    └────────────────┘    │   (Claude/     │
                     │                     │              │    GPT-4)      │
                     │                     │              └────────────────┘
                     │                     │
                     │              ┌───────┴────────┐
                     │              │ ai-consult     │
                     │              │ service        │
                     │              │    (:8102)     │
                     │              └────────────────┘
                     │
                     │              ┌────────────────┐
                     │              │ ai-diagnosis   │
                     │              │ service        │
                     │              │    (:8103)     │
                     │              └────────────────┘
                     │
                     │              ┌────────────────┐
                     └─────────────>│ ai-pharmacy    │
                                    │ service        │
                                    │    (:8104)     │
                                    └────────────────┘
```

### 调用流程

1. **业务服务**（如physician-service）通过HTTP调用ai-gateway-service
2. **ai-gateway-service**统一处理Prompt模板、日志记录、错误处理
3. **ai-gateway-service**调用具体的AI业务服务
4. **AI业务服务**使用Spring AI的ChatClient调用AI模型
5. **AI业务服务**返回结构化结果给ai-gateway-service
6. **ai-gateway-service**统一格式化后返回给业务服务

---

## ai-gateway-service职责

### 核心功能

1. **统一入口**：所有AI能力通过此服务接入
2. **Prompt管理**：集中管理所有Prompt模板
3. **ChatClient封装**：封装Spring AI的ChatClient，统一配置
4. **日志记录**：记录所有AI请求和响应
5. **限流熔断**：保护下游AI服务
6. **结果统一格式化**：所有响应使用统一格式

### 项目结构

```
ai-gateway-service/
├── src/main/java/com/xikang/ai/gateway/
│   ├── AiGatewayApplication.java
│   ├── config/
│   │   ├── ChatClientConfig.java       # ChatClient配置
│   │   └── AiProperties.java           # AI配置属性
│   ├── controller/
│   │   ├── TriageController.java       # 导诊入口
│   │   ├── ConsultController.java      # 问诊入口
│   │   ├── DiagnosisController.java    # 诊断入口
│   │   └── PharmacyController.java     # 药学入口
│   ├── service/
│   │   ├── AiCallService.java          # AI调用服务
│   │   ├── PromptTemplateService.java   # Prompt模板服务
│   │   └── AiResponseFormatter.java    # 响应格式化
│   └── model/
│       ├── AiRequest.java               # 统一请求
│       └── AiResponse.java              # 统一响应
```

### ChatClient封装示例

```java
@Configuration
public class ChatClientConfig {

    @Value("${spring.ai.api-key}")
    private String apiKey;

    @Value("${spring.ai.base-url}")
    private String baseUrl;

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
            .defaultSystem("你是一个专业的医疗AI助手，请根据提供的信息给出准确的建议。")
            .build();
    }
}
```

```java
@Service
@Slf4j
public class AiCallService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public AiCallService(ChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 通用AI调用方法
     * @param prompt 提示词
     * @return AI响应内容
     */
    public String call(String prompt) {
        log.info("AI调用请求: {}", prompt);
        try {
            String response = chatClient.call(prompt);
            log.info("AI调用响应: {}", response);
            return response;
        } catch (Exception e) {
            log.error("AI调用失败", e);
            throw new AiCallException("AI服务调用失败: " + e.getMessage());
        }
    }

    /**
     * 带参数的AI调用
     * @param template 模板
     * @param params 参数
     * @return AI响应
     */
    public String callWithTemplate(String template, Map<String, Object> params) {
        PromptTemplate promptTemplate = new PromptTemplate(template);
        promptTemplate.setTemplateVariables(params);
        Prompt prompt = promptTemplate.create();

        log.info("AI调用请求(模板): {}", prompt.getContents());
        try {
            String response = chatClient.call(prompt);
            log.info("AI调用响应: {}", response);
            return response;
        } catch (Exception e) {
            log.error("AI模板调用失败", e);
            throw new AiCallException("AI服务调用失败: " + e.getMessage());
        }
    }

    /**
     * 结构化输出调用
     * @param prompt 提示词
     * @param responseType 响应类型
     * @return 解析后的响应对象
     */
    public <T> T callForObject(String prompt, Class<T> responseType) {
        String response = call(prompt);
        try {
            // 尝试JSON解析
            if (response.trim().startsWith("{")) {
                return objectMapper.readValue(response, responseType);
            }
            // 如果不是JSON，包装为简单对象
            return parseNonJsonResponse(response, responseType);
        } catch (Exception e) {
            log.error("响应解析失败", e);
            throw new AiCallException("响应解析失败: " + e.getMessage());
        }
    }

    private <T> T parseNonJsonResponse(String response, Class<T> responseType) {
        // 处理非JSON格式响应的逻辑
        throw new UnsupportedOperationException("请实现非JSON响应解析");
    }
}
```

### Prompt模板管理

```java
@Service
public class PromptTemplateService {

    // Prompt模板定义
    private static final Map<String, String> PROMPT_TEMPLATES = new HashMap<>();

    static {
        // 导诊Prompt
        PROMPT_TEMPLATES.put("triage",
            "你是一个专业的医院导诊助手。根据患者描述的症状：{symptoms}\n" +
            "患者年龄：{age}岁，性别：{gender}\n" +
            "请分析可能的疾病并推荐合适的就诊科室。\n" +
            "请以JSON格式返回，格式如下：\n" +
            "{\n" +
            "  \"possibleDiseases\": [\"疾病1\", \"疾病2\"],\n" +
            "  \"recommendedDepartment\": \"科室名称\",\n" +
            "  \"urgency\": \"NORMAL\" // URGENT/NORMAL/LOW\n" +
            "}");

        // 预问诊Prompt
        PROMPT_TEMPLATES.put("consult",
            "你是一个专业的医生助手。请根据以下信息生成预问诊报告：\n" +
            "主诉：{chiefComplaint}\n" +
            "现病史：{presentIllness}\n" +
            "请生成结构化的问诊要点，包括：\n" +
            "1. 需要进一步询问的问题\n" +
            "2. 建议的检查项目\n" +
            "3. 初步判断");

        // 诊断Prompt
        PROMPT_TEMPLATES.put("diagnosis",
            "你是一个资深医学诊断专家。请根据以下信息给出诊断建议：\n" +
            "症状：{symptoms}\n" +
            "病史：{history}\n" +
            "体格检查：{physicalExam}\n" +
            "检验检查结果：{labResults}\n" +
            "请给出：\n" +
            "1. 可能诊断（按可能性排序）\n" +
            "2. 建议的进一步检查\n" +
            "3. 鉴别诊断要点");

        // 处方审核Prompt
        PROMPT_TEMPLATES.put("pharmacy",
            "你是一个专业临床药师。请审核以下处方：\n" +
            "临床诊断：{diagnosis}\n" +
            "处方内容：{prescription}\n" +
            "患者信息：年龄{age}岁，体重{weight}kg\n" +
            "请检查：\n" +
            "1. 药物适应症\n" +
            "2. 药物相互作用\n" +
            "3. 用药剂量合理性\n" +
            "4. 潜在不良反应");
    }

    /**
     * 获取Prompt模板
     */
    public String getTemplate(String templateKey) {
        return PROMPT_TEMPLATES.getOrDefault(templateKey, "");
    }

    /**
     * 渲染Prompt模板
     */
    public String render(String templateKey, Map<String, Object> params) {
        String template = getTemplate(templateKey);
        return renderTemplate(template, params);
    }

    /**
     * 通用模板渲染
     */
    public String renderTemplate(String template, Map<String, Object> params) {
        String result = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}",
                String.valueOf(entry.getValue()));
        }
        return result;
    }
}
```

---

## 各AI服务职责

### 1. ai-triage-service (8101)

**AI导诊服务**

**功能**：
- 症状收集与分析
- 疾病预测
- 科室推荐
- 紧急程度评估
- 智能导诊

**核心接口**：

```java
@RestController
@RequestMapping("/api/ai/triage")
public class TriageController {

    @Autowired
    private AiTriageService triageService;

    /**
     * 症状分析
     * POST /api/ai/triage/analyze
     */
    @PostMapping("/analyze")
    public Result<TriageResult> analyze(@RequestBody TriageRequest request) {
        TriageResult result = triageService.analyze(
            request.getSymptoms(),
            request.getAge(),
            request.getGender()
        );
        return Result.success(result);
    }

    /**
     * 推荐科室
     * POST /api/ai/triage/department
     */
    @PostMapping("/department")
    public Result<String> recommendDepartment(@RequestBody TriageRequest request) {
        String department = triageService.recommendDepartment(
            request.getSymptoms()
        );
        return Result.success(department);
    }
}
```

**请求/响应示例**：

```json
// POST /api/ai/triage/analyze
// Request
{
    "symptoms": ["头痛", "发热", "咽痛", "流涕"],
    "age": 35,
    "gender": "M"
}

// Response
{
    "code": 200,
    "data": {
        "possibleDiseases": [
            {"name": "急性上呼吸道感染", "probability": 0.85},
            {"name": "急性扁桃体炎", "probability": 0.72}
        ],
        "recommendedDepartment": "呼吸内科",
        "urgency": "NORMAL",
        "suggestedExams": ["血常规", "CRP", "咽拭子检测"],
        "consultTips": "建议如实告知症状持续时间和近期接触史"
    }
}
```

**服务实现示例**：

```java
@Service
public class AiTriageService {

    private final AiCallService aiCallService;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;

    public AiTriageService(AiCallService aiCallService,
                          PromptTemplateService promptTemplateService,
                          ObjectMapper objectMapper) {
        this.aiCallService = aiCallService;
        this.promptTemplateService = promptTemplateService;
        this.objectMapper = objectMapper;
    }

    public TriageResult analyze(List<String> symptoms, Integer age, String gender) {
        Map<String, Object> params = new HashMap<>();
        params.put("symptoms", String.join("、", symptoms));
        params.put("age", age);
        params.put("gender", "男".equals(gender) || "M".equals(gender) ? "男性" : "女性");

        String prompt = promptTemplateService.render("triage", params);
        String response = aiCallService.call(prompt);

        return parseTriageResult(response);
    }

    private TriageResult parseTriageResult(String response) {
        try {
            // 尝试解析为JSON
            return objectMapper.readValue(response, TriageResult.class);
        } catch (Exception e) {
            // 解析失败，尝试从文本中提取信息
            return parseTextResponse(response);
        }
    }

    private TriageResult parseTextResponse(String response) {
        TriageResult result = new TriageResult();
        // 实现文本解析逻辑
        return result;
    }
}
```

---

### 2. ai-consult-service (8102)

**AI预问诊服务**

**功能**：
- 智能预问诊
- 病历结构化
- 病史采集
- 问诊要点生成

**核心接口**：

```java
@RestController
@RequestMapping("/api/ai/consult")
public class ConsultController {

    @Autowired
    private AiConsultService consultService;

    /**
     * 预问诊
     * POST /api/ai/consult/previsit
     */
    @PostMapping("/previsit")
    public Result<PreVisitResult> previsit(@RequestBody PreVisitRequest request) {
        PreVisitResult result = consultService.generatePreVisit(request);
        return Result.success(result);
    }

    /**
     * 生成病历摘要
     * POST /api/ai/consult/summary
     */
    @PostMapping("/summary")
    public Result<String> generateSummary(@RequestBody MedicalInfoRequest request) {
        String summary = consultService.generateSummary(request);
        return Result.success(summary);
    }
}
```

**请求/响应示例**：

```json
// POST /api/ai/consult/previsit
// Request
{
    "patientId": "P001",
    "chiefComplaint": "腹痛3天",
    "presentIllness": "患者3天前无明显诱因出现上腹部疼痛，呈阵发性胀痛，伴有恶心，无呕吐",
    "pastHistory": "平素体健，否认高血压、糖尿病史，否认手术史",
    "allergyHistory": "否认药物过敏史"
}

// Response
{
    "code": 200,
    "data": {
        "structuredRecord": {
            "symptoms": ["腹痛", "恶心"],
            "duration": "3天",
            "character": "阵发性胀痛",
            "location": "上腹部",
            "aggravatingFactors": [],
            "relievingFactors": []
        },
        "followUpQuestions": [
            "腹痛与进食是否相关？",
            "大便情况如何？",
            "是否有发热？",
            "疼痛能否自行缓解？"
        ],
        "suggestedExams": ["血常规", "腹部B超", "尿常规"],
        "preliminaryAssessment": "需排除急性胃炎、胆囊炎、胰腺炎等"
    }
}
```

---

### 3. ai-diagnosis-service (8103)

**AI诊断服务**

**功能**：
- 诊断推荐
- 检查检验建议
- 鉴别诊断
- 检查结果解读

**核心接口**：

```java
@RestController
@RequestMapping("/api/ai/diagnosis")
public class DiagnosisController {

    @Autowired
    private AiDiagnosisService diagnosisService;

    /**
     * 诊断推荐
     * POST /api/ai/diagnosis/suggest
     */
    @PostMapping("/suggest")
    public Result<DiagnosisResult> suggest(@RequestBody DiagnosisRequest request) {
        DiagnosisResult result = diagnosisService.suggest(
            request.getSymptoms(),
            request.getHistory(),
            request.getPhysicalExam(),
            request.getLabResults()
        );
        return Result.success(result);
    }

    /**
     * 检查结果解读
     * POST /api/ai/diagnosis/interpret
     */
    @PostMapping("/interpret")
    public Result<InterpretationResult> interpret(@RequestBody LabResultRequest request) {
        InterpretationResult result = diagnosisService.interpretLabResults(
            request.getLabType(),
            request.getLabValues()
        );
        return Result.success(result);
    }
}
```

**请求/响应示例**：

```json
// POST /api/ai/diagnosis/suggest
// Request
{
    "symptoms": ["多饮", "多尿", "体重下降", "乏力"],
    "history": "患者50岁男性，体型肥胖，有糖尿病家族史",
    "physicalExam": "BMI 28，血压130/85mmHg",
    "labResults": {
        "fastingGlucose": "7.2 mmol/L",
        "HbA1c": "6.8%"
    }
}

// Response
{
    "code": 200,
    "data": {
        "suggestedDiagnoses": [
            {
                "icdCode": "E11.900",
                "name": "2型糖尿病",
                "confidence": 0.92,
                "reason": "符合糖尿病诊断标准，结合家族史和体型"
            },
            {
                "icdCode": "E11.900x001",
                "name": "糖尿病前期",
                "confidence": 0.65
            }
        ],
        "differentialDiagnoses": [
            {"name": "1型糖尿病", "reason": "多见于年轻人，起病急"},
            {"name": "甲状腺功能亢进", "reason": "可有多饮多尿，但常有突眼"}
        ],
        "suggestedExams": [
            {"code": "GLU", "name": "空腹血糖", "reason": "明确诊断"},
            {"code": "HbA1c", "name": "糖化血红蛋白", "reason": "评估近3月血糖控制"},
            {"code": "OGTT", "name": "口服葡萄糖耐量试验", "reason": "糖尿病分型"}
        ],
        "clinicalSuggestions": [
            "建议内分泌科就诊",
            "控制饮食，适当运动",
            "定期监测血糖"
        ]
    }
}
```

---

### 4. ai-pharmacy-service (8104)

**AI药学服务**

**功能**：
- 处方审核
- 用药指导
- 药物相互作用检查
- 随访计划生成

**核心接口**：

```java
@RestController
@RequestMapping("/api/ai/pharmacy")
public class PharmacyController {

    @Autowired
    private AiPharmacyService pharmacyService;

    /**
     * 处方审核
     * POST /api/ai/pharmacy/review
     */
    @PostMapping("/review")
    public Result<ReviewResult> reviewPrescription(@RequestBody PrescriptionRequest request) {
        ReviewResult result = pharmacyService.reviewPrescription(
            request.getPrescription(),
            request.getDiagnosis(),
            request.getPatientInfo()
        );
        return Result.success(result);
    }

    /**
     * 用药指导
     * POST /api/ai/pharmacy/guide
     */
    @PostMapping("/guide")
    public Result<MedicationGuide> getMedicationGuide(@RequestBody DrugRequest request) {
        MedicationGuide guide = pharmacyService.generateMedicationGuide(
            request.getDrugName(),
            request.getDosage(),
            request.getFrequency()
        );
        return Result.success(guide);
    }

    /**
     * 生成随访计划
     * POST /api/ai/pharmacy/followup
     */
    @PostMapping("/followup")
    public Result<FollowUpPlan> generateFollowUpPlan(@RequestBody FollowUpRequest request) {
        FollowUpPlan plan = pharmacyService.generateFollowUpPlan(request);
        return Result.success(plan);
    }
}
```

**请求/响应示例**：

```json
// POST /api/ai/pharmacy/review
// Request
{
    "prescriptionId": "RX001",
    "diagnosis": "社区获得性肺炎",
    "patientInfo": {
        "age": 65,
        "weight": 70,
        "kidneyFunction": "正常",
        "allergies": ["青霉素"]
    },
    "prescription": {
        "items": [
            {"drug": "左氧氟沙星", "dose": "0.5g", "frequency": "qd", "route": "口服", "days": 7},
            {"drug": "氨溴索", "dose": "30mg", "frequency": "tid", "route": "口服", "days": 7}
        ]
    }
}

// Response
{
    "code": 200,
    "data": {
        "reviewStatus": "PASS_WITH_WARNINGS",
        "checkResults": [
            {
                "type": "ALLERGY",
                "severity": "WARNING",
                "description": "左氧氟沙星与青霉素类存在交叉过敏可能，建议密切观察"
            },
            {
                "type": "DOSAGE",
                "severity": "INFO",
                "description": "氨溴索剂量在正常范围内"
            }
        ],
        "interactions": [],
        "suggestions": [
            "建议使用前进行皮试",
            "多饮水，避免光敏反应"
        ],
        "alternativeDrugs": [
            {"drug": "莫西沙星", "reason": "对青霉素过敏者可替代"}
        ]
    }
}
```

---

## Spring AI集成指南

### ChatClient使用方式

**方式一：构造器注入**

```java
@Service
public class MyAiService {

    private final ChatClient chatClient;

    public MyAiService(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    public String callAi(String input) {
        return chatClient.call(input);
    }
}
```

**方式二：Builder配置**

```java
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
            .defaultSystem("你是一个专业的医疗AI助手...")
            .defaultOptions(ToolCallingChatOptions.builder()
                .toolChoice(ToolChoice.AUTO)
                .build())
            .build();
    }
}
```

**调用示例**：

```java
// 简单调用
String response = chatClient.call("头痛应该挂什么科？");

// 使用UserMessage
String response = chatClient.call(new UserMessage("头痛应该挂什么科？"));

// 使用Prompt
Prompt prompt = new Prompt(new UserMessage("头痛应该挂什么科？"));
String response = chatClient.call(prompt).getResult().getOutput().getContent();
```

---

### PromptTemplate模板编写

**基本用法**：

```java
// 创建模板
PromptTemplate template = new PromptTemplate(
    "请根据以下症状判断科室：{symptoms}，年龄{age}岁"
);

// 添加变量
template.add("symptoms", "头痛、发热");
template.add("age", 35);

// 创建Prompt
Prompt prompt = template.create();

// 调用
String response = chatClient.call(prompt);
```

**使用Map传参**：

```java
PromptTemplate template = new PromptTemplate(
    "请分析以下症状：{symptoms}，并推荐科室：{department}"
);

Map<String, Object> params = new HashMap<>();
params.put("symptoms", "腹痛、腹泻");
params.put("department", "消化内科");

Prompt prompt = template.create(params);
String response = chatClient.call(prompt);
```

**模板文件加载**：

```java
// 从classpath加载模板
PromptTemplate template = PromptTemplate.from(
    ResourceUtils.getFile("classpath:prompts/triage.txt")
);

// 使用模板
template.add("symptoms", "头痛");
Prompt prompt = template.create();
```

---

### 结构化输出

**使用系统提示约束输出格式**：

```java
public class StructuredOutputService {

    private final ChatClient chatClient;

    public DiagnosisResult getStructuredDiagnosis(DiagnosisRequest request) {
        String systemPrompt = """
            你是一个医学诊断助手。请根据提供的信息给出诊断建议。
            必须严格按以下JSON格式返回，不要添加任何额外解释：
            {
                "diagnoses": [
                    {"code": "ICD编码", "name": "诊断名称", "confidence": 0.0-1.0}
                ],
                "exams": ["检查1", "检查2"],
                "reason": "诊断理由"
            }
            """;

        String userPrompt = String.format("""
            症状：%s
            病史：%s
            体格检查：%s
            """,
            String.join("、", request.getSymptoms()),
            request.getHistory(),
            request.getPhysicalExam()
        );

        return chatClient.prompt()
            .system(systemPrompt)
            .user(userPrompt)
            .call()
            .entity(DiagnosisResult.class);
    }
}
```

**使用JSON模式**：

```java
public class JsonOutputService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public TriageResult getJsonTriage(TriageRequest request) {
        // 使用SYSTEM提示约束JSON输出
        String response = chatClient.prompt()
            .system("""
                你是一个导诊助手。请严格按照以下JSON格式返回，不要包含任何其他内容：
                {
                    "diseases": ["疾病1", "疾病2"],
                    "department": "科室名",
                    "urgency": "NORMAL"
                }
                """)
            .user(String.format("症状：%s", String.join("、", request.getSymptoms())))
            .call()
            .content();

        try {
            // 解析JSON响应
            return objectMapper.readValue(response, TriageResult.class);
        } catch (JsonProcessingException e) {
            // 处理解析失败的情况
            return handleParseError(response);
        }
    }
}
```

---

## 数据库表归属

### AI服务数据库表归属总览

| 服务 | 拥有的表 | 说明 |
|------|----------|------|
| ai-triage-service | ai_triage_record | AI导诊记录 |
| ai-consult-service | ai_consultation_record, ai_medical_record_log | AI问诊记录 |
| ai-diagnosis-service | ai_exam_suggestion, ai_exam_analysis, ai_diagnosis_suggestion | AI诊断记录 |
| ai-pharmacy-service | ai_prescription_review | AI处方审核 |

### 各AI服务数据库表详细说明

#### ai-triage-service (8101)

| 表名 | 说明 |
|------|------|
| ai_triage_record | AI导诊记录表 |

#### ai-consult-service (8102)

| 表名 | 说明 |
|------|------|
| ai_consultation_record | AI问诊记录表 |
| ai_medical_record_log | AI病历日志表 |

#### ai-diagnosis-service (8103)

| 表名 | 说明 |
|------|------|
| ai_exam_suggestion | AI检查建议表 |
| ai_exam_analysis | AI检查分析表 |
| ai_diagnosis_suggestion | AI诊断建议表 |

#### ai-pharmacy-service (8104)

| 表名 | 说明 |
|------|------|
| ai_prescription_review | AI处方审核记录表 |

---

## API接口规范

### 统一响应格式

所有AI服务使用统一的响应格式：

```json
{
    "code": 200,
    "message": "success",
    "data": { /* 业务数据 */ }
}
```

**错误码定义**：

| code | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权 |
| 500 | 服务器内部错误 |
| 503 | AI服务不可用 |

**错误响应示例**：

```json
{
    "code": 503,
    "message": "AI模型服务暂时不可用，请稍后重试",
    "data": null
}
```

---

### AI服务接口汇总

#### ai-triage-service

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /api/ai/triage/analyze | 症状分析 |
| POST | /api/ai/triage/department | 科室推荐 |
| POST | /api/ai/triage/urgency | 紧急程度评估 |

**TriageRequest**：
```json
{
    "symptoms": ["症状列表"],
    "age": 35,
    "gender": "M/F"
}
```

**TriageResult**：
```json
{
    "possibleDiseases": [
        {"name": "疾病名", "probability": 0.85}
    ],
    "recommendedDepartment": "科室名",
    "urgency": "NORMAL/URGENT/LOW",
    "suggestedExams": ["检查项目"],
    "consultTips": "咨询建议"
}
```

---

#### ai-consult-service

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /api/ai/consult/previsit | 预问诊 |
| POST | /api/ai/consult/summary | 病历摘要生成 |
| POST | /api/ai/consult/questions | 生成问诊问题 |

**PreVisitRequest**：
```json
{
    "chiefComplaint": "主诉",
    "presentIllness": "现病史",
    "pastHistory": "既往史",
    "allergyHistory": "过敏史"
}
```

**PreVisitResult**：
```json
{
    "structuredRecord": {
        "symptoms": [],
        "duration": "",
        "character": ""
    },
    "followUpQuestions": ["问题1", "问题2"],
    "suggestedExams": [],
    "preliminaryAssessment": ""
}
```

---

#### ai-diagnosis-service

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /api/ai/diagnosis/suggest | 诊断推荐 |
| POST | /api/ai/diagnosis/interpret | 检查结果解读 |
| POST | /api/ai/diagnosis/differential | 鉴别诊断 |

**DiagnosisRequest**：
```json
{
    "symptoms": ["症状列表"],
    "history": "病史",
    "physicalExam": "体格检查",
    "labResults": {}
}
```

**DiagnosisResult**：
```json
{
    "suggestedDiagnoses": [
        {"icdCode": "代码", "name": "名称", "confidence": 0.9, "reason": "理由"}
    ],
    "differentialDiagnoses": [],
    "suggestedExams": [
        {"code": "检查码", "name": "检查名", "reason": "建议理由"}
    ],
    "clinicalSuggestions": []
}
```

---

#### ai-pharmacy-service

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /api/ai/pharmacy/review | 处方审核 |
| POST | /api/ai/pharmacy/guide | 用药指导 |
| POST | /api/ai/pharmacy/followup | 随访计划 |

**PrescriptionRequest**：
```json
{
    "prescriptionId": "处方ID",
    "diagnosis": "临床诊断",
    "patientInfo": {
        "age": 65,
        "weight": 70,
        "kidneyFunction": "正常",
        "allergies": ["过敏药物"]
    },
    "prescription": {
        "items": [
            {"drug": "药名", "dose": "剂量", "frequency": "频次", "route": "途径", "days": 7}
        ]
    }
}
```

**ReviewResult**：
```json
{
    "reviewStatus": "PASS/WARNING/REJECT",
    "checkResults": [
        {"type": "类型", "severity": "WARNING/ERROR/INFO", "description": "描述"}
    ],
    "interactions": [],
    "suggestions": [],
    "alternativeDrugs": []
}
```

---

## 与业务服务联调

### 被业务服务调用方式

业务服务通过HTTP调用ai-gateway-service，ai-gateway-service再路由到具体AI服务。

**调用示例（从业务服务视角）**：

```bash
# 调用AI导诊
curl -X POST http://localhost:8100/api/ai/triage/analyze \
  -H "Content-Type: application/json" \
  -H "X-Request-Id: unique-request-id" \
  -d '{
    "symptoms": ["头痛", "发热"],
    "age": 35,
    "gender": "M"
  }'

# 调用AI诊断
curl -X POST http://localhost:8100/api/ai/diagnosis/suggest \
  -H "Content-Type: application/json" \
  -d '{
    "symptoms": ["多饮", "多尿"],
    "history": "50岁男性",
    "physicalExam": "肥胖",
    "labResults": {}
  }'
```

### 接口格式约定

**请求Header**：

| Header | 说明 |
|--------|------|
| Content-Type | application/json |
| X-Request-Id | 请求追踪ID |
| Authorization | Bearer token（可选） |

**请求体**：统一使用JSON格式

**响应格式**：

```json
{
    "code": 200,
    "message": "success",
    "data": {}
}
```

**统一响应格式说明**：

所有AI服务使用统一的响应格式：
- `code`: 状态码，200表示成功
- `message`: 状态信息
- `data`: 业务数据对象

**请求示例格式**：
```json
{
    "symptoms": ["头痛", "发热", "咽痛"],
    "age": 35,
    "gender": "M"
}
```

**响应示例格式**：
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "suggestedDiagnoses": [
            {"code": "J06.901", "name": "急性上呼吸道感染", "confidence": 0.85}
        ],
        "suggestedExams": ["血常规", "CRP"],
        "department": "呼吸内科"
    }
}
```

**错误响应示例**：
```json
{
    "code": 500,
    "message": "AI服务调用失败: xxx",
    "data": null
}
```
```

### 联调检查清单

1. **网络连通性**：确保8100-8104端口可访问
2. **健康检查**：
   ```bash
   curl http://localhost:8100/actuator/health
   curl http://localhost:8101/actuator/health
   curl http://localhost:8102/actuator/health
   curl http://localhost:8103/actuator/health
   curl http://localhost:8104/actuator/health
   ```
3. **API Key配置**：确认Spring AI的API Key已配置
4. **日志查看**：
   ```bash
   # 查看AI调用日志
   tail -f logs/ai-gateway.log | grep "AI调用"
   ```

---

## Nacos 服务发现配置

### 1. AI服务注册

AI服务通过 `bootstrap.yml` 配置自动注册到 Nacos：

```yaml
spring:
  application:
    name: ai-gateway-service
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: dev
      config:
        server-addr: localhost:8848
        file-extension: yaml
  ai:
    openai:
      api-key: ${AI_API_KEY:your-api-key}
      base-url: ${AI_BASE_URL:https://api.openai.com}
```

### 2. 服务端口

| 服务 | 端口 |
|------|------|
| ai-gateway-service | 8100 |
| ai-triage-service | 8101 |
| ai-consult-service | 8102 |
| ai-diagnosis-service | 8103 |
| ai-pharmacy-service | 8104 |

### 3. 启动 Nacos

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

## 开发顺序建议

### 推荐开发顺序

```
第一阶段：基础设施（1-2天）
├── 1. ai-gateway-service基础框架
│   ├── 项目搭建
│   ├── ChatClient配置
│   ├── 统一响应格式
│   └── 基础Prompt模板
│
└── 2. 公共组件
    ├── AiCallService封装
    ├── PromptTemplateService
    └── 统一异常处理

第二阶段：核心AI服务（3-5天）
├── 3. ai-triage-service (:8101)
│   ├── 症状分析逻辑
│   ├── 科室推荐
│   └── Prompt优化
│
├── 4. ai-consult-service (:8102)
│   ├── 预问诊逻辑
│   ├── 病历结构化
│   └── Prompt优化
│
├── 5. ai-diagnosis-service (:8103)
│   ├── 诊断推荐
│   ├── 鉴别诊断
│   └── ICD编码映射
│
└── 6. ai-pharmacy-service (:8104)
    ├── 处方审核
    ├── 用药指导
    └── 随访计划

第三阶段：集成联调（1-2天）
├── 7. 与业务服务联调
│   ├── 接口格式确认
│   ├── 错误处理
│   └── 性能优化
│
└── 8. Prompt调优
    ├── 测试不同Prompt效果
    └── 输出格式优化
```

### 注意事项

1. **Prompt优先**：先设计好Prompt，再实现业务逻辑
2. **结构化输出**：AI输出尽量结构化，便于业务服务解析
3. **错误处理**：AI调用要做好超时、失败的降级处理
4. **日志记录**：记录所有AI请求和响应，便于排查问题
5. **限流保护**：AI服务要配置限流，避免API额度快速消耗
6. **Prompt版本管理**：不同版本的Prompt要做好版本控制

### 配置文件示例

**application.yml**：

```yaml
spring:
  application:
    name: ai-gateway-service
  ai:
    api-key: ${AI_API_KEY}
    base-url: ${AI_BASE_URL}
    model: claude-3-5-sonnet

server:
  port: 8100

logging:
  level:
    com.xikang.ai: DEBUG
    org.springframework.ai: DEBUG
```
