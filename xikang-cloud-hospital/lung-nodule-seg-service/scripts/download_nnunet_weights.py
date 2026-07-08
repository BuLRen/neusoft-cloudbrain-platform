"""
下载并整理 nnU-Net 预训练权重
==============================

从 Hugging Face 下载在 MSD Task06_Lung 上训练好的 nnU-Net v2 权重
（Lab-Rasool/CLN-Segmenter-MSD-fold0），并按官方要求的目录结构
整理到 models/nnunet_results/ 下，供 app/nnunet_backend.py 加载。

用法:
    pip install -r requirements-nnunet.txt   # 先装 huggingface_hub / nnunetv2

    # 推荐：国内网络默认走 hf-mirror 镜像（无需代理）
    python -m scripts.download_nnunet_weights

    # 使用代理（如 Clash / V2Ray 本地端口 7890）
    HTTPS_PROXY=http://127.0.0.1:7890 python -m scripts.download_nnunet_weights --no-mirror

    # 浏览器手动下载后，从本地目录整理
    python -m scripts.download_nnunet_weights --from-local ~/Downloads/CLN-Segmenter-MSD-fold0

来源与许可证:
    模型: https://huggingface.co/Lab-Rasool/CLN-Segmenter-MSD-fold0
    镜像: https://hf-mirror.com/Lab-Rasool/CLN-Segmenter-MSD-fold0
    许可证: CC-BY-SA 4.0（继承自 MSD Task06 数据集）
"""

from __future__ import annotations

import argparse
import os
import shutil
import sys
import tempfile
import time
import urllib.error
import urllib.request

_PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _PROJECT_ROOT not in sys.path:
    sys.path.insert(0, _PROJECT_ROOT)

from app import config as app_config  # noqa: E402

DEFAULT_REPO_ID = "Lab-Rasool/CLN-Segmenter-MSD-fold0"
DEFAULT_MIRROR_ENDPOINT = "https://hf-mirror.com"
DEFAULT_OFFICIAL_ENDPOINT = "https://huggingface.co"

# 推理所需文件（不下载 progress.png / validation_summary.json 等无关大文件）
DOWNLOAD_FILES = (
    "dataset.json",
    "nnUNetPlans.json",
    "dataset_fingerprint.json",
    "checkpoint_best.pth",
    "splits_final.json",
)
REQUIRED_FILES = (
    "dataset.json",
    "nnUNetPlans.json",
    "checkpoint_best.pth",
)


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
    parser.add_argument(
        "--from-local",
        type=str,
        default=None,
        help="跳过下载，直接从已解压的本地目录整理权重（浏览器手动下载后使用）",
    )
    parser.add_argument(
        "--endpoint",
        type=str,
        default=None,
        help=(
            "Hugging Face API 端点。"
            f"默认优先使用镜像 {DEFAULT_MIRROR_ENDPOINT}；"
            f"传 {DEFAULT_OFFICIAL_ENDPOINT} 可走官方源（通常需代理）"
        ),
    )
    parser.add_argument(
        "--no-mirror",
        action="store_true",
        help="不使用镜像，强制走官方 huggingface.co（通常需代理）",
    )
    return parser.parse_args()


def _resolve_endpoint(args: argparse.Namespace) -> str:
    if args.endpoint:
        return args.endpoint.rstrip("/")
    if args.no_mirror:
        return DEFAULT_OFFICIAL_ENDPOINT
    env_endpoint = os.environ.get("HF_ENDPOINT", "").strip().rstrip("/")
    if env_endpoint:
        return env_endpoint
    return DEFAULT_MIRROR_ENDPOINT


def _proxy_hint() -> str:
    https_proxy = os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy")
    http_proxy = os.environ.get("HTTP_PROXY") or os.environ.get("http_proxy")
    if https_proxy or http_proxy:
        return f"当前代理: HTTPS_PROXY={https_proxy or '(未设置)'} HTTP_PROXY={http_proxy or '(未设置)'}"
    return "当前未检测到 HTTP(S)_PROXY 环境变量"


