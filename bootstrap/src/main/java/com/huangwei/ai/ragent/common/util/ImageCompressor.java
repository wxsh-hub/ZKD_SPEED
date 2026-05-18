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

import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * 图片压缩工具：读取上传文件，压缩为 JPEG 并返回宽高和字节数据。
 * 一次解码同时获取尺寸和压缩结果，避免重复 IO。
 */
public final class ImageCompressor {

    private static final float JPEG_QUALITY = 0.8f;

    private ImageCompressor() {
    }

    /**
     * 压缩图片并获取宽高（一次解码）。
     *
     * @param file 上传的图片文件
     * @return 包含宽高和压缩后 JPEG 字节的结果
     */
    public static Result compressWithDimensions(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IllegalArgumentException("无法解析图片: " + file.getOriginalFilename());
            }

            int width = image.getWidth();
            int height = image.getHeight();
            byte[] data = compressToJpeg(image);

            return new Result(width, height, data);
        } catch (IOException e) {
            throw new IllegalStateException("图片读取失败: " + file.getOriginalFilename(), e);
        }
    }

    private static byte[] compressToJpeg(BufferedImage image) throws IOException {
        // 确保输出为 RGB（JPEG 不支持 alpha 通道）
        BufferedImage rgb = image;
        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            rgb.getGraphics().drawImage(image, 0, 0, null);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("没有可用的 JPEG 编码器");
        }

        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);

            writer.setOutput(new MemoryCacheImageOutputStream(out));
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }

        return out.toByteArray();
    }

    public record Result(int width, int height, byte[] data) {
    }
}
