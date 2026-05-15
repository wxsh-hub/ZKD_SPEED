import { api } from "@/services/api";

export interface NovelUploadResult {
  taskId: string;
  pipelineId: string;
  status: string;
  chunkCount: number;
  message: string;
  originalWordCount: number;
}

export interface NovelContinueParams {
  direction: string;
  wordCount: number;
  conversationId?: string;
  topK?: number;
  modelId?: string;
}

export async function uploadNovel(file: File) {
  const formData = new FormData();
  formData.append("pipelineId", "1001");
  formData.append("file", file);
  return api.post<NovelUploadResult, NovelUploadResult>("/novel/upload", formData, {
    headers: { "Content-Type": "multipart/form-data" }
  });
}

export function continueNovelStream(
  params: NovelContinueParams,
  token: string,
  callbacks: {
    onChunk: (text: string) => void;
    onMeta: (meta: { conversationId: string }) => void;
    onDone: () => void;
    onError: (err: string) => void;
  }
) {
  const controller = new AbortController();

  const run = async () => {
    const res = await fetch("/api/ragent/novel/continue", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: token,
        Accept: "text/event-stream"
      },
      body: JSON.stringify({
        direction: params.direction,
        topK: params.topK ?? 10,
        wordCount: params.wordCount,
        conversationId: params.conversationId ?? "",
        modelId: params.modelId || ""
      }),
      signal: controller.signal
    });

    if (!res.ok) {
      callbacks.onError(`请求失败: ${res.status}`);
      return;
    }

    const reader = res.body!.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    let prevEvent = "";

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop()!;

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed) continue;

        if (trimmed === "data:[DONE]" || trimmed.includes("event:done")) {
          callbacks.onDone();
          return;
        }

        if (trimmed.startsWith("event:error")) {
          callbacks.onError("续写生成失败");
          return;
        }

        if (trimmed === "event:meta") {
          prevEvent = "meta";
          continue;
        }
        if (prevEvent === "meta" && trimmed.startsWith("data:")) {
          try {
            const meta = JSON.parse(trimmed.slice(5));
            callbacks.onMeta(meta);
          } catch {
            // ignore
          }
          prevEvent = "";
          continue;
        }

        if (trimmed.startsWith("data:")) {
          callbacks.onChunk(trimmed.slice(5));
        }
      }
    }

    callbacks.onDone();
  };

  run().catch((err) => {
    if (err.name !== "AbortError") {
      callbacks.onError(err.message || "请求异常");
    }
  });

  return controller;
}
