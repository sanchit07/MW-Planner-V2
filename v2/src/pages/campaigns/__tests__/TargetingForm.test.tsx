import { configureStore } from "@reduxjs/toolkit";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { Provider } from "react-redux";
import { describe, it, expect, vi, beforeEach } from "vitest";

import TargetingForm from "../TargetingForm";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
}));

const mockAutosave = vi.fn();
vi.mock("../../../hooks/useAutosave", () => ({
  useAutosave: () => ({
    autosave: mockAutosave,
  }),
}));

vi.mock("../DemographicComponent", () => ({
  default: ({
    onFieldChange,
  }: {
    control: unknown;
    onFieldChange: (v: unknown) => Promise<void>;
  }) => (
    <div data-testid="demographic-component">
      <button
        type="button"
        onClick={() =>
          onFieldChange({
            demographics: {
              age: ["18_24"],
              gender: [],
              income: [],
              interests: [],
              venues: [],
              behavior: [],
            },
          })
        }
      >
        Trigger demographic change
      </button>
    </div>
  ),
}));

vi.mock("../geofencing/GeoFencingForm", () => ({
  default: () => <div data-testid="geofencing-form">GeoFencing</div>,
}));

vi.mock("../../signals/SignalsForm", () => ({
  default: () => <div data-testid="signals-page">Signals</div>,
}));

vi.mock(
  "../../../services/inventory/inventorySlice",
  async (importOriginal) => {
    const actual =
      await importOriginal<
        typeof import("../../../services/inventory/inventorySlice")
      >();
    return {
      ...actual,
      useGetVenuesQuery: () => ({ data: [] }),
    };
  },
);

const store = configureStore({
  reducer: {
    campaign: () => ({
      campaignData: {
        targeting: {},
      },
      campaignId: null,
    }),
  },
});

const digitalOnlyStore = configureStore({
  reducer: {
    campaign: () => ({
      campaignData: {
        targeting: {},
        mediaChannels: ["DIGITAL_OOH"],
      },
      campaignId: null,
    }),
  },
});

const bothChannelsStore = configureStore({
  reducer: {
    campaign: () => ({
      campaignData: {
        targeting: {},
        mediaChannels: ["DIGITAL_OOH", "CLASSIC_OOH"],
      },
      campaignId: null,
    }),
  },
});

function renderTargetingForm(
  props: Partial<React.ComponentProps<typeof TargetingForm>> = {},
) {
  return render(
    <Provider store={store}>
      <TargetingForm ref={React.createRef()} {...props} />
    </Provider>,
  );
}

