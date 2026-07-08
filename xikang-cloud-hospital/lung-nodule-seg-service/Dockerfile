FROM python:3.11-slim

WORKDIR /app

# 系统依赖（SimpleITK GDCM 需要）
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 可选：nnU-Net 推理后端。默认不安装（体积较大），
# 需要时构建镜像加 --build-arg INSTALL_NNUNET=1
ARG INSTALL_NNUNET=0
COPY requirements-nnunet.txt .
RUN if [ "$INSTALL_NNUNET" = "1" ]; then pip install --no-cache-dir -r requirements-nnunet.txt; fi

COPY app/ ./app/
COPY scripts/ ./scripts/

# 模型权重目录（挂载或手动拷贝 best_model.pth / nnunet_results 到此处）
RUN mkdir -p ./models

EXPOSE 8222

ENV LUNG_NODULE_SEG_PORT=8222
ENV LUNG_NODULE_SEG_DEVICE=cpu
ENV LUNG_NODULE_SEG_BACKEND=monai

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8222"]
