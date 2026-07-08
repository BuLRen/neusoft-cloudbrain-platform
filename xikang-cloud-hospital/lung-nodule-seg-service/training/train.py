"""
训练脚本
========

用法:
    # 标准训练（从头开始）
    python -m training.train

    # 指定数据集路径
    LUNG_DATASET_ROOT=/path/to/Task06_Lung python -m training.train

    # 从 checkpoint 续训
    python -m training.train --resume models/last_model.pth

    # 快速验证流程（3 个样本，2 个 epoch）
    python -m training.train --smoke-test

设备选择：cuda > mps > cpu（自动），可用 --device 强制指定。

输出：
    models/best_model.pth   验证 Dice 最优 checkpoint
    models/last_model.pth   最后一个 epoch checkpoint
"""

from __future__ import annotations

import argparse
import os
import sys
import time
from pathlib import Path

import torch
import torch.nn as nn
from monai.data import DataLoader, Dataset, decollate_batch
from monai.inferers import sliding_window_inference
from monai.losses import DiceCELoss
from monai.metrics import DiceMetric
from monai.transforms import AsDiscrete, Compose
from monai.utils import set_determinism
from tqdm import tqdm

# 添加项目根目录到 path，确保 training.* 可以正常导入
_PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _PROJECT_ROOT not in sys.path:
    sys.path.insert(0, _PROJECT_ROOT)

from training import config
from training.dataset import get_train_val_dicts
from training.model import build_model
from training.transforms import get_train_transforms, get_val_transforms


# ========================
# 设备选择
# ========================
def select_device(requested: str | None) -> torch.device:
    if requested:
        return torch.device(requested)
    if torch.cuda.is_available():
        return torch.device("cuda")
    if torch.backends.mps.is_available():
        return torch.device("mps")
    return torch.device("cpu")


# ========================
# 保存 / 加载 checkpoint
# ========================
def save_checkpoint(
    path: str,
    model: nn.Module,
    optimizer: torch.optim.Optimizer,
    scheduler,
    epoch: int,
    best_dice: float,
    state_label: str = "",
) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    torch.save(
        {
            "epoch": epoch,
            "best_dice": best_dice,
            "model": model.state_dict(),
            "optimizer": optimizer.state_dict(),
            "scheduler": scheduler.state_dict() if scheduler else None,
            "state_label": state_label,
        },
        path,
    )


def load_checkpoint(
    path: str,
    model: nn.Module,
    optimizer: torch.optim.Optimizer | None = None,
    scheduler=None,
    device: torch.device | None = None,
) -> tuple[int, float]:
    """
    加载 checkpoint，返回 (已训练 epoch 数, 最优 Dice)。
    """
    map_loc = str(device) if device else "cpu"
    ckpt = torch.load(path, map_location=map_loc, weights_only=False)

    # 兼容推理服务直接加载的纯 state_dict 格式
    if "model" in ckpt:
        sd = ckpt["model"]
    elif "state_dict" in ckpt:
        sd = ckpt["state_dict"]
    else:
        sd = ckpt

    # 去掉 DataParallel 的 module. 前缀
    clean_sd = {(k[len("module."):] if k.startswith("module.") else k): v for k, v in sd.items()}
    model.load_state_dict(clean_sd)

    if optimizer and "optimizer" in ckpt:
        try:
            optimizer.load_state_dict(ckpt["optimizer"])
        except Exception as e:
            print(f"[warn] 无法恢复 optimizer 状态（可能结构变化）: {e}")

    if scheduler and ckpt.get("scheduler"):
        try:
            scheduler.load_state_dict(ckpt["scheduler"])
        except Exception as e:
            print(f"[warn] 无法恢复 scheduler 状态: {e}")

    start_epoch = ckpt.get("epoch", 0)
    best_dice = ckpt.get("best_dice", 0.0)
    return start_epoch, best_dice


