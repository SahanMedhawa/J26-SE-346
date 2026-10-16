import { createContext, useContext } from "react";
import { Project } from "../../shared/api/client";

export const SelectedProjectContext = createContext<{
  selectedProject: Project | null;
  setSelectedProject: (project: Project) => void;
}>({ selectedProject: null, setSelectedProject: () => undefined });

export function useSelectedProject() {
  return useContext(SelectedProjectContext);
}
