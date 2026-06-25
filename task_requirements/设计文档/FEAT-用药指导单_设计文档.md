# FEAT - 用药指导单（处方级）设计文档

> 状态：**待评审** · 未实现
> 作者：Claude · 日期：2026-06-25
> 关联模块：`pharmacy-service`、`ai-pharmacy-service`、前端 `pharmacy/DispensingPage`

---

## 0. 一句话目标

医生开完方、药房发完药后，系统**按整张处方**异步生成一份"用药指导单数据"（JSON）；药房/患者通过**"下载用药指导单"按钮**实时渲染成 PDF 下载，**PDF 文件不落盘**——数据一次生成、PDF 按需产出、模板可独立升级。

---

## 1. 已确认的方案决策

| 决策点 | 选择 | 说明 |
|---|---|---|
| 单据粒度 | **整张处方一张**（按 `register_id` 聚合） | AI 一次看全，可做处方内提示 |
| AI 接入 | **先打通数据流，AI 用 mock** | 基于 `drug_info` 字段拼接结构化结果；接真模型时只改 `ai-pharmacy-service` 内部 |
| 数据落库 | **新建 `medication_guide` 表**，只存 JSON 数据 | 独立表，按 `register_id` 关联，缓存可追溯 |
| 数据生成时机 | **发药成功后异步生成 JSON** | 复用现有 `dispense` 的 `afterCommit` 钩子，不阻塞发药 |
| **PDF 生成时机** | **延迟到用户点下载按钮那一刻** | 后端实时把 JSON 渲染成 PDF 流式返回，**不落盘** |
| **PDF 渲染位置** | **后端实时渲染**（OpenHTMLtoPDF） | 纯 Java 栈，支持中文/分页/表格，前端零依赖 |
| **下载交互** | **点按钮直接下载 PDF** | 不做网页预览，最简交互 |
| **PDF 文件持久化** | **不持久化**，用完即扔 | 存储零负担，模板升级对老数据无损 |

---

## 2. 现状回顾（不动医生端）

医生开方只落 4 个字段：`register_id`、`drug_id`、`drug_usage`（自由文本）、`drug_number`（VARCHAR 数字）。

可关联拿到的语义信息：
- `drug_info` 表（实体 `DrugInfo.java`）：`instructions`、`contraindications`、`adverse_reactions`、`storage_conditions`、`specification`、`dosage_form` —— 种子数据已有真实内容
- `register` 表：`real_name`（患者）、`employee_id`（医生）
- `medical_record` 表：`diagnosis`（确诊，本次先不接入指导单，留作 P2 增强）

**约束**：以上信息足以生成指导单，**不依赖医生端任何改动**。

---

## 3. 数据库设计

### 3.1 新建表 `medication_guide`

```sql
-- 迁移脚本：docker/init-db/migrate_012_medication_guide.sql

CREATE TABLE IF NOT EXISTS public.medication_guide (
    id              BIGSERIAL PRIMARY KEY,
    register_id     BIGINT      NOT NULL,
    prescription_id BIGINT,           -- 处方聚合头中 MIN(p.id)，可空（兼容）
    patient_id      BIGINT,
    patient_name    VARCHAR(64),
    -- 整段 JSON：{ items:[...], generalAdvice, interactionsNote, generatedAt, source }
    guide_content   JSONB       NOT NULL,
    -- 'mock' | 'ai' | 'ai_cached' | 'manual'，标记产出方式
    source          VARCHAR(16) NOT NULL DEFAULT 'mock',
    -- 'success' | 'failed' | 'fallback'
    status          VARCHAR(16) NOT NULL DEFAULT 'success',
    error_message   TEXT,
    create_time     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_medication_guide_register     ON public.medication_guide(register_id);
CREATE INDEX idx_medication_guide_prescription ON public.medication_guide(prescription_id);
CREATE INDEX idx_medication_guide_patient      ON public.medication_guide(patient_id);

COMMENT ON TABLE  public.medication_guide IS '处方级用药指导单（AI 生成 / mock 拼接，按挂号聚合）';
COMMENT ON COLUMN public.medication_guide.guide_content IS '结构化指导内容 JSONB：items/generalAdvice/interactionsNote';
COMMENT ON COLUMN public.medication_guide.source       IS '产出方式：mock / ai / ai_cached / manual';
COMMENT ON COLUMN public.medication_guide.status       IS 'success / failed / fallback（AI 失败但有降级内容）';
```

