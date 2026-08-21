import { configureStore } from "@reduxjs/toolkit";
import { agencyApi } from "@services/agency/agencySlice";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import CreateCampaignForm from "../CreateCampaignForm";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({ showError: vi.fn(), showSuccess: vi.fn() }),
}));

vi.mock("react-router-dom", async () => {
  const actual =
    await vi.importActual<typeof import("react-router-dom")>(
      "react-router-dom",
    );
  return {
    ...actual,
    useLocation: () => ({ pathname: "/campaigns/create" }),
  };
});

vi.mock("../../../hooks/useStepper", () => ({
  useStepper: () => ({
    isEditMode: false,
    editCampaignId: null,
    setStepperEditMode: vi.fn(),
  }),
}));

const mockAutosave = vi.fn();
const mockAutosaveBatch = vi.fn();
vi.mock("../../../hooks/useAutosave", () => ({
  useAutosave: () => ({
    autosave: mockAutosave,
    autosaveBatch: mockAutosaveBatch,
  }),
}));

const mockCreateCampaign = vi.fn();
vi.mock("../../../services/campaign/campaignSlice", async (importOriginal) => {
  const actual =
    await importOriginal<
      typeof import("../../../services/campaign/campaignSlice")
    >();
  return {
    ...actual,
    useLazyGetSequencerQuery: () => [
      vi.fn().mockReturnValue({
        unwrap: () => Promise.resolve({ success: true, data: 1 }),
      }),
    ],
    useCreateCampaignMutation: () => [mockCreateCampaign, { isLoading: false }],
    useLazyGetAgenciesQuery: () => [vi.fn()],
    useLazyGetAllBrandsQuery: () => [vi.fn()],
    useLazyGetIabCategoriesQuery: () => [vi.fn()],
  };
});

vi.mock("../AgencyCreationForm", () => ({
  default: () => <div data-testid="agency-form">Agency Form</div>,
}));
vi.mock("../BrandCreationForm", () => ({
  default: () => <div data-testid="brand-form">Brand Form</div>,
}));

const store = configureStore({
  reducer: {
    campaign: () => ({ campaignData: null, campaignId: null }),
    profile: () => ({ profile: null }),
    [agencyApi.reducerPath]: agencyApi.reducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware().concat(agencyApi.middleware),
});

function renderCreateCampaignForm(
  props: Partial<React.ComponentProps<typeof CreateCampaignForm>> = {},
) {
  return render(
    <Provider store={store}>
      <MemoryRouter>
        <CreateCampaignForm ref={React.createRef()} {...props} />
      </MemoryRouter>
    </Provider>,
  );
}

describe("CreateCampaignForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCreateCampaign.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: { id: "camp-1", name: "Campaign 1" },
        }),
    });
  });

  describe("rendering", () => {
    it("renders campaign details card and form sections", () => {
      renderCreateCampaignForm();
      expect(
        screen.getByText("create_campaign.steps.campaign_details"),
      ).toBeInTheDocument();
      expect(
        screen.getByLabelText(/create_campaign\.form\.campaign_name/i),
      ).toBeInTheDocument();
      expect(
        screen.getByText("create_campaign.form.brand_optional"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("create_campaign.form.client_type"),
      ).toBeInTheDocument();
    });

    it("does not show agency section when client type is not Agency", () => {
      renderCreateCampaignForm();
      expect(
        screen.queryByTestId("create-campaign-agency-section"),
      ).not.toBeInTheDocument();
    });

    it("defaults the DSP dropdown to None", () => {
      renderCreateCampaignForm();
      // The DSP trigger shows the selected label; default value NONE → dsp_none.
      expect(
        screen.getByText("create_campaign.form.dsp_none"),
      ).toBeInTheDocument();
      expect(
        screen.queryByText("create_campaign.form.select_dsp"),
      ).not.toBeInTheDocument();
    });

    it("hides the DSP recommendation hint while DSP is None", () => {
      renderCreateCampaignForm();
      expect(
        screen.queryByText("create_campaign.form.dsp_recommendation_hint"),
      ).not.toBeInTheDocument();
    });

    it("shows the DSP recommendation hint when a DSP (Activate) is selected", async () => {
      const user = userEvent.setup();
      renderCreateCampaignForm();
      // Open the DSP dropdown and pick Activate.
      await user.click(screen.getByText("create_campaign.form.dsp_none"));
      await user.click(screen.getByText("create_campaign.form.dsp_active"));
      expect(
        await screen.findByText("create_campaign.form.dsp_recommendation_hint"),
      ).toBeInTheDocument();
    });
  });

  describe("ref API", () => {
    it("exposes submitForm, getFormData, isValid, validateStep, resetForm via ref", async () => {
      const ref =
        React.createRef<React.ComponentRef<typeof CreateCampaignForm>>();
      render(
        <Provider store={store}>
          <MemoryRouter>
            <CreateCampaignForm ref={ref} />
          </MemoryRouter>
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
  });

  describe("client type", () => {
    it("has client type dropdown with select placeholder", () => {
      renderCreateCampaignForm();
      expect(
        screen.getByRole("button", {
          name: /create_campaign\.form\.select_client_type|create_campaign\.client_types\./i,
        }),
      ).toBeInTheDocument();
    });
  });

  describe("dsp submission", () => {
    async function fillRequiredFieldsAndSubmit(
      user: ReturnType<typeof userEvent.setup>,
      ref: React.RefObject<React.ComponentRef<
        typeof CreateCampaignForm
      > | null>,
    ) {
      await user.type(
        screen.getByLabelText(/create_campaign\.form\.campaign_name/i),
        "Test Campaign",
      );
      await user.click(
        screen.getByRole("button", {
          name: /create_campaign\.form\.select_client_type/i,
        }),
      );
      await user.click(screen.getByText("create_campaign.client_types.direct"));

      await waitFor(() => {
        expect(ref.current).not.toBeNull();
      });

      return ref.current!.submitForm();
    }

    it("saves dsp as null when the DSP selection is left at None", async () => {
      const user = userEvent.setup();
      const ref =
        React.createRef<React.ComponentRef<typeof CreateCampaignForm>>();
      render(
        <Provider store={store}>
          <MemoryRouter>
            <CreateCampaignForm ref={ref} />
          </MemoryRouter>
        </Provider>,
      );

      const result = await fillRequiredFieldsAndSubmit(user, ref);

      expect(result).toBe(true);
      expect(mockCreateCampaign).toHaveBeenCalledWith(
        expect.objectContaining({ dsp: null }),
      );
    });

    it("saves the selected DSP when one is chosen", async () => {
      const user = userEvent.setup();
      const ref =
        React.createRef<React.ComponentRef<typeof CreateCampaignForm>>();
      render(
        <Provider store={store}>
          <MemoryRouter>
            <CreateCampaignForm ref={ref} />
          </MemoryRouter>
        </Provider>,
      );

      await user.click(screen.getByText("create_campaign.form.dsp_none"));
      await user.click(screen.getByText("create_campaign.form.dsp_active"));

      const result = await fillRequiredFieldsAndSubmit(user, ref);

      expect(result).toBe(true);
      expect(mockCreateCampaign).toHaveBeenCalledWith(
        expect.objectContaining({ dsp: "ACTIVATE" }),
      );
    });
  });
});
