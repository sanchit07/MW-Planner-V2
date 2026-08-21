import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, vi } from "vitest";

import { TooltipHeader, SelectionCell, ExpandCell } from "../cell-renderers";

// Mock Tooltip component
vi.mock("@components/ui/Tooltip", () => ({
  Tooltip: ({
    children,
    content,
  }: {
    children: React.ReactNode;
    content: string;
  }) => (
    <div data-testid="tooltip" data-content={content}>
      {children}
    </div>
  ),
}));

// Mock Button component
vi.mock("@components/ui/Button", () => ({
  Button: ({
    children,
    onClick,
    variant,
    size,
    className,
    "data-testid": testId,
  }: {
    children: React.ReactNode;
    onClick?: () => void;
    variant?: string;
    size?: string;
    className?: string;
    "data-testid"?: string;
  }) => (
    <button
      onClick={onClick}
      data-variant={variant}
      data-size={size}
      className={className}
      data-testid={testId}
    >
      {children}
    </button>
  ),
}));

describe("cell-renderers", () => {
  describe("TooltipHeader", () => {
    it("renders label without tooltip", () => {
      render(<TooltipHeader label="Test Label" />);
      expect(screen.getByText("Test Label")).toBeInTheDocument();
      expect(screen.queryByTestId("tooltip")).not.toBeInTheDocument();
    });

    it("renders label with tooltip", () => {
      render(<TooltipHeader label="Test Label" tooltip="Tooltip text" />);
      expect(screen.getByText("Test Label")).toBeInTheDocument();
      const tooltip = screen.getByTestId("tooltip");
      expect(tooltip).toBeInTheDocument();
      expect(tooltip).toHaveAttribute("data-content", "Tooltip text");
    });

    it("applies default left alignment", () => {
      const { container } = render(<TooltipHeader label="Test" />);
      const div = container.firstChild as HTMLElement;
      expect(div.className).toContain("text-left");
    });

    it("applies center alignment", () => {
      const { container } = render(
        <TooltipHeader label="Test" align="center" />,
      );
      const div = container.firstChild as HTMLElement;
      expect(div.className).toContain("text-center");
    });

    it("applies right alignment", () => {
      const { container } = render(
        <TooltipHeader label="Test" align="right" />,
      );
      const div = container.firstChild as HTMLElement;
      expect(div.className).toContain("text-right");
    });

    it("applies custom className", () => {
      const { container } = render(
        <TooltipHeader label="Test" className="custom-class" />,
      );
      const div = container.firstChild as HTMLElement;
      expect(div.className).toContain("custom-class");
    });

    it("renders tooltip with cursor-help class", () => {
      render(<TooltipHeader label="Test" tooltip="Tooltip" />);
      const span = screen.getByText("Test");
      expect(span.className).toContain("cursor-help");
    });
  });

  describe("SelectionCell", () => {
    it("renders checkbox with checked state", () => {
      const onChange = vi.fn();
      render(<SelectionCell checked={true} onChange={onChange} />);
      const checkbox = screen.getByRole("checkbox");
      expect(checkbox).toBeChecked();
    });

    it("renders checkbox with unchecked state", () => {
      const onChange = vi.fn();
      render(<SelectionCell checked={false} onChange={onChange} />);
      const checkbox = screen.getByRole("checkbox");
      expect(checkbox).not.toBeChecked();
    });

    it("calls onChange when checkbox is clicked", async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      render(<SelectionCell checked={false} onChange={onChange} />);
      const checkbox = screen.getByRole("checkbox");
      await user.click(checkbox);
      expect(onChange).toHaveBeenCalledWith(true);
    });

    it("sets indeterminate state when provided", () => {
      const onChange = vi.fn();
      const { container } = render(
        <SelectionCell
          checked={false}
          indeterminate={true}
          onChange={onChange}
        />,
      );
      const checkbox = container.querySelector(
        'input[type="checkbox"]',
      ) as HTMLInputElement;
      expect(checkbox.indeterminate).toBe(true);
    });

    it("does not set indeterminate when false", () => {
      const onChange = vi.fn();
      const { container } = render(
        <SelectionCell
          checked={false}
          indeterminate={false}
          onChange={onChange}
        />,
      );
      const checkbox = container.querySelector(
        'input[type="checkbox"]',
      ) as HTMLInputElement;
      expect(checkbox.indeterminate).toBe(false);
    });

    it("renders disabled checkbox", () => {
      const onChange = vi.fn();
      render(
        <SelectionCell checked={false} disabled={true} onChange={onChange} />,
      );
      const checkbox = screen.getByRole("checkbox");
      expect(checkbox).toBeDisabled();
    });

    it("applies testId when provided", () => {
      const onChange = vi.fn();
      render(
        <SelectionCell
          checked={false}
          onChange={onChange}
          testId="test-checkbox"
        />,
      );
      const checkbox = screen.getByTestId("test-checkbox");
      expect(checkbox).toBeInTheDocument();
    });

    it("applies cursor-pointer class", () => {
      const onChange = vi.fn();
      const { container } = render(
        <SelectionCell checked={false} onChange={onChange} />,
      );
      const checkbox = container.querySelector(
        'input[type="checkbox"]',
      ) as HTMLElement;
      expect(checkbox.className).toContain("cursor-pointer");
    });
  });

  describe("ExpandCell", () => {
    it("renders expanded state with ChevronDown", () => {
      const onToggle = vi.fn();
      render(<ExpandCell isExpanded={true} onToggle={onToggle} />);
      // Check for the button
      const button = screen.getByRole("button");
      expect(button).toBeInTheDocument();
    });

    it("renders collapsed state with ChevronRight", () => {
      const onToggle = vi.fn();
      render(<ExpandCell isExpanded={false} onToggle={onToggle} />);
      const button = screen.getByRole("button");
      expect(button).toBeInTheDocument();
    });

    it("calls onToggle when button is clicked", async () => {
      const user = userEvent.setup();
      const onToggle = vi.fn();
      render(<ExpandCell isExpanded={false} onToggle={onToggle} />);
      const button = screen.getByRole("button");
      await user.click(button);
      expect(onToggle).toHaveBeenCalledTimes(1);
    });

    it("applies correct button props", () => {
      const onToggle = vi.fn();
      render(<ExpandCell isExpanded={false} onToggle={onToggle} />);
      const button = screen.getByRole("button");
      expect(button).toHaveAttribute("data-variant", "ghost");
      expect(button).toHaveAttribute("data-size", "sm");
      expect(button.className).toContain("h-6");
      expect(button.className).toContain("w-6");
    });

    it("applies testId when provided", () => {
      const onToggle = vi.fn();
      render(
        <ExpandCell
          isExpanded={false}
          onToggle={onToggle}
          testId="expand-btn"
        />,
      );
      const button = screen.getByTestId("expand-btn");
      expect(button).toBeInTheDocument();
    });
  });
});
