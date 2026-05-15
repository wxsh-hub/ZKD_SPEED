import * as React from "react";
import { ChevronDown, Cpu } from "lucide-react";
import { cn } from "@/lib/utils";

export interface FlowStep {
  label: string;
  detail: string;
}

interface FlowTagProps {
  title: string;
  steps: FlowStep[];
  defaultExpanded?: boolean;
  className?: string;
}

export function FlowTag({ title, steps, defaultExpanded = false, className }: FlowTagProps) {
  const [expanded, setExpanded] = React.useState(defaultExpanded);

  return (
    <div className={cn("w-full", className)}>
      <button
        type="button"
        onClick={() => setExpanded((prev) => !prev)}
        className="inline-flex items-center gap-1.5 rounded-full border border-[#E5E7EB] bg-white/80 px-3 py-1 text-xs text-[#6B7280] transition-all hover:border-[#C4B5FD] hover:bg-[#F5F3FF] hover:text-[#7C3AED]"
      >
        <Cpu className="h-3 w-3" />
        <span className="font-medium">{title}</span>
        <ChevronDown
          className={cn("h-3 w-3 transition-transform duration-200", expanded && "rotate-180")}
        />
      </button>

      {expanded ? (
        <div className="mt-2 rounded-xl border border-[#E5E7EB] bg-gradient-to-br from-[#FAFAFA] to-white p-3 shadow-sm">
          <div className="flex flex-wrap items-center gap-1.5">
            {steps.map((step, i) => (
              <React.Fragment key={i}>
                <FlowStepBadge index={i} step={step} />
                {i < steps.length - 1 ? (
                  <svg
                    className="h-3 w-3 shrink-0 text-[#C4B5FD]"
                    viewBox="0 0 16 16"
                    fill="currentColor"
                  >
                    <path d="M6.22 3.22a.75.75 0 0 1 1.06 0l4.25 4.25a.75.75 0 0 1 0 1.06l-4.25 4.25a.75.75 0 0 1-1.06-1.06L9.94 8 6.22 4.28a.75.75 0 0 1 0-1.06Z" />
                  </svg>
                ) : null}
              </React.Fragment>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  );
}

function FlowStepBadge({ index, step }: { index: number; step: FlowStep }) {
  const [showTip, setShowTip] = React.useState(false);

  return (
    <span className="relative inline-flex">
      <span
        className="inline-flex cursor-default items-center gap-1 rounded-lg bg-[#F5F3FF] px-2.5 py-1 text-[11px] font-medium text-[#7C3AED] transition-colors hover:bg-[#EDE9FE]"
        onMouseEnter={() => setShowTip(true)}
        onMouseLeave={() => setShowTip(false)}
      >
        <span className="flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-[#7C3AED] text-[9px] font-bold text-white">
          {index + 1}
        </span>
        {step.label}
      </span>

      {showTip ? (
        <span className="absolute bottom-full left-1/2 z-50 mb-2 w-56 max-w-[240px] -translate-x-1/2 rounded-lg border border-[#E5E7EB] bg-white px-3 py-2 text-[11px] leading-relaxed text-[#374151] shadow-lg">
          {step.detail}
          <span className="absolute left-1/2 top-full -translate-x-1/2 border-4 border-transparent border-t-white" />
        </span>
      ) : null}
    </span>
  );
}

// ==================== 预定义流程 ====================

export const RAG_CHAT_STEPS: FlowStep[] = [
  {
    label: "Query 改写",
    detail: "调用 LLM 对用户原始问题进行语义改写和子问题拆分，提取关键实体和意图，修正错别字和口语化表达"
  },
  {
    label: "意图分类",
    detail: "将改写后的问题与预定义的意图树进行匹配，通过 LLM 打分识别用户意图属于知识库检索、工具调用还是系统闲聊"
  },
  {
    label: "知识检索",
    detail: "将问题向量化后在 Milvus 向量数据库中进行相似度搜索，同时并行查询多个知识库通道，召回相关文档片段"
  },
  {
    label: "Rerank 重排",
    detail: "使用 qwen3-rerank 模型对召回的文档片段按与问题的语义相关性重新排序，筛选出最相关的 Top-K 片段"
  },
  {
    label: "Prompt 组装",
    detail: "将系统提示词、检索到的知识片段、对话历史和用户问题按结构化模板组装成完整的 LLM 输入 Prompt"
  },
  {
    label: "LLM 生成",
    detail: "将组装好的 Prompt 发送给大语言模型（qwen3-max），流式生成最终回答，支持深度思考模式"
  },
];

export const NOVEL_CONTINUE_STEPS: FlowStep[] = [
  {
    label: "Tika 解析",
    detail: "使用 Apache Tika 解析上传的小说文件（支持 txt/md/pdf/docx），自动识别编码和格式，提取纯文本内容"
  },
  {
    label: "文本分块",
    detail: "按语义边界将长文本切分为重叠的 chunk（默认 512 token），保留上下文连贯性，每个 chunk 带位置元数据"
  },
  {
    label: "向量化",
    detail: "调用 Embedding 模型（Qwen3-Embedding-8B）将每个文本 chunk 转换为 4096 维的稠密向量，用于语义相似度计算"
  },
  {
    label: "存入 Milvus",
    detail: "将向量和对应的文本内容、元数据写入 Milvus 向量数据库，建立索引以支持高效的近似最近邻搜索"
  },
  {
    label: "摘要提取",
    detail: "从 Milvus 取出所有 chunk 拼接原文，调用 LLM 分三次提取：主要人物（性格/关系）、世界观设定、已有剧情线摘要"
  },
  {
    label: "语义检索",
    detail: "将用户的续写方向向量化，在 Milvus 中检索与续写方向最相关的原文片段，保留风格和语境参考"
  },
  {
    label: "LLM 续写",
    detail: "将人物/世界观/剧情摘要 + 相关原文片段 + 历史对话组装成 Prompt，调用 LLM（temperature=0.8）流式生成续写内容"
  },
];

export const ARTICLE_IMITATION_STEPS: FlowStep[] = [
  {
    label: "Tika 解析",
    detail: "使用 Apache Tika 解析上传的文章文件（支持 txt/md/pdf/docx），自动识别编码和格式，提取纯文本内容"
  },
  {
    label: "文本分块",
    detail: "按语义边界将长文本切分为重叠的 chunk（默认 512 token），保留上下文连贯性，每个 chunk 带位置元数据"
  },
  {
    label: "向量化",
    detail: "调用 Embedding 模型（Qwen3-Embedding-8B）将每个文本 chunk 转换为 4096 维的稠密向量，用于语义相似度计算"
  },
  {
    label: "存入 Milvus",
    detail: "将向量和对应的文本内容、元数据写入 Milvus 向量数据库，建立索引以支持高效的近似最近邻搜索"
  },
  {
    label: "风格分析",
    detail: "从 Milvus 取出所有 chunk 拼接原文，调用 LLM 分三次提取：核心论点与要点、写作风格（用词/句式/语气）、文章结构（段落/逻辑/层次）"
  },
  {
    label: "语义检索",
    detail: "将用户的仿写要求向量化，在 Milvus 中检索与要求最相关的原文片段，保留内容和语境参考"
  },
  {
    label: "LLM 仿写",
    detail: "将风格分析 + 相关原文片段 + 历史对话组装成 Prompt，调用 LLM（temperature=0.7）通过同义替换、语序调整流式生成仿写内容"
  },
];
