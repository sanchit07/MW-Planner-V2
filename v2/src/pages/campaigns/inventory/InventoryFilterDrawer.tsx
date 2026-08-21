import { MediaOwnerDropdown } from "@components/common/MediaOwnerDropdown";
import { Button } from "@components/ui/Button";
import {
  Dropdown,
  DropdownContent,
  DropdownItem,
  DropdownTrigger,
} from "@components/ui/Dropdown";
import { Label } from "@components/ui/Label";
import { ModalDrawer } from "@components/ui/ModalDrawer";
import type { TreeNode } from "@components/ui/MultiSelect";
import MultiSelect from "@components/ui/MultiSelect";
import { COUNTRY_CURRENCY_MAP } from "@constants/budget.constants";
import { INVENTORY_SIZE_LIST_MAP } from "@constants/campaign.constants";
import {
  useGetVenuesQuery,
  type VenueItem,
} from "@services/inventory/inventorySlice";
import { useTranslate, useTolgee } from "@tolgee/react";
import React, { useState, useEffect, useMemo } from "react";
import {
  CINEMA_GENRES,
  CINEMA_RATINGS,
  InventoryClassification,
  InventoryEnvironment,
  MediaChannel,
  ProgrammaticDealType,
  ProgrammaticSupport,
} from "src/constants/inventory.constants";

import {
  InventoryFilters,
  type ProgrammaticSupportFilter,
} from "../../../types/inventory.types";
import { mediaChannelsToClassifications } from "../../../utils/inventory.utils";
import storage from "../../../utils/storage";
import { toKebabKey } from "../../../utils/stringManipulation.utils";

const INVENTORY_FILTERS_STORAGE_KEY = "inventory_filters";

interface InventoryFilterDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  filters: InventoryFilters;
  onApplyFilters: (filters: InventoryFilters) => void;
  country?: string; // Country for media owner filter (default: "Singapore")
  company_type?: string; // Company type for media owner filter (default: "MEDIA_OWNER")
  goal_type?: string;
  media_channels?: string[] | null; // Campaign media channels; a single channel locks the classification filter
  mediaOwnerStaticOptions?: TreeNode[];
}

const INVENTOY_CLASSIFICATIONS = [
  InventoryClassification.CLASSIC,
  InventoryClassification.DIGITAL,
];

// Configuration for which classifications to disable based on goal type
const CLASSIFICATION_DISABLED: Record<string, string[]> = {
  ADPLAYS: [InventoryClassification.CLASSIC], // Disable CLASSIC for ADPLAYS
};

// Configuration for which classifications to require based on goal type
const CLASSIFICATION_REQUIRED: Record<string, string[]> = {
  ADPLAYS: [InventoryClassification.DIGITAL], // Require DIGITAL for ADPLAYS
};

const INVENTORY_ENVIRONMENTS = [
  InventoryEnvironment.OUTDOOR,
  InventoryEnvironment.INDOOR,
  InventoryEnvironment.SEMI_OUTDOOR,
  InventoryEnvironment.IN_TRANSIT,
];

const MODE_OF_OPERATION = ["spot", "loop"];

// SIZE_OPTIONS is computed inside the component to allow translation

const PROGRAMMATIC_DEAL_OPTIONS: ProgrammaticSupportFilter[] = [
  ProgrammaticSupport.ALL,
  ProgrammaticSupport.YES,
  ProgrammaticSupport.NO,
];

const PROGRAMMATIC_DEAL_TYPES = [
  ProgrammaticDealType.GUARANTEED,
  ProgrammaticDealType.PREFERRED_DEAL,
  ProgrammaticDealType.PRIVATE_AUCTION,
  ProgrammaticDealType.OPEN_AUCTION,
  ProgrammaticDealType.EVERGREEN_PMP,
];

