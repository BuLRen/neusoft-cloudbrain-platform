"""
下载并整理 nnU-Net 预训练权重
==============================

从 Hugging Face 下载在 MSD Task06_Lung 上训练好的 nnU-Net v2 权重
（Lab-Rasool/CLN-Segmenter-MSD-fold0），并按官方要求的目录结构
整理到 models/nnunet_results/ 下，供 app/nnunet_backend.py 加载。

用法:
    pip install -r requirements-nnunet.txt   # 先装 huggingface_hub / nnunetv2
    python -m scripts.download_nnunet_weights

    # 自定义仓库 / 输出目录
    python -m scripts.download_nnunet_weights \
        --repo-id Lab-Rasool/CLN-Segmenter-MSD-fold0 \
        --dest models/nnunet_results

来源与许可证:
    模型: https://huggingface.co/Lab-Rasool/CLN-Segmenter-MSD-fold0
    许可证: CC-BY-SA 4.0（继承自 MSD Task06 数据集）
    重要提示（来自模型卡片）：
      - 仅在公开的 MSD Task06 Lung 63 例数据上训练，未做任何临床验证
      - 是研究用途 artifact，不是医疗器械，不能用于临床诊断/治疗决策
"""

from __future__ import annotations

import argparse
import os
import shutil
import sys

_PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _PROJECT_ROOT not in sys.path:
    sys.path.insert(0, _PROJECT_ROOT)

from app import config as app_config  # noqa: E402

DEFAULT_REPO_ID = "Lab-Rasool/CLN-Segmenter-MSD-fold0"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="下载并整理 nnU-Net 预训练权重")
    parser.add_argument("--repo-id", type=str, default=DEFAULT_REPO_ID, help="Hugging Face 仓库 ID")
    parser.add_argument(
        "--dest",
        type=str,
        default=app_config.NNUNET_RESULTS_DIR,
        help="nnUNet_results 根目录（默认取 app/config.py 的 NNUNET_RESULTS_DIR）",
    )
    parser.add_argument(
        "--dataset-name",
        type=str,
        default=app_config.NNUNET_DATASET_NAME,
        help="nnU-Net 数据集目录名，例如 Dataset502_MSDLung",
    )
    parser.add_argument("--trainer", type=str, default=app_config.NNUNET_TRAINER)
    parser.add_argument("--plans", type=str, default=app_config.NNUNET_PLANS)
    parser.add_argument("--configuration", type=str, default=app_config.NNUNET_CONFIGURATION)
    parser.add_argument("--fold", type=int, default=app_config.NNUNET_FOLD)
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    try:
        from huggingface_hub import snapshot_download
    except ImportError as e:
        print(
            "未安装 huggingface_hub，请先执行: "
            "pip install -r requirements-nnunet.txt",
            file=sys.stderr,
        )
        raise SystemExit(1) from e

    print(f"[download] 从 Hugging Face 下载: {args.repo_id}")
    local_dir = snapshot_download(repo_id=args.repo_id)
    print(f"[download] 下载完成: {local_dir}")

    model_dir = os.path.join(
        args.dest, args.dataset_name,
        f"{args.trainer}__{args.plans}__{args.configuration}",
    )
    fold_dir = os.path.join(model_dir, f"fold_{args.fold}")
    os.makedirs(fold_dir, exist_ok=True)

    def _copy(src_name: str, dst_path: str, required: bool = True) -> None:
        src_path = os.path.join(local_dir, src_name)
        if not os.path.isfile(src_path):
            if required:
                raise FileNotFoundError(f"权重仓库中缺少必需文件: {src_name}")
            print(f"[download] 跳过（可选文件不存在）: {src_name}")
            return
        shutil.copy2(src_path, dst_path)
        print(f"[download] 已复制: {src_name} -> {dst_path}")

    _copy("dataset.json", os.path.join(model_dir, "dataset.json"))
    _copy("nnUNetPlans.json", os.path.join(model_dir, "plans.json"))
    _copy("dataset_fingerprint.json", os.path.join(model_dir, "dataset_fingerprint.json"), required=False)
    _copy("checkpoint_best.pth", os.path.join(fold_dir, "checkpoint_best.pth"))
    _copy("splits_final.json", os.path.join(fold_dir, "splits_final.json"), required=False)

    print("\n[download] 权重整理完成，目录结构：")
    for root, _, files in os.walk(model_dir):
        for f in files:
            print("  " + os.path.relpath(os.path.join(root, f), args.dest))

    print(
        "\n[download] 完成。启动服务前请设置环境变量：\n"
        "  export LUNG_NODULE_SEG_BACKEND=nnunet\n"
        f"  (如自定义了 --dest/--dataset-name 等参数，也请设置对应的 "
        f"NNUNET_RESULTS_DIR / NNUNET_DATASET_NAME 等环境变量)\n\n"
        "重要提示：该权重仅在公开的 MSD Task06_Lung 63 例数据集上训练，\n"
        "未做任何临床验证，仅供研究/工程验证使用，不能作为临床诊断依据。\n"
        "许可证 CC-BY-SA 4.0，来源: "
        f"https://huggingface.co/{args.repo_id}"
    )


if __name__ == "__main__":
    main()
