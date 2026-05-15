import * as React from "react";
import { ChevronDown } from "lucide-react";

import { cn } from "@/lib/utils";
import { useModelCandidates } from "@/hooks/useModelCandidates";

interface ModelSelectorProps {
  selectedModelId: string;
  onSelect: (id: string) => void;
  disabled?: boolean;
  variant?: "rounded" | "pill";
}

export function ModelSelector({
  selectedModelId,
  onSelect,
  disabled = false,
  variant = "rounded"
}: ModelSelectorProps) {
  const [open, setOpen] = React.useState(false);
  const models = useModelCandidates();

  if (models.length === 0) return null;

  const btnClass = variant === "pill"
    ? "rounded-full border px-3 py-1.5 text-xs font-medium transition-all"
    : "rounded-lg border px-3 py-1.5 text-xs font-medium transition-all";

  return (
    <div className="relative">
      <button
        type="button"
        disabled={disabled}
        onClick={() => setOpen(!open)}
        className={cn(
          btnClass,
          "flex items-center gap-1",
          selectedModelId
            ? "border-[#C4B5FD] bg-[#EDE9FE] text-[#7C3AED]"
            : "border-transparent bg-[#F5F5F5] text-[#999999] hover:bg-[#EEEEEE]",
          disabled && "cursor-not-allowed opacity-60"
        )}
      >
        {selectedModelId
          ? models.find((m) => m.id === selectedModelId)?.id || selectedModelId
          : "默认模型"}
        <ChevronDown className="h-3 w-3" />
      </button>
      {open && (
        <>
          <div
            className="fixed inset-0 z-40"
            onClick={() => setOpen(false)}
          />
          <div className="absolute bottom-full left-0 z-50 mb-1 w-48 rounded-lg border border-[#E5E7EB] bg-white py-1 shadow-lg">
            <button
              type="button"
              className="w-full px-3 py-2 text-left text-xs text-[#666] hover:bg-[#F5F5F5]"
              onClick={() => {
                onSelect("");
                setOpen(false);
              }}
            >
              默认模型
            </button>
            {models.map((m) => (
              <button
                key={m.id}
                type="button"
                className={cn(
                  "w-full px-3 py-2 text-left text-xs hover:bg-[#F5F5F5]",
                  selectedModelId === m.id
                    ? "text-[#7C3AED] font-medium"
                    : "text-[#333]"
                )}
                onClick={() => {
                  onSelect(m.id);
                  setOpen(false);
                }}
              >
                {m.id}
                <span className="ml-1 text-[#999]">({m.provider})</span>
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
