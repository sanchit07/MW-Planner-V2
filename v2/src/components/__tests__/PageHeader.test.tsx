import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";

import { PageHeader } from "../PageHeader";

describe("PageHeader", () => {
  it("should render title", () => {
    render(<PageHeader title="Test Page" />);
    expect(screen.getByText("Test Page")).toBeInTheDocument();
  });

  it("should render description when provided", () => {
    render(<PageHeader title="Test Page" description="Page description" />);
    expect(screen.getByText("Page description")).toBeInTheDocument();
  });

  it("should render actions when provided", () => {
    render(
      <PageHeader title="Test Page" actions={<button>Action Button</button>} />,
    );
    expect(screen.getByText("Action Button")).toBeInTheDocument();
  });

  it("should render leftAction when provided", () => {
    render(
      <PageHeader
        title="Test Page"
        leftAction={<button>Left Action</button>}
      />,
    );
    expect(screen.getByText("Left Action")).toBeInTheDocument();
  });

  it("should generate id from title", () => {
    const { container } = render(<PageHeader title="Test Page" />);
    const header = container.querySelector("#page-header-test-page");
    expect(header).toBeInTheDocument();
  });

  it("should handle title with spaces", () => {
    const { container } = render(<PageHeader title="My Test Page" />);
    const header = container.querySelector("#page-header-my-test-page");
    expect(header).toBeInTheDocument();
  });
});
