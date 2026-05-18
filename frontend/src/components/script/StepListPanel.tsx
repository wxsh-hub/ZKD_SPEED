import { useCallback } from "react";
import { Clock, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useScriptStore } from "@/stores/scriptStore";
import { StepItemCard } from "./StepItemCard";
import { useParams } from "react-router-dom";
import { toast } from "sonner";

export function StepListPanel() {
  const { projectId } = useParams<{ projectId: string }>();
  const { steps, addStep, reorderSteps } = useScriptStore();

  const handleAddWait = useCallback(async () => {
    if (!projectId) return;
    const seconds = prompt("等待秒数:", "1");
    if (!seconds) return;
    const num = parseInt(seconds, 10);
    if (isNaN(num) || num <= 0) {
      toast.error("请输入有效的秒数");
      return;
    }
    await addStep(projectId, "wait_seconds", JSON.stringify({ seconds: num }));
    toast.success("已添加等待步骤");
  }, [projectId, addStep]);

  const handleDragStart = useCallback((e: React.DragEvent, index: number) => {
    e.dataTransfer.setData("text/plain", String(index));
    e.dataTransfer.effectAllowed = "move";
  }, []);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = "move";
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent, dropIndex: number) => {
      e.preventDefault();
      const dragIndex = parseInt(e.dataTransfer.getData("text/plain"), 10);
      if (dragIndex === dropIndex) return;

      const newOrder = [...steps];
      const [moved] = newOrder.splice(dragIndex, 1);
      newOrder.splice(dropIndex, 0, moved);
      reorderSteps(newOrder.map((s) => s.id));
    },
    [steps, reorderSteps]
  );

  return (
    <div className="flex w-80 flex-col border-l border-slate-200 bg-white">
      <div className="flex items-center justify-between border-b border-slate-100 px-3 py-2">
        <span className="text-xs font-medium text-slate-600">
          步骤列表 ({steps.length})
        </span>
        <Button
          size="sm"
          variant="ghost"
          className="h-7 px-2 text-xs"
          onClick={handleAddWait}
        >
          <Clock className="mr-1 h-3.5 w-3.5" />
          等待
        </Button>
      </div>
      <div className="flex-1 overflow-y-auto p-2">
        {steps.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-8 text-slate-400">
            <Plus className="mb-2 h-8 w-8" />
            <p className="text-xs">暂无步骤</p>
            <p className="text-[10px] text-slate-300">
              选择工具后在截图上标注
            </p>
          </div>
        ) : (
          <div className="space-y-2">
            {steps.map((step, idx) => (
              <StepItemCard
                key={step.id}
                step={step}
                index={idx}
                onDragStart={handleDragStart}
                onDragOver={handleDragOver}
                onDrop={handleDrop}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
