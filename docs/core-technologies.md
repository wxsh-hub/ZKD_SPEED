# 项目核心技术文档

## 1. 后端代理实现图片存储访问

### 1.1 问题背景

项目使用 RustFS（兼容 S3 协议）存储文件。`FileStorageService` 上传文件后返回的 URL 格式为：

```
s3://script/65ecd0a121dd481a97028edb07f75bb8.png
```

`s3://` 是 S3 内部协议，**浏览器无法直接访问**。前端 `<img src="s3://...">` 会触发 CORS 错误：

```
Access to image at 's3://script/...' from origin 'http://localhost:5173'
has been blocked by CORS policy: Cross origin requests are only supported
for protocol schemes: chrome, chrome-extension, data, http, https
```

### 1.2 解决方案：后端代理接口

新增一个 HTTP 代理端点，前端将 `s3://` URL 传给后端，后端从 S3 读取图片并以 HTTP 响应流式返回给浏览器。

**请求流程：**

```
浏览器                        后端 (Spring Boot)                RustFS (S3)
  │                               │                               │
  │  GET /files/image             │                               │
  │  ?url=s3://script/uuid.png    │                               │
  │ ─────────────────────────────>│                               │
  │                               │  HeadObject (获取 content-type)│
  │                               │ ─────────────────────────────>│
  │                               │<───────────────────────────── │
  │                               │                               │
  │                               │  GetObject (读取图片流)        │
  │                               │ ─────────────────────────────>│
  │                               │<─── InputStream ───────────── │
  │                               │                               │
  │<─── image/png (二进制流) ─────│                               │
  │                               │                               │
```

### 1.3 后端实现

**文件：** `bootstrap/src/main/java/com/huangwei/ai/ragent/script/controller/FileProxyController.java`

```java
@Slf4j
@RestController
@RequiredArgsConstructor
public class FileProxyController {

    private final S3Client s3Client;

    @GetMapping("/files/image")
    public void proxyImage(@RequestParam String url, HttpServletResponse response) {
        // 1. 解析 s3:// URL，提取 bucket 和 key
        S3Location loc = parseS3Url(url);

        // 2. 查询对象元数据，获取 content-type
        var head = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(loc.bucket).key(loc.key).build());
        String contentType = head.contentType();

        // 3. 设置 HTTP 响应头
        response.setContentType(contentType);
        response.setHeader("Cache-Control", "public, max-age=86400");

        // 4. 从 S3 读取图片流，直接写入 HTTP 响应
        try (InputStream is = s3Client.getObject(...)) {
            OutputStream os = response.getOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                os.write(buf, 0, len);
            }
        }
    }
}
```

**关键设计决策：**

| 决策 | 选择 | 原因 |
|------|------|------|
| URL 传参方式 | Query Parameter (`?url=s3://...`) | 前端直接拼接，无需额外路由匹配 |
| Content-Type 获取 | 先查 S3 元数据，fallback 到文件扩展名 | S3 上传时已存 content-type，扩展名兜底 |
| 缓存策略 | `Cache-Control: public, max-age=86400` | 截图不会变，缓存 24 小时减少 S3 请求 |
| 流式传输 | 8KB buffer 边读边写 | 避免大图片撑爆 JVM 内存 |
| 错误处理 | NoSuchKey → 404，其他 → 500 | 图片被删返回 404，不暴露内部错误 |

### 1.4 前端适配

**文件：** `frontend/src/services/scriptService.ts`

```typescript
/**
 * 将 s3:// URL 转为后端代理 HTTP URL
 * s3://script/uuid.png → /api/ragent/files/image?url=s3://script/uuid.png
 */
export function toFileUrl(s3Url: string | null | undefined): string {
  if (!s3Url) return "";
  if (s3Url.startsWith("s3://")) {
    return `/api/ragent/files/image?url=${encodeURIComponent(s3Url)}`;
  }
  return s3Url;
}
```

**使用位置（ScriptDetailPage.tsx）：**

```tsx
// 截图列表缩略图
<img src={toFileUrl(s.fileUrl)} alt={s.fileName} />

// Canvas 标注画布
img.src = toFileUrl(screenshot.fileUrl);
```

### 1.5 完整调用链路

```
用户上传截图
  → ScriptController.uploadScreenshot()
  → ScriptServiceImpl: fileStorageService.upload("script", file)
  → LocalFileStorageServiceImpl: s3Client.putObject() → 返回 "s3://script/uuid.png"
  → 存入数据库 t_script_screenshot.file_url = "s3://script/uuid.png"

用户查看截图
  → 前端请求 GET /script/{projectId} 获取项目详情
  → 后端返回 fileUrl: "s3://script/uuid.png"
  → 前端 toFileUrl() 转换为 "/api/ragent/files/image?url=s3%3A%2F%2Fscript%2Fuuid.png"
  → 浏览器请求该 HTTP URL
  → FileProxyController 从 S3 读取图片流，返回 image/png 响应
  → 浏览器正常显示图片
```

### 1.6 性能与扩展

- **当前阶段**（个人项目）：后端代理完全够用，流量小，无需额外基础设施
- **后期扩展**（上线部署）：可替换为阿里云 OSS，图片通过 OSS CDN 域名直接访问，不经过后端。只需修改 `FileStorageService` 实现类，前端 `toFileUrl()` 加一个判断即可
