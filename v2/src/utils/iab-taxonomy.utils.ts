import { IabTaxonomyVersion } from "../types/brand.types";

// Convert a version string ("3.1", "2.2", "1.0") into a sortable number.
// Missing/NaN parts collapse to 0 so malformed entries sort to the bottom.
export function taxonomyVersionToNumber(version?: string): number {
  const parts = (version ?? "").split(".");
  const major = Number(parts[0]) || 0;
  const minor = Number(parts[1]) || 0;
  return major * 1000 + minor;
}

// Pick the ID of the highest available taxonomy version. Prefers 3.x, then
// falls back to 2.x, then 1.x — whatever the API returns. Returns undefined
// when no version has an id.
export function pickBestTaxonomyVersionId(
  versions: IabTaxonomyVersion[],
): string | undefined {
  const sorted = [...versions]
    .filter((v) => v.id)
    .sort(
      (a, b) =>
        taxonomyVersionToNumber(b.version) - taxonomyVersionToNumber(a.version),
    );
  return sorted[0]?.id;
}

// Resolve the taxonomy version to load the hierarchy for. Uses exact version
// "3.1" when present; otherwise falls back to the highest available version.
export function resolveTaxonomyVersionId(
  versions: IabTaxonomyVersion[],
): string | undefined {
  const exact31 = versions.find((v) => v.id && v.version === "3.1")?.id;
  return exact31 ?? pickBestTaxonomyVersionId(versions);
}
