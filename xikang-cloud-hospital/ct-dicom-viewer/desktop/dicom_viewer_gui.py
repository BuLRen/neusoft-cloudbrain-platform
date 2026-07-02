import os
import sys
import traceback

import numpy as np
import SimpleITK as sitk

from PyQt5.QtCore import Qt, QThread, QObject, pyqtSignal, pyqtSlot
from PyQt5.QtGui import QImage, QPixmap, QGuiApplication
from PyQt5.QtWidgets import (
    QApplication,
    QMainWindow,
    QWidget,
    QFileDialog,
    QMessageBox,
    QLabel,
    QPushButton,
    QComboBox,
    QSlider,
    QVBoxLayout,
    QHBoxLayout,
    QGridLayout,
    QGroupBox,
    QSpinBox,
    QDoubleSpinBox,
    QFrame,
    QStatusBar,
    QScrollArea,
    QSizePolicy,
)

try:
    import vtk
    from vtkmodules.qt.QVTKRenderWindowInteractor import QVTKRenderWindowInteractor
    from vtkmodules.util.numpy_support import numpy_to_vtk
    VTK_AVAILABLE = True
    VTK_IMPORT_ERROR = ""
except Exception as vtk_error:  # pragma: no cover - GUI fallback
    vtk = None
    QVTKRenderWindowInteractor = None
    numpy_to_vtk = None
    VTK_AVAILABLE = False
    VTK_IMPORT_ERROR = str(vtk_error)


sitk.ProcessObject_SetGlobalWarningDisplay(False)

METAL_MASK_FILTER_NAME = "金属伪影掩码 Metal Artifact Mask"


# =========================
# DICOM 读取
# =========================

def read_dicom_series(dcm_folder):
    """
    读取 DICOM 文件夹。
    如果文件夹里有多个 Series，默认选择切片数量最多的那个。
    """
    reader = sitk.ImageSeriesReader()
    series_ids = reader.GetGDCMSeriesIDs(dcm_folder)

    if not series_ids:
        raise RuntimeError("当前文件夹中没有找到有效的 DICOM 序列。请确认选择的是 DICOM 文件夹。")

    best_series_id = None
    best_file_names = []

    for sid in series_ids:
        file_names = reader.GetGDCMSeriesFileNames(dcm_folder, sid)
        if len(file_names) > len(best_file_names):
            best_file_names = file_names
            best_series_id = sid

    reader.SetFileNames(best_file_names)
    image = reader.Execute()

    return image, best_series_id, len(best_file_names)


# =========================
# 图像显示工具 关键一：将亨氏单位映射成0-255
# =========================

def window_to_uint8(slice_array, window_center, window_width):
    """
    将 CT 切片按照窗宽窗位转换成 0~255 的 uint8 图像。
    """
    if window_width <= 1:
        window_width = 1

    lower = window_center - window_width / 2.0
    upper = window_center + window_width / 2.0

    arr = slice_array.astype(np.float32)
    arr = np.nan_to_num(arr)
    arr = (arr - lower) / (upper - lower) * 255.0
    arr = np.clip(arr, 0, 255)

    return arr.astype(np.uint8)


def mask_overlay_to_rgb(slice_array, mask_array, window_center, window_width):
    """
    将金属掩码用红色叠加到 CT 灰度切片上。
    """
    base = window_to_uint8(slice_array, window_center, window_width)
    rgb = np.stack([base, base, base], axis=-1).astype(np.float32)

    mask = mask_array > 0
    overlay_color = np.array([255, 80, 40], dtype=np.float32)
    alpha = 0.65
    rgb[mask] = rgb[mask] * (1.0 - alpha) + overlay_color * alpha

    return np.clip(rgb, 0, 255).astype(np.uint8)


class ImageViewer(QLabel):
    """
    用 QLabel 显示灰度或 RGB 图像。
    """
    def __init__(self, title="图像"):
        super().__init__()
        self.title = title
        self.image_array = None

        self.setText(title)
        self.setAlignment(Qt.AlignCenter)
        self.setMinimumSize(140, 140)
        self.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
        self.setFrameShape(QFrame.Box)
        self.setStyleSheet("""
            QLabel {
                background-color: white;
                color: #333333;
                border: 1px solid #666666;
            }
        """)

    def set_image_array(self, image_array):
        self.image_array = image_array
        self.refresh()

    def refresh(self):
        if self.image_array is None:
            self.setText(self.title)
            return

        arr = np.ascontiguousarray(self.image_array)
        h, w = arr.shape[:2]

        if arr.ndim == 2:
            bytes_per_line = w
            image_format = QImage.Format_Grayscale8
        elif arr.ndim == 3 and arr.shape[2] == 3:
            bytes_per_line = 3 * w
            image_format = QImage.Format_RGB888
        else:
            self.setText("不支持的图像格式")
            return

        qimg = QImage(
            arr.data,
            w,
            h,
            bytes_per_line,
            image_format
        ).copy()

        pixmap = QPixmap.fromImage(qimg)
        pixmap = pixmap.scaled(
            self.size(),
            Qt.KeepAspectRatio,
            Qt.SmoothTransformation
        )
        self.setPixmap(pixmap)

    def resizeEvent(self, event):
        super().resizeEvent(event)
        self.refresh()


