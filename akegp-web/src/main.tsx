import "./shared/auth/browserAuth";
import { AuthProvider } from "./shared/auth/AuthProvider";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { App } from "./app/App";
import "./global.css";
createRoot(document.getElementById("root")!).render(<StrictMode><BrowserRouter><AuthProvider><App /></AuthProvider></BrowserRouter></StrictMode>);
