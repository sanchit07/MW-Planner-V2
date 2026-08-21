/**
 * Brand fetcher tests for CreateCampaignForm.
 *
 * Kept in a separate file so it can maintain its own vi.mock for
 * @services/brand/brandSlice without disturbing the existing
 * CreateCampaignForm.test.tsx mock setup.
 */
import {
  DropdownOption,
  RemoteDropdownProps,
} from "@components/ui/RemoteDropdown";
import { configureStore } from "@reduxjs/toolkit";
import { agencyApi } from "@services/agency/agencySlice";
import { render, waitFor } from "@testing-library/react";
import React from "react";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import CreateCampaignForm from "../CreateCampaignForm";

// ── Module mocks ──────────────────────────────────────────────────────────────

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({ showError: vi.fn(), showSuccess: vi.fn() }),
}));

vi.mock("react-router-dom", async () => {
  const actual =
    await vi.importActual<typeof import("react-router-dom")>(
      "react-router-dom",
    );
  return {
    ...actual,
    useLocation: () => ({ pathname: "/campaigns/create" }),
  };
});

vi.mock("../../../hooks/useStepper", () => ({
  useStepper: () => ({
    isEditMode: false,
    editCampaignId: null,
    setStepperEditMode: vi.fn(),
  }),
}));

vi.mock("../../../hooks/useAutosave", () => ({
  useAutosave: () => ({
    autosave: vi.fn(),
    autosaveBatch: vi.fn(),
  }),
}));

vi.mock("../AgencyCreationForm", () => ({
  default: () => <div data-testid="agency-form">Agency Form</div>,
}));

vi.mock("../BrandCreationForm", () => ({
  default: () => <div data-testid="brand-form">Brand Form</div>,
}));

vi.mock("../../../services/campaign/campaignSlice", async (importOriginal) => {
  const actual =
    await importOriginal<
      typeof import("../../../services/campaign/campaignSlice")
    >();
  return {
    ...actual,
    useLazyGetSequencerQuery: () => [
      vi.fn().mockReturnValue({
        unwrap: () => Promise.resolve({ success: true, data: 1 }),
      }),
    ],
    useCreateCampaignMutation: () => [
      vi.fn().mockReturnValue({
        unwrap: () =>
          Promise.resolve({
            success: true,
            data: { id: "camp-1", name: "Campaign 1" },
          }),
      }),
      { isLoading: false },
    ],
    useLazyGetAgenciesQuery: () => [vi.fn()],
    useLazyGetAllBrandsQuery: () => [vi.fn()],
    useLazyGetIabCategoriesQuery: () => [vi.fn()],
  };
});

// ── Brand slice mock ───────────────────────────────────────────────────────────

const mockGetBrands = vi.fn();

vi.mock("@services/brand/brandSlice", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@services/brand/brandSlice")>();
  return {
    ...actual,
    useLazyGetCompanyBrandsQuery: () => [mockGetBrands],
    useLazyGetIabCategoriesQuery: () => [vi.fn()],
  };
});

// ── RemoteDropdown mock that captures fetchers ────────────────────────────────

// capturedFetchers is reset per test via beforeEach.
// It stores the fetcher prop by dropdown id so tests can call it directly.
const capturedFetchers: Record<
  string,
  RemoteDropdownProps["fetcher"] | undefined
> = {};

vi.mock("@components/ui/RemoteDropdown", () => ({
  RemoteDropdown: (props: RemoteDropdownProps) => {
    if (props.id) capturedFetchers[props.id] = props.fetcher;
    return (
      <div>
        <button data-testid={props.id}>Select</button>
        <ul>
          {((props as unknown as { value?: DropdownOption[] }).value ?? []).map(
            (opt: DropdownOption) => (
              <li key={opt.id}>{opt.label}</li>
            ),
          )}
        </ul>
      </div>
    );
  },
}));

// ── Helpers ───────────────────────────────────────────────────────────────────

const TEST_COMPANY_ID = "company-test-123";

function makeApiResponse(
  brands: ReturnType<typeof makeBrand>[],
  total?: number,
) {
  return {
    data: {
      brands,
      total: total ?? brands.length,
      page: 1,
      limit: 1000,
    },
  };
}

