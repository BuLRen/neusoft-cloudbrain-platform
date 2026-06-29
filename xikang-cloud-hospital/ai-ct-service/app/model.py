"""
多任务 3D U-Net
===============

共享 Encoder + 两个头：
  - 分割头：5 类（0 背景 + 4 类伪影）逐体素预测
  - 分类头：5 维多标签向量 [有无伪影, 金属, 线束硬化, 部分容积, 环形]

设计：
  - Encoder 3 层下采样（通道 16→32→64→128），保持你原有结构
  - Decoder 3 层上采样还原图像分辨率
  - 分类头从 bottleneck 取特征 → GAP → MLP → 5 维

注意（实现红线）：
  本文件从 CQ500-data-clean/train/model.py 一字不改地拷贝而来。
  任何"优化重构"都禁止——改了 state_dict 的 key 会对不上，权重加载失败。
"""

import torch
import torch.nn as nn
import torch.nn.functional as F


# ========================
# 基础块
# ========================
class DoubleConv3D(nn.Module):
    def __init__(self, in_ch, out_ch):
        super().__init__()
        self.conv = nn.Sequential(
            nn.Conv3d(in_ch, out_ch, 3, padding=1),
            nn.BatchNorm3d(out_ch),
            nn.ReLU(inplace=True),
            nn.Conv3d(out_ch, out_ch, 3, padding=1),
            nn.BatchNorm3d(out_ch),
            nn.ReLU(inplace=True),
        )

    def forward(self, x):
        return self.conv(x)


class Down3D(nn.Module):
    def __init__(self, in_ch, out_ch):
        super().__init__()
        self.pool = nn.MaxPool3d(2)
        self.conv = DoubleConv3D(in_ch, out_ch)

    def forward(self, x):
        return self.conv(self.pool(x))


class Up3D(nn.Module):
    def __init__(self, in_ch, out_ch):
        super().__init__()
        self.up = nn.ConvTranspose3d(in_ch, in_ch // 2, kernel_size=2, stride=2)
        self.conv = DoubleConv3D(in_ch, out_ch)

    def forward(self, x1, x2):
        x1 = self.up(x1)
        diffD = x2.size()[2] - x1.size()[2]
        diffH = x2.size()[3] - x1.size()[3]
        diffW = x2.size()[4] - x1.size()[4]
        x1 = F.pad(x1, [diffW // 2, diffW - diffW // 2,
                        diffH // 2, diffH - diffH // 2,
                        diffD // 2, diffD - diffD // 2])
        x = torch.cat([x2, x1], dim=1)
        return self.conv(x)


# ========================
# 多任务模型
# ========================
class MultiTaskUNet3D(nn.Module):
    def __init__(self, in_ch: int = 1, base_ch: int = 16,
                 num_seg_classes: int = 5, num_cls: int = 5):
        super().__init__()
        self.num_seg_classes = num_seg_classes
        self.num_cls = num_cls

        # ---- Encoder ----
        self.inc = DoubleConv3D(in_ch, base_ch)
        self.down1 = Down3D(base_ch, base_ch * 2)
        self.down2 = Down3D(base_ch * 2, base_ch * 4)
        self.down3 = Down3D(base_ch * 4, base_ch * 8)   # bottleneck = 128

        bottleneck_ch = base_ch * 8

        # ---- 分割 Decoder ----
        self.up1 = Up3D(bottleneck_ch, base_ch * 4)
        self.up2 = Up3D(base_ch * 4, base_ch * 2)
        self.up3 = Up3D(base_ch * 2, base_ch)
        self.seg_head = nn.Conv3d(base_ch, num_seg_classes, kernel_size=1)

        # ---- 分类头（从 bottleneck 全局池化） ----
        self.cls_head = nn.Sequential(
            nn.AdaptiveAvgPool3d(1),           # (N, C, 1, 1, 1)
            nn.Flatten(1),                     # (N, C)
            nn.Linear(bottleneck_ch, 64),
            nn.ReLU(inplace=True),
            nn.Dropout(0.3),
            nn.Linear(64, num_cls),
        )

    def forward(self, x):
        # Encoder
        x1 = self.inc(x)        # (N, 16, D, H, W)
        x2 = self.down1(x1)     # (N, 32, D/2, ...)
        x3 = self.down2(x2)     # (N, 64, D/4, ...)
        x4 = self.down3(x3)     # (N, 128, D/8, ...) ← bottleneck

        # Decoder
        u = self.up1(x4, x3)
        u = self.up2(u, x2)
        u = self.up3(u, x1)
        seg_logits = self.seg_head(u)        # (N, 5, D, H, W)

        # Classification
        cls_logits = self.cls_head(x4)       # (N, 5)

        return {"seg": seg_logits, "cls": cls_logits}


# ========================
# 调试入口：打印结构和参数量
# ========================
if __name__ == "__main__":
    model = MultiTaskUNet3D()
    x = torch.randn(1, 1, 32, 128, 128)
    out = model(x)
    print("seg logits:", out["seg"].shape)
    print("cls logits:", out["cls"].shape)

    total = sum(p.numel() for p in model.parameters())
    print(f"参数量: {total:,}")
