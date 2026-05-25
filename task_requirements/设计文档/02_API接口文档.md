# 东软云医院系统 — API 接口文档

> 版本：v1.0
> 日期：2026-05-23
> 状态：初稿

---

## 1. 文档概述

### 1.1 编写目的

本文档定义了东软云医院系统全部 RESTful API 接口，是前后端开发的契约文档。后端按本文档实现接口，前端按本文档发起请求，双方以本文档为准进行联调。

### 1.2 通用约定

#### 1.2.1 基础 URL

```
开发环境: http://localhost:8080
```

所有接口均通过 API 网关（gateway-service :8080）统一入口访问，网关负责路由转发到各微服务。

#### 1.2.2 统一响应格式

所有接口（除特殊说明外）统一返回以下 JSON 格式：

```json
{
    "code": 200,
    "message": "操作成功",
    "data": { ... }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | Integer | 业务状态码 |
| message | String | 状态描述 |
| data | Object / Array / null | 业务数据，失败或无数据时为 null |

#### 1.2.3 业务状态码

| code | 含义 | 场景 |
| --- | --- | --- |
| 200 | 成功 | 正常业务响应 |
| 400 | 参数错误 | 请求参数校验失败 |
| 401 | 未认证 | Token 缺失或已过期 |
| 403 | 无权限 | 当前角色无权访问该接口 |
| 404 | 资源不存在 | 查询的数据不存在 |
| 409 | 业务冲突 | 重复挂号、处方已审核、状态不允许操作等 |
| 500 | 服务异常 | 服务内部错误 |
| 503 | AI 服务不可用 | 大模型 API 调用失败 |

#### 1.2.4 认证方式

需要认证的接口，请求头必须携带 JWT Token：

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

#### 1.2.5 分页参数

所有列表查询接口统一使用以下分页参数：

| 参数 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| page | Integer | 1 | 当前页码（从 1 开始） |
| size | Integer | 10 | 每页条数 |

分页响应格式：

```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "records": [ ... ],
        "total": 100,
        "page": 1,
        "size": 10,
        "totalPages": 10
    }
}
```

#### 1.2.6 日期时间格式

| 场景 | 格式 | 示例 |
| --- | --- | --- |
| 日期 | yyyy-MM-dd | 2026-05-23 |
| 时间 | yyyy-MM-dd HH:mm:ss | 2026-05-23 14:30:00 |
| 时间戳（数据库） | DATETIME | CURRENT_TIMESTAMP |

---

## 2. 认证鉴权模块 (auth-service :8081)

### 2.1 用户登录

```
POST /api/auth/login
```

**请求体：**

```json
{
    "username": "zhangyi",
    "password": "123456"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| username | String | 是 | 用户姓名或工号 |
| password | String | 是 | 密码 |

**响应：**

```json
{
    "code": 200,
    "message": "登录成功",
    "data": {
        "token": "eyJhbGciOiJIUzI1NiJ9...",
        "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
        "userId": 1,
        "realname": "张医生",
        "role": "physician",
        "deptId": 5,
        "deptName": "内科",
        "registLevelId": 2,
        "registLevelName": "专家号"
    }
}
```

### 2.2 用户登出

```
POST /api/auth/logout
```

**请求头：** `Authorization: Bearer {token}`

**响应：**

```json
{
    "code": 200,
    "message": "登出成功",
    "data": null
}
```

### 2.3 刷新 Token

```
POST /api/auth/refresh
```

**请求体：**

```json
{
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**响应：**

```json
{
    "code": 200,
    "message": "刷新成功",
    "data": {
        "token": "eyJhbGciOiJIUzI1NiJ9...(新Token)",
        "refreshToken": "eyJhbGciOiJIUzI1NiJ9...(新RefreshToken)"
    }
}
```

### 2.4 获取当前登录用户信息

```
GET /api/auth/userinfo
```

**请求头：** `Authorization: Bearer {token}`

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "userId": 1,
        "realname": "张医生",
        "role": "physician",
        "deptId": 5,
        "deptName": "内科",
        "registLevelId": 2,
        "registLevelName": "专家号",
        "schedulingId": 3
    }
}
```

---

## 3. 挂号收费模块 (registration-service :8091)

### 3.1 窗口挂号

#### 3.1.1 获取挂号级别列表

```
GET /api/registration/regist-levels
```

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": [
        {
            "id": 1,
            "registCode": "PT",
            "registName": "普通号",
            "registFee": 5.00,
            "registQuota": 50,
            "sequenceNo": 1
        },
        {
            "id": 2,
            "registCode": "ZJ",
            "registName": "专家号",
            "registFee": 15.00,
            "registQuota": 20,
            "sequenceNo": 2
        }
    ]
}
```

#### 3.1.2 获取科室列表

```
GET /api/registration/departments
```

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": [
        {
            "id": 1,
            "deptCode": "NK",
            "deptName": "内科",
            "deptType": "临床科室"
        },
        {
            "id": 2,
            "deptCode": "WK",
            "deptName": "外科",
            "deptType": "临床科室"
        }
    ]
}
```

#### 3.1.3 获取结算类别列表

```
GET /api/registration/settle-categories
```

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": [
        { "id": 1, "settleCode": "ZF", "settleName": "自费", "sequenceNo": 1 },
        { "id": 2, "settleCode": "YB", "settleName": "医保", "sequenceNo": 2 },
        { "id": 3, "settleCode": "XNH", "settleName": "新农合", "sequenceNo": 3 }
    ]
}
```

#### 3.1.4 根据科室+级别+午别查询出诊医生

```
GET /api/registration/available-doctors
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deptmentId | Integer | 是 | 科室 ID |
| registLevelId | Integer | 是 | 挂号级别 ID |
| noon | String | 是 | 午别：上午/下午 |

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": [
        { "id": 5, "realname": "李医生", "deptName": "内科", "registLevelName": "专家号" },
        { "id": 8, "realname": "王医生", "deptName": "内科", "registLevelName": "专家号" }
    ]
}
```

#### 3.1.5 生成病历号

```
GET /api/registration/next-case-number
```

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "caseNumber": "BL20260523001"
    }
}
```

#### 3.1.6 提交挂号

```
POST /api/registration/register
```

**请求体：**

