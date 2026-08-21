import { defineConfig, mergeConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import tsconfigPaths from "vite-tsconfig-paths";
import viteTestConfig from "./vitest.config";
import { visualizer } from "rollup-plugin-visualizer";

export default mergeConfig(
  viteTestConfig,
  defineConfig({
    plugins: [
      react(),
      tsconfigPaths(),
      tailwindcss(),
      visualizer({
        open: false, // Automatically open report in browser
        filename: "dist/stats.html", // Output file location
        gzipSize: true, // Show gzipped sizes
        brotliSize: true, // Show brotli sizes
        template: "treemap", // Visualization type: 'sunburst', 'treemap', 'network'
      }),
    ],
    clearScreen: false,
    // V2 is the sole app, served at the bare root behind v2-gateway (port 5000).
    base: "/",
    server: {
      port: 4700,
      host: "0.0.0.0",
      strictPort: true,
      allowedHosts: true,
      hmr: {
        // HMR websocket travels through the v2-gateway proxy; only the port needs fixing.
        clientPort: 443,
        protocol: "wss",
      },
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks: {
            // Vendor chunks
            "react-vendor": ["react", "react-dom", "react-router-dom"],
            "redux-vendor": ["@reduxjs/toolkit", "react-redux"],
            "form-vendor": ["react-hook-form", "@hookform/resolvers", "zod"],
            "mapbox-vendor": ["mapbox-gl", "@mapbox/mapbox-gl-draw"],
            "ui-vendor": ["lucide-react", "react-toastify"],
            "tolgee-vendor": ["@tolgee/react", "@tolgee/core"],
          },
          // Optimize chunk size
          chunkFileNames: "assets/js/[name]-[hash].js",
          entryFileNames: "assets/js/[name]-[hash].js",
          assetFileNames: "assets/[ext]/[name]-[hash].[ext]",
        },
      },
      // Optimize chunk size warnings
      chunkSizeWarningLimit: 1000,
      // Enable source maps for production debugging (optional)
      sourcemap: false, // Set to true if you need debugging
    },
  }),
);
