import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

const isCodexSeatbeltSandbox = process.env.CODEX_SANDBOX === "seatbelt";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3001,
    strictPort: true,
    ...(isCodexSeatbeltSandbox
      ? { watch: { useFsEvents: false, usePolling: true } }
      : {}),
    proxy: {
      "/api/admin": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/admin/, ""),
      },
      "/api/actions": {
        target: "http://127.0.0.1:8081",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/actions/, ""),
      },
      "/api/runtime": {
        target: "http://127.0.0.1:8083",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/runtime/, ""),
      },
      "/api/health": {
        target: "http://127.0.0.1:8084",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/health/, ""),
      },
    },
  },
});
