"""
游戏截图工具 - 专为全屏游戏设计
使用 Windows 底层 API 支持全屏游戏截图

快捷键：
  F2  → 全屏截图
  F3  → 显示窗口
  F9  → 开始
  F10 → 暂停/继续
  Esc → 最小化
"""

import os
import sys
import json
import ctypes
import ctypes.wintypes
import random
import string
import threading
import time
from datetime import datetime

import tkinter as tk
from tkinter import scrolledtext, filedialog, messagebox


# ==================== 单实例限制 ====================

def check_single_instance():
    """检查是否已有实例在运行"""
    mutex_name = "GameScreenshotTool_Mutex_2024"
    kernel32 = ctypes.windll.kernel32
    mutex = kernel32.CreateMutexW(None, False, mutex_name)
    last_error = kernel32.GetLastError()
    ERROR_ALREADY_EXISTS = 183
    if last_error == ERROR_ALREADY_EXISTS:
        kernel32.CloseHandle(mutex)
        return False
    return True


# ==================== 配置 ====================

if getattr(sys, 'frozen', False):
    BASE_DIR = os.path.dirname(sys.executable)
else:
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))

CONFIG_FILE = os.path.join(BASE_DIR, "game_config.json")
DEFAULT_DESKTOP = os.path.join(os.path.expanduser("~"), "Desktop")


def load_config():
    """加载配置文件"""
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {}


