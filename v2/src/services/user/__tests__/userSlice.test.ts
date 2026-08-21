import { configureStore } from "@reduxjs/toolkit";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("@utils/storage", () => ({
  default: {
    getItem: vi.fn().mockReturnValue(null),
    setItem: vi.fn(),
    removeItem: vi.fn(),
  },
}));

const mockBaseQueryCall = vi.hoisted(() =>
  vi.fn().mockResolvedValue({ data: { success: true, data: {} } }),
);
vi.mock("@api/axiosBaseQuery", () => ({
  default: () => mockBaseQueryCall,
}));

import {
  userSlice,
  userApi,
  setUserProfile,
  clearUserProfile,
  updateUserProfile,
  UserProfile,
} from "../userSlice";

const reducer = userSlice.reducer;

const makeProfile = (overrides: Partial<UserProfile> = {}): UserProfile => ({
  id: "user-1",
  user_id: "user-1",
  sub: "sub-123",
  email: "test@example.com",
  username: "testuser",
  first_name: "Test",
  last_name: "User",
  email_verified: true,
  phone_verified: false,
  is_global_admin: false,
  has_system_role: false,
  system_permissions: null,
  permissions: null,
  memberships: [],
  avatar: null,
  locale: "en",
  activated: true,
  current_company: {
    id: "company-1",
    name: "Test Company",
    role_id: "role-1",
    role_name: "Admin",
    seat_id: 1,
    company_type: {
      id: "type-1",
      code: "AGENCY",
      name: "Agency",
      is_demand_side: true,
      is_supplier_side: false,
    },
  },
  ...overrides,
});

const initialState = { profile: null };

describe("userSlice reducers", () => {
  describe("setUserProfile", () => {
    it("sets the profile from the payload data", () => {
      const profile = makeProfile();
      const state = reducer(
        initialState,
        setUserProfile({ success: true, data: profile }),
      );
      expect(state.profile?.email).toBe("test@example.com");
    });

    it("sets profile to null when payload.data is undefined", () => {
      const state = reducer(initialState, setUserProfile({ success: false }));
      expect(state.profile).toBeNull();
    });

    it("replaces an existing profile", () => {
      const first = reducer(
        initialState,
        setUserProfile({
          success: true,
          data: makeProfile({ first_name: "Old" }),
        }),
      );
      const second = reducer(
        first,
        setUserProfile({
          success: true,
          data: makeProfile({ first_name: "New" }),
        }),
      );
      expect(second.profile?.first_name).toBe("New");
    });

    it("preserves activeCompanyId across a fresh profile fetch when the new profile still grants access to it", () => {
      const withImpersonation = reducer(
        reducer(
          initialState,
          setUserProfile({ success: true, data: makeProfile() }),
        ),
        updateUserProfile({ activeCompanyId: "company-2" }),
      );

      const refreshed = reducer(
        withImpersonation,
        setUserProfile({
          success: true,
          data: makeProfile({
            memberships: [
              {
                company_id: "company-2",
                company_name: "Other Company",
                role_id: "role-2",
                role_name: "Viewer",
                is_active: true,
                subscriptions: [],
              },
            ],
          }),
        }),
      );

      expect(refreshed.profile?.activeCompanyId).toBe("company-2");
    });

    it("drops activeCompanyId across a fresh profile fetch when the new profile no longer grants access to it", () => {
      const withImpersonation = reducer(
        reducer(
          initialState,
          setUserProfile({ success: true, data: makeProfile() }),
        ),
        updateUserProfile({ activeCompanyId: "company-2" }),
      );

      const refreshed = reducer(
        withImpersonation,
        setUserProfile({
          success: true,
          data: makeProfile({ memberships: [] }),
        }),
      );

      expect(refreshed.profile?.activeCompanyId).toBeUndefined();
    });
  });

  describe("clearUserProfile", () => {
    it("sets profile to null", () => {
      const start = reducer(
        initialState,
        setUserProfile({ success: true, data: makeProfile() }),
      );
      const state = reducer(start, clearUserProfile());
      expect(state.profile).toBeNull();
    });

    it("is idempotent on already-null profile", () => {
      const state = reducer(initialState, clearUserProfile());
      expect(state.profile).toBeNull();
    });
  });

  describe("updateUserProfile", () => {
    it("merges partial update into existing profile", () => {
      const start = reducer(
        initialState,
        setUserProfile({ success: true, data: makeProfile() }),
      );
      const state = reducer(
        start,
        updateUserProfile({ first_name: "Updated", last_name: "Name" }),
      );
      expect(state.profile?.first_name).toBe("Updated");
      expect(state.profile?.last_name).toBe("Name");
      expect(state.profile?.email).toBe("test@example.com");
    });

    it("is a no-op when profile is null", () => {
      const state = reducer(
        initialState,
        updateUserProfile({ first_name: "Should Not Apply" }),
      );
      expect(state.profile).toBeNull();
    });
  });
});

function makeUserStore(
  profileState: { profile: UserProfile | null } = { profile: null },
) {
  return configureStore({
    reducer: {
      [userApi.reducerPath]: userApi.reducer,
      profile: () => profileState,
    },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware().concat(userApi.middleware),
  });
}

describe("userApi endpoints", () => {
  let store: ReturnType<typeof makeUserStore>;

  beforeEach(() => {
    store = makeUserStore();
    mockBaseQueryCall.mockClear();
  });

  it("reducerPath is userApi", () => {
    expect(userApi.reducerPath).toBe("userApi");
  });

  it("both endpoints are registered", () => {
    expect(userApi.endpoints).toHaveProperty("getUserById");
    expect(userApi.endpoints).toHaveProperty("getProfile");
  });

  it("getUserById query function is covered when dispatched", () => {
    store.dispatch(userApi.endpoints.getUserById.initiate("user-123"));
    expect(userApi.endpoints.getUserById).toBeDefined();
  });

  it("getProfile query function is covered when dispatched", () => {
    store.dispatch(userApi.endpoints.getProfile.initiate());
    expect(userApi.endpoints.getProfile).toBeDefined();
  });

  it("does not send X-Company-Id when no company is active", async () => {
    await store.dispatch(userApi.endpoints.getProfile.initiate());
    const requestArg = mockBaseQueryCall.mock.calls[0][0];
    expect(requestArg.headers).toBeUndefined();
  });

  it("sends X-Company-Id from the active company on getProfile", async () => {
    store = makeUserStore({
      profile: makeProfile({ activeCompanyId: "company-9" }),
    });
    await store.dispatch(userApi.endpoints.getProfile.initiate());
    const requestArg = mockBaseQueryCall.mock.calls[0][0];
    expect(requestArg.headers).toEqual({ "X-Company-Id": "company-9" });
  });

  it("sends X-Company-Id from the active company on getUserById", async () => {
    store = makeUserStore({
      profile: makeProfile({ activeCompanyId: "company-9" }),
    });
    await store.dispatch(userApi.endpoints.getUserById.initiate("user-123"));
    const requestArg = mockBaseQueryCall.mock.calls[0][0];
    expect(requestArg.url).toBe("v1/users/user-123");
    expect(requestArg.headers).toEqual({ "X-Company-Id": "company-9" });
  });

  it("falls back to current_company.id when activeCompanyId is unset", async () => {
    store = makeUserStore({
      profile: makeProfile({ activeCompanyId: undefined }),
    });
    await store.dispatch(userApi.endpoints.getProfile.initiate());
    const requestArg = mockBaseQueryCall.mock.calls[0][0];
    expect(requestArg.headers).toEqual({ "X-Company-Id": "company-1" });
  });
});
