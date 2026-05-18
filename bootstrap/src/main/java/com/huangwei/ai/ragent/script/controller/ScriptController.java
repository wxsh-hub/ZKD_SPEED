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

package com.huangwei.ai.ragent.script.controller;

import com.huangwei.ai.ragent.framework.convention.Result;
import com.huangwei.ai.ragent.framework.web.Results;
import com.huangwei.ai.ragent.script.controller.request.CreateProjectRequest;
import com.huangwei.ai.ragent.script.controller.request.SaveProjectRequest;
import com.huangwei.ai.ragent.script.controller.vo.ScriptProjectDetailVO;
import com.huangwei.ai.ragent.script.controller.vo.ScriptProjectVO;
import com.huangwei.ai.ragent.script.controller.vo.ScriptScreenshotVO;
import com.huangwei.ai.ragent.script.service.ScriptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptService scriptService;

    @GetMapping("/script/list")
    public Result<List<ScriptProjectVO>> list() {
        return Results.success(scriptService.list());
    }

    @GetMapping("/script/{projectId}")
    public Result<ScriptProjectDetailVO> getDetail(@PathVariable Long projectId) {
        return Results.success(scriptService.getDetail(projectId));
    }

    @PostMapping("/script/create")
    public Result<ScriptProjectVO> create(@RequestBody @Valid CreateProjectRequest request) {
        return Results.success(scriptService.create(request));
    }

    @DeleteMapping("/script/{projectId}")
    public Result<Void> delete(@PathVariable Long projectId) {
        scriptService.delete(projectId);
        return Results.success();
    }

    @PostMapping("/script/{projectId}/save")
    public Result<Void> save(@PathVariable Long projectId, @RequestBody SaveProjectRequest request) {
        scriptService.save(projectId, request);
        return Results.success();
    }

    @PostMapping(value = "/script/{projectId}/screenshot/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ScriptScreenshotVO> uploadScreenshot(
            @PathVariable Long projectId,
            @RequestPart("file") MultipartFile file) {
        return Results.success(scriptService.uploadScreenshot(projectId, file));
    }

    @PostMapping(value = "/script/{projectId}/screenshot/batch-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<ScriptScreenshotVO>> uploadScreenshots(
            @PathVariable Long projectId,
            @RequestPart("files") List<MultipartFile> files) {
        return Results.success(scriptService.uploadScreenshots(projectId, files));
    }

    @GetMapping("/script/{projectId}/preview")
    public Result<String> previewScript(@PathVariable Long projectId) {
        return Results.success(scriptService.previewScript(projectId));
    }

    @PostMapping("/script/{projectId}/build")
    public Result<Map<String, Object>> buildExe(@PathVariable Long projectId) {
        return Results.success(scriptService.buildExe(projectId));
    }

    @GetMapping("/script/{projectId}/build/status")
    public Result<Map<String, Object>> getBuildStatus(@PathVariable Long projectId) {
        return Results.success(scriptService.getBuildStatus(projectId));
    }

    @PostMapping(value = "/script/{projectId}/template/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadTemplate(
            @PathVariable Long projectId,
            @RequestPart("file") MultipartFile file) {
        return Results.success(scriptService.uploadTemplate(projectId, file));
    }

    @PostMapping(value = "/script/token/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ScriptScreenshotVO> uploadByToken(
            @RequestParam("token") String token,
            @RequestPart("file") MultipartFile file) {
        return Results.success(scriptService.uploadByToken(token, file));
    }
}
