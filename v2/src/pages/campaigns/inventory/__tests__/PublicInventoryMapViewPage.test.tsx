import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { MemoryRouter, useSearchParams } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { PublicInventoryMapViewPage } from "../PublicInventoryMapViewPage";

const mockGetPublicInventories = vi.fn();

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useSearchParams: vi.fn(),
  };
});

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("@services/public-access/publicAccessSlice", () => ({
  useLazyGetPublicInventoriesQuery: () => [
    (arg: unknown) => ({
      unwrap: () => Promise.resolve(mockGetPublicInventories(arg)),
    }),
    {},
  ],
  useGetPublicCampaignQuery: () => ({ data: undefined }),
}));

vi.mock("@components/ui/Mapbox", () => ({
  default: function MockMapBox() {
    return React.createElement("div", {
      "data-testid": "mapbox-wrapper",
      children: "Map",
    });
  },
}));

describe("PublicInventoryMapViewPage", () => {
  const mockUseSearchParams = vi.mocked(useSearchParams);

  const wrapper = ({ children }: { children: React.ReactNode }) =>
    React.createElement(MemoryRouter, { initialEntries: ["/"] }, children);

  beforeEach(() => {
    vi.clearAllMocks();
    mockGetPublicInventories.mockReturnValue({
      success: true,
      data: {
        content: [],
        totalElements: 0,
        last: true,
      },
    });
    mockUseSearchParams.mockReturnValue([
      new URLSearchParams("token=public-token-123"),
      vi.fn(),
    ]);
  });

  describe("rendering", () => {
    it("renders page with Selected Inventories header", async () => {
      render(<PublicInventoryMapViewPage />, { wrapper });
      await waitFor(() => {
        expect(
          screen.getByText("publicInventoryMap.selectedInventories"),
        ).toBeInTheDocument();
      });
    });

    it("renders search input", async () => {
      render(<PublicInventoryMapViewPage />, { wrapper });
      await waitFor(() => {
        expect(
          screen.getByPlaceholderText("inventories.search_placeholder"),
        ).toBeInTheDocument();
      });
    });

    it("shows loading state initially when token is present", async () => {
      mockGetPublicInventories.mockReturnValue(
        new Promise(() => {
          /* never resolve */
        }),
      );
      render(<PublicInventoryMapViewPage />, { wrapper });
      await waitFor(() => {
        expect(
          screen.getByText("inventoryPageForm.loadingInventories"),
        ).toBeInTheDocument();
      });
    });

    it("shows no inventories message when API returns empty list", async () => {
      render(<PublicInventoryMapViewPage />, { wrapper });
      await waitFor(() => {
        expect(
          screen.getByText("inventoryPageForm.noInventoriesFound"),
        ).toBeInTheDocument();
      });
    });

    it("calls getPublicInventories with token when token is in URL", async () => {
      render(<PublicInventoryMapViewPage />, { wrapper });
      await waitFor(() => {
        expect(mockGetPublicInventories).toHaveBeenCalledWith(
          expect.objectContaining({
            publicToken: "public-token-123",
            page: 0,
            size: 10,
          }),
        );
      });
    });
  });

  describe("search", () => {
    it("updates search input value when user types", async () => {
      const user = userEvent.setup();
      mockGetPublicInventories.mockReturnValue({
        success: true,
        data: {
          content: [
            {
              detail: {
                id: "1",
                name: "Board One",
                externalId: "e1",
                referenceId: "r1",
                mediaOwnerId: "m1",
                mediaOwnerName: "Owner",
                inventoryType: "Digital",
                format: "Billboard",
                environment: "Outdoor",
                thumbnail: "",
                images: [],
                panels: [],
                execution: "",
                screens: 0,
                sov: 0,
                isSelected: false,
                isCompliant: true,
                bookingMode: "",
              },
              location: {
                location: {
                  address: "123 Main St",
                  country: "US",
                  state: "NY",
                },
              },
              performance: {
                estimatedCost: 100,
                cpmRate: 10,
                perDayCost: 10,
                perDayAdPlays: 1,
                totalAdPlays: 10,
                totalSot: 1,
              },
              operations: {
                operatingTimes: {},
                maintenanceWindow: "",
                loopSize: 0,
                slotDuration: 0,
                clientPerLoop: 0,
                cycleTime: 0,
              },
              schedules: [],
            },
          ],
          totalElements: 1,
          last: true,
        },
      });
      render(<PublicInventoryMapViewPage />, { wrapper });
      await waitFor(() => {
        expect(screen.getByText("Board One")).toBeInTheDocument();
      });
      const searchInput = screen.getByPlaceholderText(
        "inventories.search_placeholder",
      );
      await user.type(searchInput, "test");
      expect(searchInput).toHaveValue("test");
    });
  });

  describe("no token", () => {
    it("does not call getPublicInventories when token is missing", async () => {
      mockUseSearchParams.mockReturnValue([new URLSearchParams(""), vi.fn()]);
      render(<PublicInventoryMapViewPage />, { wrapper });
      await waitFor(() => {
        expect(
          screen.getByText("publicInventoryMap.selectedInventories"),
        ).toBeInTheDocument();
      });
      expect(mockGetPublicInventories).not.toHaveBeenCalled();
    });
  });
});
