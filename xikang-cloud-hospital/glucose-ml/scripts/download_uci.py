"""Download UCI AIM'94 Diabetes dataset."""

from __future__ import annotations

import sys
import tarfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from src.config import DATA_RAW

UCI_URL = "https://archive.ics.uci.edu/ml/machine-learning-databases/diabetes/diabetes-data.tar.Z"
GITHUB_BASE = "https://raw.githubusercontent.com/hayesall/diabetes-time-series/main/Diabetes-Data"


def download_github_files() -> None:
    out_dir = DATA_RAW / "Diabetes-Data"
    out_dir.mkdir(parents=True, exist_ok=True)
    for i in range(1, 71):
        name = f"data-{i:02d}"
        dest = out_dir / name
        if dest.exists() and dest.stat().st_size > 100:
            continue
        url = f"{GITHUB_BASE}/{name}"
        print(f"Fetching {url}")
        urllib.request.urlretrieve(url, dest)


def main() -> None:
    DATA_RAW.mkdir(parents=True, exist_ok=True)
    extract_dir = DATA_RAW / "Diabetes-Data"
    if extract_dir.exists() and any(extract_dir.iterdir()):
        print(f"Already have data under {extract_dir}")
        return

    archive = DATA_RAW / "diabetes-data.tar.Z"
    try:
        if not archive.exists():
            print(f"Downloading {UCI_URL} ...")
            urllib.request.urlretrieve(UCI_URL, archive)
        print("Extracting tar archive ...")
        with tarfile.open(archive, "r:") as tar:
            tar.extractall(DATA_RAW)
    except Exception as ex:
        print(f"UCI archive failed ({ex}), falling back to GitHub mirror ...")
        download_github_files()

    files = list(DATA_RAW.rglob("data-*"))
    print(f"Done. Found {len(files)} patient files under {DATA_RAW}")


if __name__ == "__main__":
    main()
