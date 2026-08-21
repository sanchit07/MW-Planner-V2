import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";

import { Accordion } from "../Accordion";

describe("Accordion", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("Rendering", () => {
    it("renders accordion with title and children", () => {
      render(
        <Accordion title="Test Title">
          <div>Test Content</div>
        </Accordion>,
      );
      expect(screen.getByText("Test Title")).toBeInTheDocument();
      expect(screen.getByText("Test Content")).toBeInTheDocument();
    });

    it("renders accordion with default expanded state", () => {
      render(
        <Accordion title="Test Title">
          <div>Test Content</div>
        </Accordion>,
      );
      expect(screen.getByText("Test Content")).toBeInTheDocument();
    });

    it("renders accordion collapsed when defaultExpanded is false", () => {
      render(
        <Accordion title="Test Title" defaultExpanded={false}>
          <div>Test Content</div>
        </Accordion>,
      );
      expect(screen.queryByText("Test Content")).not.toBeInTheDocument();
    });

    it("renders header actions when provided", () => {
      render(
        <Accordion title="Test Title" headerActions={<button>Action</button>}>
          <div>Test Content</div>
        </Accordion>,
      );
      expect(
        screen.getByRole("button", { name: "Action" }),
      ).toBeInTheDocument();
    });

    it("applies custom className", () => {
      const { container } = render(
        <Accordion title="Test Title" className="custom-class">
          <div>Test Content</div>
        </Accordion>,
      );
      const card = container.querySelector(".custom-class");
      expect(card).toBeInTheDocument();
    });

    it("applies custom headerClassName", () => {
      const { container } = render(
        <Accordion title="Test Title" headerClassName="custom-header-class">
          <div>Test Content</div>
        </Accordion>,
      );
      const header = container.querySelector(".custom-header-class");
      expect(header).toBeInTheDocument();
    });

    it("applies custom contentClassName", () => {
      const { container } = render(
        <Accordion title="Test Title" contentClassName="custom-content-class">
          <div>Test Content</div>
        </Accordion>,
      );
      const content = container.querySelector(".custom-content-class");
      expect(content).toBeInTheDocument();
    });
  });

  describe("Interactions", () => {
    it("toggles expanded state when header is clicked", async () => {
      const user = userEvent.setup();
      render(
        <Accordion title="Test Title" defaultExpanded={true}>
          <div>Test Content</div>
        </Accordion>,
      );

      expect(screen.getByText("Test Content")).toBeInTheDocument();

      const header = screen.getByText("Test Title").closest("div");
      if (header) {
        await user.click(header);
      }

      await waitFor(() => {
        expect(screen.queryByText("Test Content")).not.toBeInTheDocument();
      });
    });

    it("toggles expanded state when toggle button is clicked", async () => {
      const user = userEvent.setup();
      render(
        <Accordion title="Test Title" defaultExpanded={true}>
          <div>Test Content</div>
        </Accordion>,
      );

      expect(screen.getByText("Test Content")).toBeInTheDocument();

      const toggleButton = screen.getByRole("button", {
        name: /collapse|expand/i,
      });
      await user.click(toggleButton);

      await waitFor(() => {
        expect(screen.queryByText("Test Content")).not.toBeInTheDocument();
      });
    });

    it("expands accordion when collapsed and header is clicked", async () => {
      const user = userEvent.setup();
      render(
        <Accordion title="Test Title" defaultExpanded={false}>
          <div>Test Content</div>
        </Accordion>,
      );

      expect(screen.queryByText("Test Content")).not.toBeInTheDocument();

      const header = screen.getByText("Test Title").closest("div");
      if (header) {
        await user.click(header);
      }

      await waitFor(() => {
        expect(screen.getByText("Test Content")).toBeInTheDocument();
      });
    });

    it("does not toggle when header actions are clicked", async () => {
      const user = userEvent.setup();
      const actionClickHandler = vi.fn();
      render(
        <Accordion
          title="Test Title"
          defaultExpanded={true}
          headerActions={<button onClick={actionClickHandler}>Action</button>}
        >
          <div>Test Content</div>
        </Accordion>,
      );

      const actionButton = screen.getByRole("button", { name: "Action" });
      await user.click(actionButton);

      expect(actionClickHandler).toHaveBeenCalledTimes(1);
      expect(screen.getByText("Test Content")).toBeInTheDocument();
    });

    it("updates aria-label based on expanded state", async () => {
      const user = userEvent.setup();
      render(
        <Accordion title="Test Title" defaultExpanded={true}>
          <div>Test Content</div>
        </Accordion>,
      );

      let toggleButton = screen.getByRole("button", { name: "Collapse" });
      expect(toggleButton).toBeInTheDocument();

      await user.click(toggleButton);

      await waitFor(() => {
        toggleButton = screen.getByRole("button", { name: "Expand" });
        expect(toggleButton).toBeInTheDocument();
      });
    });
  });

  describe("Accessibility", () => {
    it("has accessible toggle button with aria-label", () => {
      render(
        <Accordion title="Test Title">
          <div>Test Content</div>
        </Accordion>,
      );
      const toggleButton = screen.getByRole("button", {
        name: /collapse|expand/i,
      });
      expect(toggleButton).toBeInTheDocument();
    });

    it("header is clickable and accessible", () => {
      render(
        <Accordion title="Test Title">
          <div>Test Content</div>
        </Accordion>,
      );
      const title = screen.getByText("Test Title");
      let current: HTMLElement | null = title.parentElement;
      let header: HTMLElement | null = null;

      while (current) {
        if (
          current.className &&
          typeof current.className === "string" &&
          current.className.includes("cursor-pointer")
        ) {
          header = current;
          break;
        }
        current = current.parentElement;
      }

      expect(header).toBeInTheDocument();
      expect(header).toHaveClass("cursor-pointer");
    });
  });

  describe("Edge Cases", () => {
    it("handles empty children", () => {
      render(<Accordion title="Test Title">{null}</Accordion>);
      expect(screen.getByText("Test Title")).toBeInTheDocument();
    });

    it("handles multiple children", () => {
      render(
        <Accordion title="Test Title">
          <div>Child 1</div>
          <div>Child 2</div>
        </Accordion>,
      );
      expect(screen.getByText("Child 1")).toBeInTheDocument();
      expect(screen.getByText("Child 2")).toBeInTheDocument();
    });

    it("handles complex nested children", () => {
      render(
        <Accordion title="Test Title">
          <div>
            <h3>Nested Title</h3>
            <p>Nested Content</p>
          </div>
        </Accordion>,
      );
      expect(screen.getByText("Nested Title")).toBeInTheDocument();
      expect(screen.getByText("Nested Content")).toBeInTheDocument();
    });
  });
});
