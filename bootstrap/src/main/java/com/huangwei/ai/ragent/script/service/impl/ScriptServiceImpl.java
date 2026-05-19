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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    @Value("${script.github.token:}")
    private String githubToken;

    @Value("${script.github.owner:}")
    private String githubOwner;

    @Value("${script.github.repo:}")
    private String githubRepo;

    @Value("${script.api-base-url:}")
    private String apiBaseUrl;

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
                    .uploadToken(p.getUploadToken())
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
                .uploadToken(project.getUploadToken())
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
                .uploadToken(UUID.randomUUID().toString().replace("-", ""))
                .build();
        projectMapper.insert(project);

        return ScriptProjectVO.builder()
                .id(project.getId().toString())
                .name(project.getName())
                .description(project.getDescription())
                .status(project.getStatus())
                .uploadToken(project.getUploadToken())
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

    @Override
    @Transactional
    public ScriptScreenshotVO uploadByToken(String token, MultipartFile file) {
        if (StrUtil.isBlank(token)) {
            throw new ScriptBizException("上传Token不能为空");
        }
        ScriptProjectDO project = projectMapper.selectOne(
                new LambdaQueryWrapper<ScriptProjectDO>()
                        .eq(ScriptProjectDO::getUploadToken, token)
                        .eq(ScriptProjectDO::getDeleted, 0));
        if (project == null) {
            throw new ScriptBizException("无效的上传Token");
        }
        if (!isImageFile(file)) {
            throw new ScriptBizException("不支持的图片格式: " + file.getOriginalFilename());
        }

        Long count = screenshotMapper.selectCount(
                new LambdaQueryWrapper<ScriptScreenshotDO>()
                        .eq(ScriptScreenshotDO::getProjectId, project.getId()));

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
        // 确保项目有 uploadToken（旧项目可能没有）
        if (StrUtil.isBlank(project.getUploadToken())) {
            project.setUploadToken(UUID.randomUUID().toString().replace("-", ""));
            projectMapper.updateById(project);
        }
        String token = project.getUploadToken();
        return ScriptCodeGenerator.generate(steps, project.getName(),
                project.getTargetWidth(), project.getTargetHeight(), gui,
                apiBaseUrl, token);
    }

    @Override
    public Map<String, Object> buildExe(Long projectId, String mode) {
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

        if ("bat".equals(mode)) {
            return doBuildBat(projectId, script);
        }

        // GitHub Actions 异步编译
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

    /**
     * BAT 模式：打包 .py + .bat + 模板图片为 zip，上传 OSS，同步返回下载链接
     */
    private Map<String, Object> doBuildBat(Long projectId, String scriptContent) {
        Path tmpDir = null;
        try {
            ScriptProjectDO project = projectMapper.selectById(projectId);

            // 创建临时目录
            tmpDir = Files.createTempDirectory("bat-build-");
            Path projectDir = tmpDir.resolve(project.getName());
            Files.createDirectories(projectDir);

            // 先处理模板（下载或从截图裁剪），更新步骤的 templatePath
            downloadTemplates(projectId, scriptContent, projectDir);

            // 重新生成脚本（因为 templatePath 可能已更新）
            String updatedScript = previewScript(projectId);

            // 写入 script.py
            Files.writeString(projectDir.resolve("script.py"), updatedScript);

            // 写入 requirements.txt
            Files.writeString(projectDir.resolve("requirements.txt"), "Pillow\npyautogui\n");

            // 写入 run.bat（自动安装依赖，执行完毕自动关闭）
            String batContent = "@echo off\r\n"
                    + "chcp 65001 >nul\r\n"
                    + "echo ================================\r\n"
                    + "echo   脚本运行工具\r\n"
                    + "echo ================================\r\n"
                    + "echo.\r\n"
                    + "echo [1/2] 正在检查并安装依赖...\r\n"
                    + "pip install -r requirements.txt -q\r\n"
                    + "if errorlevel 1 (\r\n"
                    + "    echo 依赖安装失败，请确保已安装 Python 并添加到 PATH\r\n"
                    + "    echo 下载 Python: https://www.python.org/ftp/python/3.11.9/python-3.11.9-amd64.exe\r\n"
                    + "    pause\r\n"
                    + "    exit /b 1\r\n"
                    + ")\r\n"
                    + "echo [2/2] 依赖安装完成，正在运行脚本...\r\n"
                    + "echo.\r\n"
                    + "python script.py\r\n"
                    + "echo.\r\n"
                    + "if errorlevel 1 (\r\n"
                    + "    echo 脚本执行出错，请检查错误信息\r\n"
                    + "    pause\r\n"
                    + ") else (\r\n"
                    + "    timeout /t 5 /nobreak >nul\r\n"
                    + ")\r\n";
            Files.writeString(projectDir.resolve("run.bat"), batContent);

            // 打包为 zip
            String zipName = project.getName() + "_bat.zip";
            Path zipPath = tmpDir.resolve(zipName);
            zipDirectory(projectDir, zipPath);

            // 上传到 OSS（用项目名作为文件名）
            byte[] zipBytes = Files.readAllBytes(zipPath);
            String safeName = project.getName().replaceAll("[^\\w\\u4e00-\\u9fa5\\-]", "_");
            String key = "script-bat/" + projectId + "/" + safeName + "_bat.zip";
            String downloadUrl = ossUtil.upload(key, zipBytes, "application/zip");

            // 更新项目状态
            project.setStatus(ScriptStatus.SUCCESS.getValue());
            project.setExePath(downloadUrl);
            projectMapper.updateById(project);

            // 更新进度
            buildProgressMap.put(projectId, BuildProgress.builder()
                    .status(ScriptStatus.SUCCESS.getValue())
                    .progress(100)
                    .message("打包完成")
                    .downloadUrl(downloadUrl)
                    .build());

            // 5 分钟后清理进度
            CompletableFuture.delayedExecutor(5, TimeUnit.MINUTES)
                    .execute(() -> buildProgressMap.remove(projectId));

            Map<String, Object> result = new HashMap<>();
            result.put("status", ScriptStatus.SUCCESS.getValue());
            result.put("downloadUrl", downloadUrl);
            return result;

        } catch (Exception e) {
            log.error("BAT 打包失败: projectId={}", projectId, e);
            buildProgressMap.put(projectId, BuildProgress.builder()
                    .status(ScriptStatus.FAILED.getValue())
                    .progress(0)
                    .message("打包失败: " + e.getMessage())
                    .build());
            throw new ScriptBizException("打包失败: " + e.getMessage());
        } finally {
            deleteDir(tmpDir);
        }
    }

    /**
     * 将目录打包为 zip 文件
     */
    private void zipDirectory(Path sourceDir, Path zipPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            try (Stream<Path> walk = Files.walk(sourceDir)) {
                walk.filter(Files::isRegularFile).forEach(file -> {
                    String relativePath = sourceDir.relativize(file).toString().replace("\\", "/");
                    try {
                        zos.putNextEntry(new ZipEntry(relativePath));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    try {
                        Files.copy(file, zos);
                    } catch (IOException e) {
                        throw new RuntimeException("写入 zip 失败: " + file, e);
                    }
                    try {
                        zos.closeEntry();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    private void doBuild(Long projectId, String taskId, String scriptContent) {
        Path tmpDir = null;
        try {
            ScriptProjectDO buildProject = projectMapper.selectById(projectId);

            // 收集模板 URL
            List<String> templateUrls = collectTemplateUrls(projectId);

            updateProgress(projectId, 10, "正在提交编译任务到 GitHub Actions...");

            // 如果配置了 GitHub，使用 GitHub Actions 编译
            if (StrUtil.isNotBlank(githubToken) && StrUtil.isNotBlank(githubOwner) && StrUtil.isNotBlank(githubRepo)) {
                doBuildWithGitHub(projectId, taskId, scriptContent, buildProject, templateUrls);
            } else {
                doBuildLocal(projectId, taskId, scriptContent, buildProject);
            }

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
            CompletableFuture.delayedExecutor(5, TimeUnit.MINUTES)
                    .execute(() -> {
                        BuildProgress cur = buildProgressMap.get(projectId);
                        if (cur != null && ScriptStatus.FAILED.getValue().equals(cur.getStatus())) {
                            buildProgressMap.remove(projectId);
                        }
                    });
        }
    }

    /**
     * 通过 GitHub Actions 编译
     */
    private void doBuildWithGitHub(Long projectId, String taskId, String scriptContent,
                                    ScriptProjectDO buildProject, List<String> templateUrls) throws Exception {
        Path tmpDir = null;
        try {
            java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
                    .build();
            String apiBase = "https://api.github.com/repos/" + githubOwner + "/" + githubRepo;
            String authHeader = "Bearer " + githubToken;

            // 1. 触发 workflow_dispatch
            String templateUrlsJson = objectMapper.writeValueAsString(templateUrls);
            Map<String, String> inputs = new HashMap<>();
            inputs.put("script_content", scriptContent);
            inputs.put("project_name", buildProject != null ? buildProject.getName() : "script");
            inputs.put("template_urls", templateUrlsJson);
            String bodyJson = objectMapper.writeValueAsString(Map.of("ref", "main", "inputs", inputs));
            log.info("触发 GitHub Actions, body size={} bytes", bodyJson.length());

            java.net.http.HttpRequest triggerReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(apiBase + "/actions/workflows/build.yml/dispatches"))
                    .header("Authorization", authHeader)
                    .header("Accept", "application/vnd.github+json")
                    .header("Content-Type", "application/json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            java.net.http.HttpResponse<String> triggerResp = httpClient.send(triggerReq, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (triggerResp.statusCode() != 204) {
                throw new ScriptBizException("触发 GitHub Actions 失败: " + triggerResp.statusCode() + " " + triggerResp.body());
            }

            updateProgress(projectId, 20, "编译任务已提交，等待 GitHub Actions 启动...");

            // 2. 等待几秒让 workflow run 创建
            Thread.sleep(5000);

            // 3. 轮询获取最新的 workflow run
            String runId = null;
            for (int i = 0; i < 120; i++) {
                Thread.sleep(5000);

                java.net.http.HttpRequest listReq = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(apiBase + "/actions/runs?per_page=1"))
                        .header("Authorization", authHeader)
                        .header("Accept", "application/vnd.github+json")
                        .GET()
                        .build();

                java.net.http.HttpResponse<String> listResp = httpClient.send(listReq, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (listResp.statusCode() != 200) {
                    log.warn("查询 workflow runs 失败: {}", listResp.statusCode());
                    continue;
                }

                Map<String, Object> runsData = objectMapper.readValue(listResp.body(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                List<Map<String, Object>> workflowRuns = (List<Map<String, Object>>) runsData.get("workflow_runs");
                if (workflowRuns == null || workflowRuns.isEmpty()) continue;

                Map<String, Object> latestRun = workflowRuns.get(0);
                String status = (String) latestRun.get("status");
                String conclusion = (String) latestRun.get("conclusion");
                runId = latestRun.get("id").toString();

                if ("completed".equals(status)) {
                    if (!"success".equals(conclusion)) {
                        throw new ScriptBizException("GitHub Actions 编译失败: " + conclusion);
                    }
                    break;
                }

                int progress = Math.min(70, 20 + i);
                updateProgress(projectId, progress, "GitHub Actions 编译中... (" + status + ")");
            }

            if (runId == null) {
                throw new ScriptBizException("等待 GitHub Actions 超时");
            }

            updateProgress(projectId, 75, "正在下载编译产物...");

            // 4. 下载 artifact
            java.net.http.HttpRequest artifactReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(apiBase + "/actions/runs/" + runId + "/artifacts"))
                    .header("Authorization", authHeader)
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> artifactResp = httpClient.send(artifactReq, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (artifactResp.statusCode() != 200) {
                throw new ScriptBizException("获取 artifact 列表失败: " + artifactResp.statusCode());
            }

            Map<String, Object> artifactData = objectMapper.readValue(artifactResp.body(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
            List<Map<String, Object>> artifacts = (List<Map<String, Object>>) artifactData.get("artifacts");
            if (artifacts == null || artifacts.isEmpty()) {
                throw new ScriptBizException("未找到编译产物 (artifact)");
            }

            String artifactId = artifacts.get(0).get("id").toString();

            // 5. 下载 artifact zip
            java.net.http.HttpRequest downloadReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(apiBase + "/actions/artifacts/" + artifactId + "/zip"))
                    .header("Authorization", authHeader)
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();

            tmpDir = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "gha-download-");
            Path zipFile = tmpDir.resolve("artifact.zip");
            var downloadResp = httpClient.send(downloadReq, java.net.http.HttpResponse.BodyHandlers.ofFile(zipFile));
            if (downloadResp.statusCode() != 200) {
                throw new ScriptBizException("下载 artifact 失败: HTTP " + downloadResp.statusCode());
            }

            updateProgress(projectId, 85, "正在解压并上传到 OSS...");

            // 6. 解压 zip，找到 .exe 文件
            Path extractDir = tmpDir.resolve("extracted");
            Files.createDirectories(extractDir);
            if (Files.size(zipFile) == 0) {
                throw new ScriptBizException("下载的 artifact zip 为空，请检查 GitHub Actions 编译是否成功");
            }
            try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(zipFile))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path target = extractDir.resolve(entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(zis, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            if (!Files.isDirectory(extractDir)) {
                throw new ScriptBizException("artifact zip 解压失败，未生成任何文件");
            }

            File exeFile;
            try (Stream<Path> stream = Files.walk(extractDir)) {
                exeFile = stream
                        .filter(p -> p.getFileName().toString().endsWith(".exe") && Files.isRegularFile(p))
                        .findFirst()
                        .map(Path::toFile)
                        .orElseThrow(() -> new ScriptBizException("artifact 中未找到 .exe 文件"));
            }

            // 7. 上传到 OSS
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

            log.info("EXE 编译成功 (GitHub Actions): projectId={}, url={}", projectId, ossUrl);

            CompletableFuture.delayedExecutor(5, TimeUnit.MINUTES)
                    .execute(() -> {
                        BuildProgress cur = buildProgressMap.get(projectId);
                        if (cur != null && ScriptStatus.SUCCESS.getValue().equals(cur.getStatus())) {
                            buildProgressMap.remove(projectId);
                        }
                    });
        } finally {
            deleteDir(tmpDir);
        }
    }

    /**
     * 本地 Nuitka 编译（未配置 GitHub 时的回退方案）
     */
    private void doBuildLocal(Long projectId, String taskId, String scriptContent, ScriptProjectDO buildProject) {
        Path tmpDir = null;
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            Path buildBase = os.contains("win")
                    ? Path.of("C:", "temp", "nuitka-builds")
                    : Path.of("/tmp", "nuitka-builds");
            Files.createDirectories(buildBase);
            tmpDir = Files.createTempDirectory(buildBase, "script-build-");

            updateProgress(projectId, 20, "正在写入脚本文件...");

            Path scriptFile = tmpDir.resolve("script.py");
            Files.writeString(scriptFile, scriptContent);

            updateProgress(projectId, 25, "正在下载模板图片...");
            downloadTemplates(projectId, scriptContent, tmpDir);

            updateProgress(projectId, 30, "正在调用 Nuitka 编译...");

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
            pb.environment().put("TMPDIR", buildBase.toString());
            pb.environment().put("TEMP", buildBase.toString());
            pb.environment().put("TMP", buildBase.toString());

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.info("[Nuitka] {}", line);
                }
            }

            boolean finished = process.waitFor(10, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new ScriptBizException("编译超时（10分钟）");
            }
            if (process.exitValue() != 0) {
                log.error("Nuitka 编译失败，退出码: {}，输出:\n{}", process.exitValue(), output);
                throw new ScriptBizException("Nuitka 编译失败，退出码: " + process.exitValue());
            }

            updateProgress(projectId, 80, "正在查找编译产物...");

            Path outputDir = tmpDir.resolve("output");
            File exeFile;
            boolean isWindows = os.contains("win");
            try (Stream<Path> stream = Files.walk(outputDir)) {
                exeFile = stream
                        .filter(p -> {
                            String name = p.getFileName().toString();
                            if (name.contains(".dist")) return false;
                            if (isWindows) return name.endsWith(".exe");
                            return (name.endsWith(".bin") || !name.contains(".")) && Files.isRegularFile(p);
                        })
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

            log.info("EXE 编译成功 (本地): projectId={}, url={}", projectId, ossUrl);

            CompletableFuture.delayedExecutor(5, TimeUnit.MINUTES)
                    .execute(() -> {
                        BuildProgress cur = buildProgressMap.get(projectId);
                        if (cur != null && ScriptStatus.SUCCESS.getValue().equals(cur.getStatus())) {
                            buildProgressMap.remove(projectId);
                        }
                    });

        } catch (Exception e) {
            log.error("本地编译失败: projectId={}", projectId, e);
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

    /**
     * 收集项目中所有 if_image 步骤的模板 URL
     * 支持两种模式：直接上传的 URL 和从截图裁剪的区域
     */
    private List<String> collectTemplateUrls(Long projectId) {
        List<ScriptStepDO> steps = stepMapper.selectList(
                new LambdaQueryWrapper<ScriptStepDO>()
                        .eq(ScriptStepDO::getProjectId, projectId));
        List<String> urls = new ArrayList<>();
        for (ScriptStepDO step : steps) {
            if (!"if_image".equals(step.getOperationType())) continue;
            Map<String, Object> params = parseParams(step.getParamsJson());
            String templateUrl = StrUtil.nullToEmpty((String) params.get("templateUrl"));
            if (StrUtil.isNotBlank(templateUrl)) {
                urls.add(templateUrl);
            } else if (params.containsKey("cropScreenshotId")) {
                // 从截图裁剪区域，上传到 OSS 后返回 URL
                try {
                    String cropScreenshotId = String.valueOf(params.get("cropScreenshotId"));
                    int x1 = getInt(params, "x1", 0);
                    int y1 = getInt(params, "y1", 0);
                    int x2 = getInt(params, "x2", 100);
                    int y2 = getInt(params, "y2", 100);

                    ScriptScreenshotDO screenshot = screenshotMapper.selectById(Long.parseLong(cropScreenshotId));
                    if (screenshot == null || StrUtil.isBlank(screenshot.getFileUrl())) {
                        log.warn("截图不存在: screenshotId={}", cropScreenshotId);
                        continue;
                    }

                    // 裁剪并上传到 OSS
                    Path tmpFile = Files.createTempFile("tpl_crop_", ".png");
                    try {
                        cropRegionFromScreenshot(screenshot.getFileUrl(), x1, y1, x2, y2, tmpFile);
                        byte[] bytes = Files.readAllBytes(tmpFile);
                        String key = TEMPLATE_PREFIX + "/" + projectId + "/tpl_crop_" + step.getId() + ".png";
                        String url = ossUtil.upload(key, bytes, "image/png");
                        urls.add(url);

                        // 更新步骤的 templateUrl
                        step.setTemplateUrl(url);
                        params.put("templateUrl", url);
                        step.setParamsJson(objectMapper.writeValueAsString(params));
                        stepMapper.updateById(step);

                        log.info("裁剪并上传模板: stepId={}, url={}", step.getId(), url);
                    } finally {
                        Files.deleteIfExists(tmpFile);
                    }
                } catch (Exception e) {
                    log.warn("裁剪模板失败: stepId={}", step.getId(), e);
                }
            }
        }
        return urls;
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
     * 支持两种模式：
     * 1. 直接上传的模板（templateUrl）
     * 2. 画布圈区域（cropScreenshotId + x1,y1,x2,y2），编译时截取
     */
    private void downloadTemplates(Long projectId, String scriptContent, Path tmpDir) {
        List<ScriptStepDO> steps = stepMapper.selectList(
                new LambdaQueryWrapper<ScriptStepDO>()
                        .eq(ScriptStepDO::getProjectId, projectId));
        Path templatesDir = tmpDir.resolve("templates");
        boolean hasTemplates = false;

        for (ScriptStepDO step : steps) {
            if (!"if_image".equals(step.getOperationType())) continue;
            Map<String, Object> params = parseParams(step.getParamsJson());
            String templateUrl = StrUtil.nullToEmpty((String) params.get("templateUrl"));

            try {
                if (StrUtil.isNotBlank(templateUrl)) {
                    // 模式1：直接下载已上传的模板
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
                } else if (params.containsKey("cropScreenshotId")) {
                    // 模式2：从截图裁剪区域
                    String cropScreenshotId = String.valueOf(params.get("cropScreenshotId"));
                    int x1 = getInt(params, "x1", 0);
                    int y1 = getInt(params, "y1", 0);
                    int x2 = getInt(params, "x2", 100);
                    int y2 = getInt(params, "y2", 100);

                    ScriptScreenshotDO screenshot = screenshotMapper.selectById(Long.parseLong(cropScreenshotId));
                    if (screenshot == null || StrUtil.isBlank(screenshot.getFileUrl())) {
                        log.warn("截图不存在或无文件URL: screenshotId={}", cropScreenshotId);
                        continue;
                    }

                    if (!hasTemplates) {
                        Files.createDirectories(templatesDir);
                        hasTemplates = true;
                    }

                    // 下载原图并裁剪
                    String templateName = "tpl_crop_" + step.getId() + ".png";
                    Path templatePath = templatesDir.resolve(templateName);
                    cropRegionFromScreenshot(screenshot.getFileUrl(), x1, y1, x2, y2, templatePath);

                    // 更新步骤的 templatePath（用于脚本引用）
                    String relPath = "templates/" + templateName;
                    step.setTemplatePath(relPath);
                    params.put("templatePath", relPath);
                    step.setParamsJson(objectMapper.writeValueAsString(params));
                    stepMapper.updateById(step);

                    log.info("裁剪模板: screenshot={}, ({},{}) -> ({},{})", cropScreenshotId, x1, y1, x2, y2);
                }
            } catch (Exception e) {
                log.warn("处理模板失败: stepId={}", step.getId(), e);
            }
        }
    }

    /**
     * 从 OSS 下载截图并裁剪指定区域，保存为模板图片
     */
    private void cropRegionFromScreenshot(String screenshotUrl, int x1, int y1, int x2, int y2, Path outputPath) throws Exception {
        try (InputStream is = ossUtil.download(screenshotUrl)) {
            javax.imageio.stream.ImageInputStream imageInputStream = javax.imageio.ImageIO.createImageInputStream(is);
            if (imageInputStream == null) {
                throw new IOException("无法读取图片: " + screenshotUrl);
            }

            // 获取图片尺寸
            java.util.Iterator<javax.imageio.ImageReader> readers = javax.imageio.ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                throw new IOException("无法解析图片格式");
            }
            javax.imageio.ImageReader reader = readers.next();
            reader.setInput(imageInputStream);
            int imgWidth = reader.getWidth(0);
            int imgHeight = reader.getHeight(0);
            reader.dispose();
            imageInputStream.close();

            // 重新下载并读取图片（因为 reader 已消费了流）
            try (InputStream is2 = ossUtil.download(screenshotUrl)) {
                java.awt.image.BufferedImage fullImage = javax.imageio.ImageIO.read(is2);
                if (fullImage == null) {
                    throw new IOException("无法解码图片");
                }

                // 确保坐标在图片范围内
                int cropX = Math.max(0, Math.min(x1, imgWidth));
                int cropY = Math.max(0, Math.min(y1, imgHeight));
                int cropW = Math.min(x2, imgWidth) - cropX;
                int cropH = Math.min(y2, imgHeight) - cropY;

                if (cropW <= 0 || cropH <= 0) {
                    throw new IOException("裁剪区域无效: (" + x1 + "," + y1 + ")->(" + x2 + "," + y2 + ")");
                }

                java.awt.image.BufferedImage cropped = fullImage.getSubimage(cropX, cropY, cropW, cropH);
                javax.imageio.ImageIO.write(cropped, "png", outputPath.toFile());
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
                case "break_loop", "continue_loop" -> {
                    if (forDepth <= 0) throw new ScriptBizException("「跳出循环」和「继续循环」只能放在「循环开始」和「循环结束」之间");
                }
                case "if_image", "if_random", "if_ai" -> ifDepth++;
                case "else" -> {
                    if (ifDepth <= 0) throw new ScriptBizException("存在多余的「否则」，缺少对应的条件开始");
                }
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

    private int getInt(Map<String, Object> params, String key, int defaultVal) {
        Object v = params.get(key);
        if (v == null) return defaultVal;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return defaultVal; }
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
