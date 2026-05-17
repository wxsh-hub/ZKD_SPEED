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

package com.huangwei.ai.ragent.common.util;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

@Slf4j
@Component
public class OssUtil {

    private final COSClient cosClient;
    private final String bucketName;
    private final String region;

    public OssUtil(@Value("${cos.region}") String region,
                   @Value("${cos.secret-id}") String secretId,
                   @Value("${cos.secret-key}") String secretKey,
                   @Value("${cos.bucket-name}") String bucketName) {
        this.region = region;
        this.bucketName = bucketName;
        COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig config = new ClientConfig(new Region(region));
        config.setHttpProtocol(HttpProtocol.https);
        this.cosClient = new COSClient(credentials, config);
    }

    @PreDestroy
    public void destroy() {
        if (cosClient != null) {
            cosClient.shutdown();
        }
    }

    /**
     * 上传字节数组，返回公开访问 URL
     */
    public String upload(String key, byte[] data, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(data.length);
        if (contentType != null) {
            metadata.setContentType(contentType);
        }
        PutObjectRequest request = new PutObjectRequest(bucketName, key,
                new ByteArrayInputStream(data), metadata);
        cosClient.putObject(request);
        return buildUrl(key);
    }

    /**
     * 上传文件（压缩后的图片），返回公开访问 URL
     */
    public String uploadBytes(String key, byte[] data, String contentType) {
        return upload(key, data, contentType);
    }

    /**
     * 上传本地文件（EXE），返回公开访问 URL
     */
    public String uploadFile(String key, File file) {
        PutObjectRequest request = new PutObjectRequest(bucketName, key, file);
        cosClient.putObject(request);
        return buildUrl(key);
    }

    /**
     * 从 URL 下载文件，返回输入流（调用方需自行关闭）
     */
    public InputStream download(String url) {
        String key = extractKey(url);
        GetObjectRequest request = new GetObjectRequest(bucketName, key);
        COSObject obj = cosClient.getObject(request);
        return obj.getObjectContent();
    }

    /**
     * 从 URL 中删除文件
     */
    public void deleteByUrl(String url) {
        if (url == null || url.isBlank()) return;
        String key = extractKey(url);
        if (key == null) return;
        try {
            cosClient.deleteObject(bucketName, key);
            log.info("已从 COS 删除: {}", key);
        } catch (Exception e) {
            log.warn("COS 删除失败: {}", url, e);
        }
    }

    /**
     * 生成唯一的 object key
     */
    public String generateKey(String prefix, String filename) {
        String ext = "";
        int dot = filename.lastIndexOf('.');
        if (dot >= 0) {
            ext = filename.substring(dot);
        }
        return prefix + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
    }

    private String buildUrl(String key) {
        return "https://" + bucketName + ".cos." + region + ".myqcloud.com/" + key;
    }

    private String extractKey(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (path != null && path.startsWith("/")) {
                path = path.substring(1);
            }
            return path;
        } catch (Exception e) {
            log.warn("无法从 URL 提取 key: {}", url, e);
            return null;
        }
    }
}
