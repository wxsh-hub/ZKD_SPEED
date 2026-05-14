# 智答客 · 企业级 RAG 智能问答平台

> **作者**：黄炜（汕头大学 2027 届）

基于 **Java 17 + Spring Boot 3 + React 18** 构建的全栈 RAG 智能体平台，覆盖从文档入库到智能问答的全链路工程化落地，同时增加了小说续写和文章仿写的功能，目前在开发ai脚本开发。

---

## 一、项目概览

| 维度 | 数据 |
|------|------|
| 后端 Java 源文件 | 429 个 |
| 前端 TS/TSX 源文件 | 88 个 |
| 前端功能页面 | 13 个 |
| Maven 模块 | 3 个（framework / infra-ai / bootstrap） |

---

## 二、核心功能

### 2.1 RAG 智能问答

完整的 RAG 对话链路，支持流式输出（SSE）：

```
用户提问
  → 会话记忆加载（自动摘要压缩，控制 Token 成本）
  → Query 改写（语义改写 + 子问题拆分 + 纠错）
  → 意图分类（树形意图体系，低置信度时主动引导用户澄清）
  → 多路并行检索（意图定向检索 + 全局向量检索）
  → Rerank 重排（qwen3-rerank 模型，Top-K 筛选）
  → Prompt 组装（系统提示词 + 检索结果 + 对话历史）
  → LLM 流式生成
```

**亮点特性**：
- **问候快速路径**：常见问候（你好/hello/早上好等）零 LLM 成本直接回复
- **深度思考模式**：可选开启，调用推理能力更强的模型
- **模型选择器**：对话时可手动切换模型
- **对话记忆**：保留最近 N 轮，超出自动摘要压缩

### 2.2 文档入库 Pipeline

基于节点编排的 Pipeline 架构，全流程可配置、可扩展：

```
文档上传 → Tika 解析 → 文本分块（多种策略）→ Embedding 向量化 → 写入 Milvus
```

- 支持 txt / md / pdf / docx 格式
- 分块策略：固定大小、按段落、按句子、结构感知（策略模式 + 工厂模式）
- 入库任务可视化管理，支持进度追踪和失败重试

### 2.3 小说续写

上传小说文件，AI 基于原文风格续写：

```
Tika 解析 → 文本分块 → 向量化 → 存入 Milvus
  → 摘要提取（人物性格/关系、世界观设定、已有剧情线）
  → 语义检索（与续写方向相关的原文片段）
  → LLM 流式续写（temperature=0.8）
```

- 支持指定续写方向和字数（300~3000 字）
- 多轮续写，保持上下文连贯

### 2.4 文章仿写

上传参考文章，AI 通过同义替换、语序调整进行仿写：

```
Tika 解析 → 文本分块 → 向量化 → 存入 Milvus
  → 风格分析（核心论点、写作风格、文章结构）
  → 语义检索（与仿写要求相关的原文片段）
  → LLM 流式仿写（temperature=0.7）
```

- 支持附加自定义要求（更正式、更口语化等）

### 2.5 管理后台

React 管理后台，覆盖业务全流程：

| 模块 | 功能 |
|------|------|
| Dashboard | 数据概览、性能指标、趋势图表 |
| 知识库管理 | 知识库 CRUD、文档列表、入库状态 |
| 意图树 | 树形意图分类体系的可视化编辑 |
| 入库任务 | Pipeline 编排、任务执行进度追踪 |
| 推荐问法 | 管理欢迎页的示例问题 |
| 系统设置 | 模型配置、参数调整 |
| 对话链路追踪 | 完整的 RAG 调用链路可视化 |
| 用户管理 | 用户增删改查、角色权限 |

---

## 三、技术架构

### 3.1 后端架构

```
ragent/
├── framework/          # 公共框架层
│   └── ChatRequest, LLM 接口, 通用工具
├── infra-ai/           # AI 基础设施层
│   ├── ModelSelector    # 模型选择与路由
│   ├── RoutingLLMService # 带熔断的模型调用
│   └── ChatClient 实现  # 百炼/SiliconFlow/Ollama
└── bootstrap/          # 业务启动模块
    ├── rag/            # RAG 核心链路
    │   ├── core/rewrite    # Query 改写
    │   ├── core/intent     # 意图分类
    │   ├── core/retrieval  # 多路检索
    │   ├── core/rerank     # 重排序
    │   ├── core/memory     # 会话记忆
    │   └── core/prompt     # Prompt 组装
    ├── ingestion/      # 文档入库 Pipeline
    ├── novel/          # 小说续写
    ├── imitation/      # 文章仿写
    ├── knowledge/      # 知识库管理
    └── admin/          # 管理后台
```

### 3.2 前端架构

```
frontend/src/
├── pages/
│   ├── ChatPage.tsx        # 对话主页面
│   ├── NovelPage.tsx       # 小说续写页
│   ├── ImitationPage.tsx   # 文章仿写页
│   ├── LoginPage.tsx       # 登录页
│   └── admin/              # 管理后台（7 个子模块）
├── components/
│   ├── chat/          # 对话组件（输入框、消息列表、Markdown 渲染等）
│   ├── layout/        # 布局组件（Header、Sidebar、MainLayout）
│   └── common/        # 通用组件（FlowTag、ModelSelector、Avatar）
├── stores/            # Zustand 状态管理
├── services/          # API 请求封装
└── hooks/             # 自定义 Hooks
```