def _format_bytes(n: int) -> str:
    if n < 1024:
        return f"{n} B"
    if n < 1024 * 1024:
        return f"{n / 1024:.1f} KB"
    if n < 1024 * 1024 * 1024:
        return f"{n / (1024 * 1024):.1f} MB"
    return f"{n / (1024 * 1024 * 1024):.2f} GB"


def _build_direct_url(endpoint: str, repo_id: str, filename: str) -> str:
    return f"{endpoint.rstrip('/')}/{repo_id}/resolve/main/{filename}"


def _download_with_progress(url: str, dst_path: str, label: str) -> None:
    """流式下载大文件，每 2 秒打印一次进度（避免 tqdm 长时间 0% 误导）。"""
    req = urllib.request.Request(url, headers={"User-Agent": "lung-nodule-seg-service/1.0"})
    opener = urllib.request.build_opener()
    opener.addheaders = [("User-Agent", "lung-nodule-seg-service/1.0")]

    with opener.open(req, timeout=60) as resp:
        total = int(resp.headers.get("Content-Length") or 0)
        chunk_size = 1024 * 256  # 256 KB
        downloaded = 0
        t0 = time.time()
        last_print = t0

        os.makedirs(os.path.dirname(dst_path), exist_ok=True)
        with open(dst_path, "wb") as f:
            while True:
                chunk = resp.read(chunk_size)
                if not chunk:
                    break
                f.write(chunk)
                downloaded += len(chunk)
                now = time.time()
                if now - last_print >= 2.0 or (total > 0 and downloaded >= total):
                    elapsed = max(now - t0, 0.001)
                    speed = downloaded / elapsed
                    if total > 0:
                        pct = downloaded * 100.0 / total
                        print(
                            f"  [{label}] {_format_bytes(downloaded)} / {_format_bytes(total)} "
                            f"({pct:.1f}%)  {_format_bytes(int(speed))}/s  "
                            f"已用 {int(elapsed)}s",
                            flush=True,
                        )
                    else:
                        print(
                            f"  [{label}] 已下载 {_format_bytes(downloaded)}  "
                            f"{_format_bytes(int(speed))}/s  已用 {int(elapsed)}s",
                            flush=True,
                        )
                    last_print = now

    if not os.path.isfile(dst_path) or os.path.getsize(dst_path) == 0:
        raise RuntimeError(f"下载结果为空: {dst_path}")


def _download_small_file_via_hub(repo_id: str, filename: str, endpoint: str, dst_path: str) -> None:
    from huggingface_hub import hf_hub_download

    os.environ["HF_ENDPOINT"] = endpoint
    os.environ.setdefault("HUGGINGFACE_HUB_ENDPOINT", endpoint)

    cached = hf_hub_download(repo_id=repo_id, filename=filename, endpoint=endpoint)
    shutil.copy2(cached, dst_path)
    print(f"  [{filename}] 完成 ({_format_bytes(os.path.getsize(dst_path))})", flush=True)


def _download_from_hub(repo_id: str, endpoint: str) -> str:
    staging_dir = tempfile.mkdtemp(prefix="nnunet_weights_")
    print(f"[download] 仓库: {repo_id}")
    print(f"[download] 端点: {endpoint}")
    print(f"[download] {_proxy_hint()}")
    print(f"[download] 仅下载推理所需 {len(DOWNLOAD_FILES)} 个文件（跳过无关资源）")
    print(f"[download] 临时目录: {staging_dir}\n")

    os.environ["HF_ENDPOINT"] = endpoint
    os.environ.setdefault("HUGGINGFACE_HUB_ENDPOINT", endpoint)

    for i, filename in enumerate(DOWNLOAD_FILES, start=1):
        dst_path = os.path.join(staging_dir, filename)
        required = filename in REQUIRED_FILES
        print(f"[download] ({i}/{len(DOWNLOAD_FILES)}) {filename}", flush=True)

        try:
            if filename == "checkpoint_best.pth":
                url = _build_direct_url(endpoint, repo_id, filename)
                print(f"  直链: {url}", flush=True)
                print("  大文件下载中，每 2 秒刷新进度（247MB 在 ~200KB/s 约需 20 分钟，请耐心等待）...", flush=True)
                _download_with_progress(url, dst_path, filename)
            else:
                _download_small_file_via_hub(repo_id, filename, endpoint, dst_path)
        except Exception as e:
            if not required:
                print(f"  [{filename}] 可选文件下载失败，跳过: {e}", flush=True)
                continue
            print("\n[download] 下载失败。可尝试以下方案：\n", file=sys.stderr)
            print("方案 1：继续用镜像（默认）", file=sys.stderr)
            print("  python -m scripts.download_nnunet_weights\n", file=sys.stderr)
            print("方案 2：开代理后走官方源", file=sys.stderr)
            print("  HTTPS_PROXY=http://127.0.0.1:7890 \\", file=sys.stderr)
            print("    python -m scripts.download_nnunet_weights --no-mirror\n", file=sys.stderr)
            print("方案 3：浏览器手动下载", file=sys.stderr)
            print(f"  打开 {_build_direct_url(endpoint, repo_id, 'checkpoint_best.pth')}", file=sys.stderr)
            print("  把文件放到同一目录后执行：", file=sys.stderr)
            print("  python -m scripts.download_nnunet_weights --from-local /path/to/dir\n", file=sys.stderr)
            shutil.rmtree(staging_dir, ignore_errors=True)
            raise SystemExit(1) from e

        if os.path.isfile(dst_path):
            print(f"  -> 已保存 {_format_bytes(os.path.getsize(dst_path))}\n", flush=True)

    return staging_dir


