import { useNavigate } from "react-router-dom";
import { Bot, BookOpen, FileText, Wand2, ArrowRight, Sparkles } from "lucide-react";
import { useAuthStore } from "@/stores/authStore";

const FEATURES = [
  {
    icon: Bot,
    title: "RAG 智能问答",
    desc: "基于检索增强生成技术，精准理解知识库内容，给出可靠回答",
    gradient: "from-violet-500/20 to-purple-500/20",
    iconColor: "text-violet-400",
    border: "border-violet-500/20",
  },
  {
    icon: BookOpen,
    title: "小说续写",
    desc: "分析前文风格与情节脉络，AI 自然衔接续写后续内容",
    gradient: "from-blue-500/20 to-cyan-500/20",
    iconColor: "text-blue-400",
    border: "border-blue-500/20",
  },
  {
    icon: FileText,
    title: "文章仿写",
    desc: "学习目标文章的结构、语气和表达方式，生成风格一致的新文章",
    gradient: "from-amber-500/20 to-orange-500/20",
    iconColor: "text-amber-400",
    border: "border-amber-500/20",
  },
  {
    icon: Wand2,
    title: "脚本生成",
    desc: "可视化标注操作步骤，自动生成自动化脚本并编译为可执行文件",
    gradient: "from-emerald-500/20 to-teal-500/20",
    iconColor: "text-emerald-400",
    border: "border-emerald-500/20",
  },
];

export default function LandingPageB() {
  const navigate = useNavigate();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const handleStart = () => {
    navigate(isAuthenticated ? "/chat" : "/login");
  };

  return (
    <div className="min-h-screen bg-[#0a0a0f] text-white overflow-hidden">
      {/* Background glow */}
      <div className="pointer-events-none fixed inset-0">
        <div className="absolute left-1/2 top-0 h-[600px] w-[800px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-violet-600/10 blur-[120px]" />
        <div className="absolute right-0 bottom-0 h-[400px] w-[600px] translate-x-1/4 translate-y-1/4 rounded-full bg-blue-600/8 blur-[100px]" />
      </div>

      {/* Nav */}
      <nav className="relative z-10 flex items-center justify-between px-8 py-5">
        <div className="flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-violet-500 to-purple-600">
            <Bot className="h-4 w-4 text-white" />
          </div>
          <span className="text-lg font-bold">智答客</span>
        </div>
        <button
          onClick={handleStart}
          className="rounded-full border border-white/10 bg-white/5 px-5 py-2 text-sm font-medium text-white/80 backdrop-blur transition hover:bg-white/10"
        >
          登录
        </button>
      </nav>

      {/* Hero */}
      <section className="relative z-10 mx-auto max-w-4xl px-8 pt-28 pb-20 text-center">
        <div className="mb-8 inline-flex items-center gap-2 rounded-full border border-violet-500/30 bg-violet-500/10 px-4 py-1.5 text-xs font-medium text-violet-300">
          <Sparkles className="h-3.5 w-3.5" />
          AI 驱动的智能创作平台
        </div>
        <h1 className="text-6xl font-extrabold tracking-tight mb-6 leading-[1.1]">
          <span className="text-white/90">让 AI 成为你的</span>
          <br />
          <span className="bg-gradient-to-r from-violet-400 via-purple-400 to-fuchsia-400 bg-clip-text text-transparent">
            创作搭档
          </span>
        </h1>
        <p className="mx-auto max-w-xl text-base text-white/40 leading-relaxed mb-10">
          智答客集成智能问答、小说续写、文章仿写、自动化脚本生成四大功能，
          从信息检索到内容创作再到流程自动化，一站搞定。
        </p>
        <button
          onClick={handleStart}
          className="inline-flex items-center gap-2 rounded-full bg-gradient-to-r from-violet-600 to-purple-600 px-8 py-3.5 text-sm font-semibold text-white shadow-lg shadow-violet-500/30 transition hover:shadow-violet-500/50 hover:brightness-110"
        >
          开始使用 <ArrowRight className="h-4 w-4" />
        </button>
      </section>

      {/* Features */}
      <section className="relative z-10 mx-auto max-w-5xl px-8 pb-24">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {FEATURES.map((f) => (
            <div
              key={f.title}
              className={`group rounded-2xl border ${f.border} bg-gradient-to-b ${f.gradient} p-6 backdrop-blur-sm transition hover:border-white/20 hover:bg-white/5 cursor-pointer`}
              onClick={handleStart}
            >
              <f.icon className={`mb-4 h-8 w-8 ${f.iconColor}`} />
              <h3 className="mb-2 text-sm font-semibold text-white/90">{f.title}</h3>
              <p className="text-xs leading-relaxed text-white/35">{f.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Footer */}
      <footer className="relative z-10 border-t border-white/5 py-6 text-center text-xs text-white/20">
        汕头大学 · 黄炜
      </footer>
    </div>
  );
}
