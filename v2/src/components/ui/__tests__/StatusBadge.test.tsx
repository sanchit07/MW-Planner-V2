import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";

import { StatusBadge } from "../StatusBadge";

describe("StatusBadge", () => {
  it("should render status badge with children", () => {
    render(<StatusBadge status="active">Active</StatusBadge>);
    expect(screen.getByText("Active")).toBeInTheDocument();
  });

  it("should apply correct styling for draft status", () => {
    const { container } = render(
      <StatusBadge status="draft">Draft</StatusBadge>,
    );
    expect(container.querySelector(".bg-mw-neutral-50")).toBeInTheDocument();
  });

  it("should apply correct styling for active status", () => {
    const { container } = render(
      <StatusBadge status="active">Active</StatusBadge>,
    );
    expect(
      container.querySelector(".bg-mw-light-green-50"),
    ).toBeInTheDocument();
  });

  it("should apply correct styling for approved status", () => {
    const { container } = render(
      <StatusBadge status="approved">Approved</StatusBadge>,
    );
    expect(container.querySelector(".bg-mw-success-100")).toBeInTheDocument();
  });

  it("should apply correct styling for rejected status", () => {
    const { container } = render(
      <StatusBadge status="rejected">Rejected</StatusBadge>,
    );
    expect(container.querySelector(".bg-mw-error-100")).toBeInTheDocument();
  });

  it("should show status indicator dot", () => {
    const { container } = render(
      <StatusBadge status="active">Active</StatusBadge>,
    );
    const dot = container.querySelector(".w-2.h-2.rounded-full");
    expect(dot).toBeInTheDocument();
  });

  it("should use default styling for unknown status", () => {
    const { container } = render(
      <StatusBadge status="unknown">Unknown</StatusBadge>,
    );
    expect(container.querySelector(".bg-mw-neutral-50")).toBeInTheDocument();
  });

  it("should handle all status types", () => {
    const statuses = [
      "draft",
      "planned",
      "reviewing",
      "negotiating",
      "pending",
      "approved",
      "active",
      "rejected",
      "completed",
      "archived",
    ];

    statuses.forEach((status) => {
      const { container } = render(
        <StatusBadge status={status}>{status}</StatusBadge>,
      );
      expect(container.querySelector(".rounded-full")).toBeInTheDocument();
    });
  });
});
