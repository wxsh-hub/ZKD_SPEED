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

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huangwei.ai.ragent.common.util.ImageCompressor;
import com.huangwei.ai.ragent.common.util.OssUtil;
import com.huangwei.ai.ragent.script.controller.request.CreateProjectRequest;
import com.huangwei.ai.ragent.script.controller.request.SaveProjectRequest;
import com.huangwei.ai.ragent.script.controller.vo.ScriptProjectDetailVO;
import com.huangwei.ai.ragent.script.controller.vo.ScriptProjectVO;
import com.huangwei.ai.ragent.script.controller.vo.ScriptScreenshotVO;
import com.huangwei.ai.ragent.script.controller.vo.ScriptStepVO;
import com.huangwei.ai.ragent.script.dao.entity.ScriptProjectDO;
import com.huangwei.ai.ragent.script.dao.entity.ScriptScreenshotDO;
import com.huangwei.ai.ragent.script.dao.entity.ScriptStepDO;
import com.huangwei.ai.ragent.script.dao.mapper.ScriptProjectMapper;
import com.huangwei.ai.ragent.script.dao.mapper.ScriptScreenshotMapper;
import com.huangwei.ai.ragent.script.dao.mapper.ScriptStepMapper;
import com.huangwei.ai.ragent.script.service.BuildProgress;
import com.huangwei.ai.ragent.script.service.ScriptBizException;
import com.huangwei.ai.ragent.script.service.ScriptCodeGenerator;
import com.huangwei.ai.ragent.script.service.ScriptService;
import com.huangwei.ai.ragent.script.service.ScriptStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class ScriptServiceImpl implements ScriptService {

    private final ScriptProjectMapper projectMapper;
    private final ScriptScreenshotMapper screenshotMapper;
    private final ScriptStepMapper stepMapper;
    private final OssUtil ossUtil;
    private final ObjectMapper objectMapper;
    private final ThreadPoolExecutor scriptBuildExecutor;

    @Value("${script.python-path:python}")
    private String pythonPath;

    public ScriptServiceImpl(ScriptProjectMapper projectMapper,
                             ScriptScreenshotMapper screenshotMapper,
                             ScriptStepMapper stepMapper,
                             OssUtil ossUtil,
                             ObjectMapper objectMapper,
                             @Qualifier("scriptBuildExecutor") ThreadPoolExecutor scriptBuildExecutor) {
        this.projectMapper = projectMapper;
        this.screenshotMapper = screenshotMapper;
        this.stepMapper = stepMapper;
        this.ossUtil = ossUtil;
        this.objectMapper = objectMapper;
        this.scriptBuildExecutor = scriptBuildExecutor;
    }

    private static final String SCREENSHOT_PREFIX = "script/screenshots";
    private static final String EXE_PREFIX = "script-builds";
    private static final String TEMPLATE_PREFIX = "script-builds/templates";

    private final ConcurrentHashMap<Long, BuildProgress> buildProgressMap = new ConcurrentHashMap<>();

    // 图片文件 magic bytes
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] JPG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] BMP_MAGIC = {0x42, 0x4D};
    private static final byte[] WEBP_MAGIC = {0x52, 0x49, 0x46, 0x46}; // RIFF

    @Override
    public List<ScriptProjectVO> list() {
        List<ScriptProjectDO> projects = projectMapper.selectList(
                new LambdaQueryWrapper<ScriptProjectDO>()
                        .orderByDesc(ScriptProjectDO::getUpdateTime)
        );
        if (projects.isEmpty()) return List.of();

        List<Long> ids = projects.stream().map(ScriptProjectDO::getId).collect(Collectors.toList());

        // 批量查询截图，内存分组计数（避免 N+1）
        List<ScriptScreenshotDO> allScreenshots = screenshotMapper.selectList(
                new LambdaQueryWrapper<ScriptScreenshotDO>()
                        .select(ScriptScreenshotDO::getProjectId)
                        .in(ScriptScreenshotDO::getProjectId, ids));
        Map<Long, Long> screenshotCounts = allScreenshots.stream()
                .collect(Collectors.groupingBy(ScriptScreenshotDO::getProjectId, Collectors.counting()));

        return projects.stream().map(p -> {
            Long pid = p.getId();
            return ScriptProjectVO.builder()
                    .id(pid.toString())
                    .name(p.getName())
                    .description(p.getDescription())
                    .targetWidth(p.getTargetWidth())
                    .targetHeight(p.getTargetHeight())
                    .scalePct(p.getScalePct())
                    .status(p.getStatus())
                    .exePath(p.getExePath())
                    .guiEnabled(p.getGuiEnabled())
                    .screenshotCount(screenshotCounts.getOrDefault(pid, 0L).intValue())
                    .stepCount(0)
                    .createTime(p.getCreateTime())
                    .updateTime(p.getUpdateTime())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public ScriptProjectDetailVO getDetail(Long projectId) {
        ScriptProjectDO project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ScriptBizException("项目不存在: " + projectId);
        }

        List<ScriptScreenshotDO> screenshots = screenshotMapper.selectList(
                new LambdaQueryWrapper<ScriptScreenshotDO>()
                        .eq(ScriptScreenshotDO::getProjectId, projectId)
                        .orderByAsc(ScriptScreenshotDO::getSortOrder));

        List<ScriptStepDO> steps = stepMapper.selectList(
                new LambdaQueryWrapper<ScriptStepDO>()
                        .eq(ScriptStepDO::getProjectId, projectId)
                        .orderByAsc(ScriptStepDO::getStepOrder));

        return ScriptProjectDetailVO.builder()
                .id(project.getId().toString())
                .name(project.getName())
                .description(project.getDescription())
                .targetWidth(project.getTargetWidth())
                .targetHeight(project.getTargetHeight())
                .scalePct(project.getScalePct())
                .status(project.getStatus())
                .exePath(project.getExePath())
                .guiEnabled(project.getGuiEnabled())
                .screenshotCount(screenshots.size())
                .stepCount(steps.size())
                .createTime(project.getCreateTime())
                .updateTime(project.getUpdateTime())
                .screenshots(screenshots.stream().map(ScriptScreenshotVO::from).collect(Collectors.toList()))
                .steps(steps.stream().map(ScriptStepVO::from).collect(Collectors.toList()))
                .build();
    }

    @Override
    @Transactional
    public ScriptProjectVO create(CreateProjectRequest request) {
        ScriptProjectDO project = ScriptProjectDO.builder()
                .id(IdUtil.getSnowflakeNextId())
                .name(request.getName())
                .description(request.getDescription())
                .status(ScriptStatus.DRAFT.getValue())
                .build();
        projectMapper.insert(project);

        return ScriptProjectVO.builder()
                .id(project.getId().toString())
                .name(project.getName())
                .description(project.getDescription())
                .status(project.getStatus())
                .screenshotCount(0)
                .stepCount(0)
                .createTime(project.getCreateTime())
                .updateTime(project.getUpdateTime())
                .build();
    }

    @Override
    @Transactional
    public void delete(Long projectId) {
        ScriptProjectDO project = projectMapper.selectById(projectId);
        if (project != null && project.getExePath() != null) {
            ossUtil.deleteByUrl(project.getExePath());
        }
        projectMapper.deleteById(projectId);
        screenshotMapper.delete(
                new LambdaQueryWrapper<ScriptScreenshotDO>().eq(ScriptScreenshotDO::getProjectId, projectId));
        stepMapper.delete(
                new LambdaQueryWrapper<ScriptStepDO>().eq(ScriptStepDO::getProjectId, projectId));
        buildProgressMap.remove(projectId);
    }

    @Override
    @Transactional
    public void save(Long projectId, SaveProjectRequest request) {
        ScriptProjectDO project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ScriptBizException("项目不存在: " + projectId);
        }

        if (request.getGuiEnabled() != null) {
            project.setGuiEnabled(request.getGuiEnabled());
            projectMapper.updateById(project);
        }

        if (request.getScreenshots() != null) {
            for (int i = 0; i < request.getScreenshots().size(); i++) {
                SaveProjectRequest.ScreenshotItem item = request.getScreenshots().get(i);
                if (StrUtil.isNotBlank(item.getId())) {
                    ScriptScreenshotDO existing = screenshotMapper.selectById(Long.parseLong(item.getId()));
                    if (existing != null) {
                        existing.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : i);
                        screenshotMapper.updateById(existing);
                    }
                }
            }
        }

        if (request.getGuiEnabled() != null) {
            project.setGuiEnabled(request.getGuiEnabled());
            projectMapper.updateById(project);
        }

        if (request.getSteps() != null) {
            validateControlFlow(request.getSteps().stream()
                    .map(SaveProjectRequest.StepItem::getOperationType)
                    .collect(java.util.stream.Collectors.toList()));

            stepMapper.delete(
                    new LambdaQueryWrapper<ScriptStepDO>()
                            .eq(ScriptStepDO::getProjectId, projectId));

            for (int i = 0; i < request.getSteps().size(); i++) {
                SaveProjectRequest.StepItem item = request.getSteps().get(i);
                String paramsJson = "{}";
                if (item.getParamsJson() != null) {
                    try {
                        paramsJson = objectMapper.writeValueAsString(item.getParamsJson());
                    } catch (JsonProcessingException e) {
                        log.warn("序列化 paramsJson 失败", e);
                    }
                }

                ScriptStepDO step = ScriptStepDO.builder()
                        .id(IdUtil.getSnowflakeNextId())
                        .projectId(projectId)
                        .screenshotId(StrUtil.isNotBlank(item.getScreenshotId())
                                ? Long.parseLong(item.getScreenshotId()) : null)
                        .stepOrder(item.getStepOrder() != null ? item.getStepOrder() : i)
                        .operationType(item.getOperationType())
                        .paramsJson(paramsJson)
                        .deleted(0)
                        .build();
                stepMapper.insert(step);
            }
        }
    }

    @Override
    @Transactional
    public List<ScriptScreenshotVO> uploadScreenshots(Long projectId, List<MultipartFile> files) {
        ScriptProjectDO project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ScriptBizException("项目不存在: " + projectId);
        }

        Long count = screenshotMapper.selectCount(
                new LambdaQueryWrapper<ScriptScreenshotDO>()
                        .eq(ScriptScreenshotDO::getProjectId, projectId));

        List<ScriptScreenshotVO> result = new ArrayList<>();
        int successCount = 0;
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            if (!isImageFile(file)) continue;

            try {
                ScriptScreenshotVO vo = doUploadScreenshot(project, file, count.intValue() + successCount);
                result.add(vo);
                successCount++;
                project = projectMapper.selectById(projectId);
            } catch (ScriptBizException e) {
                throw e;
            } catch (Exception e) {
                log.error("上传截图失败: {}", file.getOriginalFilename(), e);
            }
        }
        return result;
    }

    @Override
    @Transactional
    public ScriptScreenshotVO uploadScreenshot(Long projectId, MultipartFile file) {
        ScriptProjectDO project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ScriptBizException("项目不存在: " + projectId);
        }
        if (!isImageFile(file)) {
            throw new ScriptBizException("不支持的图片格式: " + file.getOriginalFilename());
        }

        Long count = screenshotMapper.selectCount(
                new LambdaQueryWrapper<ScriptScreenshotDO>()
                        .eq(ScriptScreenshotDO::getProjectId, projectId));

        return doUploadScreenshot(project, file, count.intValue());
    }

    private ScriptScreenshotVO doUploadScreenshot(ScriptProjectDO project, MultipartFile file, int sortOrder) {
        // 压缩并获取尺寸（一次解码）
        ImageCompressor.Result compressed = ImageCompressor.compressWithDimensions(file);
        int width = compressed.width();
        int height = compressed.height();

        String originalName = file.getOriginalFilename();
        int scalePct = extractScale(originalName);
        if (width == 0 || height == 0) {
            int[] res = extractResolution(originalName);
            width = res[0];
            height = res[1];
        }

        if (width == 0 || height == 0) {
            throw new ScriptBizException("无法读取图片分辨率: " + originalName);
        }

        if (project.getTargetWidth() == null) {
            project.setTargetWidth(width);
            project.setTargetHeight(height);
            project.setScalePct(scalePct);
            projectMapper.updateById(project);
            log.info("项目 {} 自动设置分辨率: {}x{}, 缩放: {}%", project.getId(), width, height, scalePct);
        } else {
            if (width != project.getTargetWidth() || height != project.getTargetHeight()) {
                throw new ScriptBizException(String.format(
                        "图片分辨率不一致！项目要求 %dx%d，当前图片 %dx%d（%s）",
                        project.getTargetWidth(), project.getTargetHeight(), width, height, originalName));
            }
            if (project.getScalePct() != null && scalePct != project.getScalePct()) {
                throw new ScriptBizException(String.format(
                        "图片缩放比例不一致！项目要求 %d%%，当前图片 %d%%（%s）",
                        project.getScalePct(), scalePct, originalName));
            }
        }

        String key = ossUtil.generateKey(SCREENSHOT_PREFIX, originalName != null ? originalName : "image.jpg");
        String url = ossUtil.uploadBytes(key, compressed.data(), "image/jpeg");

        String timestampStr = extractTimestamp(originalName);

        ScriptScreenshotDO screenshot = ScriptScreenshotDO.builder()
                .id(IdUtil.getSnowflakeNextId())
                .projectId(project.getId())
                .fileName(originalName)
                .filePath(url)
                .fileUrl(url)
                .width(width)
                .height(height)
                .scalePct(scalePct)
                .timestampStr(timestampStr)
                .sortOrder(sortOrder)
                .build();
        screenshotMapper.insert(screenshot);

        return ScriptScreenshotVO.from(screenshot);
    }

    @Override
    public String previewScript(Long projectId) {
        ScriptProjectDO project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ScriptBizException("项目不存在: " + projectId);
        }

        List<ScriptStepDO> steps = stepMapper.selectList(
                new LambdaQueryWrapper<ScriptStepDO>()
                        .eq(ScriptStepDO::getProjectId, projectId)
                        .orderByAsc(ScriptStepDO::getStepOrder));

        boolean gui = project.getGuiEnabled() != null && project.getGuiEnabled() == 1;
        return ScriptCodeGenerator.generate(steps, project.getName(),
                project.getTargetWidth(), project.getTargetHeight(), gui);
    }

    @Override
    public Map<String, Object> buildExe(Long projectId) {
        ScriptProjectDO project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ScriptBizException("项目不存在: " + projectId);
        }

        BuildProgress existing = buildProgressMap.get(projectId);
        if (existing != null && ScriptStatus.BUILDING.getValue().equals(existing.getStatus())) {
            throw new ScriptBizException("编译任务正在进行中，请稍候");
        }

        // 校验控制流配对
        List<ScriptStepDO> steps = stepMapper.selectList(
                new LambdaQueryWrapper<ScriptStepDO>()
                        .eq(ScriptStepDO::getProjectId, projectId));
        validateControlFlow(steps.stream()
                .map(ScriptStepDO::getOperationType)
                .collect(java.util.stream.Collectors.toList()));

        String script = previewScript(projectId);
        String taskId = IdUtil.getSnowflakeNextIdStr();

        buildProgressMap.put(projectId, BuildProgress.builder()
                .status(ScriptStatus.BUILDING.getValue())
                .progress(10)
                .message("正在准备编译环境...")
                .build());

        project.setStatus(ScriptStatus.BUILDING.getValue());
        projectMapper.updateById(project);

        final String scriptContent = script;
        CompletableFuture.runAsync(() -> doBuild(projectId, taskId, scriptContent), scriptBuildExecutor);

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("status", ScriptStatus.BUILDING.getValue());
        return result;
    }

    private void doBuild(Long projectId, String taskId, String scriptContent) {
        Path tmpDir = null;
        try {
            // 使用纯 ASCII 路径避免 Nuitka 处理中文路径崩溃
            Path buildBase = Path.of("C:", "temp", "nuitka-builds");
            Files.createDirectories(buildBase);
            tmpDir = Files.createTempDirectory(buildBase, "script-build-");

            updateProgress(projectId, 20, "正在写入脚本文件...");

            ScriptProjectDO buildProject = projectMapper.selectById(projectId);
            Path scriptFile = tmpDir.resolve("script.py");
            Files.writeString(scriptFile, scriptContent);

            updateProgress(projectId, 25, "正在下载模板图片...");
            downloadTemplates(projectId, scriptContent, tmpDir);

            updateProgress(projectId, 30, "正在调用 Nuitka 编译...");

            // 检查是否有模板文件需要打包
            Path templatesDir = tmpDir.resolve("templates");
            boolean hasTemplates = false;
            if (Files.isDirectory(templatesDir)) {
                try (var stream = Files.list(templatesDir)) {
                    hasTemplates = stream.findAny().isPresent();
                }
            }

            List<String> cmd = new ArrayList<>();
            cmd.addAll(List.of(
                    pythonPath, "-m", "nuitka",
                    "--standalone",
                    "--onefile",
                    "--disable-console",
                    "--enable-plugin=tk-inter",
                    "--output-dir=" + tmpDir.resolve("output").toString(),
                    "--assume-yes-for-downloads"
            ));
            if (hasTemplates) {
                cmd.add("--include-data-dir=" + templatesDir + "=templates");
            }
            cmd.add(scriptFile.toString());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(tmpDir.toFile());
            pb.redirectErrorStream(true);
            // 清除环境变量中的中文路径，避免 Nuitka 依赖扫描失败
            pb.environment().put("TMPDIR", buildBase.toString());
            pb.environment().put("TEMP", buildBase.toString());
            pb.environment().put("TMP", buildBase.toString());

            Process process = pb.start();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) { /* drain */ }
            }

            boolean finished = process.waitFor(10, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new ScriptBizException("编译超时（10分钟）");
            }

            if (process.exitValue() != 0) {
                throw new ScriptBizException("Nuitka 编译失败，退出码: " + process.exitValue());
            }

            updateProgress(projectId, 80, "正在查找编译产物...");

            Path outputDir = tmpDir.resolve("output");
            File exeFile;
            try (Stream<Path> stream = Files.walk(outputDir)) {
                exeFile = stream
                        .filter(p -> p.toString().endsWith(".exe") && !p.toString().contains(".dist"))
                        .findFirst()
                        .map(Path::toFile)
                        .orElseThrow(() -> new ScriptBizException("未找到编译产物"));
            }

            updateProgress(projectId, 85, "正在上传到 OSS...");

            if (buildProject != null && buildProject.getExePath() != null) {
                ossUtil.deleteByUrl(buildProject.getExePath());
            }

            String safeName = (buildProject != null && StrUtil.isNotBlank(buildProject.getName()))
                    ? buildProject.getName().replaceAll("[^\\w\\u4e00-\\u9fa5\\-]", "_") : "script";
            String key = EXE_PREFIX + "/" + projectId + "/" + safeName + ".exe";
            String ossUrl = ossUtil.uploadFile(key, exeFile);

            if (buildProject != null) {
                buildProject.setStatus(ScriptStatus.SUCCESS.getValue());
                buildProject.setExePath(ossUrl);
                projectMapper.updateById(buildProject);
            }

            buildProgressMap.put(projectId, BuildProgress.builder()
                    .status(ScriptStatus.SUCCESS.getValue())
                    .progress(100)
                    .message("编译完成")
                    .downloadUrl(ossUrl)
                    .build());

            log.info("EXE 编译成功: projectId={}, url={}", projectId, ossUrl);

            // 延迟清理进度缓存，仅当状态未被新任务覆盖时才移除
            String finalTaskId = taskId;
            CompletableFuture.delayedExecutor(5, TimeUnit.MINUTES)
                    .execute(() -> {
                        BuildProgress cur = buildProgressMap.get(projectId);
                        if (cur != null && ScriptStatus.SUCCESS.getValue().equals(cur.getStatus())
                                && ScriptStatus.SUCCESS.getValue().equals(cur.getStatus())) {
                            buildProgressMap.remove(projectId);
                        }
                    });

        } catch (Exception e) {
            log.error("EXE 编译失败: projectId={}", projectId, e);
            buildProgressMap.put(projectId, BuildProgress.builder()
                    .status(ScriptStatus.FAILED.getValue())
                    .progress(0)
                    .message("编译失败: " + e.getMessage())
                    .build());
            try {
                ScriptProjectDO project = projectMapper.selectById(projectId);
                if (project != null) {
                    project.setStatus(ScriptStatus.DRAFT.getValue());
                    projectMapper.updateById(project);
                }
            } catch (Exception ignored) {
            }
            // 失败也延迟清理
            CompletableFuture.delayedExecutor(5, TimeUnit.MINUTES)
                    .execute(() -> {
                        BuildProgress cur = buildProgressMap.get(projectId);
                        if (cur != null && ScriptStatus.FAILED.getValue().equals(cur.getStatus())) {
                            buildProgressMap.remove(projectId);
                        }
                    });
        } finally {
            deleteDir(tmpDir);
        }
    }

    private void deleteDir(Path dir) {
        if (dir == null) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.delete(p); } catch (IOException e) { log.warn("删除失败: {}", p, e); }
                    });
        } catch (IOException e) {
            log.warn("遍历目录失败: {}", dir, e);
        }
    }

    /**
     * 从脚本内容中提取模板 URL，下载到构建目录的 templates/ 子目录
     */
    private void downloadTemplates(Long projectId, String scriptContent, Path tmpDir) {
        // 从步骤中提取所有 if_image 的模板 URL
        List<ScriptStepDO> steps = stepMapper.selectList(
                new LambdaQueryWrapper<ScriptStepDO>()
                        .eq(ScriptStepDO::getProjectId, projectId));
        Path templatesDir = tmpDir.resolve("templates");
        boolean hasTemplates = false;
        for (ScriptStepDO step : steps) {
            if (!"if_image".equals(step.getOperationType())) continue;
            Map<String, Object> params = parseParams(step.getParamsJson());
            String templateUrl = StrUtil.nullToEmpty((String) params.get("templateUrl"));
            if (StrUtil.isBlank(templateUrl)) continue;
            try {
                if (!hasTemplates) {
                    Files.createDirectories(templatesDir);
                    hasTemplates = true;
                }
                String fileName = templateUrl.substring(templateUrl.lastIndexOf('/') + 1);
                Path target = templatesDir.resolve(fileName);
                try (InputStream is = ossUtil.download(templateUrl)) {
                    Files.copy(is, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                log.info("下载模板: {} -> {}", templateUrl, target);
            } catch (Exception e) {
                log.warn("下载模板失败: {}", templateUrl, e);
            }
        }
    }

    /**
     * 校验控制流步骤是否配对：for_start/for_end、if_image/if_random/if_end
     */
    private void validateControlFlow(List<String> types) {
        int forDepth = 0;
        int ifDepth = 0;
        for (String type : types) {
            switch (type) {
                case "for_start" -> forDepth++;
                case "for_end" -> {
                    if (forDepth <= 0) throw new ScriptBizException("存在多余的「循环结束」，缺少对应的「循环开始」");
                    forDepth--;
                }
                case "if_image", "if_random" -> ifDepth++;
                case "if_end" -> {
                    if (ifDepth <= 0) throw new ScriptBizException("存在多余的「条件结束」，缺少对应的条件开始");
                    ifDepth--;
                }
            }
        }
        if (forDepth > 0) throw new ScriptBizException("缺少 " + forDepth + " 个「循环结束」来配对「循环开始」");
        if (ifDepth > 0) throw new ScriptBizException("缺少 " + ifDepth + " 个「条件结束」来配对条件开始");
    }

    private Map<String, Object> parseParams(String json) {
        if (StrUtil.isBlank(json)) return Map.of();
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private void updateProgress(Long projectId, int progress, String message) {
        buildProgressMap.put(projectId, BuildProgress.builder()
                .status(ScriptStatus.BUILDING.getValue())
                .progress(progress)
                .message(message)
                .build());
    }

    @Override
    public Map<String, Object> getBuildStatus(Long projectId) {
        BuildProgress progress = buildProgressMap.get(projectId);
        if (progress != null) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", progress.getStatus());
            result.put("progress", progress.getProgress());
            result.put("message", progress.getMessage());
            if (progress.getDownloadUrl() != null) {
                result.put("downloadUrl", progress.getDownloadUrl());
            }
            return result;
        }

        ScriptProjectDO project = projectMapper.selectById(projectId);
        Map<String, Object> result = new HashMap<>();
        if (project != null && ScriptStatus.SUCCESS.getValue().equals(project.getStatus()) && project.getExePath() != null) {
            result.put("status", ScriptStatus.SUCCESS.getValue());
            result.put("progress", 100);
            result.put("message", "编译完成");
            result.put("downloadUrl", project.getExePath());
        } else {
            result.put("status", "not_started");
            result.put("progress", 0);
            result.put("message", "尚未编译");
        }
        return result;
    }

    @Override
    public String uploadTemplate(Long projectId, MultipartFile file) {
        if (!isImageFile(file)) {
            throw new ScriptBizException("仅支持 PNG/JPG/BMP/WEBP 图片");
        }
        try {
            byte[] data = file.getBytes();
            String ext = ".png";
            String originalName = file.getOriginalFilename();
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf('.'));
            }
            String key = TEMPLATE_PREFIX + "/" + projectId + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
            return ossUtil.upload(key, data, file.getContentType());
        } catch (IOException e) {
            throw new ScriptBizException("模板上传失败", e);
        }
    }

    // ============ 文件校验 ============

    private boolean isImageFile(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[8];
            int read = is.read(header);
            if (read < 4) return false;
            return startsWith(header, PNG_MAGIC)
                    || startsWith(header, JPG_MAGIC)
                    || startsWith(header, BMP_MAGIC)
                    || startsWith(header, WEBP_MAGIC);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    // ============ 文件名解析 ============

    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("(\\d{8}_\\d{6})");
    private static final Pattern SCALE_PATTERN = Pattern.compile("_(\\d{2,3})_(?=\\d+x\\d+)");
    private static final Pattern RESOLUTION_PATTERN = Pattern.compile("(\\d{2,5})x(\\d{2,5})");

    private String extractTimestamp(String filename) {
        if (StrUtil.isBlank(filename)) return null;
        Matcher m = TIMESTAMP_PATTERN.matcher(filename);
        return m.find() ? m.group(1) : null;
    }

    private int extractScale(String filename) {
        if (StrUtil.isBlank(filename)) return 100;
        Matcher m = SCALE_PATTERN.matcher(filename);
        return m.find() ? Integer.parseInt(m.group(1)) : 100;
    }

    private int[] extractResolution(String filename) {
        if (StrUtil.isBlank(filename)) return new int[]{0, 0};
        Matcher m = RESOLUTION_PATTERN.matcher(filename);
        if (m.find()) {
            return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        }
        return new int[]{0, 0};
    }
}
