import * as React from "react";
import { differenceInCalendarDays, isValid } from "date-fns";
import {
  BookOpen,
  Bot,
  ChevronRight,
  Code,
  FileEdit,
  LogOut,
  MessageSquare,
  MoreHorizontal,
  Pencil,
  Plus,
  Search,
  Settings,
  Trash2
} from "lucide-react";
import { useNavigate } from "react-router-dom";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle
} from "@/components/ui/alert-dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu";
import { Loading } from "@/components/common/Loading";
import { cn } from "@/lib/utils";
import { useAuthStore } from "@/stores/authStore";
import { useChatStore } from "@/stores/chatStore";

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
}

export function Sidebar({ isOpen, onClose }: SidebarProps) {
  const {
    sessions,
    currentSessionId,
    isLoading,
    sessionsLoaded,
    createSession,
    deleteSession,
    renameSession,
    selectSession,
    fetchSessions
  } = useChatStore();
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();
  const [query, setQuery] = React.useState("");
  const [renamingId, setRenamingId] = React.useState<string | null>(null);
  const [renameValue, setRenameValue] = React.useState("");
  const [deleteTarget, setDeleteTarget] = React.useState<{
    id: string;
    title: string;
    hasChildren?: boolean;
  } | null>(null);
  const [avatarFailed, setAvatarFailed] = React.useState(false);
  const [expandedParents, setExpandedParents] = React.useState<Set<string>>(new Set());
  const renameInputRef = React.useRef<HTMLInputElement | null>(null);

  React.useEffect(() => {
    if (sessions.length === 0) {
      fetchSessions().catch(() => null);
    }
  }, [fetchSessions, sessions.length]);

  const filteredSessions = React.useMemo(() => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) return sessions;
    const matched = sessions.filter((session) => {
      const title = (session.title || "新对话").toLowerCase();
      return title.includes(keyword) || session.id.toLowerCase().includes(keyword);
    });
    // 搜索时，如果子分支匹配，自动包含其父会话
    const matchedIds = new Set(matched.map((s) => s.id));
    for (const session of matched) {
      if (session.parentId && !matchedIds.has(session.parentId)) {
        const parent = sessions.find((s) => s.id === session.parentId);
        if (parent) {
          matched.push(parent);
          matchedIds.add(parent.id);
        }
      }
    }
    return matched;
  }, [query, sessions]);

  const { rootSessions, childrenMap } = React.useMemo(() => {
    const roots: typeof filteredSessions = [];
    const children = new Map<string, typeof filteredSessions>();

    for (const session of filteredSessions) {
      if (session.parentId) {
        const list = children.get(session.parentId) || [];
        list.push(session);
        children.set(session.parentId, list);
      } else {
        roots.push(session);
      }
    }

    // 搜索时自动展开有匹配子分支的父会话
    if (query.trim()) {
      for (const [parentId] of children) {
        setExpandedParents((prev) => {
          if (prev.has(parentId)) return prev;
          const next = new Set(prev);
          next.add(parentId);
          return next;
        });
      }
    }

    return { rootSessions: roots, childrenMap: children };
  }, [filteredSessions, query]);

  const toggleExpand = React.useCallback((id: string) => {
    setExpandedParents((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }, []);

  const groupedSessions = React.useMemo(() => {
    const now = new Date();
    const groups = new Map<string, typeof rootSessions>();
    const order: string[] = [];

    const resolveLabel = (value?: string) => {
      const parsed = value ? new Date(value) : now;
      const date = isValid(parsed) ? parsed : now;
      const diff = Math.max(0, differenceInCalendarDays(now, date));
      if (diff === 0) return "今天";
      if (diff <= 7) return "7天内";
      if (diff <= 30) return "30天内";
      return "更早";
    };

    rootSessions.forEach((session) => {
      const label = resolveLabel(session.lastTime);
      if (!groups.has(label)) {
        groups.set(label, []);
        order.push(label);
      }
      groups.get(label)?.push(session);
    });

    return order.map((label) => ({
      label,
      items: groups.get(label) || []
    }));
  }, [rootSessions]);

  React.useEffect(() => {
    if (renamingId) {
      renameInputRef.current?.focus();
      renameInputRef.current?.select();
    }
  }, [renamingId]);

  React.useEffect(() => {
    setAvatarFailed(false);
  }, [user?.avatar, user?.userId]);

  const avatarUrl = user?.avatar?.trim();
  const showAvatar = Boolean(avatarUrl) && !avatarFailed;
  const avatarFallback = (user?.username || user?.userId || "用户").slice(0, 1).toUpperCase();
  const sessionTitleFont =
    "-apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, \"PingFang SC\", \"Hiragino Sans GB\", \"Microsoft YaHei\", \"Helvetica Neue\", Arial, sans-serif";

  const startRename = (id: string, title: string) => {
    setRenamingId(id);
    setRenameValue(title || "新对话");
  };

  const cancelRename = () => {
    setRenamingId(null);
    setRenameValue("");
  };

  const commitRename = async () => {
    if (!renamingId) return;
    const nextTitle = renameValue.trim();
    if (!nextTitle) {
      cancelRename();
      return;
    }
    const currentTitle = sessions.find((session) => session.id === renamingId)?.title || "新对话";
    if (nextTitle === currentTitle) {
      cancelRename();
      return;
    }
    await renameSession(renamingId, nextTitle);
    cancelRename();
  };

  return (
    <>
      <div
        className={cn(
          "fixed inset-0 z-30 bg-slate-900/30 backdrop-blur-sm transition-opacity lg:hidden",
          isOpen ? "opacity-100" : "pointer-events-none opacity-0"
        )}
        onClick={onClose}
      />
      <aside
        className={cn(
          "fixed left-0 top-0 z-40 flex h-screen w-[260px] flex-shrink-0 flex-col bg-[#FAFAFA] p-3 transition-transform lg:static lg:h-screen lg:translate-x-0",
          isOpen ? "translate-x-0" : "-translate-x-full"
        )}
      >
        <div className="border-b border-[#F0F0F0] pb-3">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-gradient-to-br from-[#7C3AED] to-[#A855F7]">
              <Bot className="h-5 w-5 text-white" />
            </div>
            <div style={{ fontFamily: sessionTitleFont }}>
              <p className="text-base font-semibold text-[#1A1A1A]">灵效AI</p>
              <p className="text-xs text-[#999999]">Powered by AI</p>
            </div>
          </div>
        </div>
        <div className="py-3 space-y-4">
          <div className="relative overflow-hidden rounded-2xl border border-[#E6EEF6] bg-gradient-to-br from-[#F5F3FF] via-white to-[#F0F9FF] p-3 shadow-[0_14px_30px_rgba(15,23,42,0.08)]">
            <span
              aria-hidden="true"
              className="absolute -right-10 -top-10 h-24 w-24 rounded-full bg-[#C4B5FD]/40 blur-2xl"
            />
            <span
              aria-hidden="true"
              className="absolute -left-12 -bottom-10 h-28 w-28 rounded-full bg-[#E9D5FF]/40 blur-2xl"
            />
            <div className="relative">
              <div className="flex items-center justify-between px-1">
                <span className="text-[11px] font-semibold text-[#94A3B8]">快速开始</span>
                <span className="rounded-full bg-[#EDE9FE] px-2 py-0.5 text-[10px] font-semibold text-[#7C3AED]">
                  新内容
                </span>
              </div>
              <button
                type="button"
                className="mt-2 flex w-full items-center gap-3 rounded-2xl bg-white/90 px-4 py-3 text-left shadow-[0_10px_20px_rgba(15,23,42,0.08)] transition-all hover:-translate-y-[1px] hover:shadow-[0_16px_30px_rgba(15,23,42,0.12)]"
                onClick={() => {
                  createSession().catch(() => null);
                  navigate("/chat");
                  onClose();
                }}
              >
                <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-gradient-to-br from-[#8B5CF6] to-[#7C3AED] text-white shadow-[0_6px_14px_rgba(124,58,237,0.3)]">
                  <Plus className="h-4 w-4" />
                </span>
                <span className="flex-1">
                  <span className="block text-sm font-semibold text-[#1F2937]">新建对话</span>
                  <span className="block text-xs text-[#94A3B8]">从空白开始</span>
                </span>
              </button>
              {user?.role === "admin" ? (
                <button
                  type="button"
                  className="mt-2 inline-flex items-center gap-2 rounded-full border border-[#EDE9FE] bg-[#F5F3FF] px-3 py-1.5 text-xs font-semibold text-[#7C3AED] transition-colors hover:bg-[#EDE9FE]"
                  onClick={() => {
                    navigate("/admin");
                    onClose();
                  }}
                >
                  <Settings className="h-3.5 w-3.5" />
                  管理后台
                </button>
              ) : null}
            </div>
          </div>
          <div className="rounded-2xl border border-[#E6EEF6] bg-white p-3 shadow-[0_12px_26px_rgba(15,23,42,0.06)]">
            <div className="px-1">
              <span className="text-[11px] font-semibold text-[#94A3B8]">其他功能</span>
            </div>
            <button
              type="button"
              className="mt-2 flex w-full items-center gap-3 rounded-2xl bg-white/90 px-4 py-3 text-left shadow-[0_10px_20px_rgba(15,23,42,0.08)] transition-all hover:-translate-y-[1px] hover:shadow-[0_16px_30px_rgba(15,23,42,0.12)]"
              onClick={() => {
                navigate("/novel");
                onClose();
              }}
            >
              <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-gradient-to-br from-[#F59E0B] to-[#D97706] text-white shadow-[0_6px_14px_rgba(217,119,6,0.3)]">
                <BookOpen className="h-4 w-4" />
              </span>
              <span className="flex-1">
                <span className="block text-sm font-semibold text-[#1F2937]">小说续写</span>
                <span className="block text-xs text-[#94A3B8]">上传小说，AI续写</span>
              </span>
            </button>
            <button
              type="button"
              className="mt-2 flex w-full items-center gap-3 rounded-2xl bg-white/90 px-4 py-3 text-left shadow-[0_10px_20px_rgba(15,23,42,0.08)] transition-all hover:-translate-y-[1px] hover:shadow-[0_16px_30px_rgba(15,23,42,0.12)]"
              onClick={() => {
                navigate("/imitation");
                onClose();
              }}
            >
              <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-gradient-to-br from-[#10B981] to-[#059669] text-white shadow-[0_6px_14px_rgba(16,185,129,0.3)]">
                <FileEdit className="h-4 w-4" />
              </span>
              <span className="flex-1">
                <span className="block text-sm font-semibold text-[#1F2937]">文章仿写</span>
                <span className="block text-xs text-[#94A3B8]">上传文章，AI仿写</span>
              </span>
            </button>
            <button
              type="button"
              className="mt-2 flex w-full items-center gap-3 rounded-2xl bg-white/90 px-4 py-3 text-left shadow-[0_10px_20px_rgba(15,23,42,0.08)] transition-all hover:-translate-y-[1px] hover:shadow-[0_16px_30px_rgba(15,23,42,0.12)]"
              onClick={() => {
                navigate("/script");
                onClose();
              }}
            >
              <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-gradient-to-br from-[#6366F1] to-[#4F46E5] text-white shadow-[0_6px_14px_rgba(99,102,241,0.3)]">
                <Code className="h-4 w-4" />
              </span>
              <span className="flex-1">
                <span className="block text-sm font-semibold text-[#1F2937]">脚本开发</span>
                <span className="block text-xs text-[#94A3B8]">截图标注，生成脚本</span>
              </span>
            </button>
          </div>
          <div className="rounded-2xl border border-[#E6EEF6] bg-white p-3 shadow-[0_12px_26px_rgba(15,23,42,0.06)]">
            <div className="flex items-center justify-between px-1">
              <span className="text-[11px] font-semibold text-[#94A3B8]">搜索对话</span>
              <span className="text-[10px] text-[#CBD5F5]">Ctrl / Cmd + K</span>
            </div>
            <div className="mt-2">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[#9CA3AF]" />
                <input
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder="搜索对话..."
                  className="h-10 w-full rounded-xl border border-[#E5E7EB] bg-[#F8FAFC] pl-9 pr-3 text-sm text-[#1F2937] placeholder:text-[#9CA3AF] focus:border-[#C4B5FD] focus:outline-none transition-colors"
                />
              </div>
            </div>
          </div>
        </div>
        <div className="relative flex-1 min-h-0">
          <div className="h-full overflow-y-auto sidebar-scroll">
            {sessions.length === 0 && (!sessionsLoaded || isLoading) ? (
              <div
                className="flex h-full items-center justify-center text-[#999999]"
                style={{ fontFamily: sessionTitleFont }}
              >
                <Loading label="加载会话中" />
              </div>
            ) : filteredSessions.length === 0 ? (
              <div
                className="flex h-full flex-col items-center justify-center text-[#999999]"
                style={{ fontFamily: sessionTitleFont }}
              >
                <MessageSquare className="h-16 w-16" />
                <p className="mt-2 text-[14px]">暂无对话记录</p>
              </div>
            ) : (
              <div>
                {groupedSessions.map((group, index) => (
                  <div key={group.label} className={cn("flex flex-col", index === 0 ? "mt-0" : "mt-4")}>
                    <p className="mb-1.5 pl-3 text-[12px] font-normal leading-[18px] text-[#999999]">
                      {group.label}
                    </p>
                    {group.items.map((session) => {
                      const childSessions = childrenMap.get(session.id) || [];
                      const hasChildren = childSessions.length > 0;
                      const isExpanded = expandedParents.has(session.id);

                      return (
                        <React.Fragment key={session.id}>
                          <div
                            className={cn(
                              "group my-[1px] flex min-h-[40px] cursor-pointer items-center gap-2 rounded-lg px-3 py-2 text-[14px] leading-[22px] transition-colors duration-200",
                              currentSessionId === session.id
                                ? "bg-[#EDE9FE] text-[#7C3AED]"
                                : "text-[#333333] hover:bg-[#F5F5F5]"
                            )}
                            role="button"
                            tabIndex={0}
                            onClick={() => {
                              if (renamingId === session.id) return;
                              if (renamingId) {
                                cancelRename();
                              }
                              selectSession(session.id).catch(() => null);
                              navigate(`/chat/${session.id}`);
                              onClose();
                            }}
                            onKeyDown={(event) => {
                              if (event.key === "Enter") {
                                selectSession(session.id).catch(() => null);
                                navigate(`/chat/${session.id}`);
                                onClose();
                              }
                            }}
                          >
                            {hasChildren ? (
                              <button
                                type="button"
                                className="flex h-4 w-4 shrink-0 items-center justify-center"
                                onClick={(event) => {
                                  event.stopPropagation();
                                  toggleExpand(session.id);
                                }}
                              >
                                <ChevronRight
                                  className={cn(
                                    "h-3 w-3 transition-transform",
                                    isExpanded && "rotate-90"
                                  )}
                                />
                              </button>
                            ) : (
                              <span className="w-4 shrink-0" />
                            )}
                            {renamingId === session.id ? (
                              <input
                                ref={renameInputRef}
                                value={renameValue}
                                onChange={(event) => setRenameValue(event.target.value)}
                                onClick={(event) => event.stopPropagation()}
                                onKeyDown={(event) => {
                                  if (event.key === "Enter") {
                                    event.preventDefault();
                                    commitRename().catch(() => null);
                                  }
                                  if (event.key === "Escape") {
                                    event.preventDefault();
                                    cancelRename();
                                  }
                                }}
                                onBlur={() => {
                                  commitRename().catch(() => null);
                                }}
                                className="h-6 flex-1 rounded-md border border-[#E5E5E5] bg-white px-2 text-[14px] leading-[22px] text-[#333333] focus:border-[#7C3AED] focus:outline-none"
                              />
                            ) : (
                              <span className="min-w-0 flex-1 truncate font-normal">
                                {session.title || "新对话"}
                              </span>
                            )}
                            <DropdownMenu>
                              <DropdownMenuTrigger asChild>
                                <button
                                  type="button"
                                  className={cn(
                                    "flex h-6 w-6 items-center justify-center rounded text-[#666666] transition-opacity duration-150 hover:bg-[rgba(0,0,0,0.06)]",
                                    currentSessionId === session.id
                                      ? "pointer-events-auto opacity-100 text-[#7C3AED]"
                                      : "pointer-events-none opacity-0 group-hover:pointer-events-auto group-hover:opacity-100"
                                  )}
                                  onClick={(event) => event.stopPropagation()}
                                  aria-label="会话操作"
                                >
                                  <MoreHorizontal className="h-4 w-4" />
                                </button>
                              </DropdownMenuTrigger>
                              <DropdownMenuContent
                                align="start"
                                className="min-w-[120px] rounded-lg border-0 bg-white p-0 py-1 shadow-[0_4px_16px_rgba(0,0,0,0.12)]"
                              >
                                <DropdownMenuItem
                                  onClick={(event) => {
                                    event.stopPropagation();
                                    startRename(session.id, session.title || "新对话");
                                  }}
                                  className="px-4 py-2 text-[14px] text-[#333333] focus:bg-[#F5F5F5] focus:text-[#333333] data-[highlighted]:bg-[#F5F5F5] data-[highlighted]:text-[#333333]"
                                >
                                  <Pencil className="mr-2 h-4 w-4" />
                                  重命名
                                </DropdownMenuItem>
                                <DropdownMenuItem
                                  onClick={(event) => {
                                    event.stopPropagation();
                                    setDeleteTarget({
                                      id: session.id,
                                      title: session.title || "新对话",
                                      hasChildren
                                    });
                                  }}
                                  className="px-4 py-2 text-[14px] text-[#FF4D4F] focus:bg-[#F5F5F5] focus:text-[#FF4D4F] data-[highlighted]:bg-[#F5F5F5] data-[highlighted]:text-[#FF4D4F]"
                                >
                                  <Trash2 className="mr-2 h-4 w-4" />
                                  删除
                                </DropdownMenuItem>
                              </DropdownMenuContent>
                            </DropdownMenu>
                          </div>
                          {isExpanded && childSessions.map((child) => (
                            <div
                              key={child.id}
                              className={cn(
                                "group my-[1px] flex min-h-[36px] cursor-pointer items-center gap-2 rounded-lg pl-10 pr-3 py-1.5 text-[13px] leading-[20px] transition-colors duration-200",
                                currentSessionId === child.id
                                  ? "bg-[#EDE9FE] text-[#7C3AED]"
                                  : "text-[#555] hover:bg-[#F5F5F5]"
                              )}
                              role="button"
                              tabIndex={0}
                              onClick={() => {
                                if (renamingId === child.id) return;
                                if (renamingId) {
                                  cancelRename();
                                }
                                selectSession(child.id).catch(() => null);
                                navigate(`/chat/${child.id}`);
                                onClose();
                              }}
                              onKeyDown={(event) => {
                                if (event.key === "Enter") {
                                  selectSession(child.id).catch(() => null);
                                  navigate(`/chat/${child.id}`);
                                  onClose();
                                }
                              }}
                            >
                              {renamingId === child.id ? (
                                <input
                                  ref={renameInputRef}
                                  value={renameValue}
                                  onChange={(event) => setRenameValue(event.target.value)}
                                  onClick={(event) => event.stopPropagation()}
                                  onKeyDown={(event) => {
                                    if (event.key === "Enter") {
                                      event.preventDefault();
                                      commitRename().catch(() => null);
                                    }
                                    if (event.key === "Escape") {
                                      event.preventDefault();
                                      cancelRename();
                                    }
                                  }}
                                  onBlur={() => {
                                    commitRename().catch(() => null);
                                  }}
                                  className="h-6 flex-1 rounded-md border border-[#E5E5E5] bg-white px-2 text-[13px] leading-[20px] text-[#333333] focus:border-[#7C3AED] focus:outline-none"
                                />
                              ) : (
                                <span className="min-w-0 flex-1 truncate font-normal">
                                  {child.title || "分支"}
                                </span>
                              )}
                              <DropdownMenu>
                                <DropdownMenuTrigger asChild>
                                  <button
                                    type="button"
                                    className={cn(
                                      "flex h-6 w-6 items-center justify-center rounded text-[#666666] transition-opacity duration-150 hover:bg-[rgba(0,0,0,0.06)]",
                                      currentSessionId === child.id
                                        ? "pointer-events-auto opacity-100 text-[#7C3AED]"
                                        : "pointer-events-none opacity-0 group-hover:pointer-events-auto group-hover:opacity-100"
                                    )}
                                    onClick={(event) => event.stopPropagation()}
                                    aria-label="会话操作"
                                  >
                                    <MoreHorizontal className="h-4 w-4" />
                                  </button>
                                </DropdownMenuTrigger>
                                <DropdownMenuContent
                                  align="start"
                                  className="min-w-[120px] rounded-lg border-0 bg-white p-0 py-1 shadow-[0_4px_16px_rgba(0,0,0,0.12)]"
                                >
                                  <DropdownMenuItem
                                    onClick={(event) => {
                                      event.stopPropagation();
                                      startRename(child.id, child.title || "分支");
                                    }}
                                    className="px-4 py-2 text-[14px] text-[#333333] focus:bg-[#F5F5F5] focus:text-[#333333] data-[highlighted]:bg-[#F5F5F5] data-[highlighted]:text-[#333333]"
                                  >
                                    <Pencil className="mr-2 h-4 w-4" />
                                    重命名
                                  </DropdownMenuItem>
                                  <DropdownMenuItem
                                    onClick={(event) => {
                                      event.stopPropagation();
                                      const grandChildren = childrenMap.get(child.id) || [];
                                      setDeleteTarget({
                                        id: child.id,
                                        title: child.title || "分支",
                                        hasChildren: grandChildren.length > 0
                                      });
                                    }}
                                    className="px-4 py-2 text-[14px] text-[#FF4D4F] focus:bg-[#F5F5F5] focus:text-[#FF4D4F] data-[highlighted]:bg-[#F5F5F5] data-[highlighted]:text-[#FF4D4F]"
                                  >
                                    <Trash2 className="mr-2 h-4 w-4" />
                                    删除
                                  </DropdownMenuItem>
                                </DropdownMenuContent>
                              </DropdownMenu>
                            </div>
                          ))}
                        </React.Fragment>
                      );
                    })}
                  </div>
                ))}
              </div>
            )}
          </div>
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-x-0 bottom-0 z-10 h-5 bg-gradient-to-b from-transparent to-[#FAFAFA]"
          />
        </div>
        <div className="mt-auto pt-3">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button
                type="button"
                className="flex w-full items-center gap-2 rounded-lg p-2 text-left transition-colors hover:bg-[#F5F5F5] data-[state=open]:bg-[#EEEEEE]"
                aria-label="用户菜单"
              >
                <div className="flex h-8 w-8 items-center justify-center overflow-hidden rounded-full bg-gradient-to-br from-[#7C3AED] to-[#A855F7] text-white">
                  {showAvatar ? (
                    <img
                      src={avatarUrl}
                      alt={user?.username || user?.userId || "用户"}
                      className="h-full w-full object-cover"
                      onError={() => setAvatarFailed(true)}
                    />
                  ) : (
                    <span className="text-sm font-medium">{avatarFallback}</span>
                  )}
                </div>
                <span className="flex-1 truncate text-sm font-medium text-[#1A1A1A]">
                  {(() => {
                    const fallback = user?.username || user?.userId || "用户";
                    return /^\d+$/.test(fallback) ? "用户" : fallback;
                  })()}
                </span>
                <MoreHorizontal className="h-4 w-4 text-[#999999]" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start" side="top" sideOffset={8} className="w-48">
              <DropdownMenuItem asChild>
                <a
                  href="https://github.com/wxsh-hub/ZKD_SPEED"
                  target="_blank"
                  rel="noreferrer"
                  className="flex items-center"
                >
                  <BookOpen className="mr-2 h-4 w-4" />
                  官方文档
                </a>
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => logout()} className="text-rose-600 focus:text-rose-600">
                <LogOut className="mr-2 h-4 w-4" />
                退出登录
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </aside>
      <AlertDialog open={Boolean(deleteTarget)} onOpenChange={(open) => {
        if (!open) {
          setDeleteTarget(null);
        }
      }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>删除该会话？</AlertDialogTitle>
            <AlertDialogDescription>
              [{deleteTarget?.title || "该会话"}] {deleteTarget?.hasChildren ? "及其所有分支" : ""}将被永久删除，无法恢复。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                if (!deleteTarget) return;
                const target = deleteTarget;
                const isCurrent = currentSessionId === target.id;
                setDeleteTarget(null);
                deleteSession(target.id)
                  .then(() => {
                    if (isCurrent) {
                      navigate("/chat");
                    }
                  })
                  .catch(() => null);
              }}
            >
              删除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
