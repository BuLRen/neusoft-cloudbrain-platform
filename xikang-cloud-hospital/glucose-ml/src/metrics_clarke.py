"""Clarke Error Grid analysis for blood glucose predictions (mmol/L)."""

from __future__ import annotations

from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np


def clarke_zone(actual: np.ndarray, predicted: np.ndarray) -> np.ndarray:
    a = np.asarray(actual, dtype=float)
    p = np.asarray(predicted, dtype=float)
    zones = np.full(a.shape, "E", dtype=object)

    for i in range(len(a)):
        av, pv = a[i], p[i]
        if av <= 0 or pv <= 0:
            zones[i] = "E"
            continue
        if (pv <= 1.11 * av + 0.66) and (pv >= 0.83 * av - 1.0):
            zones[i] = "A"
        elif (pv <= 1.2 * av + 1.0) and (pv >= 0.67 * av - 1.0):
            zones[i] = "B"
        elif (av < 3.9 and pv > 13.9) or (av > 13.9 and pv < 3.9):
            zones[i] = "E"
        elif av >= 3.9 and av <= 13.9 and pv >= 3.9 and pv <= 13.9:
            zones[i] = "C"
        elif (av < 3.9 and pv < 3.9) or (av > 13.9 and pv > 13.9):
            zones[i] = "D"
        else:
            zones[i] = "C"
    return zones


def clarke_ab_percent(actual: np.ndarray, predicted: np.ndarray) -> float:
    zones = clarke_zone(actual, predicted)
    ab = np.sum((zones == "A") | (zones == "B"))
    return float(ab / len(zones) * 100) if len(zones) else 0.0


def plot_clarke_grid(actual: np.ndarray, predicted: np.ndarray, out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    zones = clarke_zone(actual, predicted)
    colors = {"A": "#20b486", "B": "#1f8cff", "C": "#f59f00", "D": "#ef4d5a", "E": "#8ba0b6"}

    fig, ax = plt.subplots(figsize=(7, 7))
    for z in ["A", "B", "C", "D", "E"]:
        mask = zones == z
        if mask.any():
            ax.scatter(actual[mask], predicted[mask], c=colors[z], label=f"Zone {z}", alpha=0.6, s=18)

    lim = max(float(np.max(actual)), float(np.max(predicted)), 15.0)
    ax.plot([0, lim], [0, lim], "k--", linewidth=0.8)
    ax.set_xlabel("Actual (mmol/L)")
    ax.set_ylabel("Predicted (mmol/L)")
    ax.set_title("Clarke Error Grid")
    ax.legend(loc="upper left", fontsize=8)
    ax.set_xlim(0, lim)
    ax.set_ylim(0, lim)
    fig.tight_layout()
    fig.savefig(out_path, dpi=120)
    plt.close(fig)
