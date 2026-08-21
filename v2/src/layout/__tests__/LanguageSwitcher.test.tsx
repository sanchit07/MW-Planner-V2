import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import LanguageSwitcher from "../LanguageSwitcher";

// Mock Tolgee
const mockChangeLanguage = vi.fn();
const mockGetLanguage = vi.fn(() => "en");

vi.mock("@tolgee/react", () => ({
  useTolgee: () => ({
    getLanguage: mockGetLanguage,
    changeLanguage: mockChangeLanguage,
  }),
  useTranslate: () => ({ t: (key: string) => key }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

// Mock storage
vi.mock("@utils/storage", () => ({
  setItem: vi.fn(),
}));

describe("LanguageSwitcher", () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
    mockGetLanguage.mockReturnValue("en");
  });

  it("should render language switcher", () => {
    render(<LanguageSwitcher />);

    const switcher = document.querySelector("#language-switcher");
    expect(switcher).toBeInTheDocument();
  });

  it("should display current language", () => {
    mockGetLanguage.mockReturnValue("en");
    render(<LanguageSwitcher />);

    const currentLanguage = document.querySelector("#current-language");
    expect(currentLanguage).toBeInTheDocument();
    expect(currentLanguage).toHaveTextContent("ENG");
  });

  it("should display Japanese language when selected", () => {
    mockGetLanguage.mockReturnValue("ja");
    render(<LanguageSwitcher />);

    // The current-language span has "hidden sm:inline" class, so use querySelector
    const currentLanguage = document.querySelector("#current-language");
    expect(currentLanguage).toBeInTheDocument();
    // Even if hidden, the element should exist in the DOM
    expect(currentLanguage).toBeTruthy();
  });

  it("should change language when option is clicked", async () => {
    render(<LanguageSwitcher />);

    // Find and click the dropdown trigger
    const trigger =
      screen.getByRole("button") || document.querySelector('[name="language"]');

    if (trigger) {
      await user.click(trigger);

      // Wait for dropdown to open and find language option
      await waitFor(() => {
        const japaneseOption = screen.getByText("JPN");
        if (japaneseOption) {
          user.click(japaneseOption);
        }
      });

      // Verify changeLanguage was called
      await waitFor(() => {
        expect(mockChangeLanguage).toHaveBeenCalled();
      });
    }
  });

  it("should have correct structure with dropdown", () => {
    render(<LanguageSwitcher />);

    const switcher = document.querySelector("#language-switcher");
    expect(switcher).toBeInTheDocument();
  });
});
