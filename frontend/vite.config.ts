import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

// Proxy /api -> backend: o navegador fala so com a origem do front (sem CORS) e o
// backend nunca e exposto direto na demo.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/setupTests.ts"],
    globals: false,
  },
});
