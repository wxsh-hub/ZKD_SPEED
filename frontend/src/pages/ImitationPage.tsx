import { useCallback, useEffect, useRef, useState } from "react";
import {
  ArrowLeft,
  BookOpen,
  Download,
  FileText,
  Loader2,
  Send,
  Square,
  Trash2,
  Upload,
  X
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { MainLayout } from "@/components/layout/MainLayout";
import { cn } from "@/lib/utils";
import { FlowTag, ARTICLE_IMITATION_STEPS } from "@/components/common/FlowTag";
import { useAuthStore } from "@/stores/authStore";
import {
  uploadArticle,
  rewriteArticleStream,
  type ImitationUploadResult
} from "@/services/imitationService";
import { getSystemSettings, type ModelCandidate } from "@/services/settingsService";
import { storage } from "@/utils/storage";

const WORD_COUNT_PRESETS = [
  { ratio: 0.7, label: "精简" },
  { ratio: 0.9, label: "略少" },
  { ratio: 1.0, label: "相当" },
  { ratio: 1.1, label: "略多" },
  { ratio: 1.2, label: "扩充" },
];

const ACCEPT_TYPES = ".txt,.md,.pdf,.docx";

export function ImitationPage() {
  const navigate = useNavigate();
  const token = useAuthStore((s) => s.token) ?? storage.getToken() ?? "";

  // upload state
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadResult, setUploadResult] = useState<ImitationUploadResult | null>(null);
  const [dragging, setDragging] = useState(false);

  // rewrite state
  const [requirements, setRequirements] = useState("");
  const [wordCount, setWordCount] = useState(0);
  const [selectedModelId, setSelectedModelId] = useState("");
  const [models, setModels] = useState<ModelCandidate[]>([]);
  const [conversationId, setConversationId] = useState("");
  const [output, setOutput] = useState("");
  const [streaming, setStreaming] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const outputRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = outputRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [output]);

  useEffect(() => {
    getSystemSettings()
      .then((settings) => {
        const candidates = settings.ai?.chat?.candidates || [];
        setModels(candidates.filter((c) => c.enabled !== false));
      })
      .catch(() => null);
  }, []);

  // uploadResult 变化时，默认选中"相当"（原文等长）
  useEffect(() => {
    if (uploadResult?.originalWordCount) {
      setWordCount(Math.round(uploadResult.originalWordCount * 1.0));
    }
  }, [uploadResult?.originalWordCount]);

  const applyFile = useCallback(async (selected: File) => {
    setFile(selected);
    setUploadResult(null);
    setOutput("");
    setConversationId("");
    setUploading(true);
    try {
      const result = await uploadArticle(selected);
      setUploadResult(result);
      toast.success(`上传成功，原文 ${result.originalWordCount} 字，共 ${result.chunkCount} 个分块`);
    } catch (err: any) {
      toast.error(err?.message || "上传失败");
      setFile(null);
    } finally {
      setUploading(false);
    }
  }, []);

  const handleFileChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const selected = e.target.files?.[0];
    if (selected) applyFile(selected);
  }, [applyFile]);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragging(true);
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragging(false);
  }, []);

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragging(false);
    const dropped = e.dataTransfer.files?.[0];
    if (dropped) applyFile(dropped);
  }, [applyFile]);

  const handleRemoveFile = useCallback(() => {
    setFile(null);
    setUploadResult(null);
    setOutput("");
    setConversationId("");
  }, []);

  const handleRewrite = useCallback(async () => {
    if (!uploadResult) {
      toast.error("请先上传参考文章");
      return;
    }
    setStreaming(true);
    setOutput("");

    const controller = rewriteArticleStream(
      { requirements: requirements.trim(), wordCount, conversationId, modelId: selectedModelId },
      token,
      {
        onChunk: (text) => setOutput((prev) => prev + text),
        onMeta: (meta) => setConversationId(meta.conversationId),
        onDone: () => setStreaming(false),
        onError: (err) => {
          setStreaming(false);
          toast.error(err);
        }
      }
    );
    abortRef.current = controller;
  }, [requirements, wordCount, conversationId, token, uploadResult]);

  const handleStop = useCallback(() => {
    abortRef.current?.abort();
    setStreaming(false);
  }, []);

  const handleClearOutput = useCallback(() => {
    setOutput("");
    setConversationId("");
  }, []);

  const handleExport = useCallback(() => {
    if (!output) return;
    const blob = new Blob([output], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `imitation_${new Date().toISOString().slice(0, 10)}.txt`;
    a.click();
    URL.revokeObjectURL(url);
  }, [output]);

  return (
    <MainLayout>
      <div className="h-full overflow-y-auto bg-[#FAFAFA]">
        <div className="mx-auto max-w-[1200px] px-6 py-6">
          {/* header */}
          <div className="mb-6 flex items-center gap-3">
            <button
              onClick={() => navigate("/chat")}
              className="flex h-9 w-9 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-500 transition-colors hover:bg-slate-50"
            >
              <ArrowLeft className="h-4 w-4" />
            </button>
            <div>
              <h1 className="text-xl font-semibold text-slate-900">文章仿写</h1>
              <p className="text-sm text-slate-500">
                上传参考文章，AI 将通过同义替换、语序调整进行仿写，保持逻辑结构不变
              </p>
            </div>
          </div>

          <FlowTag title="文章仿写实现原理" steps={ARTICLE_IMITATION_STEPS} defaultExpanded className="mb-6" />

          <div className="grid gap-6 lg:grid-cols-[400px_1fr]">
            {/* left: upload + config */}
            <div className="space-y-5">
              {/* upload card */}
              <div className="rounded-xl border border-slate-200 bg-white shadow-sm">
                <div className="border-b border-slate-100 px-5 py-4">
                  <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-900">
                    <Upload className="h-4 w-4" />
                    上传参考文章
                  </h2>
                  <p className="mt-0.5 text-xs text-slate-500">
                    支持 txt、md、pdf、docx 格式
                  </p>
                </div>
                <div className="px-5 py-4">
                  {!file ? (
                    <label
                      onDragOver={handleDragOver}
                      onDragLeave={handleDragLeave}
                      onDrop={handleDrop}
                      className={cn(
                        "flex flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed px-6 py-8 cursor-pointer transition-colors",
                        dragging
                          ? "border-emerald-400 bg-emerald-50"
                          : "border-slate-200 bg-slate-50/50 hover:border-emerald-300 hover:bg-emerald-50/30"
                      )}
                    >
                      <input
                        type="file"
                        accept={ACCEPT_TYPES}
                        onChange={handleFileChange}
                        className="hidden"
                      />
                      <div className={cn(
                        "flex h-11 w-11 items-center justify-center rounded-full",
                        dragging ? "bg-emerald-100 text-emerald-600" : "bg-emerald-50 text-emerald-500"
                      )}>
                        <Upload className="h-5 w-5" />
                      </div>
                      <div className="text-center">
                        <p className="text-sm font-medium text-slate-700">
                          {dragging ? "松开即可上传" : "点击选择文件或拖拽到此处"}
                        </p>
                        <p className="mt-0.5 text-xs text-slate-400">
                          txt / md / pdf / docx
                        </p>
                      </div>
                    </label>
                  ) : (
                    <div className="space-y-3">
                      <div className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white p-3">
                        <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-emerald-50 text-emerald-500">
                          <FileText className="h-4 w-4" />
                        </div>
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-medium text-slate-700">
                            {file.name}
                          </p>
                          <p className="text-xs text-slate-400">
                            {(file.size / 1024).toFixed(1)} KB
                          </p>
                        </div>
                        <button
                          onClick={handleRemoveFile}
                          className="flex h-7 w-7 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-600"
                        >
                          <X className="h-4 w-4" />
                        </button>
                      </div>

                      {uploading ? (
                        <div className="flex items-center justify-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-600">
                          <Loader2 className="h-4 w-4 animate-spin" />
                          上传中...
                        </div>
                      ) : uploadResult ? (
                        <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
                          <p className="font-medium">上传成功</p>
                          <p className="text-xs text-emerald-600 mt-1">
                            任务 ID: {uploadResult.taskId} · 分块数:{" "}
                            {uploadResult.chunkCount} · 原文{" "}
                            {uploadResult.originalWordCount} 字
                          </p>
                        </div>
                      ) : null}
                    </div>
                  )}
                </div>
              </div>

              {/* config card */}
              <div className="rounded-xl border border-slate-200 bg-white shadow-sm">
                <div className="border-b border-slate-100 px-5 py-4">
                  <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-900">
                    <BookOpen className="h-4 w-4" />
                    仿写配置
                  </h2>
                </div>
                <div className="space-y-4 px-5 py-4">
                  {/* word count */}
                  <div className="space-y-2">
                    <label className="text-sm font-medium text-slate-700">
                      输出字数
                      {uploadResult?.originalWordCount ? (
                        <span className="ml-2 text-xs font-normal text-slate-400">
                          原文 {uploadResult.originalWordCount} 字
                        </span>
                      ) : null}
                    </label>
                    <div className="grid grid-cols-3 gap-2">
                      {WORD_COUNT_PRESETS.map((opt) => {
                        const original = uploadResult?.originalWordCount ?? 0;
                        const computed = Math.round(original * opt.ratio);
                        const disabled = computed > 3000;
                        const selected = wordCount === computed;
                        return (
                          <button
                            key={opt.ratio}
                            onClick={() => !disabled && setWordCount(computed)}
                            disabled={disabled}
                            className={cn(
                              "rounded-lg border px-3 py-2 text-center text-xs font-medium transition-colors",
                              disabled
                                ? "cursor-not-allowed border-slate-100 bg-slate-50 text-slate-300"
                                : selected
                                  ? "border-emerald-300 bg-emerald-50 text-emerald-700"
                                  : "border-slate-200 bg-white text-slate-600 hover:border-emerald-200 hover:bg-emerald-50/50"
                            )}
                          >
                            {opt.label}
                            <span className="block text-[10px] mt-0.5 text-slate-400">
                              {disabled ? "超限" : `原文${Math.round(opt.ratio * 100)}%`}
                            </span>
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  {/* model selector */}
                  {models.length > 0 && (
                    <div className="space-y-2">
                      <label className="text-sm font-medium text-slate-700">
                        模型选择
                      </label>
                      <select
                        value={selectedModelId}
                        onChange={(e) => setSelectedModelId(e.target.value)}
                        className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 focus:border-emerald-300 focus:outline-none"
                      >
                        <option value="">默认模型</option>
                        {models.map((m) => (
                          <option key={m.id} value={m.id}>
                            {m.id} ({m.provider})
                          </option>
                        ))}
                      </select>
                    </div>
                  )}

                  {/* requirements */}
                  <div className="space-y-2">
                    <label className="text-sm font-medium text-slate-700">
                      附加要求
                      <span className="ml-1 text-xs font-normal text-slate-400">（可选）</span>
                    </label>
                    <Textarea
                      value={requirements}
                      onChange={(e) => setRequirements(e.target.value)}
                      placeholder="例如：语气更正式一些、适当精简篇幅、更口语化..."
                      className="min-h-[100px] resize-none text-sm"
                    />
                  </div>

                  {/* action */}
                  {streaming ? (
                    <Button
                      onClick={handleStop}
                      variant="outline"
                      className="w-full border-red-200 text-red-600 hover:bg-red-50"
                    >
                      <Square className="mr-2 h-4 w-4" />
                      停止生成
                    </Button>
                  ) : (
                    <Button
                      onClick={handleRewrite}
                      disabled={!uploadResult}
                      className="w-full bg-emerald-600 hover:bg-emerald-700"
                    >
                      <Send className="mr-2 h-4 w-4" />
                      开始仿写
                    </Button>
                  )}
                </div>
              </div>
            </div>

            {/* right: output */}
            <div className="rounded-xl border border-slate-200 bg-white shadow-sm">
              <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
                <h2 className="text-sm font-semibold text-slate-900">
                  仿写结果
                </h2>
                {output && !streaming && (
                  <div className="flex items-center gap-1">
                    <button
                      onClick={handleExport}
                      className="flex items-center gap-1 rounded-lg px-2 py-1 text-xs text-slate-400 hover:bg-slate-100 hover:text-emerald-600"
                    >
                      <Download className="h-3 w-3" />
                      导出
                    </button>
                    <button
                      onClick={handleClearOutput}
                      className="flex items-center gap-1 rounded-lg px-2 py-1 text-xs text-slate-400 hover:bg-slate-100 hover:text-slate-600"
                    >
                      <Trash2 className="h-3 w-3" />
                      清空
                    </button>
                  </div>
                )}
              </div>
              <div className="p-5">
                <div
                  ref={outputRef}
                  className={cn(
                    "min-h-[500px] max-h-[calc(100vh-260px)] overflow-y-auto rounded-lg border border-slate-200 bg-slate-50/50 p-5",
                    !output && "flex items-center justify-center"
                  )}
                >
                  {output ? (
                    <div className="whitespace-pre-wrap text-sm leading-relaxed text-slate-700">
                      {output}
                      {streaming && (
                        <span className="ml-0.5 inline-block h-4 w-1.5 animate-pulse bg-emerald-500 align-middle" />
                      )}
                    </div>
                  ) : (
                    <div className="flex flex-col items-center gap-2 text-slate-400">
                      <FileText className="h-8 w-8" />
                      <p className="text-sm">
                        {streaming ? "正在生成中..." : "仿写内容将在这里显示"}
                      </p>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </MainLayout>
  );
}
