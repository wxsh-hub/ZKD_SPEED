import { useCallback, useRef } from "react";
import { useParams } from "react-router-dom";
import { FolderOpen, ImagePlus, X } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { useScriptStore } from "@/stores/scriptStore";
import { cn } from "@/lib/utils";

const IMAGE_EXTS = [".png", ".jpg", ".jpeg", ".bmp", ".webp"];

function isImageFile(name: string): boolean {
  const lower = name.toLowerCase();
  return IMAGE_EXTS.some((ext) => lower.endsWith(ext));
}

export function ScreenshotPanel() {
  const { projectId } = useParams<{ projectId: string }>();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const folderInputRef = useRef<HTMLInputElement>(null);
  const {
    screenshots,
    selectedScreenshotId,
    selectScreenshot,
    uploadScreenshots,
    deleteScreenshot,
  } = useScriptStore();

  const doUpload = useCallback(
    async (files: File[]) => {
      if (!projectId || files.length === 0) return;
      try {
        await uploadScreenshots(projectId, files);
        toast.success(`已上传 ${files.length} 张截图`);
      } catch (err: any) {
        toast.error(err?.message || "上传失败");
      }
    },
    [projectId, uploadScreenshots]
  );

  const handleUpload = useCallback(
    async (e: React.ChangeEvent<HTMLInputElement>) => {
      const files = e.target.files;
      if (!files || files.length === 0) return;
      await doUpload(Array.from(files));
      e.target.value = "";
    },
    [doUpload]
  );

  const handleFolderUpload = useCallback(
    async (e: React.ChangeEvent<HTMLInputElement>) => {
      const files = e.target.files;
      if (!files || files.length === 0) return;
      const imageFiles = Array.from(files).filter((f) => isImageFile(f.name));
      if (imageFiles.length === 0) {
        toast.error("文件夹中没有找到图片文件");
      } else {
        await doUpload(imageFiles);
      }
      e.target.value = "";
    },
    [doUpload]
  );

  const handleDelete = useCallback(
    async (e: React.MouseEvent, id: string) => {
      e.stopPropagation();
      try {
        await deleteScreenshot(id);
      } catch {
        toast.error("删除失败");
      }
    },
    [deleteScreenshot]
  );

  return (
    <div className="flex w-60 flex-col border-r border-slate-200 bg-white">
      <div className="flex items-center justify-between border-b border-slate-100 px-3 py-2">
        <span className="text-xs font-medium text-slate-600">截图列表</span>
        <div className="flex gap-1">
          <Button
            size="sm"
            variant="ghost"
            className="h-7 px-2 text-xs"
            onClick={() => fileInputRef.current?.click()}
          >
            <ImagePlus className="mr-1 h-3.5 w-3.5" />
            上传
          </Button>
          <Button
            size="sm"
            variant="ghost"
            className="h-7 px-2 text-xs"
            onClick={() => folderInputRef.current?.click()}
          >
            <FolderOpen className="mr-1 h-3.5 w-3.5" />
            文件夹
          </Button>
        </div>
        <input
          ref={fileInputRef}
          type="file"
          multiple
          accept=".png,.jpg,.jpeg,.bmp,.webp"
          className="hidden"
          onChange={handleUpload}
        />
        <input
          ref={folderInputRef}
          type="file"
          // @ts-ignore - webkitdirectory is non-standard but widely supported
          webkitdirectory=""
          className="hidden"
          onChange={handleFolderUpload}
        />
      </div>
      <div className="flex-1 overflow-y-auto p-2">
        {screenshots.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-8 text-slate-400">
            <ImagePlus className="mb-2 h-8 w-8" />
            <p className="text-xs">暂无截图</p>
          </div>
        ) : (
          <div className="space-y-2">
            {screenshots.map((s) => (
              <div
                key={s.id}
                onClick={() => selectScreenshot(s.id)}
                className={cn(
                  "group relative cursor-pointer overflow-hidden rounded-lg border-2 transition-all",
                  selectedScreenshotId === s.id
                    ? "border-emerald-400 shadow-sm"
                    : "border-transparent hover:border-slate-300"
                )}
              >
                <img
                  src={`/api/ragent${s.fileUrl}`}
                  alt={s.fileName}
                  className="w-full object-cover"
                  style={{ maxHeight: 120 }}
                />
                <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/50 to-transparent p-1.5">
                  <p className="truncate text-[10px] text-white">
                    {s.fileName}
                  </p>
                  <p className="text-[9px] text-white/70">
                    {s.width}×{s.height}
                  </p>
                </div>
                <button
                  onClick={(e) => handleDelete(e, s.id)}
                  className="absolute right-1 top-1 flex h-5 w-5 items-center justify-center rounded bg-black/40 text-white opacity-0 transition-opacity group-hover:opacity-100"
                >
                  <X className="h-3 w-3" />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
