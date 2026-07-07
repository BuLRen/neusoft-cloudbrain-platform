"""
数据集加载
==========

直接读取本地 Task06_Lung 目录（不下载、不拷贝），解析 dataset.json 得到
image/label 配对，按指定比例划分 train/val。

Task06_Lung 的标注：
  label=0  背景
  label=1  肺癌肿瘤

注意：该数据集肿瘤体积极小（前景体素占比 < 1%），训练必须用 RandCropByPosNegLabeld
做正负采样来缓解极端类别不均衡。
"""

from __future__ import annotations

import json
import os
import random
from typing import List, Dict, Any

from . import config


def load_data_dicts(data_root: str = config.DATA_ROOT) -> List[Dict[str, str]]:
    """
    解析 dataset.json → [{image: abs_path, label: abs_path}, ...]

    返回
    ----
    完整训练集的 data_dicts（63 条），顺序随机打乱（seed 固定）。
    """
    json_path = os.path.join(data_root, "dataset.json")
    if not os.path.isfile(json_path):
        raise FileNotFoundError(
            f"找不到 dataset.json：{json_path}\n"
            f"请确认 LUNG_DATASET_ROOT 或 ~/Downloads/Task06_Lung 路径正确。"
        )

    with open(json_path, "r", encoding="utf-8") as f:
        meta = json.load(f)

    raw = meta.get("training", [])
    if not raw:
        raise ValueError("dataset.json 中 training 字段为空")

    data_dicts = []
    for item in raw:
        # 路径形如 "./imagesTr/lung_001.nii.gz"，需转成绝对路径
        img_rel = item["image"].lstrip("./")
        lbl_rel = item["label"].lstrip("./")
        data_dicts.append({
            "image": os.path.join(data_root, img_rel),
            "label": os.path.join(data_root, lbl_rel),
        })

    rng = random.Random(config.RANDOM_SEED)
    rng.shuffle(data_dicts)
    return data_dicts


def split_train_val(
    data_dicts: List[Dict[str, str]],
    val_fraction: float = config.VAL_FRACTION,
) -> tuple[List[Dict[str, str]], List[Dict[str, str]]]:
    """
    按比例划分 train/val。

    返回
    ----
    (train_dicts, val_dicts)
    """
    n = len(data_dicts)
    n_val = max(1, int(round(n * val_fraction)))
    val_dicts = data_dicts[:n_val]
    train_dicts = data_dicts[n_val:]
    return train_dicts, val_dicts


def get_train_val_dicts(
    data_root: str = config.DATA_ROOT,
    val_fraction: float = config.VAL_FRACTION,
) -> tuple[List[Dict[str, str]], List[Dict[str, str]]]:
    """
    一站式：加载 + 划分。

    返回
    ----
    (train_dicts, val_dicts)
    """
    all_dicts = load_data_dicts(data_root)
    train, val = split_train_val(all_dicts, val_fraction)
    print(f"[dataset] 总样本: {len(all_dicts)}  训练: {len(train)}  验证: {len(val)}")
    return train, val
