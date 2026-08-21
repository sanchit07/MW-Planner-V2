import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";

import RegionTooltip from "../RegionTooltip";

describe("RegionTooltip", () => {
  const defaultProps = {
    country: "Malaysia",
    inventories: 100,
    utilization: 45,
    countCampaigns: 5,
    revenue: 10000,
  };

  describe("rendering", () => {
    it("renders inventories and campaign counts", () => {
      render(<RegionTooltip {...defaultProps} />);
      expect(screen.getByText(/100 Inventories/)).toBeInTheDocument();
      expect(screen.getByText(/5 plans/)).toBeInTheDocument();
    });

    it("renders utilization badge with percentage", () => {
      render(<RegionTooltip {...defaultProps} />);
      expect(screen.getByText("45.0% Utilized")).toBeInTheDocument();
    });

    it("renders total revenue with currency formatting", () => {
      render(<RegionTooltip {...defaultProps} />);
      expect(screen.getByText("Total Revenue")).toBeInTheDocument();
      expect(screen.getByText("USD 10,000.00")).toBeInTheDocument();
    });

    it("renders optional inventory types when provided", () => {
      render(
        <RegionTooltip
          {...defaultProps}
          digitalBillboard={30}
          staticCount={25}
          transit={20}
          retail={25}
        />,
      );
      expect(screen.getByText("Digital Billboard")).toBeInTheDocument();
      expect(screen.getByText("Static")).toBeInTheDocument();
      expect(screen.getByText("Transit")).toBeInTheDocument();
      expect(screen.getByText("Retail")).toBeInTheDocument();
      expect(screen.getByText("30")).toBeInTheDocument();
      expect(screen.getAllByText("25")).toHaveLength(2);
      expect(screen.getByText("20")).toBeInTheDocument();
    });

    it("uses country in aria-label when provided", () => {
      render(<RegionTooltip {...defaultProps} />);
      const tooltip = screen.getByRole("tooltip", {
        name: "Region details for Malaysia",
      });
      expect(tooltip).toBeInTheDocument();
    });

    it("uses fallback aria-label when country is empty", () => {
      render(<RegionTooltip {...defaultProps} country="" />);
      const tooltip = screen.getByRole("tooltip", { name: "Region details" });
      expect(tooltip).toBeInTheDocument();
    });
  });

  describe("getUtilizationBadgeStyle branches", () => {
    it("shows destructive variant when utilization >= 80", () => {
      const { container } = render(
        <RegionTooltip {...defaultProps} utilization={85} />,
      );
      expect(screen.getByText("85.0% Utilized")).toBeInTheDocument();
      expect(container.querySelector(".bg-mw-error-100")).toBeInTheDocument();
    });

    it("shows warning variant when utilization >= 60 and < 80", () => {
      const { container } = render(
        <RegionTooltip {...defaultProps} utilization={65} />,
      );
      expect(screen.getByText("65.0% Utilized")).toBeInTheDocument();
      expect(container.querySelector(".bg-mw-warning-100")).toBeInTheDocument();
    });

    it("shows success variant when utilization < 60", () => {
      const { container } = render(
        <RegionTooltip {...defaultProps} utilization={45} />,
      );
      expect(screen.getByText("45.0% Utilized")).toBeInTheDocument();
      expect(container.querySelector(".bg-mw-success-100")).toBeInTheDocument();
    });

    it("shows success variant when utilization is exactly 59", () => {
      render(<RegionTooltip {...defaultProps} utilization={59} />);
      expect(screen.getByText("59.0% Utilized")).toBeInTheDocument();
    });

    it("shows destructive variant when utilization is exactly 80", () => {
      render(<RegionTooltip {...defaultProps} utilization={80} />);
      expect(screen.getByText("80.0% Utilized")).toBeInTheDocument();
    });

    it("shows warning variant when utilization is exactly 60", () => {
      render(<RegionTooltip {...defaultProps} utilization={60} />);
      expect(screen.getByText("60.0% Utilized")).toBeInTheDocument();
    });

    it("shows success variant when utilization is 0", () => {
      render(<RegionTooltip {...defaultProps} utilization={0} />);
      expect(screen.getByText("0.0% Utilized")).toBeInTheDocument();
    });
  });
});
