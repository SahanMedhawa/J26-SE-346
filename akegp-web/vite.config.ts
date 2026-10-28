import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
	plugins: [react()],
	server: {
		proxy: {
			"/api": process.env.BACKEND_URL || "http://localhost:8080",
            "/oauth2": process.env.BACKEND_URL || "http://localhost:8080",
            "/login/oauth2": process.env.BACKEND_URL || "http://localhost:8080",
		},
	},
});
