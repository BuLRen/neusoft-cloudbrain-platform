"""
评估脚本
========

用法:
    # 使用默认 best_model.pth 评估验证集
    python -m training.evaluate

    # 指定权重文件
    python -m training.evaluate --checkpoint models/best_model.pth

    # 输出可视化切片 PNG（保存到 eval_outputs/）
    python -m training.evaluate --save-vis

    # smoke test：只看 2 个样本
    python -m training.evaluate --num-samples 2

指标：
    - 逐样本 Dice（前景类别）
    - 平均 Dice ± std
"""

from __future__ import annotations

import argparse
import os
import sys
import time

import numpy as np
import torch
from monai.data import DataLoader, Dataset, decollate_batch
from monai.inferers import sliding_window_inference
from monai.metrics import DiceMetric
from monai.transforms import AsDiscrete, Compose
from monai.utils import set_determinism

_PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _PROJECT_ROOT not in sys.path:
    sys.path.insert(0, _PROJECT_ROOT)

from training import config
from training.dataset import get_train_val_dicts
from training.model import build_model
from training.transforms import get_val_transforms


def select_device(requested: str | None) -> torch.device:
    if requested:
        return torch.device(requested)
    if torch.cuda.is_available():
        return torch.device("cuda")
    if torch.backends.mps.is_available():
        return torch.device("mps")
    return torch.device("cpu")


def save_slice_vis(
    image: np.ndarray,
    label: np.ndarray,
    pred: np.ndarray,
    out_dir: str,
    sample_idx: int,
    dice: float,
) -> None:
    """
    保存三切面对比 PNG（axial 最大前景层）。
    image: (D, H, W) float
    label/pred: (D, H, W) int
    """
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt

        # 找到前景最多的轴状切片
        fg_per_slice = label.sum(axis=(1, 2))
        if fg_per_slice.max() == 0:
            z = image.shape[0] // 2
        else:
            z = int(fg_per_slice.argmax())

        fig, axes = plt.subplots(1, 3, figsize=(12, 4))
        axes[0].imshow(image[z], cmap="gray", vmin=0, vmax=1)
        axes[0].set_title(f"CT (slice {z})")
        axes[0].axis("off")

        axes[1].imshow(image[z], cmap="gray", vmin=0, vmax=1)
        axes[1].imshow(label[z], alpha=0.4, cmap="Reds", vmin=0, vmax=1)
        axes[1].set_title("GT 标注")
        axes[1].axis("off")

        axes[2].imshow(image[z], cmap="gray", vmin=0, vmax=1)
        axes[2].imshow(pred[z], alpha=0.4, cmap="Blues", vmin=0, vmax=1)
        axes[2].set_title(f"预测（Dice={dice:.3f}）")
        axes[2].axis("off")

        plt.tight_layout()
        os.makedirs(out_dir, exist_ok=True)
        out_path = os.path.join(out_dir, f"sample_{sample_idx:03d}_dice{dice:.3f}.png")
        plt.savefig(out_path, dpi=100, bbox_inches="tight")
        plt.close(fig)
        print(f"  [vis] 保存: {out_path}")
    except ImportError:
        print("  [vis] 跳过：matplotlib 未安装")


def evaluate(args: argparse.Namespace) -> None:
    set_determinism(seed=config.RANDOM_SEED)
    device = select_device(args.device)
    print(f"[eval] 使用设备: {device}")

    # ---- 数据 ----
    _, val_dicts = get_train_val_dicts()
    if args.num_samples:
        val_dicts = val_dicts[: args.num_samples]
        print(f"[eval] 限定评估样本数: {args.num_samples}")

    val_ds = Dataset(data=val_dicts, transform=get_val_transforms())
    val_loader = DataLoader(val_ds, batch_size=1, shuffle=False, num_workers=0)
    print(f"[eval] 验证样本数: {len(val_dicts)}")

    # ---- 模型 ----
    ckpt_path = args.checkpoint
    if not ckpt_path:
        ckpt_path = os.path.join(config.CHECKPOINT_DIR, config.BEST_MODEL_FILENAME)

    if not os.path.isfile(ckpt_path):
        print(f"[eval] 权重文件不存在: {ckpt_path}")
        print("  请先运行 train.py 生成权重，或用 --checkpoint 指定路径")
        sys.exit(1)

    model = build_model().to(device)
    ckpt = torch.load(ckpt_path, map_location=device, weights_only=False)
    sd = ckpt.get("model", ckpt.get("state_dict", ckpt))
    clean_sd = {(k[len("module."):] if k.startswith("module.") else k): v for k, v in sd.items()}
    model.load_state_dict(clean_sd)
    model.eval()
    print(f"[eval] 加载权重: {ckpt_path}")
    if "epoch" in ckpt:
        print(f"       训练到 epoch {ckpt['epoch']}, best_dice={ckpt.get('best_dice', 'N/A')}")

    # ---- 评估 ----
    dice_metric = DiceMetric(include_background=False, reduction="mean")
    post_pred = Compose([AsDiscrete(argmax=True, to_onehot=config.OUT_CHANNELS)])
    post_label = Compose([AsDiscrete(to_onehot=config.OUT_CHANNELS)])

    per_sample_dice = []
    t0 = time.time()

    with torch.no_grad():
        for i, val_data in enumerate(val_loader):
            val_inputs = val_data["image"].to(device)
            val_labels = val_data["label"].to(device)

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

            # 单样本 Dice
            per_dice = dice_metric.aggregate().item()
            per_sample_dice.append(per_dice)
            print(f"  sample {i:02d}: Dice={per_dice:.4f}")
            dice_metric.reset()

            # 可视化
            if args.save_vis:
                img_np = val_inputs[0, 0].cpu().numpy()
                lbl_np = (val_labels[0, 0] > 0).cpu().numpy().astype(np.uint8)
                pred_np = (val_outputs[0].argmax(0) > 0).cpu().numpy().astype(np.uint8)
                save_slice_vis(img_np, lbl_np, pred_np, "eval_outputs", i, per_dice)

    elapsed = time.time() - t0
    mean_dice = float(np.mean(per_sample_dice))
    std_dice = float(np.std(per_sample_dice))
    print(f"\n[eval] 总耗时: {elapsed:.1f}s  平均 {elapsed / len(per_sample_dice):.1f}s/样本")
    print(f"[eval] 验证 Dice:  mean={mean_dice:.4f}  std={std_dice:.4f}")
    print(f"[eval] Dice 范围:  min={min(per_sample_dice):.4f}  max={max(per_sample_dice):.4f}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="肺结节分割模型评估脚本")
    parser.add_argument("--checkpoint", type=str, default=None, help="权重文件路径")
    parser.add_argument("--device", type=str, default=None, help="设备（cuda/mps/cpu）")
    parser.add_argument("--save-vis", action="store_true", help="保存切片可视化 PNG")
    parser.add_argument("--num-samples", type=int, default=None, help="只评估前 N 个样本")
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    evaluate(args)
