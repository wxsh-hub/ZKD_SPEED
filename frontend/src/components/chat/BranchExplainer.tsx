import * as React from "react";
import { ChevronDown, GitBranch } from "lucide-react";

import { cn } from "@/lib/utils";

export function BranchExplainer() {
  const [expanded, setExpanded] = React.useState(true);

  return (
    <div className="mx-auto max-w-[800px] px-6 pt-4">
      <div className="overflow-hidden rounded-xl border border-[#E5E7EB] bg-gradient-to-br from-[#F5F3FF] via-white to-[#F0F9FF] shadow-sm">
        <button
          type="button"
          onClick={() => setExpanded((prev) => !prev)}
          className="flex w-full items-center gap-3 px-5 py-4 text-left transition-colors hover:bg-[#7C3AED]/5"
        >
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-[#7C3AED]/15">
            <GitBranch className="h-5 w-5 text-[#7C3AED]" />
          </div>
          <div className="flex-1">
            <p className="text-sm font-semibold text-[#1F2937]">对话分支功能</p>
            <p className="text-xs text-[#6B7280]">点击展开查看工作原理</p>
          </div>
          <ChevronDown
            className={cn(
              "h-5 w-5 text-[#7C3AED] transition-transform duration-200",
              expanded && "rotate-180"
            )}
          />
        </button>

        {expanded && (
          <div className="border-t border-[#E5E7EB] px-5 pb-5 pt-4">
            <div className="mb-4 rounded-lg bg-[#7C3AED]/5 p-4">
              <p className="text-sm leading-relaxed text-[#374151]">
                对话分支允许你从对话中的任意一条消息处"分叉"，创建一个新的对话分支。
                分支会复制该消息及之前的所有历史消息，你可以在分支中继续探索不同的方向，
                而不影响原始对话。
              </p>
            </div>

            <div className="space-y-3">
              <div className="flex items-start gap-3">
                <div className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full bg-[#7C3AED] text-xs font-bold text-white">
                  1
                </div>
                <div>
                  <p className="text-sm font-medium text-[#1F2937]">找到分支点</p>
                  <p className="text-xs text-[#6B7280]">
                    将鼠标悬停在 AI 回复上，右侧会出现一个分支图标按钮
                  </p>
                </div>
              </div>

              <div className="flex items-start gap-3">
                <div className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full bg-[#7C3AED] text-xs font-bold text-white">
                  2
                </div>
                <div>
                  <p className="text-sm font-medium text-[#1F2937]">点击创建分支</p>
                  <p className="text-xs text-[#6B7280]">
                    点击分支按钮后，系统会复制该消息及之前的所有对话记录到一个新分支中
                  </p>
                </div>
              </div>

              <div className="flex items-start gap-3">
                <div className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full bg-[#7C3AED] text-xs font-bold text-white">
                  3
                </div>
                <div>
                  <p className="text-sm font-medium text-[#1F2937]">继续对话</p>
                  <p className="text-xs text-[#6B7280]">
                    在分支中发送新的消息，探索不同的对话方向，原始对话不受影响
                  </p>
                </div>
              </div>
            </div>

            <div className="mt-4 flex items-center gap-2 rounded-lg bg-[#FEF3C7] px-4 py-3">
              <span className="text-sm text-[#92400E]">
                提示：分支功能非常适合探索多种回答路径、对比不同方案、或回退到某个节点重新提问
              </span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
