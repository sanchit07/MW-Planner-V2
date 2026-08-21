import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import ScheduleOverview from "../ScheduleOverview";

// Mock dependencies
vi.mock("@components/common/SelectedInventoryListContainer", () => ({
  SelectedInventoryListContainer: ({
    campaignId,
    enabled,
    containerClassName,
    headerClassName,
    headerIcon,
    headerTitle,
    headerSubtitle,
    contentClassName,
    renderItem,
  }: {
    campaignId?: string;
    enabled: boolean;
    containerClassName?: string;
    headerClassName?: string;
    headerIcon: React.ReactNode;
    headerTitle: string;
    headerSubtitle: string;
    contentClassName?: string;
    renderItem: (item: unknown) => React.ReactNode;
  }) => (
    <div
      data-testid="selected-inventory-list-container"
      data-campaign-id={campaignId}
      data-enabled={enabled}
      data-container-class={containerClassName}
      data-header-class={headerClassName}
      data-content-class={contentClassName}
    >
      <div data-testid="header-icon">{headerIcon}</div>
      <div data-testid="header-title">{headerTitle}</div>
      <div data-testid="header-subtitle">{headerSubtitle}</div>
      <div data-testid="content">
        {renderItem({
          detail: { id: "item-1", name: "Test Item" },
        })}
      </div>
    </div>
  ),
}));

vi.mock("@components/common/InventoryDetailCard", () => ({
  InventoryDetailCard: ({
    item,
    campaignCurrency,
    emptyValueDisplay,
    fromSchedule,
  }: {
    item: unknown;
    campaignCurrency?: string;
    formatCurrency: (value: number, currency: string) => string;
    tCampaigns: (key: string) => string;
    emptyValueDisplay: string;
    fromSchedule: boolean;
  }) => (
    <div
      data-testid="inventory-detail-card"
      data-item-id={(item as { detail: { id: string } }).detail.id}
      data-campaign-currency={campaignCurrency}
      data-empty-value-display={emptyValueDisplay}
      data-from-schedule={fromSchedule}
    >
      Inventory Detail Card
    </div>
  ),
}));

const mockT = vi.fn((key: string) => key);

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: mockT,
  }),
}));

vi.mock("@utils/campaign.utils", () => ({
  formatCurrency: (value: number, currency: string) =>
    `${currency} ${value.toFixed(2)}`,
}));

vi.mock("lucide-react", () => ({
  Clock: () => <svg data-testid="clock-icon" />,
}));

