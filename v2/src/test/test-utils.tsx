import { render } from "@testing-library/react";
import { screen } from "@testing-library/react";
import React from "react";
import { MemoryRouter } from "react-router-dom";
import { expect, it } from "vitest";

export function renderWithRouter(ui: React.ReactElement) {
  return render(<MemoryRouter>{ui}</MemoryRouter>);
}

export type SimplePageTestConfig = {
  title: string;
  description: string;
  titleKey: string;
  descriptionKey: string;
};

export function runSimplePageTests(
  renderPage: () => ReturnType<typeof render>,
  config: SimplePageTestConfig,
) {
  const { title, description, titleKey, descriptionKey } = config;

  it(`renders title ${title}`, () => {
    renderPage();
    expect(
      screen.getByRole("heading", { name: title, level: 1 }),
    ).toBeInTheDocument();
  });

  it("renders description", () => {
    renderPage();
    expect(screen.getByText(description)).toBeInTheDocument();
  });

  it("renders translation keys for title and description", () => {
    const { container } = renderPage();
    expect(
      container.querySelector(`[data-key="${titleKey}"]`),
    ).toBeInTheDocument();
    expect(
      container.querySelector(`[data-key="${descriptionKey}"]`),
    ).toBeInTheDocument();
  });
}
