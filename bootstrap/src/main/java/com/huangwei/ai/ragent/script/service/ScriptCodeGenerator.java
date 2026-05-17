/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.huangwei.ai.ragent.script.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huangwei.ai.ragent.script.dao.entity.ScriptStepDO;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class ScriptCodeGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 生成基于 ctypes 的轻量级 Python 脚本（不依赖 pyautogui，EXE 体积小，不触发杀毒）
     */
    public static String generate(List<ScriptStepDO> steps, String projectName, Integer targetWidth, Integer targetHeight) {
        return generate(steps, projectName, targetWidth, targetHeight, false);
    }

    /**
     * 生成 Python 脚本，支持可选的 tkinter GUI 控制面板
     */
    public static String generate(List<ScriptStepDO> steps, String projectName,
                                  Integer targetWidth, Integer targetHeight,
                                  boolean guiEnabled) {
        StringBuilder sb = new StringBuilder();

        // 头部：ctypes 基础设施
        sb.append("import ctypes\n");
        sb.append("import ctypes.wintypes\n");
        sb.append("import time\n");
        sb.append("import sys\n");
        sb.append("import random\n");
        sb.append("import os\n");
        sb.append("from PIL import Image, ImageGrab\n");
        if (guiEnabled) {
            sb.append("import threading\n");
            sb.append("import tkinter as tk\n");
            sb.append("import traceback\n");
        }
        sb.append("\n");

        // Windows API 常量
        sb.append("user32 = ctypes.windll.user32\n\n");

        // 鼠标操作函数
        sb.append("def mouse_move(x, y):\n");
        sb.append("    user32.SetCursorPos(int(x), int(y))\n\n");

        sb.append("def mouse_click(x, y):\n");
        sb.append("    user32.SetCursorPos(int(x), int(y))\n");
        sb.append("    user32.mouse_event(0x0002, 0, 0, 0, 0)  # LEFTDOWN\n");
        sb.append("    time.sleep(0.02)\n");
        sb.append("    user32.mouse_event(0x0004, 0, 0, 0, 0)  # LEFTUP\n\n");

        sb.append("def mouse_down():\n");
        sb.append("    user32.mouse_event(0x0002, 0, 0, 0, 0)\n\n");

        sb.append("def mouse_up():\n");
        sb.append("    user32.mouse_event(0x0004, 0, 0, 0, 0)\n\n");

        sb.append("def scroll(amount):\n");
        sb.append("    user32.mouse_event(0x0800, 0, 0, int(amount * 120), 0)  # MOUSEEVENTF_WHEEL\n\n");

        sb.append("def hscroll(amount):\n");
        sb.append("    user32.mouse_event(0x1000, 0, 0, int(amount * 120), 0)  # MOUSEEVENTF_HWHEEL\n\n");

        // 图像匹配函数
        sb.append("def match_image(rel_path, x1, y1, x2, y2, threshold=0.95):\n");
        sb.append("    if getattr(sys, 'frozen', False):\n");
        sb.append("        base = os.path.dirname(os.path.abspath(sys.executable))\n");
        sb.append("    else:\n");
        sb.append("        base = os.path.dirname(os.path.abspath(__file__))\n");
        sb.append("    tpl_path = os.path.join(base, rel_path)\n");
        sb.append("    if not os.path.exists(tpl_path):\n");
        sb.append("        # Nuitka onefile 解压路径回退\n");
        sb.append("        import tempfile\n");
        sb.append("        for d in [tempfile.gettempdir(), os.environ.get('TEMP', '')]:\n");
        sb.append("            alt = os.path.join(d, rel_path)\n");
        sb.append("            if os.path.exists(alt):\n");
        sb.append("                tpl_path = alt\n");
        sb.append("                break\n");
        sb.append("    if not os.path.exists(tpl_path):\n");
        sb.append("        print(f'模板不存在: {tpl_path}')\n");
        sb.append("        return False\n");
        sb.append("    tpl = Image.open(tpl_path).convert('RGB')\n");
        sb.append("    scr = ImageGrab.grab(bbox=(x1, y1, x2, y2)).convert('RGB')\n");
        sb.append("    t_px = list(tpl.getdata())\n");
        sb.append("    s_px = list(scr.getdata())\n");
        sb.append("    diff = sum((a - b) ** 2 for a, b in zip(t_px, s_px))\n");
        sb.append("    max_diff = len(t_px) * 3 * 255 * 255\n");
        sb.append("    sim = 1.0 - diff / max_diff if max_diff > 0 else 1.0\n");
        sb.append("    return sim >= threshold\n\n");

        // Unicode 文字输入（支持中文）
        sb.append("INPUT_KEYBOARD = 1\n");
        sb.append("KEYEVENTF_KEYUP = 0x0002\n");
        sb.append("KEYEVENTF_UNICODE = 0x0004\n\n");

        sb.append("class KEYBDINPUT(ctypes.Structure):\n");
        sb.append("    _fields_ = [('wVk', ctypes.wintypes.WORD), ('wScan', ctypes.wintypes.WORD),\n");
        sb.append("                ('dwFlags', ctypes.wintypes.DWORD), ('time', ctypes.wintypes.DWORD),\n");
        sb.append("                ('dwExtraInfo', ctypes.POINTER(ctypes.c_ulong))]\n\n");

        sb.append("class INPUT(ctypes.Structure):\n");
        sb.append("    _fields_ = [('type', ctypes.wintypes.DWORD), ('ki', KEYBDINPUT),\n");
        sb.append("                ('_pad', ctypes.c_ubyte * 8)]\n\n");

        sb.append("def type_text(text):\n");
        sb.append("    for ch in text:\n");
        sb.append("        for event_flag in [KEYEVENTF_UNICODE, KEYEVENTF_UNICODE | KEYEVENTF_KEYUP]:\n");
        sb.append("            inp = INPUT()\n");
        sb.append("            inp.type = INPUT_KEYBOARD\n");
        sb.append("            inp.ki.wScan = ord(ch)\n");
        sb.append("            inp.ki.dwFlags = event_flag\n");
        sb.append("            user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(inp))\n");
        sb.append("            time.sleep(0.01)\n\n");

        if (guiEnabled) {
            // GUI 模式：生成带 tkinter 控制面板的脚本
            generateGuiMode(sb, steps, projectName);
        } else {
            // 普通模式：直接执行
            generatePlainMode(sb, steps, projectName);
        }

        return sb.toString();
    }

    /**
     * 普通模式：直接 run() 执行
     */
    private static void generatePlainMode(StringBuilder sb, List<ScriptStepDO> steps, String projectName) {
        sb.append("def run():\n");
        sb.append("    print('").append(escapePy("开始执行: " + projectName)).append("')\n\n");

        appendStepCode(sb, steps, "    ");

        sb.append("    print('执行完毕')\n\n");
        sb.append("if __name__ == '__main__':\n");
        sb.append("    run()\n");
    }

    /**
     * GUI 模式：生成带 tkinter 控制面板的脚本
     */
    private static void generateGuiMode(StringBuilder sb, List<ScriptStepDO> steps,
                                        String projectName) {
        // 全局控制变量
        sb.append("_paused = False\n");
        sb.append("_stopped = False\n\n");

        // 单次执行函数（带暂停检查）
        sb.append("def run_once():\n");
        sb.append("    print('").append(escapePy("开始执行: " + projectName)).append("')\n\n");

        appendStepCode(sb, steps, "    ", true);

        sb.append("    print('单次执行完毕')\n\n");

        // GUI 启动函数
        sb.append("def start_gui():\n");
        sb.append("    root = tk.Tk()\n");
        sb.append("    root.title('").append(escapePy(projectName)).append("')\n");
        sb.append("    root.resizable(False, False)\n\n");

        // 状态标签
        sb.append("    status_var = tk.StringVar(value='就绪')\n");
        sb.append("    tk.Label(root, textvariable=status_var, font=('Microsoft YaHei', 11), width=30).pack(pady=(10, 5))\n\n");

        // 执行次数输入
        sb.append("    cfg_frame = tk.Frame(root)\n");
        sb.append("    cfg_frame.pack(pady=5)\n");
        sb.append("    tk.Label(cfg_frame, text='执行次数:', font=('Microsoft YaHei', 9)).pack(side='left')\n");
        sb.append("    count_var = tk.StringVar(value='1')\n");
        sb.append("    tk.Entry(cfg_frame, textvariable=count_var, width=6, font=('Microsoft YaHei', 9)).pack(side='left', padx=(2, 10))\n");
        sb.append("    tk.Label(cfg_frame, text='间隔(秒):', font=('Microsoft YaHei', 9)).pack(side='left')\n");
        sb.append("    interval_var = tk.StringVar(value='0')\n");
        sb.append("    tk.Entry(cfg_frame, textvariable=interval_var, width=6, font=('Microsoft YaHei', 9)).pack(side='left', padx=2)\n\n");

        // 按钮区
        sb.append("    btn_frame = tk.Frame(root)\n");
        sb.append("    btn_frame.pack(pady=5)\n\n");

        sb.append("    def on_start():\n");
        sb.append("        global _paused, _stopped\n");
        sb.append("        try:\n");
        sb.append("            total = int(count_var.get())\n");
        sb.append("            if total < 1: raise ValueError\n");
        sb.append("        except ValueError:\n");
        sb.append("            status_var.set('执行次数请输入正整数')\n");
        sb.append("            return\n");
        sb.append("        try:\n");
        sb.append("            sec = int(interval_var.get())\n");
        sb.append("            if sec < 0: raise ValueError\n");
        sb.append("        except ValueError:\n");
        sb.append("            status_var.set('间隔请输入非负整数')\n");
        sb.append("            return\n");
        sb.append("        _paused = False\n");
        sb.append("        _stopped = False\n");
        sb.append("        start_btn.config(state='disabled')\n");
        sb.append("        pause_btn.config(state='normal')\n");
        sb.append("        stop_btn.config(state='normal')\n");
        sb.append("        threading.Thread(target=run_loop, args=(total, sec), daemon=True).start()\n\n");

        sb.append("    def on_pause():\n");
        sb.append("        global _paused\n");
        sb.append("        _paused = not _paused\n");
        sb.append("        if _paused:\n");
        sb.append("            pause_btn.config(text='继续(F6)')\n");
        sb.append("            status_var.set('已暂停')\n");
        sb.append("        else:\n");
        sb.append("            pause_btn.config(text='暂停(F6)')\n");
        sb.append("            status_var.set('执行中...')\n\n");

        sb.append("    def on_stop():\n");
        sb.append("        global _stopped\n");
        sb.append("        _stopped = True\n");
        sb.append("        status_var.set('已停止')\n");
        sb.append("        start_btn.config(state='normal')\n");
        sb.append("        pause_btn.config(state='disabled', text='暂停')\n");
        sb.append("        stop_btn.config(state='disabled')\n\n");

        sb.append("    start_btn = tk.Button(btn_frame, text='开始(F5)', width=10, command=on_start)\n");
        sb.append("    start_btn.pack(side='left', padx=5)\n");
        sb.append("    pause_btn = tk.Button(btn_frame, text='暂停(F6)', width=10, state='disabled', command=on_pause)\n");
        sb.append("    pause_btn.pack(side='left', padx=5)\n");
        sb.append("    stop_btn = tk.Button(btn_frame, text='停止(F7)', width=10, state='disabled', command=on_stop)\n");
        sb.append("    stop_btn.pack(side='left', padx=5)\n");
        sb.append("    root.bind('<F5>', lambda e: on_start())\n");
        sb.append("    root.bind('<F6>', lambda e: on_pause())\n");
        sb.append("    root.bind('<F7>', lambda e: on_stop())\n\n");

        sb.append("    def run_loop(total, interval_sec):\n");
        sb.append("        global _stopped\n");
        sb.append("        for i in range(1, total + 1):\n");
        sb.append("            if _stopped:\n");
        sb.append("                break\n");
        sb.append("            status_var.set(f'第 {i}/{total} 次执行中...')\n");
        sb.append("            try:\n");
        sb.append("                run_once()\n");
        sb.append("            except Exception as e:\n");
        sb.append("                status_var.set(f'第 {i} 次执行出错: {e}')\n");
        sb.append("                break\n");
        sb.append("            if i < total and not _stopped:\n");
        sb.append("                status_var.set(f'第 {i} 次完成，等待 {interval_sec} 秒...')\n");
        sb.append("                for _ in range(interval_sec * 10):\n");
        sb.append("                    if _stopped:\n");
        sb.append("                        break\n");
        sb.append("                    while _paused and not _stopped:\n");
        sb.append("                        time.sleep(0.1)\n");
        sb.append("                    time.sleep(0.1)\n");
        sb.append("        if not _stopped:\n");
        sb.append("            status_var.set('全部执行完毕')\n");
        sb.append("        start_btn.config(state='normal')\n");
        sb.append("        pause_btn.config(state='disabled', text='暂停')\n");
        sb.append("        stop_btn.config(state='disabled')\n\n");

        sb.append("    def on_close():\n");
        sb.append("        global _stopped\n");
        sb.append("        _stopped = True\n");
        sb.append("        root.destroy()\n");
        sb.append("    root.protocol('WM_DELETE_WINDOW', on_close)\n");
        sb.append("    root.mainloop()\n\n");

        sb.append("if __name__ == '__main__':\n");
        sb.append("    try:\n");
        sb.append("        start_gui()\n");
        sb.append("    except Exception:\n");
        sb.append("        _err = traceback.format_exc()\n");
        sb.append("        _dir = os.path.dirname(os.path.abspath(sys.executable if getattr(sys, 'frozen', False) else __file__))\n");
        sb.append("        try:\n");
        sb.append("            with open(os.path.join(_dir, 'error.log'), 'a', encoding='utf-8') as _f:\n");
        sb.append("                _f.write(_err + '\\n')\n");
        sb.append("        except: pass\n");
        sb.append("        try:\n");
        sb.append("            ctypes.windll.user32.MessageBoxW(0, _err[-500:], '脚本错误', 0x10)\n");
        sb.append("        except: pass\n");
    }

    /**
     * 将步骤代码追加到 sb，用于普通模式
     */
    private static void appendStepCode(StringBuilder sb, List<ScriptStepDO> steps, String indent) {
        appendStepCode(sb, steps, indent, false);
    }

    /**
     * 将步骤代码追加到 sb
     * @param withPauseCheck 是否在每步之间插入暂停/停止检查
     */
    private static void appendStepCode(StringBuilder sb, List<ScriptStepDO> steps,
                                       String indent, boolean withPauseCheck) {
        int indentLevel = 0;
        for (ScriptStepDO step : steps) {
            Map<String, Object> params = parseParams(step.getParamsJson());
            String type = step.getOperationType();
            String curIndent = indent + "    ".repeat(indentLevel);

            // 控制流标记不插入暂停检查
            boolean isBlock = type.startsWith("for_") || type.startsWith("if_");
            sb.append(curIndent).append("# step ").append(step.getStepOrder()).append(": ").append(type).append("\n");

            if (withPauseCheck && !isBlock) {
                sb.append(curIndent).append("if _stopped: return\n");
                sb.append(curIndent).append("while _paused and not _stopped: time.sleep(0.1)\n");
            }

            switch (type) {
                case "for_start" -> {
                    int count = getInt(params, "count", 1);
                    sb.append(curIndent).append("for _loop_i in range(").append(count).append("):\n");
                    indentLevel++;
                }
                case "for_end" -> {
                    indentLevel = Math.max(0, indentLevel - 1);
                }
                case "if_image" -> {
                    // 优先用 templatePath，其次从	templateUrl 提取文件名
                    String templatePath = StrUtil.nullToEmpty((String) params.get("templatePath"));
                    if (StrUtil.isBlank(templatePath)) {
                        String templateUrl = StrUtil.nullToEmpty((String) params.get("templateUrl"));
                        if (StrUtil.isNotBlank(templateUrl)) {
                            String fileName = templateUrl.substring(templateUrl.lastIndexOf('/') + 1);
                            templatePath = "templates/" + fileName;
                        }
                    }
                    int x1 = getInt(params, "x1", 0);
                    int y1 = getInt(params, "y1", 0);
                    int x2 = getInt(params, "x2", 100);
                    int y2 = getInt(params, "y2", 100);
                    double similarity = getDouble(params, "similarity", 0.95);
                    sb.append(curIndent).append("if match_image('").append(escapePy(templatePath))
                            .append("', ").append(x1).append(", ").append(y1)
                            .append(", ").append(x2).append(", ").append(y2)
                            .append(", ").append(similarity).append("):\n");
                    indentLevel++;
                }
                case "if_random" -> {
                    double probability = getDouble(params, "probability", 0.5);
                    sb.append(curIndent).append("if random.random() < ").append(probability).append(":\n");
                    indentLevel++;
                }
                case "if_end" -> {
                    indentLevel = Math.max(0, indentLevel - 1);
                }
                case "click" -> {
                    int x = getInt(params, "x", 0);
                    int y = getInt(params, "y", 0);
                    sb.append(curIndent).append("mouse_click(").append(x).append(", ").append(y).append(")\n");
                }
                case "mouse_move" -> {
                    int x = getInt(params, "x", 0);
                    int y = getInt(params, "y", 0);
                    sb.append(curIndent).append("mouse_move(").append(x).append(", ").append(y).append(")\n");
                }
                case "double_click" -> {
                    int x = getInt(params, "x", 0);
                    int y = getInt(params, "y", 0);
                    sb.append(curIndent).append("mouse_click(").append(x).append(", ").append(y).append(")\n");
                    sb.append(curIndent).append("time.sleep(0.05)\n");
                    sb.append(curIndent).append("mouse_click(").append(x).append(", ").append(y).append(")\n");
                }
                case "area_click" -> {
                    int x1 = getInt(params, "x1", 0);
                    int y1 = getInt(params, "y1", 0);
                    int x2 = getInt(params, "x2", 100);
                    int y2 = getInt(params, "y2", 100);
                    sb.append(curIndent).append("_rx = random.randint(").append(x1).append(", ").append(x2).append(")\n");
                    sb.append(curIndent).append("_ry = random.randint(").append(y1).append(", ").append(y2).append(")\n");
                    sb.append(curIndent).append("mouse_click(_rx, _ry)\n");
                }
                case "long_press" -> {
                    int x = getInt(params, "x", 0);
                    int y = getInt(params, "y", 0);
                    int duration = getInt(params, "duration", 1000);
                    sb.append(curIndent).append("mouse_move(").append(x).append(", ").append(y).append(")\n");
                    sb.append(curIndent).append("mouse_down()\n");
                    sb.append(curIndent).append("time.sleep(").append(duration / 1000.0).append(")\n");
                    sb.append(curIndent).append("mouse_up()\n");
                }
                case "area_long_press" -> {
                    int x1 = getInt(params, "x1", 0);
                    int y1 = getInt(params, "y1", 0);
                    int x2 = getInt(params, "x2", 100);
                    int y2 = getInt(params, "y2", 100);
                    int duration = getInt(params, "duration", 1000);
                    sb.append(curIndent).append("_rx = random.randint(").append(x1).append(", ").append(x2).append(")\n");
                    sb.append(curIndent).append("_ry = random.randint(").append(y1).append(", ").append(y2).append(")\n");
                    sb.append(curIndent).append("mouse_move(_rx, _ry)\n");
                    sb.append(curIndent).append("mouse_down()\n");
                    sb.append(curIndent).append("time.sleep(").append(duration / 1000.0).append(")\n");
                    sb.append(curIndent).append("mouse_up()\n");
                }
                case "key_press" -> {
                    String key = StrUtil.nullToEmpty((String) params.get("key"));
                    appendKeyPress(sb, curIndent, key, false, 0);
                }
                case "key_long_press" -> {
                    String key = StrUtil.nullToEmpty((String) params.get("key"));
                    int duration = getInt(params, "duration", 1000);
                    appendKeyPress(sb, curIndent, key, true, duration);
                }
                case "wait_seconds" -> {
                    double seconds = getDouble(params, "seconds", 3);
                    sb.append(curIndent).append("time.sleep(").append(seconds).append(")\n");
                }
                case "input_text" -> {
                    String text = StrUtil.nullToEmpty((String) params.get("text"));
                    sb.append(curIndent).append("type_text('").append(escapePy(text)).append("')\n");
                }
                case "scroll" -> {
                    if (params.containsKey("x1") && params.containsKey("y1")) {
                        // 新版：坐标滑动（起点→终点）
                        int x1 = getInt(params, "x1", 0);
                        int y1 = getInt(params, "y1", 0);
                        int x2 = getInt(params, "x2", 0);
                        int y2 = getInt(params, "y2", 0);
                        int duration = getInt(params, "duration", 500);
                        int moveSteps = Math.max(10, duration / 16);
                        sb.append(curIndent).append("mouse_move(").append(x1).append(", ").append(y1).append(")\n");
                        sb.append(curIndent).append("mouse_down()\n");
                        sb.append(curIndent).append("_dur = ").append(duration / 1000.0).append("\n");
                        sb.append(curIndent).append("_steps = ").append(moveSteps).append("\n");
                        sb.append(curIndent).append("_t0 = time.monotonic()\n");
                        sb.append(curIndent).append("for _i in range(1, _steps + 1):\n");
                        sb.append(curIndent).append("    _t = _i / _steps\n");
                        sb.append(curIndent).append("    _cx = int(").append(x1).append(" + (").append(x2).append(" - ").append(x1).append(") * _t)\n");
                        sb.append(curIndent).append("    _cy = int(").append(y1).append(" + (").append(y2).append(" - ").append(y1).append(") * _t)\n");
                        sb.append(curIndent).append("    mouse_move(_cx, _cy)\n");
                        sb.append(curIndent).append("    _elapsed = time.monotonic() - _t0\n");
                        sb.append(curIndent).append("    _target = _dur * _i / _steps\n");
                        sb.append(curIndent).append("    if _target > _elapsed:\n");
                        sb.append(curIndent).append("        time.sleep(_target - _elapsed)\n");
                        sb.append(curIndent).append("mouse_up()\n");
                    } else {
                        // 旧版兼容：方向+滚动量
                        String direction = StrUtil.nullToEmpty((String) params.getOrDefault("direction", "down"));
                        int amount = getInt(params, "amount", 3);
                        if ("left".equals(direction) || "right".equals(direction)) {
                            int val = "left".equals(direction) ? amount : -amount;
                            sb.append(curIndent).append("hscroll(").append(val).append(")\n");
                        } else {
                            int val = "up".equals(direction) ? amount : -amount;
                            sb.append(curIndent).append("scroll(").append(val).append(")\n");
                        }
                    }
                }
                default -> sb.append(curIndent).append("# unknown operation: ").append(type).append("\n");
            }
            sb.append("\n");
        }
    }

    private static Map<String, Object> parseParams(String json) {
        if (StrUtil.isBlank(json)) return Map.of();
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("解析 paramsJson 失败: {}", json, e);
            return Map.of();
        }
    }

    private static int getInt(Map<String, Object> params, String key, int defaultVal) {
        Object v = params.get(key);
        if (v == null) return defaultVal;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return defaultVal; }
    }

    private static double getDouble(Map<String, Object> params, String key, double defaultVal) {
        Object v = params.get(key);
        if (v == null) return defaultVal;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return defaultVal; }
    }

    private static String escapePy(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\0", "");
    }

    private static final Map<String, Integer> VK_CODES = Map.ofEntries(
            Map.entry("backspace", 0x08), Map.entry("tab", 0x09),
            Map.entry("enter", 0x0D), Map.entry("return", 0x0D),
            Map.entry("shift", 0x10), Map.entry("ctrl", 0x11), Map.entry("alt", 0x12),
            Map.entry("pause", 0x13), Map.entry("capslock", 0x14),
            Map.entry("escape", 0x1B), Map.entry("esc", 0x1B),
            Map.entry("space", 0x20),
            Map.entry("pageup", 0x21), Map.entry("pagedown", 0x22),
            Map.entry("end", 0x23), Map.entry("home", 0x24),
            Map.entry("left", 0x25), Map.entry("up", 0x26), Map.entry("right", 0x27), Map.entry("down", 0x28),
            Map.entry("insert", 0x2D), Map.entry("delete", 0x2E),
            Map.entry("win", 0x5B), Map.entry("lwin", 0x5B), Map.entry("rwin", 0x5C),
            Map.entry("f1", 0x70), Map.entry("f2", 0x71), Map.entry("f3", 0x72), Map.entry("f4", 0x73),
            Map.entry("f5", 0x74), Map.entry("f6", 0x75), Map.entry("f7", 0x76), Map.entry("f8", 0x77),
            Map.entry("f9", 0x78), Map.entry("f10", 0x79), Map.entry("f11", 0x7A), Map.entry("f12", 0x7B),
            Map.entry("numlock", 0x90), Map.entry("scrolllock", 0x91),
            Map.entry("printscreen", 0x2C),
            Map.entry("+", 0xBB), Map.entry(",", 0xBC), Map.entry("-", 0xBD),
            Map.entry(".", 0xBE), Map.entry("/", 0xBF), Map.entry("`", 0xC0),
            Map.entry("[", 0xDB), Map.entry("\\", 0xDC), Map.entry("]", 0xDD), Map.entry("'", 0xDE)
    );

    private static int resolveVk(String keyName) {
        String lower = keyName.trim().toLowerCase();
        Integer vk = VK_CODES.get(lower);
        if (vk != null) return vk;
        if (lower.length() == 1) {
            char c = lower.charAt(0);
            if (c >= '0' && c <= '9') return 0x30 + (c - '0');
            if (c >= 'a' && c <= 'z') return 0x41 + (c - 'a');
        }
        return -1;
    }

    private static void appendKeyPress(StringBuilder sb, String indent, String keyName, boolean hold, int durationMs) {
        String[] parts = keyName.toLowerCase().split("\\+");
        if (hold) {
            for (String part : parts) {
                int vk = resolveVk(part.trim());
                if (vk < 0) { sb.append(indent).append("print('unknown key: ").append(escapePy(part.trim())).append("')").append('\n'); continue; }
                sb.append(indent).append("user32.keybd_event(").append(vk).append(", 0, 0, 0)  # ").append(part.trim()).append(" DOWN").append('\n');
            }
            sb.append(indent).append("time.sleep(").append(durationMs / 1000.0).append(")").append('\n');
            for (int i = parts.length - 1; i >= 0; i--) {
                int vk = resolveVk(parts[i].trim());
                if (vk < 0) continue;
                sb.append(indent).append("user32.keybd_event(").append(vk).append(", 0, 0x0002, 0)  # ").append(parts[i].trim()).append(" UP").append('\n');
            }
        } else {
            for (String part : parts) {
                int vk = resolveVk(part.trim());
                if (vk < 0) { sb.append(indent).append("print('unknown key: ").append(escapePy(part.trim())).append("')").append('\n'); continue; }
                sb.append(indent).append("user32.keybd_event(").append(vk).append(", 0, 0, 0)").append('\n');
                sb.append(indent).append("time.sleep(0.02)").append('\n');
                sb.append(indent).append("user32.keybd_event(").append(vk).append(", 0, 0x0002, 0)").append('\n');
            }
        }
    }
}