def _install_weights(local_dir: str, args: argparse.Namespace) -> str:
    model_dir = os.path.join(
        args.dest,
        args.dataset_name,
        f"{args.trainer}__{args.plans}__{args.configuration}",
    )
    fold_dir = os.path.join(model_dir, f"fold_{args.fold}")
    os.makedirs(fold_dir, exist_ok=True)

    def _copy(src_name: str, dst_path: str, required: bool = True) -> None:
        src_path = os.path.join(local_dir, src_name)
        if not os.path.isfile(src_path):
            if required:
                raise FileNotFoundError(
                    f"缺少必需文件: {src_name}\n"
                    f"请确认目录 {local_dir} 包含: {', '.join(REQUIRED_FILES)}"
                )
            print(f"[install] 跳过（可选文件不存在）: {src_name}")
            return
        shutil.copy2(src_path, dst_path)
        print(f"[install] {src_name} -> {dst_path}")

    _copy("dataset.json", os.path.join(model_dir, "dataset.json"))
    _copy("nnUNetPlans.json", os.path.join(model_dir, "plans.json"))
    _copy("dataset_fingerprint.json", os.path.join(model_dir, "dataset_fingerprint.json"), required=False)
    _copy("checkpoint_best.pth", os.path.join(fold_dir, "checkpoint_best.pth"))
    _copy("splits_final.json", os.path.join(fold_dir, "splits_final.json"), required=False)
    return model_dir


def main() -> None:
    args = parse_args()
    staging_dir: str | None = None

    try:
        if args.from_local:
            local_dir = os.path.expanduser(args.from_local)
            if not os.path.isdir(local_dir):
                print(f"[download] 本地目录不存在: {local_dir}", file=sys.stderr)
                raise SystemExit(1)
            print(f"[download] 从本地目录整理: {local_dir}")
        else:
            endpoint = _resolve_endpoint(args)
            staging_dir = _download_from_hub(args.repo_id, endpoint)
            local_dir = staging_dir
            print(f"[download] 全部文件下载完成: {local_dir}")

        model_dir = _install_weights(local_dir, args)

        print("\n[download] 权重整理完成，目录结构：")
        for root, _, files in os.walk(model_dir):
            for f in files:
                print("  " + os.path.relpath(os.path.join(root, f), args.dest))

        print(
            "\n[download] 完成。启动服务：\n"
            "  export LUNG_NODULE_SEG_BACKEND=nnunet\n"
            "  python -m app.main\n\n"
            "重要提示：该权重仅在公开的 MSD Task06_Lung 63 例数据集上训练，\n"
            "未做任何临床验证，仅供研究/工程验证使用，不能作为临床诊断依据。"
        )
    finally:
        if staging_dir and os.path.isdir(staging_dir):
            shutil.rmtree(staging_dir, ignore_errors=True)


if __name__ == "__main__":
    main()
