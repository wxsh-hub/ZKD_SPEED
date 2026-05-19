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
import com.huangwei.ai.ragent.framework.convention.ChatMessage;
import com.huangwei.ai.ragent.framework.convention.ChatRequest;
import com.huangwei.ai.ragent.infra.chat.LLMService;
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
    private final LLMService llmService;

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
                             @Qualifier("scriptBuildExecutor") ThreadPoolExecutor scriptBuildExecutor,
                             LLMService llmService) {
        this.projectMapper = projectMapper;
        this.screenshotMapper = screenshotMapper;
        this.stepMapper = stepMapper;
        this.ossUtil = ossUtil;
        this.objectMapper = objectMapper;
        this.scriptBuildExecutor = scriptBuildExecutor;
        this.llmService = llmService;
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

    @Override
    public Map<String, Object> exportProject(Long projectId) {
        ScriptProjectDetailVO detail = getDetail(projectId);

        Map<String, Object> result = new HashMap<>();
        result.put("name", detail.getName());
        result.put("description", detail.getDescription());
        result.put("targetWidth", detail.getTargetWidth());
        result.put("targetHeight", detail.getTargetHeight());
        result.put("scalePct", detail.getScalePct());
        result.put("guiEnabled", detail.getGuiEnabled());

        List<Map<String, Object>> screenshots = new ArrayList<>();
        if (detail.getScreenshots() != null) {
            for (ScriptScreenshotVO s : detail.getScreenshots()) {
                Map<String, Object> item = new HashMap<>();
                item.put("fileName", s.getFileName());
                item.put("fileUrl", s.getFileUrl());
                item.put("width", s.getWidth());
                item.put("height", s.getHeight());
                item.put("scalePct", s.getScalePct());
                item.put("sortOrder", s.getSortOrder());
                screenshots.add(item);
            }
        }
        result.put("screenshots", screenshots);

        List<Map<String, Object>> steps = new ArrayList<>();
        if (detail.getSteps() != null) {
            for (ScriptStepVO s : detail.getSteps()) {
                Map<String, Object> item = new HashMap<>();
                item.put("stepOrder", s.getStepOrder());
                item.put("operationType", s.getOperationType());
                item.put("paramsJson", s.getParamsJson());
                item.put("templatePath", s.getTemplatePath());
                item.put("templateUrl", s.getTemplateUrl());

                // 用 sortOrder 关联截图，而非数据库 ID
                if (s.getScreenshotId() != null) {
                    for (ScriptScreenshotVO ss : detail.getScreenshots()) {
                        if (ss.getId().equals(s.getScreenshotId())) {
                            item.put("screenshotSortOrder", ss.getSortOrder());
                            break;
                        }
                    }
                }
                steps.add(item);
            }
        }
        result.put("steps", steps);

        return result;
    }

    @Override
    @Transactional
    public ScriptProjectVO importProject(Map<String, Object> data) {
        String name = (String) data.getOrDefault("name", "导入的项目");
        String description = (String) data.getOrDefault("description", "");

        ScriptProjectDO project = ScriptProjectDO.builder()
                .id(IdUtil.getSnowflakeNextId())
                .name(name)
                .description(description)
                .targetWidth(data.get("targetWidth") != null ? ((Number) data.get("targetWidth")).intValue() : null)
                .targetHeight(data.get("targetHeight") != null ? ((Number) data.get("targetHeight")).intValue() : null)
                .scalePct(data.get("scalePct") != null ? ((Number) data.get("scalePct")).intValue() : null)
                .guiEnabled(data.get("guiEnabled") != null ? ((Number) data.get("guiEnabled")).intValue() : 0)
                .status(ScriptStatus.DRAFT.getValue())
                .uploadToken(UUID.randomUUID().toString().replace("-", ""))
                .build();
        projectMapper.insert(project);
        Long projectId = project.getId();

        // 导入截图
        Map<Integer, Long> sortOrderToId = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> screenshots = (List<Map<String, Object>>) data.get("screenshots");
        if (screenshots != null) {
            for (int i = 0; i < screenshots.size(); i++) {
                Map<String, Object> s = screenshots.get(i);
                int sortOrder = s.get("sortOrder") != null ? ((Number) s.get("sortOrder")).intValue() : i;

                String fileUrl = (String) s.get("fileUrl");
                // filePath 从 fileUrl 提取路径部分，若无则用空字符串
                String filePath = "";
                if (fileUrl != null && fileUrl.contains("/")) {
                    filePath = fileUrl.substring(fileUrl.lastIndexOf("/"));
                }

                ScriptScreenshotDO screenshot = ScriptScreenshotDO.builder()
                        .id(IdUtil.getSnowflakeNextId())
                        .projectId(projectId)
                        .fileName((String) s.get("fileName"))
                        .filePath(filePath)
                        .fileUrl(fileUrl)
                        .width(s.get("width") != null ? ((Number) s.get("width")).intValue() : null)
                        .height(s.get("height") != null ? ((Number) s.get("height")).intValue() : null)
                        .scalePct(s.get("scalePct") != null ? ((Number) s.get("scalePct")).intValue() : 100)
                        .sortOrder(sortOrder)
                        .deleted(0)
                        .build();
                screenshotMapper.insert(screenshot);
                sortOrderToId.put(sortOrder, screenshot.getId());
            }
        }

        // 导入步骤
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) data.get("steps");
        if (steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                Map<String, Object> s = steps.get(i);
                int stepOrder = s.get("stepOrder") != null ? ((Number) s.get("stepOrder")).intValue() : i;
                String operationType = (String) s.get("operationType");

                String paramsJson = "{}";
                Object paramsObj = s.get("paramsJson");
                if (paramsObj != null) {
                    try {
                        paramsJson = objectMapper.writeValueAsString(paramsObj);
                    } catch (JsonProcessingException e) {
                        log.warn("序列化 paramsJson 失败", e);
                    }
                }

                // 通过 sortOrder 关联截图 ID
                Long screenshotId = null;
                if (s.get("screenshotSortOrder") != null) {
                    int ssOrder = ((Number) s.get("screenshotSortOrder")).intValue();
                    screenshotId = sortOrderToId.get(ssOrder);
                }

                ScriptStepDO step = ScriptStepDO.builder()
                        .id(IdUtil.getSnowflakeNextId())
                        .projectId(projectId)
                        .screenshotId(screenshotId)
                        .stepOrder(stepOrder)
                        .operationType(operationType)
                        .paramsJson(paramsJson)
                        .templatePath((String) s.get("templatePath"))
                        .templateUrl((String) s.get("templateUrl"))
                        .deleted(0)
                        .build();
                stepMapper.insert(step);
            }
        }

        return ScriptProjectVO.builder()
                .id(project.getId().toString())
                .name(project.getName())
                .description(project.getDescription())
                .targetWidth(project.getTargetWidth())
                .targetHeight(project.getTargetHeight())
                .scalePct(project.getScalePct())
                .status(project.getStatus())
                .uploadToken(project.getUploadToken())
                .screenshotCount(screenshots != null ? screenshots.size() : 0)
                .stepCount(steps != null ? steps.size() : 0)
                .createTime(project.getCreateTime())
                .updateTime(project.getUpdateTime())
                .build();
    }

    @Override
    @Transactional
    public ScriptProjectVO aiGenerate(String prompt, String name) {
        // 1. 调用 LLM 生成步骤 JSON
        String systemPrompt = """
                你是一个自动化脚本专家。用户会用自然语言描述一个自动化操作流程，你需要将其转换为结构化的步骤列表。

                ## 可用操作类型及参数

                | 操作类型 | 参数 | 说明 |
                |---------|------|------|
                | click | { x, y } | 点击坐标 |
                | double_click | { x, y } | 双击坐标 |
                | long_press | { x, y, duration_s } | 长按（duration_s 默认 1.0） |
                | mouse_move | { x, y } | 移动鼠标 |
                | key_press | { key } | 按键（如 enter、ctrl、a、f5） |
                | key_long_press | { key, duration_s } | 长按按键 |
                | wait_seconds | { seconds } | 等待指定秒数 |
                | input_text | { text } | 输入文字 |
                | scroll | { x, y, direction, distance } | direction: up/down/left/right, distance: 滚动距离 |
                | for_start | { count } | 循环开始，count 为循环次数 |
                | for_end | {} | 循环结束 |
                | break_loop | {} | 跳出循环 |
                | continue_loop | {} | 继续下一次循环 |
                | if_image | { similarity } | 图像条件判断（templatePath 用户后续补充） |
                | if_ai | { prompt } | AI 识别条件（region 用户后续框选） |
                | if_random | { probability } | 随机条件（0~1 概率） |
                | else | {} | 否则分支 |
                | if_end | {} | 条件结束 |

                ## 输出规则

                1. 输出严格的 JSON，不要包含 markdown 代码块标记
                2. JSON 格式：{"name": "建议的脚本名称", "description": "脚本描述", "steps": [...]}
                3. 每个 step：{"operationType": "类型", "params": {...}}
                4. 需要用户填的字段用 null 占位（如坐标 x、y、templatePath、region 等）
                5. 能推断的先填上（等待秒数、循环次数、输入文字、相似度默认 0.85、长按默认 1 秒、随机概率等）
                6. if_image 的 templatePath 设为 null，用户后续从截图裁切
                7. if_ai 的 region 设为 null，用户后续框选
                8. 坐标如果描述中有明确位置可填近似值（如"屏幕中央"可填 {x: 960, y: 540}），否则 null

                ## 控制流硬性规则（必须严格遵守，否则脚本无法运行）

                ### 规则一：配对规则
                - 每个 for_start 必须有且只有一个对应的 for_end
                - 每个 if_image / if_ai / if_random 必须有且只有一个对应的 if_end
                - 不配对的步骤会导致脚本语法错误

                ### 规则二：else 规则
                - else 只能出现在 if_image / if_ai / if_random 之后、if_end 之前
                - 一个条件块中最多只有一个 else
                - else 不能单独出现，必须在 if 块内
                - 正确结构：if_* → [操作] → else → [操作] → if_end

                ### 规则三：break_loop / continue_loop 规则
                - break_loop 和 continue_loop 只能出现在 for_start 和 for_end 之间
                - 不能出现在 for 循环外部
                - 不能出现在没有 for 的地方

                ### 规则四：嵌套规则
                - for 和 if 可以互相嵌套
                - 嵌套时必须保证每一对正确闭合
                - 例如：for_start → if_* → ... → if_end → for_end

                ### 规则五：正确结构示例

                简单循环：
                [for_start, 操作1, 操作2, for_end]

                条件判断：
                [if_image, 操作A, else, 操作B, if_end]

                循环内条件：
                [for_start, if_image, 操作A, else, 操作B, if_end, for_end]

                循环内跳出：
                [for_start, if_image, break_loop, if_end, 操作, for_end]

                ### 规则六：绝对禁止
                - 禁止出现孤立的 for_end（没有对应的 for_start）
                - 禁止出现孤立的 if_end（没有对应的 if_*/else）
                - 禁止出现孤立的 else（没有对应的 if_*）
                - 禁止在 for 循环外使用 break_loop 或 continue_loop
                - 禁止 if_*/else/if_end 出现在没有配对的随机位置

                ### 规则七：AI 识别使用限制
                - if_ai（AI 识别条件）需要调用服务器接口，有网络延迟和额外开销
                - 用户没有明确提到"AI识别""智能判断""AI判断"等关键词时，不要主动使用 if_ai
                - 优先使用 if_image（图像匹配）作为条件判断，if_image 更快更稳定
                - 只有在用户明确要求用 AI 判断屏幕内容时才使用 if_ai

                请在生成完步骤后自行检查一遍：从头到尾扫描，用栈的方式验证每个 for_start/if_*/else/for_end/if_end 是否合法配对。如果不合法就修正后再输出。
                """;

        String userMessage = "请根据以下描述生成自动化脚本步骤：\n\n" + prompt;

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(systemPrompt),
                        ChatMessage.user(userMessage)
                ))
                .modelId("qwen-plus")
                .temperature(0.3)
                .build();

        String llmResponse;
        try {
            llmResponse = llmService.chat(chatRequest);
        } catch (Exception e) {
            log.error("AI 生成脚本调用 LLM 失败", e);
            throw new ScriptBizException("AI 生成失败，请稍后重试: " + e.getMessage());
        }

        // 2. 解析 LLM 返回的 JSON
        String cleanJson = llmResponse.strip();
        // 去掉可能的 markdown 代码块标记
        if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.replaceFirst("```json\\s*", "").replaceFirst("```\\s*", "").strip();
        }

        Map<String, Object> data;
        try {
            data = objectMapper.readValue(cleanJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            log.error("解析 AI 生成的 JSON 失败, 原始响应: {}", llmResponse, e);
            throw new ScriptBizException("AI 返回格式异常，请重新描述后再试");
        }

        // 3. 校验控制流配对（安全网，防止 LLM 生成非法结构）
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) data.get("steps");
        if (steps != null && !steps.isEmpty()) {
            List<String> types = steps.stream()
                    .map(s -> (String) s.get("operationType"))
                    .collect(Collectors.toList());
            validateControlFlow(types);
        }

        // 4. 创建项目并入库（复用 importProject 逻辑）
        String projectName = (name != null && !name.isBlank()) ? name : (String) data.getOrDefault("name", "AI 生成的脚本");
        String description = (String) data.getOrDefault("description", "");
        data.put("name", projectName);
        data.put("description", description);
        data.put("screenshots", new ArrayList<>());

        return importProject(data);
    }
}
