import * as React from "react";
import { useNavigate, useParams } from "react-router-dom";

import { ChatInput } from "@/components/chat/ChatInput";
import { MessageList } from "@/components/chat/MessageList";
import { MainLayout } from "@/components/layout/MainLayout";
import { useChatStore } from "@/stores/chatStore";

export function ChatPage() {
  const navigate = useNavigate();
  const { sessionId } = useParams<{ sessionId: string }>();
  const {
    messages,
    isLoading,
    isStreaming,
    currentSessionId,
    sessions,
    isCreatingNew,
    isForking,
    fetchSessions,
    selectSession,
    createSession
  } = useChatStore();
  const showWelcome = messages.length === 0 && !isLoading;
  const [sessionsReady, setSessionsReady] = React.useState(false);
  const sessionExists = React.useMemo(() => {
    if (!sessionId) return false;
    return sessions.some((session) => session.id === sessionId);
  }, [sessionId, sessions]);

  // 当 currentSessionId 变化且与 URL 不同步时，更新 URL
  const lastNavigatedRef = React.useRef<string | null>(null);

  React.useEffect(() => {
    let active = true;
    fetchSessions()
      .catch(() => null)
      .finally(() => {
        if (active) {
          setSessionsReady(true);
        }
      });
    return () => {
      active = false;
    };
  }, [fetchSessions]);

  React.useEffect(() => {
    if (isForking) return;
    if (sessionId) {
      if (sessionsReady && !sessionExists) {
        // 会话不存在时，检查是否是当前会话（可能是刚创建的分支）
        if (currentSessionId === sessionId) {
          // 是当前会话，不需要重新创建
          return;
        }
        createSession().catch(() => null);
        navigate("/chat", { replace: true });
        return;
      }
      // 只有当 sessionId 确实变化了才调用 selectSession
      if (currentSessionId !== sessionId) {
        selectSession(sessionId).catch(() => null);
      }
      return;
    }
    if (!sessionsReady) {
      return;
    }
    if (isCreatingNew) {
      return;
    }
    if (currentSessionId) {
      return;
    }
    createSession().catch(() => null);
  }, [
    sessionId,
    sessionsReady,
    sessionExists,
    isCreatingNew,
    isForking,
    currentSessionId,
    selectSession,
    createSession,
    navigate
  ]);

  React.useEffect(() => {
    // 只有当 currentSessionId 确实变化了，且不是我们刚刚导航过的，才更新 URL
    if (currentSessionId && currentSessionId !== sessionId && lastNavigatedRef.current !== currentSessionId) {
      lastNavigatedRef.current = currentSessionId;
      navigate(`/chat/${currentSessionId}`, { replace: true });
    }
  }, [currentSessionId, sessionId, navigate]);

  return (
    <MainLayout>
      <div className="flex h-full flex-col bg-white">
        <div className="flex-1 min-h-0">
          <MessageList
            messages={messages}
            isLoading={isLoading}
            isStreaming={isStreaming}
            sessionKey={currentSessionId}
          />
        </div>
        {showWelcome ? null : (
          <div className="relative z-20 bg-white">
            <div className="mx-auto max-w-[800px] px-6 pt-1 pb-3">
              <ChatInput />
            </div>
          </div>
        )}
      </div>
    </MainLayout>
  );
}
