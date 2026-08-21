import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import BrandCreationForm from "../BrandCreationForm";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string) => key,
  }),
}));

const showSuccess = vi.fn();
vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({ showSuccess }),
}));

vi.mock("@utils/storage", () => ({
  getItem: () => JSON.stringify({ activeCompanyId: "company-1" }),
}));

const mockCreateBrand = vi.fn();
const mockSearchBrands = vi.fn();
const mockLinkBrand = vi.fn();

// Mutable so individual tests can simulate a hierarchy API failure/empty
// response and assert the hardcoded fallback kicks in.
const defaultHierarchyResult = {
  data: [
    {
      id: "uuid-iab1",
      unique_id: "IAB1",
      name: "Arts & Entertainment",
      tier: 1,
      children: [],
    },
    {
      id: "uuid-iab2",
      unique_id: "IAB2",
      name: "Automotive",
      tier: 1,
      children: [],
    },
  ],
  isError: false,
  isFetching: false,
};
let hierarchyResult: {
  data?: unknown[];
  isError?: boolean;
  isFetching?: boolean;
} = defaultHierarchyResult;

vi.mock("../../../services/brand/brandSlice", () => ({
  useCreateBrandMutation: () => [mockCreateBrand, { isLoading: false }],
  useLazyGetAllBrandsQuery: () => [mockSearchBrands],
  useLinkBrandToCompanyMutation: () => [mockLinkBrand, { isLoading: false }],
  useGetIabTaxonomyVersionsQuery: () => ({
    data: [
      { id: "version-1", version: "3.1", name: "IAB Content Taxonomy 3.1" },
    ],
  }),
  useGetIabTaxonomyHierarchyQuery: () => hierarchyResult,
}));

function renderBrandForm(
  props: Partial<React.ComponentProps<typeof BrandCreationForm>> = {},
) {
  return render(
    <BrandCreationForm isOpen={true} onClose={vi.fn()} {...props} />,
  );
}

