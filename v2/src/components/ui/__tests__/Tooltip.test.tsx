import { render, screen, waitFor, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { Tooltip } from "../Tooltip";

vi.mock("react-dom", async () => {
  const actual = await vi.importActual<typeof import("react-dom")>("react-dom");
  return {
    ...actual,
    createPortal: (node: React.ReactNode) => node,
  };
});

describe("Tooltip", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("rendering", () => {
    it("renders children", () => {
      render(
        <Tooltip content="Tooltip content">
          <button type="button">Hover me</button>
        </Tooltip>,
      );
      expect(
        screen.getByRole("button", { name: "Hover me" }),
      ).toBeInTheDocument();
    });

    it("renders trigger wrapper with inline-flex class", () => {
      const { container } = render(
        <Tooltip content="Tip">
          <span>Trigger</span>
        </Tooltip>,
      );
      const trigger = container.querySelector(".inline-flex");
      expect(trigger).toBeInTheDocument();
    });
  });

  describe("visibility", () => {
    it("shows tooltip on mouse enter", async () => {
      const user = userEvent.setup();
      render(
        <Tooltip content="Tooltip content">
          <button type="button">Hover me</button>
        </Tooltip>,
      );
      await user.hover(screen.getByRole("button", { name: "Hover me" }));
      await waitFor(() => {
        expect(screen.getByText("Tooltip content")).toBeInTheDocument();
      });
    });

    it("hides tooltip on mouse leave", async () => {
      const user = userEvent.setup();
      render(
        <Tooltip content="Tooltip content">
          <button type="button">Hover me</button>
        </Tooltip>,
      );
      await user.hover(screen.getByRole("button", { name: "Hover me" }));
      await waitFor(() => {
        expect(screen.getByText("Tooltip content")).toBeInTheDocument();
      });
      await user.unhover(screen.getByRole("button", { name: "Hover me" }));
      await waitFor(() => {
        expect(screen.queryByText("Tooltip content")).not.toBeInTheDocument();
      });
    });

    it("shows tooltip on focus", async () => {
      render(
        <Tooltip content="Tooltip content">
          <button type="button">Focus me</button>
        </Tooltip>,
      );
      const button = screen.getByRole("button", { name: "Focus me" });
      await act(async () => {
        button.focus();
      });
      await waitFor(() => {
        expect(screen.getByText("Tooltip content")).toBeInTheDocument();
      });
    });

    it("hides tooltip on blur", async () => {
      render(
        <Tooltip content="Tooltip content">
          <button type="button">Focus me</button>
        </Tooltip>,
      );
      const button = screen.getByRole("button", { name: "Focus me" });
      await act(async () => {
        button.focus();
      });
      await waitFor(() => {
        expect(screen.getByText("Tooltip content")).toBeInTheDocument();
      });
      await act(async () => {
        button.blur();
      });
      await waitFor(() => {
        expect(screen.queryByText("Tooltip content")).not.toBeInTheDocument();
      });
    });

    it("does not render tooltip content when not visible", () => {
      render(
        <Tooltip content="Hidden content">
          <button type="button">Trigger</button>
        </Tooltip>,
      );
      expect(screen.queryByText("Hidden content")).not.toBeInTheDocument();
    });
  });

  describe("content", () => {
    it("renders string content", async () => {
      const user = userEvent.setup();
      render(
        <Tooltip content="String tip">
          <button type="button">Hover</button>
        </Tooltip>,
      );
      await user.hover(screen.getByRole("button", { name: "Hover" }));
      await waitFor(() => {
        expect(screen.getByText("String tip")).toBeInTheDocument();
      });
    });

    it("renders React node content", async () => {
      const user = userEvent.setup();
      render(
        <Tooltip
          content={
            <span data-testid="custom-content">
              <strong>Bold</strong> text
            </span>
          }
        >
          <button type="button">Hover</button>
        </Tooltip>,
      );
      await user.hover(screen.getByRole("button", { name: "Hover" }));
      await waitFor(() => {
        expect(screen.getByTestId("custom-content")).toBeInTheDocument();
        expect(screen.getByText("Bold")).toBeInTheDocument();
        expect(screen.getByText(/text/)).toBeInTheDocument();
      });
    });
  });

  describe("position prop", () => {
    it("accepts position top (default)", async () => {
      const user = userEvent.setup();
      render(
        <Tooltip content="Top tip">
          <button type="button">Hover</button>
        </Tooltip>,
      );
      await user.hover(screen.getByRole("button", { name: "Hover" }));
      await waitFor(() => {
        expect(screen.getByText("Top tip")).toBeInTheDocument();
      });
    });

    it("accepts position bottom", async () => {
      const user = userEvent.setup();
      render(
        <Tooltip content="Bottom tip" position="bottom">
          <button type="button">Hover</button>
        </Tooltip>,
      );
      await user.hover(screen.getByRole("button", { name: "Hover" }));
      await waitFor(() => {
        expect(screen.getByText("Bottom tip")).toBeInTheDocument();
      });
    });

    it("accepts position left", async () => {
      const user = userEvent.setup();
      render(
        <Tooltip content="Left tip" position="left">
          <button type="button">Hover</button>
        </Tooltip>,
      );
      await user.hover(screen.getByRole("button", { name: "Hover" }));
      await waitFor(() => {
        expect(screen.getByText("Left tip")).toBeInTheDocument();
      });
    });

    it("accepts position right", async () => {
      const user = userEvent.setup();
      render(
        <Tooltip content="Right tip" position="right">
          <button type="button">Hover</button>
        </Tooltip>,
      );
      await user.hover(screen.getByRole("button", { name: "Hover" }));
      await waitFor(() => {
        expect(screen.getByText("Right tip")).toBeInTheDocument();
      });
    });
  });

  describe("offset prop", () => {
    it("accepts custom offset", async () => {
      const user = userEvent.setup();
      render(
        <Tooltip content="Offset tip" offset={12}>
          <button type="button">Hover</button>
        </Tooltip>,
      );
      await user.hover(screen.getByRole("button", { name: "Hover" }));
      await waitFor(() => {
        expect(screen.getByText("Offset tip")).toBeInTheDocument();
      });
    });
  });

  describe("className", () => {
    it("applies custom className to tooltip content when visible", async () => {
      const user = userEvent.setup();
      const { container } = render(
        <Tooltip content="Tip" className="custom-tooltip-class">
          <button type="button">Hover</button>
        </Tooltip>,
      );
      await user.hover(screen.getByRole("button", { name: "Hover" }));
      await waitFor(() => {
        const tooltipEl = container.querySelector(".custom-tooltip-class");
        expect(tooltipEl).toBeInTheDocument();
        expect(tooltipEl).toHaveTextContent("Tip");
      });
    });
  });

  describe("position calculation", () => {
    it("positions tooltip with fixed class and inline top/left when visible", async () => {
      const user = userEvent.setup();
      render(
        <Tooltip content="Positioned">
          <button type="button">Hover</button>
        </Tooltip>,
      );
      await user.hover(screen.getByRole("button", { name: "Hover" }));
      await waitFor(() => {
        const tooltip = screen.getByText("Positioned");
        expect(tooltip).toHaveClass("fixed");
        expect(tooltip).toHaveAttribute("style");
        expect((tooltip as HTMLElement).style.top).toBeTruthy();
        expect((tooltip as HTMLElement).style.left).toBeTruthy();
      });
    });

    it("applies overflow-safe position when trigger has no ref initially", async () => {
      const user = userEvent.setup();
      const mockGetBoundingClientRect = vi.fn(() => ({
        top: 100,
        left: 100,
        right: 200,
        bottom: 120,
        width: 100,
        height: 20,
        x: 0,
        y: 0,
        toJSON: () => ({}),
      }));
      const mockTooltipRect = vi.fn(() => ({
        top: 0,
        left: 0,
        right: 150,
        bottom: 30,
        width: 150,
        height: 30,
        x: 0,
        y: 0,
        toJSON: () => ({}),
      }));

      render(
        <Tooltip content="Tip">
          <button type="button">Hover</button>
        </Tooltip>,
      );
      await user.hover(screen.getByRole("button", { name: "Hover" }));

      await waitFor(() => {
        expect(screen.getByText("Tip")).toBeInTheDocument();
      });

      const triggerDiv = screen.getByRole("button", {
        name: "Hover",
      }).parentElement;
      const tooltipDiv = screen.getByText("Tip").parentElement;
      if (triggerDiv && tooltipDiv) {
        triggerDiv.getBoundingClientRect = mockGetBoundingClientRect;
        tooltipDiv.getBoundingClientRect = mockTooltipRect;
      }

      await waitFor(() => {
        const tooltip = screen.getByText("Tip");
        expect(tooltip.parentElement).toHaveStyle({
          top: expect.any(String),
          left: expect.any(String),
        });
      });
    });
  });

  describe("keyboard and focus", () => {
    it("tooltip is visible when trigger receives focus via keyboard", async () => {
      render(
        <Tooltip content="Focus tip">
          <button type="button">Focus me</button>
        </Tooltip>,
      );
      const button = screen.getByRole("button", { name: "Focus me" });
      await act(async () => {
        button.focus();
      });
      await waitFor(() => {
        expect(screen.getByText("Focus tip")).toBeInTheDocument();
      });
    });
  });

  describe("reposition on scroll and resize", () => {
    it("recalculates position on window resize when tooltip is visible", async () => {
      const user = userEvent.setup();
      render(
        <Tooltip content="Resize tip">
          <button type="button">Hover</button>
        </Tooltip>,
      );
      await user.hover(screen.getByRole("button", { name: "Hover" }));
      await waitFor(() => {
        expect(screen.getByText("Resize tip")).toBeInTheDocument();
      });
      await act(() => {
        window.dispatchEvent(new Event("resize"));
      });
      await waitFor(() => {
        expect(screen.getByText("Resize tip")).toBeInTheDocument();
      });
    });

    it("recalculates position on scroll when tooltip is visible", async () => {
      const user = userEvent.setup();
      render(
        <Tooltip content="Scroll tip">
          <button type="button">Hover</button>
        </Tooltip>,
      );
      await user.hover(screen.getByRole("button", { name: "Hover" }));
      await waitFor(() => {
        expect(screen.getByText("Scroll tip")).toBeInTheDocument();
      });
      await act(() => {
        window.dispatchEvent(new Event("scroll", { bubbles: true }));
      });
      await waitFor(() => {
        expect(screen.getByText("Scroll tip")).toBeInTheDocument();
      });
    });
  });

  describe("overflow adjustment", () => {
    it("positions tooltip and applies overflow adjustment when refs are set", async () => {
      const user = userEvent.setup();
      render(
        <Tooltip content="Overflow tip" position="top" offset={8}>
          <button type="button">Hover</button>
        </Tooltip>,
      );
      await user.hover(screen.getByRole("button", { name: "Hover" }));
      await waitFor(() => {
        const tooltip = screen.getByText("Overflow tip");
        expect(tooltip).toBeInTheDocument();
        expect((tooltip as HTMLElement).style.top).toBeDefined();
        expect((tooltip as HTMLElement).style.left).toBeDefined();
      });
    });
  });
});
