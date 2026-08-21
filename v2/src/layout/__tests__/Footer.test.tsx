import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

import { Footer } from "../Footer";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

describe("Footer", () => {
  it("should render footer with copyright text", () => {
    render(<Footer />);

    const footer = screen.getByRole("contentinfo");
    expect(footer).toBeInTheDocument();
    expect(footer).toHaveAttribute("id", "app-footer");

    const copyright = screen.getByText(/footer.copyright/i);
    expect(copyright).toBeInTheDocument();
  });

  it("should have correct footer structure", () => {
    render(<Footer />);

    const footer = screen.getByRole("contentinfo");
    expect(footer).toHaveClass("border-t", "border-mw-neutral-100");

    const copyright = document.querySelector("#footer-copyright");
    expect(copyright).toBeInTheDocument();
  });

  it("should have correct styling classes", () => {
    render(<Footer />);

    const footer = screen.getByRole("contentinfo");
    expect(footer).toHaveClass("h-[30px]", "w-full", "bg-white", "flex");
  });

  it("should render copyright text with correct classes", () => {
    render(<Footer />);

    const copyright = screen.getByText(/footer.copyright/i);
    expect(copyright).toHaveClass(
      "text-xs",
      "text-mw-neutral-200",
      "text-center",
      "truncate",
    );
  });
});
