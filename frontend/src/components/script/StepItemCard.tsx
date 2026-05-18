import { GripVertical, Pencil, Trash2 } from "lucide-react";
import type { ScriptStep } from "@/types/script";
import { getOperationLabel, getStepSummary } from "@/types/script";
import { useScriptStore } from "@/stores/scriptStore";

const TYPE_COLORS: Record<string, string> = {
  click: "bg-emerald-100 text-emerald-700",
  area_click: "bg-emerald-100 text-emerald-700",
  long_press: "bg-amber-100 text-amber-700",
  wait_image: "bg-violet-100 text-violet-700",
  wait_seconds: "bg-blue-100 text-blue-700",
  input_text: "bg-indigo-100 text-indigo-700",
  scroll: "bg-cyan-100 text-cyan-700",
};

interface Props {
  step: ScriptStep;
  index: number;
  onDragStart: (e: React.DragEvent, index: number) => void;
  onDragOver: (e: React.DragEvent) => void;
  onDrop: (e: React.DragEvent, index: number) => void;
}

export function StepItemCard({ step, index, onDragStart, onDragOver, onDrop }: Props) {
  const { deleteStep, screenshots } = useScriptStore();
  const screenshot = screenshots.find((s) => s.id === step.screenshotId);

  return (
    <div
      draggable
      onDragStart={(e) => onDragStart(e, index)}
      onDragOver={onDragOver}
      onDrop={(e) => onDrop(e, index)}
      className="group flex items-start gap-2 rounded-lg border border-slate-200 bg-white p-2 transition-colors hover:border-slate-300"
    >
      <div className="mt-0.5 cursor-grab text-slate-300">
        <GripVertical className="h-4 w-4" />
      </div>

      {screenshot ? (
        <img
          src={`/api/ragent${screenshot.fileUrl}`}
          alt=""
          className="h-10 w-14 flex-shrink-0 rounded object-cover"
        />
      ) : (
        <div className="flex h-10 w-14 flex-shrink-0 items-center justify-center rounded bg-slate-100 text-[10px] text-slate-400">
          无截图
        </div>
      )}

      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1.5">
          <span
            className={`inline-block rounded px-1.5 py-0.5 text-[10px] font-medium ${
              TYPE_COLORS[step.operationType] ?? "bg-slate-100 text-slate-600"
            }`}
          >
            {getOperationLabel(step.operationType)}
          </span>
          <span className="text-[10px] text-slate-400">#{index + 1}</span>
        </div>
        <p className="mt-0.5 truncate text-xs text-slate-600">
          {getStepSummary(step)}
        </p>
      </div>

      <button
        onClick={() => deleteStep(step.id)}
        className="mt-0.5 flex h-6 w-6 items-center justify-center rounded text-slate-300 opacity-0 transition-opacity hover:bg-red-50 hover:text-red-500 group-hover:opacity-100"
      >
        <Trash2 className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}
