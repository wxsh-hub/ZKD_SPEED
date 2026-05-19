# 项目协作说明

## 项目简介

Ragent 是一个 AI 多功能平台，包含以下核心模块：
- **对话** — AI 聊天（支持会话管理）
- **小说** — AI 小说续写
- **仿写** — 文章风格仿写
- **脚本生成器** — 可视化 UI 自动化脚本构建工具（截图标注 → Python 代码 → 编译 EXE）
- **知识库** — RAG 检索增强（文档摄入、向量检索、意图树）
- **管理后台** — 数据看板、知识库管理、用户管理、系统设置

## Git Worktree 工作区

本项目使用 git worktree 实现多 Claude 并行开发，互不干扰。

```
Desktop/11/
├── ragent-main/          ← master 分支（主工作区，用于合并代码）
├── ragent-screenshot/    ← feature/screenshot 分支（后端开发）
└── ragent-frontend/      ← feature/frontend 分支（前端开发）
```

## 代码归属

```
ragent-main/
├── frontend/                            ← 前端代码（React + TypeScript + Vite）
│   ├── src/pages/
│   │   ├── ChatPage.tsx                 ← 对话页
│   │   ├── NovelPage.tsx                ← 小说续写页
│   │   ├── ImitationPage.tsx            ← 仿写页
│   │   ├── ScriptPage.tsx               ← 脚本项目列表页
│   │   ├── ScriptDetailPage.tsx         ← 脚本编辑器页（核心）
│   │   ├── LandingPageA/B/C.tsx         ← 落地页
│   │   ├── LoginPage.tsx                ← 登录页
│   │   └── admin/                       ← 管理后台页面
│   │       ├── dashboard/               ← 数据看板
│   │       ├── knowledge/               ← 知识库管理
│   │       ├── intent-tree/             ← 意图树管理
│   │       ├── ingestion/               ← 文档摄入
│   │       ├── traces/                  ← RAG 追踪
│   │       ├── settings/                ← 系统设置
│   │       ├── sample-questions/        ← 示例问题
│   │       └── users/                   ← 用户管理
│   ├── src/components/
│   │   ├── chat/                        ← 对话组件
│   │   ├── layout/                      ← 布局组件（Sidebar 等）
│   │   └── script/                      ← 脚本组件（8个：Canvas、步骤列表等）
│   ├── src/services/
│   │   ├── api.ts                       ← axios 实例（含 bigint 转 string）
│   │   ├── scriptService.ts             ← 脚本 API 客户端（20种操作类型定义）
│   │   └── ...
│   ├── src/stores/
│   │   ├── authStore.ts                 ← 认证状态
│   │   ├── scriptStore.ts               ← 脚本状态（Zustand，备用方案）
│   │   └── ...
│   ├── src/types/
│   │   └── script.ts                    ← 脚本类型定义
│   └── src/router.tsx                   ← 路由配置
│
├── bootstrap/src/main/java/             ← 后端代码（Spring Boot）
│   └── com/huangwei/ai/ragent/
│       ├── novel/                       ← 小说模块（参考模板）
│       ├── imitation/                   ← 仿写模块
│       ├── script/                      ← 脚本生成器模块
│       │   ├── controller/
│       │   │   ├── ScriptController.java        ← 主控制器（前端调用）
│       │   │   ├── ScriptVisionController.java  ← AI 视觉分析（免认证）
│       │   │   ├── ScriptProjectController.java ← 旧版项目控制器
│       │   │   ├── ScriptStepController.java    ← 旧版步骤控制器
│       │   │   ├── request/                     ← 请求 DTO
│       │   │   └── vo/                          ← 响应 VO
│       │   ├── service/
│       │   │   ├── ScriptService.java / impl    ← 主服务（前端对应）
│       │   │   ├── ScriptCodeGenerator.java     ← Python 代码生成器（ctypes）
│       │   │   ├── ScriptVisionService.java     ← AI 视觉分析（通义千问）
│       │   │   ├── ScriptProjectService.java    ← 旧版项目服务
│       │   │   ├── ScriptStepService.java       ← 旧版步骤服务
│       │   │   └── ScriptGeneratorService.java  ← 旧版代码生成器（pyautogui）
│       │   ├── dao/
│       │   │   ├── entity/              ← DO 实体（3张表）
│       │   │   └── mapper/              ← MyBatis-Plus Mapper
│       │   ├── config/                  ← 编译线程池配置
│       │   └── util/                    ← 文件名解析工具
│       ├── rag/                         ← RAG 核心
│       ├── ingestion/                   ← 文档摄入管线
│       ├── knowledge/                   ← 知识库管理
│       ├── admin/                       ← 管理后台
│       └── user/                        ← 用户认证
│
├── framework/                           ← 后端公共框架
├── infra-ai/                            ← 后端 AI 基础设施
├── resources/database/                  ← 数据库 schema
└── tools/                               ← 工具脚本
```

