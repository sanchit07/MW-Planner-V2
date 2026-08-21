import { describe, it, expect } from "vitest";

import campaignSchema from "../campaigns/campaign.schema";

const defaultCampaignDates = {
  from: new Date("2024-01-01"),
  to: new Date("2024-01-31"),
};

function createCampaignData(
  overrides: Partial<{
    campaignName: string;
    campaignDates: { from: Date | null; to: Date | null };
    clientType: string;
  }> = {},
) {
  return {
    campaignName: "Test Campaign",
    campaignDates: defaultCampaignDates,
    clientType: "Direct",
    ...overrides,
  };
}

function parseCampaign(
  data: ReturnType<typeof createCampaignData>,
): ReturnType<typeof campaignSchema.safeParse> {
  return campaignSchema.safeParse(data);
}

describe("campaign.schema", () => {
  describe("campaignSchema", () => {
    it("should validate correct campaign data", () => {
      const result = parseCampaign(createCampaignData());
      expect(result.success).toBe(true);
    });

    it("should reject empty campaign name", () => {
      const result = parseCampaign(createCampaignData({ campaignName: "" }));
      expect(result.success).toBe(false);
    });

    it("should reject campaign name shorter than 3 characters", () => {
      const result = parseCampaign(createCampaignData({ campaignName: "AB" }));
      expect(result.success).toBe(false);
    });

    it("should reject campaign name longer than 100 characters", () => {
      const result = parseCampaign(
        createCampaignData({ campaignName: "A".repeat(101) }),
      );
      expect(result.success).toBe(false);
    });

    it("should reject when end date is before start date", () => {
      const result = parseCampaign(
        createCampaignData({
          campaignDates: {
            from: new Date("2024-01-31"),
            to: new Date("2024-01-01"),
          },
        }),
      );
      expect(result.success).toBe(false);
      if (!result.success) {
        const errorMessages = result.error.issues.map((e) => e.message);
        expect(
          errorMessages.some((msg: string) =>
            msg.includes("End date must be after start date"),
          ),
        ).toBe(true);
      }
    });

    it("should reject when end date is missing", () => {
      const result = parseCampaign(
        createCampaignData({
          campaignDates: { from: defaultCampaignDates.from, to: null },
        }),
      );
      expect(result.success).toBe(false);
    });

    it("should reject when start date is missing", () => {
      const result = parseCampaign(
        createCampaignData({
          campaignDates: { from: null, to: defaultCampaignDates.to },
        }),
      );
      expect(result.success).toBe(false);
    });
  });

  describe("mediaChannels", () => {
    it("should accept valid mediaChannels array", () => {
      const result = campaignSchema.safeParse({
        campaignName: "Test",
        campaignDates: defaultCampaignDates,
        clientType: "Direct",
        mediaChannels: ["DIGITAL_OOH"],
      });
      expect(result.success).toBe(true);
    });

    it("should reject empty mediaChannels array", () => {
      const result = campaignSchema.safeParse({
        campaignName: "Test",
        campaignDates: defaultCampaignDates,
        clientType: "Direct",
        mediaChannels: [],
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        const messages = result.error.issues.map((e) => e.message);
        expect(
          messages.some((m) => m.includes("At least one media channel")),
        ).toBe(true);
      }
    });

    it("should default mediaChannels to DIGITAL_OOH when omitted", () => {
      const result = campaignSchema.safeParse({
        campaignName: "Test",
        campaignDates: defaultCampaignDates,
        clientType: "Direct",
      });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.mediaChannels).toEqual(["DIGITAL_OOH"]);
      }
    });

    it("should accept multiple media channels", () => {
      const result = campaignSchema.safeParse({
        campaignName: "Test",
        campaignDates: defaultCampaignDates,
        clientType: "Direct",
        mediaChannels: ["DIGITAL_OOH", "CLASSIC_OOH", "TRANSIT"],
      });
      expect(result.success).toBe(true);
    });
  });
});
