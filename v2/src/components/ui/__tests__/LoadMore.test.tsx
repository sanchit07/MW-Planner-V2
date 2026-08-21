import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";

import { LoadMore } from "../LoadMore";

describe("LoadMore", () => {
  it("should show loading state", () => {
    render(
      <LoadMore
        currentPage={1}
        totalPages={5}
        pageSize={10}
        totalItems={50}
        loading
      />,
    );
    expect(screen.getByText("Loading more results...")).toBeInTheDocument();
  });

  it("should show current results when has more pages", () => {
    render(
      <LoadMore
        currentPage={1}
        totalPages={5}
        pageSize={10}
        totalItems={50}
        loading={false}
      />,
    );
    expect(screen.getByText(/Showing 10 of 50 results/)).toBeInTheDocument();
  });

  it("should show all results when no more pages", () => {
    render(
      <LoadMore
        currentPage={5}
        totalPages={5}
        pageSize={10}
        totalItems={50}
        loading={false}
      />,
    );
    expect(screen.getByText("Showing all 50 results")).toBeInTheDocument();
  });

  it("should calculate currently showing correctly", () => {
    render(
      <LoadMore
        currentPage={3}
        totalPages={5}
        pageSize={20}
        totalItems={100}
        loading={false}
      />,
    );
    expect(screen.getByText(/Showing 60 of 100 results/)).toBeInTheDocument();
  });

  it("should apply custom className", () => {
    const { container } = render(
      <LoadMore
        currentPage={1}
        totalPages={5}
        pageSize={10}
        totalItems={50}
        className="custom-class"
      />,
    );
    expect(container.querySelector(".custom-class")).toBeInTheDocument();
  });

  it("should use provided id", () => {
    const { container } = render(
      <LoadMore
        currentPage={1}
        totalPages={5}
        pageSize={10}
        totalItems={50}
        id="custom-id"
      />,
    );
    expect(container.querySelector("#custom-id")).toBeInTheDocument();
  });
});
