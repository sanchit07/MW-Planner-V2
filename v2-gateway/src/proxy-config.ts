/**
 * Pure helpers for the gateway's proxy behavior — kept separate from index.ts (which wires up
 * Express/http-proxy-middleware) so they're testable without spinning up real servers.
 */

/**
 * Strips this gateway's own loopback origin (127.0.0.1:<port> or localhost:<port>) from the
 * front of a Location header, turning an absolute self-referential redirect into a path-relative
 * one the browser resolves against whatever origin it's actually on.
 *
 * Why this matters: v2-backend builds its OAuth "authorize" redirect (and v2-iam-companion its
 * own callback redirect) from a same-machine loopback URL like `http://127.0.0.1:5000/v2iam/...`
 * — correct for server-to-server calls on the same box, but if sent to a real visitor's browser
 * verbatim, the browser would try to reach ITS OWN machine's port 5000, not this VM's. Rewriting
 * it to a bare path makes the browser re-request the SAME origin it's already on, which the
 * gateway then routes correctly.
 */
export function rewriteLoopbackLocation(location: string, gatewayPort: number): string {
  return location.replace(
    new RegExp(`^https?://(127\\.0\\.0\\.1|localhost):${gatewayPort}`, "i"),
    "",
  );
}

export interface GatewayConfig {
  port: number;
  isProduction: boolean;
  frontendUrl: string;
  frontendDistPath: string;
  backendUrl: string;
  recommendationUrl: string;
  iamUrl: string;
}

export function resolveConfig(env: NodeJS.ProcessEnv, distPathDefault: string): GatewayConfig {
  return {
    port: Number(env.GATEWAY_PORT ?? env.PORT ?? 5000),
    isProduction: env.NODE_ENV === "production",
    frontendUrl: env.V2_FRONTEND_URL ?? "http://127.0.0.1:4700",
    frontendDistPath: env.V2_DIST_PATH ?? distPathDefault,
    backendUrl: env.V2_BACKEND_URL ?? "http://127.0.0.1:10000",
    recommendationUrl: env.V2_RECOMMENDATION_URL ?? "http://127.0.0.1:10002",
    iamUrl: env.V2_IAM_URL ?? "http://127.0.0.1:10001",
  };
}
