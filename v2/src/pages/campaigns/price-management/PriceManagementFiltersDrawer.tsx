import { MediaOwnerDropdown } from "@components/common/MediaOwnerDropdown";
import { Button } from "@components/ui/Button";
import { Input } from "@components/ui/Input";
import { Label } from "@components/ui/Label";
import { ModalDrawer } from "@components/ui/ModalDrawer";
import MultiSelect, { TreeNode } from "@components/ui/MultiSelect";
import { useTranslate } from "@tolgee/react";
import { Info } from "lucide-react";
import React, { useState, useEffect } from "react";
import {
  InventoryType,
  InventoryClassification,
} from "src/constants/inventory.constants";

export interface PriceManagementFilters {
  cities: string[];
  inventoryTypes: string[];
  mediaOwners: string[];
  minPricing: number | "";
  maxPricing: number | "";
}

interface PriceManagementFiltersDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  filters: PriceManagementFilters;
  onApplyFilters: (filters: PriceManagementFilters) => void;
  onClearFilters: () => void;
  cityOptions?: TreeNode[];
}

const INVENTORY_TYPES = [
  InventoryClassification.DIGITAL,
  InventoryClassification.CLASSIC,
  InventoryClassification.CINEMA,
  InventoryType.TRANSIT,
  InventoryType.STREET_FURNITURE,
  InventoryType.PLACE_BASED,
  InventoryType.DIGITAL_NETWORK,
  InventoryType.RETAIL,
];

export const PriceManagementFiltersDrawer: React.FC<
  PriceManagementFiltersDrawerProps
