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
import com.huangwei.ai.ragent.script.controller.request.UpdateProjectRequest;
import com.huangwei.ai.ragent.script.dao.entity.ScriptProjectDO;
import com.huangwei.ai.ragent.script.dao.entity.ScriptScreenshotDO;
import com.huangwei.ai.ragent.script.service.ScriptProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ScriptProjectController {

    private final ScriptProjectService scriptProjectService;

    @Value("${script.storage.base-path:./data/script-projects}")
    private String basePath;

    @PostMapping("/script/project")
    public Result<ScriptProjectDO> create(@RequestBody @Valid CreateProjectRequest request) {
        return Results.success(scriptProjectService.create(request.getName(), request.getDescription()));
    }

    @GetMapping("/script/project/list")
    public Result<List<ScriptProjectDO>> list() {
        return Results.success(scriptProjectService.listAll());
    }

    @GetMapping("/script/project/{id}")
    public Result<Map<String, Object>> getDetail(@PathVariable Long id) {
        return Results.success(scriptProjectService.getDetail(id));
    }

    @PutMapping("/script/project/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody UpdateProjectRequest request) {
        scriptProjectService.update(id, request.getName(), request.getDescription(),
                request.getTargetWidth(), request.getTargetHeight());
        return Results.success();
    }

    @DeleteMapping("/script/project/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scriptProjectService.delete(id);
        return Results.success();
    }

    @PostMapping(value = "/script/project/{id}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<ScriptScreenshotDO>> uploadScreenshots(
            @PathVariable Long id,
            @RequestPart("files") MultipartFile[] files) {
        return Results.success(scriptProjectService.uploadScreenshots(id, files));
    }

    @DeleteMapping("/script/screenshot/{id}")
    public Result<Void> deleteScreenshot(@PathVariable Long id) {
        scriptProjectService.deleteScreenshot(id);
        return Results.success();
    }

    @GetMapping("/script/screenshot/image/{fileName}")
    public ResponseEntity<Resource> serveScreenshot(@PathVariable String fileName) {
        // 在所有项目目录中查找文件
        File baseDir = new File(basePath);
        if (baseDir.exists()) {
            for (File projectDir : baseDir.listFiles()) {
                if (projectDir.isDirectory()) {
                    File imgFile = Paths.get(projectDir.getAbsolutePath(), "screenshots", fileName).toFile();
                    if (imgFile.exists()) {
                        return ResponseEntity.ok()
                                .contentType(MediaType.IMAGE_PNG)
                                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                                .body(new FileSystemResource(imgFile));
                    }
                }
            }
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/script/template/image/{fileName}")
    public ResponseEntity<Resource> serveTemplate(@PathVariable String fileName) {
        File baseDir = new File(basePath);
        if (baseDir.exists()) {
            for (File projectDir : baseDir.listFiles()) {
                if (projectDir.isDirectory()) {
                    File tplFile = Paths.get(projectDir.getAbsolutePath(), "templates", fileName).toFile();
                    if (tplFile.exists()) {
                        return ResponseEntity.ok()
                                .contentType(MediaType.IMAGE_PNG)
                                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                                .body(new FileSystemResource(tplFile));
                    }
                }
            }
        }
        return ResponseEntity.notFound().build();
    }
}
