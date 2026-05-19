package com.huangwei.ai.ragent.script.controller;

import com.huangwei.ai.ragent.framework.convention.Result;
import com.huangwei.ai.ragent.framework.web.Results;
import com.huangwei.ai.ragent.script.service.ScriptVisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/script/vision")
@RequiredArgsConstructor
public class ScriptVisionController {

    private final ScriptVisionService scriptVisionService;

    /**
     * AI 图像识别接口（供脚本调用，不需要登录态）
     */
    @PostMapping("/analyze")
    public Result<Map<String, Boolean>> analyze(
            @RequestParam("image") MultipartFile image,
            @RequestParam("prompt") String prompt,
            @RequestParam(value = "token", required = false) String token) {
        boolean result = scriptVisionService.analyze(image, prompt, token);
        return Results.success(Map.of("result", result));
    }
}
