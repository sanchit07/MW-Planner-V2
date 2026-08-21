/**
 * Single-port public ingress for V2. Replaces the legacy V1 Express server's proxy role now that
 * V1 has been removed: V2 is the bare-root app (no more /v2 prefix), and this gateway fronts the
 * V2 backend, recommendation engine, and mock-IAM services — none of which are reachable from
 * outside this VM on their own ports in a real deployment (only this gateway's port is public).
 *
 * Route map:
 *   /v2api/*  -> V2 backend (Spring Boot, :10000), path prefix stripped
 *   /v2rec/*  -> V2 recommendation engine (Spring Boot, :10002), path prefix stripped
 *   /v2iam/*  -> mock IAM (v2-iam-companion, :10001), path prefix stripped
 *   /*        -> V2 frontend: proxied to the Vite dev server in dev, static v2/dist in production
 */
import express, { type Express } from "express";
import { createProxyMiddleware, type Options } from "http-proxy-middleware";
import path from "path";
import fs from "fs";
import { fileURLToPath } from "url";
import { resolveConfig, rewriteLoopbackLocation, type GatewayConfig } from "./proxy-config.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const defaultDistPath = path.resolve(__dirname, "..", "..", "v2", "dist");

type ProxyMiddleware = ReturnType<typeof createProxyMiddleware>;

export interface BuiltApp {
  app: Express;
  frontendProxy: ProxyMiddleware | null;
}

function backendProxy(
  pathPrefix: string,
  target: string,
  gatewayPort: number,
): express.RequestHandler {
  const options: Options = {
    target,
    changeOrigin: true,
    pathFilter: pathPrefix,
    pathRewrite: { [`^${pathPrefix}`]: "" },
    on: {
      proxyRes: (proxyRes) => {
        const location = proxyRes.headers["location"];
        if (typeof location === "string") {
          proxyRes.headers["location"] = rewriteLoopbackLocation(location, gatewayPort);
        }
      },
    },
  };
  return createProxyMiddleware(options) as unknown as express.RequestHandler;
}

/** Builds the Express app for the given config. Pure (no listen/side effects) so it's testable. */
export function buildApp(config: GatewayConfig): BuiltApp {
  const app = express();

  app.use(backendProxy("/v2api", config.backendUrl, config.port));
  app.use(backendProxy("/v2rec", config.recommendationUrl, config.port));
  app.use(backendProxy("/v2iam", config.iamUrl, config.port));

  let frontendProxy: ProxyMiddleware | null = null;

  if (config.isProduction) {
    if (fs.existsSync(config.frontendDistPath)) {
      app.use(express.static(config.frontendDistPath));
      // SPA fallback: any route the static middleware didn't already serve as a file
      // (client-side routes like /login, /campaigns/123) gets the app shell.
      app.use((_req, res) => {
        res.sendFile(path.join(config.frontendDistPath, "index.html"));
      });
    } else {
      console.warn(
        `[v2-gateway] V2 build not found at ${config.frontendDistPath} — frontend will 404`,
      );
    }
  } else {
    frontendProxy = createProxyMiddleware({
      target: config.frontendUrl,
      changeOrigin: true,
      ws: true,
    });
    app.use(frontendProxy);
  }

  return { app, frontendProxy };
}

// Only run the server when this module is the actual entry point — not when a test imports
// buildApp()/resolveConfig()/rewriteLoopbackLocation() directly.
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const config = resolveConfig(process.env, defaultDistPath);
  const { app, frontendProxy } = buildApp(config);

  const server = app.listen(config.port, () => {
    console.log(
      `[v2-gateway] listening on :${config.port} (${config.isProduction ? "production" : "dev"})`,
    );
    console.log(
      `[v2-gateway] -> backend ${config.backendUrl} | recommendation ${config.recommendationUrl} | iam ${config.iamUrl}`,
    );
  });

  // Vite HMR's websocket needs the upgrade explicitly routed to the dev-server proxy.
  server.on("upgrade", (req, socket, head) => {
    if (frontendProxy) {
      (
        frontendProxy as unknown as {
          upgrade: (req: unknown, socket: unknown, head: unknown) => void;
        }
      ).upgrade(req, socket, head);
    }
  });
}
