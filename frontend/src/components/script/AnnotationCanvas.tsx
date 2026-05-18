import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { toast } from "sonner";

import { useScriptStore } from "@/stores/scriptStore";
import { OperationToolbar } from "./OperationToolbar";
import { StepConfigDialog } from "./StepConfigDialog";
import type { OperationType } from "@/types/script";

interface DrawState {
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}

interface PendingAnnotation {
  type: OperationType;
  screenshotId: string;
  drawMode: "point" | "rect";
  // 原图坐标
  x?: number;
  y?: number;
  x1?: number;
  y1?: number;
  x2?: number;
  y2?: number;
}

export function AnnotationCanvas() {
  const { projectId } = useParams<{ projectId: string }>();
  const containerRef = useRef<HTMLDivElement>(null);
  const imgRef = useRef<HTMLImageElement>(null);

  const {
    screenshots,
    steps,
    selectedScreenshotId,
    annotationMode,
    addStep,
    addTemplateStep,
  } = useScriptStore();

  const [imgSize, setImgSize] = useState({ w: 0, h: 0 });
  const [displaySize, setDisplaySize] = useState({ w: 0, h: 0, offsetX: 0, offsetY: 0 });
  const [drawing, setDrawing] = useState(false);
  const [drawState, setDrawState] = useState<DrawState | null>(null);
  const [pending, setPending] = useState<PendingAnnotation | null>(null);

  const selectedScreenshot = screenshots.find((s) => s.id === selectedScreenshotId);
  const currentSteps = steps.filter((s) => s.screenshotId === selectedScreenshotId);

  // 计算显示尺寸
  useEffect(() => {
    const container = containerRef.current;
    if (!container || !selectedScreenshot) return;

    const updateSize = () => {
      const cw = container.clientWidth - 24; // padding
      const ch = container.clientHeight - 24;
      const imgRatio = selectedScreenshot.width / selectedScreenshot.height;
      const containerRatio = cw / ch;

      let dw: number, dh: number;
      if (imgRatio > containerRatio) {
        dw = cw;
        dh = cw / imgRatio;
      } else {
        dh = ch;
        dw = ch * imgRatio;
      }

      setImgSize({ w: selectedScreenshot.width, h: selectedScreenshot.height });
      setDisplaySize({
        w: dw,
        h: dh,
        offsetX: (cw - dw) / 2 + 12,
        offsetY: (ch - dh) / 2 + 12,
      });
    };

    updateSize();
    const observer = new ResizeObserver(updateSize);
    observer.observe(container);
    return () => observer.disconnect();
  }, [selectedScreenshot]);

  // 坐标映射：canvas → 原图
  const toOriginal = useCallback(
    (canvasX: number, canvasY: number) => {
      const x = Math.round((canvasX - displaySize.offsetX) * (imgSize.w / displaySize.w));
      const y = Math.round((canvasY - displaySize.offsetY) * (imgSize.h / displaySize.h));
      return { x: Math.max(0, Math.min(imgSize.w, x)), y: Math.max(0, Math.min(imgSize.h, y)) };
    },
    [displaySize, imgSize]
  );

  // 坐标映射：原图 → canvas
  const toCanvas = useCallback(
    (origX: number, origY: number) => {
      const x = origX * (displaySize.w / imgSize.w) + displaySize.offsetX;
      const y = origY * (displaySize.h / imgSize.h) + displaySize.offsetY;
      return { x, y };
    },
    [displaySize, imgSize]
  );

  const handleMouseDown = useCallback(
    (e: React.MouseEvent) => {
      if (!annotationMode || !selectedScreenshot) return;
      const rect = containerRef.current!.getBoundingClientRect();
      const cx = e.clientX - rect.left;
      const cy = e.clientY - rect.top;

      if (annotationMode.drawMode === "point") {
        const orig = toOriginal(cx, cy);
        setPending({
          type: annotationMode.type,
          screenshotId: selectedScreenshot.id,
          drawMode: "point",
          x: orig.x,
          y: orig.y,
        });
      } else if (annotationMode.drawMode === "rect") {
        setDrawing(true);
        setDrawState({ startX: cx, startY: cy, endX: cx, endY: cy });
      }
    },
    [annotationMode, selectedScreenshot, toOriginal]
  );

  const handleMouseMove = useCallback(
    (e: React.MouseEvent) => {
      if (!drawing) return;
      const rect = containerRef.current!.getBoundingClientRect();
      setDrawState((prev) =>
        prev
          ? { ...prev, endX: e.clientX - rect.left, endY: e.clientY - rect.top }
          : null
      );
    },
    [drawing]
  );

  const handleMouseUp = useCallback(() => {
    if (!drawing || !drawState || !annotationMode || !selectedScreenshot) return;
    setDrawing(false);

    const orig1 = toOriginal(drawState.startX, drawState.startY);
    const orig2 = toOriginal(drawState.endX, drawState.endY);

    if (Math.abs(orig1.x - orig2.x) < 5 && Math.abs(orig1.y - orig2.y) < 5) {
      setDrawState(null);
      return;
    }

    setPending({
      type: annotationMode.type,
      screenshotId: selectedScreenshot.id,
      drawMode: "rect",
      x1: Math.min(orig1.x, orig2.x),
      y1: Math.min(orig1.y, orig2.y),
      x2: Math.max(orig1.x, orig2.x),
      y2: Math.max(orig1.y, orig2.y),
    });
    setDrawState(null);
  }, [drawing, drawState, annotationMode, selectedScreenshot, toOriginal]);

  const handleConfigConfirm = useCallback(
    async (paramsJson: string) => {
      if (!pending || !projectId) return;

      const extraParams = JSON.parse(paramsJson);

      if (pending.type === "wait_image" && pending.drawMode === "rect") {
        await addTemplateStep({
          screenshotId: pending.screenshotId,
          projectId,
          x1: pending.x1!,
          y1: pending.y1!,
          x2: pending.x2!,
          y2: pending.y2!,
          timeoutS: extraParams.timeout_s ?? 10,
        });
      } else if (pending.drawMode === "point") {
        // 点击类操作：合并坐标和额外参数
        const fullParams = { x: pending.x, y: pending.y, ...extraParams };
        await addStep(projectId, pending.type, JSON.stringify(fullParams), pending.screenshotId);
      } else if (pending.drawMode === "rect") {
        // 矩形类操作：合并坐标和额外参数
        const fullParams = {
          x1: pending.x1, y1: pending.y1,
          x2: pending.x2, y2: pending.y2,
          ...extraParams,
        };
        await addStep(projectId, pending.type, JSON.stringify(fullParams), pending.screenshotId);
      }

      setPending(null);
      toast.success("已添加步骤");
    },
    [pending, projectId, addStep, addTemplateStep]
  );

  if (!selectedScreenshot) {
    return (
      <div className="flex flex-1 items-center justify-center bg-slate-50 text-sm text-slate-400">
        请从左侧选择一张截图
      </div>
    );
  }

  return (
    <div
      ref={containerRef}
      className="relative flex-1 overflow-hidden bg-slate-100"
    >
      <OperationToolbar />

      {/* 截图背景 */}
      <img
        ref={imgRef}
        src={`/api/ragent${selectedScreenshot.fileUrl}`}
        alt=""
        className="absolute select-none"
        style={{
          left: displaySize.offsetX,
          top: displaySize.offsetY,
          width: displaySize.w,
          height: displaySize.h,
        }}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        draggable={false}
      />

      {/* 已有标注叠加层 */}
      <svg
        className="pointer-events-none absolute inset-0"
        style={{ width: "100%", height: "100%" }}
      >
        {currentSteps.map((step) => {
          const params = JSON.parse(step.paramsJson);
          if (step.operationType === "click" || step.operationType === "long_press" || step.operationType === "input_text") {
            const pos = toCanvas(params.x, params.y);
            const color = step.operationType === "click" ? "#10b981" : step.operationType === "long_press" ? "#f59e0b" : "#6366f1";
            return (
              <g key={step.id}>
                <circle cx={pos.x} cy={pos.y} r={6} fill={color} opacity={0.8} />
                <circle cx={pos.x} cy={pos.y} r={10} fill="none" stroke={color} strokeWidth={1.5} opacity={0.5} />
              </g>
            );
          }
          if (step.operationType === "area_click" || step.operationType === "wait_image" || step.operationType === "scroll") {
            const p1 = toCanvas(params.x1, params.y1);
            const p2 = toCanvas(params.x2, params.y2);
            const color = step.operationType === "area_click" ? "#10b981" : step.operationType === "wait_image" ? "#8b5cf6" : "#06b6d4";
            return (
              <rect
                key={step.id}
                x={p1.x}
                y={p1.y}
                width={p2.x - p1.x}
                height={p2.y - p1.y}
                fill={color}
                fillOpacity={0.15}
                stroke={color}
                strokeWidth={1.5}
                strokeDasharray="4 2"
              />
            );
          }
          return null;
        })}

        {/* 绘制中的矩形 */}
        {drawState && (
          <rect
            x={Math.min(drawState.startX, drawState.endX)}
            y={Math.min(drawState.startY, drawState.endY)}
            width={Math.abs(drawState.endX - drawState.startX)}
            height={Math.abs(drawState.endY - drawState.startY)}
            fill="#10b981"
            fillOpacity={0.15}
            stroke="#10b981"
            strokeWidth={1.5}
            strokeDasharray="4 2"
          />
        )}
      </svg>

      {/* 坐标提示 */}
      <div className="absolute bottom-2 right-2 rounded bg-black/50 px-2 py-1 text-[10px] text-white">
        {selectedScreenshot.width} × {selectedScreenshot.height}
      </div>

      {/* 配置弹窗 */}
      {pending && (
        <StepConfigDialog
          operationType={pending.type}
          onCancel={() => setPending(null)}
          onConfirm={handleConfigConfirm}
        />
      )}
    </div>
  );
}
