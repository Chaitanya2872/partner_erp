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
  build: {
    rolldownOptions: {
      output: {
        // Stable chunks for the core libraries every route needs; they change rarely, so browsers keep them cached across
        // releases. UI primitives (Radix) are left to automatic splitting so each page loads only the ones it uses.
        codeSplitting: {
          groups: [
            { name: "vendor-react", test: /node_modules[\\/](react|react-dom|scheduler)[\\/]/ },
            { name: "vendor-router", test: /node_modules[\\/]react-router[\\/]/ },
            { name: "vendor-query", test: /node_modules[\\/]@tanstack[\\/]/ },
            { name: "vendor-forms", test: /node_modules[\\/](zod|react-hook-form|@hookform)[\\/]/ },
          ],
        },
      },
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
    testTimeout: 20_000,
  },
});
