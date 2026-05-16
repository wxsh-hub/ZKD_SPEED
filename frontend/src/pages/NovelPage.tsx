import { useCallback, useEffect, useRef, useState } from "react";
import {
  ArrowLeft,
  BookOpen,
  Download,
  FileText,
  Loader2,
  RefreshCw,
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
import { FlowTag, NOVEL_CONTINUE_STEPS } from "@/components/common/FlowTag";
import { useAuthStore } from "@/stores/authStore";
import {
  uploadNovel,
  continueNovelStream,
  type NovelUploadResult
} from "@/services/novelService";
import { getSystemSettings, type ModelCandidate } from "@/services/settingsService";
import { storage } from "@/utils/storage";

const WORD_COUNT_PRESETS = [
  { value: 300, label: "300字" },
  { value: 500, label: "500字" },
  { value: 700, label: "700字" },
  { value: 1000, label: "1000字" },
  { value: 3000, label: "3000字" },
];

const ACCEPT_TYPES = ".txt,.md,.pdf,.docx";

export function NovelPage() {
  const navigate = useNavigate();
  const token = useAuthStore((s) => s.token) ?? storage.getToken() ?? "";

  // upload state
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadResult, setUploadResult] = useState<NovelUploadResult | null>(null);
  const [dragging, setDragging] = useState(false);

  // continue state
  const [direction, setDirection] = useState("");
  const [wordCount, setWordCount] = useState(700);
  const [selectedModelId, setSelectedModelId] = useState("");
  const [models, setModels] = useState<ModelCandidate[]>([]);
  const [conversationId, setConversationId] = useState("");
  const [output, setOutput] = useState("");
  const [streaming, setStreaming] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const outputRef = useRef<HTMLDivElement>(null);

  // regenerate state
  const [history, setHistory] = useState<Array<{ direction: string; feedback: string; output: string }>>([]);
  const [showRegenerate, setShowRegenerate] = useState(false);
  const [feedback, setFeedback] = useState("");

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

  const applyFile = useCallback(async (selected: File) => {
    setFile(selected);
    setUploadResult(null);
    setOutput("");
    setConversationId("");
    setUploading(true);
    try {
      const result = await uploadNovel(selected);
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

  const handleContinue = useCallback(async () => {
    if (output) {
      setHistory((prev) => [...prev, { direction, feedback: "", output }]);
    }
    setShowRegenerate(false);
    setStreaming(true);
    setOutput("");

    const controller = continueNovelStream(
      { direction: direction.trim(), wordCount, conversationId, modelId: selectedModelId },
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
  }, [direction, wordCount, conversationId, token]);

  const handleStop = useCallback(() => {
    abortRef.current?.abort();
    setStreaming(false);
  }, []);

  const handleClearOutput = useCallback(() => {
    setOutput("");
    setConversationId("");
    setHistory([]);
    setShowRegenerate(false);
    setFeedback("");
  }, []);

  const handleExport = useCallback(() => {
    if (!output) return;
    const blob = new Blob([output], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `novel_${new Date().toISOString().slice(0, 10)}.txt`;
    a.click();
    URL.revokeObjectURL(url);
  }, [output]);

  const handleRegenerate = useCallback(() => {
    if (!feedback.trim()) {
      toast.error("请输入改进意见");
      return;
    }
    if (output) {
      setHistory((prev) => [...prev, { direction, feedback: feedback.trim(), output }]);
    }
    const regenerateDirection = feedback.trim();
    setFeedback("");
    setShowRegenerate(false);
    setStreaming(true);
    setOutput("");

    const controller = continueNovelStream(
      { direction: regenerateDirection, wordCount, conversationId, modelId: selectedModelId },
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
  }, [feedback, direction, output, wordCount, conversationId, selectedModelId, token]);

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
              <h1 className="text-xl font-semibold text-slate-900">小说续写</h1>
              <p className="text-sm text-slate-500">
                上传小说文件，输入续写方向，AI 将基于原文风格进行续写
              </p>
            </div>
          </div>

          <FlowTag title="小说续写实现原理" steps={NOVEL_CONTINUE_STEPS} defaultExpanded className="mb-6" />

          <div className="grid gap-6 lg:grid-cols-[400px_1fr]">
            {/* left: upload + config */}
            <div className="space-y-5">
              {/* upload card */}
              <div className="rounded-xl border border-slate-200 bg-white shadow-sm">
                <div className="border-b border-slate-100 px-5 py-4">
                  <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-900">
                    <Upload className="h-4 w-4" />
                    上传小说文件
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
                          ? "border-orange-400 bg-orange-50"
                          : "border-slate-200 bg-slate-50/50 hover:border-orange-300 hover:bg-orange-50/30"
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
                        dragging ? "bg-orange-100 text-orange-600" : "bg-orange-50 text-orange-500"
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
                        <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-orange-50 text-orange-500">
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
                        <div className="flex items-center justify-center gap-2 rounded-lg border border-orange-200 bg-orange-50 px-4 py-3 text-sm text-orange-600">
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
                    续写配置
                  </h2>
                </div>
                <div className="space-y-4 px-5 py-4">
                  {/* word count */}
                  <div className="space-y-2">
                    <label className="text-sm font-medium text-slate-700">
                      续写字数
                    </label>
                    <div className="grid grid-cols-5 gap-2">
                      {WORD_COUNT_PRESETS.map((opt) => {
                        const selected = wordCount === opt.value;
                        return (
                          <button
                            key={opt.value}
                            onClick={() => setWordCount(opt.value)}
                            className={cn(
                              "rounded-lg border px-3 py-2 text-center text-xs font-medium whitespace-nowrap transition-colors",
                              selected
                                ? "border-orange-300 bg-orange-50 text-orange-700"
                                : "border-slate-200 bg-white text-slate-600 hover:border-orange-200 hover:bg-orange-50/50"
                            )}
                          >
                            {opt.label}
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
                        className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 focus:border-orange-300 focus:outline-none"
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

                  {/* direction */}
                  <div className="space-y-2">
                    <label className="text-sm font-medium text-slate-700">
                      续写方向
                    </label>
                    <Textarea
                      value={direction}
                      onChange={(e) => setDirection(e.target.value)}
                      placeholder="可选，不填则按剧情自然续写。例如：主角进入密林后遭遇暴风雨，意外发现了一座废弃的古庙..."
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
                      onClick={handleContinue}
                      className="w-full bg-orange-500 hover:bg-orange-600"
                    >
                      <Send className="mr-2 h-4 w-4" />
                      开始续写
                    </Button>
                  )}
                </div>
              </div>
            </div>

            {/* right: output */}
            <div className="rounded-xl border border-slate-200 bg-white shadow-sm">
              <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
                <h2 className="text-sm font-semibold text-slate-900">
                  续写结果
                </h2>
                {output && !streaming && (
                  <div className="flex items-center gap-1">
                    <button
                      onClick={() => setShowRegenerate(!showRegenerate)}
                      title="输入改进意见，基于对话历史重新生成"
                      className={cn(
                        "flex items-center gap-1 rounded-lg px-2 py-1 text-xs transition-colors",
                        showRegenerate
                          ? "bg-orange-100 text-orange-600"
                          : "text-slate-400 hover:bg-slate-100 hover:text-orange-600"
                      )}
                    >
                      <RefreshCw className="h-3 w-3" />
                      重新生成
                    </button>
                    <button
                      onClick={handleExport}
                      className="flex items-center gap-1 rounded-lg px-2 py-1 text-xs text-slate-400 hover:bg-slate-100 hover:text-orange-600"
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
              {/* regenerate feedback - 紧跟头部，永远可见 */}
              {showRegenerate && output && !streaming && (
                <div className="border-b border-orange-100 bg-orange-50/30 px-5 py-4 space-y-3">
                  <p className="text-sm font-medium text-orange-700">
                    请描述您的改进意见
                  </p>
                  <Textarea
                    value={feedback}
                    onChange={(e) => setFeedback(e.target.value)}
                    placeholder="例如：节奏太慢了，加快剧情发展；人物对话不够生动；结局需要更开放..."
                    className="min-h-[80px] resize-none text-sm bg-white"
                    autoFocus
                    onKeyDown={(e) => {
                      if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) {
                        handleRegenerate();
                      }
                    }}
                  />
                  <div className="flex items-center gap-2">
                    <Button
                      onClick={handleRegenerate}
                      disabled={!feedback.trim()}
                      className="bg-orange-500 hover:bg-orange-600"
                      size="sm"
                    >
                      <RefreshCw className="mr-1.5 h-3.5 w-3.5" />
                      确认重新生成
                    </Button>
                    <Button
                      onClick={() => { setShowRegenerate(false); setFeedback(""); }}
                      variant="ghost"
                      size="sm"
                      className="text-slate-500"
                    >
                      取消
                    </Button>
                    <span className="ml-auto text-xs text-slate-400">
                      Ctrl+Enter 发送
                    </span>
                  </div>
                </div>
              )}
              <div className="p-5 space-y-4">
                {/* history */}
                {history.length > 0 && (
                  <div className="flex flex-wrap items-center gap-2">
                    {history.map((item, i) => (
                      <div key={i} className="flex items-center gap-1.5 rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs">
                        <span className="text-slate-400">第{i + 1}轮</span>
                        {item.feedback && (
                          <span className="text-orange-500 truncate max-w-[120px]">{item.feedback}</span>
                        )}
                      </div>
                    ))}
                  </div>
                )}

                {/* current output */}
                <div
                  ref={outputRef}
                  className={cn(
                    "min-h-[400px] max-h-[calc(100vh-360px)] overflow-y-auto rounded-lg border border-slate-200 bg-slate-50/50 p-5",
                    !output && !streaming && "flex items-center justify-center"
                  )}
                >
                  {output ? (
                    <div className="whitespace-pre-wrap text-sm leading-relaxed text-slate-700">
                      {output}
                      {streaming && (
                        <span className="ml-0.5 inline-block h-4 w-1.5 animate-pulse bg-orange-500 align-middle" />
                      )}
                    </div>
                  ) : streaming ? (
                    <div className="flex flex-col items-center gap-2 text-slate-400">
                      <Loader2 className="h-6 w-6 animate-spin text-orange-400" />
                      <p className="text-sm">正在生成中...</p>
                    </div>
                  ) : (
                    <div className="flex flex-col items-center gap-2 text-slate-400">
                      <BookOpen className="h-8 w-8" />
                      <p className="text-sm">续写内容将在这里显示</p>
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
