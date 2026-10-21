import { ReactNode, createContext, useContext, useMemo, useState } from "react";
import { Project } from "../api/client";

export const SelectedProjectContext = createContext<{
  selectedProject: Project | null;
  setSelectedProject: (project: Project) => void;
}>({ selectedProject: null, setSelectedProject: () => undefined });

export function SelectedProjectProvider({ children }: { children: ReactNode }) {
  const [selectedProject, setSelectedProject] = useState<Project | null>(null);
  const value = useMemo(() => ({ selectedProject, setSelectedProject }), [selectedProject]);
  return <SelectedProjectContext.Provider value={value}>{children}</SelectedProjectContext.Provider>;
}

export function useSelectedProject() {
  return useContext(SelectedProjectContext);
}