### 3.2 `guide_content` JSON Schema

```jsonc
{
  "items": [
    {
      "drugId": 3,
      "drugName": "阿莫西林胶囊",
      "specification": "0.25g×24粒",
      "dosageForm": "胶囊剂",
      "quantity": 2,                       // 解析 drug_number 得到
      "usageText": "口服，一日三次",         // 直接回显 prescription.drug_usage（医生原话）
      "howToTake": "口服，成人每日 3~4 次，每次 0.5g（2 粒）。",  // 来自 drug_info.instructions
      "takeWithFood": null,                // 可空，AI 可选填 "饭前"/"饭后"/"无明显要求"
      "precautions": "用药期间及停药后 3 天内禁止饮酒（双硫仑样反应）。", // 来自 contraindications 摘要
      "sideEffects": "常见皮疹、瘙痒、腹泻；罕见过敏性休克。",          // 来自 adverse_reactions
      "storage": "密封，室温保存"
    }
    // ... 每种药一项
  ],
  "generalAdvice": "请按医嘱按时服药；不要自行增减剂量或停药；如有不适及时联系医生。",
  "interactionsNote": null,                // 处方内多药提示，单药处方为 null
  "generatedAt": "2026-06-25T14:30:00",
  "modelVersion": null                     // 接真模型后填模型名，mock 阶段为 null
}
```

**设计原则**：
- `usageText`（医生原话）和 `howToTake`（药品说明书）**分开存**——前者是医嘱事实，后者是参考说明，混淆会让患者困惑
- 所有可空字段允许 null，mock 阶段不强行填，接真模型后由 AI 补全
- `interactionsNote` 单独一栏，明确"处方内多药提示"——为后续真正的配伍检查留接口位

---

## 4. 后端接口设计

### 4.1 新增 API（`ai-pharmacy-service`）

**`POST /api/ai/pharmacy/medication-guide`** —— 生成处方级指导单

请求体（由 `pharmacy-service` 组装）：
```jsonc
{
  "registerId": 1001,
  "patientId": 88,
  "patientName": "张三",
  "diagnosis": "急性扁桃体炎",          // 可空，本次 mock 不强依赖
  "items": [
    {
      "drugId": 3,
      "drugName": "阿莫西林胶囊",
      "specification": "0.25g×24粒",
      "dosageForm": "胶囊剂",
      "quantity": 2,
      "usageText": "口服，一日三次",
      "instructions": "口服，成人每日 3~4 次...",
      "contraindications": "青霉素过敏者禁用...",
      "adverseReactions": "常见皮疹...",
      "storageConditions": "密封，室温保存"
    }
  ]
}
```

响应体：
```jsonc
{
  "code": 200,
  "data": { /* guide_content JSON，同 §3.2 */ }
}
```

**实现位置**：`AiPharmacyService.generateMedicationGuide(Map prescriptionContext)`
- 本次：mock 拼接 —— 遍历 `items`，把 `instructions/contraindications/adverseReactions/storageConditions` 直接映射到 `howToTake/precautions/sideEffects/storage`
- `interactionsNote`：mock 阶段固定返回 `null`（标记"待接真模型"），或简单规则：若 `items.size() >= 2` 返回 `"本处方含多种药品，服用间隔请遵医嘱"` 的兜底文案
- 后续接真模型：在此方法内部调 LLM，**接口契约不变**

### 4.2 新增 API（`pharmacy-service`）

#### 4.2.1 `GET /api/pharmacy/medication-guide/{registerId}` —— 查询指导单数据

