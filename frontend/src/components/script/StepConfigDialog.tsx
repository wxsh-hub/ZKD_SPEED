import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { OperationType } from "@/types/script";
import { getOperationLabel } from "@/types/script";

interface Props {
  operationType: OperationType;
  onCancel: () => void;
  onConfirm: (paramsJson: string) => void;
}

export function StepConfigDialog({ operationType, onCancel, onConfirm }: Props) {
  const [count, setCount] = useState(1);
  const [intervalMs, setIntervalMs] = useState(500);
  const [durationMs, setDurationMs] = useState(1000);
  const [timeoutS, setTimeoutS] = useState(10);
  const [seconds, setSeconds] = useState(3);
  const [text, setText] = useState("");
  const [amount, setAmount] = useState(3);

  const handleConfirm = () => {
    let params: Record<string, unknown> = {};

    switch (operationType) {
      case "click":
      case "long_press":
      case "input_text":
        // 坐标已在 pending 中，这里只配置额外参数
        if (operationType === "long_press") {
          params = { duration_ms: durationMs };
        } else if (operationType === "input_text") {
          params = { text };
        }
        break;
      case "area_click":
        params = { count, interval_ms: intervalMs };
        break;
      case "wait_image":
        params = { timeout_s: timeoutS };
        break;
      case "wait_seconds":
        params = { seconds };
        break;
      case "scroll":
        params = { amount };
        break;
    }

    onConfirm(JSON.stringify(params));
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30">
      <div className="w-80 rounded-xl border border-slate-200 bg-white p-5 shadow-lg">
        <h3 className="mb-4 text-sm font-semibold text-slate-900">
          配置: {getOperationLabel(operationType)}
        </h3>

        <div className="space-y-3">
          {operationType === "area_click" && (
            <>
              <div>
                <label className="mb-1 block text-xs text-slate-600">点击次数</label>
                <Input
                  type="number"
                  min={1}
                  value={count}
                  onChange={(e) => setCount(Number(e.target.value))}
                />
              </div>
              <div>
                <label className="mb-1 block text-xs text-slate-600">间隔 (ms)</label>
                <Input
                  type="number"
                  min={0}
                  value={intervalMs}
                  onChange={(e) => setIntervalMs(Number(e.target.value))}
                />
              </div>
            </>
          )}

          {operationType === "long_press" && (
            <div>
              <label className="mb-1 block text-xs text-slate-600">长按时长 (ms)</label>
              <Input
                type="number"
                min={100}
                value={durationMs}
                onChange={(e) => setDurationMs(Number(e.target.value))}
              />
            </div>
          )}

          {operationType === "wait_image" && (
            <div>
              <label className="mb-1 block text-xs text-slate-600">超时时间 (秒)</label>
              <Input
                type="number"
                min={1}
                value={timeoutS}
                onChange={(e) => setTimeoutS(Number(e.target.value))}
              />
            </div>
          )}

          {operationType === "wait_seconds" && (
            <div>
              <label className="mb-1 block text-xs text-slate-600">等待秒数</label>
              <Input
                type="number"
                min={1}
                value={seconds}
                onChange={(e) => setSeconds(Number(e.target.value))}
              />
            </div>
          )}

          {operationType === "input_text" && (
            <div>
              <label className="mb-1 block text-xs text-slate-600">输入内容</label>
              <Input
                value={text}
                onChange={(e) => setText(e.target.value)}
                placeholder="要输入的文本"
              />
            </div>
          )}

          {operationType === "scroll" && (
            <div>
              <label className="mb-1 block text-xs text-slate-600">滚动量 (正=上, 负=下)</label>
              <Input
                type="number"
                value={amount}
                onChange={(e) => setAmount(Number(e.target.value))}
              />
            </div>
          )}

          {(operationType === "click" || operationType === "input_text") && (
            <p className="text-xs text-slate-400">
              {operationType === "click"
                ? "点击坐标已在截图上标注"
                : "请在截图上标注输入位置"}
            </p>
          )}
        </div>

        <div className="mt-5 flex justify-end gap-2">
          <Button variant="outline" size="sm" onClick={onCancel}>
            取消
          </Button>
          <Button
            size="sm"
            onClick={handleConfirm}
            className="bg-emerald-600 hover:bg-emerald-700"
          >
            确认
          </Button>
        </div>
      </div>
    </div>
  );
}
