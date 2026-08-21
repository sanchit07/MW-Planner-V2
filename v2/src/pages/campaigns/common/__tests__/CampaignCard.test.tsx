import { FALLBACK_VALUES } from "@constants/campaign.constants";
import { configureStore } from "@reduxjs/toolkit";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import type { CampaignDisplay } from "../../../../types/campaign-display.types";
import { CampaignCard } from "../CampaignCard";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock("@components/ui/Progressbar", () => ({
  Progress: ({ value }: { value: number }) => (
    <div data-testid="budget-bar" data-value={value} />
  ),
}));

vi.mock("../../components/CampaignActionsDropdownContent", () => ({
  CampaignActionsDropdownContent: () => (
    <div data-testid="campaign-actions-dropdown">Actions</div>
  ),
}));

const store = configureStore({
  reducer: { campaign: () => ({}), campaignsUI: () => ({}) },
});

const mockCampaign: CampaignDisplay = {
  id: "camp-1",
  campaignName: "Test Campaign",
  userName: "User",
  brand: "Brand A",
  status: "DRAFT",
  statusColor: "draft",
  daysLeft: FALLBACK_VALUES.DAYS_LEFT,
  budget: "1000",
  totalCost: "1200",
  startDate: "2025-01-01",
  endDate: "2025-01-31",
  impressions: 50000,
  reach: 10000,
  sov: 10.5,
  plannedSot: 50,
  totalSot: 100,
  inventory: 5,
  goals: {
    typeName: "Reach",
    goalType: "REACH",
    targetName: "",
    targetValue: 10000,
  },
  companyName: "Company",
};

function renderCampaignCard(
  props: Partial<React.ComponentProps<typeof CampaignCard>> = {},
) {
  return render(
    <Provider store={store}>
      <MemoryRouter>
        <CampaignCard campaign={mockCampaign} {...props} />
      </MemoryRouter>
    </Provider>,
  );
}

describe("CampaignCard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("rendering", () => {
    it("renders campaign name", () => {
      renderCampaignCard();
      expect(screen.getByText("Test Campaign")).toBeInTheDocument();
    });

    it("renders the plan number when present", () => {
      renderCampaignCard({
        campaign: { ...mockCampaign, planNumber: "PLN-0001" },
      });
      expect(screen.getByText(/ID: PLN-0001/)).toBeInTheDocument();
    });

    it("does not render the ID label when planNumber is missing", () => {
      renderCampaignCard();
      expect(screen.queryByText(/viewCampaign\.ID:/)).not.toBeInTheDocument();
    });

    it("renders status badge with campaign status", () => {
      renderCampaignCard();
      expect(
        screen.getByText("campaignsList.status.DRAFT"),
      ).toBeInTheDocument();
    });

    it("renders flight dates", () => {
      renderCampaignCard();
      expect(screen.getByText(/card\.brand/)).toBeInTheDocument();
      expect(screen.getByText("Brand A")).toBeInTheDocument();
    });

    it("renders View Details button", () => {
      renderCampaignCard();
      expect(
        screen.getByRole("button", { name: /card\.view_details/i }),
      ).toBeInTheDocument();
    });

    it("renders CampaignActionsDropdownContent", () => {
      renderCampaignCard();
      expect(
        screen.getByTestId("campaign-actions-dropdown"),
      ).toBeInTheDocument();
    });

    it("renders daysLeft when not fallback value", () => {
      renderCampaignCard({
        campaign: { ...mockCampaign, daysLeft: "5" },
      });
      expect(
        screen.getByText("campaignsList.durationDays"),
      ).toBeInTheDocument();
    });

    it("does not render daysLeft section when daysLeft is fallback", () => {
      renderCampaignCard();
      const daysLeftEl = screen.queryByText(FALLBACK_VALUES.DAYS_LEFT);
      expect(daysLeftEl).not.toBeInTheDocument();
    });

    it("renders SOT as hours when plannedSot exists", () => {
      renderCampaignCard({
        campaign: { ...mockCampaign, plannedSot: 25, totalSot: 100 },
      });
      expect(screen.getByText(/25\.00\s*H/)).toBeInTheDocument();
    });

    it("renders 0.00 H for SOT when plannedSot/totalSot missing", () => {
      renderCampaignCard({
        campaign: { ...mockCampaign, plannedSot: 0, totalSot: 0 },
      });
      expect(screen.getByText(/0\.00\s*H/)).toBeInTheDocument();
    });

    it("applies border-l-4 class for Planned status", () => {
      const { container } = renderCampaignCard({
        campaign: { ...mockCampaign, status: "Planned" },
      });
      expect((container.firstChild as HTMLElement)?.className).toContain(
        "border-l-4",
      );
    });

    it("renders budget utilization bar when rawBudget > 0", () => {
      renderCampaignCard({
        campaign: { ...mockCampaign, rawBudget: 1000, rawTotalCost: 500 },
      });
      expect(screen.getByTestId("budget-bar")).toBeInTheDocument();
    });

    it("does not render budget utilization bar when rawBudget is 0", () => {
      renderCampaignCard({
        campaign: { ...mockCampaign, rawBudget: 0, rawTotalCost: 0 },
      });
      expect(screen.queryByTestId("budget-bar")).not.toBeInTheDocument();
    });

    it("does not render budget utilization bar when rawBudget is absent", () => {
      renderCampaignCard({
        campaign: {
          ...mockCampaign,
          rawBudget: undefined,
          rawTotalCost: undefined,
        },
      });
      expect(screen.queryByTestId("budget-bar")).not.toBeInTheDocument();
    });

    it("renders goal type pill using the translated goal type key", () => {
      renderCampaignCard({
        campaign: {
          ...mockCampaign,
          goals: { ...mockCampaign.goals, goalType: "REACH" },
        },
      });
      expect(
        screen.getByText("campaignsList.goalTypes.REACH"),
      ).toBeInTheDocument();
    });

    it("uses custom id when provided", () => {
      renderCampaignCard({ id: "my-card" });
      expect(document.getElementById("my-card")).toBeInTheDocument();
    });

    it("applies custom className", () => {
      const { container } = renderCampaignCard({
        className: "custom-class",
      });
      const card = container.querySelector(".custom-class");
      expect(card).toBeInTheDocument();
    });
  });

  describe("interactions", () => {
    it("calls onSelect when checkbox is changed", async () => {
      const user = userEvent.setup();
      const onSelect = vi.fn();
      renderCampaignCard({ onSelect });
      const checkbox = screen.getByRole("checkbox");
      await user.click(checkbox);
      expect(onSelect).toHaveBeenCalledWith("camp-1");
    });

    it("checkbox reflects selected prop", () => {
      renderCampaignCard({ selected: true });
      const checkbox = screen.getByRole("checkbox");
      expect(checkbox).toBeChecked();
    });

    it("clicking the card body does not navigate to campaign view", async () => {
      const user = userEvent.setup();
      renderCampaignCard();
      const card = document.getElementById("campaign-card-camp-1");
      await user.click(card!);
      expect(mockNavigate).not.toHaveBeenCalled();
    });

    it("navigates to view when View Details is clicked", async () => {
      const user = userEvent.setup();
      renderCampaignCard();
      await user.click(
        screen.getByRole("button", { name: /card\.view_details/i }),
      );
      expect(mockNavigate).toHaveBeenCalledWith("/campaigns/view/camp-1");
    });
  });
});
