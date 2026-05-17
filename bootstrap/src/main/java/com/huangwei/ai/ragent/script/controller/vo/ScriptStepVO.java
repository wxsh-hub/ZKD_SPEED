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

package com.huangwei.ai.ragent.script.controller.vo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huangwei.ai.ragent.script.dao.entity.ScriptStepDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Map;

@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScriptStepVO {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String id;
    private String projectId;
    private String screenshotId;
    private Integer stepOrder;
    private String operationType;
    private Map<String, Object> paramsJson;
    private String templatePath;
    private String templateUrl;
    private Date createTime;

    public static ScriptStepVO from(ScriptStepDO step) {
        Map<String, Object> params = Map.of();
        if (step.getParamsJson() != null && !step.getParamsJson().isBlank()) {
            try {
                params = MAPPER.readValue(step.getParamsJson(), new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("解析 paramsJson 失败: {}", step.getParamsJson(), e);
            }
        }
        return ScriptStepVO.builder()
                .id(step.getId() != null ? step.getId().toString() : null)
                .projectId(step.getProjectId() != null ? step.getProjectId().toString() : null)
                .screenshotId(step.getScreenshotId() != null ? step.getScreenshotId().toString() : null)
                .stepOrder(step.getStepOrder())
                .operationType(step.getOperationType())
                .paramsJson(params)
                .templatePath(step.getTemplatePath())
                .templateUrl(step.getTemplateUrl())
                .createTime(step.getCreateTime())
                .build();
    }
}
