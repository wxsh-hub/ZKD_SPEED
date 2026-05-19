# AI 图像识别条件（if_ai）实现方案

## 背景

当前脚本的 `if_image` 条件只支持像素级模板匹配，无法理解屏幕内容语义。新增 `if_ai` 操作：脚本运行时截取屏幕指定区域，发送到后端服务器，后端调用 qwen-vl-max 视觉模型判断是否满足条件，返回是/否给脚本。

## 整体流程

```
用户标注区域 + 输入判断语句
        ↓
打包时注入：API 地址（读配置）+ uploadToken
        ↓
生成 Python 脚本（含 ai_recognize 函数）
        ↓
运行时：截图 → base64 → POST 到后端
        ↓
后端：校验 token → 调用 qwen-vl-max → 返回 true/false
        ↓
脚本：if true → 执行 if 块，else → 跳过
```

## API 地址配置（关键：支持服务器部署）

脚本中 `SCRIPT_API_BASE` 不硬编码，**打包时从 application.yaml 读取**：

```yaml
# application.yaml
script:
  storage:
    base-path: ./data/script-projects
  api-base-url: http://localhost:9090/api/ragent   # 本地开发
  # api-base-url: http://1.14.204.171/api/ragent   # 生产服务器
```

打包时通过 `@Value("${script.api-base-url}")` 注入，写入生成的 Python 脚本。

部署到不同服务器时只需修改此配置项，无需改代码。

## 需要修改/新增的文件

### 后端（新增 3 个文件）

| 文件 | 说明 |
|------|------|
| `bootstrap/.../script/controller/ScriptVisionController.java` | **新建**：公开 API，供脚本调用 |
| `bootstrap/.../script/service/ScriptVisionService.java` | **新建**：接口 |
| `bootstrap/.../script/service/impl/ScriptVisionServiceImpl.java` | **新建**：调用 qwen-vl-max |

### 后端（修改 3 个文件）

| 文件 | 说明 |
|------|------|
| `bootstrap/.../script/service/ScriptCodeGenerator.java` | 生成 `if_ai` Python 代码 + `ai_recognize` 函数 |
| `bootstrap/.../script/service/impl/ScriptServiceImpl.java` | 打包时注入 API 地址和 Token |
| `bootstrap/src/main/resources/application.yaml` | 添加 `script.api-base-url` 和 vision 模型配置 |

### 前端（修改 2 个文件）

| 文件 | 说明 |
|------|------|
| `frontend/src/services/scriptService.ts` | 新增 `if_ai` 类型、标签、颜色、提示 |
| `frontend/src/pages/ScriptDetailPage.tsx` | `if_ai` 步骤编辑面板（区域坐标 + 判断语句） |

---

## 详细设计

### 1. ScriptVisionController（公开 API）

```
POST /api/ragent/script/vision/analyze
Content-Type: multipart/form-data

参数：
  - image: MultipartFile（截图，JPEG）
  - prompt: String（判断语句）
  - token: String（项目 uploadToken）

返回：
  { "code": "0", "data": { "result": true } }
```

- 不需要用户登录态，用 `token` 参数鉴权
- 通过 `token` 查找对应项目，验证有效性
- 调用 `ScriptVisionService.analyze(image, prompt)` 获取结果

### 2. ScriptVisionServiceImpl

独立服务，直接用 OkHttpClient 调用 bailian 的 chat/completions 端点：

```java
@Service
public class ScriptVisionServiceImpl implements ScriptVisionService {

    @Value("${ai.providers.bailian.api-key}")
    private String apiKey;

    @Value("${ai.providers.bailian.url}")
    private String apiUrl;

    private final OkHttpClient httpClient;
    private final ScriptProjectMapper projectMapper;

    public boolean analyze(MultipartFile image, String prompt, String token) {
        // 1. 通过 token 查找项目，验证 token 有效性
        // 2. 将图片转为 base64
        // 3. 构建 OpenAI 兼容的多模态请求：
        //    {
        //      "model": "qwen-vl-max",
        //      "messages": [{
        //        "role": "user",
        //        "content": [
        //          {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,..."}},
        //          {"type": "text", "text": "请根据截图判断：{prompt}。只回答"是"或"不是""}
        //        ]
        //      }]
        //    }
        // 4. 解析响应，提取 "是" → true，"不是" → false
        // 5. 返回 boolean
    }
}
```

- 不修改现有 ChatClient 体系，避免影响范围过大
- API key 从配置读取
- 超时 10 秒

### 3. ScriptCodeGenerator 改动

**新增 `ai_recognize` 函数**（生成到脚本头部）：

