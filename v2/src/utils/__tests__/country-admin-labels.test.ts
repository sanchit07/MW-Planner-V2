import { describe, expect, it } from "vitest";

import {
  COUNTRY_ADMIN_LABELS,
  getCountryAdminLabel,
} from "../country-admin-labels";

describe("country-admin-labels", () => {
  describe("getCountryAdminLabel", () => {
    it('returns "State" for India admin_level_1', () => {
      expect(getCountryAdminLabel("IN", "administrative_area_level_1")).toBe(
        "State",
      );
    });

    it('returns "District" for India admin_level_2', () => {
      expect(getCountryAdminLabel("IN", "administrative_area_level_2")).toBe(
        "District",
      );
    });

    it('returns "Prefecture" for Japan admin_level_1', () => {
      expect(getCountryAdminLabel("JP", "administrative_area_level_1")).toBe(
        "Prefecture",
      );
    });

    it('returns "County" for USA admin_level_2', () => {
      expect(getCountryAdminLabel("US", "administrative_area_level_2")).toBe(
        "County",
      );
    });

    it('returns "Emirate" for UAE admin_level_1', () => {
      expect(getCountryAdminLabel("AE", "administrative_area_level_1")).toBe(
        "Emirate",
      );
    });

    it('returns "Nation" for UK admin_level_1', () => {
      expect(getCountryAdminLabel("GB", "administrative_area_level_1")).toBe(
        "Nation",
      );
    });

    it('returns "Commune" for France locality', () => {
      expect(getCountryAdminLabel("FR", "locality")).toBe("Commune");
    });

    it('returns "Province" for Canada admin_level_1', () => {
      expect(getCountryAdminLabel("CA", "administrative_area_level_1")).toBe(
        "Province",
      );
    });

    it("returns undefined for unmapped country", () => {
      expect(
        getCountryAdminLabel("XX", "administrative_area_level_1"),
      ).toBeUndefined();
    });

    it("returns undefined for unmapped type in a mapped country", () => {
      expect(getCountryAdminLabel("US", "neighborhood")).toBeUndefined();
    });

    it("handles lowercase country codes", () => {
      expect(getCountryAdminLabel("jp", "administrative_area_level_1")).toBe(
        "Prefecture",
      );
    });

    it("handles mixed-case country codes", () => {
      expect(getCountryAdminLabel("In", "administrative_area_level_1")).toBe(
        "State",
      );
    });
  });

  describe("COUNTRY_ADMIN_LABELS", () => {
    it("contains at least 40 countries", () => {
      expect(Object.keys(COUNTRY_ADMIN_LABELS).length).toBeGreaterThanOrEqual(
        40,
      );
    });

    it("all country codes are uppercase 2-letter strings", () => {
      for (const code of Object.keys(COUNTRY_ADMIN_LABELS)) {
        expect(code).toMatch(/^[A-Z]{2}$/);
      }
    });

    it("all label values are non-empty strings", () => {
      for (const [, labels] of Object.entries(COUNTRY_ADMIN_LABELS)) {
        for (const [, value] of Object.entries(labels)) {
          expect(typeof value).toBe("string");
          expect(value.length).toBeGreaterThan(0);
        }
      }
    });
  });
});