class SliderRow(QWidget):
    """
    左侧参数滑条组件。
    """
    valueChanged = pyqtSignal(float)

    def __init__(self, name, minimum, maximum, default, scale=100, suffix=""):
        super().__init__()
        self.scale = scale
        self.suffix = suffix

        self.name_label = QLabel(name)
        self.value_label = QLabel()

        self.slider = QSlider(Qt.Horizontal)
        self.slider.setMinimum(int(minimum * scale))
        self.slider.setMaximum(int(maximum * scale))
        self.slider.setValue(int(default * scale))

        layout = QGridLayout()
        layout.setContentsMargins(0, 2, 0, 2)
        layout.setHorizontalSpacing(6)
        layout.setVerticalSpacing(2)
        layout.addWidget(self.name_label, 0, 0)
        layout.addWidget(self.value_label, 0, 1)
        layout.addWidget(self.slider, 1, 0, 1, 2)
        self.setLayout(layout)

        self.slider.valueChanged.connect(self.on_value_changed)
        self.on_value_changed(self.slider.value())

    def on_value_changed(self, value):
        v = value / self.scale
        if self.scale == 1:
            text = f"{int(v)}{self.suffix}"
        else:
            text = f"{v:.2f}{self.suffix}"

        self.value_label.setText(text)
        self.valueChanged.emit(v)

    def value(self):
        return self.slider.value() / self.scale

    def set_value(self, value):
        self.slider.setValue(int(value * self.scale))


class VTKVolumeViewer(QWidget):
    """
    在 Qt 窗口中嵌入 VTK 三维体渲染。
    """
    def __init__(self):
        super().__init__()
        self._has_vtk = VTK_AVAILABLE
        self._is_mask = False
        self._data_min = 0.0
        self._data_max = 1.0

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)

        if not self._has_vtk:
            tip = QLabel(
                "当前环境未检测到 VTK / QVTK 支持。\n"
                f"导入错误：{VTK_IMPORT_ERROR}"
            )
            tip.setAlignment(Qt.AlignCenter)
            tip.setStyleSheet(
                "QLabel { background: #202020; color: #f0f0f0; border: 1px solid #444; }"
            )
            layout.addWidget(tip)
            self.vtk_widget = None
            self.renderer = None
            self.volume_mapper = None
            self.volume_property = None
            self.volume = None
            return

        self.vtk_widget = QVTKRenderWindowInteractor(self)
        layout.addWidget(self.vtk_widget)

        self.renderer = vtk.vtkRenderer()
        self.renderer.SetBackground(0.1, 0.1, 0.1)

        ren_win = self.vtk_widget.GetRenderWindow()
        ren_win.AddRenderer(self.renderer)

        self.volume_mapper = vtk.vtkSmartVolumeMapper()
        self.volume_property = vtk.vtkVolumeProperty()
        self.volume_property.SetInterpolationTypeToLinear()
        self.volume_property.ShadeOn()

        self.volume = vtk.vtkVolume()
        self.volume.SetMapper(self.volume_mapper)
        self.volume.SetProperty(self.volume_property)
        self.renderer.AddVolume(self.volume)

        self.vtk_widget.GetRenderWindow().GetInteractor().Initialize()

    def set_volume(self, volume_array_zyx, spacing_xyz, is_mask, window_center, window_width):
        if not self._has_vtk:
            return

        arr = np.ascontiguousarray(volume_array_zyx, dtype=np.float32)
        if arr.ndim != 3:
            return

        z, y, x = arr.shape
        vtk_image = vtk.vtkImageData()
        vtk_image.SetDimensions(x, y, z)
        vtk_image.SetSpacing(float(spacing_xyz[0]), float(spacing_xyz[1]), float(spacing_xyz[2]))
        vtk_image.SetOrigin(0.0, 0.0, 0.0)

        vtk_scalars = numpy_to_vtk(
            num_array=arr.ravel(order="C"),
            deep=True,
            array_type=vtk.VTK_FLOAT
        )
        vtk_scalars.SetName("CTScalars")
        vtk_image.GetPointData().SetScalars(vtk_scalars)

        self.volume_mapper.SetInputData(vtk_image)
        self._data_min = float(np.min(arr))
        self._data_max = float(np.max(arr))
        self._is_mask = bool(is_mask)

        self.update_transfer(window_center, window_width)
        self.renderer.ResetCamera()
        self.vtk_widget.GetRenderWindow().Render()

    def update_transfer(self, window_center, window_width):
        if not self._has_vtk:
            return

        color = vtk.vtkColorTransferFunction()
        opacity = vtk.vtkPiecewiseFunction()

        if self._is_mask:
            color.AddRGBPoint(0.0, 0.0, 0.0, 0.0)
            color.AddRGBPoint(255.0, 1.0, 0.35, 0.15)
            opacity.AddPoint(0.0, 0.0)
            opacity.AddPoint(254.0, 0.0)
            opacity.AddPoint(255.0, 0.85)
        else:
            ww = max(float(window_width), 1.0)
            wc = float(window_center)
            lower = wc - ww / 2.0
            upper = wc + ww / 2.0
            data_min = self._data_min
            data_max = self._data_max

            color.AddRGBPoint(data_min, 0.0, 0.0, 0.0)
            color.AddRGBPoint(lower, 0.0, 0.0, 0.0)
            color.AddRGBPoint(wc, 0.85, 0.85, 0.85)
            color.AddRGBPoint(upper, 1.0, 1.0, 1.0)
            color.AddRGBPoint(data_max, 1.0, 1.0, 1.0)

            opacity.AddPoint(data_min, 0.0)
            opacity.AddPoint(lower, 0.0)
            opacity.AddPoint(wc, 0.08)
            opacity.AddPoint(upper, 0.32)
            opacity.AddPoint(data_max, 0.52)

        self.volume_property.SetColor(color)
        self.volume_property.SetScalarOpacity(opacity)
        self.vtk_widget.GetRenderWindow().Render()


