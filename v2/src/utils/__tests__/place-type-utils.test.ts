import { describe, it, expect } from "vitest";

import {
  extractLocalNameWithType,
  getLocalNameTypeLabel,
  getPlaceTypeInfo,
} from "../place-type-utils";

describe("place-type-utils", () => {
  describe("getLocalNameTypeLabel", () => {
    it('returns "City" for locality', () => {
      expect(getLocalNameTypeLabel("locality")).toBe("City");
    });

    it('returns "Sub-locality" for sublocality', () => {
      expect(getLocalNameTypeLabel("sublocality")).toBe("Sub-locality");
    });

    it('returns "Sub-locality" for sublocality_level_1', () => {
      expect(getLocalNameTypeLabel("sublocality_level_1")).toBe("Sub-locality");
    });

    it('returns "Sub-locality" for sublocality_level_2', () => {
      expect(getLocalNameTypeLabel("sublocality_level_2")).toBe("Sub-locality");
    });

    it('returns "Area" for neighborhood', () => {
      expect(getLocalNameTypeLabel("neighborhood")).toBe("Area");
    });

    it('returns "State" for administrative_area_level_1', () => {
      expect(getLocalNameTypeLabel("administrative_area_level_1")).toBe(
        "State",
      );
    });

    it('returns "District" for administrative_area_level_2', () => {
      expect(getLocalNameTypeLabel("administrative_area_level_2")).toBe(
        "District",
      );
    });

    it('returns "District" for administrative_area_level_3', () => {
      expect(getLocalNameTypeLabel("administrative_area_level_3")).toBe(
        "District",
      );
    });

    it('returns "Country" for country', () => {
      expect(getLocalNameTypeLabel("country")).toBe("Country");
    });

    it("returns null for unknown type", () => {
      expect(getLocalNameTypeLabel("unknown_type")).toBeNull();
    });

    it("returns null for null input", () => {
      expect(getLocalNameTypeLabel(null)).toBeNull();
    });

    it("returns null for undefined input", () => {
      expect(getLocalNameTypeLabel(undefined)).toBeNull();
    });
  });

  describe("getPlaceTypeInfo", () => {
    it('labels locality as "City"', () => {
      expect(getPlaceTypeInfo("locality").label).toBe("City");
    });

    it('labels sublocality as "Sub-locality"', () => {
      expect(getPlaceTypeInfo("sublocality").label).toBe("Sub-locality");
    });

    it('labels sublocality_level_1 as "Sub-locality"', () => {
      expect(getPlaceTypeInfo("sublocality_level_1").label).toBe(
        "Sub-locality",
      );
    });

    it('labels administrative_area_level_1 as "State / Province"', () => {
      expect(getPlaceTypeInfo("administrative_area_level_1").label).toBe(
        "State / Province",
      );
    });

    it('labels administrative_area_level_2 as "District"', () => {
      expect(getPlaceTypeInfo("administrative_area_level_2").label).toBe(
        "District",
      );
    });

    it('labels country as "Country"', () => {
      expect(getPlaceTypeInfo("country").label).toBe("Country");
    });
  });

  describe("extractLocalNameWithType", () => {
    it("prefers locality over administrative levels", () => {
      const components = [
        { types: ["locality"], longText: "Kuala Lumpur" },
        { types: ["administrative_area_level_1"], longText: "Selangor" },
      ];
      expect(extractLocalNameWithType(components)).toEqual({
        name: "Kuala Lumpur",
        type: "locality",
      });
    });

    it("falls back to administrative_area_level_2 when no locality", () => {
      const components = [
        { types: ["administrative_area_level_2"], longText: "Petaling" },
        { types: ["administrative_area_level_1"], longText: "Selangor" },
      ];
      expect(extractLocalNameWithType(components)).toEqual({
        name: "Petaling",
        type: "administrative_area_level_2",
      });
    });

    it("falls back to administrative_area_level_1 as last resort", () => {
      const components = [
        { types: ["country"], longText: "Malaysia" },
        { types: ["administrative_area_level_1"], longText: "Selangor" },
      ];
      expect(extractLocalNameWithType(components)).toEqual({
        name: "Selangor",
        type: "administrative_area_level_1",
      });
    });

    it("returns null for empty array", () => {
      expect(extractLocalNameWithType([])).toBeNull();
    });

    it("returns null for null input", () => {
      expect(extractLocalNameWithType(null as unknown as [])).toBeNull();
    });

    it("supports legacy API format (long_name)", () => {
      const components = [{ types: ["locality"], long_name: "Kuala Lumpur" }];
      expect(extractLocalNameWithType(components)).toEqual({
        name: "Kuala Lumpur",
        type: "locality",
      });
    });

    it("prefers longText over long_name when both present", () => {
      const components = [
        {
          types: ["locality"],
          longText: "Kuala Lumpur (New)",
          long_name: "Kuala Lumpur (Old)",
        },
      ];
      expect(extractLocalNameWithType(components)).toEqual({
        name: "Kuala Lumpur (New)",
        type: "locality",
      });
    });
  });

  describe("getLocalNameTypeLabel with countryCode", () => {
    it('returns "State" for admin_level_1 in India', () => {
      expect(getLocalNameTypeLabel("administrative_area_level_1", "IN")).toBe(
        "State",
      );
    });

    it('returns "District" for admin_level_2 in India', () => {
      expect(getLocalNameTypeLabel("administrative_area_level_2", "IN")).toBe(
        "District",
      );
    });

    it('returns "Prefecture" for admin_level_1 in Japan', () => {
      expect(getLocalNameTypeLabel("administrative_area_level_1", "JP")).toBe(
        "Prefecture",
      );
    });

    it('returns "County" for admin_level_2 in USA', () => {
      expect(getLocalNameTypeLabel("administrative_area_level_2", "US")).toBe(
        "County",
      );
    });

    it('returns "Nation" for admin_level_1 in UK', () => {
      expect(getLocalNameTypeLabel("administrative_area_level_1", "GB")).toBe(
        "Nation",
      );
    });

    it('returns "Commune" for locality in France', () => {
      expect(getLocalNameTypeLabel("locality", "FR")).toBe("Commune");
    });

    it('returns "Federal State" for admin_level_1 in Germany', () => {
      expect(getLocalNameTypeLabel("administrative_area_level_1", "DE")).toBe(
        "Federal State",
      );
    });

    it('returns "Emirate" for admin_level_1 in UAE', () => {
      expect(getLocalNameTypeLabel("administrative_area_level_1", "AE")).toBe(
        "Emirate",
      );
    });

    it("falls back to generic label for unmapped country", () => {
      expect(getLocalNameTypeLabel("administrative_area_level_1", "XX")).toBe(
        "State",
      );
    });

    it("falls back to generic label when countryCode is null", () => {
      expect(getLocalNameTypeLabel("administrative_area_level_1", null)).toBe(
        "State",
      );
    });

    it("falls back to generic label when countryCode is undefined", () => {
      expect(
        getLocalNameTypeLabel("administrative_area_level_1", undefined),
      ).toBe("State");
    });

    it('returns "City" for locality without country-specific override', () => {
      expect(getLocalNameTypeLabel("locality", "US")).toBe("City");
    });

    it("returns null for unknown type even with valid country", () => {
      expect(getLocalNameTypeLabel("unknown_type", "US")).toBeNull();
    });

    it("returns null for null type even with valid country", () => {
      expect(getLocalNameTypeLabel(null, "US")).toBeNull();
    });
  });

  describe("getPlaceTypeInfo with countryCode", () => {
    it('labels admin_level_1 as "Prefecture" for Japan', () => {
      expect(getPlaceTypeInfo("administrative_area_level_1", "JP").label).toBe(
        "Prefecture",
      );
    });

    it('labels admin_level_1 as "State / Province" without country code', () => {
      expect(getPlaceTypeInfo("administrative_area_level_1").label).toBe(
        "State / Province",
      );
    });

    it("preserves Icon and styling when overriding label", () => {
      const withCountry = getPlaceTypeInfo("administrative_area_level_1", "JP");
      const without = getPlaceTypeInfo("administrative_area_level_1");
      expect(withCountry.Icon).toBe(without.Icon);
      expect(withCountry.bgClass).toBe(without.bgClass);
      expect(withCountry.colorClass).toBe(without.colorClass);
      expect(withCountry.label).toBe("Prefecture");
    });

    it("does not affect non-admin types", () => {
      expect(getPlaceTypeInfo("restaurant", "JP").label).toBe("Restaurant");
    });

    it('labels admin_level_2 as "County" for USA', () => {
      expect(getPlaceTypeInfo("administrative_area_level_2", "US").label).toBe(
        "County",
      );
    });

    it("returns default info for null type with country code", () => {
      const info = getPlaceTypeInfo(null, "US");
      expect(info.label).toBe("Place");
    });
  });
});
