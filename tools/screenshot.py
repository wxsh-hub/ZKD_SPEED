"""
截图工具 - 带 GUI 界面
快捷键：
  F2  → 全屏截图
  F9  → 开始
  F10 → 暂停/继续
  Esc → 退出
"""

import os
import sys
import json
import ctypes
import random
import string
import threading
import time
from datetime import datetime

import pyautogui
import keyboard
import requests
import tkinter as tk
from tkinter import scrolledtext, filedialog, messagebox


# ==================== 配置 ====================

if getattr(sys, 'frozen', False):
    BASE_DIR = os.path.dirname(sys.executable)
else:
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))

CONFIG_FILE = os.path.join(BASE_DIR, "config.json")
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


# ==================== 截图核心 ====================

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


def take_screenshot(save_dir, quality_index=0, log_callback=None):
    """全屏截图并保存到指定目录"""
    opt = QUALITY_OPTIONS[quality_index]
    w, h = pyautogui.size()
    scale = get_scale_factor()
    scale_pct = f"{int(scale * 100)}pct"
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    rand = random_suffix()

    ext = opt["ext"]
    filename = f"{timestamp}_{w}x{h}_{scale_pct}_{rand}.{ext}"
    filepath = os.path.join(save_dir, filename)

    img = pyautogui.screenshot()
    if ext == "jpg":
        img = img.convert("RGB")
        img.save(filepath, "JPEG", quality=opt["quality"])
    else:
        img.save(filepath, "PNG")

    file_size = os.path.getsize(filepath)
    size_str = f"{file_size / 1024:.0f} KB" if file_size < 1024 * 1024 else f"{file_size / 1024 / 1024:.1f} MB"

    msg = f"[截图完成] {filename}\n  保存路径: {filepath}\n  分辨率: {w}x{h}  缩放: {scale:.0%}  大小: {size_str}"
    if log_callback:
        log_callback(msg)
    return filepath


def upload_screenshot(filepath, token, server_url="http://localhost:9090/api/ragent", log_callback=None):
    """通过 Token 上传截图到后端"""
    if not token:
        if log_callback:
            log_callback("[上传失败] 未配置上传Token")
        return False

    url = f"{server_url}/script/token/upload"
    try:
        with open(filepath, "rb") as f:
            files = {"file": (os.path.basename(filepath), f, "image/png")}
            data = {"token": token}
            resp = requests.post(url, files=files, data=data, timeout=30)

        if resp.status_code == 200:
            result = resp.json()
            if result.get("code") == "0":
                if log_callback:
                    log_callback(f"[上传成功] {os.path.basename(filepath)}")
                return True
            else:
                msg = result.get("message", "未知错误")
                if log_callback:
                    log_callback(f"[上传失败] {msg}")
                return False
        else:
            if log_callback:
                log_callback(f"[上传失败] HTTP {resp.status_code}")
            return False
    except requests.exceptions.ConnectionError:
        if log_callback:
            log_callback(f"[上传失败] 无法连接服务器 {server_url}")
        return False
    except Exception as e:
        if log_callback:
            log_callback(f"[上传失败] {e}")
        return False


# ==================== GUI ====================

class ScreenshotApp:
    def __init__(self):
        self.running = False
        self.paused = False
        self.config = load_config()
        self.save_dir = self.config.get("save_dir", DEFAULT_DESKTOP)
        self.last_screenshot_time = 0.0

        self.root = tk.Tk()
        self.root.title("截图工具")
        self.root.geometry("520x650")
        self.root.resizable(True, True)

        self._build_ui()
        self._bind_hotkeys()
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

        # ---- 上传配置 ----
        upload_frame = tk.LabelFrame(self.root, text="上传配置", padx=10, pady=6)
        upload_frame.pack(fill="x", padx=10, pady=5)

        token_row = tk.Frame(upload_frame)
        token_row.pack(fill="x", pady=2)
        tk.Label(token_row, text="Token:", width=6, anchor="w").pack(side="left")
        self.token_var = tk.StringVar(value=self.config.get("upload_token", ""))
        token_entry = tk.Entry(token_row, textvariable=self.token_var, show="*")
        token_entry.pack(fill="x", padx=(0, 5), side="left", expand=True)

        server_row = tk.Frame(upload_frame)
        server_row.pack(fill="x", pady=2)
        tk.Label(server_row, text="服务器:", width=6, anchor="w").pack(side="left")
        self.server_var = tk.StringVar(value=self.config.get("server_url", "http://localhost:9090/api/ragent"))
        server_entry = tk.Entry(server_row, textvariable=self.server_var)
        server_entry.pack(fill="x", padx=(0, 5), side="left", expand=True)

        self.auto_upload_var = tk.BooleanVar(value=self.config.get("auto_upload", False))
        tk.Checkbutton(upload_frame, text="截图后自动上传", variable=self.auto_upload_var,
                       command=self._on_auto_upload_change).pack(anchor="w", pady=2)

        self.token_var.trace_add("write", lambda *_: self._save_upload_config())
        self.server_var.trace_add("write", lambda *_: self._save_upload_config())

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
            font=("Consolas", 9), wrap="word")
        self.log_text.pack(fill="both", expand=True)

    def _bind_hotkeys(self):
        keyboard.on_press_key("F2", lambda _: self.root.after(0, self._on_screenshot))
        keyboard.on_press_key("F3", lambda _: self.root.after(0, self._on_show))
        keyboard.on_press_key("F9", lambda _: self.root.after(0, self._on_start))
        keyboard.on_press_key("F10", lambda _: self.root.after(0, self._on_pause))
        keyboard.on_press_key("esc", lambda _: self.root.after(0, self._on_minimize))

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

    def _on_auto_upload_change(self):
        self.config["auto_upload"] = self.auto_upload_var.get()
        save_config(self.config)

    def _save_upload_config(self):
        self.config["upload_token"] = self.token_var.get().strip()
        self.config["server_url"] = self.server_var.get().strip()
        save_config(self.config)

    # ---- 日志（线程安全）----

    def log(self, msg):
        """线程安全的日志写入，通过 root.after 回到主线程"""
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

        def _do_screenshot():
            filepath = take_screenshot(save_dir, quality_index, log_callback=self.log)
            if filepath and self.auto_upload_var.get():
                token = self.token_var.get().strip()
                server = self.server_var.get().strip()
                if token:
                    upload_screenshot(filepath, token, server, log_callback=self.log)

        threading.Thread(target=_do_screenshot, daemon=True).start()

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
        if messagebox.askyesno("确认关闭", "确定要关闭截图工具吗？"):
            self.running = False
            keyboard.unhook_all()
            self.root.destroy()

    def run(self):
        self.log("截图工具已启动")
        w, h = pyautogui.size()
        scale = get_scale_factor()
        self.log(f"屏幕分辨率: {w}x{h}  缩放: {scale:.0%}")
        self.log(f"默认保存路径: {self.save_dir}")
        self.root.mainloop()


if __name__ == "__main__":
    app = ScreenshotApp()
    app.run()
