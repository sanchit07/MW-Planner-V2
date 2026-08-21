import { configureStore } from "@reduxjs/toolkit";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { Provider } from "react-redux";
import { describe, it, expect, vi, beforeEach } from "vitest";

import userSlice from "../../../services/user/userSlice";
import { CompanyMarketAccessItem } from "../../../types/campaign.types";
import BudgetAndGoalForm from "../BudgetAndGoalPage";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const mockAutosave = vi.fn();
const mockAutosaveBatch = vi.fn();
vi.mock("../../../hooks/useAutosave", () => ({
  useAutosave: () => {
    return {
      autosave: mockAutosave,
      autosaveBatch: mockAutosaveBatch,
      isAutosaving: false,
    };
  },
}));

// Countries now come from the market-access API (CompanyMarketAccessItem shape)
const mockMarketAccessData: CompanyMarketAccessItem[] = [
  {
    id: "id-1",
    company_id: "company-1",
    country_id: "country-sg",
    country_name: "Singapore",
    country_code: "SG",
    is_active: true,
  },
  {
    id: "id-2",
    company_id: "company-1",
    country_id: "country-lk",
    country_name: "Sri Lanka",
    country_code: "LK",
    is_active: true,
  },
];

// Market-details data returned after a country is selected
const mockMarketDetailsData = [
  {
    countryName: "Singapore",
    population: 5_000_000,
    impressions: 1_000_000,
    inventoryCount: 500,
  },
];

// vi.hoisted ensures this is available when the vi.mock factory is hoisted to the top
const { mockGetCountryMarketDetailsByIso } = vi.hoisted(() => ({
  mockGetCountryMarketDetailsByIso: vi.fn(),
}));

vi.mock("../../../services/campaign/campaignSlice", async (importOriginal) => {
  const actual =
    await importOriginal<
      typeof import("../../../services/campaign/campaignSlice")
    >();
  return {
    ...actual,
    useGetCompanyMarketAccessQuery: () => ({
      data: { markets: mockMarketAccessData },
      isLoading: false,
    }),
    useGetCountryMarketDetailsByIsoQuery: mockGetCountryMarketDetailsByIso,
    setCampaignData: vi.fn(),
  };
});

const defaultCampaignState = {
  campaignData: null,
  campaignId: null,
};
const store = configureStore({
  reducer: {
    campaign: (state = defaultCampaignState) => state,
    profile: userSlice,
  },
});

function renderBudgetForm(
  props: Partial<React.ComponentProps<typeof BudgetAndGoalForm>> = {},
) {
  return render(
    <Provider store={store}>
      <BudgetAndGoalForm {...props} />
    </Provider>,
  );
}

const threeChannelStore = configureStore({
  reducer: {
    campaign: (
      state = {
        campaignData: {
          countryId: "singapore",
          currency: "SGD",
          mediaChannels: ["DIGITAL_OOH", "CLASSIC_OOH", "TRANSIT"],
        },
        campaignId: "test-id",
      },
    ) => state,
    profile: userSlice,
  },
});

const classicOnlyStore = configureStore({
  reducer: {
    campaign: (
      state = {
        campaignData: {
          countryId: "singapore",
          currency: "SGD",
          mediaChannels: ["CLASSIC_OOH"],
        },
        campaignId: "test-id",
      },
    ) => state,
    profile: userSlice,
  },
});

const digitalOnlyStore = configureStore({
  reducer: {
    campaign: (
      state = {
        campaignData: {
          countryId: "singapore",
          currency: "SGD",
          mediaChannels: ["DIGITAL_OOH"],
        },
        campaignId: "test-id",
      },
    ) => state,
    profile: userSlice,
  },
});

const classicAndDigitalStore = configureStore({
  reducer: {
    campaign: (
      state = {
        campaignData: {
          countryId: "singapore",
          currency: "SGD",
          mediaChannels: ["CLASSIC_OOH", "DIGITAL_OOH"],
        },
        campaignId: "test-id",
      },
    ) => state,
    profile: userSlice,
  },
});

const classicOnlyWithAdplaysStore = configureStore({
  reducer: {
    campaign: (
      state = {
        campaignData: {
          countryId: "singapore",
          currency: "SGD",
          mediaChannels: ["CLASSIC_OOH"],
          goals: { goalType: "ADPLAYS", targetValue: 500 },
        },
        campaignId: "test-id",
      },
    ) => state,
    profile: userSlice,
  },
});

