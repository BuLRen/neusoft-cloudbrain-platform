"""CT 体数据三维高斯平滑（SimpleITK Recursive Gaussian）。"""

from __future__ import annotations

from typing import Sequence, Union

import SimpleITK as sitk


def smooth_gaussian_3d(
    volume: sitk.Image,
    sigma: Union[float, Sequence[float]] = 1.0,
) -> sitk.Image:
    """
    对整卷 CT 做各向同性或各向异性高斯平滑。

    sigma: 标量时对 x/y/z 三轴共用；或长度为 3 的序列 (sx, sy, sz)。
           单位为 **体素方向上的标准差**（与 SimpleITK/ITK RecursiveGaussian 默认一致）。
    """
    if isinstance(sigma, (int, float)):
        s = float(sigma)
        sig_list = [s, s, s]
    else:
        xs = tuple(float(x) for x in sigma)
        if len(xs) != 3:
            raise ValueError("sigma 序列长度须为 3 (x, y, z)")
        sig_list = list(xs)
    return sitk.SmoothingRecursiveGaussian(volume, sigma=sig_list)
