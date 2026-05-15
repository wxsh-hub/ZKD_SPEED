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

package com.huangwei.ai.ragent.framework.log;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class SystemLogCleanTask {

    private final SystemLogMapper systemLogMapper;

    private static final int BATCH_SIZE = 5000;
    private static final long MAX_TOTAL_COUNT = 1_000_000;

    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanLogs() {
        log.info("开始清理系统日志");
        int totalDeleted = 0;

        totalDeleted += cleanByLevel("INFO", 7);
        totalDeleted += cleanByLevel("WARN", 14);
        totalDeleted += cleanByLevel("ERROR", 30);

        totalDeleted += cleanOverLimit();

        log.info("系统日志清理完成，共删除 {} 条记录", totalDeleted);
    }

    private int cleanByLevel(String level, int keepDays) {
        String beforeTime = LocalDateTime.now().minusDays(keepDays).toString().replace("T", " ");
        int deleted = 0;

        while (true) {
            int batch = systemLogMapper.deleteByLevelAndTime(level, beforeTime, BATCH_SIZE);
            deleted += batch;
            if (batch < BATCH_SIZE) {
                break;
            }
        }

        if (deleted > 0) {
            log.info("清理 {} 级别日志（保留{}天），删除 {} 条", level, keepDays, deleted);
        }
        return deleted;
    }

    private int cleanOverLimit() {
        Long count = systemLogMapper.selectCount(new LambdaQueryWrapper<>());
        if (count == null || count <= MAX_TOTAL_COUNT) {
            return 0;
        }

        int toDelete = (int) (count - MAX_TOTAL_COUNT);
        int deleted = 0;

        while (deleted < toDelete) {
            int batch = systemLogMapper.deleteOldest(BATCH_SIZE);
            deleted += batch;
            if (batch < BATCH_SIZE) {
                break;
            }
        }

        log.info("总量超限清理，删除 {} 条最旧记录", deleted);
        return deleted;
    }
}
