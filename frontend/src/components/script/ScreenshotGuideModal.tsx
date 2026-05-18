import { useState } from "react";
import { Download, Copy, Check, X, Camera, Settings, Upload, ArrowRight } from "lucide-react";
import { toast } from "sonner";

interface Props {
  open: boolean;
  onClose: () => void;
  uploadToken?: string | null;
}

const STEPS = [
  {
    icon: Copy,
    title: "复制上传 Token",
    desc: "在脚本项目详情页顶部，点击「Token」按钮复制上传凭证。每个项目有独立的 Token，截图会自动关联到对应项目。",
    color: "text-violet-500",
    bg: "bg-violet-50",
  },
  {
    icon: Download,
    title: "下载并打开截图工具",
    desc: "点击下方按钮下载截图工具 EXE 文件，双击运行。首次运行可能被杀毒软件拦截，请参考编译脚本的引导添加信任。",
    color: "text-blue-500",
    bg: "bg-blue-50",
  },
  {
    icon: Settings,
    title: "配置 Token 和服务器",
    desc: "在截图工具的「上传配置」区域粘贴 Token，服务器地址默认即可。勾选「截图后自动上传」，之后每次按 F2 截图都会自动上传到当前项目。",
    color: "text-emerald-500",
    bg: "bg-emerald-50",
  },
  {
    icon: Upload,
    title: "截图并上传",
    desc: "按 F2 截图，图片会自动保存到本地并上传到服务器。也可以只设置保存路径，之后在网页上手动批量上传。",
    color: "text-amber-500",
    bg: "bg-amber-50",
  },
];

export function ScreenshotGuideModal({ open, onClose, uploadToken }: Props) {
  const [step, setStep] = useState(0);
  const [copied, setCopied] = useState(false);

  if (!open) return null;

  const current = STEPS[step];
  const isFirst = step === 0;
  const isLast = step === STEPS.length - 1;

  const handleCopy = () => {
    if (!uploadToken) return;
    navigator.clipboard.writeText(uploadToken);
    setCopied(true);
    toast.success("Token 已复制");
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="mx-4 w-full max-w-lg overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl">
        {/* header */}
        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-3">
          <h2 className="text-sm font-semibold text-slate-900">截图工具使用指南</h2>
          <button
            onClick={onClose}
            className="flex h-7 w-7 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* content */}
        <div className="px-5 py-6">
          <div className="mb-5 flex items-center gap-4">
            <div className={`flex h-14 w-14 items-center justify-center rounded-2xl ${current.bg}`}>
              <current.icon className={`h-7 w-7 ${current.color}`} />
            </div>
            <div>
              <div className="mb-1 text-xs font-medium text-slate-400">第 {step + 1} / {STEPS.length} 步</div>
              <h3 className="text-base font-semibold text-slate-900">{current.title}</h3>
            </div>
          </div>
          <p className="text-sm leading-relaxed text-slate-600">{current.desc}</p>

          {/* Token 复制区（仅第 1 步显示） */}
          {step === 0 && uploadToken && (
            <div className="mt-4 flex items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5">
              <code className="flex-1 truncate font-mono text-xs text-slate-600">{uploadToken}</code>
              <button
                onClick={handleCopy}
                className="flex h-7 items-center gap-1.5 rounded-md bg-white px-2.5 text-xs font-medium text-slate-700 shadow-sm border border-slate-200 hover:bg-slate-50 transition"
              >
                {copied ? <Check className="h-3.5 w-3.5 text-emerald-500" /> : <Copy className="h-3.5 w-3.5" />}
                {copied ? "已复制" : "复制"}
              </button>
            </div>
          )}

          {/* 无 Token 提示 */}
          {step === 0 && !uploadToken && (
            <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2.5 text-xs text-amber-600">
              当前项目暂无 Token，请先创建项目后获取。
            </div>
          )}
        </div>

        {/* footer */}
        <div className="flex items-center justify-between border-t border-slate-100 px-5 py-3">
          <div className="flex gap-1.5">
            {STEPS.map((_, i) => (
              <button
                key={i}
                onClick={() => setStep(i)}
                className={`h-2 w-2 rounded-full transition-colors ${
                  i === step ? "bg-violet-500" : "bg-slate-300 hover:bg-slate-400"
                }`}
              />
            ))}
          </div>
          <div className="flex items-center gap-2">
            {!isFirst && (
              <button
                onClick={() => setStep(step - 1)}
                className="rounded-lg px-3 py-1.5 text-xs text-slate-500 hover:bg-slate-100"
              >
                上一步
              </button>
            )}
            {isLast ? (
              <button
                onClick={onClose}
                className="rounded-lg bg-slate-900 px-4 py-1.5 text-xs font-medium text-white hover:bg-slate-800 transition"
              >
                知道了
              </button>
            ) : (
              <button
                onClick={() => setStep(step + 1)}
                className="inline-flex items-center gap-1 rounded-lg bg-slate-900 px-4 py-1.5 text-xs font-medium text-white hover:bg-slate-800 transition"
              >
                下一步 <ArrowRight className="h-3.5 w-3.5" />
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