# ========================
# 训练主函数
# ========================
def train(args: argparse.Namespace) -> None:
    set_determinism(seed=config.RANDOM_SEED)
    device = select_device(args.device)
    print(f"[train] 使用设备: {device}")

    # ---- 数据 ----
    train_dicts, val_dicts = get_train_val_dicts()
    if args.smoke_test:
        train_dicts = train_dicts[:3]
        val_dicts = val_dicts[:2]
        max_epochs = 2
        val_interval = 1
        print("[train] SMOKE TEST 模式：3 训练样本 / 2 验证样本 / 2 epochs")
    else:
        max_epochs = config.MAX_EPOCHS
        val_interval = config.VAL_INTERVAL

    train_ds = Dataset(data=train_dicts, transform=get_train_transforms())
    val_ds = Dataset(data=val_dicts, transform=get_val_transforms())

    train_loader = DataLoader(
        train_ds,
        batch_size=config.BATCH_SIZE,
        shuffle=True,
        num_workers=0,       # macOS multiprocessing 问题，0 更稳定
        pin_memory=(device.type == "cuda"),
    )
    val_loader = DataLoader(val_ds, batch_size=1, shuffle=False, num_workers=0)

    # ---- 模型 ----
    model = build_model().to(device)
    print(f"[train] 模型参数量: {sum(p.numel() for p in model.parameters()):,}")

    # ---- Loss ----
    loss_fn = DiceCELoss(to_onehot_y=True, softmax=True)

    # ---- 优化器 + 调度器 ----
    optimizer = torch.optim.AdamW(
        model.parameters(),
        lr=config.LEARNING_RATE,
        weight_decay=config.WEIGHT_DECAY,
    )
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(
        optimizer, T_max=max_epochs, eta_min=1e-6
    )

    # ---- 评估 ----
    dice_metric = DiceMetric(include_background=False, reduction="mean")
    post_pred = Compose([AsDiscrete(argmax=True, to_onehot=config.OUT_CHANNELS)])
    post_label = Compose([AsDiscrete(to_onehot=config.OUT_CHANNELS)])

    best_dice = 0.0
    start_epoch = 0

    # ---- 续训 ----
    if args.resume and os.path.isfile(args.resume):
        print(f"[train] 从 checkpoint 恢复: {args.resume}")
        start_epoch, best_dice = load_checkpoint(
            args.resume, model, optimizer, scheduler, device
        )
        print(f"[train] 已恢复到 epoch={start_epoch}, best_dice={best_dice:.4f}")

    os.makedirs(config.CHECKPOINT_DIR, exist_ok=True)
    best_model_path = os.path.join(config.CHECKPOINT_DIR, config.BEST_MODEL_FILENAME)
    last_model_path = os.path.join(config.CHECKPOINT_DIR, config.LAST_MODEL_FILENAME)

    # ========================
    # 训练循环
    # ========================
    epoch_bar = tqdm(
        range(start_epoch, max_epochs),
        initial=start_epoch,
        total=max_epochs,
        desc="训练进度",
        unit="epoch",
        dynamic_ncols=True,
    )

    for epoch in epoch_bar:
        model.train()
        epoch_loss = 0.0
        step = 0
        t0 = time.time()

        batch_bar = tqdm(
            train_loader,
            desc=f"  Epoch {epoch + 1:04d}/{max_epochs} 训练",
            unit="batch",
            leave=False,
            dynamic_ncols=True,
        )

        for batch_data in batch_bar:
            # RandCropByPosNegLabeld 会把 batch_size×num_samples 的 list 返回，
            # 需要把 list 中的样本堆叠成一个 batch
            if isinstance(batch_data["image"], list):
                inputs = torch.cat(batch_data["image"], dim=0).to(device)
                labels = torch.cat(batch_data["label"], dim=0).to(device)
            else:
                inputs = batch_data["image"].to(device)
                labels = batch_data["label"].to(device)

            optimizer.zero_grad()
            outputs = model(inputs)
            loss = loss_fn(outputs, labels)
            loss.backward()
            optimizer.step()
            epoch_loss += loss.item()
            step += 1

            # 实时更新 batch 进度条右侧信息
            batch_bar.set_postfix(loss=f"{loss.item():.4f}", refresh=False)

        batch_bar.close()
        scheduler.step()
        avg_loss = epoch_loss / max(step, 1)
        elapsed = time.time() - t0

        # 更新 epoch 进度条右侧信息
        epoch_bar.set_postfix(
            loss=f"{avg_loss:.4f}",
            lr=f"{scheduler.get_last_lr()[0]:.1e}",
            best_dice=f"{best_dice:.4f}",
            time=f"{elapsed:.0f}s",
        )

        # ---- 保存最后 epoch checkpoint ----
        save_checkpoint(last_model_path, model, optimizer, scheduler, epoch + 1, best_dice, "last")

        # ---- 验证 ----
        if (epoch + 1) % val_interval == 0:
            model.eval()
            with torch.no_grad():
                val_bar = tqdm(
                    val_loader,
                    desc=f"  Epoch {epoch + 1:04d}/{max_epochs} 验证",
                    unit="case",
                    leave=False,
                    dynamic_ncols=True,
                )
                for val_data in val_bar:
                    val_inputs = val_data["image"].to(device)
                    val_labels = val_data["label"].to(device)

                    # 滑窗推理（整卷）
                    val_outputs = sliding_window_inference(
                        val_inputs,
                        roi_size=config.VAL_PATCH_SIZE,
                        sw_batch_size=1,
                        predictor=model,
                        overlap=config.VAL_OVERLAP,
                        mode="gaussian",
                    )

                    val_outputs_list = decollate_batch(val_outputs)
                    val_labels_list = decollate_batch(val_labels)
                    val_outputs_post = [post_pred(x) for x in val_outputs_list]
                    val_labels_post = [post_label(x) for x in val_labels_list]
                    dice_metric(y_pred=val_outputs_post, y=val_labels_post)

                val_bar.close()

            mean_dice = dice_metric.aggregate().item()
            dice_metric.reset()
            tqdm.write(f"[val] epoch {epoch + 1:04d}/{max_epochs}  mean_dice={mean_dice:.4f}")

            if mean_dice > best_dice:
                best_dice = mean_dice
                save_checkpoint(
                    best_model_path, model, optimizer, scheduler,
                    epoch + 1, best_dice, "best"
                )
                tqdm.write(f"[val] ★ 新最优！best_dice={best_dice:.4f}，已保存 {best_model_path}")

                # 同步更新 epoch 进度条中的 best_dice
                epoch_bar.set_postfix(
                    loss=f"{avg_loss:.4f}",
                    lr=f"{scheduler.get_last_lr()[0]:.1e}",
                    best_dice=f"{best_dice:.4f}",
                    time=f"{elapsed:.0f}s",
                )

    epoch_bar.close()
    tqdm.write(f"\n训练完成。最优验证 Dice: {best_dice:.4f}")
    tqdm.write(f"最优权重路径: {best_model_path}")


# ========================
# CLI 入口
# ========================
def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="肺结节 3D 分割训练脚本")
    parser.add_argument(
        "--resume",
        type=str,
        default=None,
        help="从 checkpoint 续训（传入 .pth 路径）",
    )
    parser.add_argument(
        "--device",
        type=str,
        default=None,
        help="强制指定设备（cuda / mps / cpu），默认自动选择",
    )
    parser.add_argument(
        "--smoke-test",
        action="store_true",
        help="快速验证流程：使用极少样本和 epoch，确认脚本无崩溃",
    )
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    train(args)
