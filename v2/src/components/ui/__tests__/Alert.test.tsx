import { render, screen } from "@testing-library/react";
import { Info } from "lucide-react";
import { describe, it, expect } from "vitest";

import Alert from "../Alert";

describe("Alert", () => {
  describe("rendering", () => {
    it("should render alert with children text", () => {
      render(<Alert>Test alert message</Alert>);
      expect(screen.getByText("Test alert message")).toBeInTheDocument();
    });

    it("should render with info variant by default", () => {
      const { container } = render(<Alert>Info message</Alert>);
      expect(container.querySelector(".bg-mw-info-50")).toBeInTheDocument();
      expect(
        container.querySelector(".border-mw-info-200"),
      ).toBeInTheDocument();
      expect(container.querySelector(".text-mw-info-700")).toBeInTheDocument();
    });
  });

  describe("variants", () => {
    it("should apply info variant styles", () => {
      const { container } = render(<Alert variant="info">Info alert</Alert>);
      expect(container.querySelector(".bg-mw-info-50")).toBeInTheDocument();
      expect(
        container.querySelector(".border-mw-info-200"),
      ).toBeInTheDocument();
    });

    it("should apply success variant styles", () => {
      const { container } = render(
        <Alert variant="success">Success alert</Alert>,
      );
      expect(container.querySelector(".bg-mw-success-50")).toBeInTheDocument();
      expect(
        container.querySelector(".border-mw-success-200"),
      ).toBeInTheDocument();
    });

    it("should apply warning variant styles", () => {
      const { container } = render(
        <Alert variant="warning">Warning alert</Alert>,
      );
      expect(container.querySelector(".bg-mw-warning-50")).toBeInTheDocument();
      expect(
        container.querySelector(".border-mw-warning-200"),
      ).toBeInTheDocument();
    });

    it("should apply error variant styles", () => {
      const { container } = render(<Alert variant="error">Error alert</Alert>);
      expect(container.querySelector(".bg-mw-error-50")).toBeInTheDocument();
      expect(
        container.querySelector(".border-mw-error-200"),
      ).toBeInTheDocument();
    });
  });

  describe("icon", () => {
    it("should show icon by default", () => {
      const { container } = render(<Alert>Message</Alert>);
      // Check for icon wrapper
      expect(container.querySelector("svg")).toBeInTheDocument();
    });

    it("should hide icon when showIcon is false", () => {
      const { container } = render(<Alert showIcon={false}>Message</Alert>);
      expect(container.querySelector("svg")).not.toBeInTheDocument();
    });

    it("should render custom icon when provided", () => {
      render(<Alert icon={<Info data-testid="custom-icon" />}>Message</Alert>);
      expect(screen.getByTestId("custom-icon")).toBeInTheDocument();
    });
  });

  describe("styling", () => {
    it("should apply custom className", () => {
      const { container } = render(
        <Alert className="custom-class">Message</Alert>,
      );
      expect(container.querySelector(".custom-class")).toBeInTheDocument();
    });

    it("should have proper base styles", () => {
      const { container } = render(<Alert>Message</Alert>);
      const alertDiv = container.querySelector("div");
      expect(alertDiv).toHaveClass("flex");
      expect(alertDiv).toHaveClass("items-start");
      expect(alertDiv).toHaveClass("gap-2");
      expect(alertDiv).toHaveClass("p-3");
      expect(alertDiv).toHaveClass("border");
      expect(alertDiv).toHaveClass("rounded-lg");
    });
  });

  describe("accessibility", () => {
    it("should render text content in a readable format", () => {
      render(<Alert>This is an important message</Alert>);
      expect(screen.getByText("This is an important message")).toBeVisible();
    });

    it("should maintain text hierarchy with proper sizing", () => {
      const { container } = render(<Alert>Message</Alert>);
      const textContainer = container.querySelector(".text-xs");
      expect(textContainer).toBeInTheDocument();
      expect(textContainer).toHaveClass("leading-tight");
    });
  });
});
