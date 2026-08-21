import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";

import PresentationThemeSelector from "../PresentationThemeSelector";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

describe("PresentationThemeSelector", () => {
  it("renders dropdown with current theme name", () => {
    render(
      <PresentationThemeSelector
        selectedTheme="primary"
        onThemeChange={vi.fn()}
      />,
    );
    expect(
      screen.getByRole("button", { name: /mediaPlan\.theme\.primary/i }),
    ).toBeInTheDocument();
  });

  it("falls back to first theme when selectedTheme id not found", () => {
    render(
      <PresentationThemeSelector
        selectedTheme="nonexistent"
        onThemeChange={vi.fn()}
      />,
    );
    expect(
      screen.getByRole("button", { name: /mediaPlan\.theme\.primary/i }),
    ).toBeInTheDocument();
  });

  it("calls onThemeChange when a theme option is clicked", async () => {
    const onThemeChange = vi.fn();
    const user = userEvent.setup();
    render(
      <PresentationThemeSelector
        selectedTheme="primary"
        onThemeChange={onThemeChange}
      />,
    );
    await user.click(
      screen.getByRole("button", { name: /mediaPlan\.theme\.primary/i }),
    );
    await user.click(screen.getByText("mediaPlan.theme.warning"));
    expect(onThemeChange).toHaveBeenCalledWith("warning");
  });

  it("renders all four theme options when dropdown is open", async () => {
    const user = userEvent.setup();
    render(
      <PresentationThemeSelector
        selectedTheme="primary"
        onThemeChange={vi.fn()}
      />,
    );
    await user.click(
      screen.getByRole("button", { name: /mediaPlan\.theme\.primary/i }),
    );
    expect(
      screen.getAllByText("mediaPlan.theme.primary").length,
    ).toBeGreaterThan(0);
    expect(
      screen.getByRole("menuitem", { name: "mediaPlan.theme.warning" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("menuitem", { name: "mediaPlan.theme.success" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("menuitem", { name: "mediaPlan.theme.purple-warning" }),
    ).toBeInTheDocument();
  });
});
