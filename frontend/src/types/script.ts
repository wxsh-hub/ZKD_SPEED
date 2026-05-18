export type OperationType =
  | 'click'
  | 'area_click'
  | 'long_press'
  | 'wait_image'
  | 'wait_seconds'
  | 'input_text'
  | 'scroll';

export interface ClickParams {
  x: number;
  y: number;
}

export interface AreaClickParams {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  count: number;
  interval_ms: number;
}

export interface LongPressParams {
  x: number;
  y: number;
  duration_ms: number;
}

export interface WaitImageParams {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  timeout_s: number;
}

export interface WaitSecondsParams {
  seconds: number;
}

export interface InputTextParams {
  x: number;
  y: number;
  text: string;
}

export interface ScrollParams {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  amount: number;
}

export type StepParams =
  | ClickParams
  | AreaClickParams
  | LongPressParams
  | WaitImageParams
  | WaitSecondsParams
  | InputTextParams
  | ScrollParams;

export interface ScriptProject {
  id: string;
  name: string;
  description?: string;
  targetWidth?: number;
  targetHeight?: number;
  status: string;
  exePath?: string;
}

export interface ScriptScreenshot {
  id: string;
  projectId: string;
  fileName: string;
  fileUrl: string;
  width: number;
  height: number;
  scalePct: number;
  sortOrder: number;
}

export interface ScriptStep {
  id: string;
  projectId: string;
  screenshotId?: string;
  stepOrder: number;
  operationType: OperationType;
  paramsJson: string;
  templateUrl?: string;
}

export interface AnnotationMode {
  type: OperationType;
  label: string;
  icon: string;
  drawMode: 'point' | 'rect' | 'none';
}

export const ANNOTATION_MODES: AnnotationMode[] = [
  { type: 'click', label: '点击', icon: 'MousePointerClick', drawMode: 'point' },
  { type: 'area_click', label: '区域点击', icon: 'Square', drawMode: 'rect' },
  { type: 'long_press', label: '长按', icon: 'Hand', drawMode: 'point' },
  { type: 'wait_image', label: '等待图像', icon: 'Scan', drawMode: 'rect' },
  { type: 'wait_seconds', label: '等待时间', icon: 'Clock', drawMode: 'none' },
  { type: 'input_text', label: '输入文本', icon: 'Type', drawMode: 'point' },
  { type: 'scroll', label: '滚动', icon: 'ScrollText', drawMode: 'rect' },
];

export function getOperationLabel(type: OperationType): string {
  return ANNOTATION_MODES.find((m) => m.type === type)?.label ?? type;
}

export function getStepSummary(step: ScriptStep): string {
  const params = JSON.parse(step.paramsJson);
  switch (step.operationType) {
    case 'click':
      return `点击 (${params.x}, ${params.y})`;
    case 'area_click':
      return `区域点击 (${params.x1},${params.y1})-(${params.x2},${params.y2}) ×${params.count}`;
    case 'long_press':
      return `长按 (${params.x}, ${params.y}) ${params.duration_ms}ms`;
    case 'wait_image':
      return `等待图像 (${params.x1},${params.y1})-(${params.x2},${params.y2}) ${params.timeout_s}s`;
    case 'wait_seconds':
      return `等待 ${params.seconds} 秒`;
    case 'input_text':
      return `输入 "${params.text}"`;
    case 'scroll':
      return `滚动 (${params.x1},${params.y1})-(${params.x2},${params.y2}) ${params.amount}格`;
    default:
      return step.operationType;
  }
}
