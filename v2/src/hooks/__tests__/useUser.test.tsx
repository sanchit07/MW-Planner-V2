import { configureStore } from "@reduxjs/toolkit";
import authSlice from "@services/auth/authSlice";
import type { UserProfile } from "@services/user/userSlice";
import userSlice, {
  useLazyGetUserByIdQuery,
  setUserProfile,
} from "@services/user/userSlice";
import { renderHook, act, waitFor } from "@testing-library/react";
import React from "react";
import { Provider } from "react-redux";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { useUser } from "../useUser";

vi.mock("@services/user/userSlice", async (importOriginal) => {
  const mod = await importOriginal<typeof import("@services/user/userSlice")>();
  return {
    ...mod,
    useLazyGetUserByIdQuery: vi.fn(),
  };
});

const mockGetUserById = vi.fn();
const createStore = (
  profile: UserProfile | null = null,
  isAuthenticated = false,
) =>
  configureStore({
    reducer: { profile: userSlice, auth: authSlice },
    preloadedState: {
      profile: { profile },
      auth: {
        isAuthenticated,
        token: isAuthenticated ? "token" : null,
        refreshToken: null,
      },
    },
  });

const wrapper =
  (profile: UserProfile | null = null, isAuthenticated = false) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(Provider, {
      store: createStore(profile, isAuthenticated),
      children,
    });

describe("useUser", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useLazyGetUserByIdQuery).mockReturnValue([
      mockGetUserById as ReturnType<typeof useLazyGetUserByIdQuery>[0],
      { isLoading: false, error: null },
    ] as unknown as ReturnType<typeof useLazyGetUserByIdQuery>);
    mockGetUserById.mockReturnValue({
      unwrap: vi.fn().mockResolvedValue({ id: "user-1", first_name: "Jane" }),
    });
  });

  it("returns profile, isLoading, error, refetchUser, hasUserData, isAuthenticated", () => {
    const profile = {
      id: "user-1",
      first_name: "Jane",
      last_name: "Doe",
    } as UserProfile;
    const { result } = renderHook(() => useUser(), {
      wrapper: wrapper(profile, true),
    });
    expect(result.current.profile).toEqual(profile);
    expect(result.current.isLoading).toBe(false);
    expect(result.current.error).toBeNull();
    expect(typeof result.current.refetchUser).toBe("function");
    expect(result.current.hasUserData).toBe(true);
    expect(result.current.isAuthenticated).toBe(true);
  });

  it("hasUserData is false when profile is null", () => {
    const { result } = renderHook(() => useUser(), {
      wrapper: wrapper(null),
    });
    expect(result.current.hasUserData).toBe(false);
  });

  it("error returns message when query has error", () => {
    vi.mocked(useLazyGetUserByIdQuery).mockReturnValue([
      mockGetUserById as ReturnType<typeof useLazyGetUserByIdQuery>[0],
      { isLoading: false, error: { message: "Failed" } },
    ] as unknown as ReturnType<typeof useLazyGetUserByIdQuery>);
    const { result } = renderHook(() => useUser(), {
      wrapper: wrapper(null),
    });
    expect(result.current.error).toBe("Failed to load user data");
  });

  it("refetchUser calls getUserById and dispatches setUserProfile on success", async () => {
    const profile = { id: "user-1", first_name: "Jane" } as UserProfile;
    const store = createStore(profile);
    const dispatchSpy = vi.spyOn(store, "dispatch");
    const storeWrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(Provider, { store, children });
    const { result } = renderHook(() => useUser(), {
      wrapper: storeWrapper,
    });
    await act(async () => {
      await result.current.refetchUser();
    });
    expect(mockGetUserById).toHaveBeenCalledWith("user-1");
    await waitFor(() => {
      expect(dispatchSpy).toHaveBeenCalledWith(
        setUserProfile(expect.any(Object)),
      );
    });
  });

  it("refetchUser does not call API when profile has no id", async () => {
    const store = createStore({} as UserProfile);
    const storeWrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(Provider, { store, children });
    const { result } = renderHook(() => useUser(), {
      wrapper: storeWrapper,
    });
    await act(async () => {
      await result.current.refetchUser();
    });
    expect(mockGetUserById).not.toHaveBeenCalled();
  });
});
