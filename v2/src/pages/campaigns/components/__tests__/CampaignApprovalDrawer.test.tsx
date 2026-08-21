import { configureStore } from "@reduxjs/toolkit";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { Provider } from "react-redux";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { CampaignApprovalDrawer } from "../CampaignApprovalDrawer";

const mockGetApprovalDetails = vi.fn();
const mockUpdateApprovalStatus = vi
  .fn()
  .mockReturnValue({ unwrap: () => Promise.resolve() });

const mockQueryState: {
  data?: unknown;
  isLoading: boolean;
  isError: boolean;
} = {
  data: undefined,
  isLoading: false,
  isError: false,
};

vi.mock("@services/campaign/campaignSlice", () => ({
  default: vi.fn((state = {}) => state),
  campaignApi: {
    reducerPath: "campaignApi",
    reducer: vi.fn((state = {}) => state),
    middleware: vi.fn(
      () => (next: (action: unknown) => unknown) => (action: unknown) =>
        next(action),
    ),
  },
  companyApi: {
    reducerPath: "companyApi",
    reducer: vi.fn((state = {}) => state),
    middleware: vi.fn(
      () => (next: (action: unknown) => unknown) => (action: unknown) =>
        next(action),
    ),
  },
  useLazyGetCampaignApprovalDetailsQuery: () => [
    mockGetApprovalDetails,
    {
      get data() {
        return mockQueryState.data;
      },
      get isLoading() {
        return mockQueryState.isLoading;
      },
      get isError() {
        return mockQueryState.isError;
      },
    },
  ],
  useUpdateApprovalStatusMutation: () => [mockUpdateApprovalStatus],
}));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

// Mock user profile state
const mockUserProfile = {
  is_global_admin: false,
};

const defaultProps = {
  isOpen: true,
  onClose: vi.fn(),
  campaignId: "camp-1",
};

// Create a mock Redux store
const createMockStore = (
  userProfile: { is_global_admin: boolean } = mockUserProfile,
) => {
  return configureStore({
    reducer: {
      profile: vi.fn((state = { profile: userProfile }) => state),
    },
  });
};

const mockApprovalDetailsData = {
  campaignName: "Test Campaign",
  campaignId: "camp-1",
  planNumber: "PLN-0001",
  status: "PLANNED",
  budget: 10000,
  startDate: "2025-01-01",
  endDate: "2025-01-31",
  approvalPermissions: ["AGENCY", "INTERNAL"],
  approvalProgress: [
    {
      id: "progress-1",
      approvalAuthority: "AGENCY",
      status: "IN_PROGRESS",
      updatedBy: "User",
      updatedAt: "2025-01-01 10:00:00",
      comment: "",
    },
  ],
};

function renderDrawer(
  props: Partial<React.ComponentProps<typeof CampaignApprovalDrawer>> = {},
  userProfile: { is_global_admin: boolean } = mockUserProfile,
) {
  const store = createMockStore(userProfile);
  return render(
    <Provider store={store}>
      <CampaignApprovalDrawer {...defaultProps} {...props} />
    </Provider>,
  );
}