用于前端"是否存在指导单"判断、状态展示。响应：
```jsonc
{
  "code": 200,
  "data": {
    "id": 1,
    "registerId": 1001,
    "guideContent": { /* §3.2 JSON */ },
    "source": "mock",
    "status": "success",
    "createTime": "2026-06-25T14:30:00"
  }
}
```
查不到返回 `data: null`（前端按钮置灰 + "指导单生成中"提示）。

#### 4.2.2 `GET /api/pharmacy/medication-guide/{registerId}/pdf` —— **下载 PDF（核心）**

- **延迟渲染**：调用时后端取最新一条 `medication_guide` 数据 → 套 HTML 模板 → OpenHTMLtoPDF 转 PDF → 流式返回
- **不落盘**：PDF 字节流直接写入 HTTP response，不写文件系统
- **响应头**：
  ```
  Content-Type: application/pdf
  Content-Disposition: attachment; filename="medication-guide-{registerId}.pdf"
  ```
- 用户点击 → 浏览器直接弹下载对话框
- 异常情况（指导单未生成/生成失败）：返回 404 + JSON 错误体，前端提示"指导单未就绪，请稍后重试或点重新生成"

#### 4.2.3 `POST /api/pharmacy/medication-guide/{registerId}/retry` —— 手动重试生成数据

- AI 异步生成失败后，药师主动触发重新生成 JSON 数据（**不是 PDF**）
- 同步调用（手动操作允许同步）
- 成功后前端按钮变可点

### 4.3 不动现有接口

- `POST /api/ai/pharmacy/guide`（单药品）**保留不动**，向后兼容
- `POST /api/pharmacy/drugs/{drugId}/guide`（pharmacy → ai-pharmacy 单药品透传）**保留**

---

## 5. 数据生成时序（PDF 延迟生成）

复用现有 `PharmacyService.dispense` 的 `afterCommit` 钩子机制（`PharmacyService.java:342`）。

### 5.1 发药成功 → 异步生成 JSON 数据

```
[发药请求] 
    ↓
PharmacyService.dispense()
    ↓ (事务内)
扣库存 → 写流水 → 改 drug_state='已发' → 写 dispensing 发药单
    ↓ (事务提交后 afterCommit)
并行触发两个异步任务：
    ├── [现有] aiPharmacyClient.createFollowUpPlan(...)        // 创建随访
    └── [新增] generateAndSaveMedicationGuide(registerId)      // 仅生成 JSON 数据
              ↓
         1. 组装 prescriptionContext（register + 处方行 + drug_info 字段）
         2. 调 aiPharmacyClient.generateMedicationGuide(ctx) → 拿到 JSON
         3. INSERT medication_guide 表（guide_content=JSON, status='success'）
         4. 失败：INSERT 失败记录（status='failed' + error_message），不抛异常
              ↓
         全程 try/catch，不影响发药主流程
              ↓
         【注意】此阶段完全不生成 PDF 文件，只存 JSON
```

### 5.2 用户点下载 → 实时渲染 PDF

```
[用户在发药台点"下载用药指导单"按钮]
    ↓
前端 GET /api/pharmacy/medication-guide/{registerId}/pdf
    ↓
PharmacyController.downloadMedicationGuidePdf(registerId)
    ↓
1. 查 medication_guide 表，取最新一条 guide_content (JSON)
   ├── 没数据 / status='failed' → 返回 404，前端提示"指导单未就绪"
   └── 有数据 → 继续
2. 把 JSON 套进 HTML 模板（Thymeleaf 模板字符串，无需模板引擎依赖）
3. OpenHTMLtoPDF: HtmlConverter.createPdf(htmlString, outputStream)
4. 设置响应头 Content-Disposition: attachment
5. 字节流写回 response
    ↓
【不落盘】PDF 字节流直接返回给浏览器，服务端不写文件
    ↓
浏览器弹下载对话框，用户保存或打开
```

**关键约束**：
- 数据生成（异步）和 PDF 生成（按需）**完全解耦**
- PDF 永远不落盘——节省存储，模板升级对历史数据无损
- 同一 `register_id` 的 PDF 每次下载都重新渲染，永远套最新模板
- 数据生成失败不阻塞发药；PDF 渲染失败返回 404，不影响其他功能

