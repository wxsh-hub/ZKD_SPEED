import { useEffect, useState } from "react";
import { getSystemSettings, type ModelCandidate } from "@/services/settingsService";

let cached: ModelCandidate[] | null = null;

export function useModelCandidates(): ModelCandidate[] {
  const [models, setModels] = useState<ModelCandidate[]>(cached || []);

  useEffect(() => {
    if (cached) return;
    getSystemSettings()
      .then((settings) => {
        const candidates = settings.ai?.chat?.candidates || [];
        cached = candidates.filter((c) => c.enabled !== false);
        setModels(cached);
      })
      .catch(() => null);
  }, []);

  return models;
}
