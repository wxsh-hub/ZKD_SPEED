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
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.huangwei.ai.ragent.framework.exception.ClientException;
import com.huangwei.ai.ragent.script.dao.entity.ScriptProjectDO;
import com.huangwei.ai.ragent.script.dao.entity.ScriptScreenshotDO;
import com.huangwei.ai.ragent.script.dao.entity.ScriptStepDO;
import com.huangwei.ai.ragent.script.dao.mapper.ScriptProjectMapper;
import com.huangwei.ai.ragent.script.dao.mapper.ScriptScreenshotMapper;
import com.huangwei.ai.ragent.script.dao.mapper.ScriptStepMapper;
import com.huangwei.ai.ragent.script.service.ScriptProjectService;
import com.huangwei.ai.ragent.script.util.FilenameParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptProjectServiceImpl implements ScriptProjectService {

    private final ScriptProjectMapper projectMapper;
    private final ScriptScreenshotMapper screenshotMapper;
    private final ScriptStepMapper stepMapper;

    @Value("${script.storage.base-path:./data/script-projects}")
    private String basePath;

    @Override
    public ScriptProjectDO create(String name, String description) {
        ScriptProjectDO project = ScriptProjectDO.builder()
                .id(IdUtil.getSnowflakeNextId())
                .name(name)
                .description(description)
                .status("draft")
                .uploadToken(UUID.randomUUID().toString().replace("-", ""))
                .build();
        projectMapper.insert(project);

        // 创建项目目录
        try {
            Files.createDirectories(getProjectDir(project.getId()));
            Files.createDirectories(getScreenshotDir(project.getId()));
            Files.createDirectories(getTemplateDir(project.getId()));
        } catch (IOException e) {
            log.error("创建项目目录失败", e);
        }

        return project;
    }

    @Override
    public ScriptProjectDO getById(Long id) {
        return projectMapper.selectOne(
                Wrappers.lambdaQuery(ScriptProjectDO.class)
                        .eq(ScriptProjectDO::getId, id)
                        .eq(ScriptProjectDO::getDeleted, 0)
        );
    }

    @Override
    public Map<String, Object> getDetail(Long id) {
        ScriptProjectDO project = getById(id);
        if (project == null) {
            throw new ClientException("项目不存在");
        }

        List<ScriptScreenshotDO> screenshots = screenshotMapper.selectList(
                Wrappers.lambdaQuery(ScriptScreenshotDO.class)
                        .eq(ScriptScreenshotDO::getProjectId, id)
                        .eq(ScriptScreenshotDO::getDeleted, 0)
                        .orderByAsc(ScriptScreenshotDO::getSortOrder)
        );

        List<ScriptStepDO> steps = stepMapper.selectList(
                Wrappers.lambdaQuery(ScriptStepDO.class)
                        .eq(ScriptStepDO::getProjectId, id)
                        .eq(ScriptStepDO::getDeleted, 0)
                        .orderByAsc(ScriptStepDO::getStepOrder)
        );

        Map<String, Object> result = new HashMap<>();
        result.put("project", project);
        result.put("screenshots", screenshots);
        result.put("steps", steps);
        return result;
    }

    @Override
    public List<ScriptProjectDO> listAll() {
        return projectMapper.selectList(
                Wrappers.lambdaQuery(ScriptProjectDO.class)
                        .eq(ScriptProjectDO::getDeleted, 0)
                        .orderByDesc(ScriptProjectDO::getCreateTime)
        );
    }

    @Override
    public void update(Long id, String name, String description, Integer targetWidth, Integer targetHeight) {
        ScriptProjectDO project = getById(id);
        if (project == null) {
            throw new ClientException("项目不存在");
        }
        if (StrUtil.isNotBlank(name)) {
            project.setName(name);
        }
        if (description != null) {
            project.setDescription(description);
        }
        if (targetWidth != null) {
            project.setTargetWidth(targetWidth);
        }
        if (targetHeight != null) {
            project.setTargetHeight(targetHeight);
        }
        projectMapper.updateById(project);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(Long id) {
        ScriptProjectDO project = getById(id);
        if (project == null) {
            throw new ClientException("项目不存在");
        }

        // 删除所有步骤
        stepMapper.delete(
                Wrappers.lambdaQuery(ScriptStepDO.class)
                        .eq(ScriptStepDO::getProjectId, id)
        );
        // 删除所有截图
        screenshotMapper.delete(
                Wrappers.lambdaQuery(ScriptScreenshotDO.class)
                        .eq(ScriptScreenshotDO::getProjectId, id)
        );
        // 删除项目
        projectMapper.deleteById(id);
    }

    @Override
    public List<ScriptScreenshotDO> uploadScreenshots(Long projectId, MultipartFile[] files) {
        ScriptProjectDO project = getById(projectId);
        if (project == null) {
            throw new ClientException("项目不存在");
        }

        Path screenshotDir = getScreenshotDir(projectId);
        List<ScriptScreenshotDO> result = new ArrayList<>();

        // 获取当前最大排序号
        Long maxOrder = screenshotMapper.selectCount(
                Wrappers.lambdaQuery(ScriptScreenshotDO.class)
                        .eq(ScriptScreenshotDO::getProjectId, projectId)
                        .eq(ScriptScreenshotDO::getDeleted, 0)
        );
        int nextOrder = maxOrder != null ? maxOrder.intValue() : 0;

        for (MultipartFile file : files) {
            String originalName = file.getOriginalFilename();
            if (StrUtil.isBlank(originalName)) {
                continue;
            }

            // 文件夹上传时 originalName 可能带路径前缀（如 "1/20260515_xxx.png"），取纯文件名
            String pureName = originalName.contains("/") ? originalName.substring(originalName.lastIndexOf('/') + 1) : originalName;
            pureName = pureName.contains("\\") ? pureName.substring(pureName.lastIndexOf('\\') + 1) : pureName;

            // 解析文件名元数据
            FilenameParser.Result meta = FilenameParser.parse(pureName);
            int width = 0, height = 0, scalePct = 100;
            String timestampStr = null;
            if (meta != null) {
                width = meta.getWidth();
                height = meta.getHeight();
                scalePct = meta.getScalePct();
                timestampStr = meta.getTimestamp();
            }

            // 保存文件
            String savedName = IdUtil.fastSimpleUUID() + getExtension(originalName);
            Path savedPath = screenshotDir.resolve(savedName).toAbsolutePath();
            try {
                file.transferTo(savedPath);
            } catch (IOException e) {
                log.error("保存截图失败: {}", originalName, e);
                throw new ClientException("保存截图失败: " + e.getMessage());
            }

            String fileUrl = "/script/screenshot/image/" + savedName;

            ScriptScreenshotDO screenshot = ScriptScreenshotDO.builder()
                    .id(IdUtil.getSnowflakeNextId())
                    .projectId(projectId)
                    .fileName(pureName)
                    .filePath("screenshots/" + savedName)
                    .fileUrl(fileUrl)
                    .width(width)
                    .height(height)
                    .scalePct(scalePct)
                    .timestampStr(timestampStr)
                    .sortOrder(nextOrder++)
                    .build();
            screenshotMapper.insert(screenshot);
            result.add(screenshot);
        }

        // 按时间戳排序
        result.sort((a, b) -> {
            long tsA = FilenameParser.parseTimestampOrder(a.getTimestampStr());
            long tsB = FilenameParser.parseTimestampOrder(b.getTimestampStr());
            return Long.compare(tsA, tsB);
        });

        // 更新排序号
        for (int i = 0; i < result.size(); i++) {
            result.get(i).setSortOrder(i);
            screenshotMapper.updateById(result.get(i));
        }

        return result;
    }

    @Override
    public void deleteScreenshot(Long screenshotId) {
        ScriptScreenshotDO screenshot = screenshotMapper.selectById(screenshotId);
        if (screenshot == null) {
            throw new ClientException("截图不存在");
        }
        screenshotMapper.deleteById(screenshotId);
    }

    @Override
    public ScriptScreenshotDO getScreenshot(Long screenshotId) {
        return screenshotMapper.selectOne(
                Wrappers.lambdaQuery(ScriptScreenshotDO.class)
                        .eq(ScriptScreenshotDO::getId, screenshotId)
                        .eq(ScriptScreenshotDO::getDeleted, 0)
        );
    }

    private Path getProjectDir(Long projectId) {
        return Paths.get(basePath, String.valueOf(projectId));
    }

    private Path getScreenshotDir(Long projectId) {
        return getProjectDir(projectId).resolve("screenshots");
    }

    private Path getTemplateDir(Long projectId) {
        return getProjectDir(projectId).resolve("templates");
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : ".png";
    }
}