---

## 6. Mock 实现细节（本次落地）

### 6.1 `AiPharmacyService.generateMedicationGuide` 伪代码

```java
public Map<String, Object> generateMedicationGuide(Map<String, Object> ctx) {
    List<Map<String, Object>> items = (List) ctx.get("items");
    
    List<Map<String, Object>> guideItems = items.stream().map(item -> {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("drugId",         item.get("drugId"));
        g.put("drugName",       item.get("drugName"));
        g.put("specification",  item.get("specification"));
        g.put("dosageForm",     item.get("dosageForm"));
        g.put("quantity",       item.get("quantity"));
        g.put("usageText",      item.get("usageText"));     // 医生原话
        g.put("howToTake",      item.get("instructions"));  // 药品说明书
        g.put("precautions",    item.get("contraindications"));
        g.put("sideEffects",    item.get("adverseReactions"));
        g.put("storage",        item.get("storageConditions"));
        g.put("takeWithFood",   null);  // mock 不填
        return g;
    }).toList();

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("items", guideItems);
    result.put("generalAdvice", "请按医嘱按时服药；不要自行增减剂量或停药；如有不适及时联系医生。");
    // 多药兜底提示
    result.put("interactionsNote", items.size() >= 2 
        ? "本处方含多种药品，服药间隔请遵医嘱。" 
        : null);
    result.put("generatedAt", LocalDateTime.now().toString());
    result.put("modelVersion", null);
    return result;
}
```

### 6.2 数据组装（`PharmacyService` 侧）

发药 `afterCommit` 调用前，需要组装 `ctx`：
1. 用 `selectByRegisterId` 拿处方聚合头（含 patientName）
2. 用 `prescriptionDetailMapper.selectByPrescriptionId(registerId)` 拿明细
3. 对每条明细，用 `drugInfoMapper.selectById(drugId)` 拿 drug_info 字段
4. 拼成 §4.1 请求体格式

**优化点**：步骤 3 有 N+1 问题（N 个药品 N 次查询）。本次 mock 阶段容忍，发药本就不是高频操作；后续可加 `drugInfoMapper.selectByIds(List)` 批量查询。

---

## 6.5 PDF 渲染（后端实时生成，OpenHTMLtoPDF）

### 6.5.1 依赖

`pharmacy-service/pom.xml` 新增：
```xml
<dependency>
    <groupId>com.openhtmltopdf</groupId>
    <artifactId>openhtmltopdf-core</artifactId>
    <version>1.0.10</version>
</dependency>
<dependency>
    <groupId>com.openhtmltopdf</groupId>
    <artifactId>openhtmltopdf-pdfbox</artifactId>
    <version>1.0.10</version>
</dependency>
```
纯 Java 实现，无外部进程依赖，体积约 5MB（含 PDFBox）。

### 6.5.2 HTML 模板（内置字符串，不用模板引擎）

PDF 模板用 Java 字符串拼接（指导单结构简单，不上 Thymeleaf/Freemarker）。模板放在：
- `pharmacy-service/src/main/resources/templates/medication-guide.html`（静态骨架）
- `PharmacyPdfRenderer.java` 负责读取骨架 + 注入数据 → 完整 HTML 字符串

模板要点：
- 内嵌 CSS（PDF 渲染不走网络，CSS 必须内联或 `<style>` 块）
- 中文字体：用 PDFBox 内置字体自动嵌入（OpenHTMLtoPDF 1.0.10 + PDFBox 2.x 默认支持 CJK）
- 分页：每种药一个小节，CSS `page-break-inside: avoid` 防跨页断行
- 表格风格：医院 header → 患者信息表 → 处方明细表 → 多药提示 → 药师签名位 → 生成时间

### 6.5.3 渲染器伪代码

```java
// PharmacyPdfRenderer.java
public class PharmacyPdfRenderer {
    
    public byte[] render(MedicationGuide guide) {
        String html = buildHtml(guide);  // JSON → HTML 字符串
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        }
    }
    
    private String buildHtml(MedicationGuide guide) {
        // 读模板骨架，注入 guide.getGuideContent()
        // 用 StringBuilder 拼，每种药一个 <section>
    }
}
```

