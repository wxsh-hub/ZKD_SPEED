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
