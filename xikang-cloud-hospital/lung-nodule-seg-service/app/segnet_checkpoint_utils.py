"""
SegNet checkpoint 容错加载
============================

Ola-Vish/lung-tumor-segmentation 用 PyTorch-Lightning 1.5.4 保存 checkpoint，
其 `hyper_parameters` 中包含了 SegNet / CrossEntropyLoss 等原始对象引用
（例如 `project.models.segnet.SegNet`）。本服务运行环境既不安装
pytorch-lightning，也不 vendor 原仓库的 `project` 包，因此这里实现一个
"容错 Unpickler"：遇到无法解析的类（缺失的第三方模块/类）时用占位对象代替，
不影响我们真正需要的 `state_dict`（纯 Tensor 字典，不依赖任何自定义类）。

推荐流程：用 scripts/convert_segnet_checkpoint.py 把原始 .ckpt 转换成干净的
state_dict（.pth）后再放到 app/config.py 的 SEGNET_MODEL_PATH，这样服务启动
时无需每次都走容错解析。但本模块同样支持直接加载原始 .ckpt（兼容性兜底）。
"""

from __future__ import annotations

import pickle
from typing import Any, Dict

import torch


class _PlaceholderObject:
    """无法解析的 pickle 类的占位替代品，只需支持被无害地构造 / setstate。"""

    def __setstate__(self, state: Any) -> None:
        if isinstance(state, dict):
            self.__dict__.update(state)

    def __reduce__(self):
        return (_PlaceholderObject, ())


class _TolerantUnpickler(pickle.Unpickler):
    def find_class(self, module: str, name: str):  # noqa: D102
        try:
            return super().find_class(module, name)
        except Exception:
            return _PlaceholderObject


class _TolerantPickleModule:
    """实现 torch.load(pickle_module=...) 所需的最小接口。"""

    Unpickler = _TolerantUnpickler
    Pickler = pickle.Pickler
    load = staticmethod(pickle.load)
    dump = staticmethod(pickle.dump)


def _strip_prefixes(state_dict: Dict[str, torch.Tensor]) -> Dict[str, torch.Tensor]:
    """
    兼容三种权重格式：
      1. 本服务转换脚本 torch.save(model.state_dict()) 保存的干净权重（无前缀）
      2. PyTorch-Lightning checkpoint 的 state_dict（key 形如 "model.encoder...."）
      3. DataParallel 保存的权重（key 形如 "module...."）
    """
    has_model_prefix = any(k.startswith("model.") for k in state_dict.keys())
    cleaned: Dict[str, torch.Tensor] = {}
    for k, v in state_dict.items():
        if k.startswith("module."):
            k = k[len("module."):]
        if has_model_prefix:
            if not k.startswith("model."):
                continue  # 跳过 loss_fn / torchmetrics 等非网络权重
            k = k[len("model."):]
        cleaned[k] = v
    return cleaned


def load_segnet_state_dict(checkpoint_path: str) -> Dict[str, torch.Tensor]:
    """
    加载 SegNet 权重文件，返回可直接 `model.load_state_dict()` 的干净 state_dict。

    支持：
      - 本服务转换脚本产出的纯 state_dict（.pth）
      - 原仓库 PyTorch-Lightning 直接保存的 checkpoint（.ckpt），容错解析
    """
    raw = torch.load(
        checkpoint_path,
        map_location="cpu",
        pickle_module=_TolerantPickleModule,
        weights_only=False,
    )
    if isinstance(raw, dict) and isinstance(raw.get("state_dict"), dict):
        sd = raw["state_dict"]
    elif isinstance(raw, dict) and isinstance(raw.get("model"), dict):
        sd = raw["model"]
    elif isinstance(raw, dict):
        sd = raw
    else:
        raise ValueError(f"无法识别的 checkpoint 格式: {type(raw)}")
    return _strip_prefixes(sd)
