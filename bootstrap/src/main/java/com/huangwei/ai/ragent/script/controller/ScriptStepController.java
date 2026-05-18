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
import com.huangwei.ai.ragent.script.controller.request.CreateStepRequest;
import com.huangwei.ai.ragent.script.controller.request.ExtractTemplateRequest;
import com.huangwei.ai.ragent.script.controller.request.ReorderStepsRequest;
import com.huangwei.ai.ragent.script.controller.request.UpdateStepRequest;
import com.huangwei.ai.ragent.script.dao.entity.ScriptStepDO;
import com.huangwei.ai.ragent.script.service.ScriptGeneratorService;
import com.huangwei.ai.ragent.script.service.ScriptStepService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ScriptStepController {

    private final ScriptStepService scriptStepService;
    private final ScriptGeneratorService scriptGeneratorService;

    @PostMapping("/script/step")
    public Result<ScriptStepDO> create(@RequestBody @Valid CreateStepRequest request) {
        return Results.success(scriptStepService.create(
                request.getProjectId(), request.getScreenshotId(),
                request.getOperationType(), request.getParamsJson()));
    }

    @PutMapping("/script/step/{id}")
    public Result<ScriptStepDO> update(@PathVariable Long id, @RequestBody UpdateStepRequest request) {
        return Results.success(scriptStepService.update(
                id, request.getScreenshotId(), request.getOperationType(), request.getParamsJson()));
    }

    @DeleteMapping("/script/step/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scriptStepService.delete(id);
        return Results.success();
    }

    @GetMapping("/script/step/list/{projectId}")
    public Result<List<ScriptStepDO>> list(@PathVariable Long projectId) {
        return Results.success(scriptStepService.listByProject(projectId));
    }

    @PutMapping("/script/step/reorder")
    public Result<Void> reorder(@RequestBody @Valid ReorderStepsRequest request) {
        scriptStepService.reorder(request.getOrderedIds());
        return Results.success();
    }

    @PostMapping("/script/step/extract-template")
    public Result<ScriptStepDO> extractTemplate(@RequestBody @Valid ExtractTemplateRequest request) {
        return Results.success(scriptStepService.extractTemplate(
                request.getScreenshotId(), request.getProjectId(),
                request.getX1(), request.getY1(), request.getX2(), request.getY2(),
                request.getTimeoutS()));
    }

    @GetMapping("/script/project/{id}/generate")
    public ResponseEntity<String> generateScript(@PathVariable Long id) {
        String script = scriptGeneratorService.generateScript(id);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"script.py\"")
                .body(script);
    }

    @PostMapping("/script/project/{id}/compile")
    public Result<Void> compile(@PathVariable Long id) {
        scriptGeneratorService.startCompilation(id);
        return Results.success();
    }

    @GetMapping("/script/project/{id}/compile-status")
    public Result<Map<String, String>> compileStatus(@PathVariable Long id) {
        String status = scriptGeneratorService.getCompileStatus(id);
        String exePath = scriptGeneratorService.getExePath(id);
        return Results.success(Map.of("status", status, "exePath", exePath != null ? exePath : ""));
    }

    @GetMapping("/script/project/{id}/download-exe")
    public ResponseEntity<Resource> downloadExe(@PathVariable Long id) {
        String exePath = scriptGeneratorService.getExePath(id);
        if (exePath == null) {
            return ResponseEntity.notFound().build();
        }
        File file = new File(exePath);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                .body(new FileSystemResource(file));
    }
}
