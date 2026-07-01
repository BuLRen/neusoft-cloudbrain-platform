"""Download UCI + UCHTT1DM + ShanghaiT1DM + D1NAMO glucose datasets."""

from __future__ import annotations

from collections.abc import Callable
import sys
import tarfile
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from src.config import DATA_RAW  # noqa: E402

UCI_URL = "https://archive.ics.uci.edu/ml/machine-learning-databases/diabetes/diabetes-data.tar.Z"
UCI_GITHUB = "https://raw.githubusercontent.com/hayesall/diabetes-time-series/main/Diabetes-Data"
UCH_ZIP = "https://github.com/fisiologiacuantitativauc/UC_HT_T1DM/archive/refs/heads/main.zip"
SHANGHAI_ZIP = "https://ndownloader.figshare.com/files/38259264"
D1NAMO_BASE = "https://zenodo.org/records/5651217/files"
D1NAMO_GLUCOSE_ARCHIVES = (
    "diabetes_subset_pictures-glucose-food-insulin.zip",
    "healthy_subset_pictures-glucose-food.zip",
)
D1NAMO_GLUCOSE_SIZES = {
    "diabetes_subset_pictures-glucose-food-insulin.zip": 251_668_618,
    "healthy_subset_pictures-glucose-food.zip": 477_319_345,
}
USER_AGENT = "glucose-ml-downloader/1.0 (research; python urllib)"


def _archive_status(path: Path, expected_size: int | None = None) -> dict[str, object]:
    if not path.exists():
        return {"exists": False, "size": 0, "is_zip": False}
    size = path.stat().st_size
    return {
        "exists": True,
        "size": size,
        "expected_size": expected_size,
        "size_ok": expected_size is None or size == expected_size,
        "is_zip": zipfile.is_zipfile(path),
    }


def _download_with_resume(
    url: str,
    dest: Path,
    *,
    chunk_size: int = 8 * 1024 * 1024,
    expected_size: int | None = None,
    max_attempts: int = 8,
    require_zip: bool = False,
) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)

    for attempt in range(1, max_attempts + 1):
        current = dest.stat().st_size if dest.exists() else 0
        if expected_size is not None and current == expected_size:
            if not require_zip or zipfile.is_zipfile(dest):
                return

        if expected_size is not None and current > expected_size:
            dest.unlink(missing_ok=True)
            current = 0

        headers = {"User-Agent": USER_AGENT}
        if current > 0 and expected_size is not None and current < expected_size:
            headers["Range"] = f"bytes={current}-"
        elif current > 0 and expected_size is None:
            headers["Range"] = f"bytes={current}-"

        request = urllib.request.Request(url, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=180) as response:
                status = getattr(response, "status", response.getcode())
                mode = "ab" if current > 0 and status == 206 else "wb"
                if mode == "wb" and current > 0 and status != 206:
                    current = 0
                with dest.open(mode) as fh:
                    while True:
                        chunk = response.read(chunk_size)
                        if not chunk:
                            break
                        fh.write(chunk)
        except urllib.error.HTTPError as ex:
            if ex.code == 416 and dest.exists() and (not require_zip or zipfile.is_zipfile(dest)):
                return
            if attempt == max_attempts:
                raise
            time.sleep(min(2**attempt, 20))
            continue
        except Exception as ex:
            if attempt == max_attempts:
                raise
            print(f"download error {dest.name} attempt {attempt}: {ex}")
            time.sleep(min(2**attempt, 20))
            continue

        status_after = _archive_status(dest, expected_size)
        if expected_size is not None and status_after["size"] != expected_size:
            print(
                f"incomplete {dest.name}: {status_after['size']}/{expected_size} bytes "
                f"(attempt {attempt}/{max_attempts}), resuming..."
            )
            continue
        if require_zip and not status_after["is_zip"]:
            if attempt == max_attempts:
                break
            print(f"invalid zip {dest.name} after attempt {attempt}, retrying...")
            dest.unlink(missing_ok=True)
            continue
        return

    final = _archive_status(dest, expected_size)
    raise RuntimeError(
        f"downloaded file is invalid: {dest.name} "
        f"({final.get('size', 0)} bytes, expected {expected_size})"
    )


def _download_plain_file(url: str, dest: Path, *, min_size: int = 500, max_attempts: int = 6) -> None:
    """Download a small plain-text file (UCI patient records) with retries."""
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size >= min_size:
        return

    last_error: Exception | None = None
    for attempt in range(1, max_attempts + 1):
        try:
            headers = {"User-Agent": USER_AGENT}
            request = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(request, timeout=90) as response:
                data = response.read()
            if len(data) < min_size:
                raise RuntimeError(f"too small ({len(data)} bytes)")
            dest.write_bytes(data)
            return
        except Exception as ex:
            last_error = ex
            if dest.exists():
                dest.unlink(missing_ok=True)
            if attempt < max_attempts:
                wait = min(2**attempt, 30)
                print(f"retry {dest.name} ({attempt}/{max_attempts}) after {wait}s: {ex}")
                time.sleep(wait)
    raise RuntimeError(f"failed after {max_attempts} attempts: {last_error}") from last_error


