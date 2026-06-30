"""Parse UCI diabetes files → hourly multivariate series → train/val windows.

Deprecated: use scripts/prepare_all.py for multi-dataset training.
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

if __name__ == "__main__":
    script = Path(__file__).resolve().parent / "prepare_all.py"
    raise SystemExit(subprocess.call([sys.executable, str(script)], cwd=script.parents[1]))
