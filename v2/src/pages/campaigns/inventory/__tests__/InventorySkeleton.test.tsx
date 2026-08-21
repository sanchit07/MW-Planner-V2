import { render } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { InventorySkeleton } from "../InventorySkeleton";

describe("InventorySkeleton", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("rendering", () => {
    it("renders default count of 5 skeleton cards", () => {
      const { container } = render(<InventorySkeleton />);
      const cards = container.querySelectorAll(".hover\\:bg-mw-primary-50");
      expect(cards.length).toBe(5);
    });

    it("renders custom count when count prop is provided", () => {
      const { container } = render(<InventorySkeleton count={3} />);
      const cards = container.querySelectorAll(".hover\\:bg-mw-primary-50");
      expect(cards.length).toBe(3);
    });

    it("renders skeleton elements within each card", () => {
      const { container } = render(<InventorySkeleton count={1} />);
      const skeletons = container.querySelectorAll("[class*='animate-pulse']");
      expect(skeletons.length).toBeGreaterThan(0);
    });

    it("renders CardHeader and CardFooter structure", () => {
      const { container } = render(<InventorySkeleton count={1} />);
      const card = container.firstChild?.firstChild;
      expect(card).toBeInTheDocument();
      const header = card?.firstChild;
      const footer = card?.lastChild;
      expect(header).toBeInTheDocument();
      expect(footer).toBeInTheDocument();
    });
  });

  describe("props", () => {
    it("accepts count of 0 and renders no cards", () => {
      const { container } = render(<InventorySkeleton count={0} />);
      const cards = container.querySelectorAll(".hover\\:bg-mw-primary-50");
      expect(cards.length).toBe(0);
    });

    it("accepts count of 1 and renders one card", () => {
      const { container } = render(<InventorySkeleton count={1} />);
      const cards = container.querySelectorAll(".hover\\:bg-mw-primary-50");
      expect(cards.length).toBe(1);
    });
  });
});
