import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

const src = new URL("./src", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": src,
    },
  },
  server: {
    port: 5173,
    // Same-origin API in development so the HttpOnly refresh cookie (SameSite=Strict, Path=/api/v1/auth) works.
    proxy: {
      "/api": { target: process.env.VITE_API_PROXY_TARGET ?? "http://localhost:8080", changeOrigin: false },
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    css: false,
    restoreMocks: true,
  },
});
