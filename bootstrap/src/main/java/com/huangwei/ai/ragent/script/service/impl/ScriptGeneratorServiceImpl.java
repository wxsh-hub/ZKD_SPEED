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

package com.huangwei.ai.ragent.script.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huangwei.ai.ragent.framework.exception.ClientException;
import com.huangwei.ai.ragent.script.dao.entity.ScriptProjectDO;
import com.huangwei.ai.ragent.script.dao.entity.ScriptScreenshotDO;
import com.huangwei.ai.ragent.script.dao.entity.ScriptStepDO;
import com.huangwei.ai.ragent.script.dao.mapper.ScriptProjectMapper;
import com.huangwei.ai.ragent.script.dao.mapper.ScriptScreenshotMapper;
import com.huangwei.ai.ragent.script.dao.mapper.ScriptStepMapper;
import com.huangwei.ai.ragent.script.service.ScriptGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptGeneratorServiceImpl implements ScriptGeneratorService {

    private final ScriptProjectMapper projectMapper;
    private final ScriptScreenshotMapper screenshotMapper;
    private final ScriptStepMapper stepMapper;
    private final ObjectMapper objectMapper;

    @Value("${script.storage.base-path:./data/script-projects}")
    private String basePath;

    private final Map<Long, CompileTask> compileTasks = new ConcurrentHashMap<>();

    @Override
    public String generateScript(Long projectId) {
        ScriptProjectDO project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ClientException("项目不存在");
        }

        List<ScriptStepDO> steps = stepMapper.selectList(
                Wrappers.lambdaQuery(ScriptStepDO.class)
                        .eq(ScriptStepDO::getProjectId, projectId)
                        .eq(ScriptStepDO::getDeleted, 0)
                        .orderByAsc(ScriptStepDO::getStepOrder)
        );

        // 确定参考分辨率
        int refWidth = project.getTargetWidth() != null ? project.getTargetWidth() : 1920;
        int refHeight = project.getTargetHeight() != null ? project.getTargetHeight() : 1080;

        if (project.getTargetWidth() == null || project.getTargetHeight() == null) {
            // 从第一张截图获取分辨率
            ScriptScreenshotDO firstScreenshot = screenshotMapper.selectOne(
                    Wrappers.lambdaQuery(ScriptScreenshotDO.class)
                            .eq(ScriptScreenshotDO::getProjectId, projectId)
                            .eq(ScriptScreenshotDO::getDeleted, 0)
                            .orderByAsc(ScriptScreenshotDO::getSortOrder)
                            .last("LIMIT 1")
            );
            if (firstScreenshot != null) {
                if (project.getTargetWidth() == null) refWidth = firstScreenshot.getWidth();
                if (project.getTargetHeight() == null) refHeight = firstScreenshot.getHeight();
            }
        }

        StringBuilder stepsCode = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            ScriptStepDO step = steps.get(i);
            stepsCode.append(generateStepCode(step, i + 1));
            stepsCode.append("\n");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(header(refWidth, refHeight));
        sb.append("\n");
        sb.append(footer(stepsCode.toString()));
        return sb.toString();
    }

    @Override
    public void startCompilation(Long projectId) {
        if (compileTasks.containsKey(projectId)) {
            CompileTask existing = compileTasks.get(projectId);
            if ("RUNNING".equals(existing.status) || "PENDING".equals(existing.status)) {
                throw new ClientException("编译任务已在进行中");
            }
        }

        CompileTask task = new CompileTask();
        task.status = "PENDING";
        compileTasks.put(projectId, task);

        // 异步编译
        Thread compileThread = new Thread(() -> doCompile(projectId, task));
        compileThread.setDaemon(true);
        compileThread.start();
    }

    @Override
    public String getCompileStatus(Long projectId) {
        CompileTask task = compileTasks.get(projectId);
        if (task == null) {
            return "NOT_STARTED";
        }
        return task.status;
    }

    @Override
    public String getExePath(Long projectId) {
        ScriptProjectDO project = projectMapper.selectById(projectId);
        if (project == null || StrUtil.isBlank(project.getExePath())) {
            return null;
        }
        return project.getExePath();
    }

    private void doCompile(Long projectId, CompileTask task) {
        task.status = "RUNNING";
        try {
            // 1. 生成脚本
            String script = generateScript(projectId);

            // 2. 创建构建目录
            Path buildDir = Paths.get(basePath, String.valueOf(projectId), "build");
            Files.createDirectories(buildDir);

            // 3. 写入 main.py
            Path mainPy = buildDir.resolve("main.py");
            Files.write(mainPy, script.getBytes(StandardCharsets.UTF_8));

            // 4. 复制模板图片到 build/templates/
            Path templateDir = Paths.get(basePath, String.valueOf(projectId), "templates");
            Path buildTemplateDir = buildDir.resolve("templates");
            Files.createDirectories(buildTemplateDir);

            if (Files.exists(templateDir)) {
                Files.list(templateDir).forEach(src -> {
                    try {
                        Files.copy(src, buildTemplateDir.resolve(src.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        log.error("复制模板文件失败: {}", src, e);
                    }
                });
            }

            // 5. 执行 PyInstaller
            ScriptProjectDO project = projectMapper.selectById(projectId);
            String exeName = project.getName().replaceAll("[^a-zA-Z0-9_\\-]", "_");

            ProcessBuilder pb = new ProcessBuilder(
                    "pyinstaller", "--onefile", "--noconfirm",
                    "--name", exeName,
                    "--add-data", "templates;templates",
                    "main.py"
            );
            pb.directory(buildDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                Path exePath = buildDir.resolve("dist").resolve(exeName + ".exe");
                if (Files.exists(exePath)) {
                    project.setExePath(exePath.toString());
                    project.setStatus("compiled");
                    projectMapper.updateById(project);
                    task.status = "SUCCESS";
                    task.exePath = exePath.toString();
                } else {
                    task.status = "FAILED";
                    task.error = "EXE文件未生成";
                }
            } else {
                task.status = "FAILED";
                task.error = "PyInstaller退出码: " + exitCode;
            }
        } catch (Exception e) {
            log.error("编译失败", e);
            task.status = "FAILED";
            task.error = e.getMessage();
        }
    }

    private String header(int width, int height) {
        return """
                import pyautogui
                import cv2
                import numpy as np
                import time
                import random
                import os
                import sys

                # ============ Resolution Config ============
                ANNOTATION_WIDTH = %d
                ANNOTATION_HEIGHT = %d

                if getattr(sys, 'frozen', False):
                    BASE_DIR = os.path.dirname(sys.executable)
                else:
                    BASE_DIR = os.path.dirname(os.path.abspath(__file__))

                def scale_x(x):
                    rw, _ = pyautogui.size()
                    return int(x * rw / ANNOTATION_WIDTH)

                def scale_y(y):
                    _, rh = pyautogui.size()
                    return int(y * rh / ANNOTATION_HEIGHT)

                def load_template(filename):
                    path = os.path.join(BASE_DIR, "templates", filename)
                    img = cv2.imread(path, cv2.IMREAD_COLOR)
                    if img is None:
                        raise FileNotFoundError(f"Template not found: {path}")
                    return img

                # ============ Operation Functions ============
                def do_click(x, y):
                    sx, sy = scale_x(x), scale_y(y)
                    pyautogui.click(sx, sy)

                def do_area_click(x1, y1, x2, y2, count, interval_ms):
                    sx1, sy1 = scale_x(x1), scale_y(y1)
                    sx2, sy2 = scale_x(x2), scale_y(y2)
                    for i in range(count):
                        rx = random.randint(min(sx1, sx2), max(sx1, sx2))
                        ry = random.randint(min(sy1, sy2), max(sy1, sy2))
                        pyautogui.click(rx, ry)
                        if i < count - 1:
                            time.sleep(interval_ms / 1000.0)

                def do_long_press(x, y, duration_ms):
                    sx, sy = scale_x(x), scale_y(y)
                    pyautogui.mouseDown(sx, sy)
                    time.sleep(duration_ms / 1000.0)
                    pyautogui.mouseUp(sx, sy)

                def do_wait_image(x1, y1, x2, y2, template_filename, timeout_s):
                    sx1, sy1 = scale_x(x1), scale_y(y1)
                    sx2, sy2 = scale_x(x2), scale_y(y2)
                    template = load_template(template_filename)
                    start = time.time()
                    while time.time() - start < timeout_s:
                        screenshot = pyautogui.screenshot()
                        screen_np = np.array(screenshot)
                        screen_cv = cv2.cvtColor(screen_np, cv2.COLOR_RGB2BGR)
                        region = screen_cv[sy1:sy2, sx1:sx2]
                        if region.shape[0] < template.shape[0] or region.shape[1] < template.shape[1]:
                            time.sleep(0.5)
                            continue
                        result = cv2.matchTemplate(region, template, cv2.TM_CCOEFF_NORMED)
                        _, max_val, _, _ = cv2.minMaxLoc(result)
                        if max_val >= 0.8:
                            return True
                        time.sleep(0.5)
                    raise TimeoutError(f"wait_image timed out after {timeout_s}s")

                def do_wait_seconds(seconds):
                    time.sleep(seconds)

                def do_input_text(x, y, text):
                    sx, sy = scale_x(x), scale_y(y)
                    pyautogui.click(sx, sy)
                    time.sleep(0.2)
                    try:
                        import pyperclip
                        pyperclip.copy(text)
                        pyautogui.hotkey('ctrl', 'v')
                    except ImportError:
                        pyautogui.typewrite(text, interval=0.05)

                def do_scroll(x1, y1, x2, y2, amount):
                    sx = scale_x((x1 + x2) // 2)
                    sy = scale_y((y1 + y2) // 2)
                    pyautogui.moveTo(sx, sy)
                    pyautogui.scroll(amount)

                """.formatted(width, height);
    }

    private String generateStepCode(ScriptStepDO step, int stepNum) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("# Step %d: %s\n", stepNum, step.getOperationType()));

        try {
            JsonNode params = objectMapper.readTree(step.getParamsJson());

            switch (step.getOperationType()) {
                case "click" -> {
                    int x = params.get("x").asInt();
                    int y = params.get("y").asInt();
                    sb.append(String.format("do_click(%d, %d)\n", x, y));
                }
                case "area_click" -> {
                    int x1 = params.get("x1").asInt(), y1 = params.get("y1").asInt();
                    int x2 = params.get("x2").asInt(), y2 = params.get("y2").asInt();
                    int count = params.get("count").asInt(1);
                    int interval = params.get("interval_ms").asInt(500);
                    sb.append(String.format("do_area_click(%d, %d, %d, %d, %d, %d)\n", x1, y1, x2, y2, count, interval));
                }
                case "long_press" -> {
                    int x = params.get("x").asInt();
                    int y = params.get("y").asInt();
                    int dur = params.get("duration_ms").asInt(1000);
                    sb.append(String.format("do_long_press(%d, %d, %d)\n", x, y, dur));
                }
                case "wait_image" -> {
                    int x1 = params.get("x1").asInt(), y1 = params.get("y1").asInt();
                    int x2 = params.get("x2").asInt(), y2 = params.get("y2").asInt();
                    int timeout = params.get("timeout_s").asInt(10);
                    String tplFile = step.getTemplatePath() != null
                            ? new File(step.getTemplatePath()).getName() : "template.png";
                    sb.append(String.format("do_wait_image(%d, %d, %d, %d, \"%s\", %d)\n",
                            x1, y1, x2, y2, tplFile, timeout));
                }
                case "wait_seconds" -> {
                    int seconds = params.get("seconds").asInt(1);
                    sb.append(String.format("do_wait_seconds(%d)\n", seconds));
                }
                case "input_text" -> {
                    int x = params.get("x").asInt();
                    int y = params.get("y").asInt();
                    String text = params.get("text").asText("").replace("\"", "\\\"");
                    sb.append(String.format("do_input_text(%d, %d, \"%s\")\n", x, y, text));
                }
                case "scroll" -> {
                    int x1 = params.get("x1").asInt(), y1 = params.get("y1").asInt();
                    int x2 = params.get("x2").asInt(), y2 = params.get("y2").asInt();
                    int amount = params.get("amount").asInt(3);
                    sb.append(String.format("do_scroll(%d, %d, %d, %d, %d)\n", x1, y1, x2, y2, amount));
                }
                default -> sb.append("# Unknown operation: ").append(step.getOperationType()).append("\n");
            }
        } catch (Exception e) {
            sb.append("# Error parsing params: ").append(e.getMessage()).append("\n");
        }

        return sb.toString();
    }

    private String footer(String stepsCode) {
        // 将步骤代码缩进 8 个空格（在 try 块内）
        String indentedSteps = stepsCode.lines()
                .map(line -> line.isEmpty() ? "" : "        " + line)
                .collect(java.util.stream.Collectors.joining("\n"));

        return """
                # ============ Main ============
                def main():
                    print("Script started. Waiting 3 seconds...")
                    time.sleep(3)

                    try:
                %s
                        print("Script completed successfully.")
                    except TimeoutError as e:
                        print(f"Timeout: {e}")
                    except Exception as e:
                        print(f"Error: {e}")
                    finally:
                        print("Press Enter to exit...")
                        input()

                if __name__ == "__main__":
                    main()
                """.formatted(indentedSteps);
    }

    private static class CompileTask {
        String status;
        String exePath;
        String error;
    }
}
