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
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.huangwei.ai.ragent.framework.exception.ClientException;
import com.huangwei.ai.ragent.script.dao.entity.ScriptScreenshotDO;
import com.huangwei.ai.ragent.script.dao.entity.ScriptStepDO;
import com.huangwei.ai.ragent.script.dao.mapper.ScriptScreenshotMapper;
import com.huangwei.ai.ragent.script.dao.mapper.ScriptStepMapper;
import com.huangwei.ai.ragent.script.service.ScriptStepService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptStepServiceImpl implements ScriptStepService {

    private final ScriptStepMapper stepMapper;
    private final ScriptScreenshotMapper screenshotMapper;

    @Value("${script.storage.base-path:./data/script-projects}")
    private String basePath;

    @Override
    public ScriptStepDO create(Long projectId, Long screenshotId, String operationType, String paramsJson) {
        // 计算新的 step_order
        Long count = stepMapper.selectCount(
                Wrappers.lambdaQuery(ScriptStepDO.class)
                        .eq(ScriptStepDO::getProjectId, projectId)
                        .eq(ScriptStepDO::getDeleted, 0)
        );

        ScriptStepDO step = ScriptStepDO.builder()
                .id(IdUtil.getSnowflakeNextId())
                .projectId(projectId)
                .screenshotId(screenshotId)
                .stepOrder(count != null ? count.intValue() : 0)
                .operationType(operationType)
                .paramsJson(paramsJson)
                .build();
        stepMapper.insert(step);
        return step;
    }

    @Override
    public ScriptStepDO update(Long id, Long screenshotId, String operationType, String paramsJson) {
        ScriptStepDO step = stepMapper.selectById(id);
        if (step == null) {
            throw new ClientException("步骤不存在");
        }
        if (screenshotId != null) {
            step.setScreenshotId(screenshotId);
        }
        if (operationType != null) {
            step.setOperationType(operationType);
        }
        if (paramsJson != null) {
            step.setParamsJson(paramsJson);
        }
        stepMapper.updateById(step);
        return step;
    }

    @Override
    public void delete(Long id) {
        ScriptStepDO step = stepMapper.selectById(id);
        if (step == null) {
            throw new ClientException("步骤不存在");
        }
        stepMapper.deleteById(id);

        // 重新排序剩余步骤
        List<ScriptStepDO> remaining = stepMapper.selectList(
                Wrappers.lambdaQuery(ScriptStepDO.class)
                        .eq(ScriptStepDO::getProjectId, step.getProjectId())
                        .eq(ScriptStepDO::getDeleted, 0)
                        .orderByAsc(ScriptStepDO::getStepOrder)
        );
        for (int i = 0; i < remaining.size(); i++) {
            if (remaining.get(i).getStepOrder() != i) {
                remaining.get(i).setStepOrder(i);
                stepMapper.updateById(remaining.get(i));
            }
        }
    }

    @Override
    public List<ScriptStepDO> listByProject(Long projectId) {
        return stepMapper.selectList(
                Wrappers.lambdaQuery(ScriptStepDO.class)
                        .eq(ScriptStepDO::getProjectId, projectId)
                        .eq(ScriptStepDO::getDeleted, 0)
                        .orderByAsc(ScriptStepDO::getStepOrder)
        );
    }

    @Override
    public void reorder(List<Long> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            ScriptStepDO step = stepMapper.selectById(orderedIds.get(i));
            if (step != null && step.getStepOrder() != i) {
                step.setStepOrder(i);
                stepMapper.updateById(step);
            }
        }
    }

    @Override
    public ScriptStepDO extractTemplate(Long screenshotId, Long projectId, int x1, int y1, int x2, int y2, int timeoutS) {
        ScriptScreenshotDO screenshot = screenshotMapper.selectById(screenshotId);
        if (screenshot == null) {
            throw new ClientException("截图不存在");
        }

        // 确保坐标正确
        int left = Math.min(x1, x2);
        int top = Math.min(y1, y2);
        int right = Math.max(x1, x2);
        int bottom = Math.max(y1, y2);

        // 读取原图并裁剪
        Path screenshotPath = Paths.get(basePath, String.valueOf(projectId), screenshot.getFilePath());
        File sourceFile = screenshotPath.toFile();
        if (!sourceFile.exists()) {
            throw new ClientException("截图文件不存在");
        }

        String templateName = "tpl_" + IdUtil.fastSimpleUUID() + ".png";
        Path templateDir = Paths.get(basePath, String.valueOf(projectId), "templates");
        Path templatePath = templateDir.resolve(templateName);

        try {
            BufferedImage source = ImageIO.read(sourceFile);
            int cropX = Math.max(0, left);
            int cropY = Math.max(0, top);
            int cropW = Math.min(right, source.getWidth()) - cropX;
            int cropH = Math.min(bottom, source.getHeight()) - cropY;

            if (cropW <= 0 || cropH <= 0) {
                throw new ClientException("裁剪区域无效");
            }

            BufferedImage cropped = source.getSubimage(cropX, cropY, cropW, cropH);
            ImageIO.write(cropped, "png", templatePath.toFile());
        } catch (IOException e) {
            log.error("裁剪模板图片失败", e);
            throw new ClientException("裁剪模板图片失败: " + e.getMessage());
        }

        String templateUrl = "/api/ragent/script/template/image/" + templateName;
        String relTemplatePath = "templates/" + templateName;

        // 创建 wait_image 步骤
        String paramsJson = String.format(
                "{\"x1\":%d,\"y1\":%d,\"x2\":%d,\"y2\":%d,\"timeout_s\":%d}",
                left, top, right, bottom, timeoutS
        );

        ScriptStepDO step = create(projectId, screenshotId, "wait_image", paramsJson);
        step.setTemplatePath(relTemplatePath);
        step.setTemplateUrl(templateUrl);
        stepMapper.updateById(step);

        return step;
    }
}
