import { Suspense, lazy } from "react";
import { Navigate, createBrowserRouter } from "react-router-dom";

import { useAuthStore } from "@/stores/authStore";

const LoginPage = lazy(() => import("@/pages/LoginPage").then((m) => ({ default: m.LoginPage })));
const ChatPage = lazy(() => import("@/pages/ChatPage").then((m) => ({ default: m.ChatPage })));
const NotFoundPage = lazy(() => import("@/pages/NotFoundPage").then((m) => ({ default: m.NotFoundPage })));
const NovelPage = lazy(() => import("@/pages/NovelPage").then((m) => ({ default: m.NovelPage })));
const ImitationPage = lazy(() => import("@/pages/ImitationPage").then((m) => ({ default: m.ImitationPage })));
const ScriptPage = lazy(() => import("@/pages/ScriptPage").then((m) => ({ default: m.ScriptPage })));
const AdminLayout = lazy(() => import("@/pages/admin/AdminLayout").then((m) => ({ default: m.AdminLayout })));
const DashboardPage = lazy(() => import("@/pages/admin/dashboard/DashboardPage").then((m) => ({ default: m.DashboardPage })));
const KnowledgeListPage = lazy(() => import("@/pages/admin/knowledge/KnowledgeListPage").then((m) => ({ default: m.KnowledgeListPage })));
const KnowledgeDocumentsPage = lazy(() => import("@/pages/admin/knowledge/KnowledgeDocumentsPage").then((m) => ({ default: m.KnowledgeDocumentsPage })));
const KnowledgeChunksPage = lazy(() => import("@/pages/admin/knowledge/KnowledgeChunksPage").then((m) => ({ default: m.KnowledgeChunksPage })));
const IntentTreePage = lazy(() => import("@/pages/admin/intent-tree/IntentTreePage").then((m) => ({ default: m.IntentTreePage })));
const IntentListPage = lazy(() => import("@/pages/admin/intent-tree/IntentListPage").then((m) => ({ default: m.IntentListPage })));
const IntentEditPage = lazy(() => import("@/pages/admin/intent-tree/IntentEditPage").then((m) => ({ default: m.IntentEditPage })));
const IngestionPage = lazy(() => import("@/pages/admin/ingestion/IngestionPage").then((m) => ({ default: m.IngestionPage })));
const RagTracePage = lazy(() => import("@/pages/admin/traces/RagTracePage").then((m) => ({ default: m.RagTracePage })));
const RagTraceDetailPage = lazy(() => import("@/pages/admin/traces/RagTraceDetailPage").then((m) => ({ default: m.RagTraceDetailPage })));
const SystemSettingsPage = lazy(() => import("@/pages/admin/settings/SystemSettingsPage").then((m) => ({ default: m.SystemSettingsPage })));
const SampleQuestionPage = lazy(() => import("@/pages/admin/sample-questions/SampleQuestionPage").then((m) => ({ default: m.SampleQuestionPage })));
const UserListPage = lazy(() => import("@/pages/admin/users/UserListPage").then((m) => ({ default: m.UserListPage })));

function PageLoader() {
  return (
    <div className="flex h-screen items-center justify-center">
      <div className="h-6 w-6 animate-spin rounded-full border-2 border-[#E5E7EB] border-t-[#7C3AED]" />
    </div>
  );
}

function LazyPage({ children }: { children: JSX.Element }) {
  return <Suspense fallback={<PageLoader />}>{children}</Suspense>;
}

function RequireAuth({ children }: { children: JSX.Element }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return children;
}

function RequireAdmin({ children }: { children: JSX.Element }) {
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (user?.role !== "admin") {
    return <Navigate to="/chat" replace />;
  }

  return children;
}

function RedirectIfAuth({ children }: { children: JSX.Element }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (isAuthenticated) {
    return <Navigate to="/chat" replace />;
  }
  return children;
}

function HomeRedirect() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  return <Navigate to={isAuthenticated ? "/chat" : "/login"} replace />;
}

export const router = createBrowserRouter([
  {
    path: "/",
    element: <HomeRedirect />
  },
  {
    path: "/login",
    element: (
      <RedirectIfAuth>
        <LazyPage><LoginPage /></LazyPage>
      </RedirectIfAuth>
    )
  },
  {
    path: "/chat",
    element: (
      <RequireAuth>
        <LazyPage><ChatPage /></LazyPage>
      </RequireAuth>
    )
  },
  {
    path: "/chat/:sessionId",
    element: (
      <RequireAuth>
        <LazyPage><ChatPage /></LazyPage>
      </RequireAuth>
    )
  },
  {
    path: "/novel",
    element: (
      <RequireAuth>
        <LazyPage><NovelPage /></LazyPage>
      </RequireAuth>
    )
  },
  {
    path: "/imitation",
    element: (
      <RequireAuth>
        <LazyPage><ImitationPage /></LazyPage>
      </RequireAuth>
    )
  },
  {
    path: "/script",
    element: (
      <RequireAuth>
        <LazyPage><ScriptPage /></LazyPage>
      </RequireAuth>
    )
  },
  {
    path: "/script/:projectId",
    element: (
      <RequireAuth>
        <LazyPage><ScriptPage /></LazyPage>
      </RequireAuth>
    )
  },
  {
    path: "/admin",
    element: (
      <RequireAdmin>
        <LazyPage><AdminLayout /></LazyPage>
      </RequireAdmin>
    ),
    children: [
      {
        index: true,
        element: <Navigate to="/admin/dashboard" replace />
      },
      {
        path: "dashboard",
        element: <LazyPage><DashboardPage /></LazyPage>
      },
      {
        path: "knowledge",
        element: <LazyPage><KnowledgeListPage /></LazyPage>
      },
      {
        path: "knowledge/:kbId",
        element: <LazyPage><KnowledgeDocumentsPage /></LazyPage>
      },
      {
        path: "knowledge/:kbId/docs/:docId",
        element: <LazyPage><KnowledgeChunksPage /></LazyPage>
      },
      {
        path: "intent-tree",
        element: <LazyPage><IntentTreePage /></LazyPage>
      },
      {
        path: "intent-list",
        element: <LazyPage><IntentListPage /></LazyPage>
      },
      {
        path: "intent-list/:id/edit",
        element: <LazyPage><IntentEditPage /></LazyPage>
      },
      {
        path: "ingestion",
        element: <LazyPage><IngestionPage /></LazyPage>
      },
      {
        path: "traces",
        element: <LazyPage><RagTracePage /></LazyPage>
      },
      {
        path: "traces/:traceId",
        element: <LazyPage><RagTraceDetailPage /></LazyPage>
      },
      {
        path: "settings",
        element: <LazyPage><SystemSettingsPage /></LazyPage>
      },
      {
        path: "sample-questions",
        element: <LazyPage><SampleQuestionPage /></LazyPage>
      },
      {
        path: "users",
        element: <LazyPage><UserListPage /></LazyPage>
      }
    ]
  },
  {
    path: "*",
    element: <LazyPage><NotFoundPage /></LazyPage>
  }
]);