### 6.5.4 Controller 下载端点

```java
@GetMapping("/medication-guide/{registerId}/pdf")
public ResponseEntity<byte[]> downloadPdf(@PathVariable Long registerId) {
    MedicationGuide guide = pharmacyService.getLatestMedicationGuide(registerId);
    if (guide == null || !"success".equals(guide.getStatus())) {
        return ResponseEntity.status(404).build();
    }
    byte[] pdf = pdfRenderer.render(guide);
    
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentDispositionFormData("attachment", 
        "medication-guide-" + registerId + ".pdf");
    return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
}
```

### 6.5.5 为什么不用前端渲染 PDF

| 维度 | 后端 OpenHTMLtoPDF | 前端 jsPDF/html2pdf |
|---|---|---|
| 中文字体 | PDFBox 内置，开箱即用 | 需前端打包字体（>1MB） |
| 分页 | CSS 原生支持 | 表格断行易错 |
| 浏览器一致性 | 后端渲染，绝对一致 | 各浏览器效果有差异 |
| 后期加水印/章 | 改后端模板即可 | 同步改前端逻辑 |
| 体积 | 仅服务端 +5MB | 前端 +300KB~1MB |
| 适合场景 | 正式单据、归档 | 临时预览 |

后端方案明显更适合"医院单据"场景。

---

## 7. 前端改造（`DispensingPage.vue`）

### 7.1 新增"下载用药指导单"按钮

在处方详情区的按钮组（`DispensingPage.vue:308-314`），现有按钮：审方预检 / 确认发药 / 退药 / 查看该患者随访 / 查看发药单。

**新增一个按钮**：
```vue
<ElButton 
  :icon="Download" 
  :disabled="!canDownloadGuide" 
  :loading="guideDownloading"
  @click="downloadMedicationGuide"
>下载用药指导单</ElButton>
```

### 7.2 交互逻辑

- **按钮可见性**：处方已发药（`dispensationStatus === 1`）时才显示
- **按钮可用性**：
  - 调 `GET /api/pharmacy/medication-guide/{registerId}` 探测指导单是否就绪
  - 就绪 → 按钮可点
  - 未就绪/生成中 → 按钮置灰 + tooltip "指导单生成中，请稍候"
  - 生成失败 → 按钮变 "重新生成指导单" → 点击调 retry 接口
- **点击下载**：
  - 不调 axios（axios 不便处理二进制流），用 **原生 `window.open`** 或隐藏 `<a download>`：
    ```ts
    function downloadMedicationGuide() {
      const url = `/api/pharmacy/medication-guide/${registerId}/pdf`
      // 直接打开，浏览器自动识别 Content-Disposition: attachment 弹下载
      window.open(url, '_blank')
    }
    ```
  - 体验：点击 → 浏览器立即弹"保存/打开"对话框 → 用户选 → 完成
  - 不经过前端内存，不下载数据再传——后端流式直出，最省资源

### 7.3 不改的部分

- 发药主流程按钮不动
- "审方预检""确认发药""退药"逻辑不动
- **现有"查看发药单"弹窗不动**（保留原有发药单查看功能，和 PDF 下载互不干扰）
- 不做网页版用药指导预览（按你的决策，直接下载最简）

---

## 8. 错误处理与降级

| 场景 | 行为 |
|---|---|
| 发药时 `afterCommit` 调 AI 失败 | 写 `medication_guide` 一条 `status='failed'` 记录，发药**正常返回成功** |
| 前端查询指导单，最近一条是 failed | 显示失败 + 重试按钮 |
| 重试接口调用失败 | 返回 500，前端 toast 提示，可再次重试 |
| `drug_info.instructions` 为 NULL | `howToTake` 输出 null，前端显示"详见药品说明书或咨询药师" |
| AI 返回 JSON 格式异常 | mock 阶段不会发生；接真模型时需加 schema 校验 + 降级 |

---

