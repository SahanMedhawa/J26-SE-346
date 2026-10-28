import { CSSProperties } from "react";
const paths = {
  sidebar: "M3 4h18v16H3zM9 4v16M6 8h.01M6 12h.01",
  chart: "M4 3v18h17M8 16v-4M13 16V7M18 16v-7",
  grid: "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z",
  folder: "M3 7V5h6l2 2h10v12H3z",
  user: "M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0ZM4 21v-2a8 8 0 0 1 16 0v2",
  plus: "M12 5v14M5 12h14",
  search: "M16 10a6 6 0 1 1-12 0 6 6 0 0 1 12 0ZM15 15l5 5",
  arrow: "M5 12h14m-5-5 5 5-5 5",
  chevron: "m8 10 4 4 4-4",
  logout: "M10 5H5v14h5M9 12h11m-4-4 4 4-4 4",
  settings: "M12 8a4 4 0 1 1 0 8 4 4 0 0 1 0-8ZM9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1z",
  check: "m5 12 4 4L19 6",
  shield: "m12 3 8 3v6c0 4-4 7-8 9-4-2-8-5-8-9V6zM8 12l3 3 5-6",
  link: "m10 14 4-4M8 15l-1 1a3 3 0 0 1-4-4l4-4a3 3 0 0 1 4 0m2 1 1-1a3 3 0 0 1 4 4l-4 4a3 3 0 0 1-4 0",
  people: "M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM2 21v-2a7 7 0 0 1 14 0v2m0-17a4 4 0 0 1 0 8m3 4a6 6 0 0 1 3 5",
  more: "M5 12h.01M12 12h.01M19 12h.01",
  photo: "M3 3h18v18H3zM3 17l6-6 4 4 3-3 5 5M15 7h.01",
};
export function Icon({ name, className = "", style }: { name: keyof typeof paths; className?: string; style?: CSSProperties }) {
  return <svg className={`ui-icon ${className}`} style={style} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name]}/></svg>;
}
