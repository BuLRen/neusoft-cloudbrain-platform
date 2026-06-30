# glucose-ml

血糖 **LSTM+GRU 集成** 训练管线，面向「患者每日自录血糖 → 预测走向 → 预警」场景。

## 模型结构

| 组件 | 说明 |
|------|------|
| LSTM | 2 层，hidden=128 |
| GRU | 2 层，hidden=128 |
| 集成 | 推理时 `0.5×LSTM + 0.5×GRU`，导出为单个 ONNX |
| 损失 | Huber（比 MSE 更抗异常血糖） |
| UCI 微调 | 25 epoch，对齐指尖血糖 / 演示患者 |

## 数据集（扩展后）

| 来源 | 说明 | 预期患者数 |
|------|------|------------|
| UCI | 指尖血糖 + 胰岛素/进餐（**最接近患者每日自录**） | 最多 70 |
| UCHTT1DM | 智利 CGM（HT + T1DM） | 18 |
| Shanghai | T1DM + T2DM CGM | 24 |
| D1NAMO | Zenodo 血糖子集（非 sensor） | 约 9+ |

## 训练（自行执行）

```powershell
cd xikang-cloud-hospital/glucose-ml
pip install -r requirements.txt

# 数据已下载可跳过下载，直接：
python scripts/prepare_all.py
python scripts/train_lstm.py      # 训练 LSTM + GRU
python scripts/finetune_uci.py    # UCI 微调（演示/自录场景对齐）
python scripts/evaluate.py        # 输出 metrics.json
python scripts/export_onnx.py     # 导出集成 ONNX
```

或一键：

```powershell
python scripts/run_pipeline.py
```

## 如何读评估指标

`reports/metrics.json` 包含：

| 字段 | 含义 |
|------|------|
| 顶层 `mae_mmol_l` 等 | **混合验证集**（全部数据源） |
| `by_source.uci` | **UCI 单源**（论文/演示优先引用此项） |
| `demo_uci` | 同 `by_source.uci`，对齐 register 3 演示 |

> 患者端未来是**每日几次自录**，与 UCI 更相似；混合指标会被高密度 CGM 拉低，**看 UCI 子指标更合理**。

## 部署

1. `run_pipeline.py` 会自动复制 ONNX 到 `glucose-prediction-service/models/`
2. 重启 8096 推理服务
3. 医生端「刷新预测」

## 预警逻辑（推理服务）

- 预测未来 24h 血糖
- `risk`: 预测值 >10 或 <3.9 → high；>7.8 → medium；否则 low
