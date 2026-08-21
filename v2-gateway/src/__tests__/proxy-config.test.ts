import { test, describe } from "node:test";
import assert from "node:assert/strict";
import { rewriteLoopbackLocation, resolveConfig } from "../proxy-config";

describe("rewriteLoopbackLocation", () => {
  test("strips a 127.0.0.1:<port> prefix, leaving a path-relative redirect", () => {
    assert.equal(
      rewriteLoopbackLocation("http://127.0.0.1:5000/v2iam/api/v1/oauth/authorize?x=1", 5000),
      "/v2iam/api/v1/oauth/authorize?x=1",
    );
  });

  test("strips a localhost:<port> prefix case-insensitively", () => {
    assert.equal(
      rewriteLoopbackLocation("HTTP://LOCALHOST:5000/auth/oauth/callback", 5000),
      "/auth/oauth/callback",
    );
  });

  test("leaves an unrelated absolute URL untouched", () => {
    assert.equal(
      rewriteLoopbackLocation("https://real-idp.example.com/authorize", 5000),
      "https://real-idp.example.com/authorize",
    );
  });

  test("leaves a loopback URL on a DIFFERENT port untouched", () => {
    assert.equal(
      rewriteLoopbackLocation("http://127.0.0.1:10001/api/v1/oauth/authorize", 5000),
      "http://127.0.0.1:10001/api/v1/oauth/authorize",
    );
  });

  test("leaves an already-relative path untouched", () => {
    assert.equal(rewriteLoopbackLocation("/login", 5000), "/login");
  });
});

describe("resolveConfig", () => {
  test("falls back to documented defaults when nothing is set", () => {
    const config = resolveConfig({}, "/default/dist");
    assert.equal(config.port, 5000);
    assert.equal(config.isProduction, false);
    assert.equal(config.frontendUrl, "http://127.0.0.1:4700");
    assert.equal(config.frontendDistPath, "/default/dist");
    assert.equal(config.backendUrl, "http://127.0.0.1:10000");
    assert.equal(config.recommendationUrl, "http://127.0.0.1:10002");
    assert.equal(config.iamUrl, "http://127.0.0.1:10001");
  });

  test("GATEWAY_PORT takes precedence over PORT", () => {
    const config = resolveConfig({ GATEWAY_PORT: "5555", PORT: "9999" }, "/dist");
    assert.equal(config.port, 5555);
  });

  test("falls back to PORT when GATEWAY_PORT is unset", () => {
    const config = resolveConfig({ PORT: "9999" }, "/dist");
    assert.equal(config.port, 9999);
  });

  test("isProduction is true only for NODE_ENV=production", () => {
    assert.equal(resolveConfig({ NODE_ENV: "production" }, "/dist").isProduction, true);
    assert.equal(resolveConfig({ NODE_ENV: "development" }, "/dist").isProduction, false);
  });

  test("every target URL is independently overridable", () => {
    const config = resolveConfig(
      {
        V2_FRONTEND_URL: "http://127.0.0.1:1111",
        V2_BACKEND_URL: "http://127.0.0.1:2222",
        V2_RECOMMENDATION_URL: "http://127.0.0.1:3333",
        V2_IAM_URL: "http://127.0.0.1:4444",
        V2_DIST_PATH: "/custom/dist",
      },
      "/default/dist",
    );
    assert.equal(config.frontendUrl, "http://127.0.0.1:1111");
    assert.equal(config.backendUrl, "http://127.0.0.1:2222");
    assert.equal(config.recommendationUrl, "http://127.0.0.1:3333");
    assert.equal(config.iamUrl, "http://127.0.0.1:4444");
    assert.equal(config.frontendDistPath, "/custom/dist");
  });
});
