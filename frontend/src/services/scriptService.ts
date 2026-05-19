import { api } from "@/services/api";

// ==================== Types ====================

export interface ScriptProject {
  id: string;
  name: string;
  description: string;
  targetWidth: number | null;
  targetHeight: number | null;
  scalePct: number | null;
  status: "draft" | "building" | "success" | "failed";
  exePath: string | null;
  guiEnabled: number;
  uploadToken: string | null;
  screenshotCount: number;
  stepCount: number;
  createTime: string;
  updateTime: string;
}

export interface ScriptScreenshot {
  id: string;
  projectId: string;
  fileName: string;
  filePath: string;
  fileUrl: string;
  width: number;
  height: number;
  scalePct: number;
  timestampStr: string | null;
  sortOrder: number;
  createTime: string;
}

export interface ScriptStep {
  id: string;
  projectId: string;
  screenshotId: string | null;
  stepOrder: number;
  operationType: OperationType;
  paramsJson: Record<string, unknown>;
  templatePath: string | null;
  templateUrl: string | null;
  createTime: string;
}

export type OperationType =
  | "click"
  | "double_click"
  | "area_click"
  | "long_press"
  | "area_long_press"
  | "mouse_move"
  | "key_press"
  | "key_long_press"
  | "wait_seconds"
  | "input_text"
  | "scroll"
  | "for_start"
  | "for_end"
  | "break_loop"
  | "continue_loop"
  | "if_image"
  | "if_ai"
  | "if_random"
  | "else"
  | "if_end";

export const OPERATION_LABELS: Record<OperationType, string> = {
  click: "点击",
  double_click: "双击",
  area_click: "区域点击",
  long_press: "长按",
  area_long_press: "区域长按",
  mouse_move: "移动鼠标",
  key_press: "键盘点击",
  key_long_press: "键盘长按",
  wait_seconds: "等待秒数",
  input_text: "输入文字",
  scroll: "滑动",
  for_start: "循环开始",
  for_end: "循环结束",
  break_loop: "跳出循环",
  continue_loop: "继续循环",
  if_image: "条件(图像)",
  if_ai: "条件(AI识别)",
  if_random: "条件(随机)",
  else: "否则",
  if_end: "条件结束",
};

export const OPERATION_COLORS: Record<OperationType, string> = {
  click: "bg-blue-100 text-blue-700 border-blue-200",
  double_click: "bg-sky-100 text-sky-700 border-sky-200",
  area_click: "bg-indigo-100 text-indigo-700 border-indigo-200",
  long_press: "bg-purple-100 text-purple-700 border-purple-200",
  area_long_press: "bg-fuchsia-100 text-fuchsia-700 border-fuchsia-200",
  mouse_move: "bg-lime-100 text-lime-700 border-lime-200",
  key_press: "bg-cyan-100 text-cyan-700 border-cyan-200",
  key_long_press: "bg-violet-100 text-violet-700 border-violet-200",
  wait_seconds: "bg-orange-100 text-orange-700 border-orange-200",
  input_text: "bg-emerald-100 text-emerald-700 border-emerald-200",
  scroll: "bg-teal-100 text-teal-700 border-teal-200",
  for_start: "bg-yellow-100 text-yellow-700 border-yellow-200",
  for_end: "bg-yellow-100 text-yellow-700 border-yellow-200",
  break_loop: "bg-yellow-100 text-yellow-800 border-yellow-300",
  continue_loop: "bg-yellow-100 text-yellow-800 border-yellow-300",
  if_image: "bg-rose-100 text-rose-700 border-rose-200",
  if_ai: "bg-violet-100 text-violet-700 border-violet-200",
  if_random: "bg-pink-100 text-pink-700 border-pink-200",
  else: "bg-amber-100 text-amber-700 border-amber-200",
  if_end: "bg-rose-100 text-rose-700 border-rose-200",
};

