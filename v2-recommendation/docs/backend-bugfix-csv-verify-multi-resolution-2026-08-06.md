# Bugfix — CSV verify/import multi-resolution OR match

**Date:** 2026-08-06  
**Branch:** `hotfix/csv-verify-multi-resolution` (from `k8s/prod`)  
**Confidence:** High — reproduced via code path + regression tests

## Problem

Adserver line items can select multiple resolutions. CSV `/verify` accepted only a single `resolution` string and required an exact panel match against that one value. When FE passed the first selected resolution (`1920x1080`) but inventory only supported the second (`720x1280`), verify incorrectly returned:

`Inventory does not support the 1920x1080 resolution`

Scored recommendation fetch already OR-matched a `resolutions` list; CSV did not.

## Root cause

`InventoryCsvImportController` bound `resolution` as `String`. `CsvMatchCriteria.resolution` was a single string. `InventoryCsvImportService` checked equality against that one value only.

## Fix

1. Controller: `List<String> resolution` — Spring binds repeated form params (`resolution=A&resolution=B`).
2. `CsvMatchCriteria.resolutions`: `List<String>` (null/empty = no check).
3. Service: OR match — VALID if any requested resolution matches any panel `pixelWidth x pixelHeight`.
4. Error message: single → unchanged wording; multiple → `any of the requested resolutions: …`.

## API usage (backward compatible)

```
# single (unchanged)
resolution=1920x1080

# multiple (OR) — one param, comma-separated
resolution=1920x1080,720x1280
```

Same for `POST .../inventory-imports` (import).

## Tests

- `InventoryCsvImportServiceTest.verify_multipleResolutions_passesWhenInventoryMatchesAny`
- `InventoryCsvImportServiceTest.verify_multipleResolutions_failsOnlyWhenNoneMatch`
- `InventoryCsvImportControllerTest.verify_forwardsMultipleResolutionParamsAsList`
- Existing single-resolution tests updated to `List.of(...)`.

## Overall

**Fixed** — CSV verify/import now OR-matches multiple resolutions, aligned with scored recommendation fetch.
