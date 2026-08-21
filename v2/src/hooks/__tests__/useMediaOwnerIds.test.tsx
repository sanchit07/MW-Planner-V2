import { configureStore } from "@reduxjs/toolkit";
import { renderHook } from "@testing-library/react";
import { type ReactNode } from "react";
import { Provider } from "react-redux";
import { describe, expect, it } from "vitest";

import userSlice from "../../services/user/userSlice";
import { useMediaOwnerIds } from "../useMediaOwnerIds";

function wrapperWith(currentCompany: unknown) {
  const store = configureStore({
    reducer: { profile: userSlice },
    preloadedState: {
      profile: { profile: { current_company: currentCompany } as never },
    },
  });
  return ({ children }: { children: ReactNode }) => (
    <Provider store={store}>{children}</Provider>
  );
}

describe("useMediaOwnerIds", () => {
  it("returns company id + child ids for a MEDIA_OWNER", () => {
    const { result } = renderHook(() => useMediaOwnerIds(), {
      wrapper: wrapperWith({
        id: "co-1",
        company_type: { code: "MEDIA_OWNER" },
        childCompanies: { items: [{ id: "co-2" }, { id: "co-3" }] },
      }),
    });
    expect(result.current).toEqual(["co-1", "co-2", "co-3"]);
  });

  it("returns [] when the company is not a MEDIA_OWNER", () => {
    const { result } = renderHook(() => useMediaOwnerIds(), {
      wrapper: wrapperWith({
        id: "co-1",
        company_type: { code: "AGENCY" },
      }),
    });
    expect(result.current).toEqual([]);
  });

  it("returns [] when there is no current company", () => {
    const { result } = renderHook(() => useMediaOwnerIds(), {
      wrapper: wrapperWith(undefined),
    });
    expect(result.current).toEqual([]);
  });
});
