import { useCallback, useEffect, useState } from "react";
import {
  ArrowLeft,
  Download,
  FolderOpen,
  Loader2,
  Monitor,
  Package,
  Pencil,
  Plus,
  RefreshCw,
  Trash2,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
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
import { Badge } from "@/components/ui/badge";
import { MainLayout } from "@/components/layout/MainLayout";
import { cn } from "@/lib/utils";
import {
  getProjectList,
  createProject,
  deleteProject,
  type ScriptProject,
  type CreateProjectRequest,
} from "@/services/scriptService";
import { ScreenshotGuideModal } from "@/components/script/ScreenshotGuideModal";

export function ScriptPage() {
  const navigate = useNavigate();
  const [projects, setProjects] = useState<ScriptProject[]>([]);
  const [loading, setLoading] = useState(true);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<ScriptProject | null>(null);
  const [creating, setCreating] = useState(false);
  const [guideOpen, setGuideOpen] = useState(false);

  // create form
  const [form, setForm] = useState<CreateProjectRequest>({
    name: "",
    description: "",
  });

  const loadProjects = useCallback(async () => {
    try {
      setLoading(true);
      const data = await getProjectList();
      setProjects(data);
    } catch (err: any) {
      toast.error(err?.message || "加载项目列表失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadProjects();
  }, [loadProjects]);

  const handleCreate = async () => {
    if (!form.name.trim()) {
      toast.error("请输入项目名称");
      return;
    }
    setCreating(true);
    try {
      const project = await createProject({
        name: form.name.trim(),
        description: form.description?.trim() || undefined,
      });
      toast.success("项目创建成功");
      setCreateDialogOpen(false);
      setForm({ name: "", description: "" });
      navigate(`/script/${project.id}`);
    } catch (err: any) {
      toast.error(err?.message || "创建失败");
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await deleteProject(deleteTarget.id);
      toast.success("已删除");
      setDeleteTarget(null);
      await loadProjects();
    } catch (err: any) {
      toast.error(err?.message || "删除失败");
    }
  };

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return "-";
    return new Date(dateStr).toLocaleString("zh-CN");
  };

  const getStatusBadge = (status: string) => {
    if (status === "compiled") {
      return (
        <Badge variant="outline" className="border-emerald-200 bg-emerald-50 text-emerald-700">
          已编译
        </Badge>
      );
    }
    return (
      <Badge variant="outline" className="border-slate-200 bg-slate-50 text-slate-600">
        草稿
      </Badge>
    );
  };

  return (
    <MainLayout>
      <div className="h-full overflow-y-auto bg-[#FAFAFA]">
        <div className="mx-auto max-w-[1200px] px-6 py-6">
          {/* header */}
          <div className="mb-6 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <button
                onClick={() => navigate("/chat")}
                className="flex h-9 w-9 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-500 transition-colors hover:bg-slate-50"
              >
                <ArrowLeft className="h-4 w-4" />
              </button>
              <div>
                <h1 className="text-xl font-semibold text-slate-900">脚本项目</h1>
                <p className="text-sm text-slate-500">
                  创建和管理自动化脚本项目，通过截图标注生成 Python 脚本
                </p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <Button variant="outline" onClick={() => {
                const a = document.createElement("a");
                a.href = "/screenshot.exe";
                a.download = "screenshot.exe";
                a.click();
                setGuideOpen(true);
              }}>
                <Download className="mr-2 h-4 w-4" />
                截图工具
              </Button>
              <Button variant="outline" onClick={loadProjects}>
                <RefreshCw className="mr-2 h-4 w-4" />
                刷新
              </Button>
              <Button
                className="bg-violet-600 hover:bg-violet-700"
                onClick={() => setCreateDialogOpen(true)}
              >
                <Plus className="mr-2 h-4 w-4" />
                新建项目
              </Button>
            </div>
          </div>

          {/* project list */}
          {loading ? (
            <div className="flex items-center justify-center py-20">
              <Loader2 className="h-6 w-6 animate-spin text-slate-400" />
            </div>
          ) : projects.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-20 text-slate-400">
              <FolderOpen className="h-12 w-12 mb-3" />
              <p className="text-sm">暂无脚本项目</p>
              <p className="text-xs mt-1">点击「新建项目」开始创建</p>
            </div>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {projects.map((project) => (
                <Card
                  key={project.id}
                  className="group cursor-pointer transition-shadow hover:shadow-md"
                  onClick={() => navigate(`/script/${project.id}`)}
                >
                  <CardContent className="p-5">
                    <div className="mb-3 flex items-start justify-between">
                      <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-violet-50 text-violet-600">
                        <Package className="h-5 w-5" />
                      </div>
                      {getStatusBadge(project.status)}
                    </div>

                    <h3 className="mb-1 text-sm font-semibold text-slate-900 truncate">
                      {project.name}
                    </h3>
                    <p className="mb-3 text-xs text-slate-500 line-clamp-2 min-h-[32px]">
                      {project.description || "暂无描述"}
                    </p>

                    <div className="flex items-center gap-4 text-xs text-slate-400">
                      <span className="flex items-center gap-1">
                        <Monitor className="h-3 w-3" />
                        {project.targetWidth && project.targetHeight
                          ? `${project.targetWidth}x${project.targetHeight}${project.scalePct ? ` ${project.scalePct}%` : ""}`
                          : "上传截图自动检测"}
                      </span>
                      <span>{project.screenshotCount ?? 0} 张截图</span>
                      <span>{project.stepCount ?? 0} 个步骤</span>
                    </div>

                    <div className="mt-3 flex items-center justify-between border-t border-slate-100 pt-3">
                      <span className="text-xs text-slate-400">
                        {formatDate(project.updateTime)}
                      </span>
                      <div className="flex gap-1 opacity-0 transition-opacity group-hover:opacity-100">
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            navigate(`/script/${project.id}`);
                          }}
                          className="flex h-7 w-7 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-600"
                          title="编辑"
                        >
                          <Pencil className="h-3.5 w-3.5" />
                        </button>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setDeleteTarget(project);
                          }}
                          className="flex h-7 w-7 items-center justify-center rounded-md text-slate-400 hover:bg-red-50 hover:text-red-500"
                          title="删除"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </button>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* create dialog */}
      <Dialog open={createDialogOpen} onOpenChange={setCreateDialogOpen}>
        <DialogContent className="sm:max-w-[480px]">
          <DialogHeader>
            <DialogTitle>新建脚本项目</DialogTitle>
            <DialogDescription>上传第一张截图后将自动检测分辨率和缩放比例</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-700">
                项目名称 <span className="text-red-400">*</span>
              </label>
              <Input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="例如：自动签到脚本"
              />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-700">
                项目描述 <span className="text-xs text-slate-400">（可选）</span>
              </label>
              <Textarea
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                placeholder="简要描述脚本的用途..."
                className="min-h-[80px] resize-none"
              />
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setCreateDialogOpen(false)}
              disabled={creating}
            >
              取消
            </Button>
            <Button
              onClick={handleCreate}
              disabled={creating}
              className="bg-violet-600 hover:bg-violet-700"
            >
              {creating && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              创建
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* delete confirm */}
      <AlertDialog open={!!deleteTarget} onOpenChange={() => setDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除</AlertDialogTitle>
            <AlertDialogDescription>
              将永久删除项目「{deleteTarget?.name}」及其所有截图和步骤，无法恢复。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDelete}
              className="bg-red-600 text-white hover:bg-red-700"
            >
              删除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      <ScreenshotGuideModal open={guideOpen} onClose={() => setGuideOpen(false)} />
    </MainLayout>
  );
}