```json
{
    "caseNumber": "BL20260523001",
    "realName": "张三",
    "gender": "男",
    "birthdate": "1990-05-15",
    "age": 36,
    "ageType": "年",
    "cardNumber": "210102199005150012",
    "homeAddress": "沈阳市和平区XX路XX号",
    "deptmentId": 1,
    "employeeId": 5,
    "registLevelId": 2,
    "settleCategoryId": 1,
    "isBook": "是",
    "registMethod": "现金",
    "noon": "上午",
    "aiTriageId": 10
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| caseNumber | String | 是 | 病历号 |
| realName | String | 是 | 患者姓名 |
| gender | String | 是 | 性别：男/女 |
| birthdate | String | 是 | 出生日期 yyyy-MM-dd |
| age | Integer | 是 | 年龄 |
| ageType | String | 是 | 年龄类型：年/天 |
| cardNumber | String | 否 | 身份证号 |
| homeAddress | String | 否 | 家庭住址 |
| deptmentId | Integer | 是 | 挂号科室 ID |
| employeeId | Integer | 是 | 挂号医生 ID |
| registLevelId | Integer | 是 | 挂号级别 ID |
| settleCategoryId | Integer | 是 | 结算类别 ID |
| isBook | String | 是 | 是否要病历本：是/否 |
| registMethod | String | 是 | 收费方式：现金/银行卡/医保卡/微信/支付宝 |
| noon | String | 是 | 午别：上午/下午 |
| aiTriageId | Integer | 否 | 关联的 AI 导诊记录 ID（若有） |

**响应：**

```json
{
    "code": 200,
    "message": "挂号成功",
    "data": {
        "id": 1001,
        "caseNumber": "BL20260523001",
        "realName": "张三",
        "visitDate": "2026-05-23 09:15:30",
        "registMoney": 15.00,
        "visitState": 1
    }
}
```

#### 3.1.7 退号

```
PUT /api/registration/register/{id}/cancel
```

**路径参数：**

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| id | Integer | 挂号记录 ID |

**业务规则：** 只有 `visit_state=1`（已挂号）且未产生费用的记录可以退号。退号后 `visit_state` 变为 `4`（已退号）。

**响应：**

```json
{
    "code": 200,
    "message": "退号成功",
    "data": null
}
```

### 3.2 收费管理

#### 3.2.1 查询患者待缴费项目

```
GET /api/registration/patient-charges
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| caseNumber | String | 是 | 病历号 |

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "patientInfo": {
            "caseNumber": "BL20260523001",
            "realName": "张三",
            "gender": "男",
            "age": 36
        },
        "chargeItems": [
            {
                "id": 1,
                "type": "check",
                "typeName": "检查",
                "techName": "胸部CT",
                "techPrice": 280.00,
                "creationTime": "2026-05-23 10:00:00",
                "paid": false
            },
            {
                "id": 2,
                "type": "inspection",
                "typeName": "检验",
                "techName": "血常规",
                "techPrice": 35.00,
                "creationTime": "2026-05-23 10:01:00",
                "paid": false
            }
        ]
    }
}
```

#### 3.2.2 收费结算

```
POST /api/registration/charge
```

**请求体：**

```json
{
    "registerId": 1001,
    "itemIds": [1, 2],
    "itemTypes": ["check", "inspection"],
    "payMethod": "现金"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| registerId | Integer | 是 | 挂号记录 ID |
| itemIds | Array<Integer> | 是 | 待缴费的项目 ID 列表 |
| itemTypes | Array<String> | 是 | 项目类型列表，与 itemIds 一一对应：check/inspection/disposal |
| payMethod | String | 是 | 支付方式：现金/银行卡/医保卡/微信/支付宝 |

**响应：**

```json
{
    "code": 200,
    "message": "收费成功",
    "data": {
        "totalAmount": 315.00,
        "payMethod": "现金",
        "chargeTime": "2026-05-23 11:00:00"
    }
}
```

#### 3.2.3 退费

```
POST /api/registration/refund
```

**请求体：**

```json
{
    "registerId": 1001,
    "itemIds": [2],
    "itemTypes": ["inspection"]
}
```

**响应：**

```json
{
    "code": 200,
    "message": "退费成功",
    "data": {
        "refundAmount": 35.00,
        "refundTime": "2026-05-23 14:00:00"
    }
}
```

### 3.3 费用查询

#### 3.3.1 查询患者费用记录

```
GET /api/registration/expense-records
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| caseNumber | String | 否 | 病历号 |
| startDate | String | 否 | 开始日期 yyyy-MM-dd |
| endDate | String | 否 | 结束日期 yyyy-MM-dd |
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 10 |

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "records": [
            {
                "id": 1,
                "caseNumber": "BL20260523001",
                "realName": "张三",
                "type": "check",
                "typeName": "检查",
                "techName": "胸部CT",
                "amount": 280.00,
                "payMethod": "现金",
                "chargeTime": "2026-05-23 11:00:00",
                "status": "已缴费"
            }
        ],
        "total": 5,
        "page": 1,
        "size": 10,
        "totalPages": 1
    }
}
```

---

## 4. 门诊医生模块 (physician-service :8092)

### 4.1 患者查看

#### 4.1.1 获取当前医生待诊患者列表

```
GET /api/physician/patients
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| keyword | String | 否 | 病历号或姓名模糊搜索 |
| page | Integer | 否 | 页码 |
| size | Integer | 否 | 每页条数 |

**请求头：** Token 中解析出当前登录医生 ID

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "records": [
            {
                "registerId": 1001,
                "caseNumber": "BL20260523001",
                "realName": "张三",
                "gender": "男",
                "age": 36,
                "visitDate": "2026-05-23 09:15:30",
                "visitState": 1,
                "hasAiConsultation": true,
                "aiConsultSummary": {
                    "chiefComplaint": "头痛3天",
                    "symptomDuration": "3天",
                    "historySummary": "无重大既往病史",
                    "suggestedExam": "建议进行头颅CT、血常规"
                }
            }
        ],
        "total": 15,
        "page": 1,
        "size": 10,
        "totalPages": 2
    }
}
```

#### 4.1.2 获取看诊统计

```
GET /api/physician/patient-stats
```

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "totalVisited": 8,
        "totalWaiting": 7
    }
}
```

### 4.2 病历首页

#### 4.2.1 创建病历

```
POST /api/physician/medical-record
```

**请求体：**

```json
{
    "registerId": 1001,
    "readme": "头痛3天，双侧太阳穴持续性胀痛",
    "present": "患者3天前无明显诱因出现双侧太阳穴持续性胀痛，伴恶心，无呕吐",
    "presentTreat": "自行服用止痛药（具体不详），效果不佳",
    "history": "既往体健，无高血压、糖尿病病史",
    "allergy": "青霉素过敏",
    "physique": "体温36.8°C，血压120/80mmHg",
    "proposal": "建议行头颅CT、血常规检查",
    "diseaseIds": [15]
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| registerId | Integer | 是 | 挂号记录 ID |
| readme | String | 是 | 主诉 |
| present | String | 否 | 现病史 |
| presentTreat | String | 否 | 现病治疗情况 |
| history | String | 否 | 既往史 |
| allergy | String | 否 | 过敏史 |
| physique | String | 否 | 体格检查 |
| proposal | String | 否 | 检查/检验建议 |
| diseaseIds | Array<Integer> | 否 | 初步诊断关联的疾病 ID 列表 |

**响应：**

```json
{
    "code": 200,
    "message": "病历创建成功",
    "data": {
        "id": 501,
        "registerId": 1001
    }
}
```

#### 4.2.2 更新病历

```
PUT /api/physician/medical-record/{id}
```

**请求体：** 同创建病历，字段均可选。

**响应：**

```json
{
    "code": 200,
    "message": "病历更新成功",
    "data": null
}
```

#### 4.2.3 获取病历详情

```
GET /api/physician/medical-record
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| registerId | Integer | 是 | 挂号记录 ID |

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "id": 501,
        "registerId": 1001,
        "readme": "头痛3天，双侧太阳穴持续性胀痛",
        "present": "患者3天前无明显诱因出现双侧太阳穴持续性胀痛...",
        "presentTreat": "自行服用止痛药，效果不佳",
        "history": "既往体健",
        "allergy": "青霉素过敏",
        "physique": "体温36.8°C，血压120/80mmHg",
        "proposal": "建议行头颅CT、血常规检查",
        "careful": null,
        "diagnosis": null,
        "cure": null,
        "diseases": [
            { "id": 15, "diseaseName": "偏头痛", "diseaseIcd": "G43.9" }
        ]
    }
}
```

### 4.3 检查申请

#### 4.3.1 查询医技项目列表（检查类）

```
GET /api/physician/medical-technologies
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| techType | String | 是 | 类型：check-检查 / inspection-检验 / disposal-处置 |
| keyword | String | 否 | 项目编码或名称模糊搜索 |

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": [
        {
            "id": 1,
            "techCode": "XJCT",
            "techName": "胸部CT",
            "techFormat": "平扫",
            "techPrice": 280.00,
            "techType": "check",
            "priceType": "检查费",
            "deptmentId": 10,
            "deptName": "放射科"
        }
    ]
}
```

#### 4.3.2 提交检查申请

```
POST /api/physician/check-request
```

**请求体：**

```json
{
    "registerId": 1001,
    "items": [
        {
            "medicalTechnologyId": 1,
            "checkInfo": "排查肺部感染",
            "checkPosition": "胸部",
            "checkRemark": ""
        },
        {
            "medicalTechnologyId": 3,
            "checkInfo": "排查颅内病变",
            "checkPosition": "头部",
            "checkRemark": ""
        }
    ]
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| registerId | Integer | 是 | 挂号记录 ID |
| items | Array | 是 | 检查项目列表 |
| items[].medicalTechnologyId | Integer | 是 | 医技项目 ID |
| items[].checkInfo | String | 否 | 目的要求 |
| items[].checkPosition | String | 否 | 检查部位 |
| items[].checkRemark | String | 否 | 备注 |

**响应：**

```json
{
    "code": 200,
    "message": "检查申请提交成功",
    "data": {
        "requestIds": [2001, 2002]
    }
}
```

#### 4.3.3 提交检验申请

```
POST /api/physician/inspection-request
```

**请求体：**

```json
{
    "registerId": 1001,
    "items": [
        {
            "medicalTechnologyId": 5,
            "inspectionInfo": "常规血液检查",
            "inspectionPosition": "静脉血",
            "inspectionRemark": ""
        }
    ]
}
```

字段结构同检查申请，前缀改为 `inspection`。

#### 4.3.4 提交处置申请

```
POST /api/physician/disposal-request
```

**请求体：**

```json
{
    "registerId": 1001,
    "items": [
        {
            "medicalTechnologyId": 8,
            "disposalInfo": "胃部冲洗",
            "disposalPosition": "胃部",
            "disposalRemark": ""
        }
    ]
}
```

字段结构同检查申请，前缀改为 `disposal`。

### 4.4 看诊记录

#### 4.4.1 获取已看诊患者列表

```
GET /api/physician/history-patients
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| keyword | String | 否 | 病历号或姓名模糊搜索 |
| page | Integer | 否 | 页码 |
| size | Integer | 否 | 每页条数 |

**响应：** 同 4.1.1 待诊患者列表结构，`visitState` 为 `2`（医生接诊）。

### 4.5 检查/检验结果查看

#### 4.5.1 获取患者检查结果列表

```
GET /api/physician/check-results
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| registerId | Integer | 是 | 挂号记录 ID |

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": [
        {
            "id": 2001,
            "techName": "胸部CT",
            "checkPosition": "胸部",
            "checkResult": "双肺纹理清晰，未见明显实变影",
            "checkState": "已完成",
            "checkTime": "2026-05-23 14:30:00",
            "aiAnalysis": {
                "riskLevel": "normal",
                "analysisReport": "检查结果未见明显异常，胸部CT影像正常。",
                "abnormalIndicators": null,
                "correlationAnalysis": "结合患者头痛症状，胸部检查结果与头痛无直接关联。"
            }
        }
    ]
}
```

#### 4.5.2 获取患者检验结果列表

```
GET /api/physician/inspection-results
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| registerId | Integer | 是 | 挂号记录 ID |

**响应：** 结构同检查结果，字段前缀改为 `inspection`。

### 4.6 门诊确诊

#### 4.6.1 提交确诊结果

```
POST /api/physician/diagnosis
```

**请求体：**

```json
{
    "registerId": 1001,
    "medicalRecordId": 501,
    "diagnosis": "偏头痛",
    "cure": "药物治疗，注意休息，避免诱发因素",
    "careful": "规律作息，避免强光刺激",
    "diseaseIds": [15]
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| registerId | Integer | 是 | 挂号记录 ID |
| medicalRecordId | Integer | 是 | 病历 ID |
| diagnosis | String | 是 | 诊断结果 |
| cure | String | 是 | 处理意见 |
| careful | String | 否 | 注意事项 |
| diseaseIds | Array<Integer> | 是 | 确诊疾病 ID 列表 |

**响应：**

```json
{
    "code": 200,
    "message": "确诊提交成功",
    "data": null
}
```

### 4.7 开立处方

#### 4.7.1 查询药品列表

```
GET /api/physician/drugs
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| keyword | String | 否 | 药品编码、名称或拼音助记码模糊搜索 |

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": [
        {
            "id": 1,
            "drugCode": "ASP001",
            "drugName": "阿司匹林肠溶片",
            "drugFormat": "100mg*30片/盒",
            "drugUnit": "盒",
            "manufacturer": "拜耳医药",
            "drugDosage": "片剂",
            "drugType": "西药",
            "drugPrice": 25.80,
            "mnemonicCode": "ASPL"
        }
    ]
}
```

#### 4.7.2 获取药品详情

```
GET /api/physician/drugs/{id}
```

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "id": 1,
        "drugCode": "ASP001",
        "drugName": "阿司匹林肠溶片",
        "drugFormat": "100mg*30片/盒",
        "drugUnit": "盒",
        "manufacturer": "拜耳医药",
        "drugDosage": "片剂",
        "drugType": "西药",
        "drugPrice": 25.80,
        "mnemonicCode": "ASPL",
        "creationDate": "2026-01-01"
    }
}
```

#### 4.7.3 开立处方

```
POST /api/physician/prescription
```

**请求体：**

```json
{
    "registerId": 1001,
    "items": [
        {
            "drugId": 1,
            "drugUsage": "口服，一次100mg，一日1次",
            "drugNumber": 2
        },
        {
            "drugId": 5,
            "drugUsage": "口服，一次5mg，一日1次，睡前服用",
            "drugNumber": 1
        }
    ]
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| registerId | Integer | 是 | 挂号记录 ID |
| items | Array | 是 | 药品列表 |
| items[].drugId | Integer | 是 | 药品 ID |
| items[].drugUsage | String | 是 | 用法用量频次 |
| items[].drugNumber | String | 是 | 数量 |

**响应：**

```json
{
    "code": 200,
    "message": "处方开立成功",
    "data": {
        "prescriptionIds": [3001, 3002],
        "totalAmount": 76.60,
        "aiReviewResult": {
            "reviewResult": "passed",
            "riskScore": 0,
            "riskDetails": null
        }
    }
}
```

> **说明**：开立处方时会自动触发 AI 处方审核，审核结果随响应一起返回。若审核结果为 `warning` 或 `rejected`，前端弹出风险提示。

#### 4.7.4 删除处方中的药品

```
DELETE /api/physician/prescription/{id}
```

**路径参数：** 处方记录 ID（单条药品记录）

**响应：**

```json
{
    "code": 200,
    "message": "删除成功",
    "data": null
}
```

---

## 5. 医技检查模块 (medtech-service :8093)

### 5.1 检查管理

#### 5.1.1 获取待检查患者列表

```
GET /api/medtech/check/applications
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| keyword | String | 否 | 病历号或姓名模糊搜索 |
| page | Integer | 否 | 页码 |
| size | Integer | 否 | 每页条数 |

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "records": [
            {
                "registerId": 1001,
                "caseNumber": "BL20260523001",
                "realName": "张三",
                "techName": "胸部CT",
                "checkInfo": "排查肺部感染",
                "checkPosition": "胸部",
                "checkState": "待检查",
                "creationTime": "2026-05-23 10:00:00"
            }
        ],
        "total": 3,
        "page": 1,
        "size": 10,
        "totalPages": 1
    }
}
```

#### 5.1.2 获取待检查统计

```
GET /api/medtech/check/stats
```

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "totalChecked": 5,
        "totalWaiting": 3
    }
}
```

#### 5.1.3 开始检查（登记）

```
PUT /api/medtech/check/start/{id}
```

将检查申请状态从"待检查"变为"检查中"，并记录检查医生信息。

**路径参数：** 检查申请 ID

**请求体：**

```json
{
    "checkEmployeeId": 20
}
```

**响应：**

```json
{
    "code": 200,
    "message": "检查登记成功",
    "data": null
}
```

#### 5.1.4 获取已完成检查的患者列表

```
GET /api/medtech/check/completed
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| keyword | String | 否 | 病历号或姓名模糊搜索 |
| page | Integer | 否 | 页码 |
| size | Integer | 否 | 每页条数 |

