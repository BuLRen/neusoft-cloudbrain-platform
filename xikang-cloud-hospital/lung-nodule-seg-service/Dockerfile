FROM python:3.11-slim

WORKDIR /app

# 系统依赖（SimpleITK GDCM 需要）
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app/ ./app/

# 模型权重目录（挂载或手动拷贝 best_model.pth 到此处）
RUN mkdir -p ./models

EXPOSE 8107

ENV LUNG_NODULE_SEG_PORT=8107
ENV LUNG_NODULE_SEG_DEVICE=cpu

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8107"]
