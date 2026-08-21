import { render, screen, waitFor, act } from "@testing-library/react";
import { describe, it, expect } from "vitest";

import ImageWithFallback from "../ImageFallback";

describe("ImageFallback", () => {
  it("should render image with src", () => {
    render(
      <ImageWithFallback
        src="test.jpg"
        alt="Test image"
        fallbackSrc="fallback.jpg"
      />,
    );
    const img = screen.getByAltText("Test image");
    expect(img).toBeInTheDocument();
    expect(img).toHaveAttribute("src", "test.jpg");
  });

  it("should switch to fallback on error", async () => {
    const { container } = render(
      <ImageWithFallback
        src="invalid.jpg"
        alt="Test image"
        fallbackSrc="fallback.jpg"
      />,
    );
    const img = container.querySelector("img");
    if (img) {
      // Simulate error using fireEvent or by setting src to invalid
      const errorEvent = new Event("error", { bubbles: true });
      // Use act to wrap state updates
      await act(async () => {
        img.dispatchEvent(errorEvent);
      });
    }
    // After error, src should be fallback (check after state update)
    await waitFor(() => {
      const updatedImg = container.querySelector("img");
      expect(updatedImg?.getAttribute("src")).toBe("fallback.jpg");
    });
  });

  it("should apply custom className", () => {
    const { container } = render(
      <ImageWithFallback
        src="test.jpg"
        alt="Test"
        fallbackSrc="fallback.jpg"
        className="custom-class"
      />,
    );
    const img = container.querySelector("img");
    expect(img?.className).toBe("custom-class");
  });

  it("should handle load event", () => {
    const { container } = render(
      <ImageWithFallback
        src="test.jpg"
        alt="Test"
        fallbackSrc="fallback.jpg"
      />,
    );
    const img = container.querySelector("img");
    if (img) {
      // Simulate load
      const loadEvent = new Event("load");
      Object.defineProperty(img, "naturalWidth", { value: 100 });
      Object.defineProperty(img, "naturalHeight", { value: 100 });
      img.dispatchEvent(loadEvent);
    }
    expect(img).toBeInTheDocument();
  });
});
