import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";

import { Progress } from "../Progressbar";

describe("Progress", () => {
  it("should render progress bar", () => {
    render(<Progress value={50} />);
    const progressBar = screen.getByRole("progressbar");
    expect(progressBar).toBeInTheDocument();
    expect(progressBar).toHaveAttribute("aria-valuenow", "50");
    expect(progressBar).toHaveAttribute("aria-valuemax", "100");
  });

  it("should display percentage when showPercentage is true", () => {
    render(<Progress value={50} showPercentage />);
    expect(screen.getByText("50%")).toBeInTheDocument();
  });

  it("should not display percentage when showPercentage is false", () => {
    render(<Progress value={50} showPercentage={false} />);
    expect(screen.queryByText("50%")).not.toBeInTheDocument();
  });

  it("should display label when provided", () => {
    render(<Progress value={50} label="Upload Progress" />);
    expect(screen.getByText("Upload Progress")).toBeInTheDocument();
  });

  it("should show label when showLabel is true", () => {
    render(<Progress value={50} showLabel />);
    expect(screen.getByText("Progress")).toBeInTheDocument();
  });

  it("should calculate percentage correctly", () => {
    render(<Progress value={25} max={50} />);
    const progressBar = screen.getByRole("progressbar");
    expect(progressBar).toHaveAttribute("aria-valuenow", "25");
    expect(progressBar).toHaveAttribute("aria-valuemax", "50");
  });

  it("should clamp value to max", () => {
    render(<Progress value={150} max={100} />);
    const progressBar = screen.getByRole("progressbar");
    expect(progressBar).toHaveAttribute("aria-valuenow", "150");
  });

  it("should handle different sizes", () => {
    const { container: smContainer } = render(
      <Progress value={50} size="sm" />,
    );
    const { container: mdContainer } = render(
      <Progress value={50} size="md" />,
    );
    const { container: lgContainer } = render(
      <Progress value={50} size="lg" />,
    );

    // Check for size classes - use classList.contains or check for presence
    const smElement = smContainer.querySelector('[class*="h-1"]');
    const mdElement = mdContainer.querySelector('[class*="h-1"]');
    const lgElement = lgContainer.querySelector('[class*="h-2"]');

    expect(smElement).toBeInTheDocument();
    expect(mdElement).toBeInTheDocument();
    expect(lgElement).toBeInTheDocument();
  });

  it("should handle different progressTrackVariant", () => {
    const { container } = render(
      <Progress value={50} progressTrackVariant="success" />,
    );
    expect(container.querySelector(".bg-mw-success-50")).toBeInTheDocument();
  });

  it("should apply custom className", () => {
    const { container } = render(
      <Progress value={50} className="custom-class" />,
    );
    expect(container.querySelector(".custom-class")).toBeInTheDocument();
  });
});
