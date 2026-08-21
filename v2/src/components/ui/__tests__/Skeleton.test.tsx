import { render } from "@testing-library/react";
import { describe, it, expect } from "vitest";

import { Skeleton } from "../Skeleton";

describe("Skeleton", () => {
  it("should render skeleton", () => {
    const { container } = render(<Skeleton />);
    const skeleton = container.querySelector(".animate-pulse");
    expect(skeleton).toBeInTheDocument();
  });

  it("should apply custom className", () => {
    const { container } = render(<Skeleton className="custom-class" />);
    expect(container.querySelector(".custom-class")).toBeInTheDocument();
  });

  it("should have pulse animation", () => {
    const { container } = render(<Skeleton />);
    const skeleton = container.querySelector(".animate-pulse");
    expect(skeleton).toBeInTheDocument();
  });

  it("should have rounded corners", () => {
    const { container } = render(<Skeleton />);
    const skeleton = container.querySelector(".rounded-md");
    expect(skeleton).toBeInTheDocument();
  });
});
