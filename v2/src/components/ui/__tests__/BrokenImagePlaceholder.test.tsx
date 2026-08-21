import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";

import BrokenImagePlaceholder from "../BrokenImagePlaceholder";

describe("BrokenImagePlaceholder", () => {
  it("renders the placeholder tile", () => {
    render(<BrokenImagePlaceholder className="size-14" />);
    expect(
      screen.getByTestId("inventory-thumbnail-fallback"),
    ).toBeInTheDocument();
  });

  it("applies the given className so it keeps the image box size", () => {
    render(<BrokenImagePlaceholder className="w-full h-32" />);
    const el = screen.getByTestId("inventory-thumbnail-fallback");
    expect(el.className).toContain("w-full");
    expect(el.className).toContain("h-32");
  });

  it("gives each instance unique mask/clip ids so they do not collide", () => {
    const { container } = render(
      <>
        <BrokenImagePlaceholder className="size-14" />
        <BrokenImagePlaceholder className="size-14" />
      </>,
    );
    const masks = Array.from(container.querySelectorAll("mask")).map((m) =>
      m.getAttribute("id"),
    );
    expect(masks).toHaveLength(2);
    expect(masks[0]).not.toBe(masks[1]);
  });
});
