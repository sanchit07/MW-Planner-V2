import { configureStore } from "@reduxjs/toolkit";
import campaignSlice from "@services/campaign/campaignSlice";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { Provider } from "react-redux";
import { describe, it, expect, vi } from "vitest";

import ViewInventoriesPage from "../ViewInventoriesPage";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("../../InventoryMapView", () => ({
  default: (props: { rightPanel?: string }) =>
    React.createElement("div", {
      "data-testid":
        props.rightPanel === "availability" ? "availability-view" : "map-view",
    }),
}));

vi.mock("../useRecommendationScores", () => ({
  useRecommendationScores: () => ({}),
}));

function createStore() {
  return configureStore({
    reducer: { campaign: campaignSlice },
    preloadedState: {
      campaign: {
        currentCampaignName: "",
        campaignId: "c1",
        isCreating: false,
        createError: null,
        isEditMode: false,
        forecastData: null,
        recommendationRun: null,
        campaignData: {
          id: "c1",
          name: "",
          status: "",
          countryId: "US",
          currency: "USD",
          startDate: "",
          endDate: "",
          clientType: "",
          createdAt: "",
          updatedAt: "",
          inventoryCount: 0,
        },
      },
    },
  });
}

function renderOverlay(isOpen: boolean, onClose = vi.fn()) {
  return render(
    <Provider store={createStore()}>
      <ViewInventoriesPage isOpen={isOpen} onClose={onClose} />
    </Provider>,
  );
}

describe("ViewInventoriesPage", () => {
  it("renders nothing when closed", () => {
    renderOverlay(false);
    expect(
      screen.queryByText("inventories.view.title"),
    ).not.toBeInTheDocument();
  });

  it("renders title, tabs and the Map view by default when open", () => {
    renderOverlay(true);
    expect(screen.getByText("inventories.view.title")).toBeInTheDocument();
    expect(screen.getByText("inventories.view.tabs.map")).toBeInTheDocument();
    expect(
      screen.getByText("inventories.view.tabs.availability"),
    ).toBeInTheDocument();
    expect(screen.getByTestId("map-view")).toBeInTheDocument();
  });

  it("switches to the Availability panel", async () => {
    renderOverlay(true);
    await userEvent.click(
      screen.getByText("inventories.view.tabs.availability"),
    );
    expect(screen.getByTestId("availability-view")).toBeInTheDocument();
  });

  it("calls onClose when the X is clicked", async () => {
    const onClose = vi.fn();
    renderOverlay(true, onClose);
    await userEvent.click(
      screen.getByRole("button", { name: "inventories.view.title" }),
    );
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
