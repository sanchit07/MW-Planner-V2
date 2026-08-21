import { describe, it, expect } from "vitest";

import sidebarReducer, {
  toggleSidebar,
  setSidebarCollapsed,
} from "../sidebar-toggle.slice";

describe("sidebar-toggle.slice", () => {
  describe("initial state", () => {
    it("has isSidebarCollapsed false by default", () => {
      const state = sidebarReducer(undefined, { type: "unknown" });
      expect(state).toEqual({ isSidebarCollapsed: false });
    });
  });

  describe("toggleSidebar", () => {
    it("sets isSidebarCollapsed to true when currently false", () => {
      const state = sidebarReducer(
        { isSidebarCollapsed: false },
        toggleSidebar(),
      );
      expect(state.isSidebarCollapsed).toBe(true);
    });

    it("sets isSidebarCollapsed to false when currently true", () => {
      const state = sidebarReducer(
        { isSidebarCollapsed: true },
        toggleSidebar(),
      );
      expect(state.isSidebarCollapsed).toBe(false);
    });
  });

  describe("setSidebarCollapsed", () => {
    it("sets isSidebarCollapsed to true when payload is true", () => {
      const state = sidebarReducer(
        { isSidebarCollapsed: false },
        setSidebarCollapsed(true),
      );
      expect(state.isSidebarCollapsed).toBe(true);
    });

    it("sets isSidebarCollapsed to false when payload is false", () => {
      const state = sidebarReducer(
        { isSidebarCollapsed: true },
        setSidebarCollapsed(false),
      );
      expect(state.isSidebarCollapsed).toBe(false);
    });
  });
});