export const OPERATION_HINTS: Record<OperationType, string> = {
  click: "在图片上单击选择位置",
  double_click: "在图片上单击选择位置",
  area_click: "在图片上拖拽画出随机区域",
  long_press: "在图片上单击选择位置",
  area_long_press: "在图片上拖拽画出随机区域",
  mouse_move: "在图片上单击选择目标位置",
  key_press: "输入按键名如 a、enter、ctrl+a",
  key_long_press: "输入按键名和长按时长",
  wait_seconds: "输入等待的秒数",
  input_text: "输入要打字的内容",
  scroll: "在图片上拖拽画出滑动路径",
  for_start: "重复执行内部操作，设置循环次数。例如循环10次点击按钮",
  for_end: "标记循环体的结束位置，与「循环开始」配对使用",
  break_loop: "立即跳出当前循环，继续执行循环之后的操作。只能放在循环开始和循环结束之间",
  continue_loop: "跳过本次循环剩余操作，直接开始下一次循环。只能放在循环开始和循环结束之间",
  if_image: "截取屏幕上一块区域作为模板，运行时判断屏幕上是否能找到匹配的图像",
  if_ai: "截取屏幕区域，用 AI 判断是否满足条件。例如「屏幕上是否有登录按钮」。需要服务器在线",
  if_random: "按设定的概率随机决定是否执行，用于模拟人类操作的随机性。例如50%概率点击",
  else: "当上方的条件（图像匹配/随机）不满足时执行这里的操作",
  if_end: "标记条件块的结束位置，与「条件(图像)」或「条件(随机)」配对使用",
};

export interface KeyOption {
  value: string;
  label: string;
  group: string;
}

export const KEY_OPTIONS: KeyOption[] = [
  // 字母
  ..."abcdefghijklmnopqrstuvwxyz".split("").map(c => ({ value: c, label: c.toUpperCase(), group: "字母" })),
  // 数字
  ..."0123456789".split("").map(c => ({ value: c, label: c, group: "数字" })),
  // 功能键
  ...Array.from({ length: 12 }, (_, i) => ({ value: `f${i + 1}`, label: `F${i + 1}`, group: "功能键" })),
  // 修饰键
  { value: "ctrl", label: "Ctrl", group: "修饰键" },
  { value: "shift", label: "Shift", group: "修饰键" },
  { value: "alt", label: "Alt", group: "修饰键" },
  { value: "win", label: "Win", group: "修饰键" },
  // 常用键
  { value: "enter", label: "Enter (回车)", group: "常用键" },
  { value: "space", label: "Space (空格)", group: "常用键" },
  { value: "tab", label: "Tab", group: "常用键" },
  { value: "escape", label: "Escape (Esc)", group: "常用键" },
  { value: "backspace", label: "Backspace (退格)", group: "常用键" },
  { value: "delete", label: "Delete (删除)", group: "常用键" },
  { value: "insert", label: "Insert", group: "常用键" },
  // 方向键
  { value: "up", label: "↑ 上", group: "方向键" },
  { value: "down", label: "↓ 下", group: "方向键" },
  { value: "left", label: "← 左", group: "方向键" },
  { value: "right", label: "→ 右", group: "方向键" },
  // 导航键
  { value: "home", label: "Home", group: "导航键" },
  { value: "end", label: "End", group: "导航键" },
  { value: "pageup", label: "Page Up", group: "导航键" },
  { value: "pagedown", label: "Page Down", group: "导航键" },
  // 锁定键
  { value: "capslock", label: "Caps Lock", group: "锁定键" },
  { value: "numlock", label: "Num Lock", group: "锁定键" },
  { value: "scrolllock", label: "Scroll Lock", group: "锁定键" },
  { value: "printscreen", label: "Print Screen", group: "锁定键" },
  { value: "pause", label: "Pause", group: "锁定键" },
  // 符号键
  { value: "+", label: "+ (加号)", group: "符号键" },
  { value: ",", label: ", (逗号)", group: "符号键" },
  { value: "-", label: "- (减号)", group: "符号键" },
  { value: ".", label: ". (句号)", group: "符号键" },
  { value: "/", label: "/ (斜杠)", group: "符号键" },
  { value: "`", label: "` (反引号)", group: "符号键" },
  { value: "[", label: "[ (左方括号)", group: "符号键" },
  { value: "\\", label: "\\ (反斜杠)", group: "符号键" },
  { value: "]", label: "] (右方括号)", group: "符号键" },
  { value: "'", label: "' (单引号)", group: "符号键" },
];

