# 药房工作台（Pharmacy）

> 路由：`/pharmacy` · 角色：`pharmacy`（`admin` 可巡检）· 服务：`pharmacy-service`（8094）

面向门诊药房药师的工作站，串联"医生开方 → 药师发药 → AI 随访"全链路。

## 功能总览

### Tab 1 · 待发药 / 处方详情
- 左：待发药处方卡片列表（按挂号聚合，可按 `registrationId` 筛选）
- 右：处方基本信息 + 明细表
- 操作按钮：
  - **审方预检**：调用 `POST /dispense/{registerId}/review`，返回 pass/warn/block 三档结论
  - **确认发药**：二次确认后调用 `PUT /dispense/{registerId}`，扣库存 + 写流水 + 自动生成发药单 + 事务后触发 AI 随访
  - **退药**：必填退药原因后调用 `PUT /return/{registerId}`，恢复库存
  - **查看随访计划**：拉取该患者的 AI 随访计划列表，可录入反馈
  - **查看发药单**：显示该挂号下生成的用药指导单

### Tab 2 · 药库 / 库存 / 交易记录
- 顶部筛选条：关键字 + 剂型 + 分类（组合 AND 查询）
- 药品目录表格：
  - 行点击切换库存批次视图
  - 低库存整行黄色高亮
  - 操作列：[入库] [盘点] [详情]
- 库存批次：按失效日期升序，近 30 天 warning 行、近 7 天 critical 行
- 低库存提醒：批量预警 + [补货] 快捷按钮
- 交易记录：类型 + 日期范围 + 药品 ID 组合筛选，带分页

### 顶部 Signature：近效期预警条
- 默认显示近 30 天内到期的批次总数
- 7 天内到期用 danger 标签、30 天内用 warning 标签
- 进入页面自动加载，支持手动刷新

### 弹窗
1. **入库**：批号、生产/失效日期、数量、货位
2. **库存盘点**：实际数 + 账面数差额，自动写 `盘点` 流水
3. **药品详情**：完整药品字段（含分类/禁忌/不良反应） + 生成 AI 用药指导
4. **随访反馈录入**：随访类型、依从性、症状评分、AI 评估
5. **发药单查看**：面向患者的用药指导单（含发药单号 `DY-yyyymmddHHmmss-{regId}-{rxId}`）

## AI 联动

发药成功事务提交后，自动调用 `ai-pharmacy-service`：
- `POST /api/ai/pharmacy/followup` 创建随访计划
- 失败不阻塞主流程，可通过 `POST /pharmacy/followup/retry/{prescriptionId}` 重试

药品详情抽屉可调用：
- `POST /pharmacy/drugs/{drugId}/guide` 生成用药指导

## 常量与枚举

`shared/constants/pharmacy.ts` 集中维护：剂型、交易类型、发药状态、退药原因、随访类型/标签、依从性、分页默认。

## 关键约束

- 发药人姓名取 `authStore.realName`，不再传 `'pharmacy'` 字符串
- 发药 / 退药均有二次确认弹窗
- 库存扣减带乐观锁（SQL `stock_quantity >= qty` 条件）
- 处方更新使用精确字段（`updateDispensationInfo`），不覆盖医生写的 `diagnosis/total_amount`
