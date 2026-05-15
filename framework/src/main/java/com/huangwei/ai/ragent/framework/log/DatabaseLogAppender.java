package com.huangwei.ai.ragent.framework.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.huangwei.ai.ragent.framework.context.UserContext;
import com.huangwei.ai.ragent.framework.trace.RagTraceContext;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class DatabaseLogAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static final BlockingQueue<SystemLogDO> QUEUE = new ArrayBlockingQueue<>(4096);
    private static volatile boolean workerStarted = false;

    public static void startWorker(SystemLogMapper logMapper) {
        if (workerStarted) {
            return;
        }
        synchronized (DatabaseLogAppender.class) {
            if (workerStarted) {
                return;
            }
            workerStarted = true;
            Thread worker = new Thread(() -> {
                while (true) {
                    try {
                        SystemLogDO log = QUEUE.take();
                        try {
                            logMapper.insert(log);
                        } catch (Exception e) {
                            // 避免日志写入失败导致循环
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, "db-log-writer");
            worker.setDaemon(true);
            worker.start();
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!isStarted()) {
            return;
        }

        SystemLogDO log = SystemLogDO.builder()
                .traceId(RagTraceContext.getTraceId())
                .requestId(RagTraceContext.getTraceId())
                .requestMethod(getMdcValue(event, "requestMethod"))
                .requestUrl(getMdcValue(event, "requestUrl"))
                .requestIp(getMdcValue(event, "requestIp"))
                .userId(getUserId())
                .level(event.getLevel().toString())
                .loggerName(event.getLoggerName())
                .message(event.getFormattedMessage())
                .exceptionStack(getExceptionStack(event))
                .durationMs(getDuration(event))
                .createTime(toLocalDateTime(event.getTimeStamp()))
                .build();

        // 队列满了直接丢弃，不影响主业务
        QUEUE.offer(log);
    }

    private String getMdcValue(ILoggingEvent event, String key) {
        return event.getMDCPropertyMap().get(key);
    }

    private String getUserId() {
        try {
            return UserContext.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private Long getDuration(ILoggingEvent event) {
        String duration = event.getMDCPropertyMap().get("duration");
        if (duration != null) {
            try {
                return Long.parseLong(duration);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String getExceptionStack(ILoggingEvent event) {
        IThrowableProxy proxy = event.getThrowableProxy();
        if (proxy == null) {
            return null;
        }
        return ThrowableProxyUtil.asString(proxy);
    }

    private LocalDateTime toLocalDateTime(long timestamp) {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
