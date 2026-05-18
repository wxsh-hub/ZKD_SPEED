import { useNavigate } from "react-router-dom";
import { Bot, BookOpen, FileText, Wand2, ArrowRight, Github } from "lucide-react";

const FEATURES = [
  {
    icon: Bot,
    title: "RAG 智能问答",
    desc: "基于检索增强生成技术，精准理解知识库内容，给出可靠回答",
    color: "from-violet-500 to-purple-600",
    link: "/chat",
  },
  {
    icon: BookOpen,
    title: "小说续写",
    desc: "分析前文风格与情节脉络，AI 自然衔接续写后续内容",
    color: "from-blue-500 to-cyan-500",
    link: "/novel",
  },
  {
    icon: FileText,
    title: "文章仿写",
    desc: "学习目标文章的结构、语气和表达方式，生成风格一致的新文章",
    color: "from-amber-500 to-orange-500",
    link: "/imitation",
  },
  {
    icon: Wand2,
    title: "脚本生成",
    desc: "可视化标注操作步骤，自动生成自动化脚本并编译为可执行文件",
    color: "from-emerald-500 to-teal-500",
    link: "/script",
  },
];

export default function LandingPageA() {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-violet-50">
      {/* Nav */}
      <nav className="flex items-center justify-between px-8 py-5">
        <div className="flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-violet-500 to-purple-600">
            <Bot className="h-4 w-4 text-white" />
          </div>
          <span className="text-lg font-bold text-slate-900">智答客</span>
        </div>
        <button
          onClick={() => navigate("/login")}
          className="rounded-full bg-slate-900 px-5 py-2 text-sm font-medium text-white transition hover:bg-slate-800"
        >
          登录
        </button>
      </nav>

      {/* Hero */}
      <section className="mx-auto max-w-5xl px-8 pt-20 pb-16 text-center">
        <div className="inline-block rounded-full border border-violet-200 bg-violet-50 px-4 py-1.5 text-xs font-medium text-violet-600 mb-6">
          AI 驱动的智能创作平台
        </div>
        <h1 className="text-5xl font-extrabold tracking-tight text-slate-900 mb-5">
          让 AI 成为你的
          <span className="bg-gradient-to-r from-violet-600 to-purple-500 bg-clip-text text-transparent"> 创作搭档</span>
        </h1>
        <p className="mx-auto max-w-2xl text-lg text-slate-500 leading-relaxed mb-10">
          智答客集成智能问答、小说续写、文章仿写、自动化脚本生成四大功能，
          帮你从信息检索到内容创作再到流程自动化，一站搞定。
        </p>
        <div className="flex items-center justify-center gap-4">
          <button
            onClick={() => navigate("/login")}
            className="inline-flex items-center gap-2 rounded-full bg-gradient-to-r from-violet-600 to-purple-600 px-8 py-3 text-sm font-semibold text-white shadow-lg shadow-violet-500/25 transition hover:shadow-violet-500/40"
          >
            开始使用 <ArrowRight className="h-4 w-4" />
          </button>
          <a
            href="https://github.com/wxsh-hub/ZKD_SPEED"
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-6 py-3 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
          >
            <Github className="h-4 w-4" /> GitHub
          </a>
        </div>
      </section>

      {/* Features */}
      <section className="mx-auto max-w-5xl px-8 pb-24">
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {FEATURES.map((f) => (
            <div
              key={f.title}
              onClick={() => navigate(f.link)}
              className="group cursor-pointer rounded-2xl border border-slate-100 bg-white p-6 shadow-sm transition hover:-translate-y-1 hover:shadow-lg"
            >
              <div className={`mb-4 flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br ${f.color} text-white`}>
                <f.icon className="h-5 w-5" />
              </div>
              <h3 className="mb-2 text-sm font-semibold text-slate-900">{f.title}</h3>
              <p className="text-xs leading-relaxed text-slate-500">{f.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-slate-100 py-6 text-center text-xs text-slate-400">
        汕头大学 · 黄炜
      </footer>
    </div>
  );
}