**简单划分：`frontend/` 目录是前端，其余全是后端。**

## 规则

- **只改自己工作目录下的文件，绝对不要改其他目录的文件**
- 改完代码后必须提交到自己的分支：`git add . && git commit -m "提交信息"`
- 不要直接操作 master 分支
- 如需新增目录（如 `bootstrap/src/.../screenshot/`），在自己的工作区内创建即可

## 遇到问题先查 docs/

项目根目录下的 `docs/` 文件夹有重要的技术文档和经验总结，**遇到任何问题（接口报错、联调失败、环境配置等）务必先阅读 `docs/` 下的相关文档**，不要凭猜测排查。

关键文档：
- `docs/测试心得.md` — 接口测试流程、登录方式、Token 用法、常见错误码排查、日志位置
- `docs/core-technologies.md` — 核心技术实现方案（图片代理等）

## 后端 Claude 操作指引

你的工作目录是：`C:\Users\sunha\Desktop\11\ragent-screenshot`
你的分支是：`feature/screenshot`

```bash
# 第一步：进入你的工作目录
cd C:\Users\sunha\Desktop\11\ragent-screenshot

# 第二步：开发（只改这些目录下的文件）
# bootstrap/src/main/java/    ← Java 后端代码
# framework/                   ← 公共框架
# infra-ai/                    ← AI 基础设施
# resources/database/          ← 数据库 schema

# 第三步：提交
git add .
git commit -m "你的提交信息"
```

**你负责的任务：** 1.1, 1.2, 2.4, 3.1, 3.2, 3.4

## 前端 Claude 操作指引

你的工作目录是：`C:\Users\sunha\Desktop\11\ragent-frontend`
你的分支是：`feature/frontend`

```bash
# 第一步：进入你的工作目录
cd C:\Users\sunha\Desktop\11\ragent-frontend

# 第二步：开发（只改这个目录下的文件）
# frontend/src/pages/          ← 页面
# frontend/src/components/     ← 组件
# frontend/src/services/       ← API 调用
# frontend/src/stores/         ← 状态管理
# frontend/src/router.tsx      ← 路由
# frontend/src/styles/         ← 样式
# frontend/package.json        ← 依赖

# 第三步：提交
git add .
git commit -m "你的提交信息"
```

**你负责的任务：** 1.4, 2.1, 2.2, 2.3, 2.5, 3.3, 3.5

## 合并流程（主工作区操作）

```bash
cd C:\Users\sunha\Desktop\11\ragent-main
git merge feature/screenshot
git merge feature/frontend
```

## 清理 worktree（开发结束后）

```bash
git worktree remove ../ragent-screenshot
git worktree remove ../ragent-frontend
git branch -d feature/screenshot
git branch -d feature/frontend
```

## 项目技术栈

- 后端：Java 17 + Spring Boot + MyBatis-Plus + Milvus + Redis
- 前端：React + TypeScript + Vite + Tailwind CSS
- 数据库：MySQL (库名 db1)
- 向量库：Milvus
- 缓存：Redis
- AI 视觉：阿里云 DashScope（通义千问 Qwen-VL-Max）
- 对象存储：OSS（截图/模板/编译产物）

## 后端开发约定

- 参考 `novel/` 模块的代码结构：controller → service → service/impl → dao/entity → dao/mapper
- Controller 用 `@RestController` + `@RequiredArgsConstructor`
- 返回值统一用 `Result<T>` 包装，通过 `Results.success()` 构造
- SSE 流式接口用 `SseEmitter`，参考 `NovelController.continueNovel()`
- 请求体用 `@Valid` 校验，参考 `NovelContinueRequest`
- 新增 Mapper 需要在 `RagentApplication.java` 的 `@MapperScan` 中注册包名
- 数据库新表需要在 `resources/database/schema_table.sql` 中添加建表语句

## 前端开发约定

