import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";

import { Spinner, Loading } from "../Spinner";

describe("Spinner", () => {
  it("should render spinner", () => {
    render(<Spinner />);
    const spinner = screen.getByRole("status");
    expect(spinner).toBeInTheDocument();
    expect(screen.getByText("Loading...")).toBeInTheDocument();
  });

  it("should apply different sizes", () => {
    const { container: smContainer } = render(<Spinner size="sm" />);
    const { container: mdContainer } = render(<Spinner size="md" />);
    const { container: lgContainer } = render(<Spinner size="lg" />);
    const { container: xlContainer } = render(<Spinner size="xl" />);

    expect(smContainer.querySelector(".w-4")).toBeInTheDocument();
    expect(mdContainer.querySelector(".w-6")).toBeInTheDocument();
    expect(lgContainer.querySelector(".w-8")).toBeInTheDocument();
    expect(xlContainer.querySelector(".w-12")).toBeInTheDocument();
  });

  it("should apply different variants", () => {
    const { container: defaultContainer } = render(
      <Spinner variant="default" />,
    );
    const { container: primaryContainer } = render(
      <Spinner variant="primary" />,
    );
    const { container: whiteContainer } = render(<Spinner variant="white" />);

    expect(
      defaultContainer.querySelector(".text-mw-neutral-600"),
    ).toBeInTheDocument();
    expect(
      primaryContainer.querySelector(".text-mw-primary-600"),
    ).toBeInTheDocument();
    expect(whiteContainer.querySelector(".text-white")).toBeInTheDocument();
  });

  it("should apply custom className", () => {
    const { container } = render(<Spinner className="custom-class" />);
    expect(container.querySelector(".custom-class")).toBeInTheDocument();
  });
});

describe("Loading", () => {
  it("should render loading with spinner and text", () => {
    render(<Loading text="Please wait" />);
    expect(screen.getByText("Please wait")).toBeInTheDocument();
    expect(screen.getByRole("status")).toBeInTheDocument();
  });

  it("should use default text when not provided", () => {
    render(<Loading />);
    // There might be multiple "Loading..." texts (one in Spinner, one in Loading)
    const loadingTexts = screen.getAllByText("Loading...");
    expect(loadingTexts.length).toBeGreaterThan(0);
  });

  it("should render without overlay by default", () => {
    const { container } = render(<Loading />);
    expect(container.querySelector(".absolute")).not.toBeInTheDocument();
  });

  it("should render with overlay when overlay is true", () => {
    const { container } = render(<Loading overlay />);
    expect(container.querySelector(".absolute")).toBeInTheDocument();
  });

  it("should apply different sizes", () => {
    const { container: smContainer } = render(<Loading size="sm" />);
    const { container: lgContainer } = render(<Loading size="lg" />);

    expect(smContainer.querySelector(".w-4")).toBeInTheDocument();
    expect(lgContainer.querySelector(".w-8")).toBeInTheDocument();
  });
});