# =========================
# 滤波线程
# =========================

class FilterWorker(QObject):
    finished = pyqtSignal(object, str)
    failed = pyqtSignal(str)

    def __init__(self, image, filter_name, params):
        super().__init__()
        self.image = image
        self.filter_name = filter_name
        self.params = params

    @pyqtSlot()
    def run(self):
        try:
            image_float = sitk.Cast(self.image, sitk.sitkFloat32)
            name = self.filter_name

            # 关键三：滤波处理部分

            if name == "无滤波":
                result = image_float

            elif name == "高斯滤波 Gaussian":
                sigma = float(self.params["spatial_sigma"])
                result = sitk.SmoothingRecursiveGaussian(image_float, sigma)

            elif name == "双边滤波 Bilateral":
                domain_sigma = float(self.params["spatial_sigma"])
                range_sigma = float(self.params["range_sigma"])

                bilateral = sitk.BilateralImageFilter()
                bilateral.SetDomainSigma(domain_sigma)
                bilateral.SetRangeSigma(range_sigma)
                result = bilateral.Execute(image_float)

            elif name == "中值滤波 Median":
                radius = int(self.params["median_radius"])

                median = sitk.MedianImageFilter()
                median.SetRadius([radius, radius, radius])
                result = median.Execute(image_float)

            elif name == "曲率流平滑 Curvature Flow":
                iterations = int(self.params["iterations"])
                time_step = float(self.params["time_step"])

                result = sitk.CurvatureFlow(
                    image_float,
                    timeStep=time_step,
                    numberOfIterations=iterations
                )

            elif name == "各向异性扩散 Anisotropic Diffusion":
                iterations = int(self.params["iterations"])
                time_step = float(self.params["time_step"])
                conductance = float(self.params["conductance"])

                result = sitk.CurvatureAnisotropicDiffusion(
                    image_float,
                    timeStep=time_step,
                    conductanceParameter=conductance,
                    numberOfIterations=iterations
                )

            elif name == METAL_MASK_FILTER_NAME:
                threshold_lower = float(self.params["metal_threshold_lower"])
                threshold_upper = float(self.params["metal_threshold_upper"])
                gradient_threshold = float(self.params["metal_gradient_threshold"])
                opening_radius = int(self.params["metal_opening_radius"])
                closing_radius = int(self.params["metal_closing_radius"])
                min_component_size = int(self.params["metal_min_component_size"])

                hu_mask = sitk.BinaryThreshold(
                    image_float,
                    lowerThreshold=threshold_lower,
                    upperThreshold=threshold_upper,
                    insideValue=1,
                    outsideValue=0
                )
                mask = sitk.Cast(hu_mask, sitk.sitkUInt8)

                gradient = sitk.GradientMagnitude(image_float)
                gradient_mask = sitk.BinaryThreshold(
                    gradient,
                    lowerThreshold=gradient_threshold,
                    upperThreshold=1.0e12,
                    insideValue=1,
                    outsideValue=0
                )
                gradient_mask = sitk.Cast(gradient_mask, sitk.sitkUInt8)

                if opening_radius > 0:
                    opening = sitk.BinaryMorphologicalOpeningImageFilter()
                    opening.SetKernelRadius([opening_radius] * mask.GetDimension())
                    opening.SetForegroundValue(1)
                    mask = opening.Execute(mask)

                if closing_radius > 0:
                    closing = sitk.BinaryMorphologicalClosingImageFilter()
                    closing.SetKernelRadius([closing_radius] * mask.GetDimension())
                    closing.SetForegroundValue(1)
                    mask = closing.Execute(mask)

                # 用梯度结果筛选连通域，但保留通过筛选的完整金属区域。
                connected = sitk.ConnectedComponent(mask)
                if min_component_size > 0:
                    relabeled = sitk.RelabelComponent(
                        connected,
                        minimumObjectSize=min_component_size
                    )
                else:
                    relabeled = connected

                edge_labels = sitk.Mask(relabeled, gradient_mask)
                label_stats = sitk.LabelShapeStatisticsImageFilter()
                label_stats.Execute(edge_labels)

                filtered_mask = sitk.Image(mask.GetSize(), sitk.sitkUInt8)
                filtered_mask.CopyInformation(mask)
                for label in label_stats.GetLabels():
                    component_mask = sitk.BinaryThreshold(
                        relabeled,
                        lowerThreshold=label,
                        upperThreshold=label,
                        insideValue=1,
                        outsideValue=0
                    )
                    filtered_mask = sitk.Or(
                        filtered_mask,
                        sitk.Cast(component_mask, sitk.sitkUInt8)
                    )

                mask = filtered_mask

                result = sitk.Cast(mask * 255, sitk.sitkUInt8)

            else:
                raise RuntimeError(f"未知滤波器：{name}")

            message = f"滤波完成：{name}"
            self.finished.emit(result, message)

        except Exception:
            self.failed.emit(traceback.format_exc())


