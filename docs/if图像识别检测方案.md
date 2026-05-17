# IF 图像识别检测方案

## 概述

脚本自动化模块中的「条件(图像)」操作允许用户在截图上圈出一块区域作为模板图片，运行时通过截取屏幕对应区域并与模板进行像素级比对，判断是否满足相似度阈值，从而决定是否执行条件块内的步骤。

## 整体流程

```
用户圈选区域 → 裁剪上传模板到 COS → 编译时下载模板打包进 EXE → 运行时截屏比对 → 满足阈值则执行
```

## 一、前端：圈选区域与模板上传

### 1.1 用户操作

1. 用户添加一个「条件(图像)」步骤（`if_image`）
2. 选中该步骤后，在截图画布上拖拽画出一个矩形区域
3. 系统自动裁剪该区域并上传为模板图片

### 1.2 技术实现

**文件**: `frontend/src/pages/ScriptDetailPage.tsx`

画布事件处理：

- `handleMouseDown`: 检测到当前步骤为 `if_image` 时，记录拖拽起始坐标，开始绘图
- `handleMouseMove`: 实时绘制紫色虚线矩形框，提供视觉反馈
- `handleMouseUp`: 拖拽结束后调用 `cropAndUpload()` 裁剪并上传

**裁剪逻辑 (`cropAndUpload`)**:

```typescript
// 1. 创建临时 canvas，尺寸为裁剪区域大小
const cropCanvas = document.createElement("canvas");
cropCanvas.width = x2 - x1;
cropCanvas.height = y2 - y1;

// 2. 从原始截图上绘制裁剪区域
ctx.drawImage(img, x1 * scale, y1 * scale, 
              (x2 - x1) * scale, (y2 - y1) * scale, 
              0, 0, x2 - x1, y2 - y1);

// 3. 导出为 PNG blob
cropCanvas.toBlob((blob) => {
  const file = new File([blob], "template.png", { type: "image/png" });
  // 4. 调用上传接口
  onUploadTemplate(file, x1, y1, x2, y2);
}, "image/png");
```

**跨域处理**:

图片加载时设置 `img.crossOrigin = "anonymous"` 以允许画布导出图片数据（`toBlob`）。如果 COS 未配置 CORS，会回退为普通加载模式（画布被污染，裁剪功能不可用）。

### 1.3 数据存储

步骤的 `paramsJson` 结构：

```json
{
  "x1": 100,
  "y1": 200,
  "x2": 300,
  "y2": 400,
  "similarity": 0.95,
  "templateUrl": "https://wxsh-1361118632.cos.ap-guangzhou.myqcloud.com/script-builds/templates/2055662335162621952/abc123.png"
}
```

| 字段 | 说明 |
|------|------|
| `x1, y1` | 检测区域左上角坐标（原图尺寸） |
| `x2, y2` | 检测区域右下角坐标（原图尺寸） |
| `similarity` | 相似度阈值，默认 0.95（95%） |
| `templateUrl` | 模板图片在 COS 上的 URL |

## 二、后端：模板上传与编译打包

### 2.1 模板上传接口

**文件**: `ScriptController.java` + `ScriptServiceImpl.java`

```
POST /script/{projectId}/template/upload
Content-Type: multipart/form-data
```

- 校验文件为图片格式
- 上传到 COS，路径: `script-builds/templates/{projectId}/{uuid}.png`
- 返回 COS 公开访问 URL

### 2.2 编译时下载模板

**文件**: `ScriptServiceImpl.java` → `downloadTemplates()`

编译流程中，在调用 Nuitka 之前：

1. 查询项目下所有步骤，筛选出 `if_image` 类型
2. 提取每个步骤的 `templateUrl`
3. 从 COS 下载模板文件到编译临时目录的 `templates/` 子目录

```
{nuitka-tmpDir}/
├── script.py
└── templates/
    ├── abc123.png
    └── def456.png
```

### 2.3 Nuitka 打包

通过 `--include-data-dir` 参数将模板目录打包进 EXE：

```bash
python -m nuitka --standalone --onefile --disable-console \
  --enable-plugin=tk-inter \
  --include-data-dir={tmpDir}/templates=templates \
  script.py
```

运行时模板文件会被解压到 EXE 同目录或临时目录下的 `templates/` 子目录。

## 三、Python 代码生成

**文件**: `ScriptCodeGenerator.java`

### 3.1 match_image 辅助函数

代码生成器在脚本头部生成一个 `match_image` 函数：

