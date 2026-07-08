"""
SegNet 分割网络定义（2D，VGG16-BN 编码器）
==========================================

移植自开源项目 Ola-Vish/lung-tumor-segmentation
（https://github.com/Ola-Vish/lung-tumor-segmentation，MIT License），
网络结构与原仓库 project/models/segnet.py 完全一致，以便直接加载其提供的
SegNet checkpoint（在 MSD Task06_Lung 数据集上训练，验证集 Dice 0.88 / IoU 0.75）。

架构：SegNet（Badrinarayanan et al., 2015），VGG16-BN 编码器 + 最大池化索引
反池化解码器。原仓库按轴位切片（224x224）做 2D 推理，本服务沿用这一约定，
见 app/segnet_backend.py。

注意：本文件禁止改动通道数 / 分块层数，否则与官方 checkpoint 的 state_dict
key 对不上，加载会失败。
"""

from __future__ import annotations

from typing import List, Tuple

import torch
import torch.nn as nn


class VggSubBlock(nn.Module):
    def __init__(self, input_channels: int, output_channels: int):
        super().__init__()
        self.block = nn.Sequential(
            nn.Conv2d(input_channels, output_channels, kernel_size=(3, 3), stride=(1, 1), padding=(1, 1)),
            nn.BatchNorm2d(output_channels, eps=1e-05, momentum=0.1, affine=True, track_running_stats=True),
            nn.ReLU(),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.block(x)


class VggBlock(nn.Module):
    def __init__(self, input_channels: int, output_channels: int, repetitions: int = 2):
        super().__init__()
        self.first_block = VggSubBlock(input_channels, output_channels)
        self.remaining_blocks = nn.Sequential(
            *[VggSubBlock(output_channels, output_channels) for _ in range(1, repetitions)]
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        x = self.first_block(x)
        x = self.remaining_blocks(x)
        return x


class DecoderBlock(nn.Module):
    def __init__(self, input_channels: int, output_channels: int, repetitions: int = 2):
        super().__init__()
        self.first_blocks = nn.Sequential(
            *[VggSubBlock(input_channels, input_channels) for _ in range(1, repetitions)]
        )
        self.last_block = VggSubBlock(input_channels, output_channels)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        x = self.first_blocks(x)
        x = self.last_block(x)
        return x


class Encoder(nn.Module):
    def __init__(self, channels: Tuple[int, ...]):
        super().__init__()
        self.max_pool = nn.MaxPool2d(
            kernel_size=2, stride=2, padding=0, dilation=1, return_indices=True, ceil_mode=False
        )
        self.encoder_blocks = nn.ModuleList(
            [
                VggBlock(channels[idx - 1], channels[idx], repetitions=2)
                if idx < 3
                else VggBlock(channels[idx - 1], channels[idx], repetitions=3)
                for idx in range(1, len(channels))
            ]
        )

    def forward(self, x: torch.Tensor) -> Tuple[torch.Tensor, List[torch.Tensor]]:
        pool_indices: List[torch.Tensor] = []
        for block in self.encoder_blocks:
            x = block(x)
            x, indices = self.max_pool(x)
            pool_indices.append(indices)
        return x, pool_indices


class Decoder(nn.Module):
    def __init__(self, channels: Tuple[int, ...]):
        super().__init__()
        self.max_unpool = nn.MaxUnpool2d(kernel_size=2, stride=2, padding=0)
        self.decoder_blocks = nn.ModuleList(
            [
                DecoderBlock(channels[idx - 1], channels[idx], repetitions=2)
                if idx > 3
                else DecoderBlock(channels[idx - 1], channels[idx], repetitions=3)
                for idx in range(1, len(channels))
            ]
        )

    def forward(self, x: torch.Tensor, pool_indices: List[torch.Tensor]) -> torch.Tensor:
        for idx, block in enumerate(self.decoder_blocks):
            x = self.max_unpool(x, pool_indices[idx])
            x = block(x)
        return x


class SegNet(nn.Module):
    """
    参数
    ----
    enc_chs : 编码器通道，如 (3, 64, 128, 256, 512, 512)（VGG16 五个 stage）
    dec_chs : 解码器通道，如 (512, 512, 256, 128, 64, 64)（镜像编码器）
    num_classes : 输出类别数，本仓库固定为 2（背景/肿瘤）
    warm_start : 是否用 ImageNet 预训练 VGG16-BN 权重初始化编码器。
                 仅从零训练时需要；推理加载完整 checkpoint 时应为 False
                 （默认），此时不会引入 torchvision 依赖。
    """

    def __init__(
        self,
        enc_chs: Tuple[int, ...],
        dec_chs: Tuple[int, ...],
        num_classes: int = 2,
        warm_start: bool = False,
    ):
        super().__init__()
        self.encoder = Encoder(channels=enc_chs)
        self.decoder = Decoder(channels=dec_chs)
        self.last = VggSubBlock(dec_chs[-1], dec_chs[-1])
        self.output = nn.Sequential(
            nn.Conv2d(dec_chs[-1], num_classes, kernel_size=(3, 3), stride=(1, 1), padding=(1, 1)),
            nn.BatchNorm2d(num_classes, eps=1e-05, momentum=0.1, affine=True, track_running_stats=True),
        )
        if warm_start:
            self.load_vgg_weights_to_encoder()

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        encoder_features, pool_indices = self.encoder(x)
        reverse_pool_indices = pool_indices[::-1]
        decoder_output = self.decoder(encoder_features, reverse_pool_indices)
        decoder_output = self.last(decoder_output)
        decoder_output = self.output(decoder_output)
        return decoder_output

    def load_vgg_weights_to_encoder(self) -> None:
        """仅训练脚本使用：用 ImageNet 预训练 VGG16-BN 权重热启动编码器。"""
        import torchvision  # 延迟导入，避免推理环境强制依赖 torchvision

        encoder_state_dict = self.encoder.state_dict()
        encoder_keys = list(encoder_state_dict.keys())
        try:
            vgg16 = torchvision.models.vgg16_bn(weights=torchvision.models.VGG16_BN_Weights.DEFAULT)
        except AttributeError:
            vgg16 = torchvision.models.vgg16_bn(pretrained=True)  # 兼容旧版 torchvision
        vgg_state_dict = vgg16.state_dict()
        for idx, key in enumerate(vgg_state_dict):
            if idx < len(encoder_keys):
                curr_key = encoder_keys[idx]
                encoder_state_dict[curr_key] = vgg_state_dict[key]
        self.encoder.load_state_dict(encoder_state_dict)


def build_segnet_model(warm_start: bool = False) -> nn.Module:
    """构建与 Ola-Vish/lung-tumor-segmentation 完全一致的 SegNet 网络。"""
    return SegNet(
        enc_chs=(3, 64, 128, 256, 512, 512),
        dec_chs=(512, 512, 256, 128, 64, 64),
        num_classes=2,
        warm_start=warm_start,
    )
