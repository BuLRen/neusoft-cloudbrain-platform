"""
转换 Ola-Vish/lung-tumor-segmentation 的 SegNet checkpoint
============================================================

作者仓库（https://github.com/Ola-Vish/lung-tumor-segmentation，MIT License）
把训练好的 SegNet 权重存成 Google Drive 上的 PyTorch-Lightning checkpoint
（.ckpt），下载地址见其 README「My SegNet checkpoint can be downloaded from
this link」一节，无官方 API，需手动在浏览器下载。

用法：
    # 1. 从 Google Drive 手动下载 checkpoint 到本地
    #    （文件名类似 epoch=xx-step=xx.ckpt）
    # 2. 运行转换脚本，默认输出到 app/config.py 的 SEGNET_MODEL_PATH：
    python -m scripts.convert_segnet_checkpoint --src ~/Downloads/xxx.ckpt

    # 3. 重启 lung-nodule-seg-service，GET /health 的 available_models 中
    #    segnet.loaded 应变为 true。

来源与许可证:
    代码/权重: https://github.com/Ola-Vish/lung-tumor-segmentation（MIT License）
    训练数据: Medical Segmentation Decathlon Task06_Lung
    重要提示: 该权重未经任何临床验证，仅供研究/工程演示使用，
              不能作为临床诊断或治疗决策依据。
"""

from __future__ import annotations

import argparse
import os
import sys

_PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _PROJECT_ROOT not in sys.path:
    sys.path.insert(0, _PROJECT_ROOT)

import torch  # noqa: E402

from app import config as app_config  # noqa: E402
from app.segnet_checkpoint_utils import load_segnet_state_dict  # noqa: E402
from app.segnet_model import build_segnet_model  # noqa: E402


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="转换 SegNet checkpoint 为干净的 state_dict")
    parser.add_argument("--src", type=str, required=True, help="下载的 .ckpt 文件路径")
    parser.add_argument(
        "--dst",
        type=str,
        default=app_config.SEGNET_MODEL_PATH,
        help="输出路径（默认取 app/config.py 的 SEGNET_MODEL_PATH）",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    src = os.path.expanduser(args.src)
    if not os.path.isfile(src):
        print(f"[convert] 源文件不存在: {src}", file=sys.stderr)
        raise SystemExit(1)

    print(f"[convert] 解析 checkpoint: {src}")
    state_dict = load_segnet_state_dict(src)
    print(f"[convert] 解析出 {len(state_dict)} 个权重张量")

    print("[convert] 用干净 state_dict 加载到 SegNet 架构做校验...")
    model = build_segnet_model(warm_start=False)
    missing, unexpected = model.load_state_dict(state_dict, strict=False)
    if missing:
        print(
            f"[convert] 错误：缺少 {len(missing)} 个 key，架构可能不匹配：{missing[:10]}",
            file=sys.stderr,
        )
        raise SystemExit(1)
    if unexpected:
        print(f"[convert] 提示：忽略 {len(unexpected)} 个多余 key（属正常现象）: {unexpected[:5]}")

    dst_dir = os.path.dirname(args.dst)
    if dst_dir:
        os.makedirs(dst_dir, exist_ok=True)
    torch.save(model.state_dict(), args.dst)
    size_mb = os.path.getsize(args.dst) / (1024 * 1024)
    print(f"\n[convert] 完成，已保存干净权重: {args.dst}（{size_mb:.1f} MB）")
    print(
        "\n[convert] 重要提示：该权重仅在公开的 MSD Task06_Lung 数据集上训练，\n"
        "未做任何临床验证，仅供研究/工程演示使用，不能作为临床诊断依据。"
    )


if __name__ == "__main__":
    main()