> = ({ isOpen, onClose, filters, onApplyFilters, cityOptions = [] }) => {
  const { t } = useTranslate(["price"]);
  const { t: tCommon } = useTranslate(["common"]);
  const [localFilters, setLocalFilters] =
    useState<PriceManagementFilters>(filters);

  const LABEL_MAP: Record<string, string> = {
    [InventoryClassification.CLASSIC]: tCommon(
      "inventoryClassification.classic",
    ),
    [InventoryClassification.DIGITAL]: tCommon(
      "inventoryClassification.digital",
    ),
    [InventoryClassification.CINEMA]: tCommon("inventoryType.cinema"),
    [InventoryType.TRANSIT]: tCommon("inventoryType.transit"),
    [InventoryType.STREET_FURNITURE]: tCommon("inventoryType.street_furniture"),
    [InventoryType.PLACE_BASED]: tCommon("inventoryType.place_based"),
    [InventoryType.DIGITAL_NETWORK]: tCommon("inventoryType.digital_network"),
    [InventoryType.RETAIL]: tCommon("inventoryType.retail"),
  };

  // Convert string arrays to MultiSelect option format
  const toOptions = (items: string[]) =>
    items.map((item) => ({ value: item, label: LABEL_MAP[item] || item }));

  // Update local state when drawer opens or filters prop changes
  useEffect(() => {
    if (isOpen) {
      setLocalFilters(filters);
    }
  }, [isOpen, filters]);

  const handleFilterChange = (
    category: keyof PriceManagementFilters,
    value: string[] | number | "",
  ) => {
    setLocalFilters((prev) => ({
      ...prev,
      [category]: value,
    }));
  };

  const handleApplyFilters = () => {
    onApplyFilters(localFilters);
    onClose();
  };

  const handleClose = () => {
    // Reset local state to original filters on cancel
    setLocalFilters(filters);
    onClose();
  };

  // Check if filters have changed from initial state
  const hasFiltersChanged =
    JSON.stringify(localFilters) !== JSON.stringify(filters);

  // Check if all filters are empty
  const areAllFiltersEmpty =
    localFilters.cities.length === 0 &&
    localFilters.inventoryTypes.length === 0 &&
    localFilters.mediaOwners.length === 0 &&
    localFilters.minPricing === "" &&
    localFilters.maxPricing === "";

  // Disable apply button if no filters are selected and nothing has changed
  const isApplyDisabled = !hasFiltersChanged && areAllFiltersEmpty;

  return (
    <ModalDrawer
      isOpen={isOpen}
      onClose={handleClose}
      title={t("drawers.filters.title")}
      size="md"
      position="right"
      id="price-management-filters-drawer"
      footer={
        <div className="flex justify-end gap-3">
          <Button
            id="price-management-filters-drawer-reset-btn"
            variant="outline"
            className="text-mw-primary-500 outline-mw-primary-500"
            size="md"
            onClick={handleClose}
          >
            {tCommon("buttons.cancel")}
          </Button>
          <Button
            id="price-management-filters-drawer-apply-btn"
            variant="primary"
            size="md"
            onClick={handleApplyFilters}
            disabled={isApplyDisabled}
          >
            {t("drawers.filters.apply_filters")}
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        {/* City Filter */}
        <div className="space-y-2">
          <div className="inline-flex justify-start items-center gap-1">
            <Label htmlFor="city-filter" required={false}>
              {t("drawers.filters.city")}
            </Label>
            <Info className="w-3.5 h-3.5 text-mw-neutral-400" />
          </div>
          <MultiSelect
            id="city-filter"
            options={cityOptions}
            value={localFilters.cities}
            onChange={(values) => handleFilterChange("cities", values)}
            placeholder={t("drawers.filters.select_cities")}
            maxVisibleChips={3}
            searchable={true}
            clearable={true}
          />
        </div>

        {/* Inventory Type Filter */}
        <div className="space-y-2">
          <div className="inline-flex justify-start items-center gap-1">
            <Label htmlFor="inventory-type-filter" required={false}>
              {t("drawers.filters.inventory_type")}
            </Label>
            <Info className="w-3.5 h-3.5 text-mw-neutral-400" />
          </div>
          <MultiSelect
            options={toOptions(INVENTORY_TYPES)}
            value={localFilters.inventoryTypes}
            onChange={(values) => handleFilterChange("inventoryTypes", values)}
            placeholder={t("drawers.filters.select_inventory_types")}
          />
        </div>

        {/* Media Owner Filter */}
        <div className="space-y-2">
          <div className="inline-flex justify-start items-center gap-1">
            <Label htmlFor="media-owner-filter" required={false}>
              {t("drawers.filters.media_owner")}
            </Label>
            <Info className="w-3.5 h-3.5 text-mw-neutral-400" />
          </div>
          <MediaOwnerDropdown
            id="media-owner-filter"
            value={localFilters.mediaOwners}
            onChange={(values) => handleFilterChange("mediaOwners", values)}
            placeholder={t("drawers.filters.select_media_owners")}
          />
        </div>

        {/* Min Pricing / Max Pricing */}
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="min-pricing-input" required={false}>
              {t("drawers.filters.min_pricing")}
            </Label>
            <Input
              id="min-pricing-input"
              type="number"
              placeholder="0"
              value={localFilters.minPricing}
              onChange={(e) =>
                handleFilterChange(
                  "minPricing",
                  e.target.value === "" ? "" : Number(e.target.value),
                )
              }
              min={0}
              step={1}
              onKeyDown={(e) => {
                // Prevent 'e', 'E', '+', '-' keys
                if (["e", "E", "+", "-"].includes(e.key)) {
                  e.preventDefault();
                }
              }}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="max-pricing-input" required={false}>
              {t("drawers.filters.max_pricing")}
            </Label>
            <Input
              id="max-pricing-input"
              type="number"
              placeholder="1000"
              value={localFilters.maxPricing}
              onChange={(e) =>
                handleFilterChange(
                  "maxPricing",
                  e.target.value === "" ? "" : Number(e.target.value),
                )
              }
              min={0}
              step={1}
              onKeyDown={(e) => {
                // Prevent 'e', 'E', '+', '-' keys
                if (["e", "E", "+", "-"].includes(e.key)) {
                  e.preventDefault();
                }
              }}
            />
          </div>
        </div>
      </div>
    </ModalDrawer>
  );
};
