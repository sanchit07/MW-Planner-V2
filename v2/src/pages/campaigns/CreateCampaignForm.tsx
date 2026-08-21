import { CONFIG } from "@config/index";
import ImageWithFallback from "@components/ui/ImageFallback";
import { Label } from "@components/ui/Label";
import {
  DropdownOption,
  RemoteDataFetcher,
  SearchParams,
  RemoteDropdown,
} from "@components/ui/RemoteDropdown";
import { zodResolver } from "@hookform/resolvers/zod";
import { useActiveCompany } from "@hooks/useActiveCompany";
import { useAnnounce } from "@hooks/useAnnounce";
import campaignSchema from "@schemas/campaigns/campaign.schema";
import {
  useGetChildCompaniesQuery,
  useLazyGetAgenciesQuery,
} from "@services/agency/agencySlice";
import {
  useLazyGetCompanyBrandsQuery,
  useLazyGetIabCategoriesQuery,
} from "@services/brand/brandSlice";
import {
  useLazyGetSequencerQuery,
  useCreateCampaignMutation,
  // useUpdateCampaignMutation,
  setIsCreating,
  setCreateError,
  setCampaignId,
  setCampaignData,
  setIsEditMode,
} from "@services/campaign/campaignSlice";
import { useTranslate } from "@tolgee/react";
import { createNDaysPresets } from "@utils/dashboard.utils";
import {
  CalendarDays,
  Check,
  Clapperboard,
  Image,
  Info,
  Layers,
  Megaphone,
  Monitor,
  Plus,
  Tag,
  Building2,
} from "lucide-react";
import {
  useState,
  useImperativeHandle,
  forwardRef,
  useEffect,
  useMemo,
  useCallback,
  useRef,
} from "react";
import { useForm, Controller, type Resolver } from "react-hook-form";
import { useLocation } from "react-router-dom";
import { z } from "zod";

import AgencyCreationForm from "./AgencyCreationForm";
import BrandCreationForm from "./BrandCreationForm";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "../../components/ui/card";
import { DateRangePicker } from "../../components/ui/DateRangePicker";
import {
  Dropdown,
  DropdownContent,
  DropdownItem,
  DropdownSeparator,
  DropdownTrigger,
} from "../../components/ui/Dropdown";
import { Input } from "../../components/ui/Input";
import { useAutosave } from "../../hooks/useAutosave";
import { useStepper } from "../../hooks/useStepper";
import { useAppSelector, useAppDispatch } from "../../store";
import { IamBrand, IabCategory } from "../../types/brand.types";
import {
  CampaignBrand,
  CampaignCreateRequest,
  Agency,
  ChildCompanyDetails,
} from "../../types/campaign.types";
import { generateCampaignPrefix } from "../../utils/campaignNameGenerator";
import { toAPIDateString, fromAPIDateString } from "../../utils/dateUtils";

type CampaignFormData = z.infer<typeof campaignSchema>;

interface CreateCampaignFormProps {
  onSubmit?: (formData: CampaignFormData) => void;
  initialData?: Partial<CampaignFormData>;
  onValidationChange?: (isValid: boolean) => void;
  stepId?: number; // Add stepId for Redux integration
}

export interface CreateCampaignFormRef {
  submitForm: () => Promise<boolean>;
  getFormData: () => CampaignFormData | null;
  isValid: () => boolean;
  validateStep: () => Promise<{ isValid: boolean; errors: string[] }>;
  resetForm: () => void;
}

const CreateCampaignForm = forwardRef<
  CreateCampaignFormRef,
  CreateCampaignFormProps
