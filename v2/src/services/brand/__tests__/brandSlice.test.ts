import { describe, it, expect } from "vitest";

import { IamBrand } from "../../../types/brand.types";
import { transformBrandsResponse } from "../brandSlice";

const mockBrand: IamBrand = {
  id: "brand-1",
  name: "Nike",
  logo_url: "https://example.com/nike.svg",
};

const anotherBrand: IamBrand = {
  id: "brand-2",
  name: "Adidas",
};

describe("transformBrandsResponse", () => {
  describe("direct array", () => {
    it("returns a direct array unchanged", () => {
      expect(transformBrandsResponse([mockBrand])).toEqual([mockBrand]);
    });

    it("returns an empty direct array unchanged", () => {
      expect(transformBrandsResponse([])).toEqual([]);
    });

    it("returns multiple brands from a direct array", () => {
      expect(transformBrandsResponse([mockBrand, anotherBrand])).toEqual([
        mockBrand,
        anotherBrand,
      ]);
    });
  });

  describe("top-level envelope keys", () => {
    it("unwraps { brands: [...] }", () => {
      expect(transformBrandsResponse({ brands: [mockBrand] })).toEqual([
        mockBrand,
      ]);
    });

    it("unwraps { data: [...] } when data is an array", () => {
      expect(transformBrandsResponse({ data: [mockBrand] })).toEqual([
        mockBrand,
      ]);
    });

    it("unwraps { items: [...] }", () => {
      expect(transformBrandsResponse({ items: [mockBrand] })).toEqual([
        mockBrand,
      ]);
    });

    it("unwraps { content: [...] }", () => {
      expect(transformBrandsResponse({ content: [mockBrand] })).toEqual([
        mockBrand,
      ]);
    });

    it("unwraps { result: [...] }", () => {
      expect(transformBrandsResponse({ result: [mockBrand] })).toEqual([
        mockBrand,
      ]);
    });
  });

  describe("nested IAM envelope { data: { brands: [...] } }", () => {
    it("unwraps the IAM company brands envelope shape", () => {
      const response = {
        success: true,
        data: { brands: [mockBrand], total: 1 },
      };
      expect(transformBrandsResponse(response)).toEqual([mockBrand]);
    });

    it("unwraps { data: { items: [...] } }", () => {
      expect(transformBrandsResponse({ data: { items: [mockBrand] } })).toEqual(
        [mockBrand],
      );
    });

    it("unwraps { data: { content: [...] } }", () => {
      expect(
        transformBrandsResponse({ data: { content: [mockBrand] } }),
      ).toEqual([mockBrand]);
    });

    it("unwraps { data: { result: [...] } }", () => {
      expect(
        transformBrandsResponse({ data: { result: [mockBrand] } }),
      ).toEqual([mockBrand]);
    });
  });

  describe("fallback to empty array", () => {
    it("returns [] for null", () => {
      expect(transformBrandsResponse(null)).toEqual([]);
    });

    it("returns [] for undefined", () => {
      expect(transformBrandsResponse(undefined)).toEqual([]);
    });

    it("returns [] for a plain string", () => {
      expect(transformBrandsResponse("some string")).toEqual([]);
    });

    it("returns [] for a number", () => {
      expect(transformBrandsResponse(42)).toEqual([]);
    });

    it("returns [] for an object with no recognized keys", () => {
      expect(transformBrandsResponse({ foo: "bar", baz: 123 })).toEqual([]);
    });

    it("returns [] when data is an object with no recognized nested keys", () => {
      expect(transformBrandsResponse({ data: { unknown: [] } })).toEqual([]);
    });
  });
});
