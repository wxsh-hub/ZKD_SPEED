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