def save_config(data):
    """保存配置文件"""
    try:
        with open(CONFIG_FILE, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    except Exception:
        pass


# ==================== 截图核心（使用 Windows API）====================

def get_scale_factor():
    """获取 Windows DPI 缩放比例"""
    try:
        ctypes.windll.shcore.SetProcessDpiAwareness(2)
    except Exception:
        pass
    hdc = ctypes.windll.user32.GetDC(0)
    dpi = ctypes.windll.gdi32.GetDeviceCaps(hdc, 88)
    ctypes.windll.user32.ReleaseDC(0, hdc)
    return dpi / 96.0


def random_suffix(length=4):
    return ''.join(random.choices(string.ascii_lowercase + string.digits, k=length))


QUALITY_OPTIONS = [
    {"label": "PNG（无损）",       "ext": "png", "quality": None},
    {"label": "JPG 高质量（95%）", "ext": "jpg", "quality": 95},
    {"label": "JPG 标准（80%）",   "ext": "jpg", "quality": 80},
    {"label": "JPG 压缩（60%）",   "ext": "jpg", "quality": 60},
]


def capture_screen():
    """使用 Windows API 截取全屏"""
    from PIL import Image

    # 获取屏幕尺寸
    user32 = ctypes.windll.user32
    gdi32 = ctypes.windll.gdi32

    # 设置 DPI 感知
    try:
        ctypes.windll.shcore.SetProcessDpiAwareness(2)
    except Exception:
        pass

    # 获取实际屏幕尺寸
    width = user32.GetSystemMetrics(0)  # SM_CXSCREEN
    height = user32.GetSystemMetrics(1)  # SM_CYSCREEN

    # 创建设备上下文
    hdc_screen = user32.GetDC(0)
    hdc_mem = gdi32.CreateCompatibleDC(hdc_screen)
    hbitmap = gdi32.CreateCompatibleBitmap(hdc_screen, width, height)
    gdi32.SelectObject(hdc_mem, hbitmap)

    # 使用 PrintWindow 或 BitBlt 截图
    # BitBlt 可以捕获全屏游戏
    SRCCOPY = 0x00CC0020
    gdi32.BitBlt(hdc_mem, 0, 0, width, height, hdc_screen, 0, 0, SRCCOPY)

    # 转换为 PIL Image
    class BITMAPINFOHEADER(ctypes.Structure):
        _fields_ = [
            ('biSize', ctypes.c_uint32),
            ('biWidth', ctypes.c_int32),
            ('biHeight', ctypes.c_int32),
            ('biPlanes', ctypes.c_uint16),
            ('biBitCount', ctypes.c_uint16),
            ('biCompression', ctypes.c_uint32),
            ('biSizeImage', ctypes.c_uint32),
            ('biXPelsPerMeter', ctypes.c_int32),
            ('biYPelsPerMeter', ctypes.c_int32),
            ('biClrUsed', ctypes.c_uint32),
            ('biClrImportant', ctypes.c_uint32),
        ]

    bmi = BITMAPINFOHEADER()
    bmi.biSize = ctypes.sizeof(BITMAPINFOHEADER)
    bmi.biWidth = width
    bmi.biHeight = -height  # 负值表示从上到下
    bmi.biPlanes = 1
    bmi.biBitCount = 32
    bmi.biCompression = 0  # BI_RGB

    # 创建缓冲区
    buffer_size = width * height * 4
    buffer = ctypes.create_string_buffer(buffer_size)

    # 获取位图数据
    gdi32.GetDIBits(hdc_mem, hbitmap, 0, height, buffer, ctypes.byref(bmi), 0)

    # 清理资源
    gdi32.DeleteObject(hbitmap)
    gdi32.DeleteDC(hdc_mem)
    user32.ReleaseDC(0, hdc_screen)

    # 转换为 PIL Image
    img = Image.frombuffer('RGBA', (width, height), buffer, 'raw', 'BGRA', 0, 1)
    img = img.convert('RGB')

    return img, width, height


class GameScreenshot:
    """游戏截图核心类"""

    def __init__(self, log_callback=None):
        self.log_callback = log_callback

    def _log(self, msg):
        """输出日志"""
        if self.log_callback:
            self.log_callback(msg)

    def take_screenshot(self, save_dir, quality_index=0):
        """全屏截图并保存到指定目录"""
        opt = QUALITY_OPTIONS[quality_index]
        scale = get_scale_factor()
        scale_pct = f"{int(scale * 100)}pct"
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        rand = random_suffix()

        ext = opt["ext"]
        filename = f"game_{timestamp}_{rand}.{ext}"
        filepath = os.path.join(save_dir, filename)

        try:
            # 使用 Windows API 截图
            self._log("[截图] 正在使用 Windows API 抓取屏幕...")
            img, w, h = capture_screen()

            # 保存图片
            if ext == "jpg":
                img = img.convert("RGB")
                img.save(filepath, "JPEG", quality=opt["quality"])
            else:
                img.save(filepath, "PNG")

            file_size = os.path.getsize(filepath)
            size_str = f"{file_size / 1024:.0f} KB" if file_size < 1024 * 1024 else f"{file_size / 1024 / 1024:.1f} MB"

            msg = f"[截图完成] {filename}\n  保存路径: {filepath}\n  分辨率: {w}x{h}  缩放: {scale:.0%}  大小: {size_str}"
            self._log(msg)
            return filepath

        except Exception as e:
            error_msg = f"[截图失败] {str(e)}"
            self._log(error_msg)
            return None


# ==================== 全局热键 ====================

# Windows 消息常量
WM_HOTKEY = 0x0312
MOD_NOREPEAT = 0x4000

# 热键 ID
HOTKEY_F2 = 1
HOTKEY_F3 = 2
HOTKEY_F9 = 3
HOTKEY_F10 = 4
HOTKEY_ESC = 5


class HotkeyThread(threading.Thread):
    """全局热键监听线程"""

    def __init__(self, callbacks):
        super().__init__(daemon=True)
        self.callbacks = callbacks
        self.running = True

    def run(self):
        user32 = ctypes.windll.user32

        # 注册全局热键
        VK_F2 = 0x71
        VK_F3 = 0x72
        VK_F9 = 0x78
        VK_F10 = 0x79
        VK_ESCAPE = 0x1B

        user32.RegisterHotKey(None, HOTKEY_F2, MOD_NOREPEAT, VK_F2)
        user32.RegisterHotKey(None, HOTKEY_F3, MOD_NOREPEAT, VK_F3)
        user32.RegisterHotKey(None, HOTKEY_F9, MOD_NOREPEAT, VK_F9)
        user32.RegisterHotKey(None, HOTKEY_F10, MOD_NOREPEAT, VK_F10)
        user32.RegisterHotKey(None, HOTKEY_ESC, MOD_NOREPEAT, VK_ESCAPE)

        # 消息循环
        msg = ctypes.wintypes.MSG()
        while self.running and user32.GetMessageW(ctypes.byref(msg), None, 0, 0) != 0:
            if msg.message == WM_HOTKEY:
                hotkey_id = msg.wParam
                if hotkey_id in self.callbacks:
                    self.callbacks[hotkey_id]()

        # 注销热键
        user32.UnregisterHotKey(None, HOTKEY_F2)
        user32.UnregisterHotKey(None, HOTKEY_F3)
        user32.UnregisterHotKey(None, HOTKEY_F9)
        user32.UnregisterHotKey(None, HOTKEY_F10)
        user32.UnregisterHotKey(None, HOTKEY_ESC)

    def stop(self):
        self.running = False


# ==================== GUI ====================

class GameScreenshotApp:
    def __init__(self):
        self.running = False
        self.paused = False
        self.config = load_config()
        self.save_dir = self.config.get("save_dir", DEFAULT_DESKTOP)
        self.last_screenshot_time = 0.0

        self.root = tk.Tk()
        self.root.title("游戏截图工具")
        self.root.geometry("520x650")
        self.root.resizable(True, True)

        self._build_ui()

        # 初始化截图工具
        self.screenshot_tool = GameScreenshot(log_callback=self.log)

        # 启动全局热键
        self._start_hotkeys()

        self.root.protocol("WM_DELETE_WINDOW", self._on_close)

    def _build_ui(self):
        # ---- 快捷键提示 ----
        tip_frame = tk.LabelFrame(self.root, text="快捷键", padx=10, pady=6)
        tip_frame.pack(fill="x", padx=10, pady=(10, 5))

        tips = [
            ("F2", "截图"),
            ("F3", "显示窗口"),
            ("F9", "开始"),
            ("F10", "暂停/继续"),
            ("Esc", "最小化"),
        ]
        for key, desc in tips:
            row = tk.Frame(tip_frame)
            row.pack(fill="x", pady=1)
            tk.Label(row, text=key, width=6, anchor="w",
                     font=("Consolas", 10, "bold")).pack(side="left")
            tk.Label(row, text=desc, anchor="w").pack(side="left")

        # ---- 保存路径 ----
        path_frame = tk.LabelFrame(self.root, text="保存路径", padx=10, pady=6)
        path_frame.pack(fill="x", padx=10, pady=5)

        tk.Button(path_frame, text="选择路径", width=8,
                  command=self._browse_path).pack(side="right")

        self.path_var = tk.StringVar(value=self.save_dir)
        path_entry = tk.Entry(path_frame, textvariable=self.path_var)
        path_entry.pack(fill="x", padx=(0, 5))

        # ---- 图片质量 ----
        quality_frame = tk.LabelFrame(self.root, text="图片质量", padx=10, pady=6)
        quality_frame.pack(fill="x", padx=10, pady=5)

        saved_quality = self.config.get("quality", QUALITY_OPTIONS[0]["label"])
        self.quality_var = tk.StringVar(value=saved_quality)
        self.quality_var.trace_add("write", self._on_quality_change)
        quality_combo = tk.OptionMenu(quality_frame, self.quality_var,
                                      *[opt["label"] for opt in QUALITY_OPTIONS])
        quality_combo.pack(fill="x")

        # ---- 按钮区 ----
        btn_frame = tk.Frame(self.root)
        btn_frame.pack(fill="x", padx=10, pady=5)

        self.btn_screenshot = tk.Button(
            btn_frame, text="截图 (F2)", width=10,
            command=self._on_screenshot)
        self.btn_screenshot.pack(side="left", padx=(0, 5))

        self.btn_start = tk.Button(
            btn_frame, text="开始 (F9)", width=10,
            command=self._on_start)
        self.btn_start.pack(side="left", padx=5)

        self.btn_pause = tk.Button(
            btn_frame, text="暂停 (F10)", width=10,
            state="disabled", command=self._on_pause)
        self.btn_pause.pack(side="left", padx=5)

        # ---- 状态栏 ----
        self.status_var = tk.StringVar(value="就绪")
        status_label = tk.Label(
            self.root, textvariable=self.status_var,
            anchor="w", fg="#666", font=("Microsoft YaHei", 9))
        status_label.pack(fill="x", padx=12, pady=(0, 2))

        # ---- 日志区 ----
        log_frame = tk.LabelFrame(self.root, text="运行日志", padx=5, pady=5)
        log_frame.pack(fill="both", expand=True, padx=10, pady=(0, 10))

        self.log_text = scrolledtext.ScrolledText(
            log_frame, height=20, state="disabled",
            font=("Consolas", 10), wrap="word")
        self.log_text.pack(fill="both", expand=True)

    def _start_hotkeys(self):
        """启动全局热键监听"""
        callbacks = {
            HOTKEY_F2: lambda: self.root.after(0, self._on_screenshot),
            HOTKEY_F3: lambda: self.root.after(0, self._on_show),
            HOTKEY_F9: lambda: self.root.after(0, self._on_start),
            HOTKEY_F10: lambda: self.root.after(0, self._on_pause),
            HOTKEY_ESC: lambda: self.root.after(0, self._on_minimize),
        }
        self.hotkey_thread = HotkeyThread(callbacks)
        self.hotkey_thread.start()
        self.log("[初始化] 全局热键已注册")

    # ---- 路径选择 ----

    def _browse_path(self):
        path = filedialog.askdirectory(initialdir=self.save_dir, title="选择保存路径")
        if path:
            self.save_dir = path
            self.path_var.set(path)
            self.config["save_dir"] = path
            save_config(self.config)
            self.log(f"保存路径已更改: {path}")

    def _on_quality_change(self, *_):
        self.config["quality"] = self.quality_var.get()
        save_config(self.config)

    # ---- 日志（线程安全）----

    def log(self, msg):
        """线程安全的日志写入"""
        def _append():
            timestamp = datetime.now().strftime("%H:%M:%S")
            self.log_text.config(state="normal")
            self.log_text.insert("end", f"[{timestamp}] {msg}\n")
            self.log_text.see("end")
            self.log_text.config(state="disabled")
        self.root.after(0, _append)

    # ---- 按钮事件 ----

    def _on_screenshot(self):
        now = time.time()
        if now - self.last_screenshot_time < 0.3:
            return
        self.last_screenshot_time = now

        save_dir = self.path_var.get().strip()
        if not save_dir:
            save_dir = self.save_dir
        quality_label = self.quality_var.get()
        quality_index = next(
            (i for i, opt in enumerate(QUALITY_OPTIONS) if opt["label"] == quality_label), 0)
        self.log("正在截图...")
        threading.Thread(
            target=lambda: self.screenshot_tool.take_screenshot(save_dir, quality_index),
            daemon=True).start()

    def _on_start(self):
        self.running = True
        self.paused = False
        self.btn_start.config(state="disabled")
        self.btn_pause.config(state="normal", text="暂停 (F10)")
        self.status_var.set("运行中")
        self.log("已开始运行")

    def _on_pause(self):
        if not self.running:
            return
        self.paused = not self.paused
        if self.paused:
            self.btn_pause.config(text="继续 (F10)")
            self.status_var.set("已暂停")
            self.log("已暂停")
        else:
            self.btn_pause.config(text="暂停 (F10)")
            self.status_var.set("运行中")
            self.log("已继续")

    def _on_minimize(self):
        self.root.iconify()

    def _on_show(self):
        self.root.deiconify()
        self.root.lift()
        self.root.focus_force()

    def _on_close(self):
        if messagebox.askyesno("确认关闭", "确定要关闭游戏截图工具吗？"):
            self.running = False
            if hasattr(self, 'hotkey_thread'):
                self.hotkey_thread.stop()
            self.root.destroy()

    def run(self):
        self.log("游戏截图工具已启动")
        self.log("使用 Windows 底层 API，支持全屏游戏截图")
        w, h = 1920, 1080
        try:
            w = ctypes.windll.user32.GetSystemMetrics(0)
            h = ctypes.windll.user32.GetSystemMetrics(1)
        except Exception:
            pass
        scale = get_scale_factor()
        self.log(f"屏幕分辨率: {w}x{h}  缩放: {scale:.0%}")
        self.log(f"默认保存路径: {self.save_dir}")
        self.root.mainloop()


if __name__ == "__main__":
    if not check_single_instance():
        root = tk.Tk()
        root.withdraw()
        messagebox.showwarning("提示", "游戏截图工具已在运行中！\n请勿重复启动。")
        root.destroy()
        sys.exit(0)

    app = GameScreenshotApp()
    app.run()
