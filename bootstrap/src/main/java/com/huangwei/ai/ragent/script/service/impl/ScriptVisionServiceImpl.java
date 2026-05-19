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

package com.huangwei.ai.ragent.script.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.huangwei.ai.ragent.script.dao.entity.ScriptProjectDO;
import com.huangwei.ai.ragent.script.dao.mapper.ScriptProjectMapper;
import com.huangwei.ai.ragent.script.service.ScriptBizException;
import com.huangwei.ai.ragent.script.service.ScriptVisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptVisionServiceImpl implements ScriptVisionService {

    private final ScriptProjectMapper projectMapper;

    @Value("${ai.providers.bailian.api-key:}")
    private String apiKey;

    @Value("${ai.providers.bailian.url:https://dashscope.aliyuncs.com}")
    private String apiUrl;

    private static final String VISION_ENDPOINT = "/compatible-mode/v1/chat/completions";
    private static final String MODEL = "qwen-vl-max";
    private static final Gson GSON = new Gson();

    @Override
    public boolean analyze(MultipartFile image, String prompt, String token) {
        // 1. 校验 token（暂时跳过）
        // TODO: 后续恢复 token 校验

        // 2. 图片转 base64
        String base64Image;
        try {
            base64Image = Base64.getEncoder().encodeToString(image.getBytes());
        } catch (IOException e) {
            throw new ScriptBizException("读取图片失败: " + e.getMessage());
        }

        // 3. 构建请求体（OpenAI 兼容多模态格式）
        String systemPrompt = "你是一个屏幕截图分析助手。用户会给你一张截图和一个判断条件，你需要判断截图是否满足条件。只回答\"是\"或\"不是\"，不要输出其他内容。";

        JsonArray contentArray = new JsonArray();

        JsonObject imagePart = new JsonObject();
        imagePart.addProperty("type", "image_url");
        JsonObject imageUrl = new JsonObject();
        String contentType = image.getContentType() != null ? image.getContentType() : "image/png";
        imageUrl.addProperty("url", "data:" + contentType + ";base64," + base64Image);
        imagePart.add("image_url", imageUrl);
        contentArray.add(imagePart);

        JsonObject textPart = new JsonObject();
        textPart.addProperty("type", "text");
        textPart.addProperty("text", "请根据截图判断：" + prompt);
        contentArray.add(textPart);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.add("content", contentArray);

        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);

        JsonArray messages = new JsonArray();
        messages.add(systemMessage);
        messages.add(userMessage);

        JsonObject reqBody = new JsonObject();
        reqBody.addProperty("model", MODEL);
        reqBody.add("messages", messages);
        reqBody.addProperty("max_tokens", 50);

        // 4. 发送请求
        String url = apiUrl + VISION_ENDPOINT;
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        Request httpRequest = new Request.Builder()
                .url(url)
                .post(RequestBody.create(reqBody.toString(), MediaType.parse("application/json; charset=utf-8")))
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        log.info("AI 识别请求: prompt={}", prompt);

        try (Response response = client.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                log.error("AI 识别 API 失败: code={}, body={}", response.code(), errBody);
                throw new ScriptBizException("AI 识别 API 调用失败: HTTP " + response.code());
            }

            String respBody = response.body().string();
            JsonObject respJson = JsonParser.parseString(respBody).getAsJsonObject();
            String content = respJson
                    .getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString()
                    .trim();

            log.info("AI 识别结果: content='{}'", content);

            // 5. 解析结果
            return content.contains("是") && !content.contains("不是");

        } catch (IOException e) {
            log.error("AI 识别网络错误", e);
            throw new ScriptBizException("AI 识别网络错误: " + e.getMessage());
        }
    }
}