def _try_uci_tar_bundle(out_dir: Path) -> None:
    """Download and extract the official UCI diabetes tar (most reliable, ~39 patients)."""
    archive = DATA_RAW / "diabetes-data.tar.Z"
    if not archive.exists() or archive.stat().st_size < 10_000:
        print("downloading UCI official bundle (diabetes-data.tar.Z)...")
        try:
            _download_with_resume(UCI_URL, archive, max_attempts=5)
        except Exception as ex:
            print(f"uci tar download failed ({ex}), will use GitHub mirror for missing files")
            return

    try:
        print("extracting UCI tar bundle...")
        with tarfile.open(archive, "r:") as tar:
            tar.extractall(DATA_RAW)
    except Exception as ex:
        print(f"uci tar extract failed ({ex})")


def download_uci() -> None:
    out_dir = DATA_RAW / "Diabetes-Data"
    out_dir.mkdir(parents=True, exist_ok=True)

    existing = len(list(out_dir.glob("data-*")))
    if existing < 55:
        _try_uci_tar_bundle(out_dir)

    missing = [
        i
        for i in range(1, 71)
        if not (out_dir / f"data-{i:02d}").exists()
        or (out_dir / f"data-{i:02d}").stat().st_size < 500
    ]
    if missing:
        print(f"complementing {len(missing)} UCI files from GitHub mirror (slow, may skip some)...")

    for i in missing:
        name = f"data-{i:02d}"
        dest = out_dir / name
        try:
            _download_plain_file(f"{UCI_GITHUB}/{name}", dest)
            time.sleep(1.0)
        except Exception as file_ex:
            print(f"skip {name}: {file_ex}")

    print(f"uci ready: {len(list(out_dir.glob('data-*')))} files")


def download(
    url: str,
    dest: Path,
    *,
    valid: Callable[[Path], bool] | None = None,
    expected_size: int | None = None,
) -> None:
    if dest.exists() and dest.stat().st_size > 1000:
        ok = (expected_size is None or dest.stat().st_size == expected_size) and (
            valid is None or valid(dest)
        )
        if ok:
            print(f"skip existing {dest.name}")
            return
        print(f"removing invalid {dest.name} ({dest.stat().st_size} bytes)")
        dest.unlink(missing_ok=True)

    print(f"downloading {url}")
    if expected_size is not None:
        print(f"expected size: {expected_size} bytes")
    _download_with_resume(url, dest, expected_size=expected_size, require_zip=valid is zipfile.is_zipfile)
    if valid is not None and dest.exists() and not valid(dest):
        size = dest.stat().st_size
        dest.unlink(missing_ok=True)
        raise RuntimeError(f"downloaded file is invalid: {dest.name} ({size} bytes)")


def _extract_zip(archive: Path, target: Path) -> None:
    target.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive, "r") as zf:
        zf.extractall(target)


def _d1namo_glucose_ready() -> bool:
    for name in D1NAMO_GLUCOSE_ARCHIVES:
        folder = DATA_RAW / name.replace(".zip", "")
        if folder.exists() and any(folder.rglob("glucose.csv")):
            return True
        nested = folder / folder.name
        if nested.exists() and any(nested.rglob("glucose.csv")):
            return True
    return False


def download_uchtt1dm() -> None:
    target = DATA_RAW / "UC_HT_T1DM"
    if target.exists() and any(target.iterdir()):
        print("uchtt1dm ready")
        return
    archive = DATA_RAW / "UC_HT_T1DM-main.zip"
    download(UCH_ZIP, archive, valid=zipfile.is_zipfile)
    with zipfile.ZipFile(archive, "r") as zf:
        zf.extractall(DATA_RAW)
    extracted = DATA_RAW / "UC_HT_T1DM-main"
    if extracted.exists():
        extracted.rename(target)


def download_shanghai() -> None:
    target = DATA_RAW / "shanghai"
    if _shanghai_ready(target):
        print("shanghai ready")
        return
    archive = DATA_RAW / "shanghai_data.zip"
    download(SHANGHAI_ZIP, archive, valid=zipfile.is_zipfile)
    with zipfile.ZipFile(archive, "r") as zf:
        zf.extractall(target)


def _shanghai_ready(target: Path) -> bool:
    for cohort in ("Shanghai_T1DM", "Shanghai_T2DM"):
        if any(target.rglob(f"{cohort}/*.xlsx")):
            return True
    return False


def download_d1namo(skip: bool = False) -> None:
    if skip:
        print("skip d1namo download")
        return
    if _d1namo_glucose_ready():
        print("d1namo glucose ready")
        return

    for archive_name in D1NAMO_GLUCOSE_ARCHIVES:
        archive = DATA_RAW / archive_name
        url = f"{D1NAMO_BASE}/{archive_name}?download=1"
        expected_size = D1NAMO_GLUCOSE_SIZES.get(archive_name)
        download(url, archive, valid=zipfile.is_zipfile, expected_size=expected_size)
        _extract_zip(archive, DATA_RAW)
        glucose_count = len(list((DATA_RAW / archive_name.replace(".zip", "")).rglob("glucose.csv")))
        print(f"extracted {archive_name}: {glucose_count} glucose.csv files")

    total = sum(
        len(list((DATA_RAW / name.replace(".zip", "")).rglob("glucose.csv")))
        for name in D1NAMO_GLUCOSE_ARCHIVES
    )
    print(f"d1namo glucose total: {total} files")


def main() -> None:
    skip_d1namo = "--skip-d1namo" in sys.argv
    DATA_RAW.mkdir(parents=True, exist_ok=True)
    download_uci()
    download_uchtt1dm()
    download_shanghai()
    download_d1namo(skip=skip_d1namo)
    print("all downloads finished")


if __name__ == "__main__":
    main()
