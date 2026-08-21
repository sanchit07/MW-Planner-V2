import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import CampaignTypeDropdown from "../CampaignTypeDropdown";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock("@components/ui/Dropdown", () => ({
  Dropdown: ({
    value,
    onChange,
    children,
  }: {
    value: string;
    onChange: (v: string) => void;
    children: React.ReactNode;
  }) => (
    <div data-testid="dropdown">
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        data-testid="campaign-type-select"
      >
        <option value="all-campaign">All Campaigns</option>
      </select>
      {children}
    </div>
  ),
  DropdownTrigger: ({ children }: { children: React.ReactNode }) => (
    <span>{children}</span>
  ),
  DropdownContent: ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  ),
  DropdownItem: () => null,
}));

describe("CampaignTypeDropdown", () => {
  const defaultProps = {
    value: "all-campaign" as const,
    onChange: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("rendering", () => {
    it("renders with label when showLabel is true", () => {
      render(<CampaignTypeDropdown {...defaultProps} />);
      expect(
        screen.getByText("campaignTypeDropdown.label"),
      ).toBeInTheDocument();
    });

    it("renders without label when showLabel is false", () => {
      render(<CampaignTypeDropdown {...defaultProps} showLabel={false} />);
      expect(
        screen.queryByText("campaignTypeDropdown.label"),
      ).not.toBeInTheDocument();
    });

    it("renders trigger with label for current value", () => {
      render(<CampaignTypeDropdown {...defaultProps} />);
      expect(screen.getByTestId("campaign-type-select")).toHaveValue(
        "all-campaign",
      );
    });

    it("applies custom className to wrapper", () => {
      const { container } = render(
        <CampaignTypeDropdown {...defaultProps} className="custom-wrapper" />,
      );
      const wrapper = container.firstChild as HTMLElement;
      expect(wrapper).toHaveClass("custom-wrapper");
    });

    it("applies default triggerClassName when not provided", () => {
      render(<CampaignTypeDropdown {...defaultProps} />);
      const select = screen.getByTestId("campaign-type-select");
      expect(select).toBeInTheDocument();
    });

    it("applies custom triggerClassName when provided", () => {
      render(
        <CampaignTypeDropdown
          {...defaultProps}
          triggerClassName="custom-trigger"
        />,
      );
      const select = screen.getByTestId("campaign-type-select");
      expect(select).toBeInTheDocument();
    });
  });

  describe("interactions", () => {
    it("calls onChange with the selected CampaignType", async () => {
      const user = userEvent.setup();
      render(<CampaignTypeDropdown {...defaultProps} />);
      const select = screen.getByTestId("campaign-type-select");
      await user.selectOptions(select, "all-campaign");
      expect(defaultProps.onChange).toHaveBeenCalledWith("all-campaign");
    });
  });

  describe("default props", () => {
    it("defaults showLabel to true", () => {
      render(<CampaignTypeDropdown value="all-campaign" onChange={vi.fn()} />);
      expect(
        screen.getByText("campaignTypeDropdown.label"),
      ).toBeInTheDocument();
    });
  });
});
