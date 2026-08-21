import { describe, expect, it } from "vitest";

import { IabTaxonomyVersion } from "../../types/brand.types";
import {
  pickBestTaxonomyVersionId,
  resolveTaxonomyVersionId,
  taxonomyVersionToNumber,
} from "../iab-taxonomy.utils";

describe("taxonomyVersionToNumber", () => {
  it("parses major.minor into a sortable number", () => {
    expect(taxonomyVersionToNumber("3.1")).toBe(3001);
    expect(taxonomyVersionToNumber("2.2")).toBe(2002);
    expect(taxonomyVersionToNumber("1.0")).toBe(1000);
  });

  it("treats missing/malformed values as 0", () => {
    expect(taxonomyVersionToNumber(undefined)).toBe(0);
    expect(taxonomyVersionToNumber("")).toBe(0);
    expect(taxonomyVersionToNumber("abc")).toBe(0);
  });
});

describe("pickBestTaxonomyVersionId", () => {
  const v31: IabTaxonomyVersion = { id: "id-3.1", version: "3.1" };
  const v22: IabTaxonomyVersion = { id: "id-2.2", version: "2.2" };
  const v10: IabTaxonomyVersion = { id: "id-1.0", version: "1.0" };

  it("picks 3.1 when present regardless of order", () => {
    expect(pickBestTaxonomyVersionId([v10, v31, v22])).toBe("id-3.1");
  });

  it("falls back to the highest available when v3 is absent", () => {
    expect(pickBestTaxonomyVersionId([v22, v10])).toBe("id-2.2");
  });

  it("falls back to v1 when it is the only version", () => {
    expect(pickBestTaxonomyVersionId([v10])).toBe("id-1.0");
  });

  it("picks the newest within the same major (3.3 > 3.1 > 3.0)", () => {
    const v33: IabTaxonomyVersion = { id: "id-3.3", version: "3.3" };
    const v30: IabTaxonomyVersion = { id: "id-3.0", version: "3.0" };
    expect(pickBestTaxonomyVersionId([v31, v33, v30])).toBe("id-3.3");
  });

  it("picks the single highest across a full unsorted version list", () => {
    const all: IabTaxonomyVersion[] = [
      { id: "id-3.1", version: "3.1" },
      { id: "id-3.0", version: "3.0" },
      { id: "id-2.1", version: "2.1" },
      { id: "id-2.0", version: "2.0" },
      { id: "id-2.2", version: "2.2" },
      { id: "id-1.1", version: "1.1" },
      { id: "id-1.0", version: "1.0" },
      { id: "id-3.3", version: "3.3" },
    ];
    expect(pickBestTaxonomyVersionId(all)).toBe("id-3.3");
  });

  it("returns undefined for an empty list", () => {
    expect(pickBestTaxonomyVersionId([])).toBeUndefined();
  });

  it("ignores entries without an id", () => {
    expect(
      pickBestTaxonomyVersionId([
        { version: "3.1" } as IabTaxonomyVersion,
        v22,
      ]),
    ).toBe("id-2.2");
  });
});

describe("resolveTaxonomyVersionId", () => {
  const v31: IabTaxonomyVersion = { id: "id-3.1", version: "3.1" };
  const v33: IabTaxonomyVersion = { id: "id-3.3", version: "3.3" };
  const v22: IabTaxonomyVersion = { id: "id-2.2", version: "2.2" };
  const v10: IabTaxonomyVersion = { id: "id-1.0", version: "1.0" };

  it("uses exact 3.1 when present, even if a higher version exists", () => {
    expect(resolveTaxonomyVersionId([v33, v31, v22])).toBe("id-3.1");
  });

  it("falls back to the highest available when 3.1 is absent", () => {
    expect(resolveTaxonomyVersionId([v33, v22, v10])).toBe("id-3.3");
  });

  it("falls back to 2.2 when neither 3.1 nor other v3 exist", () => {
    expect(resolveTaxonomyVersionId([v22, v10])).toBe("id-2.2");
  });

  it("returns undefined for an empty list", () => {
    expect(resolveTaxonomyVersionId([])).toBeUndefined();
  });
});
