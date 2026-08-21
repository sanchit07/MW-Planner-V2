import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import CreateCampaignPage from "../CreateCampaignPage";

vi.mock("../CampaignWrapper", () => ({
  default: ({ initialStep }: { initialStep?: number }) => (
    <div data-testid="campaign-wrapper">
      CampaignWrapper initialStep={String(initialStep ?? "undefined")}
    </div>
  ),
}));

function renderCreateCampaignPage() {
  return render(
    <MemoryRouter>
      <CreateCampaignPage />
    </MemoryRouter>,
  );
}

describe("CreateCampaignPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders CampaignWrapper", () => {
    renderCreateCampaignPage();
    expect(screen.getByTestId("campaign-wrapper")).toBeInTheDocument();
  });

  it("passes initialStep 1 to CampaignWrapper", () => {
    renderCreateCampaignPage();
    expect(
      screen.getByText("CampaignWrapper initialStep=1"),
    ).toBeInTheDocument();
  });
});