describe("CampaignApprovalDrawer", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockQueryState.data = undefined;
    mockQueryState.isLoading = false;
    mockQueryState.isError = false;
  });

  describe("visibility and fetch", () => {
    it("does not fetch when isOpen is false", () => {
      renderDrawer({ isOpen: false });
      expect(mockGetApprovalDetails).not.toHaveBeenCalled();
    });

    it("fetches approval details when isOpen and campaignId are set", () => {
      renderDrawer();
      expect(mockGetApprovalDetails).toHaveBeenCalledWith({
        campaignId: "camp-1",
        activeCompanyId: "",
      });
    });
  });

  describe("loading and error states", () => {
    it("renders loading text when isLoading is true", () => {
      mockQueryState.data = undefined;
      mockQueryState.isLoading = true;
      mockQueryState.isError = false;
      renderDrawer();
      expect(screen.getByText("approval.loading")).toBeInTheDocument();
    });

    it("renders error text when isError is true", () => {
      mockQueryState.data = undefined;
      mockQueryState.isLoading = false;
      mockQueryState.isError = true;
      renderDrawer();
      expect(screen.getByText("approval.error")).toBeInTheDocument();
    });
  });

  describe("content when data is loaded", () => {
    beforeEach(() => {
      mockQueryState.data = { success: true, data: mockApprovalDetailsData };
      mockQueryState.isLoading = false;
      mockQueryState.isError = false;
    });

    it("renders campaign details section when data is loaded", () => {
      renderDrawer();
      expect(screen.getByText("Test Campaign")).toBeInTheDocument();
      expect(screen.getByText("PLN-0001")).toBeInTheDocument();
    });

    it("does not render the plan ID section when planNumber is missing", () => {
      mockQueryState.data = {
        success: true,
        data: { ...mockApprovalDetailsData, planNumber: undefined },
      };
      renderDrawer();
      expect(
        screen.queryByText("approval.campaign_id"),
      ).not.toBeInTheDocument();
    });

    it("renders Approve and Reject buttons", () => {
      renderDrawer();
      expect(
        screen.getByRole("button", { name: "approval.reject" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: "approval.approve" }),
      ).toBeInTheDocument();
    });

    it("opens approve modal when Approve is clicked", async () => {
      const user = userEvent.setup();
      renderDrawer();
      await user.click(
        screen.getByRole("button", { name: "approval.approve" }),
      );
      await waitFor(() => {
        expect(
          screen.getByText(/Confirm Approval|approval\.modal\.approve\.title/),
        ).toBeInTheDocument();
      });
    });

    it("opens reject modal when Reject is clicked", async () => {
      const user = userEvent.setup();
      renderDrawer();
      await user.click(screen.getByRole("button", { name: "approval.reject" }));
      await waitFor(() => {
        expect(
          screen.getByText(/Confirm Rejection|approval\.modal\.reject\.title/),
        ).toBeInTheDocument();
      });
    });

    it("closes reject modal when Cancel is clicked", async () => {
      const user = userEvent.setup();
      renderDrawer();
      await user.click(screen.getByRole("button", { name: "approval.reject" }));
      await waitFor(() =>
        expect(
          screen.getByText(/approval\.modal\.reject\.title|Confirm Rejection/),
        ).toBeInTheDocument(),
      );
      const cancelBtn = screen.getByRole("button", {
        name: /approval\.modal\.reject\.no|No, Don't/i,
      });
      await user.click(cancelBtn);
      await waitFor(() => {
        expect(
          screen.queryByText(
            /approval\.modal\.reject\.title|Confirm Rejection/,
          ),
        ).not.toBeInTheDocument();
      });
    });

    it("reject submit button is disabled when reason is empty", async () => {
      const user = userEvent.setup();
      renderDrawer();
      await user.click(screen.getByRole("button", { name: "approval.reject" }));
      await waitFor(() =>
        expect(
          screen.getByText(/approval\.modal\.reject\.title|Confirm Rejection/),
        ).toBeInTheDocument(),
      );
      const submitBtn = screen.getByRole("button", {
        name: /approval\.modal\.reject\.yes|Yes, Reject/i,
      });
      expect(submitBtn).toBeDisabled();
    });

    it("calls updateApprovalStatus and onApprove when confirm approve", async () => {
      const onApprove = vi.fn();
      const user = userEvent.setup();
      renderDrawer({ onApprove });
      await user.click(
        screen.getByRole("button", { name: "approval.approve" }),
      );
      await waitFor(() =>
        expect(
          screen.getByText(/approval\.modal\.approve\.title|Confirm Approval/),
        ).toBeInTheDocument(),
      );
      const confirmBtn = screen.getByRole("button", {
        name: /approval\.modal\.approve\.yes|Yes, Approve/i,
      });
      await user.click(confirmBtn);
      await waitFor(() => {
        expect(mockUpdateApprovalStatus).toHaveBeenCalledWith(
          expect.objectContaining({ status: "APPROVED" }),
        );
        expect(onApprove).toHaveBeenCalledWith("camp-1", "progress-1");
      });
    });

    it("calls updateApprovalStatus and onReject when confirm reject with reason", async () => {
      const onReject = vi.fn();
      const user = userEvent.setup();
      renderDrawer({ onReject });
      await user.click(screen.getByRole("button", { name: "approval.reject" }));
      await waitFor(() =>
        expect(
          screen.getByText(/approval\.modal\.reject\.title|Confirm Rejection/),
        ).toBeInTheDocument(),
      );
      const textbox = screen.getByRole("textbox");
      await user.type(textbox, "Not approved");
      const submitBtn = screen.getByRole("button", {
        name: /approval\.modal\.reject\.yes|Yes, Reject/i,
      });
      await user.click(submitBtn);
      await waitFor(() => {
        expect(mockUpdateApprovalStatus).toHaveBeenCalledWith(
          expect.objectContaining({
            status: "REJECTED",
            comment: "Not approved",
          }),
        );
        expect(onReject).toHaveBeenCalledWith(
          "camp-1",
          "Not approved",
          "progress-1",
        );
      });
    });
  });

  describe("no approval progress", () => {
    beforeEach(() => {
      mockQueryState.data = {
        success: true,
        data: { ...mockApprovalDetailsData, approvalProgress: [] },
      };
      mockQueryState.isLoading = false;
      mockQueryState.isError = false;
    });

    it("disables Approve and Reject when no progress", () => {
      renderDrawer();
      expect(
        screen.getByRole("button", { name: "approval.reject" }),
      ).toBeDisabled();
      expect(
        screen.getByRole("button", { name: "approval.approve" }),
      ).toBeDisabled();
    });
  });
  describe("super admin permissions", () => {
    const superAdminProfile = {
      is_global_admin: true,
    };

    beforeEach(() => {
      // Setup approval data with MEDIA_OWNER in progress (user only has AGENCY, INTERNAL permissions)
      mockQueryState.data = {
        success: true,
        data: {
          ...mockApprovalDetailsData,
          approvalPermissions: ["AGENCY", "INTERNAL"], // User doesn't have MEDIA_OWNER permission
          approvalProgress: [
            {
              id: "progress-1",
              approvalAuthority: "AGENCY",
              status: "COMPLETED",
              updatedBy: "User 1",
              updatedAt: "2025-01-01 10:00:00",
              comment: "",
            },
            {
              id: "progress-2",
              approvalAuthority: "INTERNAL",
              status: "COMPLETED",
              updatedBy: "User 2",
              updatedAt: "2025-01-02 10:00:00",
              comment: "",
            },
            {
              id: "progress-3",
              approvalAuthority: "MEDIA_OWNER",
              status: "IN_PROGRESS",
              updatedBy: "",
              updatedAt: "",
              comment: "",
            },
          ],
        },
      };
      mockQueryState.isLoading = false;
      mockQueryState.isError = false;
    });

    it("enables approve/reject buttons for super admin even without matching authority", () => {
      renderDrawer({}, superAdminProfile);
      expect(
        screen.getByRole("button", { name: "approval.reject" }),
      ).not.toBeDisabled();
      expect(
        screen.getByRole("button", { name: "approval.approve" }),
      ).not.toBeDisabled();
    });

    it("allows super admin to approve any IN_PROGRESS authority", async () => {
      const onApprove = vi.fn();
      const user = userEvent.setup();
      renderDrawer({ onApprove }, superAdminProfile);

      await user.click(
        screen.getByRole("button", { name: "approval.approve" }),
      );
      await waitFor(() =>
        expect(
          screen.getByText(/approval\.modal\.approve\.title|Confirm Approval/),
        ).toBeInTheDocument(),
      );
      const confirmBtn = screen.getByRole("button", {
        name: /approval\.modal\.approve\.yes|Yes, Approve/i,
      });
      await user.click(confirmBtn);

      await waitFor(() => {
        expect(mockUpdateApprovalStatus).toHaveBeenCalledWith(
          expect.objectContaining({
            status: "APPROVED",
            inProgressId: "progress-3", // MEDIA_OWNER progress
          }),
        );
        expect(onApprove).toHaveBeenCalledWith("camp-1", "progress-3");
      });
    });

    it("allows super admin to reject any IN_PROGRESS authority", async () => {
      const onReject = vi.fn();
      const user = userEvent.setup();
      renderDrawer({ onReject }, superAdminProfile);

      await user.click(screen.getByRole("button", { name: "approval.reject" }));
      await waitFor(() =>
        expect(
          screen.getByText(/approval\.modal\.reject\.title|Confirm Rejection/),
        ).toBeInTheDocument(),
      );
      const textbox = screen.getByRole("textbox");
      await user.type(textbox, "Super admin rejection");
      const submitBtn = screen.getByRole("button", {
        name: /approval\.modal\.reject\.yes|Yes, Reject/i,
      });
      await user.click(submitBtn);

      await waitFor(() => {
        expect(mockUpdateApprovalStatus).toHaveBeenCalledWith(
          expect.objectContaining({
            status: "REJECTED",
            comment: "Super admin rejection",
            inProgressId: "progress-3", // MEDIA_OWNER progress
          }),
        );
        expect(onReject).toHaveBeenCalledWith(
          "camp-1",
          "Super admin rejection",
          "progress-3",
        );
      });
    });
  });
});