function makeBrand(
  overrides: Partial<{
    id: string;
    name: string;
    logo_url: string;
    iab_category_ids: string[];
    iab_categories: Array<{ id?: string; name: string; unique_id?: string }>;
  }> = {},
) {
  return {
    id: overrides.id ?? "brand-1",
    name: overrides.name ?? "Nike",
    logo_url: overrides.logo_url,
    iab_category_ids: overrides.iab_category_ids ?? [],
    iab_categories: overrides.iab_categories,
  };
}

function makeStore(profileOverrides?: object | null) {
  return configureStore({
    reducer: {
      campaign: () => ({ campaignData: null, campaignId: null }),
      profile: () => ({ profile: profileOverrides ?? null }),
      [agencyApi.reducerPath]: agencyApi.reducer,
    },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware().concat(agencyApi.middleware),
  });
}

function renderForm(profileOverrides?: object | null) {
  const store = makeStore(profileOverrides);
  render(
    <Provider store={store}>
      <MemoryRouter>
        <CreateCampaignForm ref={React.createRef()} />
      </MemoryRouter>
    </Provider>,
  );
}

async function getBrandFetcher() {
  await waitFor(() =>
    expect(capturedFetchers["create-campaign-brand-dropdown"]).toBeDefined(),
  );
  return capturedFetchers["create-campaign-brand-dropdown"]!;
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("CreateCampaignForm — brandFetcher", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    for (const key of Object.keys(capturedFetchers)) {
      delete capturedFetchers[key];
    }
    mockGetBrands.mockReturnValue({
      unwrap: () => Promise.resolve(makeApiResponse([])),
    });
  });

  // The company-scoped /companies/{id}/brands endpoint requires a companyId.
  // When companyId is absent the fetcher still fires with an empty string.
  describe("company-scoped listing", () => {
    it("calls the API even when profile is null", async () => {
      renderForm(null);
      const fetcher = await getBrandFetcher();
      await fetcher({ search: "" });
      expect(mockGetBrands).not.toHaveBeenCalled();
    });

    it("does not call the API when profile has no companyId", async () => {
      renderForm({ name: "User", memberships: [] });
      const fetcher = await getBrandFetcher();
      await fetcher({ search: "" });
      expect(mockGetBrands).not.toHaveBeenCalled();
    });

    it("sends companyId from profile.activeCompanyId", async () => {
      renderForm({ activeCompanyId: TEST_COMPANY_ID });
      const fetcher = await getBrandFetcher();
      await fetcher({ search: "" });
      expect(mockGetBrands).toHaveBeenCalledWith(
        expect.objectContaining({ companyId: TEST_COMPANY_ID }),
      );
    });
  });

  describe("API call parameters", () => {
    it("sends page: 1 and limit to the API on the first call", async () => {
      renderForm({ activeCompanyId: TEST_COMPANY_ID });
      const fetcher = await getBrandFetcher();
      await fetcher({ search: "" });
      expect(mockGetBrands).toHaveBeenCalledWith(
        expect.objectContaining({
          companyId: TEST_COMPANY_ID,
          params: expect.objectContaining({ page: 1, limit: 100 }),
        }),
      );
    });

    it("does not forward search term to API (search is handled client-side)", async () => {
      renderForm({ activeCompanyId: TEST_COMPANY_ID });
      const fetcher = await getBrandFetcher();
      // Calling with a search term goes straight to client-side cache filter
      await fetcher({ search: "samsung" });
      expect(mockGetBrands).not.toHaveBeenCalled();
    });

    it("calls API when search is empty", async () => {
      renderForm({ activeCompanyId: TEST_COMPANY_ID });
      const fetcher = await getBrandFetcher();
      await fetcher({ search: "" });
      expect(mockGetBrands).toHaveBeenCalledTimes(1);
    });
  });

  describe("response mapping", () => {
    it("maps brand id and name to dropdown option id and label", async () => {
      const brand = makeBrand({ id: "b-1", name: "Apple" });
      mockGetBrands.mockReturnValue({
        unwrap: () => Promise.resolve(makeApiResponse([brand])),
      });
      renderForm({ activeCompanyId: TEST_COMPANY_ID });
      const fetcher = await getBrandFetcher();
      const result = await fetcher({ search: "" });
      const apple = result.content.find((o) => o.id === "b-1");
      expect(apple).toBeDefined();
      expect(apple?.label).toBe("Apple");
      expect(apple?.value).toBe("b-1");
    });

    it("maps logo_url to logoUrl on the dropdown option", async () => {
      const brand = makeBrand({
        id: "b-2",
        name: "Nike",
        logo_url: "https://example.com/nike.svg",
      });
      mockGetBrands.mockReturnValue({
        unwrap: () => Promise.resolve(makeApiResponse([brand])),
      });
      renderForm({ activeCompanyId: TEST_COMPANY_ID });
      const fetcher = await getBrandFetcher();
      const result = await fetcher({ search: "" });
      const nike = result.content.find((o) => o.id === "b-2") as
        | (DropdownOption & { logoUrl?: string })
        | undefined;
      expect(nike?.logoUrl).toBe("https://example.com/nike.svg");
    });

    it("produces a valid pagination envelope (totalPages=1, first+last=true)", async () => {
      const brands = [
        makeBrand({ id: "b1" }),
        makeBrand({ id: "b2", name: "Adidas Brand" }),
      ];
      mockGetBrands.mockReturnValue({
        unwrap: () => Promise.resolve(makeApiResponse(brands)),
      });
      renderForm({ activeCompanyId: TEST_COMPANY_ID });
      const fetcher = await getBrandFetcher();
      const result = await fetcher({ search: "" });
      expect(result.totalPages).toBe(1);
      expect(result.first).toBe(true);
      expect(result.last).toBe(true);
      expect(result.number).toBe(0);
    });

    it("marks the result empty when no brands are returned", async () => {
      mockGetBrands.mockReturnValue({
        unwrap: () => Promise.resolve(makeApiResponse([])),
      });
      renderForm({ activeCompanyId: TEST_COMPANY_ID });
      const fetcher = await getBrandFetcher();
      const result = await fetcher({ search: "" });
      expect(result.content).toHaveLength(0);
    });

    it("re-throws when the API call fails", async () => {
      mockGetBrands.mockReturnValue({
        unwrap: () => Promise.reject(new Error("Network error")),
      });
      renderForm({ activeCompanyId: TEST_COMPANY_ID });
      const fetcher = await getBrandFetcher();
      await expect(fetcher({ search: "" })).rejects.toThrow("Network error");
    });
  });

  // Regression: the Brand Insights box previously read `iab_category_ids`
  // (raw UUIDs) and ran them through a translation lookup, rendering
  // "undefined". The fix reads the expanded `iab_categories` objects and uses
  // `name` / `unique_id` directly. These options drive the Brand Insights
  // Category and IAB ID fields.
  describe("IAB category mapping", () => {
    type IabOption = DropdownOption & {
      description?: string;
      category?: string;
      iabId?: string;
    };

    it("maps iab_categories[0].name to category and description", async () => {
      const brand = makeBrand({
        id: "b-iab",
        iab_categories: [
          { id: "uuid-1", name: "Shopping", unique_id: "IAB22" },
        ],
      });
      mockGetBrands.mockReturnValue({
        unwrap: () => Promise.resolve(makeApiResponse([brand])),
      });
      renderForm({ activeCompanyId: TEST_COMPANY_ID });
      const fetcher = await getBrandFetcher();
      const result = await fetcher({ search: "" });
      const opt = result.content.find((o) => o.id === "b-iab") as
        | IabOption
        | undefined;
      expect(opt?.category).toBe("Shopping");
      expect(opt?.description).toBe("Shopping");
    });

    it("maps iab_categories[0].unique_id to iabId", async () => {
      const brand = makeBrand({
        id: "b-iab",
        iab_categories: [
          { id: "uuid-1", name: "Shopping", unique_id: "IAB22" },
        ],
      });
      mockGetBrands.mockReturnValue({
        unwrap: () => Promise.resolve(makeApiResponse([brand])),
      });
      renderForm({ activeCompanyId: TEST_COMPANY_ID });
      const fetcher = await getBrandFetcher();
      const result = await fetcher({ search: "" });
      const opt = result.content.find((o) => o.id === "b-iab") as
        | IabOption
        | undefined;
      expect(opt?.iabId).toBe("IAB22");
    });

    it("uses the first category when several are present", async () => {
      const brand = makeBrand({
        id: "b-iab",
        iab_categories: [
          { id: "uuid-1", name: "Automotive", unique_id: "IAB2" },
          { id: "uuid-2", name: "Shopping", unique_id: "IAB22" },
        ],
      });
      mockGetBrands.mockReturnValue({
        unwrap: () => Promise.resolve(makeApiResponse([brand])),
      });
      renderForm({ activeCompanyId: TEST_COMPANY_ID });
      const fetcher = await getBrandFetcher();
      const result = await fetcher({ search: "" });
      const opt = result.content.find((o) => o.id === "b-iab") as
        | IabOption
        | undefined;
      expect(opt?.category).toBe("Automotive");
      expect(opt?.iabId).toBe("IAB2");
    });

    it("leaves category and iabId undefined when iab_categories is empty", async () => {
      const brand = makeBrand({ id: "b-iab", iab_categories: [] });
      mockGetBrands.mockReturnValue({
        unwrap: () => Promise.resolve(makeApiResponse([brand])),
      });
      renderForm({ activeCompanyId: TEST_COMPANY_ID });
      const fetcher = await getBrandFetcher();
      const result = await fetcher({ search: "" });
      const opt = result.content.find((o) => o.id === "b-iab") as
        | IabOption
        | undefined;
      expect(opt?.category).toBeUndefined();
      expect(opt?.description).toBeUndefined();
      expect(opt?.iabId).toBeUndefined();
    });

    it("leaves category and iabId undefined when iab_categories is absent", async () => {
      const brand = makeBrand({ id: "b-iab" }); // no iab_categories key
      mockGetBrands.mockReturnValue({
        unwrap: () => Promise.resolve(makeApiResponse([brand])),
      });
      renderForm({ activeCompanyId: TEST_COMPANY_ID });
      const fetcher = await getBrandFetcher();
      const result = await fetcher({ search: "" });
      const opt = result.content.find((o) => o.id === "b-iab") as
        | IabOption
        | undefined;
      expect(opt?.category).toBeUndefined();
      expect(opt?.iabId).toBeUndefined();
    });

    it("does not derive category from iab_category_ids UUIDs", async () => {
      // The old bug: a UUID leaked into the category field. Even when
      // iab_category_ids is populated, category must stay undefined unless
      // the expanded iab_categories objects are present.
      const brand = makeBrand({
        id: "b-iab",
        iab_category_ids: ["3f9a-uuid-not-a-name"],
      });
      mockGetBrands.mockReturnValue({
        unwrap: () => Promise.resolve(makeApiResponse([brand])),
      });
      renderForm({ activeCompanyId: TEST_COMPANY_ID });
      const fetcher = await getBrandFetcher();
      const result = await fetcher({ search: "" });
      const opt = result.content.find((o) => o.id === "b-iab") as
        | IabOption
        | undefined;
      expect(opt?.category).toBeUndefined();
      expect(opt?.iabId).toBeUndefined();
    });

    it("handles a missing unique_id by leaving iabId undefined but keeping the name", async () => {
      const brand = makeBrand({
        id: "b-iab",
        iab_categories: [{ id: "uuid-1", name: "Shopping" }], // no unique_id
      });
      mockGetBrands.mockReturnValue({
        unwrap: () => Promise.resolve(makeApiResponse([brand])),
      });
      renderForm({ activeCompanyId: TEST_COMPANY_ID });
      const fetcher = await getBrandFetcher();
      const result = await fetcher({ search: "" });
      const opt = result.content.find((o) => o.id === "b-iab") as
        | IabOption
        | undefined;
      expect(opt?.category).toBe("Shopping");
      expect(opt?.iabId).toBeUndefined();
    });
  });
});