- 页面放在 `frontend/src/pages/`，路由在 `frontend/src/router.tsx` 注册
- API 调用放在 `frontend/src/services/`，用 axios 封装
- 组件放在 `frontend/src/components/`
- 样式用 Tailwind CSS，参考现有页面的 class 写法
- 新页面需要在 `router.tsx` 中添加路由

### ⚠️ 前后端 ID 传递必须用字符串（bigint 精度丢失陷阱）

后端主键是 MySQL `bigint(20)`（如 `2055228071107194880`），**超过了 JavaScript `Number.MAX_SAFE_INTEGER`（9007199254740991）**。

如果 axios 直接 `JSON.parse`，数字会被截断，导致：
- 前端拿到的 ID 与后端不一致
- 用错误 ID 查询后端，接口返回空或报错
- 页面点击无反应、数据丢失等诡异 bug

**已在 `frontend/src/services/api.ts` 中配置了 `transformResponse`，自动将 16 位以上数字转为字符串。**

**新增前端模块时务必注意：**
- `types/` 中所有 ID 字段类型必须是 `string`，不能是 `number`
- 不要手动对 ID 做 `parseInt`、`Number()` 等转换
- 传参时直接用字符串拼接：`` `/script/project/${id}` ``，不要用模板字面量以外的方式

## 启动方式

后端：
```bash
cd bootstrap
mvn spring-boot:run
# 或者在 IDE 中运行 RagentApplication.main()
```

前端：
```bash
cd frontend
npm install
npm run dev
# 访问 http://localhost:5173
```

---

## 脚本生成器模块详细说明

### 功能概述

可视化 UI 自动化脚本构建工具。用户上传目标应用的截图，在截图上标注操作步骤（点击、输入、滚动等），系统自动生成 Python 脚本，可编译为独立 EXE。

### 核心工作流

```
创建项目 → 上传截图（自动检测分辨率）→ 在截图上标注操作 → 保存 → 预览 Python 代码 → 编译（BAT/EXE）→ 下载
```

### 支持的 20 种操作类型

| 类别 | 操作 | 说明 |
|------|------|------|
| 鼠标 | click, double_click, area_click, long_press, area_long_press, mouse_move | 基础鼠标操作 |
| 键盘 | key_press, key_long_press | 按键操作（支持 40+ 键位） |
| 输入 | input_text | 文本输入（Unicode 支持） |
| 等待 | wait_seconds | 固定等待 |
| 滚动 | scroll | 滚动操作 |
| 循环 | for_start, for_end, break_loop, continue_loop | 控制流 |
| 条件 | if_image, if_ai, if_random, else, if_end | 条件分支 |

### 两种编译模式

- **BAT 模式**（推荐）：打包 .py + .bat + 模板为 zip，速度快，需要用户机器装 Python
- **EXE 模式**：通过 GitHub Actions 或本地 Nuitka 编译为独立 EXE，慢但免 Python 环境

### AI 视觉识别（if_ai）

`if_ai` 条件会截取屏幕区域，发送到后端 `/script/vision/analyze` 接口，由阿里云通义千问 Qwen-VL-Max 模型判断条件是否成立（返回是/否）。

### 代码生成技术方案

- 使用 Windows ctypes API 直接控制鼠标键盘（非 pyautogui），生成的 EXE 体积更小、不易触发杀毒
- 坐标自动缩放：标注分辨率 vs 实际屏幕分辨率
- 图像匹配：PIL 像素比较（无 OpenCV 依赖）
- GUI 模式：生成 tkinter 控制面板（开始/暂停/停止，F5/F6/F7 热键）

### 前端路由

| 路由 | 页面 | 说明 |
|------|------|------|
| `/script` | ScriptPage | 项目列表（卡片网格） |
| `/script/:projectId` | ScriptDetailPage | 项目编辑器（三栏布局） |

### 后端 API（主接口 ScriptController）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/script/list` | 项目列表 |
| GET | `/script/{projectId}` | 项目详情（含截图+步骤） |
| POST | `/script/create` | 创建项目 |
| DELETE | `/script/{projectId}` | 删除项目 |
| POST | `/script/{projectId}/save` | 保存项目（截图顺序+步骤+GUI配置） |
| POST | `/script/{projectId}/screenshot/upload` | 上传单张截图 |
| POST | `/script/{projectId}/screenshot/batch-upload` | 批量上传截图 |
| GET | `/script/{projectId}/preview` | 预览生成的 Python 代码 |
| POST | `/script/{projectId}/build` | 编译（body: `{mode: "bat"\|"github"}`） |
| GET | `/script/{projectId}/build/status` | 轮询编译状态 |
| POST | `/script/{projectId}/template/upload` | 上传 if_image 模板图 |
| POST | `/script/token/upload` | 截图工具通过 token 免认证上传 |

