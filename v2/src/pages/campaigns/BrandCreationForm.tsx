import { zodResolver } from "@hookform/resolvers/zod";
import { useAnnounce } from "@hooks/useAnnounce";
import { useTranslate } from "@tolgee/react";
import { resolveTaxonomyVersionId } from "@utils/iab-taxonomy.utils";
import { Building2, Info, Loader2 } from "lucide-react";
import React, { useEffect, useMemo, useRef, useState } from "react";
import { useForm, Controller } from "react-hook-form";

import { Button } from "../../components/ui/Button";
import {
  Dropdown,
  DropdownContent,
  DropdownItem,
  DropdownTrigger,
  DropdownScrollableContent,
  DropdownSearch,
} from "../../components/ui/Dropdown";
import { Input } from "../../components/ui/Input";
import { Label } from "../../components/ui/Label";
import { ModalDrawer } from "../../components/ui/ModalDrawer";
import { IAB_TAXONOMY_HIERARCHY_FALLBACK } from "../../constants/iab-taxonomy.constants";
import brandSchema, { BrandFormData } from "../../schemas/brands/brand.schema";
import {
  useCreateBrandMutation,
  useLazyGetAllBrandsQuery,
  useLinkBrandToCompanyMutation,
  useGetIabTaxonomyVersionsQuery,
  useGetIabTaxonomyHierarchyQuery,
} from "../../services/brand/brandSlice";
import {
  IabCategory,
  IamBrand,
  IabTaxonomyNode,
  IamBrandCreateRequest,
} from "../../types/brand.types";

interface BrandCreationFormProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit?: (data: BrandFormData) => void;
  onSuccess?: (brandId: string, brandName?: string) => void;
  companyId?: string;
  iabCategories?: IabCategory[];
  companyBrands?: { id: string; label: string }[];
}

