import { configureStore } from "@reduxjs/toolkit";
import { campaignsUISlice } from "@services/campaign/campaignsUISlice";
import { userSlice } from "@services/user/userSlice";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { Provider } from "react-redux";
import { describe, it, expect, vi, beforeEach } from "vitest";

import type { CampaignDisplay } from "../../../../types/campaign-display.types";
import { CampaignsGridView } from "../CampaignsGridView";

vi.mock("@hooks/useInfiniteScroll", () => ({
  useInfiniteScroll: () => ({ current: null }),
}));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return { ...actual, useNavigate: () => mockNavigate };
});

const mockCampaign: CampaignDisplay = {
  id: "camp-1",
  campaignName: "Campaign One",
  userName: "User",
  brand: "Brand",
  status: "Planned",
  statusColor: "paused",
  daysLeft: "30 Days",
  budget: "1000",
  totalCost: "1200",
  startDate: "2025-01-01",
  endDate: "2025-01-31",
  impressions: 50000,
  reach: 10000,
  sov: 10,
  plannedSot: 50,
  totalSot: 100,
  inventory: 5,
  goals: {
    typeName: "Reach",
    goalType: "REACH",
    targetName: "",
    targetValue: 10000,
  },
  companyName: "Co",
};

const defaultPagination = {
  currentPage: 1,
  totalPages: 2,
  pageSize: 10,
  totalItems: 15,
};

function createStore() {
  return configureStore({
    reducer: {
      campaignsUI: campaignsUISlice.reducer,
      profile: userSlice.reducer,
    },
  });
}

function renderGridView(
  props: Partial<React.ComponentProps<typeof CampaignsGridView>> = {},
) {
  const store = createStore();
  return render(
    <Provider store={store}>
      <CampaignsGridView
        data={[]}
        isLoading={false}
        isFetching={false}
        paginationInfo={defaultPagination}
        onLoadMore={vi.fn()}
        {...props}
      />
    </Provider>,
  );
}

describe("CampaignsGridView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("rendering", () => {
    it("renders loading spinner when isLoading and no data", () => {
      renderGridView({ data: [], isLoading: true });
      expect(
        document.getElementById("campaigns-grid-loading"),
      ).toBeInTheDocument();
    });

    it("renders empty message when not loading and no data", () => {
      renderGridView({ data: [] });
      expect(
        document.getElementById("campaigns-grid-empty"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("campaignsList.emptyMessage"),
      ).toBeInTheDocument();
    });

    it("renders grid with campaign cards when data is provided", () => {
      renderGridView({ data: [mockCampaign] });
      expect(document.getElementById("campaigns-grid")).toBeInTheDocument();
      expect(screen.getByText("Campaign One")).toBeInTheDocument();
    });

    it("renders LoadMore component when there is data", () => {
      renderGridView({ data: [mockCampaign] });
      expect(
        document.getElementById("campaigns-load-more"),
      ).toBeInTheDocument();
    });
  });

  describe("selection", () => {
    it("dispatches toggleItemSelection when card is selected", async () => {
      const store = createStore();
      const dispatchSpy = vi.spyOn(store, "dispatch");
      render(
        <Provider store={store}>
          <CampaignsGridView
            data={[mockCampaign]}
            isLoading={false}
            isFetching={false}
            paginationInfo={defaultPagination}
            onLoadMore={vi.fn()}
          />
        </Provider>,
      );
      const user = userEvent.setup();
      const checkbox = screen.getByRole("checkbox");
      await user.click(checkbox);
      expect(dispatchSpy).toHaveBeenCalled();
      const action = dispatchSpy.mock.calls.find(
        (c) =>
          c[0]?.type?.includes("toggleItemSelection") ||
          (c[0] as { type?: string })?.type?.includes("campaignsUI"),
      );
      expect(action).toBeDefined();
    });
  });

  describe("data accumulation", () => {
    it("shows first page data on initial load", () => {
      renderGridView({
        data: [mockCampaign],
        paginationInfo: { ...defaultPagination, currentPage: 1 },
      });
      expect(screen.getByText("Campaign One")).toBeInTheDocument();
    });

    it("does not show loading when data exists and isLoading is true", () => {
      renderGridView({ data: [mockCampaign], isLoading: true });
      expect(
        document.getElementById("campaigns-grid-loading"),
      ).not.toBeInTheDocument();
      expect(screen.getByText("Campaign One")).toBeInTheDocument();
    });
  });

  describe("querySignature change (e.g. forced refresh after bulk archive)", () => {
    it("drops stale cards and shows only the freshly fetched data once querySignature changes", () => {
      const store = createStore();
      const { rerender } = render(
        <Provider store={store}>
          <CampaignsGridView
            data={[mockCampaign]}
            isLoading={false}
            isFetching={false}
            paginationInfo={defaultPagination}
            onLoadMore={vi.fn()}
            querySignature="sig-1"
          />
        </Provider>,
      );
      expect(screen.getByText("Campaign One")).toBeInTheDocument();

      // Parent bumps the signature (a refresh nonce) after a mutation like
      // bulk archive, forcing the grid to drop everything it accumulated
      // and start over from whatever the refetch returns.
      rerender(
        <Provider store={store}>
          <CampaignsGridView
            data={[]}
            isLoading={false}
            isFetching={false}
            paginationInfo={defaultPagination}
            onLoadMore={vi.fn()}
            querySignature="sig-2"
          />
        </Provider>,
      );

      expect(screen.queryByText("Campaign One")).not.toBeInTheDocument();
      expect(
        document.getElementById("campaigns-grid-empty"),
      ).toBeInTheDocument();
    });
  });

  describe("container", () => {
    it("renders container with expected id", () => {
      renderGridView({ data: [mockCampaign] });
      expect(
        document.getElementById("campaigns-grid-container"),
      ).toBeInTheDocument();
    });
  });
});
