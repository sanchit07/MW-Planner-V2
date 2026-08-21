import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ModalDrawer } from "../ModalDrawer";

describe("ModalDrawer", () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    title: "Test Drawer",
    children: <div>Test Content</div>,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    document.body.style.overflow = "";
    // Make RAF synchronous so animation state resolves immediately in tests
    vi.stubGlobal("requestAnimationFrame", (cb: FrameRequestCallback) => {
      cb(performance.now());
      return 0;
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    document.body.style.overflow = "";
  });

  describe("Rendering", () => {
    it("renders drawer when isOpen is true", () => {
      render(<ModalDrawer {...defaultProps} />);
      expect(screen.getByText("Test Drawer")).toBeInTheDocument();
      expect(screen.getByText("Test Content")).toBeInTheDocument();
    });

    it("does not render drawer when isOpen is false", () => {
      render(<ModalDrawer {...defaultProps} isOpen={false} />);
      expect(screen.queryByText("Test Drawer")).not.toBeInTheDocument();
      expect(screen.queryByText("Test Content")).not.toBeInTheDocument();
    });

    it("renders drawer without title", () => {
      render(
        <ModalDrawer {...defaultProps} title={undefined}>
          <div>Test Content</div>
        </ModalDrawer>,
      );
      expect(screen.getByText("Test Content")).toBeInTheDocument();
      expect(screen.queryByText("Test Drawer")).not.toBeInTheDocument();
    });

    it("renders ReactNode title", () => {
      render(
        <ModalDrawer
          {...defaultProps}
          title={
            <span>
              <svg data-testid="icon" />
              Icon Title
            </span>
          }
        />,
      );
      expect(screen.getByText("Icon Title")).toBeInTheDocument();
      expect(screen.getByTestId("icon")).toBeInTheDocument();
    });

    it("renders footer when provided", () => {
      render(
        <ModalDrawer
          {...defaultProps}
          footer={<button>Footer Button</button>}
        />,
      );
      expect(
        screen.getByRole("button", { name: "Footer Button" }),
      ).toBeInTheDocument();
    });

    it("does not render footer when not provided", () => {
      const { container } = render(<ModalDrawer {...defaultProps} />);
      const footer = container.querySelector('[id*="footer"]');
      expect(footer).not.toBeInTheDocument();
    });

    it("renders close button by default", () => {
      const { container } = render(<ModalDrawer {...defaultProps} />);
      const closeButton = container.querySelector('[id*="close"]');
      expect(closeButton).toBeInTheDocument();
    });

    it("does not render close button when showCloseButton is false", () => {
      const { container } = render(
        <ModalDrawer {...defaultProps} showCloseButton={false} />,
      );
      const closeButton = container.querySelector('[id*="close"]');
      expect(closeButton).not.toBeInTheDocument();
    });

    it("renders back button by default", () => {
      render(<ModalDrawer {...defaultProps} />);
      const buttons = screen.getAllByRole("button");
      expect(buttons.length).toBeGreaterThan(0);
    });

    it("does not render back button when showBackButton is false", () => {
      render(<ModalDrawer {...defaultProps} showBackButton={false} />);
      const buttons = screen.getAllByRole("button");
      const backButtons = buttons.filter((btn) => {
        const svg = btn.querySelector("svg");
        return svg && svg.getAttribute("viewBox") === "0 0 24 24";
      });
      expect(backButtons.length).toBeLessThan(2);
    });
  });

  describe("Animation and Mount Lifecycle", () => {
    it("keeps content mounted during exit animation", async () => {
      vi.useFakeTimers();
      vi.stubGlobal("requestAnimationFrame", (cb: FrameRequestCallback) => {
        cb(performance.now());
        return 0;
      });

      const { rerender } = render(<ModalDrawer {...defaultProps} />);
      expect(screen.getByText("Test Content")).toBeInTheDocument();

      rerender(<ModalDrawer {...defaultProps} isOpen={false} />);
      // still mounted during 300ms exit animation
      expect(screen.getByText("Test Content")).toBeInTheDocument();

      // unmounts after animation completes
      act(() => {
        vi.advanceTimersByTime(300);
      });
      expect(screen.queryByText("Test Content")).not.toBeInTheDocument();

      vi.useRealTimers();
    });

    it("adds visible class after mount for enter animation", () => {
      const { container } = render(<ModalDrawer {...defaultProps} />);
      const panel = container.querySelector('[id*="panel"]');
      expect(panel?.className).toContain("translate-x-0");
    });
  });

  describe("Position and Size", () => {
    it("renders drawer on right by default", () => {
      const { container } = render(<ModalDrawer {...defaultProps} />);
      const containerDiv = container.querySelector('[id*="container"]');
      const positionDiv = containerDiv?.querySelector(".absolute.inset-y-0");
      expect(positionDiv?.className).toContain("right-0");
    });

    it("renders drawer on left when position is left", () => {
      const { container } = render(
        <ModalDrawer {...defaultProps} position="left" />,
      );
      const containerDiv = container.querySelector('[id*="container"]');
      const positionDiv = containerDiv?.querySelector(".absolute.inset-y-0");
      expect(positionDiv?.className).toContain("left-0");
    });

    it("applies correct size classes", () => {
      const { container: smContainer } = render(
        <ModalDrawer {...defaultProps} size="sm" />,
      );
      expect(smContainer.querySelector('[id*="panel"]')?.className).toContain(
        "max-w-sm",
      );

      const { container: mdContainer } = render(
        <ModalDrawer {...defaultProps} size="md" />,
      );
      expect(mdContainer.querySelector('[id*="panel"]')?.className).toContain(
        "max-w-md",
      );

      const { container: lgContainer } = render(
        <ModalDrawer {...defaultProps} size="lg" />,
      );
      expect(lgContainer.querySelector('[id*="panel"]')?.className).toContain(
        "max-w-lg",
      );
    });

    it("applies custom width when size is custom", () => {
      const { container } = render(
        <ModalDrawer {...defaultProps} size="custom" customWidth="500px" />,
      );
      const panel = container.querySelector('[id*="panel"]') as HTMLElement;
      expect(panel?.style.maxWidth).toBe("500px");
    });
  });

  describe("Interactions", () => {
    it("calls onClose when backdrop is clicked", async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();
      const { container } = render(
        <ModalDrawer {...defaultProps} onClose={onClose} />,
      );

      const backdrop = container.querySelector('[id*="backdrop"]');
      if (backdrop) {
        await user.click(backdrop);
      }

      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it("calls onClose when close button is clicked", async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();
      render(<ModalDrawer {...defaultProps} onClose={onClose} />);

      const buttons = screen.getAllByRole("button");
      const closeButton = buttons.find((btn) =>
        btn.getAttribute("id")?.includes("close"),
      );
      if (closeButton) {
        await user.click(closeButton);
      }

      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it("calls onClose when back button is clicked", async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();
      render(<ModalDrawer {...defaultProps} onClose={onClose} />);

      const buttons = screen.getAllByRole("button");
      await user.click(buttons[0]);

      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it("calls onClose when Escape key is pressed", async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();
      render(<ModalDrawer {...defaultProps} onClose={onClose} />);

      await user.keyboard("{Escape}");

      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it("does not call onClose when Escape is pressed and drawer is closed", async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();
      render(
        <ModalDrawer {...defaultProps} isOpen={false} onClose={onClose} />,
      );

      await user.keyboard("{Escape}");

      expect(onClose).not.toHaveBeenCalled();
    });

    it("does not close when clicking inside drawer content", async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();
      render(<ModalDrawer {...defaultProps} onClose={onClose} />);

      await user.click(screen.getByText("Test Content"));

      expect(onClose).not.toHaveBeenCalled();
    });
  });

  describe("Scroll lock", () => {
    it("locks body scroll when drawer is open", () => {
      render(<ModalDrawer {...defaultProps} />);
      expect(document.body.style.overflow).toBe("hidden");
    });

    it("restores body scroll when drawer is closed", () => {
      const { rerender } = render(<ModalDrawer {...defaultProps} />);
      expect(document.body.style.overflow).toBe("hidden");

      rerender(<ModalDrawer {...defaultProps} isOpen={false} />);
      expect(document.body.style.overflow).toBe("");
    });
  });

  describe("Accessibility", () => {
    it("has proper id attributes", () => {
      const { container } = render(
        <ModalDrawer {...defaultProps} id="test-drawer" />,
      );
      expect(container.querySelector("#test-drawer")).toBeInTheDocument();
      expect(
        container.querySelector("#test-drawer-backdrop"),
      ).toBeInTheDocument();
      expect(
        container.querySelector("#test-drawer-container"),
      ).toBeInTheDocument();
      expect(container.querySelector("#test-drawer-panel")).toBeInTheDocument();
      expect(
        container.querySelector("#test-drawer-header"),
      ).toBeInTheDocument();
      expect(
        container.querySelector("#test-drawer-content"),
      ).toBeInTheDocument();
    });

    it("generates default id when not provided", () => {
      const { container } = render(<ModalDrawer {...defaultProps} />);
      expect(container.querySelector("#modal-drawer")).toBeInTheDocument();
    });

    it("has accessible title in h4 tag", () => {
      render(<ModalDrawer {...defaultProps} />);
      const title = screen.getByText("Test Drawer");
      expect(title).toBeInTheDocument();
      expect(title.tagName).toBe("H4");
    });
  });

  describe("Edge Cases", () => {
    it("handles rapid open/close toggles", async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();
      const { rerender } = render(
        <ModalDrawer {...defaultProps} onClose={onClose} />,
      );

      rerender(
        <ModalDrawer {...defaultProps} isOpen={false} onClose={onClose} />,
      );
      rerender(<ModalDrawer {...defaultProps} onClose={onClose} />);

      await user.keyboard("{Escape}");
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it("handles multiple children", () => {
      render(
        <ModalDrawer {...defaultProps}>
          <div>Child 1</div>
          <div>Child 2</div>
        </ModalDrawer>,
      );
      expect(screen.getByText("Child 1")).toBeInTheDocument();
      expect(screen.getByText("Child 2")).toBeInTheDocument();
    });

    it("handles null children", () => {
      render(<ModalDrawer {...defaultProps}>{null}</ModalDrawer>);
      expect(screen.getByText("Test Drawer")).toBeInTheDocument();
    });
  });
});
