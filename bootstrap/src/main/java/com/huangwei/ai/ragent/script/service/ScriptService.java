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

import com.huangwei.ai.ragent.script.controller.request.CreateProjectRequest;
import com.huangwei.ai.ragent.script.controller.request.SaveProjectRequest;
import com.huangwei.ai.ragent.script.controller.vo.ScriptProjectDetailVO;
import com.huangwei.ai.ragent.script.controller.vo.ScriptProjectVO;
import com.huangwei.ai.ragent.script.controller.vo.ScriptScreenshotVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface ScriptService {

    List<ScriptProjectVO> list();

    ScriptProjectDetailVO getDetail(Long projectId);

    ScriptProjectVO create(CreateProjectRequest request);

    void delete(Long projectId);

    void save(Long projectId, SaveProjectRequest request);

    ScriptScreenshotVO uploadScreenshot(Long projectId, MultipartFile file);

    List<ScriptScreenshotVO> uploadScreenshots(Long projectId, List<MultipartFile> files);

    String previewScript(Long projectId);

    Map<String, Object> buildExe(Long projectId, String mode);

    Map<String, Object> getBuildStatus(Long projectId);

    String uploadTemplate(Long projectId, MultipartFile file);

    ScriptScreenshotVO uploadByToken(String token, MultipartFile file);

    Map<String, Object> exportProject(Long projectId);

    ScriptProjectVO importProject(Map<String, Object> data);

    ScriptProjectVO aiGenerate(String prompt, String name);
}
