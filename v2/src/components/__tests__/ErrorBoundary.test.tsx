import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

import { ErrorBoundary } from "../ErrorBoundary";

const ThrowError: React.FC<{ shouldThrow?: boolean }> = ({
  shouldThrow = true,
}) => {
  if (shouldThrow) throw new Error("Test error");
  return <div>Child content</div>;
};

describe("ErrorBoundary", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("rendering", () => {
    it("renders children when no error occurs", () => {
      render(
        <ErrorBoundary>
          <div>Child content</div>
        </ErrorBoundary>,
      );
      expect(screen.getByText("Child content")).toBeInTheDocument();
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("renders default error UI when child throws and no fallback provided", () => {
      render(
        <ErrorBoundary>
          <ThrowError />
        </ErrorBoundary>,
      );
      expect(screen.getByRole("alert")).toBeInTheDocument();
      expect(screen.getByText("Something went wrong")).toBeInTheDocument();
      expect(
        screen.getByText(
          "An unexpected error occurred. Please try again or refresh the page.",
        ),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /try again/i }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /reload page/i }),
      ).toBeInTheDocument();
    });

    it("renders fallback when child throws and fallback is provided", () => {
      render(
        <ErrorBoundary
          fallback={<div data-testid="custom-fallback">Custom fallback</div>}
        >
          <ThrowError />
        </ErrorBoundary>,
      );
      expect(screen.getByTestId("custom-fallback")).toBeInTheDocument();
      expect(screen.getByText("Custom fallback")).toBeInTheDocument();
      expect(
        screen.queryByText("Something went wrong"),
      ).not.toBeInTheDocument();
    });

    it("default error UI has aria-live assertive", () => {
      render(
        <ErrorBoundary>
          <ThrowError />
        </ErrorBoundary>,
      );
      const alert = screen.getByRole("alert");
      expect(alert).toHaveAttribute("aria-live", "assertive");
    });
  });

  describe("onError callback", () => {
    it("calls onError with error and errorInfo when child throws", () => {
      const onError = vi.fn();
      render(
        <ErrorBoundary onError={onError}>
          <ThrowError />
        </ErrorBoundary>,
      );
      expect(onError).toHaveBeenCalledTimes(1);
      expect(onError).toHaveBeenCalledWith(
        expect.any(Error),
        expect.objectContaining({
          componentStack: expect.any(String),
        }),
      );
      expect(onError.mock.calls[0][0].message).toBe("Test error");
    });

    it("does not require onError to be provided", () => {
      expect(() =>
        render(
          <ErrorBoundary>
            <ThrowError />
          </ErrorBoundary>,
        ),
      ).not.toThrow();
    });
  });

  describe("Try again button", () => {
    it("shows error UI again when child throws again after Try again", async () => {
      const user = userEvent.setup();
      render(
        <ErrorBoundary>
          <ThrowError />
        </ErrorBoundary>,
      );
      await user.click(screen.getByRole("button", { name: /try again/i }));
      expect(screen.getByRole("alert")).toBeInTheDocument();
      expect(screen.getByText("Something went wrong")).toBeInTheDocument();
    });
  });

  describe("Reload page button", () => {
    it("calls window.location.reload when Reload page is clicked", async () => {
      const user = userEvent.setup();
      const reloadMock = vi.fn();
      Object.defineProperty(window, "location", {
        value: { ...window.location, reload: reloadMock },
        writable: true,
      });
      render(
        <ErrorBoundary>
          <ThrowError />
        </ErrorBoundary>,
      );
      await user.click(screen.getByRole("button", { name: /reload page/i }));
      expect(reloadMock).toHaveBeenCalledTimes(1);
    });
  });
});
