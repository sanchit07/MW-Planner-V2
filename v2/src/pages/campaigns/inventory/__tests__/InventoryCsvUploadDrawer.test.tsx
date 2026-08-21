import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import type { CampaignCreateResponse } from "src/types/campaign.types";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { InventoryCsvUploadDrawer } from "../InventoryCsvUploadDrawer";

const {
  mockVerifyCsv,
  mockUploadCsv,
  mockDeleteCsvFile,
  mockDownloadCsvFile,
  mockInventoryCsvFile,
  mockUseGetInventoryCsvFilesQuery,
} = vi.hoisted(() => ({
  mockVerifyCsv: vi.fn(),
  mockUploadCsv: vi.fn(),
  mockDeleteCsvFile: vi.fn(),
  mockDownloadCsvFile: vi.fn(),
  mockInventoryCsvFile: vi.fn(),
  mockUseGetInventoryCsvFilesQuery: vi.fn(),
}));

const mockShowError = vi.fn();
const mockShowSuccess = vi.fn();

vi.mock("@tolgee/react", () => ({
  useTranslate: (ns: string[]) => {
    const key = (k: string) => (ns[0] === "common" ? k : k);
    return { t: key };
  },
}));

vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({
    showError: mockShowError,
    showSuccess: mockShowSuccess,
  }),
}));

vi.mock("../ViewFileInventoryDrawer", () => ({
  ViewFileInventoryDrawer: () =>
    React.createElement("div", { "data-testid": "view-file-drawer" }),
}));

vi.mock("../ViewLogDrawer", () => ({
  ViewLogDrawer: () =>
    React.createElement("div", { "data-testid": "view-log-drawer" }),
}));

vi.mock("@services/inventory/inventorySlice", () => ({
  useVerifyInventoryCsvMutation: () => [mockVerifyCsv, {}],
  useUploadInventoryCsvMutation: () => [mockUploadCsv, {}],
  useGetInventoryCsvFilesQuery: (...args: unknown[]) =>
    mockUseGetInventoryCsvFilesQuery(...args),
  useDeleteInventoryCsvFileMutation: () => [mockDeleteCsvFile, {}],
  useDownloadInventoryCsvFileMutation: () => [mockDownloadCsvFile, {}],
  useUseInventoryCsvFileMutation: () => [mockInventoryCsvFile, {}],
}));

const minimalCampaignData: CampaignCreateResponse = {
  id: "campaign-1",
  name: "",
  status: "",
  countryId: "US",
  startDate: "",
  endDate: "",
  clientType: "",
  createdAt: "",
  updatedAt: "",
};

describe("InventoryCsvUploadDrawer", () => {
  const defaultProps: React.ComponentProps<typeof InventoryCsvUploadDrawer> = {
    isOpen: true,
    onClose: vi.fn(),
    campaignId: "campaign-1",
    campaignData: minimalCampaignData,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockVerifyCsv.mockReturnValue({
      unwrap: () => Promise.resolve({ data: null }),
    });
    mockUseGetInventoryCsvFilesQuery.mockReturnValue({
      data: { data: { content: [], totalPages: 0, totalElements: 0 } },
      isLoading: false,
      refetch: vi.fn(),
    });
  });

  describe("rendering", () => {
    it("renders nothing when isOpen is false", () => {
      render(<InventoryCsvUploadDrawer {...defaultProps} isOpen={false} />);
      expect(
        screen.queryByText("inventoryCsvUpload.title"),
      ).not.toBeInTheDocument();
    });

    it("renders drawer with title Import Inventories when open", () => {
      render(<InventoryCsvUploadDrawer {...defaultProps} />);
      expect(screen.getByText("inventoryCsvUpload.title")).toBeInTheDocument();
    });

    it("renders Need a template section and Download Template button", () => {
      render(<InventoryCsvUploadDrawer {...defaultProps} />);
      expect(screen.getByText("Need a template?")).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /Download Template/i }),
      ).toBeInTheDocument();
    });

    it("renders tabs Existing Files and Upload New", () => {
      render(<InventoryCsvUploadDrawer {...defaultProps} />);
      expect(
        screen.getByRole("button", {
          name: /inventoryCsvUpload\.tabs\.existingFiles/i,
        }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", {
          name: /inventoryCsvUpload\.tabs\.uploadNew/i,
        }),
      ).toBeInTheDocument();
    });

    it("renders cancel button in footer", () => {
      render(<InventoryCsvUploadDrawer {...defaultProps} />);
      expect(
        screen.getByRole("button", { name: /buttons\.cancel/i }),
      ).toBeInTheDocument();
    });

    it("shows Import button in footer on Upload New tab by default", () => {
      render(<InventoryCsvUploadDrawer {...defaultProps} />);
      expect(
        screen.getByRole("button", { name: /Import/i }),
      ).toBeInTheDocument();
    });

    it("shows Existing Files empty state when switching to Existing Files tab", async () => {
      const user = userEvent.setup();
      render(<InventoryCsvUploadDrawer {...defaultProps} />);
      await user.click(
        screen.getByRole("button", {
          name: /inventoryCsvUpload\.tabs\.existingFiles/i,
        }),
      );
      expect(screen.getByText("No files yet")).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /Upload CSV/i }),
      ).toBeInTheDocument();
    });
  });

  describe("Download Template", () => {
    it("has Download Template button that triggers template download", async () => {
      const user = userEvent.setup();
      const createObjectURL = vi.fn(() => "blob:mock");
      const revokeObjectURL = vi.fn();
      vi.stubGlobal("URL", { createObjectURL, revokeObjectURL });

      render(<InventoryCsvUploadDrawer {...defaultProps} />);
      const downloadBtn = screen.getByRole("button", {
        name: /Download Template/i,
      });
      expect(downloadBtn).toBeInTheDocument();
      await user.click(downloadBtn);

      expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob));
      vi.unstubAllGlobals();
    });
  });

  describe("close", () => {
    it("calls onClose when cancel button is clicked", async () => {
      const user = userEvent.setup();
      render(<InventoryCsvUploadDrawer {...defaultProps} />);
      await user.click(
        screen.getByRole("button", { name: /buttons\.cancel/i }),
      );
      expect(defaultProps.onClose).toHaveBeenCalledWith(true);
    });
  });

  describe("Existing Files tab", () => {
    it("shows Use File button disabled when no file is selected", async () => {
      const user = userEvent.setup();
      render(<InventoryCsvUploadDrawer {...defaultProps} />);
      await user.click(
        screen.getByRole("button", {
          name: /inventoryCsvUpload\.tabs\.existingFiles/i,
        }),
      );
      const useFileBtn = screen.getByRole("button", { name: /Use File/i });
      expect(useFileBtn).toBeDisabled();
    });

    it("shows Use File button on Existing Files tab", async () => {
      const user = userEvent.setup();
      render(<InventoryCsvUploadDrawer {...defaultProps} />);
      await user.click(
        screen.getByRole("button", {
          name: /inventoryCsvUpload\.tabs\.existingFiles/i,
        }),
      );
      expect(
        screen.getByRole("button", { name: /Use File/i }),
      ).toBeInTheDocument();
    });
  });
});
