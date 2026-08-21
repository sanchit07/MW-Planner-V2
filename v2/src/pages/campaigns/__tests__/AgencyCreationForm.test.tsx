import { configureStore } from "@reduxjs/toolkit";
import { agencyApi } from "@services/agency/agencySlice";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { describe, it, expect, vi, beforeEach } from "vitest";

import AgencyCreationForm from "../AgencyCreationForm";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const showSuccess = vi.fn();
vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({ showSuccess }),
}));

const mockCreateAgency = vi.fn();
const mockLinkAgency = vi.fn();
const mockSearchAgencies = vi.fn();
vi.mock("../../../services/agency/agencySlice", async (importOriginal) => {
  const actual =
    await importOriginal<
      typeof import("../../../services/agency/agencySlice")
    >();
  return {
    ...actual,
    useCreateAgencyMutation: () => [mockCreateAgency, { isLoading: false }],
    useLinkAgencyMutation: () => [mockLinkAgency, { isLoading: false }],
    useLazyGetAgenciesQuery: () => [mockSearchAgencies],
  };
});

const store = configureStore({
  reducer: {
    // Add your reducers here, e.g.:
    // agency: agencyReducer,
    // or a dummy reducer if not needed for this test
    dummy: (state = {}) => state,
    profile: () => ({
      profile: {
        current_company: {
          id: "company-1",
          company_type: { code: "AGENCY" },
          name: "Test Agency",
        },
        // add other fields as needed for your component
      },
    }),
    [agencyApi.reducerPath]: agencyApi.reducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware().concat(agencyApi.middleware),
});

function renderAgencyForm(
  props: Partial<React.ComponentProps<typeof AgencyCreationForm>> = {},
) {
  return render(
    <Provider store={store}>
      <AgencyCreationForm isOpen={true} onClose={vi.fn()} {...props} />
    </Provider>,
  );
}

describe("AgencyCreationForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCreateAgency.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          data: { id: "agency-1" },
        }),
    });
    mockLinkAgency.mockReturnValue({
      unwrap: () => Promise.resolve({ success: true }),
    });
    mockSearchAgencies.mockReturnValue({
      unwrap: () => Promise.resolve({ data: [] }),
    });
  });

  describe("rendering", () => {
    it("renders nothing when isOpen is false", () => {
      renderAgencyForm({ isOpen: false, onClose: vi.fn() });
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    it("renders drawer title when open", () => {
      renderAgencyForm();
      expect(screen.getByText("agency_form.title")).toBeInTheDocument();
    });

    it("renders name, email and domain fields when open", () => {
      renderAgencyForm();
      expect(screen.getByLabelText(/agency_form\.name/i)).toBeInTheDocument();
      expect(
        screen.getByLabelText(/agency_form\.company_email/i),
      ).toBeInTheDocument();
      expect(screen.getByText("agency_form.domain")).toBeInTheDocument();
    });

    it("renders Cancel and Create buttons", () => {
      renderAgencyForm();
      expect(
        screen.getByRole("button", { name: /agency_form\.cancel/i }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /agency_form\.create/i }),
      ).toBeInTheDocument();
    });
  });

  describe("interactions", () => {
    it("calls onClose when Cancel is clicked", async () => {
      const onClose = vi.fn();
      renderAgencyForm({ onClose });
      await userEvent.click(
        screen.getByRole("button", { name: /agency_form\.cancel/i }),
      );
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it("submits form and calls onSuccess with agency id on success", async () => {
      const onSuccess = vi.fn();
      const onClose = vi.fn();
      renderAgencyForm({ onSuccess, onClose });
      await userEvent.type(
        screen.getByPlaceholderText(/agency_form\.name_placeholder/i),
        "Agency A",
      );
      await userEvent.type(
        screen.getByPlaceholderText(/agency_form\.company_email_placeholder/i),
        "test@agency.com",
      );
      await userEvent.type(
        screen.getByPlaceholderText(/agency_form\.domain_placeholder/i),
        "agency-domain",
      );
      await userEvent.click(
        screen.getByRole("button", { name: /agency_form\.create/i }),
      );
      await waitFor(
        () => {
          expect(mockCreateAgency).toHaveBeenCalled();
        },
        { timeout: 3000 },
      );
      await waitFor(
        () => {
          expect(onSuccess).toHaveBeenCalledWith("agency-1", "Agency A");
        },
        { timeout: 3000 },
      );
      expect(showSuccess).toHaveBeenCalledWith("agencyForm.success");
      expect(onClose).toHaveBeenCalled();
    }, 10000);

    it("calls onSubmit with form data when provided and submit succeeds", async () => {
      const onSubmit = vi.fn();
      renderAgencyForm({ onSubmit });
      await userEvent.type(
        screen.getByPlaceholderText(/agency_form\.name_placeholder/i),
        "Agency B",
      );
      await userEvent.type(
        screen.getByPlaceholderText(/agency_form\.company_email_placeholder/i),
        "b@agency.com",
      );
      await userEvent.type(
        screen.getByPlaceholderText(/agency_form\.domain_placeholder/i),
        "agency-domain",
      );
      await userEvent.click(
        screen.getByRole("button", { name: /agency_form\.create/i }),
      );
      await waitFor(
        () => {
          expect(onSubmit).toHaveBeenCalledWith(
            expect.objectContaining({
              name: "Agency B",
              companyEmail: "b@agency.com",
              domain: "agency-domain",
            }),
          );
        },
        { timeout: 3000 },
      );
    }, 10000);

    it("does not call onSuccess or onClose when create fails", async () => {
      mockCreateAgency.mockRejectedValueOnce(new Error("API error"));
      const onSuccess = vi.fn();
      const onClose = vi.fn();
      const consoleSpy = vi
        .spyOn(console, "error")
        .mockImplementation(() => {});
      renderAgencyForm({ onSuccess, onClose });
      await userEvent.type(
        screen.getByPlaceholderText(/agency_form\.name_placeholder/i),
        "A",
      );
      await userEvent.type(
        screen.getByPlaceholderText(/agency_form\.company_email_placeholder/i),
        "a@b.com",
      );
      await userEvent.type(
        screen.getByPlaceholderText(/agency_form\.domain_placeholder/i),
        "agency-domain",
      );
      await userEvent.click(
        screen.getByRole("button", { name: /agency_form\.create/i }),
      );
      await waitFor(() => {
        expect(mockCreateAgency).toHaveBeenCalled();
      });
      expect(onSuccess).not.toHaveBeenCalled();
      expect(onClose).not.toHaveBeenCalled();
      expect(showSuccess).not.toHaveBeenCalled();
      consoleSpy.mockRestore();
    });
  });

  describe("link existing agency", () => {
    it("searches without a company_id, shows a matching suggestion, and links it", async () => {
      mockSearchAgencies.mockReturnValue({
        unwrap: () =>
          Promise.resolve({
            data: [{ id: "agency-9", name: "Acme Agency", activated: true }],
          }),
      });
      const onSuccess = vi.fn();
      const onClose = vi.fn();
      renderAgencyForm({ onSuccess, onClose, companyId: "company-1" });

      await userEvent.type(
        screen.getByPlaceholderText(/agency_form\.name_placeholder/i),
        "Acme Agency",
      );

      await waitFor(
        () => {
          expect(mockSearchAgencies).toHaveBeenCalledWith({
            search: "Acme Agency",
            all: true,
          });
        },
        { timeout: 3000 },
      );

      await waitFor(() => {
        expect(screen.getByText("Acme Agency")).toBeInTheDocument();
      });

      await userEvent.click(screen.getByText("Acme Agency"));

      expect(screen.getByText("agency_form.title_link")).toBeInTheDocument();
      expect(screen.getByText("agency_form.link_notice")).toBeInTheDocument();
      expect(screen.getByText("agency_form.active")).toBeInTheDocument();
      // No country/media owner on this agency - the subtitle line under the
      // name shouldn't render an empty/dangling separator.
      expect(screen.queryByText("agency_form.country")).not.toBeInTheDocument();
      expect(
        screen.queryByText("agency_form.media_owner"),
      ).not.toBeInTheDocument();

      await userEvent.click(
        screen.getByRole("button", { name: /agency_form\.link/i }),
      );

      await waitFor(() => {
        expect(mockLinkAgency).toHaveBeenCalledWith({
          agencyData: {
            agency_id: "agency-9",
            campaign_approval: "manual",
            creative_approval: "manual",
          },
          id: "company-1",
        });
      });
      expect(showSuccess).toHaveBeenCalledWith(
        "agency_form.link_agency_success",
      );
      expect(onSuccess).toHaveBeenCalledWith("agency-9", "Acme Agency");
      expect(onClose).toHaveBeenCalled();
    }, 10000);

    it("shows country and media owner as a single subtitle line under the name", async () => {
      mockSearchAgencies.mockReturnValue({
        unwrap: () =>
          Promise.resolve({
            data: [
              {
                id: "agency-9",
                name: "OMD Asia Pacific",
                activated: true,
                countryName: "Singapore",
                mediaOwnerName: "Moving Walls",
              },
            ],
          }),
      });
      renderAgencyForm({ companyId: "company-1" });

      await userEvent.type(
        screen.getByPlaceholderText(/agency_form\.name_placeholder/i),
        "OMD Asia Pacific",
      );

      await waitFor(() => {
        expect(screen.getByText("OMD Asia Pacific")).toBeInTheDocument();
      });
      await userEvent.click(screen.getByText("OMD Asia Pacific"));

      expect(screen.getByText("Singapore · Moving Walls")).toBeInTheDocument();
    }, 10000);

    it("disables Create and shows an error when name matches a company agency", async () => {
      renderAgencyForm({
        companyAgencies: [{ id: "agency-1", label: "Existing Agency" }],
      });

      await userEvent.type(
        screen.getByPlaceholderText(/agency_form\.name_placeholder/i),
        "Existing Agency",
      );

      await waitFor(() => {
        expect(
          screen.getByText("agency_form.own_agency_exists"),
        ).toBeInTheDocument();
      });
      expect(
        screen.getByRole("button", { name: /agency_form\.create/i }),
      ).toBeDisabled();
    });
  });

  describe("accessibility", () => {
    it("has name and email inputs with labels", () => {
      renderAgencyForm();
      expect(screen.getByLabelText(/agency_form\.name/i)).toBeInTheDocument();
      expect(
        screen.getByLabelText(/agency_form\.company_email/i),
      ).toBeInTheDocument();
    });
  });
});
