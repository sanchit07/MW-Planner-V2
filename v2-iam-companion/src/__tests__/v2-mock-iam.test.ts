import { test, describe } from "node:test";
import assert from "node:assert/strict";
import crypto from "crypto";
import {
  signJwt,
  verifyJwt,
  publicJwk,
  V2_IAM_BASE,
  issueAuthCode,
  consumeAuthCode,
} from "../v2-mock-iam";

function s256Challenge(verifier: string): string {
  return crypto.createHash("sha256").update(verifier).digest("base64url");
}

describe("signJwt / verifyJwt", () => {
  test("round-trips claims and sets iss/iat/nbf/exp", () => {
    const token = signJwt({ sub: "user-1", email: "a@b.com" }, 3600);
    const payload = verifyJwt(token);
    assert.ok(payload, "expected a valid payload");
    assert.equal(payload!.sub, "user-1");
    assert.equal(payload!.email, "a@b.com");
    assert.equal(payload!.iss, V2_IAM_BASE);
    assert.equal(typeof payload!.iat, "number");
    assert.equal(typeof payload!.nbf, "number");
    assert.equal(payload!.exp, payload!.iat + 3600);
  });

  test("rejects an expired token", () => {
    const token = signJwt({ sub: "user-1" }, -10); // already expired
    assert.equal(verifyJwt(token), null);
  });

  test("rejects a tampered payload (signature no longer matches)", () => {
    const token = signJwt({ sub: "user-1" }, 3600);
    const [header, payload, signature] = token.split(".");
    const tamperedPayload = Buffer.from(
      JSON.stringify({ ...JSON.parse(Buffer.from(payload, "base64url").toString()), sub: "attacker" }),
    ).toString("base64url");
    const tampered = `${header}.${tamperedPayload}.${signature}`;
    assert.equal(verifyJwt(tampered), null);
  });

  test("rejects a malformed token (wrong number of segments)", () => {
    assert.equal(verifyJwt("not.a.valid.jwt.token"), null);
    assert.equal(verifyJwt("onlyonepart"), null);
  });

  test("the JWKS entry's public key actually verifies signJwt's output", () => {
    // Exercises the same path v2-backend's NimbusJwtDecoder takes: import the
    // published JWK and verify a real token against it, independent of verifyJwt().
    const jwkPublicKey = crypto.createPublicKey({
      key: publicJwk,
      format: "jwk",
    } as unknown as crypto.JsonWebKeyInput);
    const token = signJwt({ sub: "user-2" }, 3600);
    const [header, payload, signature] = token.split(".");
    const ok = crypto.verify(
      "RSA-SHA256",
      Buffer.from(`${header}.${payload}`),
      jwkPublicKey,
      Buffer.from(signature, "base64url"),
    );
    assert.ok(ok, "JWKS public key must verify tokens signJwt issues");
  });
});

describe("PKCE-protected auth codes", () => {
  test("redeems successfully when code_verifier matches the stored code_challenge", () => {
    const verifier = "verifier-123";
    const code = issueAuthCode("user-1", "http://localhost/callback", s256Challenge(verifier));
    assert.equal(consumeAuthCode(code, verifier), "user-1");
  });

  test("rejects redemption when code_verifier does not match", () => {
    const verifier = "verifier-123";
    const code = issueAuthCode("user-1", "http://localhost/callback", s256Challenge(verifier));
    assert.equal(consumeAuthCode(code, "wrong-verifier"), undefined);
  });

  test("rejects redemption when no code_verifier is supplied but a challenge was stored", () => {
    const code = issueAuthCode(
      "user-1",
      "http://localhost/callback",
      s256Challenge("verifier-123"),
    );
    assert.equal(consumeAuthCode(code), undefined);
  });

  test("a code is single-use even with the correct verifier", () => {
    const verifier = "verifier-123";
    const code = issueAuthCode("user-1", "http://localhost/callback", s256Challenge(verifier));
    assert.equal(consumeAuthCode(code, verifier), "user-1");
    assert.equal(consumeAuthCode(code, verifier), undefined);
  });

  test("codes issued without a code_challenge (legacy/no-PKCE path) still redeem", () => {
    const code = issueAuthCode("user-1", "http://localhost/callback");
    assert.equal(consumeAuthCode(code), "user-1");
  });

  test("rejects an unknown code", () => {
    assert.equal(consumeAuthCode("not-a-real-code", "anything"), undefined);
  });
});

describe("JWKS shape", () => {
  test("publicJwk carries the kid/use/alg NimbusJwtDecoder expects", () => {
    assert.equal(publicJwk.kid, "v2-local-key");
    assert.equal(publicJwk.use, "sig");
    assert.equal(publicJwk.alg, "RS256");
    assert.equal(publicJwk.kty, "RSA");
    assert.ok(publicJwk.n && publicJwk.e, "RSA JWK must carry n and e");
  });

  test("signJwt's header kid matches the published JWKS kid", () => {
    const token = signJwt({ sub: "user-1" }, 3600);
    const header = JSON.parse(Buffer.from(token.split(".")[0], "base64url").toString());
    assert.equal(header.kid, publicJwk.kid);
    assert.equal(header.alg, "RS256");
  });
});