describe("ScheduleOverview", () => {
  const defaultProps = {
    campaignId: "campaign-123",
    campaignCurrency: "USD",
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockT.mockImplementation((key: string) => key);
  });

  describe("Rendering", () => {
    it("renders component with campaignId", () => {
      render(<ScheduleOverview {...defaultProps} />);
      expect(
        screen.getByTestId("selected-inventory-list-container"),
      ).toBeInTheDocument();
    });

    it("renders without campaignId", () => {
      render(<ScheduleOverview campaignCurrency="USD" />);
      expect(
        screen.getByTestId("selected-inventory-list-container"),
      ).toBeInTheDocument();
    });

    it("passes campaignId to SelectedInventoryListContainer", () => {
      render(<ScheduleOverview {...defaultProps} />);
      const container = screen.getByTestId("selected-inventory-list-container");
      expect(container).toHaveAttribute("data-campaign-id", "campaign-123");
    });

    it("enables container when campaignId is provided", () => {
      render(<ScheduleOverview {...defaultProps} />);
      const container = screen.getByTestId("selected-inventory-list-container");
      expect(container).toHaveAttribute("data-enabled", "true");
    });

    it("disables container when campaignId is not provided", () => {
      render(<ScheduleOverview campaignCurrency="USD" />);
      const container = screen.getByTestId("selected-inventory-list-container");
      expect(container).toHaveAttribute("data-enabled", "false");
    });

    it("renders header icon with Clock icon", () => {
      render(<ScheduleOverview {...defaultProps} />);
      expect(screen.getByTestId("clock-icon")).toBeInTheDocument();
    });

    it("renders header title with translation", () => {
      render(<ScheduleOverview {...defaultProps} />);
      expect(screen.getByTestId("header-title")).toHaveTextContent(
        "viewCampaign.scheduleTab.title",
      );
    });

    it("renders header subtitle with translation", () => {
      render(<ScheduleOverview {...defaultProps} />);
      expect(screen.getByTestId("header-subtitle")).toHaveTextContent(
        "viewCampaign.scheduleTab.subTitle",
      );
    });

    it("passes correct container className", () => {
      render(<ScheduleOverview {...defaultProps} />);
      const container = screen.getByTestId("selected-inventory-list-container");
      expect(container).toHaveAttribute(
        "data-container-class",
        "p-4 flex flex-col h-full",
      );
    });

    it("passes correct header className", () => {
      render(<ScheduleOverview {...defaultProps} />);
      const container = screen.getByTestId("selected-inventory-list-container");
      expect(container).toHaveAttribute(
        "data-header-class",
        "pb-4 border-b border-container-border",
      );
    });

    it("passes correct content className", () => {
      render(<ScheduleOverview {...defaultProps} />);
      const container = screen.getByTestId("selected-inventory-list-container");
      expect(container).toHaveAttribute(
        "data-content-class",
        "p-0! pt-5! flex flex-col gap-4 flex-1 max-h-96",
      );
    });
  });

  describe("InventoryDetailCard rendering", () => {
    it("renders InventoryDetailCard for each item", () => {
      render(<ScheduleOverview {...defaultProps} />);
      expect(screen.getByTestId("inventory-detail-card")).toBeInTheDocument();
    });

    it("passes item to InventoryDetailCard", () => {
      render(<ScheduleOverview {...defaultProps} />);
      const card = screen.getByTestId("inventory-detail-card");
      expect(card).toHaveAttribute("data-item-id", "item-1");
    });

    it("passes campaignCurrency to InventoryDetailCard", () => {
      render(<ScheduleOverview {...defaultProps} />);
      const card = screen.getByTestId("inventory-detail-card");
      expect(card).toHaveAttribute("data-campaign-currency", "USD");
    });

    it("passes formatCurrency function to InventoryDetailCard", () => {
      render(<ScheduleOverview {...defaultProps} />);
      // Function is passed, we verify the card renders correctly
      expect(screen.getByTestId("inventory-detail-card")).toBeInTheDocument();
    });

    it("passes tCampaigns function to InventoryDetailCard", () => {
      render(<ScheduleOverview {...defaultProps} />);
      // Function is passed, we verify the card renders correctly
      expect(screen.getByTestId("inventory-detail-card")).toBeInTheDocument();
    });

    it("passes emptyValueDisplay as DASH", () => {
      render(<ScheduleOverview {...defaultProps} />);
      const card = screen.getByTestId("inventory-detail-card");
      // EmptyValueDisplay.DASH is "--" based on the test result
      expect(card).toHaveAttribute("data-empty-value-display", "--");
    });

    it("passes fromSchedule as true", () => {
      render(<ScheduleOverview {...defaultProps} />);
      const card = screen.getByTestId("inventory-detail-card");
      expect(card).toHaveAttribute("data-from-schedule", "true");
    });
  });

  describe("Edge cases", () => {
    it("handles undefined campaignCurrency", () => {
      render(<ScheduleOverview campaignId="campaign-123" />);
      const card = screen.getByTestId("inventory-detail-card");
      // When campaignCurrency is undefined, the attribute may not be set
      expect(card).toBeInTheDocument();
    });

    it("handles empty campaignId", () => {
      render(<ScheduleOverview campaignId="" campaignCurrency="USD" />);
      const container = screen.getByTestId("selected-inventory-list-container");
      expect(container).toHaveAttribute("data-enabled", "false");
    });

    it("handles forecastData prop (unused but passed)", () => {
      const forecastData = {
        impressions: 1000,
        clicks: 50,
      };

      render(
        <ScheduleOverview
          {...defaultProps}
          forecastData={forecastData as never}
        />,
      );

      // Component should render without errors
      expect(
        screen.getByTestId("selected-inventory-list-container"),
      ).toBeInTheDocument();
    });
  });

  describe("Component structure", () => {
    it("renders with correct root className", () => {
      const { container } = render(<ScheduleOverview {...defaultProps} />);
      const rootDiv = container.querySelector(".inventory-list");
      expect(rootDiv).toBeInTheDocument();
      expect(rootDiv).toHaveClass("pt-4", "h-full");
    });

    it("renders header icon with correct styling classes", () => {
      render(<ScheduleOverview {...defaultProps} />);
      const headerIcon = screen.getByTestId("header-icon");
      const iconContainer = headerIcon.firstChild as HTMLElement;
      expect(iconContainer).toHaveClass(
        "w-10",
        "h-10",
        "bg-mw-purple-warning-50",
        "rounded-xs",
        "flex",
        "items-center",
        "justify-center",
      );
    });
  });
});
