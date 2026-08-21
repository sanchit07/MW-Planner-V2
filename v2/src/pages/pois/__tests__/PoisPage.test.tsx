import { render, screen } from "@testing-library/react";
import React from "react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import PoisPage from "../PoisPage";

vi.mock("@tolgee/react", () => ({
  T: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
}));

function renderPoisPage() {
  return render(
    <MemoryRouter>
      <PoisPage />
    </MemoryRouter>,
  );
}

describe("PoisPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders title POIs", () => {
    renderPoisPage();
    expect(
      screen.getByRole("heading", { name: "POIs", level: 1 }),
    ).toBeInTheDocument();
  });

  it("renders description", () => {
    renderPoisPage();
    expect(
      screen.getByText("Manage Points of Interest with unique identifiers."),
    ).toBeInTheDocument();
  });
});
