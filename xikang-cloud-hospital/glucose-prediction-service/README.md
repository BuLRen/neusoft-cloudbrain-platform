# glucose-prediction-service

血糖 LSTM 预测推理服务（FastAPI + ONNX Runtime）。由 `medtech-service` 通过 HTTP 调用，不直连数据库。

---

## 1. 部署前准备

### 必需文件

```
glucose-prediction-service/
├── app/
├── models/
│   ├── glucose_lstm_v1.onnx   # 模型权重（必需）
│   └── norm_stats.json        # 归一化参数（必需）
├── requirements.txt
└── Dockerfile                 # 可选，Docker 部署
```

若 `models/glucose_lstm_v1.onnx` 不存在，需先在 `glucose-ml` 中训练并导出：

```bash
cd ../glucose-ml
python scripts/run_pipeline.py    # 或 train_lstm.py + export_onnx.py
# 将 artifacts/glucose_lstm_v1.onnx 复制到本目录 models/
```

### 云服务器建议配置

| 项目 | 建议 |
|------|------|
| CPU | 2 核及以上（纯 CPU 推理，无需 GPU） |
| 内存 | ≥ 1 GB |
| 系统 | Ubuntu 22.04 / CentOS 7+ |
| Python | 3.10 或 3.11 |
| 端口 | 8096（默认，可改） |

> 本服务无鉴权，**只应对内网开放**，不要直接暴露到公网。

---

## 2. 方式 A：直接部署（systemd）

### 2.1 上传代码

```bash
# 本地打包（不含 __pycache__）
cd xikang-cloud-hospital
tar czf glucose-prediction-service.tar.gz \
  --exclude='__pycache__' \
  glucose-prediction-service/

scp glucose-prediction-service.tar.gz user@your-server:/opt/
ssh user@your-server
cd /opt && tar xzf glucose-prediction-service.tar.gz
```

### 2.2 安装依赖

```bash
cd /opt/glucose-prediction-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### 2.3 验证启动

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8096

# 另开终端
curl http://127.0.0.1:8096/health
# {"status":"UP","model_id":"glucose_lstm_v1","model_loaded":true}
```

### 2.4 注册 systemd 服务

创建 `/etc/systemd/system/glucose-prediction.service`：

```ini
[Unit]
Description=Glucose LSTM Prediction Service
After=network.target

[Service]
Type=simple
User=www-data
WorkingDirectory=/opt/glucose-prediction-service
Environment=PATH=/opt/glucose-prediction-service/.venv/bin
ExecStart=/opt/glucose-prediction-service/.venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8096
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable glucose-prediction
sudo systemctl start glucose-prediction
sudo systemctl status glucose-prediction
```

---

## 3. 方式 B：Docker 部署

```bash
cd glucose-prediction-service
docker build -t glucose-prediction:1.0 .
docker run -d \
  --name glucose-prediction \
  --restart unless-stopped \
  -p 8096:8096 \
  glucose-prediction:1.0
```

验证：

```bash
curl http://127.0.0.1:8096/health
docker logs glucose-prediction
```

---

## 4. 对接 medtech-service

Java 侧通过环境变量指向云上的 Python 服务：

```bash
# medtech-service 启动前设置
export GLUCOSE_PREDICTION_BASE_URL=http://<python服务内网IP>:8096
```

或在 `application.yml` / Nacos 中配置：

```yaml
glucose-prediction:
  base-url: http://10.0.0.12:8096
  connect-timeout-ms: 5000
  read-timeout-ms: 15000
```

**网络要求**：`medtech-service` 所在机器必须能访问 Python 服务的 `8096` 端口。

---

## 5. 防火墙与安全

```bash
# 仅允许 medtech 所在内网 IP 访问（示例）
sudo ufw allow from 10.0.0.0/24 to any port 8096
sudo ufw deny 8096
```

- 不要将 8096 映射到公网
- 若 medtech 与 Python 同机，用 `http://127.0.0.1:8096` 即可

---

## 6. 接口说明

### `GET /health`

```json
{"status":"UP","model_id":"glucose_lstm_v1","model_loaded":true}
```

### `POST /predict`

```json
{
  "register_id": 9001,
  "observations": [
    {
      "observed_at": "2026-06-26T08:00:00",
      "blood_glucose": 6.2,
      "insulin_total": 0,
      "meal_flag": 0,
      "exercise_flag": 0
    }
  ]
}
```

需至少 **48 条按小时对齐的观测**（不足时服务会重采样；仍不足则返回 `risk_level: unknown`）。

---

## 7. 常见问题

| 现象 | 处理 |
|------|------|
| 启动报 `Model not found` | 确认 `models/glucose_lstm_v1.onnx` 已上传 |
| medtech 日志 `glucose prediction service unavailable` | 检查 `GLUCOSE_PREDICTION_BASE_URL`、防火墙、服务是否运行 |
| 预测始终 unknown | 观测数据不足 48 小时窗口，需补录居家血糖 |
| 端口冲突 | 本服务默认 8096；若与同机 payment-service 冲突，改端口并同步 Java 配置 |

---

## 8. 更新模型

1. 在 `glucose-ml` 重新训练并导出 ONNX
2. 替换 `models/glucose_lstm_v1.onnx`（及 `norm_stats.json` 如有变化）
3. 重启服务：`sudo systemctl restart glucose-prediction` 或 `docker restart glucose-prediction`
