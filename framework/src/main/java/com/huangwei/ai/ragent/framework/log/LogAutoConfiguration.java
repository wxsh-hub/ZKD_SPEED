package com.huangwei.ai.ragent.framework.log;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LogAutoConfiguration implements ApplicationRunner {

    private final SystemLogMapper systemLogMapper;

    public LogAutoConfiguration(SystemLogMapper systemLogMapper) {
        this.systemLogMapper = systemLogMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        DatabaseLogAppender.startWorker(systemLogMapper);
        log.info("数据库日志落库 Worker 已启动");
    }
}