# =========================
# 主窗口
# =========================

class CTFilterWindow(QMainWindow):
    def __init__(self):
        super().__init__()

        self.setWindowTitle("CT 影像 SimpleITK 滤波器可视化工具")
        self.resize(960, 600)
        self.setMinimumSize(640, 440)

        self.original_image = None
        self.filtered_image = None

        self.original_array = None
        self.filtered_array = None
        self.filtered_is_mask = False

        self.current_folder = None
        self.current_slice = 0
        self.running_filter_name = None

        self.worker_thread = None
        self.worker = None

        self.init_ui()
        self._fit_window_to_screen()

    def init_ui(self):
        main_widget = QWidget()
        main_layout = QHBoxLayout(main_widget)

        # 左侧控制区：放入滚动条，矮屏也能用；宽度可随窗口变化
        control_panel = self.create_control_panel()
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.NoFrame)
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarAlwaysOff)
        scroll.setVerticalScrollBarPolicy(Qt.ScrollBarAsNeeded)
        scroll.setMinimumWidth(260)
        scroll.setMaximumWidth(420)
        scroll.setWidget(control_panel)

        # 右侧图像显示区
        image_panel = self.create_image_panel()

        main_layout.addWidget(scroll)
        main_layout.addWidget(image_panel, stretch=1)

        self.setCentralWidget(main_widget)

        self.status_bar = QStatusBar()
        self.setStatusBar(self.status_bar)
        self.status_bar.showMessage("请先打开 DICOM 文件夹。")

        self.update_param_state()

    def _fit_window_to_screen(self):
        """
        按当前显示器「可用区域」限制初始大小并居中，避免笔记本打开后超出屏幕。
        仍可通过系统边缘调整窗口大小。
        """
        screen = QGuiApplication.primaryScreen()
        if screen is None:
            return
        ag = screen.availableGeometry()
        mw, mh = self.minimumWidth(), self.minimumHeight()
        # 不要超出显示器可用区域（留边距）；小屏可把当前尺寸压下去以便完整显示并允许继续手动拉大
        max_w = int(ag.width() * 0.96)
        max_h = int(ag.height() * 0.94)
        if mw > max_w or mh > max_h:
            self.setMinimumSize(min(mw, max_w), min(mh, max_h))
            mw, mh = self.minimumWidth(), self.minimumHeight()
        tw = max(mw, min(self.width(), max_w))
        th = max(mh, min(self.height(), max_h))
        self.resize(tw, th)
        fg = self.frameGeometry()
        fg.moveCenter(ag.center())
        self.move(fg.topLeft())

    def create_control_panel(self):
        panel = QWidget()
        layout = QVBoxLayout(panel)

        # 打开按钮
        self.btn_open = QPushButton("打开 DICOM 文件夹")
        self.btn_open.clicked.connect(self.open_dicom_folder)
        layout.addWidget(self.btn_open)

        # 基本信息
        info_group = QGroupBox("图像信息")
        info_layout = QVBoxLayout(info_group)

        self.info_label = QLabel("尚未加载图像")
        self.info_label.setWordWrap(True)

        info_layout.addWidget(self.info_label)
        layout.addWidget(info_group)

        # 切片控制
        slice_group = QGroupBox("切片选择")
        slice_layout = QVBoxLayout(slice_group)

        self.slice_label = QLabel("切片：- / -")

        self.slice_slider = QSlider(Qt.Horizontal)
        self.slice_slider.setEnabled(False)
        self.slice_slider.valueChanged.connect(self.on_slice_changed)

        slice_layout.addWidget(self.slice_label)
        slice_layout.addWidget(self.slice_slider)
        layout.addWidget(slice_group)

        # 窗宽窗位
        window_group = QGroupBox("窗宽 / 窗位")
        window_layout = QGridLayout(window_group)

        self.window_center_spin = QSpinBox()
        self.window_center_spin.setRange(-3000, 3000)
        self.window_center_spin.setValue(50)
        self.window_center_spin.valueChanged.connect(self.on_window_changed)

        self.window_width_spin = QSpinBox()
        self.window_width_spin.setRange(1, 6000)
        self.window_width_spin.setValue(350)
        self.window_width_spin.valueChanged.connect(self.on_window_changed)

        window_layout.addWidget(QLabel("窗位 Center"), 0, 0)
        window_layout.addWidget(self.window_center_spin, 0, 1)
        window_layout.addWidget(QLabel("窗宽 Width"), 1, 0)
        window_layout.addWidget(self.window_width_spin, 1, 1)

        layout.addWidget(window_group)

        # 滤波器选择
        filter_group = QGroupBox("滤波器")
        filter_layout = QVBoxLayout(filter_group)

        self.filter_combo = QComboBox()
        self.filter_combo.addItems([
            "无滤波",
            "高斯滤波 Gaussian",
            "双边滤波 Bilateral",
            "中值滤波 Median",
            "曲率流平滑 Curvature Flow",
            "各向异性扩散 Anisotropic Diffusion",
            METAL_MASK_FILTER_NAME,
        ])
        self.filter_combo.currentTextChanged.connect(self.update_param_state)

        filter_layout.addWidget(QLabel("选择滤波器"))
        filter_layout.addWidget(self.filter_combo)

        self.spatial_sigma_slider = SliderRow(
            "空间 Sigma",
            minimum=0.1,
            maximum=10.0,
            default=1.0,
            scale=100
        )

        self.range_sigma_slider = SliderRow(
            "灰度 Sigma",
            minimum=1.0,
            maximum=300.0,
            default=50.0,
            scale=10
        )

        self.median_radius_slider = SliderRow(
            "中值半径",
            minimum=1,
            maximum=8,
            default=1,
            scale=1
        )

        self.iter_slider = SliderRow(
            "迭代次数",
            minimum=1,
            maximum=30,
            default=5,
            scale=1
        )

        self.time_step_spin = QDoubleSpinBox()
        self.time_step_spin.setRange(0.001, 0.25)
        self.time_step_spin.setDecimals(4)
        self.time_step_spin.setSingleStep(0.005)
        self.time_step_spin.setValue(0.0625)

        self.conductance_spin = QDoubleSpinBox()
        self.conductance_spin.setRange(0.1, 20.0)
        self.conductance_spin.setDecimals(2)
        self.conductance_spin.setSingleStep(0.5)
        self.conductance_spin.setValue(3.0)

        self.metal_threshold_lower_slider = SliderRow(
            "阈值下限 HU",
            minimum=-1000,
            maximum=5000,
            default=1000,
            scale=1
        )

        self.metal_threshold_upper_slider = SliderRow(
            "阈值上限 HU",
            minimum=-1000,
            maximum=5000,
            default=4000,
            scale=1
        )

        self.metal_gradient_threshold_slider = SliderRow(
            "梯度阈值",
            minimum=0,
            maximum=2000,
            default=100,
            scale=1
        )

        self.metal_opening_radius_slider = SliderRow(
            "开运算半径",
            minimum=0,
            maximum=5,
            default=1,
            scale=1
        )

        self.metal_closing_radius_slider = SliderRow(
            "闭运算半径",
            minimum=0,
            maximum=10,
            default=2,
            scale=1
        )

        self.metal_min_component_size_slider = SliderRow(
            "最小连通域体素数",
            minimum=0,
            maximum=10000,
            default=50,
            scale=1
        )

        filter_layout.addWidget(self.spatial_sigma_slider)
        filter_layout.addWidget(self.range_sigma_slider)
        filter_layout.addWidget(self.median_radius_slider)
        filter_layout.addWidget(self.iter_slider)

        self.time_step_label = QLabel("时间步长 Time Step")
        filter_layout.addWidget(self.time_step_label)
        filter_layout.addWidget(self.time_step_spin)

        self.conductance_label = QLabel("扩散 Conductance")
        filter_layout.addWidget(self.conductance_label)
        filter_layout.addWidget(self.conductance_spin)

        self.metal_param_group = QGroupBox("金属伪影掩码参数")
        metal_param_layout = QVBoxLayout(self.metal_param_group)
        metal_param_layout.setContentsMargins(6, 6, 6, 6)
        metal_param_layout.setSpacing(4)
        metal_param_layout.addWidget(self.metal_threshold_lower_slider)
        metal_param_layout.addWidget(self.metal_threshold_upper_slider)
        metal_param_layout.addWidget(self.metal_gradient_threshold_slider)
        metal_param_layout.addWidget(self.metal_opening_radius_slider)
        metal_param_layout.addWidget(self.metal_closing_radius_slider)
        metal_param_layout.addWidget(self.metal_min_component_size_slider)
        filter_layout.addWidget(self.metal_param_group)

        self.btn_apply = QPushButton("应用滤波")
        self.btn_apply.setEnabled(False)
        self.btn_apply.clicked.connect(self.apply_filter)

        filter_layout.addWidget(self.btn_apply)

        layout.addWidget(filter_group)
        self.update_param_state()

        # 保存
        save_group = QGroupBox("保存结果")
        save_layout = QVBoxLayout(save_group)

        self.btn_save_png = QPushButton("保存当前切片 PNG")
        self.btn_save_png.setEnabled(False)
        self.btn_save_png.clicked.connect(self.save_current_slice_png)

        self.btn_save_volume = QPushButton("保存滤波后体数据 NIfTI / NRRD")
        self.btn_save_volume.setEnabled(False)
        self.btn_save_volume.clicked.connect(self.save_filtered_volume)

        save_layout.addWidget(self.btn_save_png)
        save_layout.addWidget(self.btn_save_volume)

        layout.addWidget(save_group)

        layout.addStretch()
        return panel

    def create_image_panel(self):
        panel = QWidget()
        layout = QVBoxLayout(panel)
        layout.setContentsMargins(4, 4, 4, 4)

        top_row = QWidget()
        top_layout = QHBoxLayout(top_row)
        top_layout.setContentsMargins(0, 0, 0, 0)

        left_group = QGroupBox("原图")
        left_group.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
        left_layout = QVBoxLayout(left_group)
        self.original_viewer = ImageViewer("原图")
        left_layout.addWidget(self.original_viewer, stretch=1)

        right_group = QGroupBox("滤波结果")
        right_group.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
        right_layout = QVBoxLayout(right_group)
        self.filtered_viewer = ImageViewer("滤波结果")
        right_layout.addWidget(self.filtered_viewer, stretch=1)

        top_layout.addWidget(left_group, stretch=1)
        top_layout.addWidget(right_group, stretch=1)

        volume_group = QGroupBox("三维 VTK 体渲染")
        volume_layout = QVBoxLayout(volume_group)
        self.vtk_viewer = VTKVolumeViewer()
        self.vtk_viewer.setMinimumHeight(240)
        volume_layout.addWidget(self.vtk_viewer, stretch=1)

        layout.addWidget(top_row, stretch=3)
        layout.addWidget(volume_group, stretch=2)

        return panel

    def update_param_state(self):
        name = self.filter_combo.currentText()

        use_spatial = name in [
            "高斯滤波 Gaussian",
            "双边滤波 Bilateral",
        ]

        use_range = name in [
            "双边滤波 Bilateral",
        ]

        use_median = name in [
            "中值滤波 Median",
        ]

        use_iter = name in [
            "曲率流平滑 Curvature Flow",
            "各向异性扩散 Anisotropic Diffusion",
        ]

        use_time_step = use_iter

        use_conductance = name in [
            "各向异性扩散 Anisotropic Diffusion",
        ]

        use_metal_mask = name == METAL_MASK_FILTER_NAME

        self._set_param_widget_active(self.spatial_sigma_slider, use_spatial)
        self._set_param_widget_active(self.range_sigma_slider, use_range)
        self._set_param_widget_active(self.median_radius_slider, use_median)
        self._set_param_widget_active(self.iter_slider, use_iter)
        self._set_param_widget_active(self.time_step_label, use_time_step)
        self._set_param_widget_active(self.time_step_spin, use_time_step)
        self._set_param_widget_active(self.conductance_label, use_conductance)
        self._set_param_widget_active(self.conductance_spin, use_conductance)
        self._set_param_widget_active(self.metal_param_group, use_metal_mask)

    def _set_param_widget_active(self, widget, active):
        widget.setEnabled(active)
        widget.setVisible(active)

    def open_dicom_folder(self):
        folder = QFileDialog.getExistingDirectory(
            self,
            "选择 DICOM 文件夹",
            os.getcwd()
        )

        if not folder:
            return

        try:
            self.status_bar.showMessage("正在读取 DICOM，请稍等...")
            QApplication.processEvents()

            image, series_id, file_count = read_dicom_series(folder)

            self.original_image = image
            self.filtered_image = None

            self.original_array = sitk.GetArrayFromImage(image)
            self.filtered_array = None
            self.filtered_is_mask = False

            self.current_folder = folder

            z, y, x = self.original_array.shape
            spacing = image.GetSpacing()
            size = image.GetSize()

            self.slice_slider.setEnabled(True)
            self.slice_slider.setMinimum(0)
            self.slice_slider.setMaximum(z - 1)
            self.slice_slider.setValue(z // 2)

            self.btn_apply.setEnabled(True)
            self.btn_save_png.setEnabled(True)
            self.btn_save_volume.setEnabled(False)

            self.info_label.setText(
                f"路径：{folder}\n"
                f"Series ID：{series_id}\n"
                f"DICOM 文件数：{file_count}\n"
                f"Size(x,y,z)：{size}\n"
                f"Array(z,y,x)：{self.original_array.shape}\n"
                f"Spacing：({spacing[0]:.3f}, {spacing[1]:.3f}, {spacing[2]:.3f})"
            )

            self.status_bar.showMessage("DICOM 加载完成。")
            self.update_display()
            self.update_3d_view()

        except Exception as e:
            QMessageBox.critical(self, "读取失败", str(e))
            self.status_bar.showMessage("读取 DICOM 失败。")

# 关键二：切换ct层面
    def on_slice_changed(self, value):
        self.current_slice = value
        self.update_display()

    def on_window_changed(self):
        self.update_display()
        self.update_3d_transfer()

    def update_display(self):
        if self.original_array is None:
            return

        z_count = self.original_array.shape[0]
        self.current_slice = self.slice_slider.value()

        self.slice_label.setText(
            f"切片：{self.current_slice + 1} / {z_count}"
        )

        center = self.window_center_spin.value()
        width = self.window_width_spin.value()

        original_slice = self.original_array[self.current_slice, :, :]
        original_uint8 = window_to_uint8(original_slice, center, width)
        self.original_viewer.set_image_array(original_uint8)

        if self.filtered_array is not None:
            filtered_slice = self.filtered_array[self.current_slice, :, :]
            if self.filtered_is_mask:
                filtered_uint8 = mask_overlay_to_rgb(
                    original_slice,
                    filtered_slice,
                    center,
                    width
                )
            else:
                filtered_uint8 = window_to_uint8(filtered_slice, center, width)
            self.filtered_viewer.set_image_array(filtered_uint8)
        else:
            self.filtered_viewer.set_image_array(original_uint8)

    # 关键三：收集滤波器参数
    def collect_filter_params(self):
        return {
            "spatial_sigma": self.spatial_sigma_slider.value(),
            "range_sigma": self.range_sigma_slider.value(),
            "median_radius": int(self.median_radius_slider.value()),
            "iterations": int(self.iter_slider.value()),
            "time_step": float(self.time_step_spin.value()),
            "conductance": float(self.conductance_spin.value()),
            "metal_threshold_lower": int(self.metal_threshold_lower_slider.value()),
            "metal_threshold_upper": int(self.metal_threshold_upper_slider.value()),
            "metal_gradient_threshold": int(self.metal_gradient_threshold_slider.value()),
            "metal_opening_radius": int(self.metal_opening_radius_slider.value()),
            "metal_closing_radius": int(self.metal_closing_radius_slider.value()),
            "metal_min_component_size": int(self.metal_min_component_size_slider.value()),
        }

    def apply_filter(self):
        if self.original_image is None:
            QMessageBox.warning(self, "提示", "请先打开 DICOM 文件夹。")
            return

        filter_name = self.filter_combo.currentText()
        params = self.collect_filter_params()
        self.running_filter_name = filter_name

        self.btn_apply.setEnabled(False)
        self.status_bar.showMessage(f"正在执行滤波：{filter_name} ...")

        # 关键四：用线程将滤波操作放到子线程中避免主线程卡死
        self.worker_thread = QThread()
        self.worker = FilterWorker(
            self.original_image,
            filter_name,
            params
        )

        self.worker.moveToThread(self.worker_thread)

        self.worker_thread.started.connect(self.worker.run)
        self.worker.finished.connect(self.on_filter_finished)
        self.worker.failed.connect(self.on_filter_failed)

        self.worker.finished.connect(self.worker_thread.quit)
        self.worker.failed.connect(self.worker_thread.quit)

        self.worker.finished.connect(self.worker.deleteLater)
        self.worker.failed.connect(self.worker.deleteLater)
        self.worker_thread.finished.connect(self.worker_thread.deleteLater)

        self.worker_thread.start()

    def on_filter_finished(self, result_image, message):
        self.filtered_image = result_image
        self.filtered_array = sitk.GetArrayFromImage(result_image)
        self.filtered_is_mask = self.running_filter_name == METAL_MASK_FILTER_NAME
        self.running_filter_name = None

        self.btn_apply.setEnabled(True)
        self.btn_save_volume.setEnabled(True)

        self.update_display()
        self.update_3d_view()
        self.status_bar.showMessage(message)

    def on_filter_failed(self, error_message):
        self.running_filter_name = None
        self.btn_apply.setEnabled(True)
        self.status_bar.showMessage("滤波失败。")

        QMessageBox.critical(
            self,
            "滤波失败",
            error_message
        )

    def save_current_slice_png(self):
        if self.original_array is None:
            return

        save_path, _ = QFileDialog.getSaveFileName(
            self,
            "保存当前切片 PNG",
            os.path.join(os.getcwd(), f"slice_{self.current_slice + 1}.png"),
            "PNG Image (*.png)"
        )

        if not save_path:
            return

        try:
            center = self.window_center_spin.value()
            width = self.window_width_spin.value()

            if self.filtered_array is not None:
                arr = self.filtered_array[self.current_slice, :, :]
            else:
                arr = self.original_array[self.current_slice, :, :]

            if self.filtered_array is not None and self.filtered_is_mask:
                original_slice = self.original_array[self.current_slice, :, :]
                uint8_arr = mask_overlay_to_rgb(
                    original_slice,
                    arr,
                    center,
                    width
                )
            else:
                uint8_arr = window_to_uint8(arr, center, width)

            if uint8_arr.ndim == 3:
                out_img = sitk.GetImageFromArray(uint8_arr, isVector=True)
            else:
                out_img = sitk.GetImageFromArray(uint8_arr)
                out_img = sitk.Cast(out_img, sitk.sitkUInt8)
            sitk.WriteImage(out_img, save_path)

            self.status_bar.showMessage(f"PNG 保存成功：{save_path}")

        except Exception as e:
            QMessageBox.critical(self, "保存失败", str(e))

    def update_3d_view(self):
        if self.original_array is None:
            return

        if self.filtered_array is not None:
            target_array = self.filtered_array
            is_mask = self.filtered_is_mask
        else:
            target_array = self.original_array
            is_mask = False

        spacing_xyz = self.original_image.GetSpacing()
        self.vtk_viewer.set_volume(
            target_array,
            spacing_xyz,
            is_mask=is_mask,
            window_center=self.window_center_spin.value(),
            window_width=self.window_width_spin.value()
        )

    def update_3d_transfer(self):
        self.vtk_viewer.update_transfer(
            self.window_center_spin.value(),
            self.window_width_spin.value()
        )

    def save_filtered_volume(self):
        if self.filtered_image is None:
            QMessageBox.warning(self, "提示", "请先执行滤波。")
            return

        save_path, _ = QFileDialog.getSaveFileName(
            self,
            "保存滤波后的三维体数据",
            os.path.join(
                os.getcwd(),
                "metal_artifact_mask.nii.gz" if self.filtered_is_mask else "filtered_ct.nii.gz"
            ),
            "NIfTI (*.nii.gz *.nii);;NRRD (*.nrrd)"
        )

        if not save_path:
            return

        try:
            sitk.WriteImage(self.filtered_image, save_path)
            self.status_bar.showMessage(f"体数据保存成功：{save_path}")

        except Exception as e:
            QMessageBox.critical(self, "保存失败", str(e))


def main():
    app = QApplication(sys.argv)
    window = CTFilterWindow()
    window.show()
    sys.exit(app.exec_())


if __name__ == "__main__":
    main()