import { useCallback, useEffect, useRef, useState } from "react";
import {
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  Code,
  Copy,
  Download,
  FolderOpen,
  GripVertical,
  HelpCircle,
  ImagePlus,
  Loader2,
  MousePointerClick,
  Plus,
  Save,
  Trash2,
  Upload,
  X,
  Zap,
  Github,
} from "lucide-react";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { MainLayout } from "@/components/layout/MainLayout";
import { cn } from "@/lib/utils";
import {
  getProjectDetail,
  saveProject,
  uploadScreenshot,
  uploadScreenshots,
  previewScript,
  buildExe,
  getBuildStatus,
  uploadTemplate,
  exportProject,
  type ScriptProjectDetail,
  type ScriptScreenshot,
  type ScriptStep,
  type OperationType,
  type BuildStatus,
  OPERATION_LABELS,
  OPERATION_COLORS,
  OPERATION_HINTS,
  KEY_OPTIONS,
} from "@/services/scriptService";
import { ScreenshotGuideModal } from "@/components/script/ScreenshotGuideModal";

// 将相对路径的 fileUrl 补全为可通过代理访问的地址
function resolveFileUrl(url: string | null | undefined): string {
  if (!url) return "";
  if (url.startsWith("http://") || url.startsWith("https://")) return url;
  return `/api/ragent${url.startsWith("/") ? "" : "/"}${url}`;
}

// ============ Key Selector ============

function KeySelector({ value, onChange }: { value: string; onChange: (val: string) => void }) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const handleClick = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, []);

  const filtered = KEY_OPTIONS.filter((opt) => {
    const q = search.toLowerCase();
    return (
      opt.value.toLowerCase().includes(q) ||
      opt.label.toLowerCase().includes(q) ||
      opt.group.toLowerCase().includes(q)
    );
  });

  const grouped = filtered.reduce<Record<string, typeof KEY_OPTIONS>>((acc, opt) => {
    (acc[opt.group] ||= []).push(opt);
    return acc;
  }, {});

  const displayLabel = KEY_OPTIONS.find((o) => o.value === value)?.label ?? value;

  return (
    <div ref={containerRef} className="relative" data-step-index={0} data-field="key">
      <Input
        ref={inputRef}
        value={open ? search : displayLabel}
        onFocus={() => {
          setSearch("");
          setOpen(true);
        }}
        onChange={(e) => {
          setSearch(e.target.value);
          setOpen(true);
        }}
        onBlur={() => {
          setTimeout(() => {
            setOpen(false);
            if (search && !KEY_OPTIONS.some((o) => o.value === search)) {
              const match = KEY_OPTIONS.find(
                (o) => o.label.toLowerCase() === search.toLowerCase()
              );
              if (match) onChange(match.value);
            }
          }, 150);
        }}
        onKeyDown={(e) => {
          if (e.key === "Enter") {
            if (filtered.length === 1) {
              onChange(filtered[0].value);
              setOpen(false);
              inputRef.current?.blur();
            }
          }
          if (e.key === "Escape") {
            setOpen(false);
            inputRef.current?.blur();
          }
        }}
        placeholder="搜索或输入按键名..."
        className="h-7 text-xs"
      />
      {open && (
        <div className="absolute left-0 top-full z-50 mt-1 max-h-52 w-56 overflow-y-auto rounded-md border border-slate-200 bg-white py-1 shadow-lg">
          {Object.entries(grouped).length === 0 ? (
            <div className="px-3 py-2 text-xs text-slate-400">无匹配按键</div>
          ) : (
            Object.entries(grouped).map(([group, items]) => (
              <div key={group}>
                <div className="px-3 py-1 text-[10px] font-medium text-slate-400 bg-slate-50">{group}</div>
                {items.map((opt) => (
                  <button
                    key={opt.value}
                    type="button"
                    onMouseDown={(e) => {
                      e.preventDefault();
                      onChange(opt.value);
                      setOpen(false);
                    }}
                    className={cn(
                      "flex w-full items-center px-3 py-1.5 text-xs hover:bg-violet-50",
                      value === opt.value && "bg-violet-100 text-violet-700 font-medium"
                    )}
                  >
                    <span className="flex-1 text-left">{opt.label}</span>
                    <span className="ml-2 text-[10px] text-slate-400">{opt.value}</span>
                  </button>
                ))}
              </div>
            ))
          )}
          <div className="border-t border-slate-100 px-3 py-1.5 text-[10px] text-slate-400">
            支持组合键，如输入 ctrl+a
          </div>
        </div>
      )}
    </div>
  );
}

// ============ Step Editor ============

interface StepItemProps {
  step: ScriptStep;
  index: number;
  screenshots: ScriptScreenshot[];
  onUpdate: (index: number, patch: Partial<ScriptStep>) => void;
  onDelete: (index: number) => void;
  onMoveUp: (index: number) => void;
  onMoveDown: (index: number) => void;
  onUploadTemplateDirect: (index: number, file: File) => void;
  isFirst: boolean;
  isLast: boolean;
  isActive: boolean;
  onClick: () => void;
}