### AI 视觉接口（ScriptVisionController，免认证）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/script/vision/analyze` | AI 图像分析（multipart: image + prompt） |

### 数据库表

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| `t_script_project` | 脚本项目 | name, target_width, target_height, scale_pct, status, exe_path, gui_enabled, upload_token |
| `t_script_screenshot` | 项目截图 | project_id, file_name, file_url, width, height, scale_pct, sort_order |
| `t_script_step` | 操作步骤 | project_id, screenshot_id, step_order, operation_type, params_json, template_path |

### 脚本编辑器页面布局（ScriptDetailPage）

```
┌─────────────────────────────────────────────────────────┐
│ 顶部工具栏：保存 | 预览 | 编译 | 下载 | 帮助              │
├──────────┬──────────────────────┬───────────────────────┤
│ 左栏     │ 中栏                 │ 右栏                  │
│ 截图列表  │ Canvas 标注画布       │ 步骤列表              │
│ ·上传    │ ·点击标注             │ ·拖拽排序             │
│ ·删除    │ ·框选区域             │ ·编辑参数             │
│ ·选择    │ ·滚动路径             │ ·插入步骤             │
│          │                      │ ·删除                 │
├──────────┴──────────────────────┴───────────────────────┤
│ 底部操作栏：20种操作类型按钮                               │
└─────────────────────────────────────────────────────────┘
```

### 前后端代码对应关系

| 前端 | 后端 | 说明 |
|------|------|------|
| `scriptService.ts` | `ScriptController.java` | API 调用 |
| `scriptService.ts` 类型定义 | `request/` + `vo/` | 请求/响应 DTO |
| `ScriptPage.tsx` | `ScriptController.list/create/delete` | 项目列表 |
| `ScriptDetailPage.tsx` | `ScriptController.save/preview/build` | 编辑器 |
| `types/script.ts` | `ScriptStatus.java` | 枚举定义 |
| `stores/scriptStore.ts` | `ScriptServiceImpl.java` | 状态管理（备用） |

### 代码生成器（ScriptCodeGenerator.java）

这是脚本生成器的核心类，负责将前端保存的步骤数据转换为可执行的 Python 代码。

输入：`ScriptProjectDetailVO`（项目+截图+步骤）
输出：Python 代码字符串

生成的 Python 脚本特性：
- 使用 `ctypes.windll.user32` 直接调用 Windows API
- 支持坐标缩放（标注分辨率 vs 运行时屏幕分辨率）
- PIL 像素级图像匹配（wait_image, if_image）
- SendInput + KEYEVENTF_UNICODE 实现中文输入
- 完整控制流支持（for/while/break/continue/if-else）
- AI 识别通过 HTTP 请求调用后端 vision 接口
- 可选 tkinter GUI 控制面板

---

## 分工

| 工作区 | 分支 | 负责内容 |
|--------|------|----------|
| ragent-main | master | 合并代码、协调 |
| ragent-screenshot | feature/screenshot | 后端接口（1.1, 1.2, 2.4, 3.1, 3.2, 3.4） |
| ragent-frontend | feature/frontend | 前端页面（1.4, 2.1, 2.2, 2.3, 2.5, 3.3, 3.5） |

## 本地测试配置（多项目并行）

为支持多项目同时运行进行接口测试，本地开发时修改以下配置，**提交时不要包含这些改动**。

### 后端配置修改

文件：`bootstrap/src/main/resources/application.yaml`

```yaml
server:
  port: 9091  # 原为 9090，改为 9091 避免端口冲突

spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/222?...  # 数据库名改为 222（或你本地的库名）
```

### 前端配置修改

文件：`frontend/vite.config.ts`（或对应的代理配置文件）

```typescript
proxy: {
  '/api': {
    target: 'http://localhost:9091',  // 原为 9090，改为 9091
    changeOrigin: true,
  }
}
```

### 提交时排除配置文件

```bash
git add .
git checkout -- bootstrap/src/main/resources/application.yaml
git checkout -- frontend/vite.config.ts
git commit -m "你的提交信息"
```

### 当前端口分配

| 项目 | 后端端口 | 前端端口 |
|------|----------|----------|
| ragent-main (master) | 9090 | 5173 |
| ragent-screenshot (feature/screenshot) | 9091 | 5174 |
