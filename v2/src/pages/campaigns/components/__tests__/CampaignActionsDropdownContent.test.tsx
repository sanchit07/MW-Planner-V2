import { Dropdown, DropdownTrigger } from "@components/ui/Dropdown";
import { configureStore } from "@reduxjs/toolkit";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { CampaignActionsDropdownContent } from "../CampaignActionsDropdownContent";

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return { ...actual, useNavigate: () => mockNavigate };
});

const mockShowSuccess = vi.fn();
const mockShowError = vi.fn();
vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({
    showSuccess: mockShowSuccess,
    showError: mockShowError,
  }),
}));

const mockBulkActions = vi
  .fn()
  .mockReturnValue({ unwrap: () => Promise.resolve() });
const mockDeleteCampaign = vi
  .fn()
  .mockReturnValue({ unwrap: () => Promise.resolve() });
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
  useBulkActionsCampaignMutation: () => [mockBulkActions],
  useDeleteCampaignMutation: () => [mockDeleteCampaign],
}));

const mockGeneratePublicToken = vi.fn().mockReturnValue({
  unwrap: () =>
    Promise.resolve({ success: true, data: { publicToken: "mock-token" } }),
});
vi.mock("@services/public-access/publicAccessSlice", () => ({
  publicAccessApi: {
    reducerPath: "publicAccessApi",
    reducer: vi.fn((state = {}) => state),
    middleware: vi.fn(
      () => (next: (action: unknown) => unknown) => (action: unknown) =>
        next(action),
    ),
  },
  publicInventoryApi: {
    reducerPath: "publicInventoryApi",
    reducer: vi.fn((state = {}) => state),
    middleware: vi.fn(
      () => (next: (action: unknown) => unknown) => (action: unknown) =>
        next(action),
    ),
  },
  useGeneratePublicTokenMutation: () => [mockGeneratePublicToken],
}));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("../CampaignApprovalDrawer", () => ({
  CampaignApprovalDrawer: () => <div data-testid="campaign-approval-drawer" />,
}));

// ─── Share Modal Mock ──────────────────────────────────────────────────────────
// Captures shareUrl/isOpen; renders a testid element + close button when open
let capturedShareUrl = "";

vi.mock("../../media-plan/ShareModalDrawer", () => ({
  default: (props: {
    isOpen: boolean;
    onClose: () => void;
    shareUrl: string;
  }) => {
    capturedShareUrl = props.shareUrl;
    if (!props.isOpen) return null;
    return (
      <div data-testid="share-modal-drawer">
        <button onClick={props.onClose} data-testid="share-modal-close">
          Close
        </button>
      </div>
    );
  },
}));

// ─── PPT Downloader Mock ───────────────────────────────────────────────────────
// Download feature is disabled; the downloader never mounts. Mock kept as a stub.
vi.mock("../CampaignPPTDownloader", () => ({
  CampaignPPTDownloader: (props: { campaignId: string }) => {
    return (
      <div data-testid="ppt-downloader" data-campaign-id={props.campaignId} />
    );
  },
}));

const mockProfile = {
  username: "test-user",
  current_company: { role_name: "Administrator" },
};

function createMockStore() {
  return configureStore({
    reducer: {
      profile: (state = { profile: mockProfile }) => state,
    },
  });
}

function renderDropdown(
  props: Partial<
    React.ComponentProps<typeof CampaignActionsDropdownContent>
  > = {},
) {
  return render(
    <Provider store={createMockStore()}>
      <MemoryRouter>
        <Dropdown>
          <DropdownTrigger asChild>
            <button type="button">Open menu</button>
          </DropdownTrigger>
          <CampaignActionsDropdownContent
            campaignId="camp-1"
            campaignData={{ status: "planned" }}
            {...props}
          />
        </Dropdown>
      </MemoryRouter>
    </Provider>,
  );
}

async function openDropdown() {
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: /open menu/i }));
}

