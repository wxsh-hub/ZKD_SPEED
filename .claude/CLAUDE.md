
# 项目协作说明

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
├── frontend/                    ← 前端代码（React + TypeScript + Vite）
│   ├── src/pages/               ← 页面
│   ├── src/components/          ← 组件
│   ├── src/services/            ← API 调用
│   ├── src/stores/              ← 状态管理
│   └── src/router.tsx           ← 路由
│
├── bootstrap/src/main/java/     ← 后端代码（Spring Boot）
│   └── com/huangwei/ai/ragent/
│       ├── novel/               ← 小说模块
│       ├── imitation/           ← 仿写模块
│       ├── screenshot/          ← 截图模块（待开发）
│       ├── rag/                 ← RAG 核心
│       ├── ingestion/           ← 文档摄入
│       ├── knowledge/           ← 知识库管理
│       └── user/                ← 用户认证
│
├── framework/                   ← 后端公共框架
├── infra-ai/                    ← 后端 AI 基础设施
├── resources/database/          ← 数据库 schema
└── tools/                       ← 工具脚本（Python 截图工具等）
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

## 当前开发计划：AI 自动化脚本生成器

### 阶段一：截图上传
- 1.1 后端：截图上传接口 POST /screenshot/upload
- 1.2 后端：截图列表接口 GET /screenshot/list
- 1.3 截图工具：集成上传功能（GUI 加 token 输入框）
- 1.4 前端：截图管理页

### 阶段二：标注编辑器
- 2.1 前端：Canvas 标注画布（fabric.js/Konva.js）
- 2.2 前端：操作类型面板（点击/长按/等待/输入/滚动）
- 2.3 前端：步骤列表（拖拽排序、编辑、删除）
- 2.4 后端：标注数据存储接口 POST /script/save
- 2.5 前端：保存/加载标注

### 阶段三：脚本生成
- 3.1 后端：脚本生成引擎（标注 JSON → Python 代码）
- 3.2 后端：脚本预览接口 GET /script/preview/{id}
- 3.3 前端：脚本预览页（语法高亮）
- 3.4 后端：PyInstaller 打包接口（异步编译为 exe）
- 3.5 前端：打包下载页

### 阶段四：完善体验
- 4.1 前端：tkinter 菜单配置
- 4.2 后端：AI 增强生成（可选，调 LLM 优化脚本）
- 4.3 前端：任务模板

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
