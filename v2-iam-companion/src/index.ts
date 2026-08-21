import express from "express";
import { createV2MockIam } from "./v2-mock-iam.js";

const app = express();
app.use(express.json());
// v2-backend's token exchange (and the OAuth2 spec generally) sends
// application/x-www-form-urlencoded bodies — without this, /api/v1/oauth/token
// never sees `code`/`code_verifier`/`refresh_token` at all (req.body stays empty).
app.use(express.urlencoded({ extended: true }));

// Registered before the IAM router below, since that router ends in a
// catch-all that would otherwise swallow this request too.
app.get("/healthz", (_req, res) => res.json({ ok: true }));

// Mounted at the service root — the old V1 host nested this under `/v2iam`;
// here the whole service *is* the IAM host, so `mw-planner.iam.service-url`
// (and `proxy.applications.iam-api`) should point at this base URL with no
// extra path segment, e.g. http://127.0.0.1:10001
app.use(createV2MockIam());

const PORT = Number(process.env.PORT ?? 10001);
app.listen(PORT, () => {
  console.log(`[v2-iam-companion] listening on :${PORT}`);
});