describe("CampaignActionsDropdownContent", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    capturedShareUrl = "";
  });

  describe("rendering", () => {
    it("renders view details item when dropdown is open", async () => {
      renderDropdown({ campaignData: { status: "planned" } });
      await openDropdown();
      expect(
        screen.getByRole("menuitem", { name: /card\.view_details/i }),
      ).toBeInTheDocument();
    });

    it("renders edit item when dropdown is open", async () => {
      renderDropdown();
      await openDropdown();
      expect(
        screen.getByRole("menuitem", { name: /card\.edit/i }),
      ).toBeInTheDocument();
    });

    it("renders CampaignApprovalDrawer", () => {
      renderDropdown();
      expect(
        screen.getByTestId("campaign-approval-drawer"),
      ).toBeInTheDocument();
    });

    it("hides view when hideNavigation includes view", async () => {
      renderDropdown({ hideNavigation: ["view"] });
      await openDropdown();
      expect(
        screen.queryByRole("menuitem", { name: /card\.view_details/i }),
      ).not.toBeInTheDocument();
    });

    it("hides item case-insensitively", async () => {
      renderDropdown({ hideNavigation: ["VIEW"] });
      await openDropdown();
      expect(
        screen.queryByRole("menuitem", { name: /card\.view_details/i }),
      ).not.toBeInTheDocument();
    });

    it("disables view when status is draft and no permission override", async () => {
      renderDropdown({ campaignData: { status: "draft" } });
      await openDropdown();
      const viewItem = screen.getByRole("menuitem", {
        name: /card\.view_details/i,
      });
      expect(viewItem).toBeDisabled();
    });

    it("enables view when status is not draft", async () => {
      renderDropdown({ campaignData: { status: "planned" } });
      await openDropdown();
      const viewItem = screen.getByRole("menuitem", {
        name: /card\.view_details/i,
      });
      expect(viewItem).not.toBeDisabled();
    });

    // PL3-I12: Reserve Inventories is an undeveloped stub (no handler/route/API
    // is ever wired). It is hidden entirely until the feature is built.
    it("does not render reserve inventories (hidden, unimplemented)", async () => {
      renderDropdown({ campaignData: { status: "planned" } });
      await openDropdown();
      expect(
        screen.queryByRole("menuitem", {
          name: /card\.reserve_inventories/i,
        }),
      ).not.toBeInTheDocument();
    });

    it("overrides permission when permissions.view is true for draft", async () => {
      renderDropdown({
        campaignData: { status: "draft" },
        permissions: { view: true },
      });
      await openDropdown();
      const viewItem = screen.getByRole("menuitem", {
        name: /card\.view_details/i,
      });
      expect(viewItem).not.toBeDisabled();
    });
  });

  describe("navigation", () => {
    it("navigates to view when view details is clicked", async () => {
      const user = userEvent.setup();
      renderDropdown({ campaignData: { status: "planned" } });
      await openDropdown();
      await user.click(
        screen.getByRole("menuitem", { name: /card\.view_details/i }),
      );
      expect(mockNavigate).toHaveBeenCalledWith("/campaigns/view/camp-1");
    });

    it("opens edit confirmation modal when edit is clicked", async () => {
      const user = userEvent.setup();
      renderDropdown();
      await openDropdown();
      await user.click(screen.getByRole("menuitem", { name: /card\.edit/i }));
      await waitFor(() => {
        expect(
          screen.getByText("campaignActions.editModal.title"),
        ).toBeInTheDocument();
      });
      expect(mockNavigate).not.toHaveBeenCalled();
    });

    it("navigates to media plan when view media plan is clicked", async () => {
      const user = userEvent.setup();
      renderDropdown();
      await openDropdown();
      await user.click(
        screen.getByRole("menuitem", { name: /card\.view_media_plan/i }),
      );
      expect(mockNavigate).toHaveBeenCalledWith("/campaigns/media-plan/camp-1");
    });

    it("navigates to price management when price management is clicked", async () => {
      const user = userEvent.setup();
      renderDropdown();
      await openDropdown();
      await user.click(
        screen.getByRole("menuitem", { name: /card\.price_management/i }),
      );
      expect(mockNavigate).toHaveBeenCalledWith(
        "/campaigns/price-management/camp-1",
      );
    });
  });

  describe("confirmation modal", () => {
    it("opens duplicate confirmation when duplicate is clicked", async () => {
      const user = userEvent.setup();
      renderDropdown();
      await openDropdown();
      await user.click(
        screen.getByRole("menuitem", { name: /card\.duplicate/i }),
      );
      await waitFor(() => {
        expect(
          screen.getByText("campaignActions.duplicateModal.title"),
        ).toBeInTheDocument();
      });
      expect(
        screen.getByText("campaignActions.duplicateModal.message"),
      ).toBeInTheDocument();
    });

    it("opens delete confirmation when delete is clicked", async () => {
      const user = userEvent.setup();
      renderDropdown({ campaignData: { status: "draft" } });
      await openDropdown();
      await user.click(screen.getByRole("menuitem", { name: /card\.delete/i }));
      await waitFor(() => {
        expect(
          screen.getByText("campaignActions.deleteModal.title"),
        ).toBeInTheDocument();
      });
    });

    it("opens archive confirmation when archive is clicked", async () => {
      const user = userEvent.setup();
      renderDropdown();
      await openDropdown();
      await user.click(
        screen.getByRole("menuitem", { name: /card\.archive/i }),
      );
      await waitFor(() => {
        expect(
          screen.getByText("campaignActions.archiveModal.title"),
        ).toBeInTheDocument();
      });
    });

    it("opens edit confirmation when edit is clicked", async () => {
      const user = userEvent.setup();
      renderDropdown();
      await openDropdown();
      await user.click(screen.getByRole("menuitem", { name: /card\.edit/i }));
      await waitFor(() => {
        expect(
          screen.getByText("campaignActions.editModal.title"),
        ).toBeInTheDocument();
      });
      expect(
        screen.getByText("campaignActions.editModal.message4"),
      ).toBeInTheDocument();
    });

    it("navigates to edit page when edit is confirmed", async () => {
      const user = userEvent.setup();
      renderDropdown();
      await openDropdown();
      await user.click(screen.getByRole("menuitem", { name: /card\.edit/i }));
      await waitFor(() =>
        expect(
          screen.getByText("campaignActions.editModal.primaryButton"),
        ).toBeInTheDocument(),
      );
      await user.click(
        screen.getByText("campaignActions.editModal.primaryButton"),
      );
      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith("/campaigns/edit/camp-1");
      });
    });

    it("closes edit modal when cancel is clicked", async () => {
      const user = userEvent.setup();
      renderDropdown();
      await openDropdown();
      await user.click(screen.getByRole("menuitem", { name: /card\.edit/i }));
      await waitFor(() =>
        expect(
          screen.getByText("campaignActions.editModal.primaryButton"),
        ).toBeInTheDocument(),
      );
      await user.click(screen.getByText("campaignActions.cancel"));
      await waitFor(() => {
        expect(
          screen.queryByText("campaignActions.editModal.title"),
        ).not.toBeInTheDocument();
      });
      expect(mockNavigate).not.toHaveBeenCalled();
    });

    it("calls deleteCampaign and showSuccess when delete is confirmed", async () => {
      const user = userEvent.setup();
      renderDropdown({ campaignData: { status: "draft" } });
      await openDropdown();
      await user.click(screen.getByRole("menuitem", { name: /card\.delete/i }));
      await waitFor(() =>
        expect(
          screen.getByText("campaignActions.deleteModal.primaryButton"),
        ).toBeInTheDocument(),
      );
      await user.click(
        screen.getByText("campaignActions.deleteModal.primaryButton"),
      );
      await waitFor(() => {
        expect(mockDeleteCampaign).toHaveBeenCalledWith("camp-1");
        expect(mockShowSuccess).toHaveBeenCalledWith(
          "campaignActions.deleteSuccess",
        );
      });
    });

    it("calls bulkActionsCampaign and showSuccess when duplicate is confirmed", async () => {
      const user = userEvent.setup();
      renderDropdown();
      await openDropdown();
      await user.click(
        screen.getByRole("menuitem", { name: /card\.duplicate/i }),
      );
      await waitFor(() =>
        expect(
          screen.getByText("campaignActions.duplicateModal.primaryButton"),
        ).toBeInTheDocument(),
      );
      await user.click(
        screen.getByText("campaignActions.duplicateModal.primaryButton"),
      );
      await waitFor(() => {
        expect(mockBulkActions).toHaveBeenCalledWith({
          campaignIds: ["camp-1"],
          action: "DUPLICATE",
        });
        expect(mockShowSuccess).toHaveBeenCalledWith(
          "campaignActions.duplicatedSuccess",
        );
        expect(mockNavigate).toHaveBeenCalledWith("/campaigns");
      });
    });

    it("calls showError when delete fails", async () => {
      mockDeleteCampaign.mockReturnValueOnce({
        unwrap: () => Promise.reject(new Error("Network error")),
      });
      const user = userEvent.setup();
      renderDropdown({ campaignData: { status: "draft" } });
      await openDropdown();
      await user.click(screen.getByRole("menuitem", { name: /card\.delete/i }));
      await waitFor(() =>
        expect(
          screen.getByText("campaignActions.deleteModal.primaryButton"),
        ).toBeInTheDocument(),
      );
      await user.click(
        screen.getByText("campaignActions.deleteModal.primaryButton"),
      );
      await waitFor(() => {
        expect(mockShowError).toHaveBeenCalledWith(
          "campaignActions.deleteError",
        );
      });
    });

    it("closes modal when cancel is clicked", async () => {
      const user = userEvent.setup();
      renderDropdown();
      await openDropdown();
      await user.click(
        screen.getByRole("menuitem", { name: /card\.duplicate/i }),
      );
      await waitFor(() =>
        expect(screen.getByText("campaignActions.cancel")).toBeInTheDocument(),
      );
      await user.click(screen.getByText("campaignActions.cancel"));
      await waitFor(() => {
        expect(
          screen.queryByText("campaignActions.duplicateModal.title"),
        ).not.toBeInTheDocument();
      });
    });

    it("calls onRefresh after successful confirm", async () => {
      const onRefresh = vi.fn();
      const user = userEvent.setup();
      renderDropdown({
        campaignData: { status: "draft" },
        handlers: { onRefresh },
      });
      await openDropdown();
      await user.click(screen.getByRole("menuitem", { name: /card\.delete/i }));
      await waitFor(() =>
        expect(
          screen.getByText("campaignActions.deleteModal.primaryButton"),
        ).toBeInTheDocument(),
      );
      await user.click(
        screen.getByText("campaignActions.deleteModal.primaryButton"),
      );
      await waitFor(
        () => {
          expect(mockDeleteCampaign).toHaveBeenCalled();
          expect(onRefresh).toHaveBeenCalled();
        },
        { timeout: 2000 },
      );
    });
  });

  describe("custom handlers", () => {
    // Assign Creative is hidden (unimplemented — no route/API wired).
    it("does not render assign creative (hidden, unimplemented)", async () => {
      renderDropdown({
        campaignData: { status: "planned" },
        handlers: { onAssignCreative: vi.fn() },
      });
      await openDropdown();
      expect(
        screen.queryByRole("menuitem", { name: /card\.assign_creative/i }),
      ).not.toBeInTheDocument();
    });

    it("calls onCampaignApproval when provided instead of opening drawer", async () => {
      const onCampaignApproval = vi.fn();
      const user = userEvent.setup();
      renderDropdown({ handlers: { onCampaignApproval } });
      await openDropdown();
      await user.click(
        screen.getByRole("menuitem", { name: /card\.campaign_approval/i }),
      );
      expect(onCampaignApproval).toHaveBeenCalledWith("camp-1");
    });

    it("calls onShare when share is clicked", async () => {
      const onShare = vi.fn();
      const user = userEvent.setup();
      renderDropdown({ handlers: { onShare } });
      await openDropdown();
      await user.click(screen.getByRole("menuitem", { name: /card\.share/i }));
      expect(onShare).toHaveBeenCalledWith("camp-1");
    });
  });

  describe("default props", () => {
    it("renders with empty campaignData", async () => {
      renderDropdown({ campaignId: "c1", campaignData: undefined });
      await openDropdown();
      expect(
        screen.getByRole("menuitem", { name: /card\.view_details/i }),
      ).toBeInTheDocument();
    });
  });

  // ─── Share Feature ──────────────────────────────────────────────────────────

  describe("share feature", () => {
    it("renders share item in the dropdown for non-draft campaigns", async () => {
      renderDropdown({ campaignData: { status: "planned" } });
      await openDropdown();
      expect(
        screen.getByRole("menuitem", { name: /card\.share/i }),
      ).toBeInTheDocument();
    });

    it("share item is disabled when status is draft", async () => {
      renderDropdown({ campaignData: { status: "draft" } });
      await openDropdown();
      expect(
        screen.getByRole("menuitem", { name: /card\.share/i }),
      ).toBeDisabled();
    });

    it("share item is enabled when status is planned", async () => {
      renderDropdown({ campaignData: { status: "planned" } });
      await openDropdown();
      expect(
        screen.getByRole("menuitem", { name: /card\.share/i }),
      ).not.toBeDisabled();
    });

    it("share item is hidden when hideNavigation includes 'share'", async () => {
      renderDropdown({ hideNavigation: ["share"] });
      await openDropdown();
      expect(
        screen.queryByRole("menuitem", { name: /card\.share/i }),
      ).not.toBeInTheDocument();
    });

    it("opens ShareModalDrawer when share is clicked", async () => {
      const user = userEvent.setup();
      renderDropdown({ campaignData: { status: "planned" } });
      await openDropdown();
      await user.click(screen.getByRole("menuitem", { name: /card\.share/i }));
      await waitFor(() =>
        expect(screen.getByTestId("share-modal-drawer")).toBeInTheDocument(),
      );
    });

    it("passes a public media plan URL with the minted token as shareUrl to ShareModalDrawer", async () => {
      const user = userEvent.setup();
      renderDropdown({ campaignData: { status: "planned" } });
      await openDropdown();
      await user.click(screen.getByRole("menuitem", { name: /card\.share/i }));
      await waitFor(() =>
        expect(screen.getByTestId("share-modal-drawer")).toBeInTheDocument(),
      );
      expect(capturedShareUrl).toContain("/public/media-plan/view/camp-1");
      expect(capturedShareUrl).toContain("token=mock-token");
    });

    it("falls back to the internal media plan URL when token generation fails", async () => {
      mockGeneratePublicToken.mockReturnValueOnce({
        unwrap: () => Promise.reject(new Error("Network error")),
      });
      const user = userEvent.setup();
      renderDropdown({ campaignData: { status: "planned" } });
      await openDropdown();
      await user.click(screen.getByRole("menuitem", { name: /card\.share/i }));
      await waitFor(() =>
        expect(screen.getByTestId("share-modal-drawer")).toBeInTheDocument(),
      );
      expect(capturedShareUrl).toContain("/campaigns/media-plan/camp-1");
    });

    it("closes ShareModalDrawer when its onClose is invoked", async () => {
      const user = userEvent.setup();
      renderDropdown({ campaignData: { status: "planned" } });
      await openDropdown();
      await user.click(screen.getByRole("menuitem", { name: /card\.share/i }));
      await waitFor(() =>
        expect(screen.getByTestId("share-modal-drawer")).toBeInTheDocument(),
      );
      await user.click(screen.getByTestId("share-modal-close"));
      await waitFor(() =>
        expect(
          screen.queryByTestId("share-modal-drawer"),
        ).not.toBeInTheDocument(),
      );
    });

    it("still calls handlers.onShare for backward compatibility", async () => {
      const onShare = vi.fn();
      const user = userEvent.setup();
      renderDropdown({
        campaignData: { status: "planned" },
        handlers: { onShare },
      });
      await openDropdown();
      await user.click(screen.getByRole("menuitem", { name: /card\.share/i }));
      expect(onShare).toHaveBeenCalledWith("camp-1");
    });
  });

  // ─── Download Feature (removed — item commented out) ─────────────────────────

  describe("download feature", () => {
    it("does not render the download item (feature disabled)", async () => {
      renderDropdown({ campaignData: { status: "planned" } });
      await openDropdown();
      expect(
        screen.queryByRole("menuitem", { name: /card\.download/i }),
      ).not.toBeInTheDocument();
    });
  });
});
