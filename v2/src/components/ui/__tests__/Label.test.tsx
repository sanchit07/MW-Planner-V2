import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

import { Label } from "../Label";

vi.mock("../Tooltip", () => ({
  Tooltip: ({
    content,
    children,
  }: {
    content: React.ReactNode;
    children: React.ReactNode;
  }) => (
    <span data-testid="tooltip-mock" data-content={String(content)}>
      {children}
    </span>
  ),
}));

describe("Label", () => {
  describe("rendering", () => {
    it("renders label with children", () => {
      render(<Label>Username</Label>);
      expect(screen.getByText("Username")).toBeInTheDocument();
    });

    it("renders label element with correct tag", () => {
      render(<Label>Email</Label>);
      const label = screen.getByText("Email").closest("label");
      expect(label).toBeInTheDocument();
    });

    it("wraps content in div with inline-flex layout class", () => {
      const { container } = render(<Label>Label</Label>);
      const wrapper = container.firstChild as HTMLElement;
      expect(wrapper).toHaveClass(
        "inline-flex",
        "justify-start",
        "items-center",
        "gap-1",
      );
    });
  });

  describe("required prop", () => {
    it("does not render optional span when required is true (default)", () => {
      const { container } = render(<Label>Required field</Label>);
      const optionalSpan = container.querySelector("span.text-mw-neutral-500");
      expect(optionalSpan).not.toBeInTheDocument();
    });

    it("renders optional span when required is false", () => {
      const { container } = render(
        <Label required={false}>Optional field</Label>,
      );
      const optionalSpan = container.querySelector("span.text-mw-neutral-500");
      expect(optionalSpan).toBeInTheDocument();
    });
  });

  describe("info prop", () => {
    it("does not render Tooltip or info icon when info is not provided", () => {
      render(<Label>No info</Label>);
      expect(screen.queryByTestId("tooltip-mock")).not.toBeInTheDocument();
    });

    it("renders Tooltip with info content when info is provided", () => {
      render(<Label info="Enter your username">Username</Label>);
      const tooltip = screen.getByTestId("tooltip-mock");
      expect(tooltip).toBeInTheDocument();
      expect(tooltip).toHaveAttribute("data-content", "Enter your username");
    });

    // it("renders info icon (child of Tooltip) when info is provided", () => {
    //   const tooltip = screen.getByTestId("tooltip-mock");
    //   expect(tooltip.querySelector("svg")).toBeInTheDocument();
    // });
  });

  describe("className", () => {
    it("applies custom className to label element", () => {
      const { container } = render(
        <Label className="custom-class">Label</Label>,
      );
      const label = container.querySelector("label");
      expect(label?.className).toContain("custom-class");
    });

    it("merges base classes with custom className", () => {
      const { container } = render(
        <Label className="custom-class">Label</Label>,
      );
      const label = container.querySelector("label");
      expect(label?.className).toContain("text-sm");
      expect(label?.className).toContain("custom-class");
    });
  });

  describe("label attributes", () => {
    it("passes htmlFor to label", () => {
      render(<Label htmlFor="input-id">Label</Label>);
      const label = screen.getByText("Label").closest("label");
      expect(label).toHaveAttribute("for", "input-id");
    });

    it("passes data attributes through", () => {
      render(
        <Label data-testid="label-id" data-cy="label">
          Label
        </Label>,
      );
      expect(screen.getByTestId("label-id")).toBeInTheDocument();
      expect(screen.getByTestId("label-id")).toHaveAttribute(
        "data-cy",
        "label",
      );
    });

    it("passes id to label", () => {
      render(<Label id="label-id">Label</Label>);
      const label = screen.getByText("Label").closest("label");
      expect(label).toHaveAttribute("id", "label-id");
    });
  });

  describe("accessibility", () => {
    it("label is associated with form control via htmlFor", () => {
      render(
        <>
          <Label htmlFor="email-input">Email</Label>
          <input id="email-input" type="email" />
        </>,
      );
      const input = screen.getByRole("textbox", { name: /email/i });
      expect(input).toBeInTheDocument();
    });
  });
});
