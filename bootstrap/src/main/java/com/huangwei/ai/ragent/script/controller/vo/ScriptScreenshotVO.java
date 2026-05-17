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

import com.huangwei.ai.ragent.script.dao.entity.ScriptScreenshotDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScriptScreenshotVO {

    private String id;
    private String projectId;
    private String fileName;
    private String filePath;
    private String fileUrl;
    private Integer width;
    private Integer height;
    private Integer scalePct;
    private String timestampStr;
    private Integer sortOrder;
    private Date createTime;

    public static ScriptScreenshotVO from(ScriptScreenshotDO screenshot) {
        return ScriptScreenshotVO.builder()
                .id(screenshot.getId() != null ? screenshot.getId().toString() : null)
                .projectId(screenshot.getProjectId() != null ? screenshot.getProjectId().toString() : null)
                .fileName(screenshot.getFileName())
                .filePath(screenshot.getFilePath())
                .fileUrl(screenshot.getFileUrl())
                .width(screenshot.getWidth())
                .height(screenshot.getHeight())
                .scalePct(screenshot.getScalePct())
                .timestampStr(screenshot.getTimestampStr())
                .sortOrder(screenshot.getSortOrder())
                .createTime(screenshot.getCreateTime())
                .build();
    }
}
