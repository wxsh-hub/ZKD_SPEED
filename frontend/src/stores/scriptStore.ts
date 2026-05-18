import { create } from "zustand";
import type {
  ScriptProject,
  ScriptScreenshot,
  ScriptStep,
  AnnotationMode,
} from "@/types/script";
import * as scriptService from "@/services/scriptService";

interface ScriptState {
  projects: ScriptProject[];
  currentProject: ScriptProject | null;
  screenshots: ScriptScreenshot[];
  steps: ScriptStep[];
  selectedScreenshotId: string | null;
  annotationMode: AnnotationMode | null;
  isCompiling: boolean;
  compileStatus: string | null;
  isLoading: boolean;

  loadProjects: () => Promise<void>;
  createProject: (name: string) => Promise<string>;
  loadProject: (id: string) => Promise<void>;
  deleteProject: (id: string) => Promise<void>;
  uploadScreenshots: (projectId: string, files: File[]) => Promise<void>;
  deleteScreenshot: (id: string) => Promise<void>;
  selectScreenshot: (id: string | null) => void;
  setAnnotationMode: (mode: AnnotationMode | null) => void;
  addStep: (
    projectId: string,
    operationType: string,
    paramsJson: string,
    screenshotId?: string
  ) => Promise<void>;
  addTemplateStep: (data: {
    screenshotId: string;
    projectId: string;
    x1: number;
    y1: number;
    x2: number;
    y2: number;
    timeoutS?: number;
  }) => Promise<void>;
  updateStep: (id: string, paramsJson: string) => Promise<void>;
  deleteStep: (id: string) => Promise<void>;
  reorderSteps: (orderedIds: string[]) => Promise<void>;
  generateScript: (projectId: string) => Promise<Blob>;
  startCompilation: (projectId: string) => Promise<void>;
  pollCompileStatus: (projectId: string) => Promise<void>;
}

export const useScriptStore = create<ScriptState>((set, get) => ({
  projects: [],
  currentProject: null,
  screenshots: [],
  steps: [],
  selectedScreenshotId: null,
  annotationMode: null,
  isCompiling: false,
  compileStatus: null,
  isLoading: false,

  loadProjects: async () => {
    const projects = await scriptService.listProjects();
    set({ projects });
  },

  createProject: async (name: string) => {
    const project = await scriptService.createProject(name);
    set((s) => ({ projects: [project, ...s.projects] }));
    return project.id;
  },

  loadProject: async (id: string) => {
    set({ isLoading: true });
    try {
      const detail = await scriptService.getProjectDetail(id);
      set({
        currentProject: detail.project,
        screenshots: detail.screenshots,
        steps: detail.steps,
        selectedScreenshotId: detail.screenshots[0]?.id ?? null,
      });
    } finally {
      set({ isLoading: false });
    }
  },

  deleteProject: async (id: string) => {
    await scriptService.deleteProject(id);
    set((s) => ({
      projects: s.projects.filter((p) => p.id !== id),
      currentProject: s.currentProject?.id === id ? null : s.currentProject,
    }));
  },

  uploadScreenshots: async (projectId: string, files: File[]) => {
    const uploaded = await scriptService.uploadScreenshots(projectId, files);
    set((s) => ({
      screenshots: [...s.screenshots, ...uploaded].sort(
        (a, b) => a.sortOrder - b.sortOrder
      ),
    }));
  },

  deleteScreenshot: async (id: string) => {
    await scriptService.deleteScreenshot(id);
    set((s) => ({
      screenshots: s.screenshots.filter((sc) => sc.id !== id),
      selectedScreenshotId:
        s.selectedScreenshotId === id ? null : s.selectedScreenshotId,
    }));
  },

  selectScreenshot: (id: string | null) => {
    set({ selectedScreenshotId: id });
  },

  setAnnotationMode: (mode: AnnotationMode | null) => {
    set({ annotationMode: mode });
  },

  addStep: async (
    projectId: string,
    operationType: string,
    paramsJson: string,
    screenshotId?: string
  ) => {
    const step = await scriptService.createStep(
      projectId,
      operationType,
      paramsJson,
      screenshotId
    );
    set((s) => ({ steps: [...s.steps, step] }));
  },

  addTemplateStep: async (data) => {
    const step = await scriptService.extractTemplate(data);
    set((s) => ({ steps: [...s.steps, step] }));
  },

  updateStep: async (id: string, paramsJson: string) => {
    const step = await scriptService.updateStep(id, { paramsJson });
    set((s) => ({
      steps: s.steps.map((st) => (st.id === id ? step : st)),
    }));
  },

  deleteStep: async (id: string) => {
    await scriptService.deleteStep(id);
    set((s) => ({ steps: s.steps.filter((st) => st.id !== id) }));
  },

  reorderSteps: async (orderedIds: string[]) => {
    await scriptService.reorderSteps(orderedIds);
    const stepMap = new Map(get().steps.map((s) => [s.id, s]));
    const reordered = orderedIds
      .map((id, idx) => {
        const step = stepMap.get(id);
        if (step) return { ...step, stepOrder: idx };
        return null;
      })
      .filter(Boolean) as ScriptStep[];
    set({ steps: reordered });
  },

  generateScript: async (projectId: string) => {
    return scriptService.generateScript(projectId);
  },

  startCompilation: async (projectId: string) => {
    set({ isCompiling: true, compileStatus: "PENDING" });
    await scriptService.startCompilation(projectId);
  },

  pollCompileStatus: async (projectId: string) => {
    const result = await scriptService.getCompileStatus(projectId);
    set({
      compileStatus: result.status,
      isCompiling: result.status === "RUNNING" || result.status === "PENDING",
    });
  },
}));
