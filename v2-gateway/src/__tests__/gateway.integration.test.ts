import { test, describe, before, after } from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import express from "express";
import { buildApp } from "../index";
import { resolveConfig } from "../proxy-config";

function listenEphemeral(app: express.Express): Promise<{ server: http.Server; port: number }> {
  return listenOn(app, 0);
}

function listenOn(app: express.Express, port: number): Promise<{ server: http.Server; port: number }> {
  return new Promise((resolve) => {
    const server = app.listen(port, () => {
      const boundPort = (server.address() as { port: number }).port;
      resolve({ server, port: boundPort });
    });
  });
}

describe("v2-gateway integration", () => {
  let backend: { server: http.Server; port: number };
  let recommendation: { server: http.Server; port: number };
  let iam: { server: http.Server; port: number };
  let frontend: { server: http.Server; port: number };
  let gateway: { server: http.Server; port: number };
  let gatewayBase: string;

  before(async () => {
    const backendApp = express();
    backendApp.get("/api/v1/campaigns", (_req, res) => res.json({ from: "backend" }));
    backendApp.get("/api/v1/auth/oauth/authorize", (_req, res) => {
      // Simulates v2-backend's real RedirectView: an absolute Location header built from a
      // same-origin loopback iam.service-url, e.g. http://127.0.0.1:<gatewayPort>/v2iam/...
      // `gateway` is assigned later in this same before() hook, but this handler only runs when
      // a test later makes a request — by then `gateway.port` is set.
      res.redirect(302, `http://127.0.0.1:${gateway.port}/v2iam/api/v1/oauth/authorize?state=abc`);
    });
    backend = await listenEphemeral(backendApp);

    const recApp = express();
    recApp.get("/health", (_req, res) => res.json({ from: "recommendation" }));
    recommendation = await listenEphemeral(recApp);

    const iamApp = express();
    iamApp.get("/api/v1/oauth/authorize", (_req, res) => res.json({ from: "iam" }));
    iam = await listenEphemeral(iamApp);

    const frontendApp = express();
    frontendApp.get("/", (_req, res) => res.send("<html>frontend root</html>"));
    frontendApp.get("/login", (_req, res) => res.send("<html>login page</html>"));
    frontend = await listenEphemeral(frontendApp);

    // Fixed (not 0/ephemeral): rewriteLoopbackLocation needs to know its own listening port
    // ahead of time, exactly like the real deployment (a fixed, configured port) does — an
    // OS-assigned ephemeral port wouldn't be known until after listen() and would defeat the
    // rewrite this test is actually checking.
    const config = resolveConfig(
      {
        GATEWAY_PORT: "34917",
        NODE_ENV: "development",
        V2_FRONTEND_URL: `http://127.0.0.1:${frontend.port}`,
        V2_BACKEND_URL: `http://127.0.0.1:${backend.port}`,
        V2_RECOMMENDATION_URL: `http://127.0.0.1:${recommendation.port}`,
        V2_IAM_URL: `http://127.0.0.1:${iam.port}`,
      },
      "/unused/dist",
    );
    const { app } = buildApp(config);
    gateway = await listenOn(app, config.port);
    gatewayBase = `http://127.0.0.1:${gateway.port}`;
    // The mocked backend redirect above needs the real gateway port baked in — rebuild the
    // backend app now that it's known. (listenEphemeral already captured gateway.port by
    // reference in the closure above, so no further action needed here.)
  });

  after(() => {
    for (const s of [backend, recommendation, iam, frontend, gateway]) s.server.close();
  });

  test("routes /v2api/* to the backend with the prefix stripped", async () => {
    const res = await fetch(`${gatewayBase}/v2api/api/v1/campaigns`);
    assert.equal(res.status, 200);
    assert.deepEqual(await res.json(), { from: "backend" });
  });

  test("routes /v2rec/* to the recommendation engine with the prefix stripped", async () => {
    const res = await fetch(`${gatewayBase}/v2rec/health`);
    assert.equal(res.status, 200);
    assert.deepEqual(await res.json(), { from: "recommendation" });
  });

  test("routes /v2iam/* to the mock IAM with the prefix stripped", async () => {
    const res = await fetch(`${gatewayBase}/v2iam/api/v1/oauth/authorize`);
    assert.equal(res.status, 200);
    assert.deepEqual(await res.json(), { from: "iam" });
  });

  test("falls through to the frontend proxy for an unmatched path", async () => {
    const res = await fetch(`${gatewayBase}/`);
    assert.equal(res.status, 200);
    assert.match(await res.text(), /frontend root/);
  });

  test("a client-side route (e.g. /login) is handed to the frontend, not 404'd by the gateway", async () => {
    const res = await fetch(`${gatewayBase}/login`);
    assert.equal(res.status, 200);
    assert.match(await res.text(), /login page/);
  });

  test("rewrites a backend redirect's loopback Location header to a relative path", async () => {
    const res = await fetch(`${gatewayBase}/v2api/api/v1/auth/oauth/authorize`, {
      redirect: "manual",
    });
    assert.equal(res.status, 302);
    // Must be path-relative (no scheme/host) so the browser resolves it against the real origin
    // it's currently on, not the loopback address the backend built it from.
    assert.equal(res.headers.get("location"), "/v2iam/api/v1/oauth/authorize?state=abc");
  });
});