function renderBudgetFormWithStore(
  store: ReturnType<typeof configureStore>,
  props: Partial<React.ComponentProps<typeof BudgetAndGoalForm>> = {},
) {
  return render(
    <Provider store={store}>
      <BudgetAndGoalForm {...props} />
    </Provider>,
  );
}

function renderBudgetFormWithCampaignData(
  campaignData: Record<string, unknown>,
  props: Partial<React.ComponentProps<typeof BudgetAndGoalForm>> = {},
) {
  const storeWithData = configureStore({
    reducer: {
      campaign: (state = { campaignData, campaignId: "test-id" }) => state,
      profile: userSlice,
    },
  });
  return render(
    <Provider store={storeWithData}>
      <BudgetAndGoalForm {...props} />
    </Provider>,
  );
}

describe("BudgetAndGoalForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Default: market-details query has resolved and is not fetching
    mockGetCountryMarketDetailsByIso.mockReturnValue({
      data: { data: mockMarketDetailsData },
      isFetching: false,
    });
    // Stub the IP-geolocation fetch so tests never hit the real ipapi.co
    // service. Default: no detectable country (auto-select stays inert).
    global.fetch = vi.fn().mockResolvedValue({
      json: async () => ({}),
    }) as unknown as typeof fetch;
  });

  describe("rendering", () => {
    it("renders market selection card with country dropdown", () => {
      renderBudgetForm();
      expect(
        screen.getByText("budget_goal.market_selection.title"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("budget_goal.market_selection.target_country"),
      ).toBeInTheDocument();
    });

    it("renders budget and goal setup card with currency and budget inputs", () => {
      renderBudgetForm();
      expect(
        screen.getByText("budget_goal.budget_goal_setup.title"),
      ).toBeInTheDocument();
      expect(
        screen.getByLabelText(/budget_goal\.budget_goal_setup\.currency/i),
      ).toBeInTheDocument();
      expect(
        screen.getByPlaceholderText(
          /budget_goal\.budget_goal_setup\.budget_placeholder/i,
        ),
      ).toBeInTheDocument();
    });

    it("renders goal type dropdown", () => {
      renderBudgetForm();
      expect(
        screen.getByText("budget_goal.budget_goal_setup.goal_type"),
      ).toBeInTheDocument();
    });
  });

  describe("ref API", () => {
    it("exposes submitForm, getFormData, isValid, validateStep, resetForm via ref", async () => {
      const ref =
        React.createRef<React.ComponentRef<typeof BudgetAndGoalForm>>();
      renderBudgetForm({ ref });
      await waitFor(() => {
        expect(ref.current).not.toBeNull();
      });
      expect(typeof ref.current?.submitForm).toBe("function");
      expect(typeof ref.current?.getFormData).toBe("function");
      expect(typeof ref.current?.isValid).toBe("function");
      expect(typeof ref.current?.validateStep).toBe("function");
      expect(typeof ref.current?.resetForm).toBe("function");
    });

    it("getFormData returns object with budget form keys", async () => {
      const ref =
        React.createRef<React.ComponentRef<typeof BudgetAndGoalForm>>();
      renderBudgetForm({ ref });
      await waitFor(() => {
        expect(ref.current).not.toBeNull();
      });
      const data = ref.current?.getFormData();
      expect(data).toBeDefined();
      expect(data).toHaveProperty("country");
      expect(data).toHaveProperty("currency");
      expect(data).toHaveProperty("budget");
      expect(data).toHaveProperty("goalType");
    });

    it("resetForm clears form to default data", async () => {
      const ref =
        React.createRef<React.ComponentRef<typeof BudgetAndGoalForm>>();
      renderBudgetForm({ ref });
      await waitFor(() => {
        expect(ref.current).not.toBeNull();
      });
      ref.current?.resetForm();
      const data = ref.current?.getFormData();
      expect(data).toMatchObject({
        country: "",
        currency: "",
        goalType: "",
        budget: 500,
      });
    });

    it("submitForm returns false while market-details is still fetching", async () => {
      mockGetCountryMarketDetailsByIso.mockReturnValue({
        data: undefined,
        isFetching: true,
      });
      const ref =
        React.createRef<React.ComponentRef<typeof BudgetAndGoalForm>>();
      renderBudgetForm({ ref });
      await waitFor(() => {
        expect(ref.current).not.toBeNull();
      });
      const result = await ref.current?.submitForm();
      expect(result).toBe(false);
    });
  });

  describe("country selection", () => {
    it("shows country options from market-access API when dropdown is opened", async () => {
      renderBudgetForm();
      const trigger = screen.getByRole("button", {
        name: /budget_goal\.market_selection\.select_country|Singapore/i,
      });
      await userEvent.click(trigger);
      await waitFor(() => {
        expect(screen.getByText("Singapore")).toBeInTheDocument();
      });
    });

    it("shows all active countries from market-access API", async () => {
      renderBudgetForm();
      const trigger = screen.getByRole("button", {
        name: /budget_goal\.market_selection\.select_country|Singapore/i,
      });
      await userEvent.click(trigger);
      await waitFor(() => {
        expect(screen.getByText("Singapore")).toBeInTheDocument();
        expect(screen.getByText("Sri Lanka")).toBeInTheDocument();
      });
    });

    it("auto-selects LKR currency when Sri Lanka (multi-word country) is selected", async () => {
      const user = userEvent.setup();
      renderBudgetForm();

      const trigger = screen.getByRole("button", {
        name: /budget_goal\.market_selection\.select_country/i,
      });
      await user.click(trigger);

      await waitFor(() => {
        expect(screen.getByText("Sri Lanka")).toBeInTheDocument();
      });

      await user.click(screen.getByText("Sri Lanka"));

      await waitFor(() => {
        expect(mockAutosaveBatch).toHaveBeenCalledWith(
          expect.objectContaining({ currency: "LKR" }),
        );
      });
    });

    it("triggers market-details query with the selected country's ISO code", async () => {
      const user = userEvent.setup();
      renderBudgetForm();

      const trigger = screen.getByRole("button", {
        name: /budget_goal\.market_selection\.select_country/i,
      });
      await user.click(trigger);
      await waitFor(() => {
        expect(screen.getByText("Singapore")).toBeInTheDocument();
      });
      await user.click(screen.getByText("Singapore"));

      await waitFor(() => {
        // Hook must have been called with "SG" after the click sets selectedCountryIso
        expect(mockGetCountryMarketDetailsByIso).toHaveBeenCalledWith(
          "SG",
          expect.objectContaining({ skip: false }),
        );
      });
    });

    it("auto-selects the IP-detected country and behaves like a manual select (currency + market-details ISO)", async () => {
      // ipapi.co reports Singapore for this session
      global.fetch = vi.fn().mockResolvedValue({
        json: async () => ({ country_name: "Singapore" }),
      }) as unknown as typeof fetch;

      renderBudgetForm();

      await waitFor(() => {
        // Currency is derived + autosaved, same as a manual dropdown click
        expect(mockAutosaveBatch).toHaveBeenCalledWith(
          expect.objectContaining({ countryId: "Singapore", currency: "SGD" }),
        );
      });

      await waitFor(() => {
        // ISO is set so /market-details fires — this is the fix under test
        expect(mockGetCountryMarketDetailsByIso).toHaveBeenCalledWith(
          "SG",
          expect.objectContaining({ skip: false }),
        );
      });
    });

    it("does not auto-select when the IP-detected country is not in the company's markets", async () => {
      global.fetch = vi.fn().mockResolvedValue({
        json: async () => ({ country_name: "France" }),
      }) as unknown as typeof fetch;

      renderBudgetForm();

      // Give the geolocation effect time to resolve
      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalled();
      });

      expect(mockAutosaveBatch).not.toHaveBeenCalled();
    });
  });

  describe("market insights loading state", () => {
    it("shows loading overlay in market insights while market-details is fetching", async () => {
      mockGetCountryMarketDetailsByIso.mockReturnValue({
        data: undefined,
        isFetching: true,
      });
      const user = userEvent.setup();
      renderBudgetForm();

      // Open dropdown and select Singapore so the market insights card renders
      const trigger = screen.getByRole("button", {
        name: /budget_goal\.market_selection\.select_country/i,
      });
      await user.click(trigger);
      await waitFor(() => {
        expect(screen.getByText("Singapore")).toBeInTheDocument();
      });
      await user.click(screen.getByText("Singapore"));

      // Spinner (role="status") should be visible inside the market insights card
      await waitFor(() => {
        expect(screen.getByRole("status")).toBeInTheDocument();
      });
    });

    it("does not show loading overlay once market-details has resolved", async () => {
      // isFetching: false (default) — spinner must not be present
      renderBudgetFormWithCampaignData({ countryId: "Singapore" });

      await waitFor(() => {
        expect(
          screen.getByText("budget_goal.market_insights.title"),
        ).toBeInTheDocument();
      });

      expect(screen.queryByRole("status")).not.toBeInTheDocument();
    });
  });

  describe("budget distribution", () => {
    it("distribute evenly across 3 channels sums to exactly 100.0%", async () => {
      const user = userEvent.setup();
      renderBudgetFormWithStore(threeChannelStore);

      // Wait for distribution banner (requires country + channels)
      await waitFor(() => {
        expect(
          screen.getByText("budget_goal.distribution.edit_btn"),
        ).toBeInTheDocument();
      });

      // Open distribution modal
      await user.click(screen.getByText("budget_goal.distribution.edit_btn"));

      // Click distribute evenly
      await waitFor(() => {
        expect(
          screen.getByText("budget_goal.distribution.distribute_evenly"),
        ).toBeInTheDocument();
      });
      await user.click(
        screen.getByText("budget_goal.distribution.distribute_evenly"),
      );

      // Total must be exactly 100.0% (not 99.9% from floating-point truncation)
      await waitFor(() => {
        expect(screen.getByText(/100\.0%/)).toBeInTheDocument();
      });
    });
  });

  describe("ADPLAYS goal type — channel-based disable", () => {
    const openGoalDropdown = async (
      user: ReturnType<typeof userEvent.setup>,
    ) => {
      const trigger = screen.getByText(
        "budget_goal.budget_goal_setup.select_goal_type",
      );
      await user.click(trigger);
      await waitFor(() => {
        expect(
          screen.getAllByText(
            (content, el) =>
              el?.tagName === "P" &&
              content.includes("budget_goal.goal_types.adplays"),
          ).length,
        ).toBeGreaterThan(0);
      });
    };

    it("disables ADPLAYS when only CLASSIC_OOH is selected", async () => {
      const user = userEvent.setup();
      renderBudgetFormWithStore(classicOnlyStore);
      await openGoalDropdown(user);

      const adplaysOption = screen
        .getAllByText(
          (content, el) =>
            el?.tagName === "P" &&
            content.includes("budget_goal.goal_types.adplays"),
        )[0]
        .closest("button");

      expect(adplaysOption).toBeDisabled();
    });

    it("enables ADPLAYS when DIGITAL_OOH is the only channel", async () => {
      const user = userEvent.setup();
      renderBudgetFormWithStore(digitalOnlyStore);
      await openGoalDropdown(user);

      const adplaysOption = screen
        .getAllByText(
          (content, el) =>
            el?.tagName === "P" &&
            content.includes("budget_goal.goal_types.adplays"),
        )[0]
        .closest("button");

      expect(adplaysOption).not.toBeDisabled();
    });

    it("enables ADPLAYS when CLASSIC_OOH and DIGITAL_OOH are both selected", async () => {
      const user = userEvent.setup();
      renderBudgetFormWithStore(classicAndDigitalStore);
      await openGoalDropdown(user);

      const adplaysOption = screen
        .getAllByText(
          (content, el) =>
            el?.tagName === "P" &&
            content.includes("budget_goal.goal_types.adplays"),
        )[0]
        .closest("button");

      expect(adplaysOption).not.toBeDisabled();
    });

    it("auto-clears ADPLAYS goal and calls autosave when channels are classic-only", async () => {
      renderBudgetFormWithStore(classicOnlyWithAdplaysStore);

      await waitFor(() => {
        expect(mockAutosave).toHaveBeenCalledWith("goals", {}, true);
      });
    });
  });

  describe("onValidationChange", () => {
    it("calls onValidationChange when form validity changes", async () => {
      const onValidationChange = vi.fn();
      renderBudgetForm({ onValidationChange });
      await waitFor(() => {
        expect(onValidationChange).toHaveBeenCalledWith(false);
      });
    });
  });

  describe("goal type REACH alert", () => {
    it("should not show alert when no goal type is selected", () => {
      renderBudgetForm();
      const alertText = "budget_goal.budget_goal_setup.reach_planning_note";
      expect(screen.queryByText(alertText)).not.toBeInTheDocument();
    });

    it("should show alert when REACH goal type is selected", async () => {
      const user = userEvent.setup();
      renderBudgetForm();

      // Open goal type dropdown
      const goalTypeButton = screen.getByText(
        "budget_goal.budget_goal_setup.select_goal_type",
      );
      await user.click(goalTypeButton);

      // Look for REACH option in dropdown
      const reachOptions = screen.getAllByText((content, element) => {
        return (
          element?.tagName === "P" &&
          content.includes("budget_goal.goal_types.reach")
        );
      });

      await user.click(reachOptions[0]);

      // Wait for alert to appear
      await waitFor(() => {
        const alertText = "budget_goal.budget_goal_setup.reach_planning_note";
        expect(screen.getByText(alertText)).toBeInTheDocument();
      });
    });

    it("should hide alert when goal type is changed from REACH to another type", async () => {
      const user = userEvent.setup();
      renderBudgetForm();

      // First select REACH
      const goalTypeButton = screen.getByText(
        "budget_goal.budget_goal_setup.select_goal_type",
      );
      await user.click(goalTypeButton);

      const reachOptions = screen.getAllByText((content, element) => {
        return (
          element?.tagName === "P" &&
          content.includes("budget_goal.goal_types.reach")
        );
      });
      await user.click(reachOptions[0]);

      // Verify alert is shown
      await waitFor(() => {
        const alertText = "budget_goal.budget_goal_setup.reach_planning_note";
        expect(screen.getByText(alertText)).toBeInTheDocument();
      });

      // Change to a different goal type (IMPRESSIONS)
      const newGoalTypeButton = screen.getByRole("button", {
        name: /budget_goal\.goal_types\.reach/i,
      });
      await user.click(newGoalTypeButton);

      const impressionsOptions = screen.getAllByText((content, element) => {
        return (
          element?.tagName === "P" &&
          content.includes("budget_goal.goal_types.impressions")
        );
      });
      await user.click(impressionsOptions[0]);

      // Verify alert is hidden
      await waitFor(() => {
        const alertText = "budget_goal.budget_goal_setup.reach_planning_note";
        expect(screen.queryByText(alertText)).not.toBeInTheDocument();
      });
    });

    it("should render alert with proper styling", async () => {
      const user = userEvent.setup();
      const { container } = renderBudgetForm();

      // Select REACH goal type
      const goalTypeButton = screen.getByText(
        "budget_goal.budget_goal_setup.select_goal_type",
      );
      await user.click(goalTypeButton);

      const reachOptions = screen.getAllByText((content, element) => {
        return (
          element?.tagName === "P" &&
          content.includes("budget_goal.goal_types.reach")
        );
      });
      await user.click(reachOptions[0]);

      // Wait for alert and verify it has info variant styling
      await waitFor(() => {
        const alert = container.querySelector(".bg-mw-info-50");
        expect(alert).toBeInTheDocument();
      });
    });
  });

  describe("thousand separators", () => {
    it("displays the budget amount with thousand separators", async () => {
      const { container } = renderBudgetFormWithCampaignData({
        countryId: "singapore",
        currency: "SGD",
        budget: 21633,
      });

      await waitFor(() => {
        const budgetInput = container.querySelector(
          "#budget-goal-budget-input",
        ) as HTMLInputElement;
        expect(budgetInput.value).toBe("21,633");
      });
    });

    it("displays a large target value with thousand separators", async () => {
      const { container } = renderBudgetFormWithCampaignData({
        countryId: "singapore",
        currency: "SGD",
        budget: 5000,
        goals: { goalType: "IMPRESSIONS", targetValue: 1500000 },
      });

      await waitFor(() => {
        const targetInput = container.querySelector(
          "#budget-goal-target-value-input",
        ) as HTMLInputElement;
        expect(targetInput.value).toBe("1,500,000");
      });
    });

    it("regroups the budget with commas as the user types", async () => {
      const user = userEvent.setup();
      const { container } = renderBudgetFormWithCampaignData({
        countryId: "singapore",
        currency: "SGD",
      });

      const budgetInput = (await waitFor(() =>
        container.querySelector("#budget-goal-budget-input"),
      )) as HTMLInputElement;

      await user.clear(budgetInput);
      await user.type(budgetInput, "25000");

      await waitFor(() => {
        expect(budgetInput.value).toBe("25,000");
      });
    });
  });
});