function StepItem({
  step,
  index,
  screenshots,
  onUpdate,
  onDelete,
  onMoveUp,
  onMoveDown,
  onUploadTemplateDirect,
  isFirst,
  isLast,
  isActive,
  onClick,
}: StepItemProps) {
  const tplInputRef = useRef<HTMLInputElement>(null);
  const renderParams = () => {
    const params = step.paramsJson || {};
    switch (step.operationType) {
      case "click":
      case "double_click":
      case "mouse_move":
        return (
          <div className="flex gap-2">
            <Input
              type="number"
              data-step-index={index}
              data-field="x"
              value={params.x !== undefined && params.x !== null ? String(params.x) : ""}
              onChange={(e) => {
                const v = e.target.value;
                onUpdate(index, { paramsJson: { ...params, x: v === "" ? "" : Number(v) } });
              }}
              placeholder="X坐标"
              className="h-7 w-28 text-xs"
            />
            <Input
              type="number"
              data-step-index={index}
              data-field="y"
              value={params.y !== undefined && params.y !== null ? String(params.y) : ""}
              onChange={(e) => {
                const v = e.target.value;
                onUpdate(index, { paramsJson: { ...params, y: v === "" ? "" : Number(v) } });
              }}
              placeholder="Y坐标"
              className="h-7 w-28 text-xs"
            />
          </div>
        );
      case "area_click":
      case "area_long_press":
        return (
          <div className="flex flex-wrap gap-2">
            <Input type="number" data-step-index={index} data-field="x1"
              value={params.x1 !== undefined && params.x1 !== null ? String(params.x1) : ""}
              onChange={(e) => { const v = e.target.value; onUpdate(index, { paramsJson: { ...params, x1: v === "" ? "" : Number(v) } }); }}
              placeholder="左上X" className="h-7 w-20 text-xs" />
            <Input type="number" data-step-index={index} data-field="y1"
              value={params.y1 !== undefined && params.y1 !== null ? String(params.y1) : ""}
              onChange={(e) => { const v = e.target.value; onUpdate(index, { paramsJson: { ...params, y1: v === "" ? "" : Number(v) } }); }}
              placeholder="左上Y" className="h-7 w-20 text-xs" />
            <Input type="number" data-step-index={index} data-field="x2"
              value={params.x2 !== undefined && params.x2 !== null ? String(params.x2) : ""}
              onChange={(e) => { const v = e.target.value; onUpdate(index, { paramsJson: { ...params, x2: v === "" ? "" : Number(v) } }); }}
              placeholder="右下X" className="h-7 w-20 text-xs" />
            <Input type="number" data-step-index={index} data-field="y2"
              value={params.y2 !== undefined && params.y2 !== null ? String(params.y2) : ""}
              onChange={(e) => { const v = e.target.value; onUpdate(index, { paramsJson: { ...params, y2: v === "" ? "" : Number(v) } }); }}
              placeholder="右下Y" className="h-7 w-20 text-xs" />
            {step.operationType === "area_long_press" && (
              <Input type="number" data-step-index={index} data-field="duration"
                value={params.duration !== undefined && params.duration !== null ? String(params.duration) : ""}
                onChange={(e) => { const v = e.target.value; onUpdate(index, { paramsJson: { ...params, duration: v === "" ? "" : Number(v) } }); }}
                placeholder="长按时长(ms)" className="h-7 w-28 text-xs" />
            )}
          </div>
        );
      case "long_press":
        return (
          <div className="flex flex-col gap-1.5">
            <div className="flex gap-2">
              <Input type="number" data-step-index={index} data-field="x"
                value={params.x !== undefined && params.x !== null ? String(params.x) : ""}
                onChange={(e) => { const v = e.target.value; onUpdate(index, { paramsJson: { ...params, x: v === "" ? "" : Number(v) } }); }}
                placeholder="X坐标" className="h-7 w-28 text-xs" />
              <Input type="number" data-step-index={index} data-field="y"
                value={params.y !== undefined && params.y !== null ? String(params.y) : ""}
                onChange={(e) => { const v = e.target.value; onUpdate(index, { paramsJson: { ...params, y: v === "" ? "" : Number(v) } }); }}
                placeholder="Y坐标" className="h-7 w-28 text-xs" />
            </div>
            <Input type="number" data-step-index={index} data-field="duration"
              value={params.duration !== undefined && params.duration !== null ? String(params.duration) : ""}
              onChange={(e) => { const v = e.target.value; onUpdate(index, { paramsJson: { ...params, duration: v === "" ? "" : Number(v) } }); }}
              placeholder="长按时长(ms)" className="h-7 w-full text-xs" />
          </div>
        );
      case "wait_seconds":
        return (
          <div className="flex items-center gap-2">
            <Input
              type="number"
              step="0.1"
              min="0.1"
              data-step-index={index}
              data-field="seconds"
              value={params.seconds !== undefined && params.seconds !== null ? String(params.seconds) : ""}
              onChange={(e) => {
                const v = e.target.value;
                onUpdate(index, {
                  paramsJson: { ...params, seconds: v === "" ? "" : Number(v) },
                });
              }}
              placeholder="等待秒数"
              className="h-7 w-24 text-xs"
            />
            <span className="text-xs text-slate-400">秒</span>
          </div>
        );
      case "input_text":
        return (
          <Input
            data-step-index={index}
            data-field="text"
            value={(params.text as string) ?? ""}
            onChange={(e) =>
              onUpdate(index, {
                paramsJson: { ...params, text: e.target.value },
              })
            }
            placeholder="输入内容..."
            className="h-7 text-xs"
          />
        );
      case "key_press":
        return (
          <KeySelector
            value={(params.key as string) ?? ""}
            onChange={(val) => onUpdate(index, { paramsJson: { ...params, key: val } })}
          />
        );
      case "key_long_press":
        return (
          <div className="flex flex-col gap-1.5">
            <KeySelector
              value={(params.key as string) ?? ""}
              onChange={(val) => onUpdate(index, { paramsJson: { ...params, key: val } })}
            />
            <Input
              type="number"
              data-step-index={index}
              data-field="duration"
              value={params.duration !== undefined && params.duration !== null ? String(params.duration) : ""}
              onChange={(e) => {
                const v = e.target.value;
                onUpdate(index, { paramsJson: { ...params, duration: v === "" ? "" : Number(v) } });
              }}
              placeholder="长按时长(ms)"
              className="h-7 w-full text-xs"
            />
          </div>
        );
      case "scroll":
        return (
          <div className="flex flex-col gap-1.5">
            <div className="flex items-center gap-2">
              <Input
                type="number"
                step="100"
                min="100"
                data-step-index={index}
                data-field="duration"
                value={params.duration !== undefined && params.duration !== null ? String(params.duration) : ""}
                onChange={(e) => {
                  const v = e.target.value;
                  onUpdate(index, { paramsJson: { ...params, duration: v === "" ? "" : Number(v) } });
                }}
                placeholder="时长(ms)"
                className="h-7 w-24 text-xs"
              />
              <span className="text-xs text-slate-400">ms</span>
              {params.x1 !== undefined && params.x1 !== 0 && (
                <span className="text-xs text-slate-500">
                  ({params.x1},{params.y1})→({params.x2},{params.y2})
                </span>
              )}
            </div>
            <span className="text-xs text-slate-400">在图片上拖拽画出滑动路径</span>
          </div>
        );
      case "for_start":
        return (
          <div className="flex items-center gap-2">
            <span className="text-xs text-slate-500">循环</span>
            <Input
              type="number"
              min="1"
              value={params.count !== undefined && params.count !== null ? String(params.count) : ""}
              onChange={(e) => {
                const v = e.target.value;
                onUpdate(index, { paramsJson: { ...params, count: v === "" ? "" : Number(v) } });
              }}
              onBlur={(e) => {
                if (!e.target.value || Number(e.target.value) < 1) {
                  onUpdate(index, { paramsJson: { ...params, count: 1 } });
                }
              }}
              placeholder="次数"
              className="h-7 w-16 text-xs"
            />
            <span className="text-xs text-slate-400">次</span>
          </div>
        );
      case "for_end":
        return <span className="text-xs text-slate-400">循环结束</span>;
      case "if_image":
        const hasRegion = params.x1 !== undefined && params.x2 !== undefined;
        const hasTemplate = !!params.templateUrl;
        return (
          <div className="flex flex-col gap-1.5">
            <div className="flex items-center gap-2">
              <span className="text-xs text-slate-500">相似度 ≥</span>
              <Input
                type="number"
                step="0.01"
                min="0"
                max="1"
                value={params.similarity !== undefined ? String(params.similarity) : ""}
                onChange={(e) => {
                  const v = e.target.value;
                  onUpdate(index, { paramsJson: { ...params, similarity: v === "" ? "" : Number(v) } });
                }}
                onBlur={(e) => {
                  const v = Number(e.target.value);
                  if (!e.target.value || v < 0 || v > 1) {
                    onUpdate(index, { paramsJson: { ...params, similarity: 0.95 } });
                  }
                }}
                className="h-7 w-20 text-xs"
              />
            </div>
            {/* 模式1：画布圈区域（编译时后端截取） */}
            {hasRegion && !hasTemplate && (
              <div className="flex items-center gap-2">
                <div className="flex items-center gap-1 rounded border border-rose-200 bg-rose-50 px-2 py-1 text-[11px] text-rose-600">
                  <MousePointerClick className="h-3 w-3" />
                  区域截取
                </div>
                <span className="text-[10px] text-slate-400">
                  ({params.x1},{params.y1}) → ({params.x2},{params.y2})
                </span>
                <button
                  onClick={() => {
                    onUpdate(index, { paramsJson: { ...params, x1: undefined, y1: undefined, x2: undefined, y2: undefined, cropScreenshotId: undefined } });
                  }}
                  className="text-[10px] text-slate-400 hover:text-red-500"
                >
                  清除
                </button>
              </div>
            )}
            {/* 模式2：直接上传模板 */}
            <div className="flex items-center gap-2">
              <input
                ref={tplInputRef}
                type="file"
                accept="image/*"
                className="hidden"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) {
                    onUploadTemplateDirect(index, file);
                    if (tplInputRef.current) tplInputRef.current.value = "";
                  }
                }}
              />
              <button
                onClick={() => tplInputRef.current?.click()}
                className="flex items-center gap-1 rounded border border-slate-200 bg-slate-50 px-2 py-1 text-[11px] text-slate-600 hover:bg-slate-100"
              >
                <Upload className="h-3 w-3" />
                {hasRegion ? "改为上传模板" : "上传模板"}
              </button>
              {hasTemplate && (
                <>
                  <a
                    href={resolveFileUrl(params.templateUrl as string)}
                    target="_blank"
                    rel="noreferrer"
                    className="flex items-center gap-1 rounded border border-emerald-200 bg-emerald-50 px-2 py-1 text-[11px] text-emerald-600 hover:bg-emerald-100"
                  >
                    <ImagePlus className="h-3 w-3" />
                    查看模板
                  </a>
                  <span className="text-[10px] text-emerald-500">已设置</span>
                </>
              )}
            </div>
            <p className="text-[10px] text-slate-400">在画布上拖拽圈出检测区域，或直接上传模板图片</p>
            <span className="text-xs text-slate-400">上传模板图片或在截图上拖拽圈出检测区域</span>
          </div>
        );
      case "if_ai":
        const aiHasRegion = params.x1 !== undefined && params.x2 !== undefined;
        return (
          <div className="flex flex-col gap-1.5">
            {/* 区域坐标显示 */}
            {aiHasRegion ? (
              <div className="flex items-center gap-2">
                <div className="flex items-center gap-1 rounded border border-violet-200 bg-violet-50 px-2 py-1 text-[11px] text-violet-600">
                  <MousePointerClick className="h-3 w-3" />
                  AI 识别区域
                </div>
                <span className="text-[10px] text-slate-400">
                  ({params.x1},{params.y1}) → ({params.x2},{params.y2})
                </span>
                <span className="text-[10px] text-slate-400">拖拽可重新选区</span>
              </div>
            ) : (
              <span className="text-[10px] text-slate-400">在画布上拖拽圈出 AI 识别区域</span>
            )}
            {/* AI 判断语句 */}
            <div className="flex items-center gap-2">
              <span className="text-xs text-slate-500">判断</span>
              <Input
                placeholder="如：屏幕上是否有登录按钮"
                value={params.prompt || ""}
                onChange={(e) => {
                  onUpdate(index, { paramsJson: { ...params, prompt: e.target.value } });
                }}
                className="h-7 flex-1 text-xs"
              />
            </div>
            {/* 相似度 */}
            <div className="flex items-center gap-2">
              <span className="text-xs text-slate-500">相似度</span>
              <Input
                type="number"
                step="0.05"
                min="0"
                max="1"
                value={params.similarity !== undefined ? String(params.similarity) : ""}
                onChange={(e) => {
                  const v = e.target.value;
                  onUpdate(index, { paramsJson: { ...params, similarity: v === "" ? "" : Number(v) } });
                }}
                onBlur={(e) => {
                  const v = Number(e.target.value);
                  if (!e.target.value || v < 0 || v > 1) {
                    onUpdate(index, { paramsJson: { ...params, similarity: 0.8 } });
                  }
                }}
                className="h-7 w-20 text-xs"
              />
            </div>
          </div>
        );
      case "if_random":
        return (
          <div className="flex items-center gap-2">
            <span className="text-xs text-slate-500">概率</span>
            <Input
              type="number"
              step="0.01"
              min="0"
              max="1"
              value={params.probability !== undefined ? String(params.probability) : ""}
              onChange={(e) => {
                const v = e.target.value;
                onUpdate(index, { paramsJson: { ...params, probability: v === "" ? "" : Number(v) } });
              }}
              onBlur={(e) => {
                const v = Number(e.target.value);
                if (!e.target.value || v < 0 || v > 1) {
                  onUpdate(index, { paramsJson: { ...params, probability: 0.5 } });
                }
              }}
              className="h-7 w-20 text-xs"
            />
          </div>
        );
      case "else":
        return <span className="text-xs text-amber-600 font-medium">否则（条件不满足时执行）</span>;
      case "if_end":
        return <span className="text-xs text-slate-400">条件结束</span>;
      default:
        return null;
    }
  };

  return (
    <div
      onClick={onClick}
      className={cn(
        "group flex items-start gap-2 rounded-lg border p-3 transition-colors cursor-pointer",
        isActive
          ? "border-violet-300 bg-violet-50/50"
          : "border-slate-200 bg-white hover:border-slate-300"
      )}
    >
      <div className="flex flex-col items-center gap-0.5 pt-0.5">
        <button
          onClick={(e) => {
            e.stopPropagation();
            onMoveUp(index);
          }}
          disabled={isFirst}
          className="text-slate-300 hover:text-slate-600 disabled:opacity-30"
        >
          <GripVertical className="h-3 w-3 rotate-180" />
        </button>
        <span className="text-[10px] font-medium text-slate-400">{index + 1}</span>
        <button
          onClick={(e) => {
            e.stopPropagation();
            onMoveDown(index);
          }}
          disabled={isLast}
          className="text-slate-300 hover:text-slate-600 disabled:opacity-30"
        >
          <GripVertical className="h-3 w-3" />
        </button>
      </div>

      <div className="min-w-0 flex-1">
        <div className="mb-0.5 flex items-center gap-2">
          <Badge
            variant="outline"
            className={cn("text-[10px] px-1.5 py-0", OPERATION_COLORS[step.operationType])}
          >
            {OPERATION_LABELS[step.operationType]}
          </Badge>
          {step.screenshotId && (
            <span className="text-[10px] text-slate-400 truncate">
              关联截图 #
              {screenshots.findIndex((s) => s.id === step.screenshotId) + 1}
            </span>
          )}
        </div>
        <div onClick={(e) => {
          const target = e.target as HTMLElement;
          if (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.closest("[data-step-index]")) {
            e.stopPropagation();
          }
        }}>
          {renderParams()}
        </div>
        {step.operationType !== "scroll" && (
          <span className="text-xs text-slate-400">{OPERATION_HINTS[step.operationType]}</span>
        )}
      </div>

      <button
        onClick={(e) => {
          e.stopPropagation();
          onDelete(index);
        }}
        className="flex h-6 w-6 items-center justify-center rounded text-slate-300 opacity-0 transition-opacity hover:bg-red-50 hover:text-red-500 group-hover:opacity-100"
      >
        <Trash2 className="h-3 w-3" />
      </button>
    </div>
  );
}

// ============ Insert Step Button ============

interface InsertStepDropdownProps {
  onInsert: (type: OperationType) => void;
  elseAllowed: boolean;
}

function hasUnclosedIf(steps: ScriptStep[], beforeIndex: number): boolean {
  let depth = 0;
  for (let i = 0; i < beforeIndex && i < steps.length; i++) {
    const t = steps[i].operationType;
    if (t === "if_image" || t === "if_random") depth++;
    else if (t === "if_end") depth = Math.max(0, depth - 1);
    else if (t === "else" && depth > 0) depth--;
  }
  return depth > 0;
}

