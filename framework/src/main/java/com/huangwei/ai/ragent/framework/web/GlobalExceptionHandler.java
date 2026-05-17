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

package com.huangwei.ai.ragent.framework.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.huangwei.ai.ragent.framework.convention.Result;
import com.huangwei.ai.ragent.framework.errorcode.BaseErrorCode;
import com.huangwei.ai.ragent.framework.exception.AbstractException;
import com.huangwei.ai.ragent.framework.trace.RagTraceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Optional;

/**
 * 全局异常处理器
 * 拦截指定异常并通过优雅构建方式返回前端信息
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 拦截参数验证异常
     */
    @SneakyThrows
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Result<Void> validExceptionHandler(HttpServletRequest request, MethodArgumentNotValidException ex) {
        BindingResult bindingResult = ex.getBindingResult();
        FieldError firstFieldError = CollectionUtil.getFirst(bindingResult.getFieldErrors());
        String exceptionStr = Optional.ofNullable(firstFieldError)
                .map(FieldError::getDefaultMessage)
                .orElse(StrUtil.EMPTY);
        String traceId = RagTraceContext.getTraceId();
        log.error("[{}] {} traceId={} [ex] {}", request.getMethod(), getUrl(request), traceId, exceptionStr);
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), exceptionStr, traceId);
    }

    /**
     * 拦截应用内抛出的异常
     */
    @ExceptionHandler(value = {AbstractException.class})
    public Result<Void> abstractException(HttpServletRequest request, AbstractException ex) {
        String traceId = RagTraceContext.getTraceId();
        if (ex.getCause() != null) {
            log.error("[{}] {} traceId={} [ex] {}", request.getMethod(), getUrl(request), traceId, ex.getMessage(), ex.getCause());
            return Results.failure(ex, traceId);
        }
        StringBuilder stackTraceBuilder = new StringBuilder();
        stackTraceBuilder.append(ex.getClass().getName()).append(": ").append(ex.getErrorMessage()).append("\n");
        StackTraceElement[] stackTrace = ex.getStackTrace();
        for (int i = 0; i < Math.min(5, stackTrace.length); i++) {
            stackTraceBuilder.append("\tat ").append(stackTrace[i]).append("\n");
        }
        log.error("[{}] {} traceId={} [ex] {}\n{}", request.getMethod(), getUrl(request), traceId, ex.getMessage(), stackTraceBuilder);
        return Results.failure(ex, traceId);
    }

    /**
     * 拦截未登录异常
     */
    @ExceptionHandler(value = NotLoginException.class)
    public Result<Void> notLoginException(HttpServletRequest request, NotLoginException ex) {
        String traceId = RagTraceContext.getTraceId();
        log.warn("[{}] {} traceId={} [auth] not-login: {}", request.getMethod(), getUrl(request), traceId, ex.getMessage());
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), "未登录或登录已过期", traceId);
    }

    /**
     * 拦截无角色权限异常
     */
    @ExceptionHandler(value = NotRoleException.class)
    public Result<Void> notRoleException(HttpServletRequest request, NotRoleException ex) {
        String traceId = RagTraceContext.getTraceId();
        log.warn("[{}] {} traceId={} [auth] no-role: {}", request.getMethod(), getUrl(request), traceId, ex.getMessage());
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), "权限不足", traceId);
    }

    /**
     * 拦截业务异常（RuntimeException 子类，如 ScriptBizException）
     * 返回具体错误信息给前端，而不是通用的"系统出错"
     */
    @ExceptionHandler(value = RuntimeException.class)
    public Result<Void> runtimeExceptionHandler(HttpServletRequest request, RuntimeException ex) {
        String traceId = RagTraceContext.getTraceId();
        log.error("[{}] {} traceId={} [biz] {}", request.getMethod(), getUrl(request), traceId, ex.getMessage(), ex);
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), ex.getMessage(), traceId);
    }

    /**
     * 拦截未捕获异常
     */
    @ExceptionHandler(value = Throwable.class)
    public Result<Void> defaultErrorHandler(HttpServletRequest request, Throwable throwable) {
        String traceId = RagTraceContext.getTraceId();
        log.error("[{}] {} traceId={}", request.getMethod(), getUrl(request), traceId, throwable);
        return Results.failure(traceId);
    }

    private String getUrl(HttpServletRequest request) {
        if (StrUtil.isBlank(request.getQueryString())) {
            return request.getRequestURL().toString();
        }
        return request.getRequestURL().toString() + "?" + request.getQueryString();
    }
}