>(({ onSubmit, onValidationChange }, ref) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { t: tCommon } = useTranslate(["common"]);
  const dispatch = useAppDispatch();
  const location = useLocation();
  const { showError } = useAnnounce();
  // Redux stepper integration
  const { isEditMode, editCampaignId, setStepperEditMode } = useStepper();

  // Initialize autosave hook with error handling
  const { autosave, autosaveBatch } = useAutosave({
    debounceMs: 0, // Wait 1.5 seconds after user stops typing
    onSuccess: (response) => {
      dispatch(setCampaignData(response.data));
    },
    onError: (error) => {
      console.error("Autosave failed:", error);
    },
  });

  // Handle autosave on blur for form fields (only in edit mode)
  const handleFieldBlur = useCallback(
    async (fieldName: string, value: unknown, canSkipValueCheck = false) => {
      // Only autosave when in edit mode
      if (!isEditMode) {
        return;
      }

      try {
        // Handle special field mappings for API compatibility
        let apiFieldName = fieldName;
        let apiValue = value;
        // Map form field names to API field names
        switch (fieldName) {
          case "campaignName":
            apiFieldName = "name";
            break;
          case "campaignDates":
            // Handle date range separately
            if (
              value &&
              typeof value === "object" &&
              "from" in value &&
              "to" in value
            ) {
              const dateRange = value as { from: Date | null; to: Date | null };

              // Only autosave if both dates are selected
              if (dateRange.from && dateRange.to) {
                await autosaveBatch({
                  startDate: toAPIDateString(dateRange.from),
                  endDate: toAPIDateString(dateRange.to),
                });
              }
              return;
            }
            break;
          case "brand":
            apiFieldName = "brand";
            apiValue = value
              ? {
                  id: value,
                  name: selectedBrandLabelRef.current,
                  categories: selectedBrandCategoriesRef.current,
                }
              : undefined;
            break;
          case "clientType":
            apiFieldName = "clientType";
            apiValue =
              value === "Direct Advertiser" ? "DIRECT_ADVERTISER" : "AGENCY";
            break;
          case "agency":
            apiFieldName = "agency";
            apiValue = value
              ? { id: value, name: selectedAgencyLabelRef.current }
              : undefined;
            break;
          case "mediaChannels":
            apiFieldName = "mediaChannels";
            break;
          case "dsp":
            apiFieldName = "dsp";
            // "None" isn't a real DSP selection - autosave it as null rather
            // than the literal string "NONE".
            apiValue = value === "NONE" ? null : value;
            break;
          case "seatId":
            apiFieldName = "seatId";
            break;
          default:
            // Use field name as-is for other fields like description
            break;
        }

        // Skip empty values
        if (!canSkipValueCheck) {
          if (apiValue === undefined || apiValue === null || apiValue === "") {
            console.log(
              `Autosave: Skipping empty value for field ${fieldName}`,
            );
            return;
          }
        }

        await autosave(apiFieldName, apiValue, canSkipValueCheck);
      } catch (error) {
        console.error(`Failed to autosave ${fieldName}:`, error);
      }
    },
    [autosave, autosaveBatch, isEditMode],
  );

  // Track form initialization state to prevent clearing agency during load
  const isInitializedRef = useRef(false);
  const previousClientTypeRef = useRef<string>("");
  const previousEditCampaignIdRef = useRef<string | null>(null);

  // Capture selected brand/agency labels at selection time so they're
  // always available for the creation response merge (Fix 4), regardless
  // of whether the agencies/brands arrays are still populated.
  const selectedBrandLabelRef = useRef<string | undefined>(undefined);
  const selectedBrandCategoriesRef =
    useRef<CampaignBrand["categories"]>(undefined);
  const selectedAgencyLabelRef = useRef<string | undefined>(undefined);

  // Set default date range: today to 30 days from today
  const defaultData: CampaignFormData = useMemo(() => {
    const today = new Date();
    const thirtyDaysFromToday = new Date();
    thirtyDaysFromToday.setDate(today.getDate() + 30);

    return {
      campaignName: "",
      campaignDates: {
        from: today,
        to: thirtyDaysFromToday,
      },
      brand: "",
      clientType: "",
      agency: "",
      mediaChannels: ["DIGITAL_OOH"],
      dsp: "NONE",
      seatId: "",
      status: "DRAFT",
      description: "",
      currency: "",
      budget: undefined,
    };
  }, []);

  const {
    register,
    handleSubmit,
    formState: { errors, isValid: formIsValid },
    watch,
    control,
    getValues,
    setValue,
    reset,
  } = useForm<CampaignFormData>({
    resolver: zodResolver(
      campaignSchema,
    ) as unknown as Resolver<CampaignFormData>,
    defaultValues: {
      ...defaultData,
    },
  });

  // Get campaign state
  const campaignState = useAppSelector((state) => state.campaign);
  const profile = useAppSelector((s) => s.profile.profile);
  const {
    isAgency,
    companyId: activeCompanyId,
    companyName: activeCompanyName,
  } = useActiveCompany();

  // Auto-populate seatId from user profile on create mode
  useEffect(() => {
    if (!isEditMode && profile?.current_company?.seat_id) {
      setValue("seatId", String(profile.current_company.seat_id));
    }
  }, [profile, isEditMode, setValue]);

  // Notify parent of validation changes
  useEffect(() => {
    if (onValidationChange) {
      onValidationChange(formIsValid);
    }
  }, [formIsValid, onValidationChange]);

  // Watch only the fields we need for conditional rendering
  const watchedClientType = watch("clientType");
  const watchedBrand = watch("brand");

  const campaignDatePresets = useMemo(
    () => createNDaysPresets(tCommon),
    [tCommon],
  );

  // Remove old agency-related code since we're using RemoteDropdown
  const [isBrandModalOpen, setIsBrandModalOpen] = useState(false);
  const [isAgencyModalOpen, setIsAgencyModalOpen] = useState(false);
  const [brandDropdownKey, setBrandDropdownKey] = useState(0);
  const [iabCategories, setIabCategories] = useState<IabCategory[]>([]);
  const [getSequencer] = useLazyGetSequencerQuery();
  const [isLoadingCampaignName, setIsLoadingCampaignName] = useState(false);

  const [createCampaign] = useCreateCampaignMutation();
  // const [updateCampaign] = useUpdateCampaignMutation();
  const [getAgencies] = useLazyGetAgenciesQuery();
  const [getBrands] = useLazyGetCompanyBrandsQuery();
  const [getIabCategories] = useLazyGetIabCategoriesQuery();
  const { showSuccess } = useAnnounce();
  const [brands, setBrands] = useState<BrandDropdownOption[]>([]);
  // Ref cache of all brands fetched via API pagination. Used to serve "return
  // from cache" responses (after search clear or revisiting a page) without
  // re-hitting the backend. Kept in sync with brands state but NOT in the
  // brandFetcher dependency array to avoid recreating the fetcher on every append.
  const loadedBrandsRef = useRef<BrandDropdownOption[]>([]);
  const loadedBrandsTotalRef = useRef<number>(0);
  const [agencies, setAgencies] = useState<AgencyDropdownOption[]>([]);

  const handleBrandSuccess = useCallback(
    (brandId: string, brandName?: string) => {
      loadedBrandsRef.current = [];
      loadedBrandsTotalRef.current = 0;
      setBrands([]);
      setBrandDropdownKey((k) => k + 1);
      setIsBrandModalOpen(false);
      setValue("brand", brandId);
      selectedBrandLabelRef.current = brandName;
      handleFieldBlur("brand", brandId);
      if (campaignState.campaignData) {
        dispatch(
          setCampaignData({
            ...campaignState.campaignData,
            brand: { id: brandId, name: brandName ?? "" },
          }),
        );
      }
    },
    [setValue, campaignState.campaignData, dispatch, handleFieldBlur],
  );

  const handleAgencySuccess = useCallback(
    (agencyId: string, agencyName?: string) => {
      setAgencies([]);
      setIsAgencyModalOpen(false);
      setValue("agency", agencyId);
      selectedAgencyLabelRef.current = agencyName;
      handleFieldBlur("agency", agencyId);
      if (campaignState.campaignData) {
        dispatch(
          setCampaignData({
            ...campaignState.campaignData,
            agency: { id: agencyId, name: agencyName ?? "" },
          }),
        );
      }
    },
    [setValue, campaignState.campaignData, dispatch, handleFieldBlur],
  );

  // Define brand dropdown option interface
  interface BrandDropdownOption extends DropdownOption {
    id: string;
    label: string;
    value: string;
    description?: string;
    category?: string;
    iabId?: string;
    logoUrl?: string;
    categories?: CampaignBrand["categories"];
  }

  // Define agency dropdown option interface
  interface AgencyDropdownOption extends DropdownOption {
    id: string;
    label: string;
    value: string;
    description?: string;
    country?: string;
  }

  // Clear brand options when selected company/agency changes
  useEffect(() => {
    setBrands([]);
    loadedBrandsRef.current = [];
    loadedBrandsTotalRef.current = 0;
    // clear selected brand value when switching companies
    setValue("brand", "");
    selectedBrandLabelRef.current = undefined;
  }, [profile?.activeCompanyId, profile?.current_company?.id, setValue]);

  // Reset agency options when selected company/agency changes. For agency
  // users, the field is locked to their own (possibly impersonated) company
  // rather than left for manual selection — see agency dropdown render below.
  useEffect(() => {
    setAgencies([]);
    if (isAgency && activeCompanyId) {
      setValue("agency", activeCompanyId);
      selectedAgencyLabelRef.current = activeCompanyName;
    } else {
      setValue("agency", "");
      selectedAgencyLabelRef.current = undefined;
    }
  }, [
    profile?.activeCompanyId,
    profile?.current_company?.id,
    isAgency,
    activeCompanyId,
    activeCompanyName,
    setValue,
  ]);

  // Agency logins always create campaigns for themselves as the agency —
  // Client Type is locked to "Agency" rather than left for manual selection,
  // matching the agency field's own lock above.
  useEffect(() => {
    if (isAgency) {
      setValue("clientType", "Agency");
    }
  }, [isAgency, setValue]);

  // Brand data fetcher for RemoteDropdown.
  // No-search path: paginated API call (page/limit forwarded to backend).
  // Search path: filter client-side from the brands already loaded into state.
  // Brand data fetcher for RemoteDropdown — sourced from IAM /metadata/brands
  const brandFetcher: RemoteDataFetcher<BrandDropdownOption> = useCallback(
    async (params: SearchParams) => {
      try {
        const companyId =
          profile?.activeCompanyId || profile?.current_company?.id || "";
        const searchTerm = (params.search as string | undefined)?.toLowerCase();
        const page = params.page ?? 0;
        const size = params.size ?? 100;

        const toOption = (brand: IamBrand): BrandDropdownOption => ({
          id: brand.id,
          label: brand.name,
          value: brand.id,
          description: brand.iab_categories?.[0]?.name,
          category: brand.iab_categories?.[0]?.name,
          iabId: brand.iab_categories?.[0]?.unique_id,
          logoUrl: brand.logo_url,
          categories: brand.iab_categories?.map((c) => ({
            id: c.id,
            name: c.name,
            fullPath: c.full_path,
            tier: c.tier,
          })),
        });

        if (searchTerm) {
          // Filter the ref cache client-side — no API call, no stale-closure risk
          const filtered = loadedBrandsRef.current.filter((b) =>
            b.label.toLowerCase().includes(searchTerm),
          );
          const start = page * size;
          const slice = filtered.slice(start, start + size);
          const totalPages = size > 0 ? Math.ceil(filtered.length / size) : 1;

          return {
            content: slice,
            totalElements: filtered.length,
            totalPages,
            size,
            number: page,
            first: page === 0,
            last: slice.length === 0 || page >= totalPages - 1,
            empty: filtered.length === 0,
            numberOfElements: slice.length,
          };
        }

        // No search: serve from ref cache if the requested page is already loaded
        const start = page * size;
        const total = loadedBrandsTotalRef.current;
        const allFetched = total > 0 && loadedBrandsRef.current.length >= total;

        if (loadedBrandsRef.current.length >= start + size || allFetched) {
          const slice = loadedBrandsRef.current.slice(start, start + size);
          const totalPages = size > 0 ? Math.ceil(total / size) : 1;

          return {
            content: slice,
            totalElements: total,
            totalPages,
            size,
            number: page,
            first: page === 0,
            last: slice.length === 0 || page >= totalPages - 1,
            empty: slice.length === 0,
            numberOfElements: slice.length,
          };
        }

        // Page not in cache yet — fetch from API (page is 0-based here, API is 1-based)
        if (!companyId) {
          return {
            content: [],
            totalElements: 0,
            totalPages: 0,
            size,
            number: page,
            first: true,
            last: true,
            empty: true,
            numberOfElements: 0,
          };
        }

        const resp = await getBrands({
          companyId,
          params: { page: page + 1, limit: size },
        }).unwrap();

        const brandsData: IamBrand[] = resp?.data?.brands || [];
        const options = brandsData.map(toOption);

        // Append new items to ref cache (dedup by id)
        const existingIds = new Set(loadedBrandsRef.current.map((b) => b.id));
        const newOnes = options.filter((o) => !existingIds.has(o.id as string));
        if (newOnes.length > 0) {
          loadedBrandsRef.current = [...loadedBrandsRef.current, ...newOnes];
          loadedBrandsTotalRef.current =
            resp?.data?.total ?? loadedBrandsRef.current.length;
          setBrands([...loadedBrandsRef.current]);
        }

        const totalElements = loadedBrandsTotalRef.current;
        const pageSize = resp?.data?.limit ?? size;
        const totalPages =
          pageSize > 0 ? Math.ceil(totalElements / pageSize) : 1;

        return {
          content: options,
          totalElements,
          totalPages,
          size: pageSize,
          number: page,
          first: page === 0,
          last: options.length === 0 || page >= totalPages - 1,
          empty: options.length === 0,
          numberOfElements: options.length,
        };
      } catch (error) {
        console.error("Error fetching brands:", error);
        throw error;
      }
    },
    [getBrands, profile?.activeCompanyId, profile?.current_company?.id],
  );

  const { data: childCompaniesData } = useGetChildCompaniesQuery({
    id: activeCompanyId,
  });
  const hasChildAgencies = Boolean(
    childCompaniesData?.children?.some(
      (child: ChildCompanyDetails) =>
        child.company?.company_type?.code === "AGENCY",
    ),
  );

  // Agency data fetcher for RemoteDropdown
  const agencyFetcher: RemoteDataFetcher<AgencyDropdownOption> = useCallback(
    async (params: SearchParams) => {
      let options: AgencyDropdownOption[] = [];

      // Agency users are locked to their own (possibly impersonated) company —
      // planning for a child agency is done by impersonating it first, not by
      // picking it from this dropdown (see the impersonation note in the JSX).
      if (isAgency && activeCompanyId) {
        options.push({
          id: activeCompanyId,
          label: activeCompanyName,
          value: activeCompanyId,
        });
      } else {
        // Default: fetch agencies as before
        const queryParams: Record<string, unknown> = {
          page: params.page || 0,
          size: params.size || 10,
          sortBy: "name",
          sortDir: "ASC",
          company_id:
            profile?.activeCompanyId || profile?.current_company?.id || "",
        };
        if (params.search) queryParams.search = params.search as string;
        const response = await getAgencies(queryParams).unwrap();
        if (!response.data) throw new Error("No data received from API");
        options = response.data.map((agency: Agency) => ({
          id: agency.id,
          label: agency.name,
          value: agency.id,
        }));
      }

      // Optionally update state if needed
      setAgencies(options);

      return {
        content: options,
        totalElements: options.length,
        totalPages: 1,
        size: options.length,
        number: 0,
        first: true,
        last: true,
        empty: options.length === 0,
        numberOfElements: options.length,
      };
    },
    [
      getAgencies,
      isAgency,
      activeCompanyId,
      activeCompanyName,
      profile?.activeCompanyId,
      profile?.current_company?.id,
    ],
  );

  // Populate form from Redux campaign data (loaded by CampaignWrapper)
  useEffect(() => {
    // Reset initialization when campaign ID changes (edit different campaign or new campaign)
    if (
      previousEditCampaignIdRef.current !== null &&
      previousEditCampaignIdRef.current !== editCampaignId
    ) {
      isInitializedRef.current = false;
      previousClientTypeRef.current = "";
    }

    if (isEditMode && editCampaignId && campaignState.campaignData) {
      const campaignData = campaignState.campaignData;

      if (!isInitializedRef.current && campaignData) {
        // Transform API data to form format
        const formData = {
          campaignName: campaignData.name,
          campaignDates: {
            from: fromAPIDateString(campaignData.startDate),
            to: fromAPIDateString(campaignData.endDate),
          },
          brand: campaignData.brand?.id || "",
          clientType:
            campaignData.clientType === "DIRECT_ADVERTISER"
              ? "Direct Advertiser"
              : "Agency",
          agency: campaignData.agency?.id || "",
          mediaChannels: campaignData.mediaChannels?.length
            ? campaignData.mediaChannels
            : ["DIGITAL_OOH"],
          dsp: campaignData.dsp || "NONE",
          seatId: String(
            (campaignData as unknown as Record<string, unknown>).seatId ??
              profile?.current_company?.seat_id ??
              "",
          ),
          status: campaignData.status,
          description: campaignData.description || "",
        };

        // Set form values
        Object.entries(formData).forEach(([key, value]) => {
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          setValue(key as keyof CampaignFormData, value as any);
        });

        // Store the initial clientType to track manual changes
        previousClientTypeRef.current = formData.clientType;
        isInitializedRef.current = true;
      }
    } else if (!isEditMode) {
      // Reset flags when in create mode
      isInitializedRef.current = false;
      previousClientTypeRef.current = "";
    }

    // Update the previous edit campaign ID
    previousEditCampaignIdRef.current = editCampaignId;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isEditMode, editCampaignId, campaignState.campaignData, setValue]);

  useEffect(() => {
    const isEditing =
      location.pathname.includes("/edit") || campaignState.campaignId !== null;
    // Only fetch sequencer for new campaigns, not when editing
    if (!isEditing) {
      const fetchSequencer = async () => {
        setIsLoadingCampaignName(true);
        try {
          const prefix = generateCampaignPrefix("Plan");
          const result = await getSequencer(prefix).unwrap();

          if (result.success && result.data) {
            const formattedNumber = result.data.toString().padStart(3, "0");
            const campaignName = `${prefix}${formattedNumber}`;

            setValue("campaignName", campaignName);
          }
        } catch (error) {
          console.error("Error fetching sequencer:", error);
          const prefix = generateCampaignPrefix("Plan");
          const fallbackName = `${prefix}001`;
          setValue("campaignName", fallbackName);
        } finally {
          setIsLoadingCampaignName(false);
        }
      };

      fetchSequencer();
    }
  }, [location.pathname, getSequencer, setValue, campaignState.campaignId]);

  // Only clear agency when user manually changes clientType from "Agency" to something else
  useEffect(() => {
    if (
      isInitializedRef.current &&
      previousClientTypeRef.current === "Agency" &&
      watchedClientType !== "Agency" &&
      watchedClientType !== ""
    ) {
      setValue("agency", "");
    }
    if (watchedClientType !== "") {
      previousClientTypeRef.current = watchedClientType;
    }
  }, [watchedClientType, setValue]);

  // Form submission handler
  const onFormSubmit = useCallback(
    async (data: CampaignFormData) => {
      const transformFormDataToApiRequest = (
        formData: CampaignFormData,
      ): CampaignCreateRequest => {
        return {
          name: formData.campaignName,
          description: formData.description || "",
          status: formData.status || "DRAFT",
          startDate: formData.campaignDates.from
            ? toAPIDateString(formData.campaignDates.from)
            : "",
          endDate: formData.campaignDates.to
            ? toAPIDateString(formData.campaignDates.to)
            : "",
          brand: formData.brand
            ? {
                id: formData.brand,
                name: selectedBrandLabelRef.current ?? "",
                categories: selectedBrandCategoriesRef.current,
              }
            : undefined,
          clientType:
            formData.clientType === "Direct Advertiser"
              ? "DIRECT_ADVERTISER"
              : "AGENCY",
          agency: formData.agency
            ? {
                id: formData.agency,
                name: selectedAgencyLabelRef.current ?? "",
              }
            : undefined,
          budget: formData.budget || undefined,
          currency: formData.currency || undefined,
          currentCompanyId: profile?.current_company?.id ?? undefined,
          currentCompanyName: profile?.current_company?.name ?? undefined,
          companyId:
            profile?.activeCompanyId ||
            profile?.current_company?.id ||
            undefined,
          mediaChannels: formData.mediaChannels?.length
            ? formData.mediaChannels
            : ["DIGITAL_OOH"],
          // "None" isn't a real DSP selection - save it as null rather than
          // the literal string "NONE".
          dsp: formData.dsp && formData.dsp !== "NONE" ? formData.dsp : null,
          seatId: formData.seatId || undefined,
        };
      };

      try {
        dispatch(setIsCreating(true));
        dispatch(setCreateError(null));

        const apiRequest = transformFormDataToApiRequest(data);
        if (isEditMode && editCampaignId) {
          // // Update existing campaign
          // console.log("Updating campaign with ID:", editCampaignId);
          // const result = await updateCampaign({
          //   campaignId: editCampaignId,
          //   campaignData: apiRequest,
          // }).unwrap();
          // if (result.success && result.data) {
          //   dispatch(setCampaignData(result.data));
          //   console.log("Campaign updated successfully:", result.data);
          //   showSuccess("Campaign updated successfully!");
          //   if (onSubmit) {
          //     onSubmit(data);
          //   }
          // } else {
          //   throw new Error("Campaign update failed: Invalid response");
          // }
          // if (onSubmit) {
          //   onSubmit(data);
          // }
        } else {
          // Create new campaign
          const result = await createCampaign(apiRequest).unwrap();

          if (result.success && result.data) {
            dispatch(setCampaignId(result.data.id));
            // Merge brand/agency names from the labels captured at selection time.
            // The backend may return "unknown"/"Unknown" as a placeholder — treat it as missing.
            const isValidName = (name: string | undefined | null): boolean =>
              !!name && name.toLowerCase() !== "unknown";
            const resolvedBrandName = isValidName(result.data.brand?.name)
              ? result.data.brand?.name
              : selectedBrandLabelRef.current;
            const resolvedAgencyName = isValidName(result.data.agency?.name)
              ? result.data.agency?.name
              : selectedAgencyLabelRef.current;
            dispatch(
              setCampaignData({
                ...result.data,
                brand: result.data.brand
                  ? {
                      ...result.data.brand,
                      name: resolvedBrandName ?? result.data.brand.name,
                    }
                  : result.data.brand,
                agency: result.data.agency
                  ? {
                      ...result.data.agency,
                      name: resolvedAgencyName ?? result.data.agency.name,
                    }
                  : result.data.agency,
              }),
            );

            // Set edit mode for subsequent steps
            setStepperEditMode(true, result.data.id);
            dispatch(setIsEditMode(true));
            showSuccess(tCampaigns("campaignCreatedSuccess"));
            if (onSubmit) {
              onSubmit(data);
            }
          } else {
            throw new Error("Campaign creation failed: Invalid response");
          }
        }
      } catch (error) {
        dispatch(
          setCreateError(
            error?.toString() ||
              (isEditMode
                ? tCampaigns("campaignUpdateFailed")
                : tCampaigns("campaignCreateFailed")),
          ),
        );
        // Re-throw the error so submitForm can catch it
        throw error;
      } finally {
        dispatch(setIsCreating(false));
      }
    },
    [
      dispatch,
      createCampaign,
      setStepperEditMode,
      onSubmit,
      isEditMode,
      editCampaignId,
      showSuccess,
      profile,
    ],
  );

  // Get current brand details for dynamic content
  const getCurrentBrand = (): BrandDropdownOption | undefined => {
    return brands.find((brand) => brand.id === watchedBrand);
  };

  // Find brand option by ID for form initialization
  const getBrandOptionById = useCallback(
    (brandId: string | null | undefined): BrandDropdownOption | null => {
      if (!brandId) return null;
      const found = brands.find((brand) => brand.id === brandId);
      if (found) return found;
      const brandName = campaignState.campaignData?.brand?.name;
      if (brandName) {
        return {
          id: brandId,
          label: brandName,
          value: brandId,
        } as BrandDropdownOption;
      }
      return null;
    },
    [brands, campaignState.campaignData],
  );

  // Find agency option by ID for form initialization
  const getAgencyOptionById = useCallback(
    (agencyId: string | null | undefined): AgencyDropdownOption | null => {
      if (!agencyId) return null;
      const found = agencies.find((agency) => agency.id === agencyId);
      if (found) return found;
      // Falls back to the campaign's stored agency name (edit mode), then
      // to the locked-in agency label (agency logins, new campaign) — the
      // latter is set as soon as the company is known, before the `agencies`
      // fetcher has had a chance to run.
      const agencyName =
        campaignState.campaignData?.agency?.name ??
        selectedAgencyLabelRef.current;
      if (agencyName && agencyName.toLowerCase() !== "unknown") {
        return {
          id: agencyId,
          label: agencyName,
          value: agencyId,
        } as AgencyDropdownOption;
      }
      return null;
    },
    [agencies, campaignState.campaignData],
  );

  const handleCreateBrand = async () => {
    try {
      // Fetch IAB categories when opening the brand creation form
      const result = await getIabCategories().unwrap();
      if (result.success && result.data) {
        setIabCategories(result.data);
      }
    } catch (error) {
      console.error("Failed to fetch IAB categories:", error);
      // Still open the modal even if categories fail to load
      // The form will fall back to an empty state
    }
    setIsBrandModalOpen(true);
  };

  const handleCreateAgency = () => {
    setIsAgencyModalOpen(true);
  };

  // Expose form methods to parent component
  useImperativeHandle(
    ref,
    () => ({
      submitForm: async () => {
        return new Promise((resolve) => {
          handleSubmit(
            async (data) => {
              try {
                await onFormSubmit(data);
                resolve(true);
              } catch {
                resolve(false);
              }
            },
            (errors) => {
              console.warn("Form validation failed:", errors);
              resolve(false);
            },
          )();
        });
      },
      getFormData: () => {
        const data = getValues();
        const isFormValid = Object.keys(errors).length === 0;
        return isFormValid ? data : null;
      },
      isValid: () => {
        return formIsValid && Object.keys(errors).length === 0;
      },
      validateStep: async () => {
        // Trigger form validation and return result
        return new Promise((resolve) => {
          handleSubmit(
            () => {
              // Form is valid
              resolve({ isValid: true, errors: [] });
            },
            (formErrors) => {
              // Form has validation errors
              const errorMessages = Object.values(formErrors).map(
                (error) =>
                  error?.message || tCampaigns("commentsTab.validationError"),
              );
              showError(errorMessages.join(", "));
              resolve({ isValid: false, errors: errorMessages });
            },
          )();
        });
      },
      resetForm: () => {
        reset(defaultData);
      },
    }),
    [
      handleSubmit,
      onFormSubmit,
      getValues,
      errors,
      formIsValid,
      reset,
      defaultData,
      showError,
    ],
  );

  return (
    <div id="create-campaign-form" className="flex gap-6">
      {/* Left Side - Form */}
      <div id="create-campaign-form-left" className="flex-1">
        <Card id="create-campaign-form-card">
          <CardHeader id="create-campaign-form-header" className="p-4">
            <div className="flex items-center gap-2 border-b border-mw-neutral-100 pb-2">
              <div className="w-10 h-10 bg-mw-warning-100 rounded-lg flex items-center justify-center">
                <Megaphone className="w-5 h-5 text-mw-warning-500" />
              </div>
              <div className="flex-1 inline-flex flex-col justify-start items-start gap-1">
                <div className="self-stretch inline-flex justify-start items-start gap-2">
                  <CardTitle className="text-sm font-medium leading-none">
                    {tCampaigns("create_campaign.steps.campaign_details")}
                  </CardTitle>
                </div>
                <p className="text-sm font-normal text-mw-neutral-500 leading-4">
                  {tCampaigns(
                    "create_campaign.steps.campaign_setup_description",
                  )}
                </p>
              </div>
            </div>
          </CardHeader>
          <CardContent id="create-campaign-form-content">
            <div className="space-y-3">
              <div className="grid grid-cols-2 gap-6">
                <div>
                  <Input
                    {...register("campaignName")}
                    id="create-campaign-name-input"
                    label={tCampaigns("create_campaign.form.campaign_name")}
                    placeholder={
                      isLoadingCampaignName
                        ? tCampaigns("create_campaign.form.generating")
                        : tCampaigns("create_campaign.form.enter_campaign_name")
                    }
                    error={errors.campaignName?.message}
                    required
                    disabled={isLoadingCampaignName}
                    onBlur={(e) =>
                      handleFieldBlur("campaignName", e.target.value)
                    }
                  />
                </div>
                <div>
                  <Controller
                    name="campaignDates"
                    control={control}
                    render={({ field }) => (
                      <div className="space-y-2">
                        <div className="flex items-center justify-between">
                          <Label htmlFor="create-campaign-dates-picker-trigger">
                            <span className="inline-flex items-center gap-1">
                              <CalendarDays className="h-4 w-4" />
                              {tCampaigns(
                                "create_campaign.form.campaign_dates",
                              )}
                            </span>
                          </Label>
                          {field.value?.from && field.value?.to && (
                            <span className="inline-flex items-center rounded-full bg-mw-primary-50 px-2.5 py-0.5 text-xs font-medium text-mw-primary-600 dark:bg-mw-primary-900/30 dark:text-mw-primary-400">
                              {Math.round(
                                (field.value.to.getTime() -
                                  field.value.from.getTime()) /
                                  (1000 * 60 * 60 * 24),
                              ) + 1}{" "}
                              {tCampaigns("create_campaign.form.days_duration")}
                            </span>
                          )}
                        </div>
                        <DateRangePicker
                          id="create-campaign-dates-picker"
                          value={field.value}
                          onChange={(value) => {
                            field.onChange(value);
                            // Trigger autosave after a short delay to simulate blur behavior
                            setTimeout(
                              () => handleFieldBlur("campaignDates", value),
                              100,
                            );
                          }}
                          placeholder={tCampaigns(
                            "create_campaign.form.select_dates",
                          )}
                          clearable={false}
                          minDate={new Date(new Date().setHours(0, 0, 0, 0))}
                          maxDate={
                            new Date(
                              new Date().setFullYear(
                                new Date().getFullYear() + 1,
                              ),
                            )
                          }
                          error={errors.campaignDates?.message}
                          numberOfMonths={2}
                          required
                          presets={campaignDatePresets}
                        />
                      </div>
                    )}
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-6">
                <div>
                  <Label className="mb-2">
                    <span className="inline-flex items-center gap-1">
                      <Tag className="h-4 w-4" />
                      {tCampaigns("create_campaign.form.brand_optional")}
                    </span>
                  </Label>
                  <Controller
                    name="brand"
                    control={control}
                    render={({ field }) => (
                      <RemoteDropdown<BrandDropdownOption>
                        key={brandDropdownKey}
                        id="create-campaign-brand-dropdown"
                        fetcher={brandFetcher}
                        value={getBrandOptionById(field.value) || null}
                        onClear={() => {
                          field.onChange(null);
                          handleFieldBlur("brand", "", true);
                        }}
                        clearable={true}
                        onSelectionChange={(selected) => {
                          if (Array.isArray(selected)) {
                            const option =
                              selected.length > 0 ? selected[0] : null;
                            const value = option?.id ?? "";
                            const label = option?.label;
                            const categories = option?.categories;
                            field.onChange(value);
                            selectedBrandLabelRef.current = label;
                            selectedBrandCategoriesRef.current = categories;
                            handleFieldBlur("brand", value);
                            if (campaignState.campaignData) {
                              dispatch(
                                setCampaignData({
                                  ...campaignState.campaignData,
                                  brand: value
                                    ? {
                                        id: value,
                                        name: label ?? "",
                                        categories,
                                      }
                                    : undefined,
                                }),
                              );
                            }
                          } else {
                            const value = selected ? selected.id : "";
                            const label = selected ? selected.label : undefined;
                            const categories = selected?.categories;
                            field.onChange(value);
                            selectedBrandLabelRef.current = label;
                            selectedBrandCategoriesRef.current = categories;
                            handleFieldBlur("brand", value);
                            if (campaignState.campaignData) {
                              dispatch(
                                setCampaignData({
                                  ...campaignState.campaignData,
                                  brand: value
                                    ? {
                                        id: value,
                                        name: label ?? "",
                                        categories,
                                      }
                                    : undefined,
                                }),
                              );
                            }
                          }
                        }}
                        placeholder={tCampaigns(
                          "create_campaign.form.enter_brand_name",
                        )}
                        searchable={true}
                        searchPlaceholder={tCampaigns(
                          "brand_dropdown.search_brands",
                        )}
                        className="w-full"
                        maxHeight="250px"
                        pageSize={100}
                        renderOption={(option, isSelected) => (
                          <div
                            className={`flex flex-col items-start gap-1 px-3 py-1 ${isSelected ? "bg-mw-primary-50" : ""}`}
                          >
                            <p className="font-medium text-sm text-mw-neutral-500">
                              {option.label}
                            </p>
                            {option.description && (
                              <p className="text-xs text-mw-neutral-400">
                                {option.description}
                              </p>
                            )}
                            <DropdownSeparator />
                          </div>
                        )}
                        renderEmpty={() => (
                          <div className="p-4 text-center">
                            <p className="text-sm text-mw-neutral-500 mb-3">
                              {tCampaigns("brand_dropdown.no_brands")}
                            </p>
                          </div>
                        )}
                        renderFooter={(setIsOpen) => (
                          <div>
                            <button
                              id="create-brand-btn"
                              type="button"
                              onClick={() => {
                                handleCreateBrand();
                                setIsOpen(false);
                              }}
                              className="w-full flex items-start justify-start gap-2 px-3 py-2 text-mw-primary-500 hover:text-mw-primary-600 hover:bg-mw-primary-50 text-sm"
                            >
                              <Plus className="size-4" />
                              {tCampaigns("brand_dropdown.create_brand")}
                            </button>
                          </div>
                        )}
                      />
                    )}
                  />
                  {errors.brand && (
                    <div
                      id="create-campaign-brand-error"
                      className="text-sm text-mw-error-500 mt-1"
                    >
                      {errors.brand.message}
                    </div>
                  )}
                </div>
                <div>
                  <Label className="mb-2">
                    <span className="inline-flex items-center gap-1">
                      <Layers className="h-4 w-4" />
                      {tCampaigns("create_campaign.form.media_channels")}
                    </span>
                  </Label>
                  <Controller
                    name="mediaChannels"
                    control={control}
                    render={({ field }) => {
                      const MEDIA_CHANNELS = [
                        {
                          value: "DIGITAL_OOH",
                          icon: <Monitor className="w-4 h-4 shrink-0" />,
                        },
                        {
                          value: "CLASSIC_OOH",
                          icon: <Image className="w-4 h-4 shrink-0" />,
                        },
                        {
                          value: "CINEMA",
                          icon: <Clapperboard className="w-4 h-4 shrink-0" />,
                        },
                      ];
                      const selectedValues: string[] =
                        Array.isArray(field.value) && field.value.length > 0
                          ? field.value
                          : ["DIGITAL_OOH"];
                      const selectedLabels = MEDIA_CHANNELS.filter((c) =>
                        selectedValues.includes(c.value),
                      )
                        .map((c) =>
                          tCampaigns(
                            `create_campaign.media_channels.${c.value}.label`,
                          ),
                        )
                        .join(", ");
                      return (
                        <Dropdown name="media-channel">
                          <DropdownTrigger className="w-full justify-between">
                            <span className="truncate text-mw-neutral-700">
                              {selectedLabels}
                            </span>
                          </DropdownTrigger>
                          <DropdownContent align="left" className="w-full">
                            {MEDIA_CHANNELS.map((ch) => {
                              const isSelected = selectedValues.includes(
                                ch.value,
                              );
                              const isLastSelected =
                                isSelected && selectedValues.length === 1;
                              return (
                                <DropdownItem
                                  key={ch.value}
                                  value={ch.value}
                                  className="!px-3 !py-2 !items-start text-left"
                                  closeOnSelect={false}
                                  onClick={() => {
                                    let next: string[];
                                    if (isSelected) {
                                      if (isLastSelected) return;
                                      next = selectedValues.filter(
                                        (v) => v !== ch.value,
                                      );
                                    } else {
                                      next = [...selectedValues, ch.value];
                                    }
                                    field.onChange(next);
                                    handleFieldBlur("mediaChannels", next);
                                  }}
                                >
                                  <div
                                    className={`mt-0.5 w-4 h-4 rounded shrink-0 border flex items-center justify-center ${
                                      isSelected
                                        ? "bg-mw-primary-500 border-mw-primary-500"
                                        : "border-mw-neutral-300"
                                    }`}
                                  >
                                    {isSelected && (
                                      <Check className="w-3 h-3 text-white" />
                                    )}
                                  </div>
                                  <div className="text-mw-neutral-500 shrink-0">
                                    {ch.icon}
                                  </div>
                                  <div className="flex flex-col gap-0.5 flex-1 min-w-0 text-left">
                                    <span className="text-sm font-medium text-mw-neutral-800">
                                      {tCampaigns(
                                        `create_campaign.media_channels.${ch.value}.label`,
                                      )}
                                    </span>
                                    <span className="text-xs text-mw-neutral-500 leading-tight whitespace-normal">
                                      {tCampaigns(
                                        `create_campaign.media_channels.${ch.value}.description`,
                                      )}
                                    </span>
                                  </div>
                                  {isSelected && (
                                    <Check className="w-4 h-4 text-mw-primary-500 shrink-0" />
                                  )}
                                </DropdownItem>
                              );
                            })}
                          </DropdownContent>
                        </Dropdown>
                      );
                    }}
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-6">
                <div>
                  <Label className="mb-2">
                    {tCampaigns("create_campaign.form.client_type")}
                  </Label>
                  <Controller
                    name="clientType"
                    control={control}
                    render={({ field }) => (
                      <div
                        className={
                          isAgency ? "pointer-events-none opacity-60" : ""
                        }
                      >
                        <Dropdown name="client-type">
                          <DropdownTrigger className="w-full justify-between">
                            {field.value
                              ? field.value === "Agency"
                                ? tCampaigns(
                                    `create_campaign.client_types.agency`,
                                  )
                                : tCampaigns(
                                    `create_campaign.client_types.direct`,
                                  )
                              : tCampaigns(
                                  "create_campaign.form.select_client_type",
                                )}
                          </DropdownTrigger>
                          <DropdownContent align="left" className="w-full">
                            <DropdownItem
                              value="direct"
                              onClick={() => {
                                field.onChange("Direct Advertiser");
                                handleFieldBlur(
                                  "clientType",
                                  "Direct Advertiser",
                                );
                                setTimeout(
                                  () => handleFieldBlur("agency", "", true),
                                  100,
                                );
                              }}
                            >
                              {tCampaigns(
                                "create_campaign.client_types.direct",
                              )}
                            </DropdownItem>
                            <DropdownItem
                              value="agency"
                              onClick={() => {
                                field.onChange("Agency");
                                handleFieldBlur("clientType", "Agency");
                              }}
                            >
                              {tCampaigns(
                                "create_campaign.client_types.agency",
                              )}
                            </DropdownItem>
                          </DropdownContent>
                        </Dropdown>
                      </div>
                    )}
                  />
                  {errors.clientType && (
                    <div
                      id="create-campaign-client-type-error"
                      className="text-sm text-mw-error-500 mt-1"
                    >
                      {errors.clientType.message}
                    </div>
                  )}
                </div>
              </div>

              {watchedClientType === "Agency" && (
                <div
                  id="create-campaign-agency-section"
                  className="grid grid-cols-2 gap-6"
                >
                  <div>
                    <Label className="mb-2">
                      {tCampaigns("create_campaign.form.agency")}
                    </Label>
                    <Controller
                      name="agency"
                      control={control}
                      render={({ field }) => (
                        <RemoteDropdown<AgencyDropdownOption>
                          id="create-campaign-agency-dropdown"
                          fetcher={agencyFetcher}
                          disabled={isAgency}
                          value={getAgencyOptionById(field.value) || null}
                          onSelectionChange={(selected) => {
                            if (Array.isArray(selected)) {
                              const value =
                                selected.length > 0 ? selected[0].id : "";
                              const label =
                                selected.length > 0
                                  ? selected[0].label
                                  : undefined;
                              field.onChange(value);
                              selectedAgencyLabelRef.current = label;
                              handleFieldBlur("agency", value);
                              if (campaignState.campaignData) {
                                dispatch(
                                  setCampaignData({
                                    ...campaignState.campaignData,
                                    agency: value
                                      ? { id: value, name: label ?? "" }
                                      : undefined,
                                  }),
                                );
                              }
                            } else {
                              const value = selected ? selected.id : "";
                              const label = selected
                                ? selected.label
                                : undefined;
                              field.onChange(value);
                              selectedAgencyLabelRef.current = label;
                              handleFieldBlur("agency", value);
                              if (campaignState.campaignData) {
                                dispatch(
                                  setCampaignData({
                                    ...campaignState.campaignData,
                                    agency: value
                                      ? { id: value, name: label ?? "" }
                                      : undefined,
                                  }),
                                );
                              }
                            }
                          }}
                          placeholder={tCampaigns(
                            "create_campaign.form.select_agency",
                          )}
                          searchable={true}
                          searchPlaceholder={tCampaigns(
                            "create_campaign.form.search_here",
                          )}
                          pageSize={10}
                          className="w-full"
                          maxHeight="250px"
                          renderOption={(option, isSelected) => (
                            <div
                              className={`flex flex-col items-start gap-1 px-3 py-1 ${isSelected ? "bg-mw-primary-50" : ""}`}
                            >
                              <p className="font-medium text-sm text-mw-neutral-500">
                                {option.label}
                              </p>
                              {option.description && (
                                <p className="text-xs text-mw-neutral-400">
                                  {option.description}
                                </p>
                              )}
                              <DropdownSeparator />
                            </div>
                          )}
                          renderEmpty={() => (
                            <div className="p-4 text-center">
                              <p className="text-sm text-mw-neutral-500 mb-3">
                                {tCampaigns("agency_dropdown.no_agencies")}
                              </p>
                            </div>
                          )}
                          renderFooter={(setIsOpen) =>
                            !isAgency && (
                              <div>
                                <button
                                  id="create-agency-btn"
                                  type="button"
                                  onClick={() => {
                                    handleCreateAgency();
                                    setIsOpen(false);
                                  }}
                                  className="w-full flex items-start justify-start gap-2 px-3 py-2 text-mw-primary-500 hover:text-mw-primary-600 hover:bg-mw-primary-50 text-sm"
                                >
                                  <Plus className="size-4" />
                                  {tCampaigns("agency_dropdown.create_agency")}
                                </button>
                              </div>
                            )
                          }
                        />
                      )}
                    />
                    {errors.agency && (
                      <div
                        id="create-campaign-agency-error"
                        className="text-sm text-mw-error-500 mt-1"
                      >
                        {errors.agency.message}
                      </div>
                    )}
                    {isAgency && hasChildAgencies && (
                      <p
                        id="create-campaign-agency-impersonation-note"
                        className="mt-1 text-xs text-mw-neutral-500"
                      >
                        {tCampaigns(
                          "create_campaign.form.agency_child_company_note",
                        )}
                      </p>
                    )}
                  </div>
                </div>
              )}

              {/* DSP + Seat ID row */}
              <div className="grid grid-cols-2 gap-6">
                <div>
                  <Label className="mb-2">
                    <span className="inline-flex items-center gap-1">
                      {tCampaigns("create_campaign.form.dsp")}
                      <span className="text-xs text-mw-neutral-400 font-normal">
                        {tCampaigns("create_campaign.form.optional_label")}
                      </span>
                    </span>
                  </Label>
                  <Controller
                    name="dsp"
                    control={control}
                    render={({ field }) => (
                      <Dropdown name="dsp">
                        <DropdownTrigger className="w-full justify-between">
                          {field.value === "ACTIVATE"
                            ? tCampaigns("create_campaign.form.dsp_active")
                            : field.value === "NONE"
                              ? tCampaigns("create_campaign.form.dsp_none")
                              : tCampaigns("create_campaign.form.select_dsp")}
                        </DropdownTrigger>
                        <DropdownContent align="left" className="w-full">
                          <DropdownItem
                            value="NONE"
                            onClick={() => {
                              field.onChange("NONE");
                              // Selecting "None" autosaves dsp as null, which
                              // the default empty-value check would otherwise
                              // skip - bypass it so the clear actually saves.
                              handleFieldBlur("dsp", "NONE", true);
                            }}
                          >
                            {tCampaigns("create_campaign.form.dsp_none")}
                          </DropdownItem>
                          <DropdownItem
                            value="ACTIVATE"
                            onClick={() => {
                              field.onChange("ACTIVATE");
                              handleFieldBlur("dsp", "ACTIVATE");
                            }}
                          >
                            {tCampaigns("create_campaign.form.dsp_active")}
                          </DropdownItem>
                          <DropdownSeparator />
                          <div className="px-3 py-2 text-xs text-mw-neutral-500">
                            {tCampaigns(
                              "create_campaign.form.dsp_add_seat_id_hint",
                            )}{" "}
                            <a
                              href={`${CONFIG.ACCOUNT_URL}/dsp`}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="text-mw-primary-500 hover:text-mw-primary-600 underline"
                            >
                              {tCampaigns(
                                "create_campaign.form.dsp_add_seat_id_link",
                              )}
                            </a>
                          </div>
                        </DropdownContent>
                      </Dropdown>
                    )}
                  />
                  {watch("dsp") && watch("dsp") !== "NONE" && (
                    <div className="mt-1 space-y-1">
                      <p className="text-xs text-mw-neutral-500">
                        {tCampaigns(
                          "create_campaign.form.dsp_recommendation_hint",
                        )}
                      </p>
                    </div>
                  )}
                </div>
                {watch("dsp") === "ACTIVATE" && (
                  <div>
                    <Label className="mb-2">
                      <span className="inline-flex items-center gap-1">
                        {tCampaigns("create_campaign.form.seat_id")}
                        <span className="text-xs text-mw-neutral-400 font-normal">
                          {tCampaigns("create_campaign.form.optional_label")}
                        </span>
                      </span>
                    </Label>
                    <Controller
                      name="seatId"
                      control={control}
                      render={({ field }) => (
                        <Input
                          id="create-campaign-seat-id-input"
                          value={field.value ?? ""}
                          disabled
                          placeholder={tCampaigns(
                            "create_campaign.form.seat_id_placeholder",
                          )}
                        />
                      )}
                    />
                    <p className="flex items-center gap-1 text-xs text-mw-neutral-400 mt-1">
                      <Info className="w-3.5 h-3.5 shrink-0" />
                      {tCampaigns("create_campaign.form.seat_id_helper")}
                    </p>
                  </div>
                )}
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Right Side - Form Insights & Quick Tips */}
      <div id="create-campaign-form-right" className="w-72 space-y-4">
        {/* Form Insights - Always visible */}
        {!getCurrentBrand() && (
          <Card id="create-campaign-form-insights">
            <CardHeader className="p-4">
              <div className="inline-flex justify-start items-center gap-2">
                <Info className="w-4 h-4 relative overflow-hidden" />
                <h3 className="font-medium text-sm">
                  {tCampaigns("create_campaign.sidebar.form_insights")}
                </h3>
              </div>
            </CardHeader>
            <CardContent>
              <div className="space-y-4 text-xs">
                <div className="flex justify-start items-start gap-2">
                  <div className="h-5 flex justify-start items-center gap-2.5">
                    <div className="w-3 h-3 bg-mw-success-500 rounded-full mt-1.5"></div>
                  </div>
                  <div className="justify-start text-mw-neutral-500 text-xs font-normal leading-tight">
                    {tCampaigns(
                      "create_campaign.sidebar.form_insights_tips.campaign_name_unique",
                    )}
                  </div>
                </div>
                <div className="flex justify-start items-start gap-2">
                  <div className="h-5 flex justify-start items-center gap-2.5">
                    <div className="w-3 h-3 bg-mw-success-600 rounded-full mt-1.5"></div>
                  </div>
                  <div className="justify-start text-mw-neutral-500 text-xs font-normal leading-tight">
                    {tCampaigns(
                      "create_campaign.sidebar.form_insights_tips.realistic_dates",
                    )}
                  </div>
                </div>
                <div className="flex justify-start items-start gap-2">
                  <div className="h-5 flex justify-start items-center gap-2.5">
                    <div className="w-3 h-3 bg-mw-success-600 rounded-full mt-1.5"></div>
                  </div>
                  <div className="justify-start text-mw-neutral-500 text-xs font-normal leading-tight">
                    {tCampaigns(
                      "create_campaign.sidebar.form_insights_tips.brand_targeting",
                    )}
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        )}

        {/* Brand Insights - Only visible when brand is selected */}
        {getCurrentBrand() && (
          <Card id="create-campaign-brand-insights">
            <CardHeader className="p-4">
              <div className="inline-flex justify-start items-center gap-2">
                <Info className="w-4 h-4 relative overflow-hidden" />
                <h3 className="font-medium text-sm">
                  {tCampaigns("create_campaign.sidebar.brand_insights")}
                </h3>
              </div>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                <div className="self-stretch p-1 rounded outline -outline-offset-1 outline-mw-neutral-100  flex flex-col justify-center items-start gap-2.5">
                  <div className="self-stretch rounded inline-flex justify-start items-center gap-2">
                    <ImageWithFallback
                      src={getCurrentBrand()?.logoUrl ?? ""}
                      alt={getCurrentBrand()?.label || ""}
                      fallbackElement={
                        <div className="w-12 h-12 rounded-xl bg-mw-neutral-100 dark:bg-mw-neutral-700 flex items-center justify-center flex-shrink-0">
                          <Building2 className="w-6 h-6 text-mw-neutral-400" />
                        </div>
                      }
                      className="w-12 h-12 rounded-xl object-cover"
                    />
                    <div className="flex-1 inline-flex flex-col justify-start items-start gap-1">
                      <div className="justify-start text-mw-neutral-500 text-xs font-medium leading-tight">
                        {getCurrentBrand()?.label}
                      </div>
                    </div>
                  </div>
                </div>
                <div className="space-y-4">
                  <div className="flex justify-start items-start gap-2">
                    <div className="h-5 flex justify-start items-center gap-2.5">
                      <div className="w-3 h-3 bg-mw-success-600 rounded-full"></div>
                    </div>
                    <div className="justify-start text-mw-neutral-500 text-xs font-normal leading-tight mt-0.5">
                      {tCampaigns("brand_dropdown.category_label")}:{" "}
                      {getCurrentBrand()?.category}
                    </div>
                  </div>
                  <div className="flex justify-start items-start gap-2">
                    <div className="h-5 flex justify-start items-center gap-2.5">
                      <div className="w-3 h-3 bg-mw-success-600 rounded-full mt-1.5"></div>
                    </div>
                    <div className="justify-start text-mw-neutral-500 text-xs font-normal leading-tight">
                      {tCampaigns("brand_dropdown.brand_selection_help")}
                    </div>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        )}

        {/* Quick Tips */}
        <Card>
          <CardHeader className="p-4">
            <div className="inline-flex justify-start items-center gap-2">
              <Info className="w-4 h-4 relative overflow-hidden" />
              <h3 className="font-medium text-sm">
                {tCampaigns("create_campaign.sidebar.quick_tips")}
              </h3>
            </div>
          </CardHeader>
          <CardContent>
            <div className="space-y-4 text-xs">
              <div className="flex justify-start items-start gap-2">
                <div className="h-5 flex justify-start items-center gap-2.5">
                  <div className="w-3 h-3 bg-mw-primary-500 rounded-full mt-1.5"></div>
                </div>
                <div className="justify-start text-mw-neutral-500 text-xs font-normal leading-tight">
                  {getCurrentBrand()
                    ? tCampaigns(
                        "create_campaign.sidebar.tips.brand_targeting_recommendations",
                      )
                    : tCampaigns(
                        "create_campaign.sidebar.tips.brand_recommendations",
                      )}
                </div>
              </div>
              <div className="flex justify-start items-start gap-2">
                <div className="h-5 flex justify-start items-center gap-2.5">
                  <div className="w-3 h-3 bg-mw-primary-500 rounded-full mt-1.5"></div>
                </div>
                <div className="justify-start text-mw-neutral-500 text-xs font-normal leading-tight">
                  {tCampaigns("create_campaign.sidebar.tips.budget_next")}
                </div>
              </div>
              <div className="flex justify-start items-start gap-2">
                <div className="h-5 flex justify-start items-center gap-2.5">
                  <div className="w-3 h-3 bg-mw-primary-500 rounded-full mt-1.5"></div>
                </div>
                <div className="justify-start text-mw-neutral-500 text-xs font-normal leading-tight">
                  {tCampaigns("create_campaign.sidebar.tips.can_change_later")}
                </div>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Brand Creation Modal Drawer */}
      <BrandCreationForm
        isOpen={isBrandModalOpen}
        onClose={() => setIsBrandModalOpen(false)}
        onSuccess={handleBrandSuccess}
        iabCategories={iabCategories}
        companyId={
          profile?.activeCompanyId || profile?.current_company?.id || ""
        }
        companyBrands={brands}
      />

      {/* Agency Creation Modal Drawer */}
      <AgencyCreationForm
        isOpen={isAgencyModalOpen}
        onClose={() => setIsAgencyModalOpen(false)}
        onSuccess={handleAgencySuccess}
        companyId={
          profile?.activeCompanyId || profile?.current_company?.id || ""
        }
        companyAgencies={agencies}
      />
    </div>
  );
});

CreateCampaignForm.displayName = "CreateCampaignForm";

export default CreateCampaignForm;
