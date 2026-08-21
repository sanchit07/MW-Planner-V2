import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@utils/storage", () => ({
  default: {
    getItem: vi.fn(),
    setItem: vi.fn(),
    removeItem: vi.fn(),
    removeAll: vi.fn(),
  },
}));

vi.mock("@api/axiosBaseQuery", () => ({
  default: vi.fn(() => vi.fn()),
}));

const makeToken = (expiresIn: number) =>
  JSON.stringify({
    access_token: "access-token",
    refresh_token: "refresh-token",
    expires_in: expiresIn,
    token_type: "Bearer",
    scope: "openid",
    state: "",
  });

describe("authSlice - getInitialState", () => {
  beforeEach(() => {
    vi.resetModules();
  });

  it("unauthenticated when no token in storage", async () => {
    const storage = await import("@utils/storage");
    vi.mocked(storage.default.getItem).mockReturnValue(null);

    const { default: authReducer } = await import("../authSlice");
    const state = authReducer(undefined, { type: "@@INIT" });

    expect(state.isAuthenticated).toBe(false);
    expect(state.token).toBeNull();
  });

  it("authenticated when valid non-expired token in storage", async () => {
    const storage = await import("@utils/storage");
    vi.mocked(storage.default.getItem).mockReturnValue(
      makeToken(Date.now() + 3_600_000),
    );

    const { default: authReducer } = await import("../authSlice");
    const state = authReducer(undefined, { type: "@@INIT" });

    expect(state.isAuthenticated).toBe(true);
    expect(state.token).toBe("access-token");
    expect(state.refreshToken).toBe("refresh-token");
    expect(state.hasPlannerAccess).toBe(true);
  });

  it("authenticated when access token is expired but a refresh token is present (defers to the 401 interceptor)", async () => {
    const storage = await import("@utils/storage");
    vi.mocked(storage.default.getItem).mockReturnValue(
      makeToken(Date.now() - 1_000),
    );

    const { default: authReducer } = await import("../authSlice");
    const state = authReducer(undefined, { type: "@@INIT" });

    expect(state.isAuthenticated).toBe(true);
    expect(state.token).toBe("access-token");
    expect(state.refreshToken).toBe("refresh-token");
    expect(storage.default.removeItem).not.toHaveBeenCalledWith("auth_token");
  });

  it("unauthenticated when access token is expired and there is no refresh token (does not remove from storage to avoid cross-tab logout)", async () => {
    const storage = await import("@utils/storage");
    vi.mocked(storage.default.getItem).mockReturnValue(
      JSON.stringify({
        access_token: "access-token",
        expires_in: Date.now() - 1_000,
        token_type: "Bearer",
        scope: "openid",
        state: "",
      }),
    );

    const { default: authReducer } = await import("../authSlice");
    const state = authReducer(undefined, { type: "@@INIT" });

    expect(state.isAuthenticated).toBe(false);
    expect(state.token).toBeNull();
    expect(storage.default.removeItem).not.toHaveBeenCalledWith("auth_token");
  });

  it("authenticated when token has no expires_in (backward compat)", async () => {
    const storage = await import("@utils/storage");
    vi.mocked(storage.default.getItem).mockReturnValue(
      JSON.stringify({
        access_token: "access-token",
        refresh_token: "refresh-token",
        token_type: "Bearer",
        scope: "openid",
        state: "",
      }),
    );

    const { default: authReducer } = await import("../authSlice");
    const state = authReducer(undefined, { type: "@@INIT" });

    expect(state.isAuthenticated).toBe(true);
  });

  it("unauthenticated when storage contains malformed JSON", async () => {
    const storage = await import("@utils/storage");
    vi.mocked(storage.default.getItem).mockReturnValue("not-valid-json{{{");

    const { default: authReducer } = await import("../authSlice");
    const state = authReducer(undefined, { type: "@@INIT" });

    expect(state.isAuthenticated).toBe(false);
  });
});

describe("authSlice - logout action", () => {
  it("clears auth state and storage on logout", async () => {
    const storage = await import("@utils/storage");
    vi.mocked(storage.default.getItem).mockReturnValue(null);

    const { default: authReducer, logout } = await import("../authSlice");

    const loggedInState = {
      isAuthenticated: true,
      token: "access-token",
      refreshToken: "refresh-token",
      hasPlannerAccess: true,
    };

    const state = authReducer(loggedInState, logout());

    expect(state.isAuthenticated).toBe(false);
    expect(state.token).toBeNull();
    expect(storage.default.removeAll).toHaveBeenCalled();
  });
});