## 9. 实现清单（评审通过后按此执行）

### 9.1 数据库（1 个迁移文件）
- [ ] `docker/init-db/migrate_012_medication_guide.sql` —— 建表 + 索引 + 注释

### 9.2 ai-pharmacy-service（2 个文件）
- [ ] `AiPharmacyService.generateMedicationGuide(Map)` —— 新增方法（mock 实现）
- [ ] `AiPharmacyController` 新增 `POST /api/ai/pharmacy/medication-guide` 端点

### 9.3 pharmacy-service（7 个文件 + 1 个依赖）
- [ ] `pom.xml` —— 新增 `openhtmltopdf-core` + `openhtmltopdf-pdfbox` 依赖
- [ ] `entity/MedicationGuide.java` —— 新实体
- [ ] `mapper/MedicationGuideMapper.java` + `mapper/MedicationGuideMapper.xml` —— CRUD
- [ ] `service/AiPharmacyClient.java` —— 新增 `generateMedicationGuide(ctx)` 方法
- [ ] `service/PharmacyService.java` —— 新增 `generateAndSaveMedicationGuide(registerId)` + `getLatestMedicationGuide(registerId)` + `retryMedicationGuide(registerId)`；在 `dispense` 的 afterCommit 钩子里追加调用
- [ ] **`service/PharmacyPdfRenderer.java`** —— 新增，JSON → HTML → PDF 渲染器
- [ ] **`resources/templates/medication-guide.html`** —— PDF 模板骨架（含内嵌 CSS）
- [ ] `controller/PharmacyController.java` —— 新增 3 个端点：
  - `GET /medication-guide/{registerId}`（查数据状态）
  - **`GET /medication-guide/{registerId}/pdf`（下载 PDF）**
  - `POST /medication-guide/{registerId}/retry`（重试生成数据）

### 9.4 前端（2 个文件）
- [ ] `src/shared/api/modules/pharmacy.ts` —— 新增 `getMedicationGuideStatus()` + `retryMedicationGuide()` 函数（**下载 PDF 用 `window.open`，不走 axios**）
- [ ] `src/shared/types/pharmacy.ts` —— 新增 `MedicationGuide` 类型
- [ ] `src/modules/pharmacy/pages/DispensingPage.vue` —— 新增"下载用药指导单"按钮 + 状态探测 + 下载逻辑

### 9.5 不动的部分
- 医生端任何代码（`physician-service`、`physician/` 前端）
- 现有 `POST /api/ai/pharmacy/guide` 单药品接口
- 现有发药、退药、审方、查看发药单逻辑

---

## 10. 后续演进路径（不在本次范围）

| 阶段 | 工作 |
|---|---|
| P1.5 接真模型 | `AiPharmacyService.generateMedicationGuide` 内部接 LLM，输出 `takeWithFood` 等更结构化字段；schema 校验 + 降级 |
| P2 患者端展示 | 患者端 H5/小程序展示指导单（需鉴权调整） |
| P2 配伍检查 | 新增 `interactions` 字段做强提示（详见下一份设计文档） |
| P2 诊断联动 | 把 `medical_record.diagnosis` 接入 ctx，让指导单能"结合诊断给建议" |

---

## 11. 待评审问题

请确认以下几点，再开始实现：

1. **表名 `medication_guide`** 是否 OK？还是用 `prescription_guide` / `dispensing_guide`？
2. **PDF 模板风格**：医院正式单据风格（header + 表格 + 签名位，适合打印归档），还是患者友好卡片风格（大字号、简单排版）？默认按"正式单据"做。
3. **PDF 文件名**：`medication-guide-{registerId}.pdf` 这种格式行不行？要不要带患者名/日期？
4. **`source` 字段的 mock 标记**：是否需要在 PDF 角落显示"演示数据"水印，避免答辩时被误认为是真 AI？
5. **失败重试上限**：手动重试接口要不要限频（避免疯狂重试烧钱）？mock 阶段无所谓，接真模型时再加？
6. **N+1 查询**：mock 阶段先容忍，还是要一并优化掉？