const BrandCreationForm: React.FC<BrandCreationFormProps> = ({
  isOpen,
  onClose,
  onSubmit,
  onSuccess,
  companyId,
  companyBrands = [],
}) => {
  const { t: tBrands } = useTranslate(["brands"]);
  const { t: tCommon } = useTranslate(["common"]);
  const [createBrand, { isLoading }] = useCreateBrandMutation();
  const [searchBrands] = useLazyGetAllBrandsQuery();
  const [linkBrandToCompany] = useLinkBrandToCompanyMutation();
  const { showSuccess } = useAnnounce();

  const [suggestions, setSuggestions] = useState<IamBrand[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [selectedBrand, setSelectedBrand] = useState<IamBrand | null>(null);
  const [isLinking, setIsLinking] = useState(false);
  const searchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Step 1 — resolve the ID of IAB taxonomy version 3.1
  const { data: taxonomyVersions = [] } = useGetIabTaxonomyVersionsQuery(
    undefined,
    { skip: !isOpen },
  );

  // Use exact version 3.1 when present; otherwise fall back to the highest
  // available version so the hierarchy still loads.
  const version31Id = useMemo(
    () => resolveTaxonomyVersionId(taxonomyVersions),
    [taxonomyVersions],
  );

  // Step 2 — fetch the full hierarchy for version 3.1
  const {
    data: hierarchyData = [],
    isError: hierarchyError,
    isFetching: hierarchyFetching,
  } = useGetIabTaxonomyHierarchyQuery(version31Id ?? "", {
    skip: !isOpen || !version31Id,
  });

  // Fall back to the hardcoded taxonomy when the API errors, or resolves with
  // no nodes once fetching has settled (e.g. missing version id or empty body),
  // so the category picker always has data.
  const hierarchyNodes = useMemo<IabTaxonomyNode[]>(() => {
    if (hierarchyData.length > 0) return hierarchyData;
    if (hierarchyError || (!hierarchyFetching && isOpen))
      return IAB_TAXONOMY_HIERARCHY_FALLBACK;
    return hierarchyData;
  }, [hierarchyData, hierarchyError, hierarchyFetching, isOpen]);

  // Lookup maps — index by both `id` (UUID) and `unique_id` (e.g. "IAB4")
  // because `children` in the API response may reference either format.
  const nodeMap = useMemo(() => {
    const map = new Map<string, (typeof hierarchyNodes)[number]>();
    for (const node of hierarchyNodes) {
      map.set(node.id, node);
      if (node.unique_id) map.set(node.unique_id, node);
    }
    return map;
  }, [hierarchyNodes]);

  // Tier 1: prefer nodes explicitly tagged tier === 1; fall back to nodes not
  // referenced as a child by any other node (handles APIs that omit the tier field).
  const tier1Options = useMemo(() => {
    const hasTierField = hierarchyNodes.some((n) => n.tier != null);
    let candidates: typeof hierarchyNodes;
    if (hasTierField) {
      candidates = hierarchyNodes.filter((n) => n.tier === 1);
    } else {
      const childKeys = new Set<string>();
      for (const node of hierarchyNodes) {
        for (const childRef of node.children ?? []) {
          if (typeof childRef === "string") childKeys.add(childRef);
        }
      }
      candidates = hierarchyNodes.filter(
        (n) => !childKeys.has(n.id) && !childKeys.has(n.unique_id ?? ""),
      );
    }
    return [...candidates].sort((a, b) => a.name.localeCompare(b.name));
  }, [hierarchyNodes]);

  const {
    register,
    handleSubmit,
    formState: { errors },
    control,
    reset,
    watch,
    setValue,
  } = useForm({
    resolver: zodResolver(brandSchema),
    defaultValues: {
      name: "",
      iabCategoryId: "",
      iabSubcategoryId: "",
      description: "",
      website: "",
    },
  });

  const brandName = watch("name");

  // Debounced search — only active in create mode (no brand selected yet)
  useEffect(() => {
    if (selectedBrand) return;

    const trimmed = brandName?.trim() ?? "";
    if (trimmed.length < 2) {
      setSuggestions([]);
      setIsSearching(false);
      return;
    }

    setIsSearching(true);
    if (searchTimerRef.current) clearTimeout(searchTimerRef.current);

    searchTimerRef.current = setTimeout(async () => {
      try {
        const results = await searchBrands({ search: trimmed }).unwrap();
        setSuggestions(results ?? []);
      } catch {
        setSuggestions([]);
      } finally {
        setIsSearching(false);
      }
    }, 300);

    return () => {
      if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
    };
  }, [brandName, searchBrands, selectedBrand]);

  const watchedTier1Id = watch("iabCategoryId");

  // Tier 2: children of the selected tier 1 node.
  // Children may be nested node objects (tree response) or string refs (flat
  // response). Both are handled: objects are used directly, strings are
  // resolved via nodeMap (keyed by both id and unique_id).
  const tier2Options = useMemo(() => {
    const tier1Node = nodeMap.get(watchedTier1Id);
    if (!tier1Node?.children?.length) return [];

    const children = tier1Node.children as (IabTaxonomyNode | string)[];
    const firstChild = children[0];

    let nodes: IabTaxonomyNode[];
    if (typeof firstChild === "object" && firstChild !== null) {
      nodes = children as IabTaxonomyNode[];
    } else {
      nodes = (children as string[])
        .map((ref) => nodeMap.get(ref))
        .filter((n): n is IabTaxonomyNode => !!n);
    }

    return [...nodes].sort((a, b) => a.name.localeCompare(b.name));
  }, [watchedTier1Id, nodeMap]);

  // Reset tier 2 whenever tier 1 changes
  useEffect(() => {
    setValue("iabSubcategoryId", "");
  }, [watchedTier1Id, setValue]);

  // Reset everything when the drawer closes
  useEffect(() => {
    if (!isOpen) {
      reset({
        name: "",
        iabCategoryId: "",
        iabSubcategoryId: "",
        description: "",
        website: "",
      });
      setSuggestions([]);
      setIsSearching(false);
      setSelectedBrand(null);
    }
  }, [isOpen, reset]);

  // Close drawer — always resets fully (used by X button and success callbacks)
  const handleClose = () => {
    reset({
      name: "",
      iabCategoryId: "",
      iabSubcategoryId: "",
      description: "",
      website: "",
    });
    setSuggestions([]);
    setSelectedBrand(null);
    onClose();
  };

  // Footer Cancel: if in link mode, revert to create mode; otherwise close drawer
  const handleCancel = () => {
    if (selectedBrand) {
      reset({
        name: "",
        iabCategoryId: "",
        iabSubcategoryId: "",
        description: "",
        website: "",
      });
      setSuggestions([]);
      setSelectedBrand(null);
    } else {
      handleClose();
    }
  };

  // Clicking a suggestion enters link mode — modal title and body switch to brand details
  const handleSelectSuggestion = (brand: IamBrand) => {
    setSelectedBrand(brand);
    setSuggestions([]);
  };

  // Link selected brand to the current company
  const handleLinkBrand = async () => {
    if (!selectedBrand || !companyId) return;
    setIsLinking(true);
    try {
      await linkBrandToCompany({
        companyId,
        brandId: selectedBrand.id,
      }).unwrap();
      showSuccess(tBrands("brand_creation.form.link_brand_success"));
      if (onSuccess) onSuccess(selectedBrand.id, selectedBrand.name);
      handleClose();
    } catch (err) {
      console.error("Failed to link brand:", err);
    } finally {
      setIsLinking(false);
    }
  };

  // Create brand from scratch (existing flow)
  const onFormSubmit = async (data: BrandFormData) => {
    try {
      // Use the most specific category: tier 2 if selected, else tier 1
      const categoryId = data.iabSubcategoryId || data.iabCategoryId;

      const requestBody: IamBrandCreateRequest = {
        name: data.name,
        ...(categoryId && { iab_category_ids: [categoryId] }),
        ...(data.description && { description: data.description }),
        ...(data.website && { logo_url: data.website }),
        ...(companyId && { company_ids: [companyId] }),
      };

      const result = await createBrand({
        brandData: requestBody,
        activeCompanyId: companyId ?? "",
      }).unwrap();

      if (onSubmit) onSubmit(data);
      if (onSuccess && result.id) onSuccess(result.id, data.name);
      if (result.id) {
        showSuccess(tBrands("brand_creation.form.createdSuccessBrand"));
        onClose();
      }
    } catch (err) {
      console.error("Failed to create brand:", err);
    }
  };

  const isLinkMode = !!selectedBrand;
  const trimmedBrandName = brandName?.trim() ?? "";
  // Brand already claimed by at least one company — can't create again
  const hasOwnBrand =
    !selectedBrand &&
    trimmedBrandName.length >= 2 &&
    companyBrands.some(
      (b) => b.label.toLowerCase() === trimmedBrandName.toLowerCase(),
    );
  // Exact name match exists elsewhere — link instead of create
  const hasExactMatch =
    !hasOwnBrand &&
    !selectedBrand &&
    trimmedBrandName.length >= 2 &&
    suggestions.some(
      (b) => b.name.toLowerCase() === trimmedBrandName.toLowerCase(),
    );
  const showSuggestions =
    !hasOwnBrand &&
    !selectedBrand &&
    trimmedBrandName.length >= 2 &&
    (isSearching || suggestions.length > 0);

  return (
    <ModalDrawer
      isOpen={isOpen}
      onClose={handleClose}
      onBack={isLinkMode ? handleCancel : undefined}
      title={
        isLinkMode
          ? tBrands("brand_creation.title_link")
          : tBrands("brand_creation.title")
      }
      size="lg"
      footer={
        <div className="flex justify-end gap-3">
          <Button
            variant="outline"
            size="md"
            className="outline-mw-primary-500 text-mw-primary-500"
            onClick={handleCancel}
            disabled={isLoading || isLinking}
          >
            {tCommon("buttons.cancel")}
          </Button>

          {isLinkMode ? (
            <Button
              type="button"
              onClick={handleLinkBrand}
              variant="primary"
              disabled={isLinking}
            >
              {isLinking
                ? tBrands("brand_creation.actions.linking")
                : tBrands("brand_creation.actions.link")}
            </Button>
          ) : (
            <Button
              type="button"
              onClick={handleSubmit(onFormSubmit)}
              variant="primary"
              disabled={isLoading || hasExactMatch || hasOwnBrand}
            >
              {isLoading
                ? tBrands("brand_creation.actions.creating")
                : tBrands("brand_creation.actions.create")}
            </Button>
          )}
        </div>
      }
    >
      {isLinkMode ? (
        /* Link mode — compact confirmation: panel height follows the
           content instead of a fixed card + notice box, so a brand with
           little metadata (e.g. no industry/description/categories) never
           leaves a half-empty drawer. */
        <div className="pt-2">
          {/* Header: avatar + name + status + industry subtitle */}
          <div className="flex items-center gap-3.5 pb-4 border-b border-mw-neutral-100 dark:border-mw-neutral-700">
            <div className="h-14 w-14 flex-shrink-0 rounded-xl overflow-hidden bg-gradient-to-br from-mw-primary-50 to-white dark:from-mw-primary-900/30 dark:to-mw-neutral-800 border border-mw-primary-100 dark:border-mw-primary-800 flex items-center justify-center">
              {selectedBrand.logo_url ? (
                <>
                  <img
                    src={selectedBrand.logo_url}
                    alt={selectedBrand.name}
                    className="h-full w-full object-contain p-1"
                    onError={(e) => {
                      e.currentTarget.style.display = "none";
                      (
                        e.currentTarget.nextElementSibling as HTMLElement | null
                      )?.removeAttribute("hidden");
                    }}
                  />
                  <span hidden>
                    <Building2 className="h-6 w-6 text-mw-primary-500" />
                  </span>
                </>
              ) : (
                <Building2 className="h-6 w-6 text-mw-primary-500" />
              )}
            </div>
            <div className="min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <p className="text-base font-semibold text-mw-neutral-900 dark:text-mw-neutral-100 truncate">
                  {selectedBrand.name}
                </p>
                <span className="inline-flex items-center gap-1.5 text-xs font-medium text-mw-success-600 dark:text-mw-success-400">
                  <span
                    className={`h-1.5 w-1.5 rounded-full ${
                      selectedBrand.is_active !== false
                        ? "bg-mw-success-500"
                        : "bg-mw-neutral-400"
                    }`}
                  />
                  {selectedBrand.is_active !== false
                    ? tBrands("brand_creation.form.active")
                    : tBrands("brand_creation.form.inactive")}
                </span>
              </div>
              {selectedBrand.industry && (
                <p className="text-xs text-mw-neutral-500 dark:text-mw-neutral-400 mt-0.5 truncate">
                  {selectedBrand.industry}
                </p>
              )}
            </div>
          </div>

          {/* Categories — only when the brand has any */}
          {(() => {
            const cats = selectedBrand.iab_categories?.length
              ? selectedBrand.iab_categories.map((c) => ({
                  key: c.code,
                  label: c.name,
                }))
              : (selectedBrand.iab_category_ids ?? []).map((id) => ({
                  key: id,
                  label: id,
                }));
            return cats.length > 0 ? (
              <div className="flex flex-wrap gap-1.5 pt-3">
                {cats.map(({ key, label }) => (
                  <span
                    key={key}
                    className="text-xs px-2 py-0.5 rounded-full bg-mw-primary-50 dark:bg-mw-primary-900/30 text-mw-primary-600 dark:text-mw-primary-400 border border-mw-primary-200 dark:border-mw-primary-800"
                  >
                    {label}
                  </span>
                ))}
              </div>
            ) : null;
          })()}

          {/* Description — only when present */}
          {selectedBrand.description && (
            <p className="text-sm text-mw-neutral-600 dark:text-mw-neutral-400 leading-relaxed pt-5">
              {selectedBrand.description}
            </p>
          )}

          {/* Companies linked — only when there are any */}
          {(selectedBrand.company_ids?.length ?? 0) > 0 && (
            <div className="flex items-center justify-between text-sm pt-3 mt-3 border-t border-mw-neutral-100 dark:border-mw-neutral-700">
              <span className="text-mw-neutral-500 dark:text-mw-neutral-400">
                {tBrands("brand_creation.form.companies_linked")}
              </span>
              <span className="font-medium text-mw-neutral-900 dark:text-mw-neutral-100">
                {selectedBrand.company_ids!.length}
              </span>
            </div>
          )}

          {/* What linking does — a single caption line, not a big notice box */}
          <div className="flex items-start gap-2 text-sm text-mw-neutral-500 dark:text-mw-neutral-400 leading-relaxed pt-8">
            <Info className="h-4 w-4 mt-0.5 flex-shrink-0 text-mw-primary-500" />
            <p>{tBrands("brand_creation.form.link_notice")}</p>
          </div>
        </div>
      ) : (
        /* Create mode — full form */
        <form>
          <div className="space-y-4 pt-2">
            {/* Brand Name + suggestions */}
            <div className="space-y-1">
              <Input
                {...register("name")}
                label={tBrands("brand_creation.form.brand_name")}
                placeholder={tBrands(
                  "brand_creation.form.brand_name_placeholder",
                )}
                error={errors.name?.message}
                id="brand-name"
                autoFocus
                required
              />

              {/* Already own this brand */}
              {hasOwnBrand && (
                <p className="text-sm text-mw-error-500 mt-1">
                  {tBrands("brand_creation.form.own_brand_exists")}
                </p>
              )}

              {/* Suggestions panel */}
              {showSuggestions && (
                <div className="rounded-md border border-mw-neutral-200 dark:border-mw-neutral-600 bg-white dark:bg-mw-neutral-800 shadow-sm">
                  <p className="px-3 py-2 text-xs font-medium text-mw-neutral-500 dark:text-mw-neutral-400 border-b border-mw-neutral-100 dark:border-mw-neutral-700">
                    {hasExactMatch
                      ? tBrands("brand_creation.form.exact_brand_match_header")
                      : tBrands("brand_creation.form.similar_brands_header")}
                  </p>

                  {isSearching ? (
                    <div className="flex items-center gap-2 px-3 py-3 text-sm text-mw-neutral-500">
                      <Loader2 className="h-4 w-4 animate-spin" />
                      {tBrands("brand_creation.form.searching")}
                    </div>
                  ) : suggestions.length === 0 ? (
                    <p className="px-3 py-3 text-sm text-mw-neutral-400 dark:text-mw-neutral-500">
                      {tBrands("brand_creation.form.no_similar_brands")}
                    </p>
                  ) : (
                    <>
                      <ul className="max-h-64 overflow-y-auto divide-y divide-mw-neutral-100 dark:divide-mw-neutral-700">
                        {suggestions.map((brand) => (
                          <li key={brand.id}>
                            <button
                              type="button"
                              className="w-full flex items-center gap-3 px-3 py-2.5 text-left hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-700 transition-colors cursor-pointer"
                              onClick={() => handleSelectSuggestion(brand)}
                            >
                              <div className="h-8 w-8 flex-shrink-0 rounded overflow-hidden bg-mw-neutral-100 dark:bg-mw-neutral-700 flex items-center justify-center">
                                {brand.logo_url ? (
                                  <>
                                    <img
                                      src={brand.logo_url}
                                      alt={brand.name}
                                      className="h-full w-full object-contain"
                                      onError={(e) => {
                                        e.currentTarget.style.display = "none";
                                        (
                                          e.currentTarget
                                            .nextElementSibling as HTMLElement | null
                                        )?.removeAttribute("hidden");
                                      }}
                                    />
                                    <span hidden>
                                      <Building2 className="h-4 w-4 text-mw-neutral-400" />
                                    </span>
                                  </>
                                ) : (
                                  <Building2 className="h-4 w-4 text-mw-neutral-400" />
                                )}
                              </div>
                              <div className="flex-1 min-w-0">
                                <p className="text-sm font-medium text-mw-neutral-900 dark:text-mw-neutral-100 truncate">
                                  {brand.name}
                                </p>
                                {brand.iab_category_ids?.[0] && (
                                  <p className="text-xs text-mw-neutral-500 dark:text-mw-neutral-400">
                                    {brand.iab_category_ids[0]}
                                  </p>
                                )}
                              </div>
                            </button>
                          </li>
                        ))}
                      </ul>
                    </>
                  )}
                </div>
              )}
            </div>

            {/* IAB Category Tier 1 */}
            <div className="space-y-2">
              <Label>{tBrands("brand_creation.form.category_tier1")}</Label>
              <Controller
                name="iabCategoryId"
                control={control}
                render={({ field }) => {
                  const selected = tier1Options.find(
                    (o) => o.id === field.value,
                  );
                  return (
                    <Dropdown searchable={true}>
                      <DropdownTrigger className="w-full justify-between">
                        {selected?.name ||
                          tBrands("brand_creation.form.category_placeholder")}
                      </DropdownTrigger>
                      <DropdownContent align="left" className="w-full">
                        <DropdownSearch
                          placeholder={tBrands(
                            "brand_creation.form.category_search_placeholder",
                          )}
                        />
                        <DropdownScrollableContent key="brand-category-tier1-list">
                          {tier1Options.map((option) => (
                            <DropdownItem
                              key={option.id}
                              className="border-b border-mw-neutral-100 dark:border-mw-neutral-700 rounded-none"
                              searchableText={`${option.name} ${option.unique_id ?? ""}`}
                              onClick={() => field.onChange(option.id)}
                            >
                              <div className="flex flex-col items-start">
                                {option.name}
                                {option.unique_id && (
                                  <span className="text-xs text-mw-neutral-500 mt-1">
                                    {option.unique_id}
                                  </span>
                                )}
                              </div>
                            </DropdownItem>
                          ))}
                        </DropdownScrollableContent>
                      </DropdownContent>
                    </Dropdown>
                  );
                }}
              />
              {errors.iabCategoryId && (
                <div className="text-sm text-mw-error-500 mt-1">
                  {errors.iabCategoryId.message}
                </div>
              )}
            </div>

            {/* IAB Subcategory Tier 2 */}
            <div className="space-y-2">
              <Label>{tBrands("brand_creation.form.subcategory_tier2")}</Label>
              <Controller
                name="iabSubcategoryId"
                control={control}
                render={({ field }) => {
                  const selected = tier2Options.find(
                    (o) => o.id === field.value,
                  );
                  return (
                    <div
                      className={
                        tier2Options.length === 0
                          ? "opacity-50 pointer-events-none"
                          : undefined
                      }
                    >
                      <Dropdown searchable={true}>
                        <DropdownTrigger className="w-full justify-between">
                          {selected?.name ||
                            tBrands(
                              "brand_creation.form.subcategory_placeholder",
                            )}
                        </DropdownTrigger>
                        <DropdownContent align="left" className="w-full">
                          <DropdownSearch
                            placeholder={tBrands(
                              "brand_creation.form.subcategory_search_placeholder",
                            )}
                          />
                          <DropdownScrollableContent key="brand-category-tier2-list">
                            {tier2Options.map((option) => (
                              <DropdownItem
                                key={option.id}
                                className="border-b border-mw-neutral-100 dark:border-mw-neutral-700 rounded-none"
                                searchableText={`${option.name} ${option.unique_id ?? ""}`}
                                onClick={() => field.onChange(option.id)}
                              >
                                <div className="flex flex-col items-start">
                                  {option.name}
                                  {option.unique_id && (
                                    <span className="text-xs text-mw-neutral-500 mt-1">
                                      {option.unique_id}
                                    </span>
                                  )}
                                </div>
                              </DropdownItem>
                            ))}
                          </DropdownScrollableContent>
                        </DropdownContent>
                      </Dropdown>
                    </div>
                  );
                }}
              />
            </div>

            {/* Description */}
            <div className="space-y-2">
              <Label>{tBrands("brand_creation.form.description")}</Label>
              <textarea
                {...register("description")}
                className="w-full px-3 py-2 border border-mw-neutral-100 dark:border-mw-neutral-600 rounded-md focus:outline-none focus:ring-1 focus:ring-mw-primary-500 dark:bg-mw-neutral-800 dark:text-mw-neutral-100 resize-none"
                rows={3}
                placeholder={tBrands(
                  "brand_creation.form.description_placeholder",
                )}
              />
              {errors.description && (
                <div className="text-sm text-mw-error-500 mt-1">
                  {errors.description.message}
                </div>
              )}
            </div>

            {/* Website / Logo URL */}
            <Input
              {...register("website")}
              label={tBrands("brand_creation.form.website")}
              id="website"
              placeholder={tBrands("brand_creation.form.website_placeholder")}
              error={errors.website?.message}
            />
          </div>
        </form>
      )}
    </ModalDrawer>
  );
};

export default BrandCreationForm;
