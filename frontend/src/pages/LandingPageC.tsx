import { useNavigate } from "react-router-dom";
import { Bot, BookOpen, FileText, Wand2, ArrowRight, Github } from "lucide-react";

const FEATURES = [
  {
    icon: Bot,
    title: "灵效AI",
    desc: "检索增强生成，精准回答知识库问题",
    num: "01",
  },
  {
    icon: BookOpen,
    title: "小说续写",
    desc: "分析风格脉络，AI 自然衔接续写",
    num: "02",
  },
  {
    icon: FileText,
    title: "文章仿写",
    desc: "学习结构语气，生成风格一致的新文章",
    num: "03",
  },
  {
    icon: Wand2,
    title: "脚本生成",
    desc: "可视化标注步骤，自动编译为可执行文件",
    num: "04",
  },
];

export default function LandingPageC() {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-white">
      {/* Nav */}
      <nav className="flex items-center justify-between px-10 py-6">
        <div className="flex items-center gap-2.5">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-slate-900">
            <Bot className="h-5 w-5 text-white" />
          </div>
          <span className="text-xl font-bold tracking-tight text-slate-900">灵效AI</span>
        </div>
        <div className="flex items-center gap-3">
          <a
            href="https://github.com/wxsh-hub/ZKD_SPEED"
            target="_blank"
            rel="noreferrer"
            className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-900 transition"
          >
            <Github className="h-4 w-4" />
          </a>
          <button
            onClick={() => navigate("/login")}
            className="rounded-lg bg-slate-900 px-5 py-2 text-sm font-medium text-white transition hover:bg-slate-800"
          >
            登录
          </button>
        </div>
      </nav>

      {/* Hero */}
      <section className="mx-auto max-w-4xl px-10 pt-24 pb-10">
        <p className="mb-4 text-sm font-medium text-slate-400 tracking-wide uppercase">
          AI 驱动 · 汕头大学
        </p>
        <h1 className="text-[3.5rem] font-extrabold leading-[1.15] tracking-tight text-slate-900 mb-6">
          智能问答，创作无限。
        </h1>
        <p className="max-w-xl text-lg text-slate-500 leading-relaxed mb-10">
          从精准检索到内容创作再到流程自动化，
          <br />
          一站式的 AI 智能平台。
        </p>
        <div className="flex items-center gap-4">
          <button
            onClick={() => navigate("/login")}
            className="inline-flex items-center gap-2 rounded-lg bg-slate-900 px-7 py-3 text-sm font-semibold text-white transition hover:bg-slate-800"
          >
            立即体验 <ArrowRight className="h-4 w-4" />
          </button>
        </div>
      </section>

      {/* Divider */}
      <div className="mx-auto max-w-5xl px-10">
        <div className="h-px bg-slate-100" />
      </div>

      {/* Features */}
      <section className="mx-auto max-w-5xl px-10 py-20">
        <div className="grid grid-cols-1 gap-0 divide-y divide-slate-100 sm:grid-cols-2 sm:divide-y-0 sm:divide-x">
          {FEATURES.map((f) => (
            <div
              key={f.title}
              onClick={() => navigate("/login")}
              className="group cursor-pointer p-8 transition hover:bg-slate-50"
            >
              <div className="mb-3 flex items-center gap-3">
                <span className="text-xs font-mono font-medium text-slate-300">{f.num}</span>
                <f.icon className="h-5 w-5 text-slate-400 group-hover:text-violet-500 transition" />
              </div>
              <h3 className="mb-1.5 text-base font-semibold text-slate-900">{f.title}</h3>
              <p className="text-sm text-slate-500 leading-relaxed">{f.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-slate-100 py-8 text-center">
        <p className="text-xs text-slate-300">汕头大学 · 黄炜</p>
      </footer>
    </div>
  );
}