### 3.3 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Java 17、Spring Boot 3、MyBatis-Plus |
| 前端框架 | React 18、TypeScript、Vite、Tailwind CSS |
| 向量数据库 | Milvus |
| 关系数据库 | MySQL |
| 缓存 | Redis（意图树缓存、限流、会话状态） |
| 文档解析 | Apache Tika |
| 对象存储 | 阿里云 OSS / S3 |
| AI 模型 | 百炼（qwen 系列）、SiliconFlow、Ollama（本地） |
| 状态管理 | Zustand |
| UI 组件 | shadcn/ui、Lucide Icons |

---

## 四、模型路由与容错

系统支持多模型候选、优先级调度与自动降级：

```yaml
# application.yaml 配置示例
ai:
  chat:
    default-model: qwen3-max
    deep-thinking-model: qwen3-max
    candidates:
      - id: qwen3-max
        provider: bailian
        priority: 1
      - id: qwen-plus
        provider: bailian
        priority: 2
      - id: qwen3-local
        provider: ollama
        priority: 3
```

- **优先级调度**：按 priority 顺序尝试调用
- **熔断机制**：模型连续失败后自动跳过，定期探测恢复
- **ProbeBufferingCallback**：流式场景下缓冲首个数据包，失败时切换模型不污染输出
- **模型指定**：调用方可通过 `modelId` 参数指定使用特定模型

---

## 五、前端页面展示

### 欢迎页
- 渐变背景 + 动画入场
- 推荐问法卡片（从后端动态加载）
- 深度思考开关、模型选择器
- FlowTag 展示 RAG 实现原理（悬停查看详细说明）

### 对话页
- SSE 流式输出，实时渲染 Markdown
- 支持代码高亮、表格、列表等富文本
- 停止生成、继续对话
- 会话列表侧边栏

### 小说续写 / 文章仿写
- 文件拖拽上传
- 字数选择、模型选择、自定义方向/要求
- 流式输出 + 导出为 txt

### 管理后台
- Dashboard 数据面板（KPI 卡片、趋势图、性能指标）
- 知识库、文档、入库任务的完整 CRUD
- 意图树可视化编辑器
- 对话链路追踪详情页

---

## 六、快速启动

### 环境要求

- JDK 17+
- Node.js 18+
- MySQL 8.0+
- Redis 6+
- Milvus 2.x

### 后端启动

```bash
# 1. 创建数据库并执行建表脚本
mysql -u root -p < resources/database/schema_table.sql

# 2. 修改配置文件
vim bootstrap/src/main/resources/application.yaml
# 配置 MySQL、Redis、Milvus 连接信息和 AI 模型 API Key

# 3. 启动
cd bootstrap
mvn spring-boot:run
```

### 前端启动

```bash
cd frontend
npm install
npm run dev
# 访问 http://localhost:5173
```

---

## 七、设计模式应用

| 模式 | 应用场景 |
|------|----------|
| 策略模式 | 文本分块策略（FixedSize / Paragraph / Sentence / StructureAware） |
| 工厂模式 | ChunkingStrategyFactory、StreamCallbackFactory |
| 模板方法 | AbstractEmbeddingChunker |
| 责任链 | 入库 Pipeline 节点编排执行 |
| 路由模式 | RoutingLLMService 多模型候选 + 熔断降级 |
| 观察者模式 | SSE 流式事件推送 |

---

## 八、项目结构（后端关键模块）

```
bootstrap/src/main/java/com/huangwei/ai/ragent/
├── rag/                    # RAG 核心
│   ├── core/rewrite/       # Query 改写（多问题拆分）
│   ├── core/intent/        # 意图分类（树形结构 + LLM 打分）
│   ├── core/retrieval/     # 多路检索引擎
│   ├── core/rerank/        # Rerank 重排序
│   ├── core/memory/        # 会话记忆管理
│   ├── core/prompt/        # Prompt 模板组装
│   ├── core/mcp/           # MCP 工具集成
│   └── service/            # 对话服务层
├── ingestion/              # 文档入库
│   ├── node/               # Pipeline 节点（Parser/Chunker/Embedding/Writer）
│   └── domain/             # 领域模型与枚举
├── novel/                  # 小说续写模块
├── imitation/              # 文章仿写模块
├── knowledge/              # 知识库管理
├── user/                   # 用户认证
└── admin/                  # 管理后台

infra-ai/src/main/java/com/huangwei/ai/ragent/infra/
├── chat/                   # LLM 调用（RoutingLLMService）
├── model/                  # 模型选择（ModelSelector）
└── embedding/              # Embedding 服务

framework/src/main/java/com/huangwei/ai/ragent/framework/
└── convention/             # 通用约定（ChatRequest 等）
```

---

## 九、许可证

本项目为个人作品，仅供学习交流使用。

---

**作者**：黄炜 | 汕头大学 2027 届
