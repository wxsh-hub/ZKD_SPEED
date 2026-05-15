import * as React from "react";
import { Brain, ChevronDown, GitFork, Bot } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { FeedbackButtons } from "@/components/chat/FeedbackButtons";
import { ThinkingIndicator } from "@/components/chat/ThinkingIndicator";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useChatStore } from "@/stores/chatStore";
import type { Message } from "@/types";

const MarkdownRenderer = React.lazy(() =>
  import("@/components/chat/MarkdownRenderer").then((m) => ({ default: m.MarkdownRenderer }))
);

interface MessageItemProps {
  message: Message;
  isLast?: boolean;
}

export const MessageItem = React.memo(function MessageItem({ message, isLast }: MessageItemProps) {
  const navigate = useNavigate();
  const forkFromMessage = useChatStore((state) => state.forkFromMessage);
  const finishForking = useChatStore((state) => state.finishForking);
  const selectSession = useChatStore((state) => state.selectSession);
  const isUser = message.role === "user";
  const showFeedback =
    message.role === "assistant" &&
    message.status !== "streaming" &&
    message.id &&
    !message.id.startsWith("assistant-");
  const showFork = message.status !== "streaming" && message.id;
  const isThinking = Boolean(message.isThinking);
  const [thinkingExpanded, setThinkingExpanded] = React.useState(false);
  const hasThinking = Boolean(message.thinking && message.thinking.trim().length > 0);
  const hasContent = message.content.trim().length > 0;
  const isWaiting = message.status === "streaming" && !isThinking && !hasContent;
  const [forking, setForking] = React.useState(false);

  const handleFork = async () => {
    if (forking) return;
    setForking(true);
    try {
      const newSessionId = await forkFromMessage(message.id);
      if (newSessionId) {
        navigate(`/chat/${newSessionId}`);
        // 延迟重置 isForking，确保 forkFromMessage 的状态更新和 React 渲染周期完成
        // 避免 selectSession 在 currentSessionId 尚未更新时被触发
        setTimeout(() => finishForking(), 300);
      }
    } finally {
      setForking(false);
    }
  };

  if (isUser) {
    return (
      <div className="flex">
        <div className="user-message">
          <p className="whitespace-pre-wrap break-words">{message.content}</p>
        </div>
      </div>
    );
  }

  const thinkingDuration = message.thinkingDuration ? `${message.thinkingDuration}秒` : "";
  return (
    <div className="group flex">
      <div className="min-w-0 flex-1 space-y-4">
        {isThinking ? (
          <ThinkingIndicator content={message.thinking} duration={message.thinkingDuration} />
        ) : null}
        {!isThinking && hasThinking ? (
          <div className="overflow-hidden rounded-lg border border-[#7C3AED]/30 bg-[#7C3AED]/10">
            <button
              type="button"
              onClick={() => setThinkingExpanded((prev) => !prev)}
              className="flex w-full items-center gap-2 px-4 py-3 text-left transition-colors hover:bg-[#7C3AED]/15"
            >
              <div className="flex flex-1 items-center gap-2">
                <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-[#7C3AED]/20">
                  <Brain className="h-4 w-4 text-[#a78bfa]" />
                </div>
                <span className="text-sm font-medium text-[#a78bfa]">深度思考</span>
                {thinkingDuration ? (
                  <span className="rounded-full bg-[#7C3AED]/20 px-2 py-0.5 text-xs text-[#a78bfa]">
                    {thinkingDuration}
                  </span>
                ) : null}
              </div>
              <ChevronDown
                className={cn(
                  "h-4 w-4 text-[#a78bfa] transition-transform",
                  thinkingExpanded && "rotate-180"
                )}
              />
            </button>
            {thinkingExpanded ? (
              <div className="border-t border-[#7C3AED]/20 px-4 pb-4">
                <div className="mt-3 whitespace-pre-wrap text-sm leading-relaxed text-[#c4b5fd]">
                  {message.thinking}
                </div>
              </div>
            ) : null}
          </div>
        ) : null}
        <div className="space-y-2">
          {isWaiting ? (
            <div className="ai-wait" aria-label="思考中">
              <span className="ai-wait-dots" aria-hidden="true">
                <span className="ai-wait-dot" />
                <span className="ai-wait-dot" />
                <span className="ai-wait-dot" />
              </span>
            </div>
          ) : null}
          {hasContent ? (
            <React.Suspense fallback={<div className="text-sm text-[#999]">加载中...</div>}>
              <MarkdownRenderer content={message.content} />
            </React.Suspense>
          ) : null}
          {message.status === "error" ? (
            <p className="text-xs text-rose-500">生成已中断。</p>
          ) : null}

          {/* 模型信息提示 */}
          {message.modelId ? (
            <div className="flex items-center gap-1.5 text-xs text-[#6B7280]">
              <Bot className="h-3.5 w-3.5" />
              <span>由 <span className="font-medium text-[#7C3AED]">{message.modelId}</span> 模型回答</span>
            </div>
          ) : null}

          <div className="flex items-center gap-1">
            {showFeedback ? (
              <FeedbackButtons
                messageId={message.id}
                feedback={message.feedback ?? null}
                content={message.content}
                alwaysVisible={Boolean(isLast)}
              />
            ) : null}
            {showFork ? (
              <Button
                variant="ghost"
                size="icon"
                onClick={handleFork}
                disabled={forking}
                aria-label="从此处分叉"
                className={cn(
                  "h-8 w-8 text-[#999999] hover:bg-[#F5F5F5] hover:text-[#7C3AED]",
                  "opacity-0 group-hover:opacity-100 transition-opacity",
                  isLast && "opacity-100"
                )}
              >
                <GitFork className="h-4 w-4" />
              </Button>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
});
