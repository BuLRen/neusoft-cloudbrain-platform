"""
推理网络定义
============

与 training/model.py 保持完全一致：使用 MONAI UNet，相同参数。

注意：本文件禁止改动网络参数（channels / strides 等），
否则权重 state_dict 的 key 与训练权重对不上，加载失败。
"""

from __future__ import annotations

import torch.nn as nn
from monai.networks.nets import UNet

from . import config


def build_model() -> nn.Module:
    """
    构建分割网络，与训练端完全相同的架构配置。
    """
    model = UNet(
        spatial_dims=3,
        in_channels=config.IN_CHANNELS,
        out_channels=config.OUT_CHANNELS,
        channels=config.MODEL_CHANNELS,
        strides=config.MODEL_STRIDES,
        num_res_units=2,
        act="PRELU",
        norm="INSTANCE",
        dropout=0.1,
    )
    return model