export interface ScriptProjectDetail extends ScriptProject {
  screenshots: ScriptScreenshot[];
  steps: ScriptStep[];
}

export interface CreateProjectRequest {
  name: string;
  description?: string;
  targetWidth?: number;
  targetHeight?: number;
}

export interface BuildStatus {
  status: string;
  progress: number;
  message?: string;
  downloadUrl?: string;
}

// ==================== API ====================

export async function getProjectList(): Promise<ScriptProject[]> {
  return api.get("/script/list");
}

export async function getProjectDetail(projectId: string): Promise<ScriptProjectDetail> {
  return api.get(`/script/${projectId}`);
}

export async function createProject(data: CreateProjectRequest): Promise<ScriptProject> {
  return api.post("/script/create", data);
}

export async function deleteProject(projectId: string): Promise<void> {
  return api.delete(`/script/${projectId}`);
}

export async function saveProject(
  projectId: string,
  data: {
    screenshots?: Array<{ id?: string; fileName: string; fileUrl: string; width: number; height: number; sortOrder: number }>;
    steps?: Array<{ id?: string; screenshotId?: string; stepOrder: number; operationType: OperationType; paramsJson: Record<string, unknown> }>;
    guiEnabled?: number;
  }
): Promise<void> {
  return api.post(`/script/${projectId}/save`, data);
}

export async function uploadScreenshot(
  projectId: string,
  file: File
): Promise<ScriptScreenshot> {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("projectId", projectId);
  return api.post(`/script/${projectId}/screenshot/upload`, formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
}

export async function uploadScreenshots(
  projectId: string,
  files: File[]
): Promise<ScriptScreenshot[]> {
  const formData = new FormData();
  files.forEach((file) => formData.append("files", file));
  return api.post(`/script/${projectId}/screenshot/batch-upload`, formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
}

export async function previewScript(projectId: string): Promise<string> {
  return api.get(`/script/${projectId}/preview`);
}

export async function buildExe(projectId: string, mode: "bat" | "github" = "bat"): Promise<{ taskId?: string; status: string; downloadUrl?: string }> {
  return api.post(`/script/${projectId}/build`, { mode });
}

export async function getBuildStatus(projectId: string): Promise<BuildStatus> {
  return api.get(`/script/${projectId}/build/status`);
}

export async function uploadTemplate(
  projectId: string,
  file: File
): Promise<string> {
  const formData = new FormData();
  formData.append("file", file);
  return api.post(`/script/${projectId}/template/upload`, formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
}

export async function exportProject(projectId: string): Promise<void> {
  // 使用 fetch 直接下载文件，绕过 axios 拦截器（导出接口不走 Result<T> 包装）
  const token = localStorage.getItem("ragent_token") || "";
  const base = (import.meta as any).env?.VITE_API_BASE_URL || "";
  const res = await fetch(`${base}/script/${projectId}/export`, {
    headers: { Authorization: token },
  });
  if (!res.ok) throw new Error("导出失败");
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `script-project-${projectId}.json`;
  a.click();
  URL.revokeObjectURL(url);
}

export async function importProject(data: Record<string, unknown>): Promise<ScriptProject> {
  return api.post("/script/import", data);
}

export async function aiGenerateScript(prompt: string, name?: string): Promise<ScriptProject> {
  return api.post("/script/ai-generate", { prompt, name });
}
