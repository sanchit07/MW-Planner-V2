import { render, screen } from "@testing-library/react";
import React from "react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import TagsPage from "../TagsPage";

vi.mock("@tolgee/react", () => ({
  T: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
}));

function renderTagsPage() {
  return render(
    <MemoryRouter>
      <TagsPage />
    </MemoryRouter>,
  );
}

describe("TagsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders title Tags", () => {
    renderTagsPage();
    expect(
      screen.getByRole("heading", { name: "Tags", level: 1 }),
    ).toBeInTheDocument();
  });

  it("renders description", () => {
    renderTagsPage();
    expect(
      screen.getByText("Manage and organize content with tags."),
    ).toBeInTheDocument();
  });
});
