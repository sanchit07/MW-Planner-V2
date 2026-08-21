import { render, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import {
  useMediaPlanData,
  UnifiedMediaPlanData,
} from "../../media-plan/useMediaPlanData";
import { CampaignPPTDownloader } from "../CampaignPPTDownloader";

// ─── Mocks ────────────────────────────────────────────────────────────────────

vi.mock("../../media-plan/useMediaPlanData", () => ({
  useMediaPlanData: vi.fn(),
}));

const mockGeneratePublicToken = vi.fn().mockReturnValue({
  unwrap: vi.fn().mockResolvedValue({
    success: true,
    data: { publicToken: "test-token-uuid" },
  }),
});

vi.mock("@services/public-access/publicAccessSlice", () => ({
  useGeneratePublicTokenMutation: () => [mockGeneratePublicToken],
}));

// vi.hoisted ensures this is available when the vi.mock factory runs
const mockGenerateMediaPlanPPT = vi.hoisted(() =>
  vi.fn().mockResolvedValue(undefined),
);

vi.mock("@utils/mediaPlanPPTGenerator", () => ({
  generateMediaPlanPPT: mockGenerateMediaPlanPPT,
}));

vi.mock("../../media-plan/constants", () => ({
  THEMES_COLORS: [
    {
      id: "primary",
      name: "Blue Ocean",
      colors: {
        primary: "#003DA6",
        secondary: "#0063D3",
        accent: "#00A3FF",
        text: "#FFFFFF",
        background: "#FFFFFF",
      },
    },
  ],
}));

// ─── Test Data Factory ────────────────────────────────────────────────────────

const createMockMediaPlanData = (
  overrides: Partial<UnifiedMediaPlanData> = {},
): UnifiedMediaPlanData =>
  ({
    isLoading: false,
    isError: false,
    mediaPlan: {
      headerInfo: { name: "Test Campaign", clientName: "Acme Corp" },
      performanceMetrics: { totalCost: 10000, estimatedImpression: 50000 },
      geographicTargeting: null,
    },
    headerInfo: { name: "Test Campaign", totalCost: 10000, impressions: 50000 },
    performanceMetrics: { totalCost: 10000, estimatedImpression: 50000 },
    geographicTargeting: null,
    costSplitByInventoryType: [],
    costSplitByState: [],
    costSplitByCity: [],
    costSplitByVenueType: [],
    forecastData: null,
    priceSummary: null,
    selectedInventory: {
      summaryStatistics: {
        totalAssets: 2,
        totalFormatTypes: 1,
        totalCities: 1,
      },
      locations: [
        {
          detail: {
            name: "Billboard Alpha",
            format: "DIGITAL",
            inventoryType: "DIGITAL",
            mediaOwnerName: "Owner Co",
          },
          location: {
            location: {
              city: "Tokyo",
              state: "Tokyo",
              country: "JP",
              locationCoordinates: {
                coordinates: [{ latitude: 35.68, longitude: 139.69 }],
              },
            },
          },
          performance: {
            estimatedCost: 5000,
            totalAdPlays: 1000,
            perDayAdPlays: 33,
          },
          scheduleDates: ["2026-01-01", "2026-01-15"],
          scheduleHours: ["08:00 - 22:00"],
        },
      ],
    },
    ...overrides,
  }) as unknown as UnifiedMediaPlanData;

// ─── Props Helper ─────────────────────────────────────────────────────────────

const defaultProps = {
  campaignId: "camp-1",
  onComplete: vi.fn(),
  onError: vi.fn(),
};

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("CampaignPPTDownloader", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useMediaPlanData).mockReturnValue(null);
    mockGeneratePublicToken.mockReturnValue({
      unwrap: vi.fn().mockResolvedValue({
        success: true,
        data: { publicToken: "test-token-uuid" },
      }),
    });
  });

  // ─── Render ──────────────────────────────────────────────────────────────

  describe("render", () => {
    it("renders null — produces no visible DOM output", () => {
      vi.mocked(useMediaPlanData).mockReturnValue(null);
      const { container } = render(<CampaignPPTDownloader {...defaultProps} />);
      expect(container).toBeEmptyDOMElement();
    });

    it("does not trigger PPT generation while mediaPlanData is null", async () => {
      vi.mocked(useMediaPlanData).mockReturnValue(null);
      render(<CampaignPPTDownloader {...defaultProps} />);
      await new Promise((r) => setTimeout(r, 50));
      expect(mockGeneratePublicToken).not.toHaveBeenCalled();
      expect(mockGenerateMediaPlanPPT).not.toHaveBeenCalled();
    });

    it("does not trigger PPT generation while isLoading is true", async () => {
      vi.mocked(useMediaPlanData).mockReturnValue(
        createMockMediaPlanData({ isLoading: true }),
      );
      render(<CampaignPPTDownloader {...defaultProps} />);
      await new Promise((r) => setTimeout(r, 50));
      expect(mockGenerateMediaPlanPPT).not.toHaveBeenCalled();
    });

    it("does not trigger PPT generation while isError is true", async () => {
      vi.mocked(useMediaPlanData).mockReturnValue(
        createMockMediaPlanData({ isError: true }),
      );
      render(<CampaignPPTDownloader {...defaultProps} />);
      await new Promise((r) => setTimeout(r, 50));
      expect(mockGenerateMediaPlanPPT).not.toHaveBeenCalled();
    });
  });

  // ─── Happy Path ──────────────────────────────────────────────────────────

  describe("PPT generation — happy path", () => {
    it("calls generateMediaPlanPPT exactly once when valid data is available", async () => {
      vi.mocked(useMediaPlanData).mockReturnValue(createMockMediaPlanData());
      render(<CampaignPPTDownloader {...defaultProps} />);
      await waitFor(() =>
        expect(mockGenerateMediaPlanPPT).toHaveBeenCalledTimes(1),
      );
    });

    it("calls generatePublicToken with the correct campaignId", async () => {
      vi.mocked(useMediaPlanData).mockReturnValue(createMockMediaPlanData());
      render(
        <CampaignPPTDownloader
          campaignId="camp-42"
          onComplete={vi.fn()}
          onError={vi.fn()}
        />,
      );
      await waitFor(() =>
        expect(mockGeneratePublicToken).toHaveBeenCalledWith("camp-42"),
      );
    });

    it("embeds the publicToken in the mapImageLink passed to generateMediaPlanPPT", async () => {
      vi.mocked(useMediaPlanData).mockReturnValue(createMockMediaPlanData());
      render(<CampaignPPTDownloader {...defaultProps} />);
      await waitFor(() =>
        expect(mockGenerateMediaPlanPPT).toHaveBeenCalledWith(
          expect.objectContaining({
            mapImageLink: expect.stringContaining("test-token-uuid"),
          }),
        ),
      );
    });

    it("passes undefined for mapImage, scheduleChartImage, and selectedInventoryChartImage", async () => {
      vi.mocked(useMediaPlanData).mockReturnValue(createMockMediaPlanData());
      render(<CampaignPPTDownloader {...defaultProps} />);
      await waitFor(() =>
        expect(mockGenerateMediaPlanPPT).toHaveBeenCalledWith(
          expect.objectContaining({
            mapImage: undefined,
            scheduleChartImage: undefined,
            selectedInventoryChartImage: undefined,
          }),
        ),
      );
    });

    it("uses THEMES_COLORS[0] as the PPT theme", async () => {
      vi.mocked(useMediaPlanData).mockReturnValue(createMockMediaPlanData());
      render(<CampaignPPTDownloader {...defaultProps} />);
      await waitFor(() =>
        expect(mockGenerateMediaPlanPPT).toHaveBeenCalledWith(
          expect.objectContaining({
            theme: expect.objectContaining({
              id: "primary",
              name: "Blue Ocean",
            }),
          }),
        ),
      );
    });

    it("builds a fileName that includes the campaign name", async () => {
      vi.mocked(useMediaPlanData).mockReturnValue(createMockMediaPlanData());
      render(<CampaignPPTDownloader {...defaultProps} />);
      await waitFor(() =>
        expect(mockGenerateMediaPlanPPT).toHaveBeenCalledWith(
          expect.objectContaining({
            fileName: expect.stringContaining("Test Campaign"),
          }),
        ),
      );
    });

    it("calls onComplete after successful PPT generation", async () => {
      const onComplete = vi.fn();
      vi.mocked(useMediaPlanData).mockReturnValue(createMockMediaPlanData());
      render(
        <CampaignPPTDownloader {...defaultProps} onComplete={onComplete} />,
      );
      await waitFor(() => expect(onComplete).toHaveBeenCalledTimes(1));
    });
  });

  // ─── Error Handling ───────────────────────────────────────────────────────

  describe("PPT generation — error handling", () => {
    it("uses fallback map URL (without token) when token generation throws", async () => {
      mockGeneratePublicToken.mockReturnValue({
        unwrap: vi
          .fn()
          .mockRejectedValue(new Error("Token service unavailable")),
      });
      vi.mocked(useMediaPlanData).mockReturnValue(createMockMediaPlanData());
      render(<CampaignPPTDownloader {...defaultProps} />);
      await waitFor(() =>
        expect(mockGenerateMediaPlanPPT).toHaveBeenCalledWith(
          expect.objectContaining({
            mapImageLink: expect.stringContaining(
              "/public/inventory-map/view/camp-1",
            ),
          }),
        ),
      );
      // Token itself should not be in the fallback URL
      const callArgs = mockGenerateMediaPlanPPT.mock.calls[0][0];
      expect(callArgs.mapImageLink).not.toContain("test-token-uuid");
    });

    it("uses fallback map URL when token response has no publicToken", async () => {
      mockGeneratePublicToken.mockReturnValue({
        unwrap: vi.fn().mockResolvedValue({ success: false, data: {} }),
      });
      vi.mocked(useMediaPlanData).mockReturnValue(createMockMediaPlanData());
      render(<CampaignPPTDownloader {...defaultProps} />);
      await waitFor(() =>
        expect(mockGenerateMediaPlanPPT).toHaveBeenCalledWith(
          expect.objectContaining({
            mapImageLink: expect.stringContaining(
              "/public/inventory-map/view/camp-1",
            ),
          }),
        ),
      );
    });

    it("calls onError when generateMediaPlanPPT throws", async () => {
      const onError = vi.fn();
      const onComplete = vi.fn();
      mockGenerateMediaPlanPPT.mockRejectedValueOnce(
        new Error("PPT generation failed"),
      );
      vi.mocked(useMediaPlanData).mockReturnValue(createMockMediaPlanData());
      render(
        <CampaignPPTDownloader
          {...defaultProps}
          onComplete={onComplete}
          onError={onError}
        />,
      );
      await waitFor(() => expect(onError).toHaveBeenCalledTimes(1));
      expect(onComplete).not.toHaveBeenCalled();
    });

    it("does not call onComplete when PPT generation fails", async () => {
      const onComplete = vi.fn();
      mockGenerateMediaPlanPPT.mockRejectedValueOnce(new Error("fail"));
      vi.mocked(useMediaPlanData).mockReturnValue(createMockMediaPlanData());
      render(
        <CampaignPPTDownloader {...defaultProps} onComplete={onComplete} />,
      );
      await new Promise((r) => setTimeout(r, 100));
      expect(onComplete).not.toHaveBeenCalled();
    });
  });

  // ─── Data Transformation ─────────────────────────────────────────────────

  describe("data transformation", () => {
    it("transforms selectedInventory locations into MediaPlanResponse structure", async () => {
      vi.mocked(useMediaPlanData).mockReturnValue(createMockMediaPlanData());
      render(<CampaignPPTDownloader {...defaultProps} />);
      await waitFor(() =>
        expect(mockGenerateMediaPlanPPT).toHaveBeenCalledTimes(1),
      );
      const pptPayload = mockGenerateMediaPlanPPT.mock.calls[0][0];
      expect(pptPayload.mediaPlan.selectedInventory.locations[0]).toMatchObject(
        {
          name: "Billboard Alpha",
          city: "Tokyo",
          state: "Tokyo",
          country: "JP",
          cost: 5000,
          mediaOwnerName: "Owner Co",
        },
      );
    });

    it("extracts summaryStatistics correctly from selectedInventory", async () => {
      vi.mocked(useMediaPlanData).mockReturnValue(createMockMediaPlanData());
      render(<CampaignPPTDownloader {...defaultProps} />);
      await waitFor(() =>
        expect(mockGenerateMediaPlanPPT).toHaveBeenCalledTimes(1),
      );
      const pptPayload = mockGenerateMediaPlanPPT.mock.calls[0][0];
      expect(
        pptPayload.mediaPlan.selectedInventory.summaryStatistics,
      ).toMatchObject({
        totalAssets: 2,
        totalFormatTypes: 1,
        totalCities: 1,
      });
    });

    it("sets selectedInventory to undefined in the PPT payload when mediaPlanData has no selectedInventory", async () => {
      vi.mocked(useMediaPlanData).mockReturnValue(
        createMockMediaPlanData({ selectedInventory: null as never }),
      );
      render(<CampaignPPTDownloader {...defaultProps} />);
      await waitFor(() =>
        expect(mockGenerateMediaPlanPPT).toHaveBeenCalledTimes(1),
      );
      const pptPayload = mockGenerateMediaPlanPPT.mock.calls[0][0];
      expect(pptPayload.mediaPlan.selectedInventory).toBeUndefined();
    });

    it("merges headerInfo from mediaPlanData into the PPT payload", async () => {
      vi.mocked(useMediaPlanData).mockReturnValue(createMockMediaPlanData());
      render(<CampaignPPTDownloader {...defaultProps} />);
      await waitFor(() =>
        expect(mockGenerateMediaPlanPPT).toHaveBeenCalledTimes(1),
      );
      const pptPayload = mockGenerateMediaPlanPPT.mock.calls[0][0];
      expect(pptPayload.mediaPlan.headerInfo).toMatchObject({
        name: "Test Campaign",
      });
    });

    it("includes lat/lng coordinates from inventory location in the transformed locations", async () => {
      vi.mocked(useMediaPlanData).mockReturnValue(createMockMediaPlanData());
      render(<CampaignPPTDownloader {...defaultProps} />);
      await waitFor(() =>
        expect(mockGenerateMediaPlanPPT).toHaveBeenCalledTimes(1),
      );
      const pptPayload = mockGenerateMediaPlanPPT.mock.calls[0][0];
      expect(pptPayload.mediaPlan.selectedInventory.locations[0]).toMatchObject(
        { lat: 35.68, lng: 139.69 },
      );
    });
  });

  // ─── hasTriggeredRef Guard ────────────────────────────────────────────────

  describe("hasTriggeredRef guard", () => {
    it("does not generate PPT more than once when mediaPlanData reference changes after first trigger", async () => {
      const dataV1 = createMockMediaPlanData();
      // Different object reference, same content (simulates subsidiary queries resolving)
      const dataV2 = {
        ...createMockMediaPlanData(),
        costSplitByInventoryType: [{ id: "new" }],
      } as unknown as UnifiedMediaPlanData;

      vi.mocked(useMediaPlanData).mockReturnValue(dataV1);
      const { rerender } = render(<CampaignPPTDownloader {...defaultProps} />);
      await waitFor(() =>
        expect(mockGenerateMediaPlanPPT).toHaveBeenCalledTimes(1),
      );

      // Simulate hook returning a new object (subsidiary query resolved)
      vi.mocked(useMediaPlanData).mockReturnValue(dataV2);
      rerender(<CampaignPPTDownloader {...defaultProps} />);

      await new Promise((r) => setTimeout(r, 50));
      expect(mockGenerateMediaPlanPPT).toHaveBeenCalledTimes(1);
    });

    it("does not call onComplete more than once across multiple re-renders", async () => {
      const onComplete = vi.fn();
      const dataV1 = createMockMediaPlanData();
      const dataV2 = { ...createMockMediaPlanData() } as UnifiedMediaPlanData;

      vi.mocked(useMediaPlanData).mockReturnValue(dataV1);
      const { rerender } = render(
        <CampaignPPTDownloader {...defaultProps} onComplete={onComplete} />,
      );
      await waitFor(() => expect(onComplete).toHaveBeenCalledTimes(1));

      vi.mocked(useMediaPlanData).mockReturnValue(dataV2);
      rerender(
        <CampaignPPTDownloader {...defaultProps} onComplete={onComplete} />,
      );
      await new Promise((r) => setTimeout(r, 50));

      expect(onComplete).toHaveBeenCalledTimes(1);
    });
  });
});
