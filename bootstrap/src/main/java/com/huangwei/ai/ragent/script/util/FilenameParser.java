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

package com.huangwei.ai.ragent.script.util;

import lombok.Data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析截图工具生成的文件名
 * 格式: {timestamp}_{w}x{h}_{scale_pct}_{rand}.{ext}
 * 示例: 20260515_143022_1920x1080_150pct_a3bx.png
 */
public class FilenameParser {

    private static final Pattern PATTERN = Pattern.compile(
            "^(\\d{8}_\\d{6})_(\\d+)x(\\d+)_(\\d+)pct_[a-zA-Z0-9]+\\..+$"
    );

    @Data
    public static class Result {
        private String timestamp;
        private int width;
        private int height;
        private int scalePct;
    }

    public static Result parse(String fileName) {
        if (fileName == null) {
            return null;
        }
        Matcher matcher = PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            return null;
        }
        Result result = new Result();
        result.setTimestamp(matcher.group(1));
        result.setWidth(Integer.parseInt(matcher.group(2)));
        result.setHeight(Integer.parseInt(matcher.group(3)));
        result.setScalePct(Integer.parseInt(matcher.group(4)));
        return result;
    }

    /**
     * 从时间戳字符串解析排序用的数值（yyyyMMddHHmmss）
     */
    public static long parseTimestampOrder(String timestamp) {
        if (timestamp == null) {
            return 0;
        }
        try {
            return Long.parseLong(timestamp.replace("_", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
