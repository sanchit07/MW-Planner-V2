import { PresentationTheme } from "@pages/campaigns/media-plan/types";
import { renderHook } from "@testing-library/react";
import { describe, it, expect } from "vitest";

import { useThemeClasses } from "../useThemeClasses";

describe("useThemeClasses", () => {
  it("should return theme classes", () => {
    const theme: PresentationTheme = {
      id: 1,
      name: "Test Theme",
    } as unknown as PresentationTheme;

    const { result } = renderHook(() => useThemeClasses(theme));

    expect(result.current.textColor).toBeDefined();
    expect(result.current.bgColor).toBeDefined();
  });

  it("should update when theme id changes", () => {
    const theme1: PresentationTheme = {
      id: 1,
      name: "Theme 1",
    } as unknown as PresentationTheme;

    const theme2: PresentationTheme = {
      id: 2,
      name: "Theme 2",
    } as unknown as PresentationTheme;

    const { result, rerender } = renderHook(
      ({ theme }) => useThemeClasses(theme),
      {
        initialProps: { theme: theme1 },
      },
    );

    const firstResult = result.current;

    rerender({ theme: theme2 });

    expect(result.current).not.toBe(firstResult);
  });
});