```python
from PIL import Image, ImageGrab

def match_image(rel_path, x1, y1, x2, y2, threshold=0.95):
    # 1. 定位模板文件
    if getattr(sys, 'frozen', False):
        base = os.path.dirname(os.path.abspath(sys.executable))
    else:
        base = os.path.dirname(os.path.abspath(__file__))
    tpl_path = os.path.join(base, rel_path)
    
    # Nuitka onefile 解压路径回退
    if not os.path.exists(tpl_path):
        import tempfile
        for d in [tempfile.gettempdir(), os.environ.get('TEMP', '')]:
            alt = os.path.join(d, rel_path)
            if os.path.exists(alt):
                tpl_path = alt
                break
    
    # 2. 加载模板图片
    tpl = Image.open(tpl_path).convert('RGB')
    
    # 3. 截取屏幕指定区域
    scr = ImageGrab.grab(bbox=(x1, y1, x2, y2)).convert('RGB')
    
    # 4. 逐像素比对
    t_px = list(tpl.getdata())   # 模板像素列表 [(R,G,B), ...]
    s_px = list(scr.getdata())   # 截图像素列表 [(R,G,B), ...]
    
    # 5. 计算归一化均方误差相似度
    diff = sum((a - b) ** 2 for a, b in zip(t_px, s_px))
    max_diff = len(t_px) * 3 * 255 * 255  # 理论最大差异值
    sim = 1.0 - diff / max_diff if max_diff > 0 else 1.0
    
    return sim >= threshold
```

### 3.2 生成的 if 语句

当步骤为 `if_image` 时，代码生成器输出：

```python
# step N: if_image
if match_image('templates/abc123.png', 100, 200, 300, 400, 0.95):
    # if_image 和 if_end 之间的步骤会自动缩进
    mouse_click(150, 250)
    time.sleep(1.0)
# if_end 后恢复正常缩进
```

### 3.3 缩进控制

代码生成器使用 `indentLevel` 变量追踪嵌套层级：

- `for_start` / `if_image` / `if_random`: 输出 `if/for` 语句后 `indentLevel++`
- `for_end` / `if_end`: `indentLevel--`
- 所有步骤使用 `curIndent = indent + "    ".repeat(indentLevel)` 确保正确缩进

支持嵌套：

```python
for _loop_i in range(3):
    if match_image('templates/abc.png', 0, 0, 100, 100, 0.9):
        mouse_click(50, 50)
        if random.random() < 0.5:
            time.sleep(1.0)
    mouse_click(200, 200)
```

## 四、相似度算法详解

### 4.1 算法原理

使用**归一化均方误差（Normalized MSE）**计算相似度：

```
对于每对像素 (R1,G1,B1) 和 (R2,G2,B2):
  pixel_diff = (R1-R2)^2 + (G1-G2)^2 + (B1-B2)^2

total_diff = Σ pixel_diff
max_diff = pixel_count * 3 * 255^2

similarity = 1.0 - total_diff / max_diff
```

- `similarity = 1.0` → 完全相同
- `similarity = 0.0` → 完全不同（每个通道都差 255）

### 4.2 阈值建议

| 场景 | 建议阈值 | 说明 |
|------|----------|------|
| 精确匹配 | 0.98 ~ 0.99 | 适用于 UI 固定、无动态元素 |
| 一般场景 | 0.90 ~ 0.95 | 默认值，容忍少量像素差异 |
| 宽松匹配 | 0.80 ~ 0.90 | 有背景变化、动画、抗锯齿 |

### 4.3 注意事项

- **分辨率一致性**: 截图和运行时屏幕分辨率必须一致，否则像素无法一一对应
- **模板尺寸**: 模板图片必须与 `x1,y1,x2,y2` 定义的区域尺寸完全一致
- **屏幕缩放**: 运行时屏幕缩放比例（DPI scaling）会影响截图尺寸，需与截图时一致
- **性能**: 像素逐个比较，大区域（如 1920x1080）会较慢，建议圈选小区域

## 五、配置要求

### 5.1 COS CORS 配置

前端裁剪功能需要 COS 存储桶配置 CORS：

- **来源**: `*`（或前端域名）
- **允许方法**: GET, HEAD, POST
- **允许 Headers**: `*`

### 5.2 Python 依赖

编译后的 EXE 依赖 PIL（Pillow）库：

- `Image`: 图片加载
- `ImageGrab`: 屏幕截图

Nuitka 编译时需确保 Pillow 已安装：`pip install Pillow`

### 5.3 前端依赖

无额外依赖，使用浏览器原生 Canvas API 进行图片裁剪。
