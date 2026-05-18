import { useCallback, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  ArrowLeft,
  Code,
  Download,
  Loader2,
  Package,
} from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { useScriptStore } from "@/stores/scriptStore";

export function ProjectHeader() {
  const navigate = useNavigate();
  const { projectId } = useParams<{ projectId: string }>();
  const { currentProject, isCompiling, compileStatus, generateScript, startCompilation, pollCompileStatus } =
    useScriptStore();

  const [generating, setGenerating] = useState(false);

  const handleGenerate = useCallback(async () => {
    if (!projectId) return;
    setGenerating(true);
    try {
      const blob = await generateScript(projectId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "script.py";
      a.click();
      URL.revokeObjectURL(url);
      toast.success("脚本已下载");
    } catch {
      toast.error("生成脚本失败");
    } finally {
      setGenerating(false);
    }
  }, [projectId, generateScript]);

  const handleCompile = useCallback(async () => {
    if (!projectId) return;
    try {
      await startCompilation(projectId);
      toast.success("开始编译...");
      // 轮询状态
      const poll = async () => {
        await pollCompileStatus(projectId);
        const status = useScriptStore.getState().compileStatus;
        if (status === "SUCCESS") {
          toast.success("编译完成");
        } else if (status === "FAILED") {
          toast.error("编译失败");
        } else if (status === "RUNNING" || status === "PENDING") {
          setTimeout(poll, 2000);
        }
      };
      setTimeout(poll, 2000);
    } catch {
      toast.error("启动编译失败");
    }
  }, [projectId, startCompilation, pollCompileStatus]);

  if (!currentProject) return null;

  return (
    <div className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-2">
      <div className="flex items-center gap-3">
        <button
          onClick={() => navigate("/script")}
          className="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 text-slate-500 transition-colors hover:bg-slate-50"
        >
          <ArrowLeft className="h-4 w-4" />
        </button>
        <h1 className="text-sm font-semibold text-slate-900">
          {currentProject.name}
        </h1>
      </div>
      <div className="flex items-center gap-2">
        <Button
          size="sm"
          variant="outline"
          onClick={handleGenerate}
          disabled={generating}
        >
          {generating ? (
            <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" />
          ) : (
            <Code className="mr-1 h-3.5 w-3.5" />
          )}
          生成脚本
        </Button>
        <Button
          size="sm"
          onClick={handleCompile}
          disabled={isCompiling}
          className="bg-emerald-600 hover:bg-emerald-700"
        >
          {isCompiling ? (
            <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" />
          ) : (
            <Package className="mr-1 h-3.5 w-3.5" />
          )}
          {isCompiling ? "编译中..." : "编译 EXE"}
        </Button>
        {compileStatus === "SUCCESS" && (
          <Button
            size="sm"
            variant="outline"
            onClick={() =>
              window.open(`/api/ragent/script/project/${projectId}/download-exe`)
            }
          >
            <Download className="mr-1 h-3.5 w-3.5" />
            下载 EXE
          </Button>
        )}
      </div>
    </div>
  );
}
