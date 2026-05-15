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

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SystemLogMapper extends BaseMapper<SystemLogDO> {

    /**
     * 分批删除指定级别、指定时间之前的日志
     */
    @Delete("DELETE FROM t_system_log WHERE level = #{level} AND create_time < #{beforeTime} LIMIT #{batchSize}")
    int deleteByLevelAndTime(@Param("level") String level,
                             @Param("beforeTime") String beforeTime,
                             @Param("batchSize") int batchSize);

    /**
     * 分批删除最旧的日志（总量超限时使用）
     */
    @Delete("DELETE FROM t_system_log ORDER BY create_time ASC LIMIT #{batchSize}")
    int deleteOldest(@Param("batchSize") int batchSize);
}
