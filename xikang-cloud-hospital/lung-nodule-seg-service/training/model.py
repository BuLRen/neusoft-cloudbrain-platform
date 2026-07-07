"""
分割网络定义
============

使用 MONAI 内置 UNet（3D），输出 2 类（背景 / 肺癌肿瘤）。

架构选择：
  - MONAI UNet 比手写 UNet 更省心，内置残差 skip 连接和激活配置
  - 默认 channels=(16,32,64,128)，strides=(2,2,2)，适合 Mac M2 内存
  - GPU 机器可扩展到 channels=(32,64,128,256,320)

注意（实现红线）：
  app/model.py 从本文件导入 build_model()，两者必须保持一致，
  否则训练权重和推理权重的 state_dict key 对不上，加载失败。
"""

from __future__ import annotations

import torch
import torch.nn as nn

from monai.networks.nets import UNet

from . import config


def build_model() -> nn.Module:
    """
    构建 3D 分割网络。

    返回
    ----
    torch.nn.Module，未加载权重。
    """
    model = UNet(
        spatial_dims=3,
        in_channels=config.IN_CHANNELS,
        out_channels=config.OUT_CHANNELS,
        channels=config.MODEL_CHANNELS,
        strides=config.MODEL_STRIDES,
        num_res_units=2,        # 每层额外的残差块数
        act="PRELU",
        norm="INSTANCE",
        dropout=0.1,
    )
    return model


def count_parameters(model: nn.Module) -> int:
    return sum(p.numel() for p in model.parameters() if p.requires_grad)


if __name__ == "__main__":
    m = build_model()
    x = torch.randn(1, 1, *config.PATCH_SIZE)
    out = m(x)
    print(f"输入  shape: {x.shape}")
    print(f"输出  shape: {out.shape}")
    print(f"参数量: {count_parameters(m):,}")