describe("TargetingForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("rendering", () => {
    it("renders tabs for demographics, geofencing, inventoryTypes, and signals", () => {
      renderTargetingForm();
      expect(
        screen.getByRole("button", {
          name: /targeting\.tabTitles\.demographics/i,
        }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", {
          name: /targeting\.tabTitles\.geoFencing/i,
        }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", {
          name: /targeting\.tabTitles\.inventoryTypes/i,
        }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /targeting\.tabTitles\.signals/i }),
      ).toBeInTheDocument();
    });

    it("renders DemographicComponent when demographics tab is active", () => {
      renderTargetingForm();
      expect(screen.getByTestId("demographic-component")).toBeInTheDocument();
    });

    it("renders GeoFencingForm when geofencing tab is selected", async () => {
      renderTargetingForm();
      await userEvent.click(
        screen.getByRole("button", {
          name: /targeting\.tabTitles\.geoFencing/i,
        }),
      );
      expect(screen.getByTestId("geofencing-form")).toBeInTheDocument();
    });

    it("renders signals tab as disabled", () => {
      renderTargetingForm();
      const signalsTab = screen.getByRole("button", {
        name: /targeting\.tabTitles\.signals/i,
      });
      expect(signalsTab).toBeInTheDocument();
      expect(signalsTab).toBeDisabled();
    });

    it("renders inventory types content when inventoryTypes tab is selected", async () => {
      renderTargetingForm();
      await userEvent.click(
        screen.getByRole("button", {
          name: /targeting\.tabTitles\.inventoryTypes/i,
        }),
      );
      await waitFor(() => {
        expect(
          screen.getByText("targeting.inventoryTypes.digitalOoh.title"),
        ).toBeInTheDocument();
        expect(
          screen.getByText("targeting.inventoryTypes.classicOoh.title"),
        ).toBeInTheDocument();
      });
    });

    it("disables Classic OOH venue type when only DIGITAL_OOH channel selected", async () => {
      render(
        <Provider store={digitalOnlyStore}>
          <TargetingForm ref={React.createRef()} />
        </Provider>,
      );
      await userEvent.click(
        screen.getByRole("button", {
          name: /targeting\.tabTitles\.inventoryTypes/i,
        }),
      );
      await waitFor(() => {
        expect(
          screen.getByText("targeting.inventoryTypes.classicOoh.title"),
        ).toBeInTheDocument();
      });
      // Classic OOH multiselect wrapper should carry cursor-not-allowed (disabled)
      const classicLabel = screen.getByText(
        "targeting.inventoryTypes.classicOoh.title",
      );
      const classicSection = classicLabel.closest("div")?.parentElement;
      const classicMultiselect = classicSection?.querySelector(
        '[class*="cursor-not-allowed"]',
      );
      expect(classicMultiselect).toBeInTheDocument();
    });

    it("renders the Inventory Types section with checkboxes for both channels, all checked by default except the Coming Soon ones", async () => {
      render(
        <Provider store={bothChannelsStore}>
          <TargetingForm ref={React.createRef()} />
        </Provider>,
      );
      await userEvent.click(
        screen.getByRole("button", {
          name: /targeting\.tabTitles\.inventoryTypes/i,
        }),
      );
      await waitFor(() => {
        expect(
          screen.getByText("targeting.inventoryClusters.digitalOoh.title"),
        ).toBeInTheDocument();
        expect(
          screen.getByText("targeting.inventoryClusters.classicOoh.title"),
        ).toBeInTheDocument();
      });
      const checkedByDefault = [
        "targeting.inventoryClusters.options.DIGITAL",
        "targeting.inventoryClusters.options.DIGITAL_TRANSIT",
        "targeting.inventoryClusters.options.CLASSIC",
        "targeting.inventoryClusters.options.CLASSIC_TRANSIT",
      ];
      for (const label of checkedByDefault) {
        expect((screen.getByLabelText(label) as HTMLInputElement).checked).toBe(
          true,
        );
      }
    });

    it("disables Digital Network and Classic Network with a Coming Soon tooltip, unchecked by default", async () => {
      render(
        <Provider store={bothChannelsStore}>
          <TargetingForm ref={React.createRef()} />
        </Provider>,
      );
      await userEvent.click(
        screen.getByRole("button", {
          name: /targeting\.tabTitles\.inventoryTypes/i,
        }),
      );
      const comingSoonLabels = [
        "targeting.inventoryClusters.options.DIGITAL_NETWORK",
        "targeting.inventoryClusters.options.CLASSIC_NETWORK",
      ];
      for (const label of comingSoonLabels) {
        const checkbox = (await screen.findByLabelText(
          label,
        )) as HTMLInputElement;
        expect(checkbox.checked).toBe(false);
        expect(checkbox).toBeDisabled();
      }
    });

    it("hides the Classic OOH inventory-cluster group when only DIGITAL_OOH channel is selected", async () => {
      render(
        <Provider store={digitalOnlyStore}>
          <TargetingForm ref={React.createRef()} />
        </Provider>,
      );
      await userEvent.click(
        screen.getByRole("button", {
          name: /targeting\.tabTitles\.inventoryTypes/i,
        }),
      );
      await waitFor(() => {
        expect(
          screen.getByLabelText("targeting.inventoryClusters.options.DIGITAL"),
        ).toBeInTheDocument();
      });
      expect(
        screen.queryByText("targeting.inventoryClusters.classicOoh.title"),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByLabelText("targeting.inventoryClusters.options.CLASSIC"),
      ).not.toBeInTheDocument();
    });

    it("unchecking an inventory-cluster checkbox autosaves the updated list", async () => {
      renderTargetingForm();
      await userEvent.click(
        screen.getByRole("button", {
          name: /targeting\.tabTitles\.inventoryTypes/i,
        }),
      );
      const digitalTransitCheckbox = await screen.findByLabelText(
        "targeting.inventoryClusters.options.DIGITAL_TRANSIT",
      );
      await userEvent.click(digitalTransitCheckbox);
      await waitFor(() => {
        expect(mockAutosave).toHaveBeenCalledWith(
          "targeting",
          expect.objectContaining({
            inventoryCluster: ["DIGITAL"],
          }),
        );
      });
    });

    it("enables Digital OOH venue type when DIGITAL_OOH channel selected", async () => {
      render(
        <Provider store={digitalOnlyStore}>
          <TargetingForm ref={React.createRef()} />
        </Provider>,
      );
      await userEvent.click(
        screen.getByRole("button", {
          name: /targeting\.tabTitles\.inventoryTypes/i,
        }),
      );
      await waitFor(() => {
        expect(
          screen.getByText("targeting.inventoryTypes.digitalOoh.title"),
        ).toBeInTheDocument();
      });
      const digitalLabel = screen.getByText(
        "targeting.inventoryTypes.digitalOoh.title",
      );
      const digitalSection = digitalLabel.closest("div")?.parentElement;
      const digitalMultiselect = digitalSection?.querySelector(
        '[class*="cursor-not-allowed"]',
      );
      expect(digitalMultiselect).not.toBeInTheDocument();
    });
  });

  describe("unified targeting field change", () => {
    it("calls autosave with merged targeting when demographic change is triggered", async () => {
      renderTargetingForm();
      await userEvent.click(
        screen.getByRole("button", { name: /Trigger demographic change/i }),
      );
      await waitFor(() => {
        expect(mockAutosave).toHaveBeenCalledWith(
          "targeting",
          expect.objectContaining({
            demographics: expect.objectContaining({ age: ["18_24"] }),
          }),
        );
      });
    });
  });

  describe("ref API", () => {
    it("exposes submitForm, getFormData, isValid, validateStep, resetForm via ref", async () => {
      const ref = React.createRef<React.ComponentRef<typeof TargetingForm>>();
      render(
        <Provider store={store}>
          <TargetingForm ref={ref} />
        </Provider>,
      );
      await waitFor(() => {
        expect(ref.current).not.toBeNull();
      });
      expect(typeof ref.current?.submitForm).toBe("function");
      expect(typeof ref.current?.getFormData).toBe("function");
      expect(typeof ref.current?.isValid).toBe("function");
      expect(typeof ref.current?.validateStep).toBe("function");
      expect(typeof ref.current?.resetForm).toBe("function");
    });

    it("resetForm resets activeTab to demographics", async () => {
      const ref = React.createRef<React.ComponentRef<typeof TargetingForm>>();
      render(
        <Provider store={store}>
          <TargetingForm ref={ref} />
        </Provider>,
      );
      await waitFor(() => expect(ref.current).not.toBeNull());

      // Navigate to geofencing tab
      await userEvent.click(
        screen.getByRole("button", {
          name: /targeting\.tabTitles\.geoFencing/i,
        }),
      );
      await waitFor(() =>
        expect(screen.getByTestId("geofencing-form")).toBeInTheDocument(),
      );

      // Reset and verify demographics tab is active
      ref.current?.resetForm();
      await waitFor(() =>
        expect(screen.getByTestId("demographic-component")).toBeInTheDocument(),
      );
    });
  });

  describe("venue type reconciliation on media channel change", () => {
    const makeStore = (mediaChannels: string[]) =>
      configureStore({
        reducer: {
          campaign: () => ({
            campaignData: {
              mediaChannels,
              targeting: {
                venueTypes: {
                  digitalOoh: ["digital-venue"],
                  classicOoh: ["classic-venue"],
                },
              },
            },
            campaignId: "campaign-1",
          }),
        },
      });

    it("clears classic venue types and autosaves when only Digital OOH is selected", async () => {
      render(
        <Provider store={makeStore(["DIGITAL_OOH"])}>
          <TargetingForm ref={React.createRef()} />
        </Provider>,
      );

      await waitFor(() => {
        expect(mockAutosave).toHaveBeenCalledWith(
          "targeting",
          expect.objectContaining({
            venueTypes: expect.objectContaining({
              digitalOoh: ["digital-venue"],
              classicOoh: [],
            }),
          }),
        );
      });
    });

    it("clears digital venue types and autosaves when only Classic OOH is selected", async () => {
      render(
        <Provider store={makeStore(["CLASSIC_OOH"])}>
          <TargetingForm ref={React.createRef()} />
        </Provider>,
      );

      await waitFor(() => {
        expect(mockAutosave).toHaveBeenCalledWith(
          "targeting",
          expect.objectContaining({
            venueTypes: expect.objectContaining({
              digitalOoh: [],
              classicOoh: ["classic-venue"],
            }),
          }),
        );
      });
    });

    it("does not clear or autosave when both media channels are selected", async () => {
      render(
        <Provider store={makeStore(["DIGITAL_OOH", "CLASSIC_OOH"])}>
          <TargetingForm ref={React.createRef()} />
        </Provider>,
      );

      // Give effects a chance to run.
      await new Promise((resolve) => setTimeout(resolve, 50));

      expect(mockAutosave).not.toHaveBeenCalledWith(
        "targeting",
        expect.anything(),
      );
    });
  });

  describe("inventory cluster reconciliation on media channel change", () => {
    const makeClusterStore = (mediaChannels: string[]) =>
      configureStore({
        reducer: {
          campaign: () => ({
            campaignData: {
              mediaChannels,
              targeting: {
                inventoryCluster: ["DIGITAL", "CLASSIC"],
              },
            },
            campaignId: "campaign-1",
          }),
        },
      });

    it("clears classic inventory clusters and autosaves when only Digital OOH is selected", async () => {
      render(
        <Provider store={makeClusterStore(["DIGITAL_OOH"])}>
          <TargetingForm ref={React.createRef()} />
        </Provider>,
      );

      await waitFor(() => {
        expect(mockAutosave).toHaveBeenCalledWith(
          "targeting",
          expect.objectContaining({
            inventoryCluster: ["DIGITAL"],
          }),
        );
      });
    });

    it("clears digital inventory clusters and autosaves when only Classic OOH is selected", async () => {
      render(
        <Provider store={makeClusterStore(["CLASSIC_OOH"])}>
          <TargetingForm ref={React.createRef()} />
        </Provider>,
      );

      await waitFor(() => {
        expect(mockAutosave).toHaveBeenCalledWith(
          "targeting",
          expect.objectContaining({
            inventoryCluster: ["CLASSIC"],
          }),
        );
      });
    });

    it("does not clear or autosave when both media channels are selected", async () => {
      render(
        <Provider store={makeClusterStore(["DIGITAL_OOH", "CLASSIC_OOH"])}>
          <TargetingForm ref={React.createRef()} />
        </Provider>,
      );

      // Give effects a chance to run.
      await new Promise((resolve) => setTimeout(resolve, 50));

      expect(mockAutosave).not.toHaveBeenCalledWith(
        "targeting",
        expect.objectContaining({
          inventoryCluster: expect.anything(),
        }),
      );
    });
  });
});