function InsertStepDropdown({ onInsert, elseAllowed }: InsertStepDropdownProps) {
  return (
    <div className="flex items-center justify-center py-1.5">
      <div className="h-px flex-1 bg-slate-200" />
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button className="mx-2 flex h-7 w-7 items-center justify-center rounded-full border border-dashed border-slate-300 bg-white text-slate-400 transition-colors hover:border-violet-400 hover:text-violet-500 hover:bg-violet-50">
            <Plus className="h-4 w-4" />
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="center" side="right">
          {(Object.keys(OPERATION_LABELS) as OperationType[]).map((type) => (
            <DropdownMenuItem
              key={type}
              onClick={() => onInsert(type)}
              disabled={type === "else" && !elseAllowed}
            >
              <span
                className={cn(
                  "inline-block w-2 h-2 rounded-full mr-2",
                  OPERATION_COLORS[type]
                )}
              />
              {OPERATION_LABELS[type]}
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>
      <div className="h-px flex-1 bg-slate-200" />
    </div>
  );
}

// ============ Guide Modal ============

const GUIDE_STEPS = [
  {
    title: "",
    description: "",
    image: "",
    isTitlePage: true,
  },
  {
    title: "第 1 步：下载截图工具",
    description: "首先下载截图工具，用它截取你需要自动化的应用或游戏画面。截图时请确保画面清晰、分辨率合适。",
    image: "/guide/进入脚本/下载截图工具.png",
  },
  {
    title: "第 2 步：上传截图",
    description: "点击左侧截图列表上方的 + 按钮，上传游戏或应用的截图。支持单张上传或整个文件夹批量上传。截图会自动压缩并存储。",
    image: "/guide/进入脚本/上传图片或者文件夹.png",
  },
  {
    title: "第 3 步：添加操作步骤",
    description: "点击底部工具栏的操作类型按钮（点击、区域点击、长按、输入文字、滚动、等待），在右侧步骤列表中添加一个新步骤。",
    image: "/guide/进入脚本/增加脚本操作.png",
  },
  {
    title: "第 4 步：标注坐标",
    description: "选中右侧的某个步骤，然后在中间画布上点击或框选来标注操作位置。坐标会自动填入步骤参数中，也可以手动输入。",
    image: "/guide/进入脚本/填写操作坐标.png",
  },
  {
    title: "第 5 步：保存并编译",
    description: "点击右上角「保存」按钮保存项目，然后点击「编译脚本」生成可执行文件。编译完成后可下载 EXE 文件。",
    image: "/guide/进入脚本/保存编译下载脚本.png",
  },
];

const GUIDE_DISMISSED_KEY = "script_guide_dismissed";

interface ScriptGuideModalProps {
  open: boolean;
  onClose: () => void;
}

function ScriptGuideModal({ open, onClose }: ScriptGuideModalProps) {
  const [step, setStep] = useState(0);

  if (!open) return null;

  const current = GUIDE_STEPS[step];
  const isFirst = step === 0;
  const isLast = step === GUIDE_STEPS.length - 1;
  const isTitlePage = (current as any).isTitlePage === true;

  return (
    <div className="absolute inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="mx-4 w-full max-w-[680px] overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl">
        {/* header */}
        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-3">
          <h2 className="text-sm font-semibold text-slate-900">脚本生成器使用指南</h2>
          <button
            onClick={onClose}
            className="flex h-7 w-7 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* content */}
        <div className="px-5 py-5">
          {isTitlePage ? (
            <div className="flex flex-col items-center justify-center py-16">
              <MousePointerClick className="mb-4 h-12 w-12 text-violet-500" />
              <h1 className="text-2xl font-bold text-slate-900">创建脚本指南</h1>
            </div>
          ) : (
            <>
              {/* image */}
              <div className="mb-4 max-h-[420px] overflow-hidden rounded-lg border border-slate-200 bg-slate-50">
                {current.image ? (
                  <img src={current.image} alt={current.title} className="w-full object-contain" />
                ) : (
                  <div className="flex h-full items-center justify-center text-center text-slate-400">
                    <div>
                      <MousePointerClick className="mx-auto mb-2 h-8 w-8" />
                      <p className="text-xs">引导截图占位</p>
                    </div>
                  </div>
                )}
              </div>

              <h3 className="mb-1.5 text-sm font-semibold text-slate-800">{current.title}</h3>
              <p className="text-xs leading-relaxed text-slate-500">{current.description}</p>
            </>
          )}
        </div>

        {/* footer */}
        <div className="flex items-center justify-between border-t border-slate-100 px-5 py-3">
          {/* dots */}
          <div className="flex gap-1.5">
            {GUIDE_STEPS.map((_, i) => (
              <button
                key={i}
                onClick={() => setStep(i)}
                className={`h-2 w-2 rounded-full transition-colors ${
                  i === step ? "bg-violet-500" : "bg-slate-300 hover:bg-slate-400"
                }`}
              />
            ))}
          </div>

          {/* buttons */}
          <div className="flex gap-2">
            {!isFirst && (
              <button
                onClick={() => setStep(step - 1)}
                className="flex items-center gap-1 rounded-md border border-slate-200 px-3 py-1.5 text-xs text-slate-600 hover:bg-slate-50"
              >
                <ChevronLeft className="h-3 w-3" />
                上一步
              </button>
            )}
            {isLast ? (
              <button
                onClick={onClose}
                className="rounded-md bg-violet-600 px-4 py-1.5 text-xs font-medium text-white hover:bg-violet-700"
              >
                开始使用
              </button>
            ) : (
              <button
                onClick={() => setStep(step + 1)}
                className="flex items-center gap-1 rounded-md bg-violet-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-violet-700"
              >
                下一步
                <ChevronRight className="h-3 w-3" />
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

// ============ Build Mode Modal ============

interface BuildModeModalProps {
  open: boolean;
  onClose: () => void;
  onSelect: (mode: "bat" | "github") => void;
}

function BuildModeModal({ open, onClose, onSelect }: BuildModeModalProps) {
  const [selected, setSelected] = useState<"bat" | "github">("bat");

  if (!open) return null;

  return (
    <div className="absolute inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="mx-4 w-full max-w-[480px] overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl">
        {/* header */}
        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-3">
          <h2 className="text-sm font-semibold text-slate-900">选择构建模式</h2>
          <button
            onClick={onClose}
            className="flex h-7 w-7 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* options */}
        <div className="space-y-3 px-5 py-4">
          {/* BAT mode */}
          <button
            onClick={() => setSelected("bat")}
            className={`w-full rounded-lg border-2 p-4 text-left transition-all ${
              selected === "bat"
                ? "border-violet-500 bg-violet-50"
                : "border-slate-200 hover:border-slate-300"
            }`}
          >
            <div className="flex items-center gap-3">
              <div className={`flex h-10 w-10 items-center justify-center rounded-lg ${
                selected === "bat" ? "bg-violet-500 text-white" : "bg-slate-100 text-slate-500"
              }`}>
                <Zap className="h-5 w-5" />
              </div>
              <div className="flex-1">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-semibold text-slate-900">BAT 模式</span>
                  <span className="rounded-full bg-green-100 px-2 py-0.5 text-[10px] font-medium text-green-700">推荐</span>
                </div>
                <p className="mt-0.5 text-xs text-slate-500">直接分发 .py + .bat 文件，几秒完成</p>
              </div>
            </div>
            <div className="mt-3 rounded-md bg-slate-50 px-3 py-2">
              <p className="text-[11px] leading-relaxed text-slate-600">
                <span className="font-medium text-green-700">优点：</span>打包速度极快，无编译依赖
              </p>
              <p className="mt-1 text-[11px] leading-relaxed text-slate-600">
                <span className="font-medium text-amber-700">注意：</span>用户需安装 Python 环境，下载后双击 run.bat 运行
              </p>
            </div>
          </button>

          {/* GitHub mode */}
          <button
            onClick={() => setSelected("github")}
            className={`w-full rounded-lg border-2 p-4 text-left transition-all ${
              selected === "github"
                ? "border-violet-500 bg-violet-50"
                : "border-slate-200 hover:border-slate-300"
            }`}
          >
            <div className="flex items-center gap-3">
              <div className={`flex h-10 w-10 items-center justify-center rounded-lg ${
                selected === "github" ? "bg-violet-500 text-white" : "bg-slate-100 text-slate-500"
              }`}>
                <Github className="h-5 w-5" />
              </div>
              <div className="flex-1">
                <span className="text-sm font-semibold text-slate-900">EXE 编译模式</span>
                <p className="mt-0.5 text-xs text-slate-500">编译为独立 EXE 可执行文件</p>
              </div>
            </div>
            <div className="mt-3 rounded-md bg-slate-50 px-3 py-2">
              <p className="text-[11px] leading-relaxed text-slate-600">
                <span className="font-medium text-green-700">优点：</span>用户无需安装 Python，双击即可运行
              </p>
              <p className="mt-1 text-[11px] leading-relaxed text-slate-600">
                <span className="font-medium text-amber-700">注意：</span>需要几分钟编译时间，离开页面会继续编译
              </p>
            </div>
          </button>
        </div>

        {/* footer */}
        <div className="flex justify-end border-t border-slate-100 px-5 py-3">
          <Button
            size="sm"
            onClick={() => {
              onSelect(selected);
              onClose();
            }}
          >
            开始构建
          </Button>
        </div>
      </div>
    </div>
  );
}

// ============ Python Guide Modal ============

interface PythonGuideModalProps {
  open: boolean;
  onClose: () => void;
  downloadUrl?: string | null;
  projectName?: string;
}

function PythonGuideModal({ open, onClose, downloadUrl, projectName }: PythonGuideModalProps) {
  if (!open) return null;

  return (
    <div className="absolute inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="mx-4 w-full max-w-[520px] overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl">
        {/* header */}
        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-3">
          <h2 className="text-sm font-semibold text-slate-900">使用说明</h2>
          <button
            onClick={onClose}
            className="flex h-7 w-7 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* content */}
        <div className="px-5 py-4">
          <div className="mb-4 rounded-lg bg-green-50 border border-green-200 px-4 py-3">
            <p className="text-sm font-medium text-green-800">打包完成！</p>
            <p className="mt-1 text-xs text-green-700">
              已生成 {projectName}_bat.zip，包含 script.py、run.bat 和模板图片
            </p>
          </div>

          <h3 className="mb-3 text-sm font-semibold text-slate-900">运行步骤：</h3>

          <div className="space-y-4">
            {/* Step 1 */}
            <div className="flex gap-3">
              <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-violet-100 text-xs font-bold text-violet-700">
                1
              </div>
              <div>
                <p className="text-sm font-medium text-slate-800">下载并解压 zip 文件</p>
                <p className="mt-0.5 text-xs text-slate-500">解压后会看到 script.py、run.bat 和 templates 文件夹</p>
              </div>
            </div>

            {/* Step 2 */}
            <div className="flex gap-3">
              <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-violet-100 text-xs font-bold text-violet-700">
                2
              </div>
              <div>
                <p className="text-sm font-medium text-slate-800">安装 Python 环境</p>
                <p className="mt-0.5 text-xs text-slate-500">如果已安装 Python 可跳过此步</p>
                <a
                  href="https://www.python.org/ftp/python/3.11.9/python-3.11.9-amd64.exe"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="mt-2 inline-flex items-center gap-1.5 rounded-md bg-blue-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-blue-700 transition-colors"
                >
                  <Download className="h-3.5 w-3.5" />
                  下载 Python 3.11.9
                </a>
                <div className="mt-2 rounded-md bg-amber-50 border border-amber-200 px-3 py-2">
                  <p className="text-[11px] font-medium text-amber-800">安装时务必勾选：</p>
                  <p className="mt-1 text-[11px] text-amber-700">
                    ☑ <span className="font-semibold">Add Python to PATH</span>（添加到环境变量）
                  </p>
                  <p className="mt-1 text-[11px] text-amber-600">
                    如果忘记勾选，可以在系统设置中手动添加 Python 路径到 PATH
                  </p>
                </div>
              </div>
            </div>

            {/* Step 3 */}
            <div className="flex gap-3">
              <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-violet-100 text-xs font-bold text-violet-700">
                3
              </div>
              <div>
                <p className="text-sm font-medium text-slate-800">双击 run.bat 运行脚本</p>
                <p className="mt-0.5 text-xs text-slate-500">确保 script.py 和 templates 文件夹在同一目录下</p>
              </div>
            </div>
          </div>
        </div>

        {/* footer */}
        <div className="flex items-center justify-between border-t border-slate-100 px-5 py-3">
          {downloadUrl ? (
            <a
              href={downloadUrl}
              download={`${projectName || "script"}_bat.zip`}
              onClick={onClose}
              className="inline-flex items-center gap-1.5 rounded-md bg-violet-600 px-4 py-2 text-xs font-medium text-white hover:bg-violet-700 transition-colors"
            >
              <Download className="h-3.5 w-3.5" />
              下载压缩包
            </a>
          ) : <div />}
          <Button size="sm" onClick={onClose}>
            我知道了
          </Button>
        </div>
      </div>
    </div>
  );
}

// ============ Build Guide Modal ============

const BUILD_GUIDE_STEPS = [
  {
    title: "",
    description: "",
    image: "",
    isTitlePage: true,
  },
  {
    title: "第 1 步：杀毒软件隔离区找回",
    description:
      "部分杀毒软件（如 360、火绒、Windows Defender）可能会将编译后的 EXE 误判为威胁并自动隔离。请打开杀毒软件的「隔离区」或「病毒和威胁防护历史记录」，找到被拦截的文件并选择「还原」。",
    image: "/guide/编译脚本/找回隔离文件.png",
  },
  {
    title: "第 2 步：添加杀毒软件信任",
    description:
      "为避免再次被拦截，请将还原后的 EXE 文件或所在文件夹添加到杀毒软件的「白名单」或「排除项」中。Windows Defender 路径：设置 → 隐私和安全性 → Windows 安全中心 → 病毒和威胁防护 → 管理设置 → 排除项。",
    image: "/guide/编译脚本/信任文件.png",
  },
  {
    title: "第 3 步：Windows SmartScreen 警告",
    description:
      "双击运行 EXE 时，Windows 可能弹出「Windows 已保护你的电脑」的蓝色警告。这是因为 EXE 未经过微软官方签名认证。请点击窗口底部的「更多信息」链接。",
    image: "/guide/编译脚本/windows拦截显示更多信息.png",
  },
  {
    title: "第 4 步：仍要运行",
    description:
      "点击「更多信息」后，窗口会显示应用的发布者信息。确认无误后，点击底部的「仍要运行」按钮即可正常启动程序。下次运行时不会再弹出此警告。",
    image: "/guide/编译脚本/windows仍要运行.png",
  },
];

interface BuildGuideModalProps {
  open: boolean;
  onClose: () => void;
  downloadUrl?: string | null;
  projectName?: string;
}

function BuildGuideModal({ open, onClose, downloadUrl, projectName }: BuildGuideModalProps) {
  const [step, setStep] = useState(0);

  if (!open) return null;

  const current = BUILD_GUIDE_STEPS[step];
  const isFirst = step === 0;
  const isLast = step === BUILD_GUIDE_STEPS.length - 1;
  const isTitlePage = (current as any).isTitlePage === true;

  return (
    <div className="absolute inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="mx-4 w-full max-w-[680px] overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl">
        {/* header */}
        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-3">
          <h2 className="text-sm font-semibold text-slate-900">脚本使用引导</h2>
          <button
            onClick={onClose}
            className="flex h-7 w-7 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* content */}
        <div className="px-5 py-5">
          {isTitlePage ? (
            <div className="flex flex-col items-center justify-center py-16">
              <HelpCircle className="mb-4 h-12 w-12 text-violet-500" />
              <h1 className="text-2xl font-bold text-slate-900">脚本使用指南</h1>
            </div>
          ) : (
            <>
              {/* image */}
              <div className="mb-4 max-h-[420px] overflow-hidden rounded-lg border border-slate-200 bg-slate-50">
                {current.image ? (
                  <img src={current.image} alt={current.title} className="w-full object-contain" />
                ) : (
                  <div className="flex h-full items-center justify-center text-center text-slate-400">
                    <div>
                      <HelpCircle className="mx-auto mb-2 h-8 w-8" />
                      <p className="text-xs">引导截图占位</p>
                    </div>
                  </div>
                )}
              </div>

              <h3 className="mb-1.5 text-sm font-semibold text-slate-800">{current.title}</h3>
              <p className="text-xs leading-relaxed text-slate-500">{current.description}</p>
            </>
          )}
        </div>

        {/* footer */}
        <div className="flex items-center justify-between border-t border-slate-100 px-5 py-3">
          {/* dots */}
          <div className="flex gap-1.5">
            {BUILD_GUIDE_STEPS.map((_, i) => (
              <button
                key={i}
                onClick={() => setStep(i)}
                className={`h-2 w-2 rounded-full transition-colors ${
                  i === step ? "bg-violet-500" : "bg-slate-300 hover:bg-slate-400"
                }`}
              />
            ))}
          </div>

          {/* buttons */}
          <div className="flex gap-2">
            {!isFirst && (
              <button
                onClick={() => setStep(step - 1)}
                className="flex items-center gap-1 rounded-md border border-slate-200 px-3 py-1.5 text-xs text-slate-600 hover:bg-slate-50"
              >
                <ChevronLeft className="h-3 w-3" />
                上一步
              </button>
            )}
            {isLast ? (
              <button
                onClick={onClose}
                className="rounded-md border border-slate-200 px-3 py-1.5 text-xs text-slate-600 hover:bg-slate-50"
              >
                我知道了
              </button>
            ) : (
              <button
                onClick={() => setStep(step + 1)}
                className="flex items-center gap-1 rounded-md bg-violet-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-violet-700"
              >
                下一步
                <ChevronRight className="h-3 w-3" />
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

// ============ Canvas Overlay ============

interface CanvasOverlayProps {
  screenshot: ScriptScreenshot;
  steps: ScriptStep[];
  activeStepId: string | null;
  onCanvasClick: (x: number, y: number) => void;
  onCanvasAreaSelect: (x1: number, y1: number, x2: number, y2: number, duration?: number) => void;
  onUploadTemplate: (file: File, x1: number, y1: number, x2: number, y2: number) => void;
  onNoStepClick: () => void;
}

function CanvasOverlay({
  screenshot,
  steps,
  activeStepId,
  onCanvasClick,
  onCanvasAreaSelect,
  onUploadTemplate,
  onNoStepClick,
}: CanvasOverlayProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [drawing, setDrawing] = useState(false);
  const [startPos, setStartPos] = useState<{ x: number; y: number } | null>(null);
  const [currentPos, setCurrentPos] = useState<{ x: number; y: number } | null>(null);
  const [imgSize, setImgSize] = useState({ w: 0, h: 0 });
  const imgRef = useRef<HTMLImageElement | null>(null);
  const drawStartTimeRef = useRef<number>(0);

  const activeStep = steps.find((s) => s.id === activeStepId) ?? null;

  // load image via fetch → blob to avoid canvas taint
  useEffect(() => {
    let blobUrl: string | null = null;
    let cancelled = false;
    const url = resolveFileUrl(screenshot.fileUrl);
    fetch(url)
      .then((res) => res.blob())
      .then((blob) => {
        if (cancelled) return;
        blobUrl = URL.createObjectURL(blob);
        const img = new Image();
        img.onload = () => {
          if (cancelled) return;
          imgRef.current = img;
          setImgSize({ w: img.naturalWidth, h: img.naturalHeight });
        };
        img.src = blobUrl;
      })
      .catch(() => {
        // fetch 失败时回退为普通加载
        if (cancelled) return;
        const img = new Image();
        img.onload = () => {
          imgRef.current = img;
          setImgSize({ w: img.naturalWidth, h: img.naturalHeight });
        };
        img.src = url;
      });
    return () => {
      cancelled = true;
      if (blobUrl) URL.revokeObjectURL(blobUrl);
    };
  }, [screenshot.fileUrl]);

  // draw
  useEffect(() => {
    const canvas = canvasRef.current;
    const container = containerRef.current;
    const img = imgRef.current;
    if (!canvas || !container || !img || !imgSize.w) return;

    const containerW = container.clientWidth;
    const scale = containerW / imgSize.w;
    const displayH = imgSize.h * scale;

    canvas.width = containerW;
    canvas.height = displayH;

    const ctx = canvas.getContext("2d")!;
    ctx.drawImage(img, 0, 0, containerW, displayH);

    // draw active step annotation only
    if (activeStep) {
      const p = activeStep.paramsJson || {};
      ctx.save();
      ctx.strokeStyle = "#7C3AED";
      ctx.fillStyle = "rgba(124, 58, 237, 0.15)";
      ctx.lineWidth = 2.5;

      switch (activeStep.operationType) {
        case "click":
        case "double_click":
        case "long_press":
        case "mouse_move": {
          const cx = (p.x as number) * scale;
          const cy = (p.y as number) * scale;
          ctx.beginPath();
          ctx.arc(cx, cy, 8, 0, Math.PI * 2);
          ctx.fill();
          ctx.stroke();
          ctx.beginPath();
          ctx.moveTo(cx - 4, cy);
          ctx.lineTo(cx + 4, cy);
          ctx.moveTo(cx, cy - 4);
          ctx.lineTo(cx, cy + 4);
          ctx.stroke();
          break;
        }
        case "area_click":
        case "area_long_press": {
          const x1 = (p.x1 ?? p.x) as number;
          const y1 = (p.y1 ?? p.y) as number;
          const x2 = (p.x2 ?? (x1 + 50)) as number;
          const y2 = (p.y2 ?? (y1 + 50)) as number;
          const rx1 = x1 * scale;
          const ry1 = y1 * scale;
          const rw = (x2 - x1) * scale;
          const rh = (y2 - y1) * scale;
          ctx.fillRect(rx1, ry1, rw, rh);
          ctx.strokeRect(rx1, ry1, rw, rh);
          break;
        }
        case "scroll": {
          if (p.x1 !== undefined && p.x2 !== undefined) {
            const sx = (p.x1 as number) * scale;
            const sy = (p.y1 as number) * scale;
            const ex = (p.x2 as number) * scale;
            const ey = (p.y2 as number) * scale;
            ctx.beginPath();
            ctx.moveTo(sx, sy);
            ctx.lineTo(ex, ey);
            ctx.stroke();
            // arrowhead
            const angle = Math.atan2(ey - sy, ex - sx);
            const aLen = 14;
            ctx.beginPath();
            ctx.moveTo(ex, ey);
            ctx.lineTo(ex - aLen * Math.cos(angle - 0.4), ey - aLen * Math.sin(angle - 0.4));
            ctx.moveTo(ex, ey);
            ctx.lineTo(ex - aLen * Math.cos(angle + 0.4), ey - aLen * Math.sin(angle + 0.4));
            ctx.stroke();
            // dots
            ctx.beginPath();
            ctx.arc(sx, sy, 5, 0, Math.PI * 2);
            ctx.fill();
            ctx.stroke();
            ctx.beginPath();
            ctx.arc(ex, ey, 5, 0, Math.PI * 2);
            ctx.fill();
            ctx.stroke();
          }
          break;
        }
        case "if_image": {
          if (p.x1 !== undefined && p.x2 !== undefined) {
            const rx1 = (p.x1 as number) * scale;
            const ry1 = (p.y1 as number) * scale;
            const rw = ((p.x2 as number) - (p.x1 as number)) * scale;
            const rh = ((p.y2 as number) - (p.y1 as number)) * scale;
            ctx.setLineDash([6, 4]);
            ctx.strokeStyle = "#E11D48";
            ctx.fillStyle = "rgba(225, 29, 72, 0.1)";
            ctx.fillRect(rx1, ry1, rw, rh);
            ctx.strokeRect(rx1, ry1, rw, rh);
            ctx.setLineDash([]);
            ctx.fillStyle = "#E11D48";
            ctx.font = "11px sans-serif";
            ctx.fillText("检测区域", rx1 + 4, ry1 + 14);
          }
          break;
        }
      }
      ctx.restore();
    }

    // draw current drawing
    if (drawing && startPos && currentPos) {
      ctx.save();
      ctx.strokeStyle = "#7C3AED";
      ctx.fillStyle = "rgba(124, 58, 237, 0.1)";
      ctx.lineWidth = 2;
      ctx.setLineDash([5, 5]);
      if (activeStep && activeStep.operationType === "scroll") {
        ctx.beginPath();
        ctx.moveTo(startPos.x, startPos.y);
        ctx.lineTo(currentPos.x, currentPos.y);
        ctx.stroke();
        const angle = Math.atan2(currentPos.y - startPos.y, currentPos.x - startPos.x);
        const aLen = 14;
        ctx.beginPath();
        ctx.moveTo(currentPos.x, currentPos.y);
        ctx.lineTo(currentPos.x - aLen * Math.cos(angle - 0.4), currentPos.y - aLen * Math.sin(angle - 0.4));
        ctx.moveTo(currentPos.x, currentPos.y);
        ctx.lineTo(currentPos.x - aLen * Math.cos(angle + 0.4), currentPos.y - aLen * Math.sin(angle + 0.4));
        ctx.stroke();
        // 显示拖拽耗时
        const elapsed = Math.max(100, Date.now() - drawStartTimeRef.current);
        const label = `${elapsed}ms`;
        ctx.setLineDash([]);
        ctx.font = "bold 12px sans-serif";
        ctx.fillStyle = "#7C3AED";
        ctx.strokeStyle = "#fff";
        ctx.lineWidth = 3;
        const lx = (startPos.x + currentPos.x) / 2;
        const ly = (startPos.y + currentPos.y) / 2 - 10;
        ctx.strokeText(label, lx, ly);
        ctx.fillText(label, lx, ly);
      } else {
        const rx = Math.min(startPos.x, currentPos.x);
        const ry = Math.min(startPos.y, currentPos.y);
        const rw = Math.abs(currentPos.x - startPos.x);
        const rh = Math.abs(currentPos.y - startPos.y);
        ctx.fillRect(rx, ry, rw, rh);
        ctx.strokeRect(rx, ry, rw, rh);
      }
      ctx.restore();
    }
  }, [steps, activeStepId, imgSize, drawing, startPos, currentPos]);

  const getCanvasPos = (e: React.MouseEvent) => {
    const canvas = canvasRef.current!;
    const rect = canvas.getBoundingClientRect();
    return {
      x: e.clientX - rect.left,
      y: e.clientY - rect.top,
    };
  };

  const toImagePos = (canvasX: number, canvasY: number) => {
    const canvas = canvasRef.current!;
    const scale = canvas.width / imgSize.w;
    return {
      x: Math.round(canvasX / scale),
      y: Math.round(canvasY / scale),
    };
  };

  const handleMouseDown = (e: React.MouseEvent) => {
    if (!activeStep) { onNoStepClick(); return; }
    const pos = getCanvasPos(e);
    const type = activeStep.operationType;
    if (type === "area_click" || type === "area_long_press" || type === "long_press" || type === "scroll" || type === "if_image" || type === "if_ai") {
      setDrawing(true);
      setStartPos(pos);
      setCurrentPos(pos);
      drawStartTimeRef.current = Date.now();
    }
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!drawing) return;
    setCurrentPos(getCanvasPos(e));
  };

  const handleMouseUp = (e: React.MouseEvent) => {
    if (!drawing || !startPos) return;
    setDrawing(false);
    const endPos = getCanvasPos(e);
    const s = toImagePos(startPos.x, startPos.y);
    const ep = toImagePos(endPos.x, endPos.y);
    const elapsed = Date.now() - drawStartTimeRef.current;
    if (Math.abs(ep.x - s.x) > 5 || Math.abs(ep.y - s.y) > 5) {
      if (activeStep && activeStep.operationType === "if_image") {
        // 裁剪模板图片并上传
        cropAndUpload(startPos, endPos, s, ep);
      } else {
        const duration = activeStep?.operationType === "scroll" ? Math.max(100, elapsed) : undefined;
        onCanvasAreaSelect(s.x, s.y, ep.x, ep.y, duration);
      }
    }
    setStartPos(null);
    setCurrentPos(null);
  };

  const cropAndUpload = (
    canvasStart: { x: number; y: number },
    canvasEnd: { x: number; y: number },
    imgStart: { x: number; y: number },
    imgEnd: { x: number; y: number }
  ) => {
    const img = imgRef.current;
    const canvas = canvasRef.current;
    if (!img || !canvas) return;
    const scale = canvas.width / imgSize.w;
    const x1 = Math.min(imgStart.x, imgEnd.x);
    const y1 = Math.min(imgStart.y, imgEnd.y);
    const x2 = Math.max(imgStart.x, imgEnd.x);
    const y2 = Math.max(imgStart.y, imgEnd.y);
    // 在临时 canvas 上裁剪
    const cropCanvas = document.createElement("canvas");
    cropCanvas.width = x2 - x1;
    cropCanvas.height = y2 - y1;
    const ctx = cropCanvas.getContext("2d")!;
    ctx.drawImage(img, x1 * scale, y1 * scale, (x2 - x1) * scale, (y2 - y1) * scale, 0, 0, x2 - x1, y2 - y1);
    // 只记录区域坐标，不上传模板（后端编译时截取）
    onCanvasAreaSelect(x1, y1, x2, y2);
  };

  const handleClick = (e: React.MouseEvent) => {
    if (drawing) return;
    if (!activeStep) { onNoStepClick(); return; }
    const type = activeStep.operationType;
    if (type === "click" || type === "double_click" || type === "long_press" || type === "mouse_move") {
      const pos = getCanvasPos(e);
      const imgPos = toImagePos(pos.x, pos.y);
      onCanvasClick(imgPos.x, imgPos.y);
    }
  };

  return (
    <div ref={containerRef} className="relative w-full">
      <canvas
        ref={canvasRef}
        className="w-full cursor-crosshair"
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onClick={handleClick}
      />
    </div>
  );
}

// ============ Main Page ============

export function ScriptDetailPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();

  const [project, setProject] = useState<ScriptProjectDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [activeScreenshotIndex, setActiveScreenshotIndex] = useState(0);
  const [activeStepIndex, setActiveStepIndex] = useState(-1);
  const activeStepIdRef = useRef<string | null>(null);
  const [deleteScreenshotTarget, setDeleteScreenshotTarget] = useState<ScriptScreenshot | null>(
    null
  );

  // guide modal
  const [showGuide, setShowGuide] = useState(true);
  const handleCloseGuide = useCallback(() => {
    setShowGuide(false);
  }, []);

  // build guide modal
  const [showBuildGuide, setShowBuildGuide] = useState(false);
  const [showScreenshotGuide, setShowScreenshotGuide] = useState(false);

  // gui options
  const [guiEnabled, setGuiEnabled] = useState(false);

  // script preview
  const [previewCode, setPreviewCode] = useState<string | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [showPreview, setShowPreview] = useState(false);

  // build
  const [buildStatus, setBuildStatus] = useState<string>("idle");
  const [buildProgress, setBuildProgress] = useState(0);
  const [buildMessage, setBuildMessage] = useState("");
  const [downloadUrl, setDownloadUrl] = useState<string | null>(null);
  const [buildMode, setBuildMode] = useState<"bat" | "github">("bat");
  const [showBuildModeModal, setShowBuildModeModal] = useState(false);
  const [showPythonGuide, setShowPythonGuide] = useState(false);
  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const folderInputRef = useRef<HTMLInputElement>(null);

  const loadProject = useCallback(async () => {
    if (!projectId) return;
    try {
      setLoading(true);
      const data = await getProjectDetail(projectId);
      setProject(data);
      setGuiEnabled(data.guiEnabled === 1);
      if (data.screenshots.length > 0 && activeScreenshotIndex >= data.screenshots.length) {
        setActiveScreenshotIndex(0);
      }
      // 恢复编译状态
      if (data.status === "success" && data.exePath) {
        setBuildStatus("success");
        setDownloadUrl(data.exePath);
      } else if (data.status === "building") {
        setBuildStatus("building");
        startPolling();
      }
    } catch (err: any) {
      toast.error(err?.message || "加载项目失败");
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    loadProject();
    return () => {
      if (pollTimerRef.current) clearInterval(pollTimerRef.current);
    };
  }, [loadProject]);

  const screenshots = project?.screenshots ?? [];
  const steps = project?.steps ?? [];
  const activeScreenshot = screenshots[activeScreenshotIndex];

  // polling
  const startPolling = () => {
    if (pollTimerRef.current) clearInterval(pollTimerRef.current);
    pollTimerRef.current = setInterval(async () => {
      if (!projectId) return;
      try {
        const status = await getBuildStatus(projectId);
        setBuildProgress(status.progress);
        setBuildMessage(status.message || "");
        if (status.status === "success") {
          setBuildStatus("success");
          setDownloadUrl(status.downloadUrl || null);
          stopPolling();
          toast.success("EXE 编译完成");
        } else if (status.status === "failed") {
          setBuildStatus("failed");
          setBuildMessage(status.message || "编译失败");
          stopPolling();
          toast.error(status.message || "编译失败");
        }
      } catch {
        // 忽略轮询错误
      }
    }, 2000);
  };

  const stopPolling = () => {
    if (pollTimerRef.current) {
      clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
    }
  };

  // step CRUD
  const handleAddStep = (type: OperationType, insertAfterIndex?: number) => {
    if (!project) return;
    if (type === "else") {
      const checkIndex = insertAfterIndex !== undefined ? insertAfterIndex + 1 : steps.length;
      if (!hasUnclosedIf(steps, checkIndex)) {
        toast.error("没有未结束的条件块，无法添加「否则」");
        return;
      }
    }
    const defaultParams: Record<OperationType, Record<string, unknown>> = {
      click: {},
      double_click: {},
      area_click: {},
      long_press: {},
      area_long_press: {},
      mouse_move: {},
      key_press: {},
      key_long_press: {},
      wait_seconds: { seconds: 1 },
      input_text: {},
      scroll: { x1: 0, y1: 0, x2: 0, y2: 0, duration: 500 },
      for_start: { count: 3 },
      for_end: {},
      break_loop: {},
      continue_loop: {},
      if_image: { x1: 0, y1: 0, x2: 100, y2: 100, similarity: 0.95 },
      if_ai: { x1: 0, y1: 0, x2: 100, y2: 100, prompt: "", similarity: 0.8 },
      if_random: { probability: 0.5 },
      else: {},
      if_end: {},
    };

    const newStep: ScriptStep = {
      id: `temp_${Date.now()}`,
      projectId: project.id,
      screenshotId: type === "wait_seconds" ? null : activeScreenshot?.id ?? null,
      stepOrder: 0,
      operationType: type,
      paramsJson: defaultParams[type],
      templatePath: null,
      templateUrl: null,
      createTime: new Date().toISOString(),
    };

    let newSteps: ScriptStep[];
    let newActiveIndex: number;

    if (insertAfterIndex !== undefined) {
      // Insert after the specified index
      const insertPos = insertAfterIndex + 1;
      newSteps = [...steps.slice(0, insertPos), newStep, ...steps.slice(insertPos)];
      newActiveIndex = insertPos;
    } else {
      // Append to end
      newSteps = [...steps, newStep];
      newActiveIndex = steps.length;
    }

    // Reindex stepOrder
    newSteps = newSteps.map((s, i) => ({ ...s, stepOrder: i }));

    setProject({ ...project, steps: newSteps });
    setActiveStepIndex(newActiveIndex);
    activeStepIdRef.current = newStep.id;
  };

  const handleUpdateStep = (index: number, patch: Partial<ScriptStep>) => {
    if (!project) return;
    const updated = [...steps];
    updated[index] = { ...updated[index], ...patch };
    setProject({ ...project, steps: updated });
  };

  const handleDeleteStep = (index: number) => {
    if (!project) return;
    const updated = steps.filter((_, i) => i !== index).map((s, i) => ({ ...s, stepOrder: i }));
    setProject({ ...project, steps: updated });
    if (activeStepIndex >= updated.length) {
      const newIdx = updated.length - 1;
      setActiveStepIndex(newIdx);
      activeStepIdRef.current = newIdx >= 0 ? updated[newIdx].id : null;
    }
  };

  const handleMoveStep = (index: number, direction: -1 | 1) => {
    if (!project) return;
    const target = index + direction;
    if (target < 0 || target >= steps.length) return;
    const updated = [...steps];
    [updated[index], updated[target]] = [updated[target], updated[index]];
    const reordered = updated.map((s, i) => ({ ...s, stepOrder: i }));
    setProject({ ...project, steps: reordered });
    setActiveStepIndex(target);
    activeStepIdRef.current = steps[target].id;
  };

  // canvas interactions
  const handleCanvasClick = (x: number, y: number) => {
    const stepId = activeStepIdRef.current;
    if (!stepId) return;
    const idx = steps.findIndex((s) => s.id === stepId);
    if (idx < 0) return;
    const step = steps[idx];
    if (step.operationType === "click" || step.operationType === "double_click" || step.operationType === "mouse_move") {
      handleUpdateStep(idx, { paramsJson: { x, y } });
    } else if (step.operationType === "long_press") {
      handleUpdateStep(idx, {
        paramsJson: { x, y, duration: step.paramsJson.duration || 1000 },
      });
    }
  };

  const handleCanvasAreaSelect = (x1: number, y1: number, x2: number, y2: number, duration?: number) => {
    const stepId = activeStepIdRef.current;
    if (!stepId) return;
    const idx = steps.findIndex((s) => s.id === stepId);
    if (idx < 0) return;
    const step = steps[idx];
    const minX = Math.min(x1, x2);
    const minY = Math.min(y1, y2);
    const maxX = Math.max(x1, x2);
    const maxY = Math.max(y1, y2);
    if (step.operationType === "area_click") {
      handleUpdateStep(idx, {
        paramsJson: { x1: minX, y1: minY, x2: maxX, y2: maxY },
      });
    } else if (step.operationType === "area_long_press") {
      handleUpdateStep(idx, {
        paramsJson: { x1: minX, y1: minY, x2: maxX, y2: maxY, duration: step.paramsJson.duration || 1000 },
      });
    } else if (step.operationType === "scroll") {
      handleUpdateStep(idx, {
        paramsJson: { x1, y1, x2, y2, duration: duration || (step.paramsJson.duration as number) || 500 },
      });
    } else if (step.operationType === "if_ai") {
      handleUpdateStep(idx, {
        paramsJson: { ...step.paramsJson, x1: minX, y1: minY, x2: maxX, y2: maxY },
      });
      toast.success("已设置 AI 识别区域");
    } else if (step.operationType === "if_image") {
      // 记录区域坐标和截图ID，后端编译时截取
      const currentScreenshotId = activeScreenshot?.id ?? step.screenshotId;
      handleUpdateStep(idx, {
        paramsJson: {
          ...step.paramsJson,
          x1: minX, y1: minY, x2: maxX, y2: maxY,
          cropScreenshotId: currentScreenshotId,
        },
        // 清除旧的 templateUrl（改为区域截取模式）
        templateUrl: undefined as any,
        templatePath: undefined as any,
      });
      toast.success("已设置检测区域");
    }
  };

  const handleUploadTemplate = async (file: File, x1: number, y1: number, x2: number, y2: number) => {
    const stepId = activeStepIdRef.current;
    if (!stepId || !project) return;
    const idx = steps.findIndex((s) => s.id === stepId);
    if (idx < 0) return;
    try {
      const templateUrl = await uploadTemplate(project.id, file);
      handleUpdateStep(idx, {
        paramsJson: { ...steps[idx].paramsJson, x1, y1, x2, y2, templateUrl },
      });
      toast.success("模板已设置");
    } catch {
      toast.error("模板上传失败");
    }
  };

  // 直接上传模板图片（不经过截图裁剪压缩）
  const handleUploadTemplateDirect = async (index: number, file: File) => {
    if (!project) return;
    try {
      const templateUrl = await uploadTemplate(project.id, file);
      handleUpdateStep(index, {
        paramsJson: { ...steps[index].paramsJson, templateUrl },
      });
      toast.success("模板上传成功");
    } catch {
      toast.error("模板上传失败");
    }
  };

  // screenshot upload
  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !projectId) return;
    setUploading(true);
    try {
      const screenshot = await uploadScreenshot(projectId, file);
      setProject((prev) => {
        if (!prev) return prev;
        return {
          ...prev,
          screenshots: [...prev.screenshots, screenshot],
        };
      });
      toast.success("截图上传成功");
    } catch (err: any) {
      toast.error(err?.message || "上传失败");
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  const handleFolderUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const fileList = e.target.files;
    if (!fileList || fileList.length === 0 || !projectId) return;

    const imageFiles = Array.from(fileList).filter((f) =>
      f.type.startsWith("image/")
    );
    if (imageFiles.length === 0) {
      toast.warning("所选文件夹中没有找到图片文件");
      if (folderInputRef.current) folderInputRef.current.value = "";
      return;
    }

    const BATCH_SIZE = 5;
    const totalBatches = Math.ceil(imageFiles.length / BATCH_SIZE);
    setUploading(true);
    try {
      const allScreenshots: ScriptScreenshot[] = [];
      for (let i = 0; i < imageFiles.length; i += BATCH_SIZE) {
        const batch = imageFiles.slice(i, i + BATCH_SIZE);
        const batchNum = Math.floor(i / BATCH_SIZE) + 1;
        toast.info(`正在上传第 ${batchNum}/${totalBatches} 批（${batch.length} 张）...`);
        const result = await uploadScreenshots(projectId, batch);
        allScreenshots.push(...result);
      }
      setProject((prev) => {
        if (!prev) return prev;
        return {
          ...prev,
          screenshots: [...prev.screenshots, ...allScreenshots],
        };
      });
      toast.success(`成功上传 ${allScreenshots.length} 张截图`);
    } catch (err: any) {
      toast.error(err?.message || "批量上传失败");
    } finally {
      setUploading(false);
      if (folderInputRef.current) folderInputRef.current.value = "";
    }
  };

  const handleDeleteScreenshot = () => {
    if (!deleteScreenshotTarget || !project) return;
    setProject({
      ...project,
      screenshots: project.screenshots.filter((s) => s.id !== deleteScreenshotTarget.id),
      steps: project.steps
        .filter((s) => s.screenshotId !== deleteScreenshotTarget.id)
        .map((s, i) => ({ ...s, stepOrder: i })),
    });
    setDeleteScreenshotTarget(null);
    if (activeScreenshotIndex >= project.screenshots.length - 1) {
      setActiveScreenshotIndex(Math.max(0, project.screenshots.length - 2));
    }
  };

  // save
  // 必填字段校验
  const FIELD_LABELS: Record<string, string> = {
    x: "X坐标", y: "Y坐标", x1: "左上X", y1: "左上Y", x2: "右下X", y2: "右下Y",
    duration: "时长", seconds: "秒数", text: "输入内容", key: "按键名",
  };

  const STEP_REQUIRED_FIELDS: Record<OperationType, string[]> = {
    click: ["x", "y"],
    double_click: ["x", "y"],
    area_click: ["x1", "y1", "x2", "y2"],
    long_press: ["x", "y", "duration"],
    area_long_press: ["x1", "y1", "x2", "y2", "duration"],
    mouse_move: ["x", "y"],
    key_press: ["key"],
    key_long_press: ["key", "duration"],
    wait_seconds: ["seconds"],
    input_text: ["text"],
    scroll: [],
    for_start: [],
    for_end: [],
    break_loop: [],
    continue_loop: [],
    if_image: [],
    if_ai: ["x1", "y1", "x2", "y2", "prompt"],
    if_random: [],
    else: [],
    if_end: [],
  };

  const validateSteps = (): boolean => {
    if (steps.length === 0) {
      toast.error("请先添加至少一个操作步骤再编译");
      return false;
    }
    const hasRealOperation = steps.some(
      (s) => !["for_start", "for_end", "break_loop", "continue_loop", "if_image", "if_random", "if_ai", "else", "if_end"].includes(s.operationType)
    );
    if (!hasRealOperation) {
      toast.error("请先添加至少一个操作步骤（点击、输入、滑动等），仅有控制流无法编译");
      return false;
    }
    for (let i = 0; i < steps.length; i++) {
      const step = steps[i];
      const required = STEP_REQUIRED_FIELDS[step.operationType] || [];
      for (const field of required) {
        const val = step.paramsJson?.[field];
        if (val === undefined || val === null || val === "" || (typeof val === "number" && val === 0)) {
          // 聚焦到空输入框
          const el = document.querySelector(
            `[data-step-index="${i}"][data-field="${field}"]`
          ) as HTMLElement | null;
          if (el) el.focus();
          const label = FIELD_LABELS[field] || field;
          toast.error(`步骤 ${i + 1}「${OPERATION_LABELS[step.operationType]}」的 ${label} 不能为空`);
          return false;
        }
      }
    }
    // 校验控制流配对
    let forDepth = 0;
    let ifDepth = 0;
    for (const step of steps) {
      if (step.operationType === "for_start") forDepth++;
      else if (step.operationType === "for_end") {
        if (forDepth <= 0) { toast.error("存在多余的「循环结束」，缺少对应的「循环开始」"); return false; }
        forDepth--;
      }
      else if (step.operationType === "break_loop" || step.operationType === "continue_loop") {
        if (forDepth <= 0) { toast.error("「跳出循环」和「继续循环」只能放在「循环开始」和「循环结束」之间"); return false; }
      }
      else if (step.operationType === "if_image" || step.operationType === "if_random" || step.operationType === "if_ai") ifDepth++;
      else if (step.operationType === "else") {
        // else 不影响深度计数，但需要校验前面有对应的 if
        if (ifDepth <= 0) { toast.error("存在多余的「否则」，缺少对应的条件开始"); return false; }
      }
      else if (step.operationType === "if_end") {
        if (ifDepth <= 0) { toast.error("存在多余的「条件结束」，缺少对应的条件开始"); return false; }
        ifDepth--;
      }
    }
    if (forDepth > 0) { toast.error(`缺少 ${forDepth} 个「循环结束」来配对「循环开始」`); return false; }
    if (ifDepth > 0) { toast.error(`缺少 ${ifDepth} 个「条件结束」来配对条件开始`); return false; }
    return true;
  };

  const handleSave = async () => {
    if (!project || !projectId) return;
    if (!validateSteps()) return;
    setSaving(true);
    try {
      await saveProject(projectId, {
        guiEnabled: guiEnabled ? 1 : 0,
        screenshots: project.screenshots.map((s, i) => ({
          id: s.id.startsWith("temp_") ? undefined : s.id,
          fileName: s.fileName,
          fileUrl: s.fileUrl,
          width: s.width,
          height: s.height,
          sortOrder: i,
        })),
        steps: project.steps.map((s, i) => ({
          id: s.id.startsWith("temp_") ? undefined : s.id,
          screenshotId: s.screenshotId ?? undefined,
          stepOrder: i,
          operationType: s.operationType,
          paramsJson: s.paramsJson,
        })),
      });
      toast.success("保存成功");
      await loadProject();
    } catch (err: any) {
      toast.error(err?.message || "保存失败");
    } finally {
      setSaving(false);
    }
  };

  // preview
  const handlePreview = async () => {
    if (!projectId || !project) return;
    setPreviewLoading(true);
    setShowPreview(true);
    try {
      await saveProject(projectId, {
        guiEnabled: guiEnabled ? 1 : 0,
        screenshots: project.screenshots.map((s, i) => ({
          id: s.id.startsWith("temp_") ? undefined : s.id,
          fileName: s.fileName,
          fileUrl: s.fileUrl,
          width: s.width,
          height: s.height,
          sortOrder: i,
        })),
        steps: project.steps.map((s, i) => ({
          id: s.id.startsWith("temp_") ? undefined : s.id,
          screenshotId: s.screenshotId ?? undefined,
          stepOrder: i,
          operationType: s.operationType,
          paramsJson: s.paramsJson,
        })),
      });
      const code = await previewScript(projectId);
      setPreviewCode(code);
    } catch (err: any) {
      toast.error(err?.message || "预览失败");
      setPreviewCode(null);
    } finally {
      setPreviewLoading(false);
    }
  };

  // build
  const handleBuild = async (mode: "bat" | "github" = "bat") => {
    if (!projectId || !project) return;
    if (!validateSteps()) return;
    setBuildMode(mode);
    setBuildStatus("building");
    setBuildProgress(0);
    setBuildMessage("正在保存...");
    try {
      // 先自动保存
      await saveProject(projectId, {
        guiEnabled: guiEnabled ? 1 : 0,
        screenshots: project.screenshots.map((s, i) => ({
          id: s.id.startsWith("temp_") ? undefined : s.id,
          fileName: s.fileName,
          fileUrl: s.fileUrl,
          width: s.width,
          height: s.height,
          sortOrder: i,
        })),
        steps: project.steps.map((s, i) => ({
          id: s.id.startsWith("temp_") ? undefined : s.id,
          screenshotId: s.screenshotId ?? undefined,
          stepOrder: i,
          operationType: s.operationType,
          paramsJson: s.paramsJson,
        })),
      });
      setBuildMessage(mode === "bat" ? "正在打包..." : "正在提交编译任务...");
      const result = await buildExe(projectId, mode);
      if (mode === "bat" && result.downloadUrl) {
        // BAT 模式：同步返回，直接显示下载
        setBuildStatus("success");
        setDownloadUrl(result.downloadUrl);
        setBuildProgress(100);
        toast.success("打包完成");
        setShowPythonGuide(true);
      } else {
        // GitHub 模式：异步轮询
        startPolling();
      }
    } catch (err: any) {
      setBuildStatus("failed");
      setBuildMessage(err?.message || "编译失败");
      toast.error(err?.message || "编译失败");
    }
  };

  const handleBuildClick = () => {
    if (!validateSteps()) return;
    setShowBuildModeModal(true);
  };

  if (loading) {
    return (
      <MainLayout>
        <div className="flex h-full items-center justify-center">
          <Loader2 className="h-6 w-6 animate-spin text-slate-400" />
        </div>
      </MainLayout>
    );
  }

  if (!project) {
    return (
      <MainLayout>
        <div className="flex h-full flex-col items-center justify-center gap-2 text-slate-400">
          <p>项目不存在</p>
          <Button variant="outline" onClick={() => navigate("/script")}>
            返回项目列表
          </Button>
        </div>
      </MainLayout>
    );
  }

  return (
    <MainLayout>
      <div className="flex h-full flex-col overflow-hidden">
        {/* top bar */}
        <div className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-2.5">
          <div className="flex items-center gap-3">
            <button
              onClick={() => navigate("/script")}
              className="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 text-slate-500 transition-colors hover:bg-slate-50"
            >
              <ArrowLeft className="h-4 w-4" />
            </button>
            <div>
              <h1 className="text-sm font-semibold text-slate-900">{project.name}</h1>
              <p className="text-xs text-slate-400">
                {project.targetWidth && project.targetHeight
                  ? `${project.targetWidth}x${project.targetHeight}${project.scalePct ? ` ${project.scalePct}%` : ""}`
                  : "上传截图自动检测分辨率"}
                {" · "}{screenshots.length} 张截图 · {steps.length} 个步骤
              </p>
            </div>
            {project.uploadToken && (
              <button
                onClick={() => {
                  navigator.clipboard.writeText(project.uploadToken!);
                  toast.success("Token 已复制到剪贴板");
                }}
                className="flex items-center gap-1.5 rounded-md border border-slate-200 bg-slate-50 px-2 py-1 text-xs text-slate-500 transition-colors hover:bg-slate-100"
                title="点击复制上传Token，用于截图工具免登录上传"
              >
                <span className="font-mono text-[10px]">Token</span>
                <Copy className="h-3 w-3" />
              </button>
            )}
          </div>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => {
              const a = document.createElement("a");
              a.href = "/screenshot.exe";
              a.download = "screenshot.exe";
              a.click();
              setShowScreenshotGuide(true);
            }}>
              <Download className="mr-1.5 h-3.5 w-3.5" />
              截图工具
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={handlePreview}
              disabled={previewLoading}
            >
              {previewLoading ? (
                <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
              ) : (
                <Code className="mr-1.5 h-3.5 w-3.5" />
              )}
              预览脚本
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                if (project) {
                  exportProject(project.id);
                  toast.success("导出成功");
                }
              }}
            >
              <Download className="mr-1.5 h-3.5 w-3.5" />
              导出
            </Button>

            {/* 编译/下载按钮 */}
            {buildStatus === "building" ? (
              <div className="flex items-center gap-2">
                <div className="h-2 w-24 overflow-hidden rounded-full bg-slate-200">
                  <div
                    className="h-full bg-violet-500 transition-all duration-500"
                    style={{ width: `${buildProgress}%` }}
                  />
                </div>
                <span className="text-xs text-slate-500">{buildMessage || "编译中..."}</span>
              </div>
            ) : buildStatus === "success" && downloadUrl ? (
              <>
                <Button
                  variant="outline"
                  size="sm"
                  asChild
                >
                  <a href={downloadUrl} download={buildMode === "bat" ? `${project.name}_bat.zip` : `${project.name}.exe`}>
                    <Download className="mr-1.5 h-3.5 w-3.5" />
                    下载脚本
                  </a>
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={handleBuildClick}
                >
                  重新编译
                </Button>
              </>
            ) : buildStatus === "failed" ? (
              <>
                <span className="text-xs text-red-500">{buildMessage}</span>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={handleBuildClick}
                >
                  重新编译
                </Button>
              </>
            ) : (
              <Button
                variant="outline"
                size="sm"
                onClick={handleBuildClick}
              >
                <Code className="mr-1.5 h-3.5 w-3.5" />
                编译脚本
              </Button>
            )}

            {/* build help icon */}
            <button
              onClick={() => setShowBuildGuide(true)}
              title="编译后如何使用脚本？"
              className="flex h-7 w-7 items-center justify-center rounded-full text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
            >
              <HelpCircle className="h-4 w-4" />
            </button>

            <Button
              size="sm"
              onClick={handleSave}
              disabled={saving}
              className="bg-violet-600 hover:bg-violet-700"
            >
              {saving ? (
                <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
              ) : (
                <Save className="mr-1.5 h-3.5 w-3.5" />
              )}
              保存
            </Button>
          </div>
        </div>

        {/* main content: 3 columns */}
        <div className="flex min-h-0 flex-1">
          {/* left: screenshots */}
          <div className="min-h-0 w-[200px] shrink-0 overflow-y-auto border-r border-slate-200 bg-[#FAFAFA]">
            <div className="p-3">
              <div className="mb-2 flex items-center justify-between">
                <span className="text-xs font-medium text-slate-500">截图列表</span>
                <div className="flex items-center gap-1">
                  <button
                    onClick={() => fileInputRef.current?.click()}
                    disabled={uploading}
                    className="flex h-6 w-6 items-center justify-center rounded text-slate-400 hover:bg-slate-200 hover:text-slate-600"
                    title="上传单张图片"
                  >
                    {uploading ? (
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    ) : (
                      <ImagePlus className="h-3.5 w-3.5" />
                    )}
                  </button>
                  <button
                    onClick={() => folderInputRef.current?.click()}
                    disabled={uploading}
                    className="flex h-6 w-6 items-center justify-center rounded text-slate-400 hover:bg-slate-200 hover:text-slate-600"
                    title="上传整个文件夹"
                  >
                    <FolderOpen className="h-3.5 w-3.5" />
                  </button>
                </div>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="image/*"
                  onChange={handleUpload}
                  className="hidden"
                />
                <input
                  ref={folderInputRef}
                  type="file"
                  // @ts-ignore - webkitdirectory is not in standard TS types
                  webkitdirectory=""
                  directory=""
                  multiple
                  onChange={handleFolderUpload}
                  className="hidden"
                />
              </div>

              {screenshots.length === 0 ? (
                <div className="flex flex-col items-center py-8 text-slate-400">
                  <Upload className="h-8 w-8 mb-2" />
                  <p className="text-xs text-center">
                    点击上方 + 按钮
                    <br />
                    上传截图
                  </p>
                </div>
              ) : (
                <div className="space-y-2">
                  {screenshots.map((s, i) => (
                    <div
                      key={s.id}
                      onClick={() => {
                        setActiveScreenshotIndex(i);
                      }}
                      className={cn(
                        "group relative cursor-pointer overflow-hidden rounded-lg border-2 transition-colors",
                        i === activeScreenshotIndex
                          ? "border-violet-400"
                          : "border-transparent hover:border-slate-300"
                      )}
                    >
                      <img
                        src={resolveFileUrl(s.fileUrl)}
                        alt={s.fileName}
                        className="w-full object-cover"
                        style={{ aspectRatio: `${s.width}/${s.height}` }}
                      />
                      <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/50 to-transparent p-1.5">
                        <p className="truncate text-[10px] text-white">{s.fileName}</p>
                      </div>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          setDeleteScreenshotTarget(s);
                        }}
                        className="absolute right-1 top-1 flex h-5 w-5 items-center justify-center rounded bg-black/50 text-white opacity-0 transition-opacity hover:bg-red-500 group-hover:opacity-100"
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* center: canvas */}
          <div className="flex min-w-0 flex-1 flex-col overflow-hidden bg-slate-100">
            <div className="flex-1 overflow-auto p-4">
              {activeScreenshot ? (
                <CanvasOverlay
                  screenshot={activeScreenshot}
                  steps={steps.filter(
                    (s) => !s.screenshotId || s.screenshotId === activeScreenshot.id
                  )}
                  activeStepId={activeStepIdRef.current}
                  onCanvasClick={handleCanvasClick}
                  onCanvasAreaSelect={handleCanvasAreaSelect}
                  onUploadTemplate={handleUploadTemplate}
                  onNoStepClick={() => toast.warning("请先在下方工具栏添加一个操作步骤")}
                />
              ) : (
                <div className="flex h-full items-center justify-center text-slate-400">
                  <div className="text-center">
                    <MousePointerClick className="mx-auto h-10 w-10 mb-2" />
                    <p className="text-sm">上传截图后在此标注操作位置</p>
                  </div>
                </div>
              )}
            </div>

            {/* operation toolbar */}
            <div className="border-t border-slate-200 bg-white px-4 py-3">
              <div className="mb-2 text-sm text-slate-500 font-medium">添加步骤:</div>
              <div className="flex flex-wrap items-center gap-2">
                {(Object.keys(OPERATION_LABELS) as OperationType[]).map((type) => {
                  const elseDisabled = type === "else" && !hasUnclosedIf(steps, steps.length);
                  return (
                    <button
                      key={type}
                      onClick={() => handleAddStep(type)}
                      disabled={elseDisabled}
                      title={OPERATION_HINTS[type]}
                      className={cn(
                        "rounded-lg border px-4 py-2 text-sm font-medium transition-colors",
                        OPERATION_COLORS[type],
                        elseDisabled ? "opacity-40 cursor-not-allowed" : "hover:opacity-80"
                      )}
                    >
                      {OPERATION_LABELS[type]}
                    </button>
                  );
                })}
              </div>
            </div>
          </div>

          {/* right: steps */}
          <div className="flex min-h-0 w-[320px] shrink-0 flex-col overflow-hidden border-l border-slate-200 bg-[#FAFAFA]">
            {/* fixed header area */}
            <div className="shrink-0 p-3 pb-0">
              {/* GUI options */}
              <div className="mb-3 rounded-lg border border-slate-200 bg-white p-3">
                <label className="flex items-center justify-between cursor-pointer">
                  <span className="text-xs font-medium text-slate-600">添加 GUI 控制面板</span>
                  <button
                    type="button"
                    onClick={() => setGuiEnabled(!guiEnabled)}
                    className={cn(
                      "relative inline-flex h-5 w-9 items-center rounded-full transition-colors",
                      guiEnabled ? "bg-violet-600" : "bg-slate-300"
                    )}
                  >
                    <span
                      className={cn(
                        "inline-block h-3.5 w-3.5 rounded-full bg-white transition-transform",
                        guiEnabled ? "translate-x-[18px]" : "translate-x-1"
                      )}
                    />
                  </button>
                </label>
                {guiEnabled && (
                  <p className="mt-2 text-[10px] text-slate-400">编译后 EXE 带有控制窗口，可设置执行次数、间隔、开始/暂停/停止</p>
                )}
              </div>

              <div className="mb-2 flex items-center justify-between">
                <span className="text-xs font-medium text-slate-500">
                  操作步骤 ({steps.length})
                </span>
                {steps.length > 0 && (
                  <span className="text-[10px] text-slate-400">
                    点击画布标注坐标
                  </span>
                )}
              </div>
            </div>

            {/* scrollable steps list */}
            <div className="min-h-0 flex-1 overflow-y-auto px-3 pb-3">
              {steps.length === 0 ? (
                <div className="flex flex-col items-center py-8 text-slate-400">
                  <MousePointerClick className="h-8 w-8 mb-2" />
                  <p className="text-xs text-center">
                    使用下方工具栏
                    <br />
                    添加操作步骤
                  </p>
                </div>
              ) : (
                <div className="space-y-0">
                  {steps.map((step, i) => (
                    <div key={step.id}>
                      {i === 0 && (
                        <InsertStepDropdown
                          onInsert={(type) => handleAddStep(type, -1)}
                          elseAllowed={hasUnclosedIf(steps, 0)}
                        />
                      )}
                      {i > 0 && (
                        <InsertStepDropdown
                          onInsert={(type) => handleAddStep(type, i - 1)}
                          elseAllowed={hasUnclosedIf(steps, i)}
                        />
                      )}
                      <StepItem
                        step={step}
                        index={i}
                        screenshots={screenshots}
                        onUpdate={handleUpdateStep}
                        onDelete={handleDeleteStep}
                        onMoveUp={(idx) => handleMoveStep(idx, -1)}
                        onMoveDown={(idx) => handleMoveStep(idx, 1)}
                        onUploadTemplateDirect={handleUploadTemplateDirect}
                        isFirst={i === 0}
                        isLast={i === steps.length - 1}
                        isActive={i === activeStepIndex}
                        onClick={() => { setActiveStepIndex(i); activeStepIdRef.current = step.id; }}
                      />
                    </div>
                  ))}
                  <InsertStepDropdown
                    onInsert={(type) => handleAddStep(type, steps.length - 1)}
                    elseAllowed={hasUnclosedIf(steps, steps.length)}
                  />
                </div>
              )}
            </div>
          </div>
        </div>

        {/* script preview panel */}
        {showPreview && (
          <div className="absolute inset-0 z-50 flex items-center justify-center bg-black/30">
            <div className="mx-4 max-h-[80vh] w-full max-w-[700px] overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl">
              <div className="flex items-center justify-between border-b border-slate-200 px-5 py-3">
                <h2 className="text-sm font-semibold text-slate-900">脚本预览</h2>
                <button
                  onClick={() => setShowPreview(false)}
                  className="flex h-7 w-7 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
              <div className="max-h-[60vh] overflow-auto bg-slate-950 p-5">
                {previewLoading ? (
                  <div className="flex items-center justify-center py-10 text-slate-400">
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    生成中...
                  </div>
                ) : previewCode ? (
                  <pre className="text-sm leading-relaxed text-emerald-400 font-mono whitespace-pre">
                    {previewCode}
                  </pre>
                ) : (
                  <div className="py-10 text-center text-slate-500">无预览内容</div>
                )}
              </div>
            </div>
          </div>
        )}

        {/* delete screenshot confirm */}
        <AlertDialog
          open={!!deleteScreenshotTarget}
          onOpenChange={() => setDeleteScreenshotTarget(null)}
        >
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>删除截图</AlertDialogTitle>
              <AlertDialogDescription>
                将删除截图「{deleteScreenshotTarget?.fileName}」及关联的操作步骤。
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>取消</AlertDialogCancel>
              <AlertDialogAction
                onClick={handleDeleteScreenshot}
                className="bg-red-600 text-white hover:bg-red-700"
              >
                删除
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>

        {/* guide modal */}
        <ScriptGuideModal open={showGuide} onClose={handleCloseGuide} />
        <BuildModeModal
          open={showBuildModeModal}
          onClose={() => setShowBuildModeModal(false)}
          onSelect={(mode) => {
            setShowBuildModeModal(false);
            handleBuild(mode);
            if (mode === "github") setShowBuildGuide(true);
          }}
        />
        <PythonGuideModal
          open={showPythonGuide}
          onClose={() => setShowPythonGuide(false)}
          downloadUrl={downloadUrl}
          projectName={project.name}
        />
        <BuildGuideModal open={showBuildGuide} onClose={() => setShowBuildGuide(false)} downloadUrl={downloadUrl} projectName={project.name} />
        <ScreenshotGuideModal open={showScreenshotGuide} onClose={() => setShowScreenshotGuide(false)} uploadToken={project.uploadToken} />
      </div>
    </MainLayout>
  );
}