describe("BrandCreationForm", () => {
  const user = userEvent.setup({ delay: null });

  beforeEach(() => {
    vi.clearAllMocks();
    hierarchyResult = defaultHierarchyResult;
    mockCreateBrand.mockReturnValue({
      unwrap: () => Promise.resolve({ id: "brand-1" }),
    });
    mockSearchBrands.mockReturnValue({
      unwrap: () => Promise.resolve([]),
    });
    mockLinkBrand.mockReturnValue({
      unwrap: () => Promise.resolve({}),
    });
  });

  describe("rendering", () => {
    it("renders drawer title when open", () => {
      renderBrandForm();
      expect(screen.getByText("brand_creation.title")).toBeInTheDocument();
    });

    it("renders brand name, category, description and website fields", () => {
      renderBrandForm();
      expect(
        screen.getByLabelText(/brand_creation\.form\.brand_name/i),
      ).toBeInTheDocument();
      expect(
        screen.getByText("brand_creation.form.category_tier1"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("brand_creation.form.description"),
      ).toBeInTheDocument();
      expect(
        screen.getByLabelText(/brand_creation\.form\.website/i),
      ).toBeInTheDocument();
    });

    it("renders Cancel and Create buttons", () => {
      renderBrandForm();
      expect(
        screen.getByRole("button", {
          name: /buttons\.cancel/i,
        }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", {
          name: /brand_creation\.actions\.create/i,
        }),
      ).toBeInTheDocument();
    });
  });

  describe("interactions", () => {
    it("calls onClose when Cancel is clicked", async () => {
      const onClose = vi.fn();
      renderBrandForm({ onClose });
      await user.click(
        screen.getByRole("button", {
          name: /buttons\.cancel/i,
        }),
      );
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it("submits form with required fields and calls onSuccess on success", async () => {
      const onSuccess = vi.fn();
      const onClose = vi.fn();
      renderBrandForm({ onSuccess, onClose });
      await user.type(
        screen.getByPlaceholderText(
          "brand_creation.form.brand_name_placeholder",
        ),
        "Test Brand",
      );
      const categoryTrigger = screen.getByRole("button", {
        name: /brand_creation\.form\.category_placeholder/i,
      });
      await user.click(categoryTrigger);
      const firstCategory = await screen.findByText(
        "IAB1",
        {},
        { timeout: 3000 },
      );
      await user.click(firstCategory);
      await user.click(
        screen.getByRole("button", {
          name: /brand_creation\.actions\.create/i,
        }),
      );
      await waitFor(
        () => {
          expect(mockCreateBrand).toHaveBeenCalled();
        },
        { timeout: 3000 },
      );
      await waitFor(
        () => {
          expect(onSuccess).toHaveBeenCalledWith("brand-1", "Test Brand");
        },
        { timeout: 3000 },
      );
      expect(showSuccess).toHaveBeenCalledWith(
        "brand_creation.form.createdSuccessBrand",
      );
      expect(onClose).toHaveBeenCalled();
    });

    it("calls onSubmit with form data when provided", async () => {
      const onSubmit = vi.fn();
      renderBrandForm({ onSubmit });
      await user.type(
        screen.getByPlaceholderText(
          "brand_creation.form.brand_name_placeholder",
        ),
        "Submit Brand",
      );
      const categoryTrigger = screen.getByRole("button", {
        name: /brand_creation\.form\.category_placeholder/i,
      });
      await user.click(categoryTrigger);
      const firstCategory = await screen.findByText(
        "IAB1",
        {},
        { timeout: 3000 },
      );
      await user.click(firstCategory);
      await user.click(
        screen.getByRole("button", {
          name: /brand_creation\.actions\.create/i,
        }),
      );
      await waitFor(
        () => {
          expect(onSubmit).toHaveBeenCalledWith(
            expect.objectContaining({
              name: "Submit Brand",
              iabCategoryId: "uuid-iab1",
            }),
          );
        },
        { timeout: 3000 },
      );
    });

    it("does not call onSuccess or onClose when create fails", async () => {
      mockCreateBrand.mockRejectedValueOnce(new Error("API error"));
      const onSuccess = vi.fn();
      const onClose = vi.fn();
      const consoleSpy = vi
        .spyOn(console, "error")
        .mockImplementation(() => {});
      renderBrandForm({ onSuccess, onClose });
      await user.type(
        screen.getByPlaceholderText(
          "brand_creation.form.brand_name_placeholder",
        ),
        "Fail Brand",
      );
      const categoryTrigger = screen.getByRole("button", {
        name: /brand_creation\.form\.category_placeholder/i,
      });
      await user.click(categoryTrigger);
      const firstCategory = await screen.findByText(
        "IAB1",
        {},
        { timeout: 3000 },
      );
      await user.click(firstCategory);
      await user.click(
        screen.getByRole("button", {
          name: /brand_creation\.actions\.create/i,
        }),
      );
      await waitFor(
        () => {
          expect(mockCreateBrand).toHaveBeenCalled();
        },
        { timeout: 3000 },
      );
      expect(onSuccess).not.toHaveBeenCalled();
      expect(onClose).not.toHaveBeenCalled();
      expect(showSuccess).not.toHaveBeenCalled();
      consoleSpy.mockRestore();
    });

    it("disables Create button and shows error when name matches a company brand", async () => {
      renderBrandForm({
        companyBrands: [{ id: "b1", label: "Nike" }],
      });
      await user.type(
        screen.getByPlaceholderText(
          "brand_creation.form.brand_name_placeholder",
        ),
        "nike",
      );
      expect(
        screen.getByRole("button", {
          name: /brand_creation\.actions\.create/i,
        }),
      ).toBeDisabled();
      expect(
        screen.getByText("brand_creation.form.own_brand_exists"),
      ).toBeInTheDocument();
    });
  });

  describe("hierarchy fallback", () => {
    it("uses hardcoded taxonomy when the hierarchy API errors", async () => {
      hierarchyResult = { data: [], isError: true, isFetching: false };
      renderBrandForm();
      await user.click(
        screen.getByRole("button", {
          name: /brand_creation\.form\.category_placeholder/i,
        }),
      );
      // "Attractions" is the first tier-1 category in the hardcoded fallback.
      expect(
        await screen.findByText("Attractions", {}, { timeout: 3000 }),
      ).toBeInTheDocument();
    });

    it("uses hardcoded taxonomy when the hierarchy resolves empty", async () => {
      hierarchyResult = { data: [], isError: false, isFetching: false };
      renderBrandForm();
      await user.click(
        screen.getByRole("button", {
          name: /brand_creation\.form\.category_placeholder/i,
        }),
      );
      expect(
        await screen.findByText("Attractions", {}, { timeout: 3000 }),
      ).toBeInTheDocument();
    });

    it("does not fall back while the hierarchy is still fetching", async () => {
      hierarchyResult = { data: [], isError: false, isFetching: true };
      renderBrandForm();
      await user.click(
        screen.getByRole("button", {
          name: /brand_creation\.form\.category_placeholder/i,
        }),
      );
      expect(screen.queryByText("Attractions")).not.toBeInTheDocument();
    });
  });

  describe("category search", () => {
    it("filters tier 1 options by name via the search input", async () => {
      renderBrandForm();
      await user.click(
        screen.getByRole("button", {
          name: /brand_creation\.form\.category_placeholder/i,
        }),
      );
      // Both tier-1 options present before searching.
      expect(
        await screen.findByText("Arts & Entertainment", {}, { timeout: 3000 }),
      ).toBeInTheDocument();
      expect(screen.getByText("Automotive")).toBeInTheDocument();

      const searchInput = screen.getByPlaceholderText(
        "brand_creation.form.category_search_placeholder",
      );
      await user.type(searchInput, "Automotive");

      await waitFor(() => {
        expect(
          screen.queryByText("Arts & Entertainment"),
        ).not.toBeInTheDocument();
      });
      expect(screen.getByText("Automotive")).toBeInTheDocument();
    });

    it("filters tier 1 options by IAB code via the search input", async () => {
      renderBrandForm();
      await user.click(
        screen.getByRole("button", {
          name: /brand_creation\.form\.category_placeholder/i,
        }),
      );
      expect(
        await screen.findByText("Arts & Entertainment", {}, { timeout: 3000 }),
      ).toBeInTheDocument();

      const searchInput = screen.getByPlaceholderText(
        "brand_creation.form.category_search_placeholder",
      );
      // "IAB2" is the unique_id of "Automotive".
      await user.type(searchInput, "IAB2");

      await waitFor(() => {
        expect(
          screen.queryByText("Arts & Entertainment"),
        ).not.toBeInTheDocument();
      });
      expect(screen.getByText("Automotive")).toBeInTheDocument();
    });
  });

  describe("accessibility", () => {
    it("has brand name input with label", () => {
      renderBrandForm();
      expect(
        screen.getByLabelText(/brand_creation\.form\.brand_name/i),
      ).toBeInTheDocument();
    });
  });

  describe("link existing brand", () => {
    it("shows a compact confirmation with no dangling sections for a brand with no extra metadata", async () => {
      mockSearchBrands.mockReturnValue({
        unwrap: () =>
          Promise.resolve([
            { id: "brand-9", name: "ADMIN Test", is_active: true },
          ]),
      });
      renderBrandForm({ companyId: "company-1" });

      await user.type(
        screen.getByLabelText(/brand_creation\.form\.brand_name/i),
        "ADMIN Test",
      );

      await waitFor(() => {
        expect(screen.getByText("ADMIN Test")).toBeInTheDocument();
      });
      await user.click(screen.getByText("ADMIN Test"));

      expect(screen.getByText("brand_creation.title_link")).toBeInTheDocument();
      expect(
        screen.getByText("brand_creation.form.active"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("brand_creation.form.link_notice"),
      ).toBeInTheDocument();
      // No industry, categories, description or linked-companies data - none
      // of those sections should render an empty shell.
      expect(
        screen.queryByText("brand_creation.form.industry"),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByText("brand_creation.form.companies_linked"),
      ).not.toBeInTheDocument();
    });

    it("shows industry as a subtitle, plus categories, description and companies linked when present", async () => {
      mockSearchBrands.mockReturnValue({
        unwrap: () =>
          Promise.resolve([
            {
              id: "brand-9",
              name: "Coca-Cola Zero Sugar",
              is_active: true,
              industry: "Beverages",
              description: "Global zero-sugar cola brand.",
              iab_categories: [{ code: "IAB8", name: "Food & Drink" }],
              company_ids: ["c1", "c2", "c3", "c4"],
            },
          ]),
      });
      renderBrandForm({ companyId: "company-1" });

      await user.type(
        screen.getByLabelText(/brand_creation\.form\.brand_name/i),
        "Coca-Cola Zero Sugar",
      );

      await waitFor(() => {
        expect(screen.getByText("Coca-Cola Zero Sugar")).toBeInTheDocument();
      });
      await user.click(screen.getByText("Coca-Cola Zero Sugar"));

      expect(screen.getByText("Beverages")).toBeInTheDocument();
      expect(screen.getByText("Food & Drink")).toBeInTheDocument();
      expect(
        screen.getByText("Global zero-sugar cola brand."),
      ).toBeInTheDocument();
      expect(
        screen.getByText("brand_creation.form.companies_linked"),
      ).toBeInTheDocument();
      expect(screen.getByText("4")).toBeInTheDocument();
    });

    it("links the selected brand to the company when Link is clicked", async () => {
      mockSearchBrands.mockReturnValue({
        unwrap: () =>
          Promise.resolve([
            { id: "brand-9", name: "ADMIN Test", is_active: true },
          ]),
      });
      const onSuccess = vi.fn();
      renderBrandForm({ companyId: "company-1", onSuccess });

      await user.type(
        screen.getByLabelText(/brand_creation\.form\.brand_name/i),
        "ADMIN Test",
      );
      await waitFor(() => {
        expect(screen.getByText("ADMIN Test")).toBeInTheDocument();
      });
      await user.click(screen.getByText("ADMIN Test"));

      await user.click(
        screen.getByRole("button", { name: /brand_creation\.actions\.link/i }),
      );

      await waitFor(() => {
        expect(mockLinkBrand).toHaveBeenCalledWith({
          companyId: "company-1",
          brandId: "brand-9",
        });
      });
      expect(onSuccess).toHaveBeenCalledWith("brand-9", "ADMIN Test");
    });
  });
});
