import { configureStore } from "@reduxjs/toolkit";
import { renderHook, act, waitFor } from "@testing-library/react";
import React from "react";
import { Provider } from "react-redux";
import { describe, it, expect, vi, beforeEach } from "vitest";

import campaignsUIReducer, {
  setPaginationPage,
} from "../../../services/campaign/campaignsUISlice";
import userSlice, {
  setUserProfile,
  updateUserProfile,
} from "../../../services/user/userSlice";
import { useDataSync } from "../hooks/useDataSync";

vi.mock("@utils/storage", () => ({
  default: {
    getItem: vi.fn(() => null),
    setItem: vi.fn(),
    removeItem: vi.fn(),
    removeAll: vi.fn(),
  },
}));

const { mockUseGetCampaignsQuery } = vi.hoisted(() => ({
  mockUseGetCampaignsQuery: vi.fn(),
}));

vi.mock("@services/campaign/campaignSlice", async (importOriginal) => {
  const actual =
    await importOriginal<
      typeof import("../../../services/campaign/campaignSlice")
    >();
  return {
    ...actual,
    useGetCampaignsQuery: mockUseGetCampaignsQuery,
  };
});

const mockRefetch = vi.fn();

/** Minimal valid UserProfile payload for setUserProfile */
function makeProfilePayload(activeCompanyId: string) {
  return {
    success: true,
    data: {
      id: "user-1",
      user_id: "user-1",
      sub: "user-1",
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
      memberships: [
        {
          company_id: activeCompanyId,
          company_name: "Test Co",
          role_id: "role-1",
          role_name: "Admin",
          is_active: true,
          subscriptions: [],
        },
      ],
      avatar: null,
      locale: "en",
      activated: true,
      current_company: {
        id: activeCompanyId,
        name: "Test Co",
        seat_id: 1,
        company_type: {
          id: "ct-1",
          name: "Agency",
          code: "AGENCY",
          is_supplier_side: false,
          is_demand_side: true,
        },
        role_id: "role-1",
        role_name: "Admin",
      },
      activeCompanyId,
    },
  };
}

function createStore(initialCompanyId = "company-1") {
  const store = configureStore({
    reducer: {
      profile: userSlice,
      campaignsUI: campaignsUIReducer,
    },
  });
  store.dispatch(setUserProfile(makeProfilePayload(initialCompanyId)));
  return store;
}

function makeWrapper(store: ReturnType<typeof createStore>) {
  return ({ children }: { children: React.ReactNode }) => (
    <Provider store={store}>{children}</Provider>
  );
}

describe("useDataSync", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseGetCampaignsQuery.mockReturnValue({
      data: undefined,
      isLoading: false,
      isFetching: false,
      error: undefined,
      refetch: mockRefetch,
    });
  });

  describe("companyId in queryParams", () => {
    it("passes the user's activeCompanyId as companyId to the query", () => {
      const store = createStore("company-abc");
      renderHook(() => useDataSync(), { wrapper: makeWrapper(store) });

      expect(mockUseGetCampaignsQuery).toHaveBeenCalledWith(
        expect.objectContaining({ companyId: "company-abc" }),
        expect.anything(),
      );
    });

    it("uses memberships[0].company_id when activeCompanyId is absent", () => {
      const store = createStore("company-1");
      // Remove activeCompanyId, leaving only the membership fallback
      store.dispatch(
        updateUserProfile({ activeCompanyId: undefined } as Parameters<
          typeof updateUserProfile
        >[0]),
      );

      renderHook(() => useDataSync(), { wrapper: makeWrapper(store) });

      expect(mockUseGetCampaignsQuery).toHaveBeenCalledWith(
        expect.objectContaining({ companyId: "company-1" }),
        expect.anything(),
      );
    });
  });

  describe("company switch", () => {
    it("re-queries with the new companyId when activeCompanyId changes", async () => {
      const store = createStore("company-1");
      renderHook(() => useDataSync(), { wrapper: makeWrapper(store) });

      expect(mockUseGetCampaignsQuery).toHaveBeenCalledWith(
        expect.objectContaining({ companyId: "company-1" }),
        expect.anything(),
      );

      act(() => {
        store.dispatch(updateUserProfile({ activeCompanyId: "company-2" }));
      });

      await waitFor(() => {
        expect(mockUseGetCampaignsQuery).toHaveBeenCalledWith(
          expect.objectContaining({ companyId: "company-2" }),
          expect.anything(),
        );
      });
    });

    it("resets pagination to page 1 when the active company changes", async () => {
      const store = createStore("company-1");
      store.dispatch(setPaginationPage(5));
      expect(store.getState().campaignsUI.pagination.page).toBe(5);

      renderHook(() => useDataSync(), { wrapper: makeWrapper(store) });

      act(() => {
        store.dispatch(updateUserProfile({ activeCompanyId: "company-2" }));
      });

      await waitFor(() => {
        expect(store.getState().campaignsUI.pagination.page).toBe(1);
      });
    });

    it("does not reset pagination on initial mount (no company change)", async () => {
      const store = createStore("company-1");
      store.dispatch(setPaginationPage(3));

      renderHook(() => useDataSync(), { wrapper: makeWrapper(store) });

      // Allow effects to settle
      await act(async () => {});

      expect(store.getState().campaignsUI.pagination.page).toBe(3);
    });
  });
});
