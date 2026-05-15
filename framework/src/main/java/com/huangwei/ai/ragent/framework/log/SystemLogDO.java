package com.huangwei.ai.ragent.framework.log;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_system_log")
public class SystemLogDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String traceId;
    private String requestId;
    private String requestMethod;
    private String requestUrl;
    private String requestIp;
    private String userId;
    private String level;
    private String loggerName;
    private String message;
    private String exceptionStack;
    private Long durationMs;
    private LocalDateTime createTime;
}