**响应：** 同 5.1.1 结构，`checkState` 为"已完成"。

#### 5.1.5 录入检查结果

```
PUT /api/medtech/check/result/{id}
```

**路径参数：** 检查申请 ID

**请求体：**

```json
{
    "checkResult": "双肺纹理清晰，未见明显实变影，心影大小形态正常",
    "inputcheckEmployeeId": 20,
    "checkRemark": ""
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| checkResult | String | 是 | 检查结果 |
| inputcheckEmployeeId | Integer | 是 | 结果录入医生 ID |
| checkRemark | String | 否 | 备注 |

**响应：**

```json
{
    "code": 200,
    "message": "检查结果录入成功",
    "data": null
}
```

> **说明**：检查结果录入成功后，系统自动触发 AI 检查结果分析（异步）。

### 5.2 检验管理

#### 5.2.1 获取待检验患者列表

```
GET /api/medtech/inspection/applications
```

**查询参数：** 同检查管理 5.1.1

**响应：** 结构同 5.1.1，字段前缀改为 `inspection`。

#### 5.2.2 开始检验（登记）

```
PUT /api/medtech/inspection/start/{id}
```

**请求体：**

```json
{
    "inspectionEmployeeId": 25
}
```

#### 5.2.3 获取已完成检验的患者列表

```
GET /api/medtech/inspection/completed
```

#### 5.2.4 录入检验结果

```
PUT /api/medtech/inspection/result/{id}
```

**请求体：**

```json
{
    "inspectionResult": "WBC 15.2×10^9/L, RBC 4.5×10^12/L, HGB 135g/L, PLT 220×10^9/L",
    "inputinspectionEmployeeId": 25,
    "inspectionRemark": "白细胞偏高"
}
```

### 5.3 处置管理

#### 5.3.1 获取待处置患者列表

```
GET /api/medtech/disposal/applications
```

#### 5.3.2 开始处置（登记）

```
PUT /api/medtech/disposal/start/{id}
```

**请求体：**

```json
{
    "disposalEmployeeId": 30
}
```

#### 5.3.3 获取已完成处置的患者列表

```
GET /api/medtech/disposal/completed
```

#### 5.3.4 录入处置结果

```
PUT /api/medtech/disposal/result/{id}
```

**请求体：**

```json
{
    "disposalResult": "洗胃完成，胃内容物已清除，患者状态稳定",
    "inputdisposalEmployeeId": 30,
    "disposalRemark": ""
}
```

---

## 6. 药房管理模块 (pharmacy-service :8094)

### 6.1 药品管理

#### 6.1.1 获取药品列表

```
GET /api/pharmacy/drugs
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| keyword | String | 否 | 药品编码、名称或助记码 |
| drugType | String | 否 | 药品类型筛选 |
| page | Integer | 否 | 页码 |
| size | Integer | 否 | 每页条数 |

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "records": [
            {
                "id": 1,
                "drugCode": "ASP001",
                "drugName": "阿司匹林肠溶片",
                "drugFormat": "100mg*30片/盒",
                "drugUnit": "盒",
                "manufacturer": "拜耳医药",
                "drugDosage": "片剂",
                "drugType": "西药",
                "drugPrice": 25.80,
                "mnemonicCode": "ASPL"
            }
        ],
        "total": 50,
        "page": 1,
        "size": 10,
        "totalPages": 5
    }
}
```

#### 6.1.2 新增药品

```
POST /api/pharmacy/drugs
```

**请求体：**

```json
{
    "drugCode": "ASP001",
    "drugName": "阿司匹林肠溶片",
    "drugFormat": "100mg*30片/盒",
    "drugUnit": "盒",
    "manufacturer": "拜耳医药",
    "drugDosage": "片剂",
    "drugType": "西药",
    "drugPrice": 25.80,
    "mnemonicCode": "ASPL"
}
```

#### 6.1.3 更新药品信息

```
PUT /api/pharmacy/drugs/{id}
```

**请求体：** 同新增，字段均可选。

#### 6.1.4 删除药品（逻辑删除）

```
DELETE /api/pharmacy/drugs/{id}
```

### 6.2 发药管理

#### 6.2.1 获取待发药患者列表

```
GET /api/pharmacy/pending
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| keyword | String | 否 | 病历号或姓名 |
| status | String | 否 | 状态筛选：未发(默认)/已发/已退/全部 |
| page | Integer | 否 | 页码 |
| size | Integer | 否 | 每页条数 |

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "records": [
            {
                "registerId": 1001,
                "caseNumber": "BL20260523001",
                "realName": "张三",
                "prescriptions": [
                    {
                        "id": 3001,
                        "drugName": "阿司匹林肠溶片",
                        "drugFormat": "100mg*30片/盒",
                        "drugUsage": "口服，一次100mg，一日1次",
                        "drugNumber": "2",
                        "drugPrice": 25.80,
                        "drugState": "未发"
                    }
                ],
                "totalAmount": 76.60
            }
        ],
        "total": 3,
        "page": 1,
        "size": 10,
        "totalPages": 1
    }
}
```

#### 6.2.2 确认发药

```
PUT /api/pharmacy/dispense/{registerId}
```

**路径参数：** 挂号记录 ID

**响应：**

```json
{
    "code": 200,
    "message": "发药成功",
    "data": {
        "registerId": 1001,
        "dispenseTime": "2026-05-23 16:00:00",
        "followUpGenerated": true
    }
}
```

> **说明**：发药成功后，系统自动触发 AI 用药随访计划生成（异步）。

#### 6.2.3 退药

```
PUT /api/pharmacy/return/{registerId}
```

**路径参数：** 挂号记录 ID

**响应：**

```json
{
    "code": 200,
    "message": "退药成功",
    "data": null
}
```

### 6.3 交易记录

#### 6.3.1 获取交易记录列表

```
GET /api/pharmacy/transactions
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| keyword | String | 否 | 病历号或姓名 |
| startDate | String | 否 | 开始日期 |
| endDate | String | 否 | 结束日期 |
| type | String | 否 | 类型：发药/退药/全部 |
| page | Integer | 否 | 页码 |
| size | Integer | 否 | 每页条数 |

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "records": [
            {
                "id": 3001,
                "caseNumber": "BL20260523001",
                "realName": "张三",
                "drugName": "阿司匹林肠溶片",
                "drugNumber": "2",
                "type": "发药",
                "time": "2026-05-23 16:00:00"
            }
        ],
        "total": 10,
        "page": 1,
        "size": 10,
        "totalPages": 1
    }
}
```

---

## 7. AI 智能服务模块 (ai-gateway-service :8100)

所有 AI 接口统一通过网关 `:8080/api/ai/...` 访问，网关路由到 `ai-gateway-service(:8100)` 后分发到各 AI 子服务。

### 7.1 AI 智能导诊

#### 7.1.1 导诊分析

```
POST /api/ai/triage/analyze
```

**请求体：**

```json
{
    "patientName": "张三",
    "patientAge": 35,
    "patientGender": "男",
    "symptomDescription": "持续胸痛三天，伴有咳嗽和发热，体温38.5°C"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| patientName | String | 否 | 患者姓名 |
| patientAge | Integer | 否 | 患者年龄 |
| patientGender | String | 否 | 患者性别：男/女 |
| symptomDescription | String | 是 | 症状描述文本 |

**响应：**

```json
{
    "code": 200,
    "message": "分析完成",
    "data": {
        "triageId": 10,
        "recommendDeptId": 5,
        "recommendDeptName": "心内科",
        "recommendDoctorId": 12,
        "recommendDoctorName": "李主任",
        "riskLevel": "urgent",
        "isPriority": true,
        "aiAnalysis": "根据患者持续胸痛3天伴发热咳嗽的症状描述，建议优先排查心血管方面的问题，推荐挂心内科。发热提示可能存在感染，需进一步检查确认。"
    }
}
```

#### 7.1.2 查询导诊历史记录

```
GET /api/ai/triage/records
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| patientName | String | 否 | 患者姓名 |
| startDate | String | 否 | 开始日期 |
| endDate | String | 否 | 结束日期 |
| page | Integer | 否 | 页码 |
| size | Integer | 否 | 每页条数 |

### 7.2 AI 预问诊

#### 7.2.1 开始预问诊

```
POST /api/ai/consult/start
```

**请求体：**

```json
{
    "registerId": 1001
}
```

**响应：**

```json
{
    "code": 200,
    "message": "问诊开始",
    "data": {
        "consultationId": 201,
        "roundNumber": 1,
        "aiQuestion": "您好，我是 AI 问诊助手。请问您今天主要哪里不舒服？请描述一下您的症状。",
        "consultationState": "in_progress"
    }
}
```

#### 7.2.2 问诊对话（发送患者回答，获取 AI 下一轮提问）

```
POST /api/ai/consult/chat
```

**请求体：**

```json
{
    "registerId": 1001,
    "patientAnswer": "头疼三天了，主要是太阳穴两侧疼，有时候会恶心"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| registerId | Integer | 是 | 挂号记录 ID |
| patientAnswer | String | 是 | 患者的回答内容 |

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "roundNumber": 2,
        "aiQuestion": "请问头疼是持续性的还是阵发性的？有没有伴随视力模糊的情况？之前有没有类似的情况？",
        "consultationState": "in_progress"
    }
}
```

#### 7.2.3 结束问诊并生成摘要

```
POST /api/ai/consult/complete
```

**请求体：**

```json
{
    "registerId": 1001
}
```

**响应：**

```json
{
    "code": 200,
    "message": "问诊完成",
    "data": {
        "consultationId": 201,
        "chiefComplaint": "双侧太阳穴持续性头痛3天，伴恶心",
        "symptomDuration": "3天",
        "historySummary": "既往体健，无高血压、糖尿病病史",
        "allergySummary": "青霉素过敏",
        "medicationSummary": "近3天自行服用布洛芬，效果不佳",
        "aiSummary": "患者3天前出现双侧太阳穴持续性头痛，伴有恶心，自行服用布洛芬效果不佳。既往体健，青霉素过敏。",
        "suggestedExam": "建议进行头颅CT、血常规、经颅多普勒超声检查",
        "consultationState": "completed",
        "completionTime": "2026-05-23 09:30:00"
    }
}
```

#### 7.2.4 查询问诊记录

```
GET /api/ai/consult/records
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| registerId | Integer | 是 | 挂号记录 ID |

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": [
        {
            "id": 201,
            "roundNumber": 1,
            "aiQuestion": "您好，请问您今天主要哪里不舒服？",
            "patientAnswer": "头疼三天了，主要是太阳穴两侧疼",
            "consultationState": "completed"
        },
        {
            "id": 202,
            "roundNumber": 2,
            "aiQuestion": "请问头疼是持续性的还是阵发性的？",
            "patientAnswer": "持续性的，有时候会恶心",
            "consultationState": "completed"
        }
    ]
}
```

### 7.3 AI 辅助病历生成

#### 7.3.1 生成病历草稿

```
POST /api/ai/medical-record/generate
```

**请求体：**

```json
{
    "registerId": 1001,
    "sourceType": "consultation"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| registerId | Integer | 是 | 挂号记录 ID |
| sourceType | String | 是 | 生成来源：consultation-预问诊 / dictation-医生口述 / exam-检查结果 |

**响应：**

```json
{
    "code": 200,
    "message": "病历草稿生成成功",
    "data": {
        "logId": 301,
        "aiReadme": "双侧太阳穴持续性头痛3天，伴恶心",
        "aiPresent": "患者3天前无明显诱因出现双侧太阳穴持续性胀痛，疼痛程度中等，伴有恶心，无呕吐，无视力模糊。自行服用布洛芬后疼痛可暂时缓解，但反复发作。",
        "aiHistory": "既往体健，否认高血压、糖尿病、心脏病等慢性病史。",
        "aiAllergy": "青霉素过敏。",
        "aiPhysique": "建议测量体温、血压，进行神经系统查体。",
        "aiDiagnosis": "偏头痛可能，建议进一步行头颅CT排除器质性病变。"
    }
}
```

#### 7.3.2 记录医生对 AI 病历的修改

```
PUT /api/ai/medical-record/feedback
```

**请求体：**

```json
{
    "logId": 301,
    "isAdopted": 1,
    "doctorModification": "{\"readme\":\"修改后的主诉内容\"}"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| logId | Integer | 是 | AI 病历生成日志 ID |
| isAdopted | Integer | 是 | 0-未采纳 / 1-部分采纳 / 2-完全采纳 |
| doctorModification | String | 否 | 医生修改内容（JSON 格式） |

### 7.4 AI 检查推荐与结果分析

#### 7.4.1 获取 AI 检查方案推荐

```
POST /api/ai/exam/suggest
```

**请求体：**

```json
{
    "registerId": 1001
}
```

**响应：**

```json
{
    "code": 200,
    "message": "推荐生成完成",
    "data": [
        {
            "id": 401,
            "techId": 5,
            "techName": "血常规",
            "suggestType": "inspection",
            "suggestReason": "患者有发热症状（38.5°C），建议行血常规检查排查感染",
            "priority": 1,
            "isAdopted": 0
        },
        {
            "id": 402,
            "techId": 2,
            "techName": "头颅CT",
            "suggestType": "check",
            "suggestReason": "患者持续性头痛3天，建议行头颅CT排除颅内病变",
            "priority": 1,
            "isAdopted": 0
        }
    ]
}
```

#### 7.4.2 标记检查推荐采纳状态

```
PUT /api/ai/exam/suggest/{id}/adopt
```

**请求体：**

```json
{
    "isAdopted": 1
}
```

#### 7.4.3 获取检查结果 AI 分析报告

```
GET /api/ai/exam/analysis
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| registerId | Integer | 是 | 挂号记录 ID |
| analysisType | String | 否 | 分析类型：check/inspection，不传则返回全部 |

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": [
        {
            "id": 501,
            "analysisType": "inspection",
            "techName": "血常规",
            "originalResult": "WBC 15.2×10^9/L, RBC 4.5×10^12/L...",
            "abnormalIndicators": "[{\"指标\":\"白细胞(WBC)\",\"值\":\"15.2\",\"参考值\":\"4-10\",\"单位\":\"10^9/L\"}]",
            "riskLevel": "attention",
            "analysisReport": "白细胞计数偏高(15.2×10^9/L)，高于正常参考值(4-10×10^9/L)，提示可能存在感染或炎症反应。建议结合临床症状进一步判断。",
            "correlationAnalysis": "结合患者发热(38.5°C)和咳嗽症状，白细胞升高与感染高度相关，支持上呼吸道感染的初步判断。",
            "isViewed": 0,
            "analysisTime": "2026-05-23 15:00:00"
        }
    ]
}
```

#### 7.4.4 标记分析报告已查看

```
PUT /api/ai/exam/analysis/{id}/view
```

### 7.5 AI 辅助诊断

#### 7.5.1 获取诊断推荐

```
POST /api/ai/diagnosis/analyze
```

**请求体：**

```json
{
    "registerId": 1001
}
```

**响应：**

```json
{
    "code": 200,
    "message": "诊断分析完成",
    "data": [
        {
            "id": 601,
            "diseaseId": 15,
            "diseaseName": "偏头痛",
            "recommendIcd": "G43.9",
            "probability": 72.50,
            "riskLevel": "low",
            "treatmentDirection": "急性发作期可使用曲普坦类药物缓解，日常注意避免诱发因素（强光、噪音、精神紧张），规律作息。",
            "diagnosisBasis": "患者为年轻男性，双侧太阳穴持续性头痛3天伴恶心，符合偏头痛特征。血常规提示轻度感染，需排除继发性头痛。",
            "isAdopted": 0,
            "sortOrder": 1
        },
        {
            "id": 602,
            "diseaseId": 28,
            "diseaseName": "上呼吸道感染",
            "recommendIcd": "J06.9",
            "probability": 45.00,
            "riskLevel": "low",
            "treatmentDirection": "对症治疗，必要时使用抗生素。注意休息，多饮水。",
            "diagnosisBasis": "患者发热38.5°C，咳嗽，白细胞偏高，符合上呼吸道感染表现。头痛可能为感染的伴随症状。",
            "isAdopted": 0,
            "sortOrder": 2
        }
    ]
}
```

#### 7.5.2 标记诊断推荐采纳状态

```
PUT /api/ai/diagnosis/suggestion/{id}/adopt
```

**请求体：**

```json
{
    "isAdopted": 1
}
```

### 7.6 AI 处方审核

#### 7.6.1 提交处方审核

```
POST /api/ai/pharmacy/review
```

**请求体：**

```json
{
    "registerId": 1001,
    "prescriptionId": 3001
}
```

**响应（审核通过）：**

```json
{
    "code": 200,
    "message": "审核完成",
    "data": {
        "reviewId": 701,
        "reviewResult": "passed",
        "drugConflict": null,
        "allergyRisk": null,
        "duplicateDrug": null,
        "dosageCheck": null,
        "riskScore": 0,
        "riskDetails": null
    }
}
```

**响应（审核警告）：**

```json
{
    "code": 200,
    "message": "审核完成，存在风险",
    "data": {
        "reviewId": 701,
        "reviewResult": "warning",
        "drugConflict": "[{\"drugA\":\"阿司匹林\",\"drugB\":\"布洛芬\",\"conflictType\":\"同为NSAIDs，联用增加胃肠道出血风险\",\"suggestion\":\"建议仅保留一种NSAIDs药物\"}]",
        "allergyRisk": null,
        "duplicateDrug": null,
        "dosageCheck": null,
        "riskScore": 65,
        "riskDetails": "处方中阿司匹林与布洛芬均属于非甾体抗炎药(NSAIDs)，联合使用会显著增加胃肠道出血和肾功能损伤的风险。建议仅保留一种NSAIDs药物，或更换为其他类型的止痛药。"
    }
}
```

#### 7.6.2 医生确认处方审核结果

```
PUT /api/ai/pharmacy/review/{id}/confirm
```

**请求体：**

```json
{
    "doctorAction": "overridden"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| doctorAction | String | 是 | accepted-接受建议修改 / overridden-确认强制通过 |

### 7.7 AI 用药随访

#### 7.7.1 生成随访计划

```
POST /api/ai/follow-up/generate
```

> 由药房发药成功后自动调用，前端通常不直接调用。

**请求体：**

```json
{
    "registerId": 1001,
    "prescriptionId": 3001
}
```

**响应：**

```json
{
    "code": 200,
    "message": "随访计划生成成功",
    "data": {
        "plans": [
            {
                "id": 801,
                "followUpDay": 3,
                "plannedDate": "2026-05-26",
                "followUpType": "medication",
                "contentTemplate": "您好，请问您是否按时服用了阿司匹林肠溶片？服药后是否出现恶心、胃痛等不适？",
                "planStatus": "pending"
            },
            {
                "id": 802,
                "followUpDay": 7,
                "plannedDate": "2026-05-30",
                "followUpType": "recovery",
                "contentTemplate": "您好，头痛症状是否有所缓解？是否需要复诊？",
                "planStatus": "pending"
            }
        ]
    }
}
```

#### 7.7.2 获取患者随访计划列表

```
GET /api/ai/follow-up/plans
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| registerId | Integer | 是 | 挂号记录 ID |

#### 7.7.3 提交随访反馈

```
POST /api/ai/follow-up/feedback
```

**请求体：**

```json
{
    "followUpPlanId": 801,
    "registerId": 1001,
    "isOnTime": 1,
    "hasSideEffect": 0,
    "sideEffect": null,
    "symptomRelief": "partial",
    "needRevisit": 0,
    "patientFeedback": "头疼比之前好了一些，但还是偶尔会疼"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| followUpPlanId | Integer | 是 | 随访计划 ID |
| registerId | Integer | 是 | 挂号记录 ID |
| isOnTime | Integer | 否 | 是否按时服药：0-否 / 1-是 |
| hasSideEffect | Integer | 否 | 是否出现副作用：0-否 / 1-是 |
| sideEffect | String | 否 | 副作用描述 |
| symptomRelief | String | 否 | 症状缓解：relieved/partial/unchanged/worsened |
| needRevisit | Integer | 否 | 是否需要复诊：0-否 / 1-是 |
| patientFeedback | String | 否 | 患者补充反馈 |

**响应：**

```json
{
    "code": 200,
    "message": "反馈提交成功",
    "data": {
        "aiAssessment": "患者服药3天后头痛症状部分缓解，无副作用。整体治疗方向正确，建议继续服药观察。",
        "aiAdvice": "请继续按时服药，注意休息，避免强光和噪音刺激。如头痛加重或出现新症状，请及时复诊。"
    }
}
```

#### 7.7.4 获取随访记录列表

```
GET /api/ai/follow-up/records
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| registerId | Integer | 是 | 挂号记录 ID |

---

## 8. 公共数据接口

以下接口提供基础数据查询，供多个模块共用。

### 8.1 科室管理

#### 8.1.1 获取科室列表

```
GET /api/common/departments
```

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": [
        {
            "id": 1,
            "deptCode": "NK",
            "deptName": "内科",
            "deptType": "临床科室",
            "delmark": 1
        }
    ]
}
```

### 8.2 疾病管理

#### 8.2.1 获取疾病列表

```
GET /api/common/diseases
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| keyword | String | 否 | 疾病编码或名称模糊搜索 |
| diseaseCategory | String | 否 | 疾病分类筛选 |

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": [
        {
            "id": 15,
            "diseaseCode": "PJT",
            "diseaseName": "偏头痛",
            "diseaseIcd": "G43.9",
            "diseaseCategory": "神经系统疾病"
        }
    ]
}
```

### 8.3 员工/医生管理

#### 8.3.1 获取医生列表

```
GET /api/common/doctors
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deptmentId | Integer | 否 | 科室 ID 筛选 |
| registLevelId | Integer | 否 | 挂号级别 ID 筛选 |

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": [
        {
            "id": 5,
            "realname": "李主任",
            "deptmentId": 1,
            "deptName": "内科",
            "registLevelId": 2,
            "registLevelName": "专家号",
            "schedulingId": 3
        }
    ]
}
```

### 8.4 排班管理

#### 8.4.1 获取排班规则列表

```
GET /api/common/schedulings
```

**响应：**

```json
{
    "code": 200,
    "message": "操作成功",
    "data": [
        {
            "id": 1,
            "ruleName": "周一三五上午",
            "weekRule": "1上3上5上",
            "delmark": 1
        }
    ]
}
```

---

## 9. 接口索引总表

### 9.1 认证鉴权模块

| 编号 | 方法 | 路径 | 说明 | 需认证 |
| --- | --- | --- | --- | --- |
| AUTH-01 | POST | /api/auth/login | 用户登录 | 否 |
| AUTH-02 | POST | /api/auth/logout | 用户登出 | 是 |
| AUTH-03 | POST | /api/auth/refresh | 刷新 Token | 否 |
| AUTH-04 | GET | /api/auth/userinfo | 获取当前用户信息 | 是 |

### 9.2 挂号收费模块

| 编号 | 方法 | 路径 | 说明 | 需认证 |
| --- | --- | --- | --- | --- |
| REG-01 | GET | /api/registration/regist-levels | 挂号级别列表 | 是 |
| REG-02 | GET | /api/registration/departments | 科室列表 | 是 |
| REG-03 | GET | /api/registration/settle-categories | 结算类别列表 | 是 |
| REG-04 | GET | /api/registration/available-doctors | 查询出诊医生 | 是 |
| REG-05 | GET | /api/registration/next-case-number | 生成病历号 | 是 |
| REG-06 | POST | /api/registration/register | 提交挂号 | 是 |
| REG-07 | PUT | /api/registration/register/{id}/cancel | 退号 | 是 |
| REG-08 | GET | /api/registration/patient-charges | 查询待缴费项目 | 是 |
| REG-09 | POST | /api/registration/charge | 收费结算 | 是 |
| REG-10 | POST | /api/registration/refund | 退费 | 是 |
| REG-11 | GET | /api/registration/expense-records | 费用记录查询 | 是 |

### 9.3 门诊医生模块

| 编号 | 方法 | 路径 | 说明 | 需认证 |
| --- | --- | --- | --- | --- |
| PHY-01 | GET | /api/physician/patients | 待诊患者列表 | 是 |
| PHY-02 | GET | /api/physician/patient-stats | 看诊统计 | 是 |
| PHY-03 | POST | /api/physician/medical-record | 创建病历 | 是 |
| PHY-04 | PUT | /api/physician/medical-record/{id} | 更新病历 | 是 |
| PHY-05 | GET | /api/physician/medical-record | 获取病历详情 | 是 |
| PHY-06 | GET | /api/physician/medical-technologies | 医技项目列表 | 是 |
| PHY-07 | POST | /api/physician/check-request | 提交检查申请 | 是 |
| PHY-08 | POST | /api/physician/inspection-request | 提交检验申请 | 是 |
| PHY-09 | POST | /api/physician/disposal-request | 提交处置申请 | 是 |
| PHY-10 | GET | /api/physician/history-patients | 已看诊患者列表 | 是 |
| PHY-11 | GET | /api/physician/check-results | 检查结果列表 | 是 |
| PHY-12 | GET | /api/physician/inspection-results | 检验结果列表 | 是 |
| PHY-13 | POST | /api/physician/diagnosis | 提交确诊结果 | 是 |
| PHY-14 | GET | /api/physician/drugs | 查询药品列表 | 是 |
| PHY-15 | GET | /api/physician/drugs/{id} | 获取药品详情 | 是 |
| PHY-16 | POST | /api/physician/prescription | 开立处方 | 是 |
| PHY-17 | DELETE | /api/physician/prescription/{id} | 删除处方药品 | 是 |

### 9.4 医技检查模块

| 编号 | 方法 | 路径 | 说明 | 需认证 |
| --- | --- | --- | --- | --- |
| MED-01 | GET | /api/medtech/check/applications | 待检查患者列表 | 是 |
| MED-02 | GET | /api/medtech/check/stats | 检查统计 | 是 |
| MED-03 | PUT | /api/medtech/check/start/{id} | 开始检查登记 | 是 |
| MED-04 | GET | /api/medtech/check/completed | 已完成检查列表 | 是 |
| MED-05 | PUT | /api/medtech/check/result/{id} | 录入检查结果 | 是 |
| MED-06 | GET | /api/medtech/inspection/applications | 待检验患者列表 | 是 |
| MED-07 | PUT | /api/medtech/inspection/start/{id} | 开始检验登记 | 是 |
| MED-08 | GET | /api/medtech/inspection/completed | 已完成检验列表 | 是 |
| MED-09 | PUT | /api/medtech/inspection/result/{id} | 录入检验结果 | 是 |
| MED-10 | GET | /api/medtech/disposal/applications | 待处置患者列表 | 是 |
| MED-11 | PUT | /api/medtech/disposal/start/{id} | 开始处置登记 | 是 |
| MED-12 | GET | /api/medtech/disposal/completed | 已完成处置列表 | 是 |
| MED-13 | PUT | /api/medtech/disposal/result/{id} | 录入处置结果 | 是 |

### 9.5 药房管理模块

| 编号 | 方法 | 路径 | 说明 | 需认证 |
| --- | --- | --- | --- | --- |
| PHA-01 | GET | /api/pharmacy/drugs | 药品列表 | 是 |
| PHA-02 | POST | /api/pharmacy/drugs | 新增药品 | 是 |
| PHA-03 | PUT | /api/pharmacy/drugs/{id} | 更新药品 | 是 |
| PHA-04 | DELETE | /api/pharmacy/drugs/{id} | 删除药品 | 是 |
| PHA-05 | GET | /api/pharmacy/pending | 待发药患者列表 | 是 |
| PHA-06 | PUT | /api/pharmacy/dispense/{registerId} | 确认发药 | 是 |
| PHA-07 | PUT | /api/pharmacy/return/{registerId} | 退药 | 是 |
| PHA-08 | GET | /api/pharmacy/transactions | 交易记录 | 是 |

### 9.6 AI 智能服务模块

| 编号 | 方法 | 路径 | 说明 | 需认证 |
| --- | --- | --- | --- | --- |
| AI-01 | POST | /api/ai/triage/analyze | AI 导诊分析 | 是 |
| AI-02 | GET | /api/ai/triage/records | 导诊历史记录 | 是 |
| AI-03 | POST | /api/ai/consult/start | 开始预问诊 | 是 |
| AI-04 | POST | /api/ai/consult/chat | 问诊对话 | 是 |
| AI-05 | POST | /api/ai/consult/complete | 结束问诊生成摘要 | 是 |
| AI-06 | GET | /api/ai/consult/records | 查询问诊记录 | 是 |
| AI-07 | POST | /api/ai/medical-record/generate | 生成病历草稿 | 是 |
| AI-08 | PUT | /api/ai/medical-record/feedback | 记录医生修改 | 是 |
| AI-09 | POST | /api/ai/exam/suggest | AI 检查方案推荐 | 是 |
| AI-10 | PUT | /api/ai/exam/suggest/{id}/adopt | 标记推荐采纳 | 是 |
| AI-11 | GET | /api/ai/exam/analysis | 检查结果 AI 分析 | 是 |
| AI-12 | PUT | /api/ai/exam/analysis/{id}/view | 标记分析已查看 | 是 |
| AI-13 | POST | /api/ai/diagnosis/analyze | AI 诊断推荐 | 是 |
| AI-14 | PUT | /api/ai/diagnosis/suggestion/{id}/adopt | 标记诊断采纳 | 是 |
| AI-15 | POST | /api/ai/pharmacy/review | AI 处方审核 | 是 |
| AI-16 | PUT | /api/ai/pharmacy/review/{id}/confirm | 确认审核结果 | 是 |
| AI-17 | POST | /api/ai/follow-up/generate | 生成随访计划 | 是 |
| AI-18 | GET | /api/ai/follow-up/plans | 随访计划列表 | 是 |
| AI-19 | POST | /api/ai/follow-up/feedback | 提交随访反馈 | 是 |
| AI-20 | GET | /api/ai/follow-up/records | 随访记录列表 | 是 |

### 9.7 公共数据接口

| 编号 | 方法 | 路径 | 说明 | 需认证 |
| --- | --- | --- | --- | --- |
| COM-01 | GET | /api/common/departments | 科室列表 | 是 |
| COM-02 | GET | /api/common/diseases | 疾病列表 | 是 |
| COM-03 | GET | /api/common/doctors | 医生列表 | 是 |
| COM-04 | GET | /api/common/schedulings | 排班规则列表 | 是 |

---

## 10. 错误码速查表

| code | 场景 | 典型 message |
| --- | --- | --- |
| 400 | 必填参数缺失 | "患者姓名不能为空" |
| 400 | 参数格式错误 | "日期格式不正确，应为 yyyy-MM-dd" |
| 401 | Token 过期 | "Token 已过期，请重新登录" |
| 401 | Token 无效 | "无效的 Token" |
| 403 | 角色不匹配 | "当前用户无权访问该功能" |
| 404 | 挂号记录不存在 | "未找到该挂号记录" |
| 404 | 患者不存在 | "未找到该患者信息" |
| 409 | 重复挂号 | "该患者今日已挂号" |
| 409 | 状态不允许操作 | "该挂号记录已退号，无法操作" |
| 409 | 处方已审核 | "该处方已完成审核" |
| 500 | 服务内部异常 | "服务异常，请联系管理员" |
| 503 | AI 服务不可用 | "AI 服务暂时不可用，请稍后重试" |

---

> **文档维护说明**：本文档随项目迭代持续更新。任何接口的新增、修改、废弃均需同步更新本文档，并通知前后端开发人员。
