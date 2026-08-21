import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect } from "vitest";

import InventoryThumbnail from "../InventoryThumbnail";

describe("InventoryThumbnail", () => {
  it("renders the image with the given src, alt and className when src is provided", () => {
    render(
      <InventoryThumbnail
        src="https://example.com/a.jpg"
        alt="Billboard A"
        className="size-14 rounded-sm"
      />,
    );
    const img = screen.getByAltText("Billboard A") as HTMLImageElement;
    expect(img).toBeInTheDocument();
    expect(img.getAttribute("src")).toBe("https://example.com/a.jpg");
    expect(img.className).toContain("size-14");
  });

  it("shows the broken-image fallback when src is empty", () => {
    render(<InventoryThumbnail src="" alt="No image" className="size-14" />);
    expect(screen.queryByAltText("No image")).not.toBeInTheDocument();
    expect(
      screen.getByTestId("inventory-thumbnail-fallback"),
    ).toBeInTheDocument();
  });

  it("swaps to the broken-image fallback when the image fails to load", () => {
    render(
      <InventoryThumbnail
        src="https://example.com/broken.jpg"
        alt="Broken"
        className="size-14"
      />,
    );
    const img = screen.getByAltText("Broken");
    fireEvent.error(img);
    expect(screen.queryByAltText("Broken")).not.toBeInTheDocument();
    expect(
      screen.getByTestId("inventory-thumbnail-fallback"),
    ).toBeInTheDocument();
  });

  it("applies the className to the fallback so it keeps the image box size", () => {
    render(
      <InventoryThumbnail src="" alt="No image" className="w-full h-32" />,
    );
    const fallback = screen.getByTestId("inventory-thumbnail-fallback");
    expect(fallback.className).toContain("w-full");
    expect(fallback.className).toContain("h-32");
  });
});
