# v2-iam-companion

Standalone mock IAM / OAuth2 authorization server for the V2 stack (`v2/`, `v2-backend/`, `v2-recommendation/`).

This is **not real IAM**. It's a thin, self-signed-JWT authorization shim that lets you pick which real Admin Console user (from `account-sphere.replit.app`) you want to view Planner as — the "select a user, then go to dashboard" login flow. All identity *data* (users, memberships, companies, roles, permissions) is fetched live from the Admin Console; only the token issuance is mocked, because the Admin Console has no OAuth server of its own yet.

It was originally part of the legacy V1 Express server (`server/v2-mock-iam.ts` + `server/admin-console-client.ts`), which V2 depended on purely for auth. It has been extracted here so V2 can boot with **zero runtime dependency on V1**.

## Run

```bash
npm install
npm run dev      # tsx watch, defaults to :10001
```

Environment variables:

| Var | Required | Purpose |
| --- | --- | --- |
| `PORT` | no (default 10001) | HTTP port |
| `V2_IAM_BASE` | no (default `http://127.0.0.1:10001`) | Must exactly match `mw-planner.iam.service-url` in whichever v2-backend config profile is active — it's stamped as the `iss` claim on every issued JWT and used to build the JWKS URL v2-backend validates against |
| `ADMIN_CONSOLE_BASE_URL` | no (default `https://account-sphere.replit.app/api`) | Where to fetch users/roles/permissions from |
| `ADMIN_CONSOLE_TOKEN` | **yes** | Bearer token for the Admin Console API |

The RSA signing key is generated on first boot and persisted at `.data/v2-iam-key.pem` (gitignored) so restarts don't invalidate v2-backend's cached JWKS.

## Routes

Same routes as the original `server/v2-mock-iam.ts`, mounted at the service root instead of under `/v2iam`: `/.well-known/jwks.json`, `/api/v1/oauth/authorize` (renders the user-picker), `/api/v1/oauth/token`, `/userinfo`, `/api/v1/users/*`, `/api/v1/companies/*`.