```python
SCRIPT_API_BASE = "{{apiBase}}"   # 打包时替换为实际地址
SCRIPT_TOKEN = "{{token}}"        # 打包时替换为 uploadToken

def ai_recognize(x1, y1, x2, y2, prompt):
    """AI 图像识别：截取屏幕区域，发送到服务器判断"""
    sx1, sy1 = scale_x(x1), scale_y(y1)
    sx2, sy2 = scale_x(x2), scale_y(y2)
    img = ImageGrab.grab(bbox=(sx1, sy1, sx2, sy2))

    import io, base64, urllib.request, json
    buf = io.BytesIO()
    img.save(buf, format='JPEG', quality=80)
    img_b64 = base64.b64encode(buf.getvalue()).decode()

    boundary = '----FormBoundary7MA4YWxkTrZu0gW'
    body = (
        f'--{boundary}\r\n'
        f'Content-Disposition: form-data; name="token"\r\n\r\n'
        f'{SCRIPT_TOKEN}\r\n'
        f'--{boundary}\r\n'
        f'Content-Disposition: form-data; name="prompt"\r\n\r\n'
        f'{prompt}\r\n'
        f'--{boundary}\r\n'
        f'Content-Disposition: form-data; name="image"; filename="screen.jpg"\r\n'
        f'Content-Type: image/jpeg\r\n\r\n'
    ).encode() + base64.b64decode(img_b64) + f'\r\n--{boundary}--\r\n'.encode()

    req = urllib.request.Request(
        f'{SCRIPT_API_BASE}/script/vision/analyze',
        data=body,
        headers={'Content-Type': f'multipart/form-data; boundary={boundary}'}
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            result = json.loads(resp.read())
            return result.get('data', {}).get('result', False)
    except Exception as e:
        print(f'AI识别失败: {e}')
        return False
```

**switch 新增 `if_ai` 分支**：

```java
case "if_ai" -> {
    String prompt = StrUtil.nullToEmpty((String) params.get("prompt"));
    int x1 = getInt(params, "x1", 0);
    int y1 = getInt(params, "y1", 0);
    int x2 = getInt(params, "x2", 100);
    int y2 = getInt(params, "y2", 100);
    sb.append(curIndent).append("if ai_recognize(")
        .append(x1).append(", ").append(y1).append(", ")
        .append(x2).append(", ").append(y2)
        .append(", '").append(escapePy(prompt)).append("'):\n");
    indentLevel++;
}
```

### 3. ScriptServiceImpl 打包注入

`generate()` 方法新增参数 `apiBase` 和 `uploadToken`，在 `doBuildBat` 中传入：

```java
@Value("${script.api-base-url:http://localhost:9090/api/ragent}")
private String apiBaseUrl;

// doBuildBat 中：
String script = ScriptCodeGenerator.generate(
    steps, project.getName(),
    project.getTargetWidth(), project.getTargetHeight(),
    project.getGuiEnabled() == 1,
    apiBaseUrl,          // API 地址
    project.getUploadToken()  // 项目 token
);
```

### 4. 前端 — scriptService.ts

```typescript
// OperationType 新增
| "if_ai"

// OPERATION_LABELS
if_ai: "条件(AI识别)"

// OPERATION_COLORS
if_ai: "bg-violet-100 text-violet-700 border-violet-200"

// OPERATION_HINTS
if_ai: "截取屏幕区域，用 AI 判断是否满足条件。例如「屏幕上是否有登录按钮」。需要服务器在线"
```

### 5. 前端 — ScriptDetailPage.tsx

`if_ai` 参数面板：
- 区域坐标 x1, y1, x2, y2（复用 if_image 的画布拖拽逻辑）
- 判断语句文本框（prompt）

```typescript
// defaultParams
if_ai: { x1: 0, y1: 0, x2: 100, y2: 100, prompt: "" }

// STEP_REQUIRED_FIELDS
if_ai: ["x1", "y1", "x2", "y2", "prompt"]
```

### 6. application.yaml 配置

```yaml
script:
  storage:
    base-path: ./data/script-projects
  api-base-url: http://localhost:9090/api/ragent   # 脚本调用的后端地址
  github:
    token: ghp_xxx
    owner: wxsh-hub
    repo: workflow

ai:
  vision:
    default-model: qwen-vl-max
    candidates:
      - id: qwen-vl-max
        provider: bailian
        model: qwen-vl-max
        priority: 1
```

---

## 安全说明

- `uploadToken` 打包时写入脚本，仅用于 AI 识别接口鉴权
- `api-base-url` 打包时写入脚本，部署不同服务器需重新打包
- Vision API 调用经过 token 校验，防止未授权访问
- 脚本不暴露 AI provider 的 API Key，全部由后端中转

## 验证方式

1. 后端启动后，curl 测试 `POST /script/vision/analyze`
2. 前端添加 `if_ai` 步骤，填写区域和判断语句
3. 预览脚本，确认生成 `ai_recognize` 函数和调用
4. BAT 打包后解压，确认 `SCRIPT_API_BASE` 和 `SCRIPT_TOKEN` 已注入
5. 修改 `application.yaml` 的 `api-base-url`，重新打包验证地址切换
