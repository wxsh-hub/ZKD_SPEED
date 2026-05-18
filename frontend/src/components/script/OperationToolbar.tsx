import {
  MousePointerClick,
  Square,
  Hand,
  Scan,
  Clock,
  Type,
  ScrollText,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useScriptStore } from "@/stores/scriptStore";
import { ANNOTATION_MODES } from "@/types/script";

const ICONS: Record<string, React.ElementType> = {
  MousePointerClick,
  Square,
  Hand,
  Scan,
  Clock,
  Type,
  ScrollText,
};

export function OperationToolbar() {
  const { annotationMode, setAnnotationMode } = useScriptStore();

  return (
    <div className="absolute left-3 top-3 z-10 flex flex-col gap-1 rounded-lg border border-slate-200 bg-white/95 p-1.5 shadow-sm backdrop-blur-sm">
      {ANNOTATION_MODES.map((mode) => {
        const Icon = ICONS[mode.icon] ?? MousePointerClick;
        const active = annotationMode?.type === mode.type;
        return (
          <button
            key={mode.type}
            onClick={() => setAnnotationMode(active ? null : mode)}
            title={mode.label}
            className={cn(
              "flex h-8 w-8 items-center justify-center rounded-md transition-colors",
              active
                ? "bg-emerald-100 text-emerald-700"
                : "text-slate-500 hover:bg-slate-100 hover:text-slate-700"
            )}
          >
            <Icon className="h-4 w-4" />
          </button>
        );
      })}
    </div>
  );
}
