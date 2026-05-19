package com.huangwei.ai.ragent.script.service;

import org.springframework.web.multipart.MultipartFile;

public interface ScriptVisionService {

    /**
     * AI 图像识别：截取屏幕区域，调用视觉模型判断是否满足条件
     *
     * @param image 截图（JPEG）
     * @param prompt 判断语句，如 "屏幕上是否有登录按钮"
     * @param token 项目 uploadToken
     * @return true=是，false=不是
     */
    boolean analyze(MultipartFile image, String prompt, String token);
}