export const InventoryFilterDrawer: React.FC<InventoryFilterDrawerProps> = ({
  isOpen,
  onClose,
  filters,
  onApplyFilters,
  country,
  company_type = "MEDIA_OWNER",
  goal_type,
  media_channels,
  mediaOwnerStaticOptions,
}) => {
  const countryIso = country
    ? (COUNTRY_CURRENCY_MAP[toKebabKey(country)]?.isoCode ?? country)
    : undefined;
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { t: tCommon } = useTranslate(["common"]);

  // Venue types come from the /venues API as a hierarchical tree (same source
  // as the Targeting step). Selected values are the venue `stringValue`s and are
  // sent to /filter as `venueTypes`.
  const language = useTolgee(["language"]).getLanguage();
  const { data: venuesData = [] } = useGetVenuesQuery({ language });
  const venueTreeNodes = useMemo((): TreeNode[] => {
    const transform = (items: VenueItem[]): TreeNode[] =>
      items.map((item) => ({
        label: item.name,
        value: item.stringValue,
        id: String(item.enumerationId),
        disabled: false,
        ...(item.definition ? { description: item.definition } : {}),
        ...(item.children?.length
          ? { children: transform(item.children) }
          : {}),
      }));
    return transform(venuesData);
  }, [venuesData]);

  // Single campaign media channel locks the classification filter: the
  // matching classification is forced-on and the other one is disabled.
  // Empty when both/no channels are selected (no lock).
  const channelLockedClassifications = useMemo(
    () => mediaChannelsToClassifications(media_channels),
    [media_channels],
  );

  // The Cinema section (genre + rating constraints) is only relevant when the
  // campaign targets cinema — either the media channel includes CINEMA or the
  // resolved classification union includes Cinema.
  const showCinemaSection = useMemo(() => {
    const channels = Array.isArray(media_channels) ? media_channels : [];
    return (
      channels.includes(MediaChannel.CINEMA) ||
      channelLockedClassifications.includes(InventoryClassification.CINEMA)
    );
  }, [media_channels, channelLockedClassifications]);

  // Load filters from localStorage
  const loadFiltersFromStorage = (): InventoryFilters => {
    try {
      const stored = storage.getItem(INVENTORY_FILTERS_STORAGE_KEY);
      if (stored) {
        const parsed = JSON.parse(stored);
        return {
          ...parsed,
          searchbyquery: filters.searchbyquery || parsed.searchbyquery || "",
          programmaticSupport: parsed.programmaticSupport ?? "ALL",
          dealTypes: Array.isArray(parsed.dealTypes) ? parsed.dealTypes : [],
        };
      }
    } catch (error) {
      console.error("Error loading inventory filters from storage:", error);
    }
    return {
      ...filters,
      searchbyquery: filters.searchbyquery || "",
      programmaticSupport: filters.programmaticSupport ?? "ALL",
      dealTypes: filters.dealTypes ?? [],
    };
  };

  const [localFilters, setLocalFilters] = useState<InventoryFilters>(
    loadFiltersFromStorage(),
  );

  // Update local state when drawer opens - merge with stored filters
  useEffect(() => {
    if (isOpen) {
      const storedFilters = loadFiltersFromStorage();

      // Auto-select required classifications based on goal type
      const requiredClassifications = goal_type
        ? CLASSIFICATION_REQUIRED[goal_type.toUpperCase()] || []
        : [];

      const currentClassifications =
        storedFilters.inventoryClassification || [];
      // Channel lock wins over anything stored (stale manual picks included)
      const mergedClassifications =
        channelLockedClassifications.length === 1
          ? channelLockedClassifications
          : Array.from(
              new Set([...currentClassifications, ...requiredClassifications]),
            );

      setLocalFilters({
        ...storedFilters,
        inventoryClassification: mergedClassifications,
        searchbyquery:
          filters.searchbyquery || storedFilters.searchbyquery || "",
      });
    }
  }, [isOpen, filters, goal_type, channelLockedClassifications]);

  const handleFilterChange = (
    category: keyof InventoryFilters,
    value: string[] | string,
  ) => {
    // Ensure required classifications are always included
    if (category === "inventoryClassification" && Array.isArray(value)) {
      const requiredClassifications = goal_type
        ? CLASSIFICATION_REQUIRED[goal_type.toUpperCase()] || []
        : [];

      // Channel lock: the classification cannot be changed at all
      const mergedValue =
        channelLockedClassifications.length === 1
          ? channelLockedClassifications
          : Array.from(new Set([...value, ...requiredClassifications]));

      setLocalFilters((prev) => ({
        ...prev,
        [category]: mergedValue,
      }));
    } else {
      setLocalFilters((prev) => ({
        ...prev,
        [category]: value,
      }));
    }
  };

  const handleApplyFilters = () => {
    // Preserve searchbyquery when applying filters
    const filtersToApply = {
      ...localFilters,
      searchbyquery: filters.searchbyquery || "",
    };

    // Save filters to localStorage (without searchbyquery as it's not a filter)
    try {
      const { ...filtersToStore } = filtersToApply;
      storage.setItem(
        INVENTORY_FILTERS_STORAGE_KEY,
        JSON.stringify(filtersToStore),
      );
    } catch (error) {
      console.error("Error saving inventory filters to storage:", error);
    }

    onApplyFilters(filtersToApply);
    onClose();
  };

  // Clear only resets the drawer's local fields; nothing is applied or persisted
  // until the user clicks Apply (which runs handleApplyFilters). The drawer
  // stays open.
  const handleClearFilters = () => {
    const emptyFilters: InventoryFilters = {
      mediaOwners: [],
      venueTypes: [],
      bookingMode: [],
      sizes: [],
      environments: [],
      // Clear keeps the channel-locked classification (user cannot remove it)
      inventoryClassification:
        channelLockedClassifications.length === 1
          ? channelLockedClassifications
          : [],
      latitude: "",
      longitude: "",
      searchbyquery: filters.searchbyquery || "", // Preserve search query
      programmaticSupport: "ALL",
      dealTypes: [],
      cinemaGenres: [],
      cinemaRatings: [],
    };
    setLocalFilters(emptyFilters);
  };

  const handleClose = () => {
    // Reset local state to saved filters on cancel
    setLocalFilters(filters);
    onClose();
  };

  const LABEL_NAMESPACE: Record<string, string> = {
    [InventoryClassification.CLASSIC]: tCommon(
      "inventoryClassification.classic",
    ),
    [InventoryClassification.DIGITAL]: tCommon(
      "inventoryClassification.digital",
    ),
    [InventoryEnvironment.OUTDOOR]: tCommon("inventoryEnvironment.outdoor"),
    [InventoryEnvironment.INDOOR]: tCommon("inventoryEnvironment.indoor"),
    [InventoryEnvironment.SEMI_OUTDOOR]: tCommon(
      "inventoryEnvironment.semi_outdoor",
    ),
    [InventoryEnvironment.IN_TRANSIT]: tCommon(
      "inventoryEnvironment.in_transit",
    ),
    [ProgrammaticDealType.GUARANTEED]: tCommon(
      "programmaticDealType.guaranteed",
    ),
    [ProgrammaticDealType.PREFERRED_DEAL]: tCommon(
      "programmaticDealType.preferred_deal",
    ),
    [ProgrammaticDealType.PRIVATE_AUCTION]: tCommon(
      "programmaticDealType.private_auction",
    ),
    [ProgrammaticDealType.OPEN_AUCTION]: tCommon(
      "programmaticDealType.open_auction",
    ),
    [ProgrammaticDealType.EVERGREEN_PMP]: tCommon(
      "programmaticDealType.evergreen_pmp",
    ),
    spot: tCommon("inventoryMode.spot"),
    loop: tCommon("inventoryMode.loop"),
  };

  const SIZE_OPTIONS = INVENTORY_SIZE_LIST_MAP.map((item) => ({
    ...item,
    label: tCommon(`inventorySize.${item.id.toLowerCase()}.label`),
    description: tCommon(`inventorySize.${item.id.toLowerCase()}.description`),
  }));

  // Convert string arrays to MultiSelect option format
  const toOptions = (items: string[]) =>
    items.map((item) => ({
      value: item,
      label: LABEL_NAMESPACE[item] || item.replace(/_/g, " "),
    }));

  // Get classification options with disabled state based on goal type
  const getClassificationOptions = () => {
    const disabledClassifications = goal_type
      ? CLASSIFICATION_DISABLED[goal_type.toUpperCase()] || []
      : [];

    const requiredClassifications = goal_type
      ? CLASSIFICATION_REQUIRED[goal_type.toUpperCase()] || []
      : [];

    const isChannelLocked = channelLockedClassifications.length === 1;

    return INVENTOY_CLASSIFICATIONS.map((classification) => {
      // Channel lock disables both options: the locked one stays selected,
      // the other one is unavailable for this campaign's media channel.
      if (isChannelLocked) {
        const isLocked = channelLockedClassifications.includes(classification);
        return {
          value: classification,
          label: LABEL_NAMESPACE[classification] || classification,
          disabled: true,
          description: isLocked
            ? tCampaigns("inventories.filters.classification_locked_by_channel")
            : tCampaigns("inventories.filters.classification_not_in_channel"),
        };
      }

      const isDisabled = disabledClassifications.includes(classification);
      const isRequired = requiredClassifications.includes(classification);

      let description: string | undefined;
      if (isDisabled) {
        description = tCampaigns(
          "inventories.filters.classification_not_available",
          { goalType: goal_type?.toLowerCase() ?? "" },
        );
      } else if (isRequired) {
        description = tCampaigns(
          "inventories.filters.classification_required",
          { goalType: goal_type?.toLowerCase() ?? "" },
        );
      }

      return {
        value: classification,
        label: LABEL_NAMESPACE[classification] || classification,
        disabled: isDisabled,
        description,
      };
    });
  };

  return (
    <ModalDrawer
      isOpen={isOpen}
      onClose={handleClose}
      title={tCampaigns("inventories.filters.title")}
      size="lg"
      footer={
        <div className="flex justify-end gap-3">
          <Button
            variant="outline"
            className="outline-mw-primary-500 text-mw-primary-500"
            size="md"
            onClick={handleClearFilters}
          >
            {tCampaigns("inventories.filters.clear_filters")}
          </Button>
          <Button variant="primary" size="md" onClick={handleApplyFilters}>
            {tCampaigns("inventories.filters.apply_filters")}
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        {/* <div className="space-y-2">
          <div className="flex items-center gap-2">
            <h3 className="text-base font-medium text-mw-neutral-900 dark:text-white">
              {tCampaigns("inventories.filters.location_section")}
            </h3>
          </div>

          <Input
            id="latitude"
            type="text"
            label={tCampaigns("inventories.filters.latitude")}
            placeholder={tCampaigns("inventories.filters.latitude_placeholder")}
            value={localFilters.latitude || ""}
            onChange={(e) => handleFilterChange("latitude", e.target.value)}
          />

          <Input
            id="longitude"
            type="text"
            placeholder={tCampaigns(
              "inventories.filters.longitude_placeholder",
            )}
            label={tCampaigns("inventories.filters.longitude")}
            value={localFilters.longitude || ""}
            onChange={(e) => handleFilterChange("longitude", e.target.value)}
          />
        </div> */}
        {/* Section 2: Operation Filters */}
        <div className="space-y-2">
          <div className="flex items-center gap-2 ">
            <h3 className="text-base font-medium text-mw-neutral-900">
              {tCampaigns("inventories.filters.operations_section")}
            </h3>
          </div>

          {/* Media Owner */}
          <div className="space-y-2">
            <Label>{tCampaigns("inventories.filters.media_owner")}</Label>
            <MediaOwnerDropdown
              id="inventory-filter-media-owner"
              value={localFilters.mediaOwners}
              onChange={(values) => handleFilterChange("mediaOwners", values)}
              placeholder={tCampaigns(
                "inventories.filters.media_owner_placeholder",
              )}
              companyType={company_type}
              country={countryIso}
              staticOptions={mediaOwnerStaticOptions}
            />
          </div>

          {/* Inventory Classification */}
          <div className="space-y-2">
            <Label>
              {tCampaigns("inventories.filters.inventory_classification")}
            </Label>
            <MultiSelect
              options={getClassificationOptions()}
              value={localFilters.inventoryClassification}
              onChange={(values) =>
                handleFilterChange("inventoryClassification", values)
              }
              placeholder={tCampaigns(
                "inventories.filters.inventory_classification_placeholder",
              )}
            />
          </div>

          {/* Venue Type */}
          <div className="space-y-2">
            <Label>{tCampaigns("inventories.filters.venue_type")}</Label>
            <MultiSelect
              options={venueTreeNodes}
              value={localFilters.venueTypes}
              onChange={(values) => handleFilterChange("venueTypes", values)}
              placeholder={tCampaigns(
                "inventories.filters.venue_type_placeholder",
              )}
            />
          </div>

          {/* Environment */}
          <div className="space-y-2">
            <Label>{tCampaigns("inventories.filters.environment")}</Label>
            <MultiSelect
              options={toOptions(INVENTORY_ENVIRONMENTS)}
              value={localFilters.environments}
              onChange={(values) => handleFilterChange("environments", values)}
              placeholder={tCampaigns(
                "inventories.filters.environment_placeholder",
              )}
            />
          </div>

          {/* Mode of Operation */}
          <div className="space-y-2">
            <Label>{tCampaigns("inventories.filters.mode_of_operation")}</Label>
            <MultiSelect
              options={toOptions(MODE_OF_OPERATION)}
              value={localFilters.bookingMode}
              onChange={(values) => handleFilterChange("bookingMode", values)}
              placeholder={tCampaigns(
                "inventories.filters.mode_of_operation_placeholder",
              )}
            />
          </div>

          {/* Size */}
          <div className="space-y-2">
            <Label>{tCampaigns("inventories.filters.size")}</Label>
            <MultiSelect
              options={SIZE_OPTIONS}
              value={localFilters.sizes}
              onChange={(values) => handleFilterChange("sizes", values)}
              placeholder={tCampaigns("inventories.filters.size_placeholder")}
            />
          </div>

          {/* Programmatic Deal */}
          <div className="space-y-2">
            <Label>{tCampaigns("inventories.filters.programmatic_deal")}</Label>
            <Dropdown
              value={localFilters.programmaticSupport ?? "ALL"}
              onChange={(value) =>
                handleFilterChange(
                  "programmaticSupport",
                  value as ProgrammaticSupportFilter,
                )
              }
            >
              <DropdownTrigger hasValue={true}>
                {localFilters.programmaticSupport === "YES"
                  ? tCampaigns("inventories.filters.programmatic_deal_yes")
                  : localFilters.programmaticSupport === "NO"
                    ? tCampaigns("inventories.filters.programmatic_deal_no")
                    : tCampaigns("inventories.filters.programmatic_deal_all")}
              </DropdownTrigger>
              <DropdownContent>
                {PROGRAMMATIC_DEAL_OPTIONS.map((opt) => (
                  <DropdownItem key={opt} value={opt}>
                    {opt === "ALL"
                      ? tCampaigns("inventories.filters.programmatic_deal_all")
                      : opt === "YES"
                        ? tCampaigns(
                            "inventories.filters.programmatic_deal_yes",
                          )
                        : tCampaigns(
                            "inventories.filters.programmatic_deal_no",
                          )}
                  </DropdownItem>
                ))}
              </DropdownContent>
            </Dropdown>
          </div>

          {/* Programmatic Deal Type */}
          <div className="space-y-2">
            <Label>
              {tCampaigns("inventories.filters.programmatic_deal_type")}
            </Label>
            <MultiSelect
              options={toOptions(PROGRAMMATIC_DEAL_TYPES)}
              value={localFilters.dealTypes ?? []}
              onChange={(values) => handleFilterChange("dealTypes", values)}
              placeholder={tCampaigns(
                "inventories.filters.programmatic_deal_type_placeholder",
              )}
            />
          </div>
        </div>

        {/* Section 3: Cinema (only when campaign targets cinema) */}
        {showCinemaSection && (
          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <h3 className="text-base font-medium text-mw-neutral-900">
                {tCampaigns("inventories.filters.cinema_section")}
              </h3>
            </div>

            {/* Cinema Genres */}
            <div className="space-y-2">
              <Label>{tCampaigns("inventories.filters.cinema_genres")}</Label>
              <MultiSelect
                options={CINEMA_GENRES.map((g) => ({ value: g, label: g }))}
                value={localFilters.cinemaGenres ?? []}
                onChange={(values) =>
                  handleFilterChange("cinemaGenres", values)
                }
                placeholder={tCampaigns(
                  "inventories.filters.cinema_genres_placeholder",
                )}
              />
            </div>

            {/* Cinema Ratings */}
            <div className="space-y-2">
              <Label>{tCampaigns("inventories.filters.cinema_ratings")}</Label>
              <MultiSelect
                options={CINEMA_RATINGS.map((r) => ({ value: r, label: r }))}
                value={localFilters.cinemaRatings ?? []}
                onChange={(values) =>
                  handleFilterChange("cinemaRatings", values)
                }
                placeholder={tCampaigns(
                  "inventories.filters.cinema_ratings_placeholder",
                )}
              />
            </div>
          </div>
        )}
      </div>
    </ModalDrawer>
  );
};

export default InventoryFilterDrawer;
