import {
  HierarchicalTable,
  ColumnSort,
} from "@components/common/HierarchicalTable";
import PageHeader from "@components/PageHeader";
import { Button } from "@components/ui/Button";
import { Card, CardContent, CardHeader } from "@components/ui/card";
import { Dropdown, DropdownTrigger } from "@components/ui/Dropdown";
import { Modal } from "@components/ui/Modal";
import { Loading } from "@components/ui/Spinner";
import { StatusBadge } from "@components/ui/StatusBadge";
import { TablePagination } from "@components/ui/TablePagination";
import { useLazyGetCampaignQuery } from "@services/campaign/campaignSlice";
import {
  inventoryApi,
  useLazyGetCampaignSchedulePricesQuery,
} from "@services/inventory/inventorySlice";
import { useTranslate } from "@tolgee/react";
import { formatDisplayDateRange } from "@utils/dateUtils";
import { getScheduleTimeSlot } from "@utils/schedule.utils";
import storage from "@utils/storage";
import { clsx } from "clsx";
import {
  ArrowLeft,
  Check,
  ChevronLeft,
  ChevronRight,
  Gift,
  Info,
  MoreHorizontal,
  Percent,
  X,
} from "lucide-react";
import { useState, useEffect, useCallback, useMemo } from "react";
import { useDispatch } from "react-redux";
import { useNavigate, useParams } from "react-router-dom";

import { ApplyBonusDrawer } from "./ApplyBonusDrawer";
import { ApplyDiscountDrawer } from "./ApplyDiscountDrawer";
import { usePriceTableColumns } from "./columns/usePriceTableColumns";
import { InlinePriceEditProvider } from "./components/InlinePriceEditContext";
import { PriceHistoryDrawer } from "./components/PriceHistoryDrawer";
import MapAvailabilityView from "./MapAvailabilityView";
import {
  PriceManagementFiltersDrawer,
  PriceManagementFilters,
} from "./PriceManagementFiltersDrawer";
import {
  PriceManagementToolbar,
  PriceManagementViewType,
} from "./PriceManagementToolbar";
import { PricingSummaryDrawer } from "./PricingSummaryDrawer";
import { PendingPriceEdit, PendingPriceEdits } from "./types";
import { CampaignActionsDropdownContent } from "../components/CampaignActionsDropdownContent";

interface InfoContent {
  title: string;
  subtitle: string;
}

// Default hidden columns (columns that should be hidden by default)
const DEFAULT_HIDDEN_COLUMNS = [
  "mediaOwner",
  "impression",
  "bonusType",
  "discount",
  "monthlyRateCard",
  "weeklyRateCard",
  "dailyRate",
  "cpmRate",
  "cpsRate",
  "reach",
];

const COLUMN_VISIBILITY_STORAGE_KEY = "campaign-price-management-columns";

/**
 * Schedules come back named "Schedule 1", "Schedule 2"... The table shows them
 * as "Schedule #1". Only that default pattern is rewritten so a schedule the
 * user has named themselves is displayed verbatim.
 */
export const formatScheduleName = (
  name: string | undefined,
  index: number,
): string => {
  const fallback = `Schedule #${index + 1}`;
  if (!name) return fallback;

  const defaultPattern = /^schedule\s*#?\s*(\d+)$/i.exec(name.trim());
  return defaultPattern ? `Schedule #${defaultPattern[1]}` : name;
};

const DEFAULT_FILTERS: PriceManagementFilters = {
  cities: [],
  inventoryTypes: [],
  mediaOwners: [],
  minPricing: "",
  maxPricing: "",
};

const CampaignPriceManagement: React.FC = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { campaignId } = useParams<{ campaignId: string }>();
  const { t, isLoading: isPriceNsLoading } = useTranslate(["price"]);
  const { t: tCommon, isLoading: isCommonNsLoading } = useTranslate(["common"]);
  const { t: tCampaigns, isLoading: isCampaignsNsLoading } = useTranslate([
    "campaigns",
  ]);
  // Namespaces load async (see src/config/tolgee.ts). Before they're ready,
  // t() returns the raw key string (fallbackNs is ""), so this page must not
  // render its labels until all three are loaded - otherwise every mount
  // briefly shows literal keys like "table.columns.inventory_name".
  const areTranslationsLoading =
    isPriceNsLoading || isCommonNsLoading || isCampaignsNsLoading;

  const infoContents: InfoContent[] = [
    {
      title: t("info.select_multiple_inventories.title"),
      subtitle: t("info.select_multiple_inventories.subtitle"),
    },
    {
      title: t("info.customize_view.title"),
      subtitle: t("info.customize_view.subtitle"),
    },
    {
      title: t("info.campaign_approval_workflow.title"),
      subtitle: t("info.campaign_approval_workflow.subtitle"),
    },
    {
      title: t("info.understand_price_updates.title"),
      subtitle: t("info.understand_price_updates.subtitle"),
    },
  ];

  const BONUS_TYPE_OPTIONS = useMemo(
    () => [
      {
        value: "value_added",
        label: t("drawers.apply_bonus.options.value_added"),
      },
      {
        value: "volume_bonus",
        label: t("drawers.apply_bonus.options.volume_bonus"),
      },
      {
        value: "seasonal_bonus",
        label: t("drawers.apply_bonus.options.seasonal_bonus"),
      },
      { value: "make_good", label: t("drawers.apply_bonus.options.make_good") },
      { value: "none", label: t("drawers.apply_bonus.options.none") },
    ],
    [t],
  );

  const [currentIndex, setCurrentIndex] = useState(0);
  // searchInput is the raw, controlled input value (updates every keystroke).
  // searchTerm is what's actually sent to the API - it only changes on Enter,
  // so typing alone never triggers a request.
  const [searchInput, setSearchInput] = useState("");
  const [searchTerm, setSearchTerm] = useState("");
  const [viewType, setViewType] = useState<PriceManagementViewType>("grid");

  // Table state
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [sortState, setSortState] = useState<ColumnSort[]>([]);
  const [selectedItems, setSelectedItems] = useState<Set<string>>(new Set());
  const [expandedItems, setExpandedItems] = useState<Set<string>>(new Set());

  // Drawer state
  const [isDiscountDrawerOpen, setIsDiscountDrawerOpen] = useState(false);
  const [isBonusDrawerOpen, setIsBonusDrawerOpen] = useState(false);
  const [isFiltersDrawerOpen, setIsFiltersDrawerOpen] = useState(false);
  const [isSummaryDrawerOpen, setIsSummaryDrawerOpen] = useState(false);
  const [isColumnsDrawerOpen, setIsColumnsDrawerOpen] = useState(false);
  const [isHistoryDrawerOpen, setIsHistoryDrawerOpen] = useState(false);
  const [isLeaveConfirmOpen, setIsLeaveConfirmOpen] = useState(false);

  const [filters, setFilters] =
    useState<PriceManagementFilters>(DEFAULT_FILTERS);

  // Unsaved inline proposed-price edits. Editing a cell only stages a change
  // here; nothing is persisted until the user saves from the summary drawer.
  const [pendingPriceEdits, setPendingPriceEdits] = useState<PendingPriceEdits>(
    {},
  );
  const hasPendingPriceEdits = Object.keys(pendingPriceEdits).length > 0;

  const handleDraftPriceChange = useCallback(
    (rowKey: string, edit: PendingPriceEdit) => {
      setPendingPriceEdits((prev) => ({ ...prev, [rowKey]: edit }));
    },
    [],
  );

  // A direct edit on an inventory row stages an authoritative override for it
  // (see usePriceTableColumns). Editing one of its schedules afterwards must
  // drop that override so the inventory goes back to showing the sum of its
  // schedules - otherwise it keeps displaying the stale pre-schedule-edit
  // value.
  const handleDiscardDraftRow = useCallback((rowKey: string) => {
    setPendingPriceEdits((prev) => {
      if (!(rowKey in prev)) return prev;
      const { [rowKey]: _removed, ...rest } = prev;
      return rest;
    });
  }, []);

  // Cancel/close on the summary drawer discards any unsaved price edits,
  // same as it already does for unsaved custom-fee edits.
  const handleDiscardPendingPriceEdits = useCallback(() => {
    setPendingPriceEdits({});
  }, []);

  // Called once the summary drawer has persisted the staged edits.
  const handlePendingPriceEditsSaved = useCallback(() => {
    setPendingPriceEdits({});
  }, []);

  // API hooks
  const [
    fetchCampaignSchedulePrices,
    { isLoading, isFetching, data: priceData },
  ] = useLazyGetCampaignSchedulePricesQuery();
  const [getCampaign, { data: campaignData }] = useLazyGetCampaignQuery();

  const loadInitialHiddenColumns = useCallback((): string[] => {
    try {
      const stored = storage.getItem(COLUMN_VISIBILITY_STORAGE_KEY);
      if (stored) {
        const parsed = JSON.parse(stored) as { hiddenColumns?: string[] };
        if (parsed.hiddenColumns && Array.isArray(parsed.hiddenColumns)) {
          return parsed.hiddenColumns;
        }
      }
    } catch (error) {
      console.error("Error loading column visibility from storage:", error);
    }
    return DEFAULT_HIDDEN_COLUMNS;
  }, []);

  const [hiddenColumns, setHiddenColumns] = useState<string[]>(() =>
    loadInitialHiddenColumns(),
  );

  // Handle column visibility change
  const handleColumnVisibilityChange = useCallback(
    (newHiddenColumns: string[]) => {
      setHiddenColumns(newHiddenColumns);
      try {
        const currentState = storage.getItem(COLUMN_VISIBILITY_STORAGE_KEY);
        const parsed = currentState
          ? (JSON.parse(currentState) as Record<string, unknown>)
          : {};
        storage.setItem(
          COLUMN_VISIBILITY_STORAGE_KEY,
          JSON.stringify({ ...parsed, hiddenColumns: newHiddenColumns }),
        );
      } catch (error) {
        console.error("Error saving column visibility to storage:", error);
      }
    },
    [],
  );

  const handlePrevious = () => {
    setCurrentIndex((prev) => (prev > 0 ? prev - 1 : infoContents.length - 1));
  };

  const handleNext = () => {
    setCurrentIndex((prev) => (prev < infoContents.length - 1 ? prev + 1 : 0));
  };

  const handleSearchChange = (value: string) => {
    setSearchInput(value);
  };

  const handleSearchSubmit = () => {
    setSearchTerm(searchInput);
    setCurrentPage(0); // Reset to first page for a new search
  };

  const handleViewChange = (view: "grid" | "mapView" | "calender") => {
    setViewType(view);
    // View change implementation will be added later
  };

  // Count active filters for badge display
  const getActiveFilterCount = useMemo(() => {
    let count = 0;
    if (filters.cities.length > 0) count++;
    if (filters.inventoryTypes.length > 0) count++;
    if (filters.mediaOwners.length > 0) count++;
    if (filters.minPricing !== "" || filters.maxPricing !== "") count++;
    return count;
  }, [filters]);

  // Load price management data
  const loadPriceData = useCallback(() => {
    if (!campaignId) return;

    const sortBy = sortState.length > 0 ? sortState[0].key : "name";
    const sortDir = sortState.length > 0 ? sortState[0].direction : "asc";

    // Prepare filters for API
    const apiFilters: {
      name?: string;
      cities?: string[];
      inventoryTypes?: string[];
      mediaOwnerIds?: string[];
      minPricing?: number;
      maxPricing?: number;
    } = {};

    const nameToUse = searchTerm.trim();
    if (nameToUse) {
      apiFilters.name = nameToUse;
    }

    if (filters.cities.length > 0) {
      apiFilters.cities = filters.cities;
    }
    if (filters.inventoryTypes.length > 0) {
      apiFilters.inventoryTypes = filters.inventoryTypes;
    }
    if (filters.mediaOwners.length > 0) {
      apiFilters.mediaOwnerIds = filters.mediaOwners;
    }
    if (filters.minPricing !== "" && typeof filters.minPricing === "number") {
      apiFilters.minPricing = filters.minPricing;
    }
    if (filters.maxPricing !== "" && typeof filters.maxPricing === "number") {
      apiFilters.maxPricing = filters.maxPricing;
    }

    fetchCampaignSchedulePrices({
      campaignId,
      params: {
        page: currentPage,
        size: pageSize,
        sortBy,
        sortDir: sortDir.toUpperCase() as "ASC" | "DESC",
        filters: Object.keys(apiFilters).length > 0 ? apiFilters : undefined,
      },
    });
    getCampaign(campaignId);
  }, [
    campaignId,
    currentPage,
    pageSize,
    sortState,
    filters,
    searchTerm,
    fetchCampaignSchedulePrices,
    getCampaign,
  ]);

  const columns = usePriceTableColumns({
    currency: campaignData?.data?.currency || "",
    pendingEdits: pendingPriceEdits,
    onDraftChange: handleDraftPriceChange,
    onDiscardRow: handleDiscardDraftRow,
  });

  // Load campaign data on mount
  useEffect(() => {
    if (campaignId) {
      getCampaign(campaignId);
    }
  }, [campaignId, getCampaign]);

  useEffect(() => {
    loadPriceData();
  }, [loadPriceData]);

  useEffect(() => {
    return () => {
      dispatch(inventoryApi.util.invalidateTags(["Inventory"]));
    };
  }, [dispatch]);

  // Warn on tab close/refresh/browser back when there are unsaved price
  // edits - the in-app back button is guarded separately below.
  useEffect(() => {
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      if (!hasPendingPriceEdits) return;
      e.preventDefault();
    };
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [hasPendingPriceEdits]);

  // Handle expansion
  const handleExpansionChange = useCallback((expanded: Set<string>) => {
    setExpandedItems(expanded);
  }, []);

  // Transform price data to hierarchical format
  const tableData = useMemo(() => {
    if (!priceData?.data?.content) return [];

    return priceData.data.content.map((item) => {
      const inventoryId = item.inventoryId;
      const schedules = item.schedules || [];

      const dateRange = formatDisplayDateRange(
        item.startDate,
        item.endDate,
        tCommon,
      );

      const schedulesWithBonus = schedules.filter((sch) => sch.bonusType);

      let bonusCombined: string;
      if (schedulesWithBonus.length > 1) {
        bonusCombined = t("table.multiple");
      } else if (schedulesWithBonus.length === 1) {
        bonusCombined = t("table.single");
      } else {
        bonusCombined = "--";
      }

      // Roll the schedule time slots up to the inventory row: a single distinct
      // slot is shown as-is, several become "Multiple", none falls back to the
      // inventory-level timeslot.
      const scheduleTimeSlots = Array.from(
        new Set(
          schedules
            .map((schedule) => getScheduleTimeSlot(schedule.bookingMatrix))
            .filter((slot) => slot && slot !== "--"),
        ),
      );

      let timeSlot: string;
      if (scheduleTimeSlots.length > 1) {
        timeSlot = t("table.multiple");
      } else if (scheduleTimeSlots.length === 1) {
        timeSlot = scheduleTimeSlots[0];
      } else if (item.timeslot) {
        timeSlot = `${item.timeslot.startTime} - ${item.timeslot.endTime}`;
      } else {
        timeSlot = "--";
      }

      return {
        id: inventoryId,
        inventoryName: item.inventoryName,
        // Cinema buy attributes (operator · hall · showtime), rendered as a
        // secondary line under the inventory name. Undefined for non-cinema.
        cinemaFields: item.cinemaFields,
        dateRange,
        timeSlot,
        sov: item.sov || 0,
        adPlays: item.adPlays || 0,
        currentRate: item.currentRate || 0,
        proposedRate: item.proposedRate || 0,
        // New columns (hidden by default)
        mediaOwner: item.mediaOwnerName || "--",
        impression: item.adPlays || 0,
        bonusType: bonusCombined,
        discount: item.discountPercent || 0, // Not available in current structure
        monthlyRateCard: item.monthlyRateCard || 0,
        weeklyRateCard: item.weeklyRateCard || 0,
        dailyRate: item.dailyRate || 0,
        cpmRate: item.cpmRate || 0,
        cpsRate: 0, // Not available in current structure
        reach: item.reach, // Not available in current structure
        campaignInventoryScheduleId: item.id,
        // Children (schedules) - use schedules from API response
        children: schedules.map((schedule, schIndex) => ({
          id: schedule.id || `schedule-${schIndex}`,
          parentId: inventoryId,
          inventoryName: formatScheduleName(schedule.name, schIndex),
          dateRange: formatDisplayDateRange(
            schedule.startDate,
            schedule.endDate,
            tCommon,
          ),
          timeSlot: getScheduleTimeSlot(schedule.bookingMatrix),
          sov: schedule.sov || 0,
          adPlays: schedule.adPlays || 0,
          currentRate: schedule.currentRate || 0,
          proposedRate: schedule.proposedRate || 0,
          originalSchedule: schedule,
          campaignInventoryScheduleId: item.id,
          discount: schedule.discount ?? null, // Not available in current structure
          bonusType: schedule.bonusType
            ? BONUS_TYPE_OPTIONS.find((opt) => opt.value === schedule.bonusType)
                ?.label || "--"
            : "--", // Not available in current structure
        })),
      };
    });
  }, [priceData, t, tCommon, BONUS_TYPE_OPTIONS]);

  const currentContent = infoContents[currentIndex];

  // Count parent items (inventories) that are either:
  // 1. Directly selected, OR
  // 2. Have at least one child selected
  const selectedParentCount = useMemo(() => {
    const selectedParentIds = new Set<string>();

    // Get all directly selected parent IDs
    Array.from(selectedItems).forEach((id) => {
      if (!id.includes(":")) {
        selectedParentIds.add(id);
      } else {
        // Extract parent ID from child selection (format: "parentId:childId")
        const parentId = id.split(":")[0];
        selectedParentIds.add(parentId);
      }
    });

    return selectedParentIds.size;
  }, [selectedItems]);

  // Leaving with unsaved inline price edits discards them - confirm first,
  // same wording the summary drawer used to show on its own close.
  const handleBack = () => {
    if (hasPendingPriceEdits) {
      setIsLeaveConfirmOpen(true);
      return;
    }
    navigate("/campaigns");
  };

  const handleConfirmLeave = () => {
    handleDiscardPendingPriceEdits();
    setIsLeaveConfirmOpen(false);
    navigate("/campaigns");
  };

  const handleClearAll = () => {
    setSelectedItems(new Set());
  };

  const handleApplyFilters = (newFilters: PriceManagementFilters) => {
    setFilters(newFilters);
    setCurrentPage(0); // Reset to first page when filters change
  };

  const handleClearFilters = () => {
    setFilters(DEFAULT_FILTERS);
    setCurrentPage(0); // Reset to first page when filters are cleared
  };

  const backButton = (
    <div onClick={handleBack}>
      <ArrowLeft className="w-5 h-5" cursor="pointer" />
    </div>
  );

  // Only blank the table to a full skeleton when there is nothing to show yet
  // (first load, or a search/filter/sort that has no cached rows for its new
  // params). Once rows exist, a refetch (isFetching) dims them and shows a
  // small overlay spinner instead of wiping the table - the user's scroll
  // position, selection, and expanded rows all stay visible.
  const hasPriceData = Boolean(priceData?.data);
  const showTableSkeleton = isLoading || (isFetching && !hasPriceData);
  const isRefreshingWithData = isFetching && hasPriceData;

  if (areTranslationsLoading) {
    return (
      <div
        id="campaign-price-management-page"
        className="flex h-full w-full items-center justify-center"
      >
        <Loading size="lg" variant="primary" text="" />
      </div>
    );
  }

  return (
    <div
      id="campaign-price-management-page"
      className="h-full w-full flex flex-col overflow-hidden"
    >
      <PageHeader
        title={campaignData?.data?.name || ""}
        description={
          <div className="inline-flex items-center gap-2">
            {campaignData?.data?.planNumber && (
              <p>
                {t("common.id")} {campaignData.data.planNumber}
              </p>
            )}
            {campaignData?.data?.status && (
              <StatusBadge status={campaignData.data.status.toLowerCase()}>
                {tCampaigns(
                  `campaignsList.status.${campaignData.data.status}`,
                ) || campaignData.data.status}
              </StatusBadge>
            )}
          </div>
        }
        actions={
          <>
            <Dropdown>
              <DropdownTrigger asChild>
                <Button
                  variant="outline"
                  size="iconMd"
                  className="outline-mw-primary-500 text-mw-primary-500"
                >
                  <MoreHorizontal className="h-4 w-4" />
                </Button>
              </DropdownTrigger>
              <CampaignActionsDropdownContent
                campaignId={campaignId || ""}
                campaignData={{
                  status: campaignData?.data?.status || "",
                }}
                handlers={{
                  onRefresh: () => {
                    if (campaignId) {
                      const sortBy = sortState[0]?.key || "name";
                      const sortDir = sortState[0]?.direction || "asc";
                      fetchCampaignSchedulePrices({
                        campaignId,
                        params: {
                          page: currentPage,
                          size: pageSize,
                          sortBy,
                          sortDir: sortDir.toUpperCase() as "ASC" | "DESC",
                        },
                      });
                    }
                  },
                }}
                hideNavigation={["priceManagement"]}
              />
            </Dropdown>
          </>
        }
        leftAction={backButton}
      />
      <div className="p-4">
        <Card
          className={clsx(
            "p-4 border",
            selectedParentCount > 0
              ? "border-mw-purple-warning-200 bg-mw-purple-warning-50!"
              : "border-mw-info-200 bg-mw-info-50!",
          )}
        >
          {selectedParentCount > 0 ? (
            <CardContent className="relative w-full p-0!">
              <Info className="absolute left-0 top-0 h-4 w-4 text-mw-purple-warning-600" />
              <div className="flex items-center justify-between w-full pl-7">
                <div>
                  <h5 className="mb-1 font-medium leading-none tracking-tight text-mw-purple-warning-900">
                    {selectedParentCount}{" "}
                    {selectedParentCount === 1
                      ? t("selection.inventory_selected")
                      : t("selection.inventories_selected")}
                  </h5>
                  <div className="text-sm text-mw-purple-warning-800">
                    {hasPendingPriceEdits
                      ? t("actions.pending_price_edits_blocked")
                      : t("selection.apply_actions")}
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-9 rounded-md px-3 border border-mw-purple-warning-300 bg-white text-mw-purple-warning-700 hover:bg-mw-purple-warning-100"
                    onClick={handleClearAll}
                    data-testid="button-clear-selection-action"
                  >
                    <X className="h-4 w-4 mr-1" />
                    {t("actions.clear_all")}
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-9 rounded-md px-3 border border-mw-purple-warning-300 bg-white text-mw-purple-warning-700 hover:bg-mw-purple-warning-100"
                    onClick={() => setIsDiscountDrawerOpen(true)}
                    disabled={hasPendingPriceEdits}
                    title={
                      hasPendingPriceEdits
                        ? t("actions.pending_price_edits_blocked")
                        : undefined
                    }
                    data-testid="button-apply-discount-action"
                  >
                    <Percent className="h-4 w-4 mr-1" />
                    {t("actions.apply_discount")}
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-9 rounded-md px-3 border border-mw-purple-warning-300 bg-white text-mw-purple-warning-700 hover:bg-mw-purple-warning-100"
                    onClick={() => setIsBonusDrawerOpen(true)}
                    disabled={hasPendingPriceEdits}
                    title={
                      hasPendingPriceEdits
                        ? t("actions.pending_price_edits_blocked")
                        : undefined
                    }
                    data-testid="button-apply-bonus-action"
                  >
                    <Gift className="h-4 w-4 mr-1" />
                    {t("actions.apply_bonus")}
                  </Button>
                </div>
              </div>
            </CardContent>
          ) : (
            <CardContent className="flex items-center justify-between w-full p-0!">
              <div className="flex-1 mr-4">
                <div className="flex items-center gap-2 mb-1">
                  <Check className="h-5 w-5 text-mw-info-600" />
                  <h5 className="font-medium leading-none tracking-tight text-mw-info-900 mb-0">
                    {currentContent.title}
                  </h5>
                </div>
                <div className="text-sm text-mw-info-800">
                  {currentContent.subtitle}
                </div>
              </div>
              <div className="flex items-center gap-3">
                <div className="flex items-center gap-1">
                  {infoContents.map((_, index) => (
                    <button
                      key={index}
                      type="button"
                      onClick={() => setCurrentIndex(index)}
                      aria-label={t("info.go_to_tip", {
                        defaultValue: `Go to tip ${index + 1}`,
                        index: index + 1,
                      })}
                      data-testid={`button-carousel-dot-${index + 1}`}
                      className={clsx(
                        "h-2 rounded-full transition-all",
                        index === currentIndex
                          ? "bg-mw-info-600 w-6"
                          : "bg-mw-info-300 hover:bg-mw-info-400 w-2",
                      )}
                    />
                  ))}
                </div>
                <div className="flex items-center gap-1">
                  <Button
                    variant="ghost"
                    size="iconMd"
                    className="h-8 w-8 text-mw-info-600 hover:text-mw-info-700 hover:bg-mw-info-100"
                    aria-label={t("info.previous_tip", {
                      defaultValue: "Previous tip",
                    })}
                    data-testid="button-carousel-prev"
                    onClick={handlePrevious}
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="iconMd"
                    className="h-8 w-8 text-mw-info-600 hover:text-mw-info-700 hover:bg-mw-info-100"
                    aria-label={t("info.next_tip", {
                      defaultValue: "Next tip",
                    })}
                    data-testid="button-carousel-next"
                    onClick={handleNext}
                  >
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
                <span className="text-xs text-mw-info-600 font-medium min-w-[3rem] text-center">
                  {currentIndex + 1}/{infoContents.length}
                </span>
              </div>
            </CardContent>
          )}
        </Card>
      </div>

      <div className="flex-1 w-full overflow-hidden dark:bg-mw-neutral-900 px-4 pb-4">
        <Card className="flex flex-col h-full">
          <CardHeader id="campaigns-card-header" className="p-4">
            <PriceManagementToolbar
              searchValue={searchInput}
              onSearchChange={handleSearchChange}
              onSearchSubmit={handleSearchSubmit}
              viewType={viewType}
              onViewChange={handleViewChange}
              activeFilterCount={getActiveFilterCount}
              onOpenFilters={() => setIsFiltersDrawerOpen(true)}
              onOpenColumns={() => setIsColumnsDrawerOpen(true)}
              onOpenSummary={() => setIsSummaryDrawerOpen(true)}
              onOpenHistory={() => setIsHistoryDrawerOpen(true)}
              disabled={isFetching}
            />
          </CardHeader>
          <CardContent className="flex-1 min-w-0 overflow-hidden">
            {viewType === "grid" ? (
              <div className="h-full min-w-0 flex flex-col">
                {/* min-w-0 on the flex chain: a flex child defaults to
                    min-width:auto, which makes it refuse to shrink below its
                    content. Without it the wide table pushes the card out and
                    CardContent's overflow-hidden clips the right-hand columns
                    instead of this container scrolling them. */}
                <div
                  id="campaigns-price-table-container"
                  className="relative flex-1 min-w-0 overflow-auto scrollbar-thin scrollbar-thumb-mw-neutral-300 scrollbar-track-transparent"
                >
                  {isRefreshingWithData && (
                    <Loading overlay size="md" variant="primary" text="" />
                  )}
                  <InlinePriceEditProvider>
                    <HierarchicalTable
                      className={clsx(
                        "min-h-[calc(100vh-450px)]",
                        isRefreshingWithData &&
                          "pointer-events-none opacity-50 transition-opacity",
                      )}
                      data={tableData}
                      columns={columns}
                      selection={{
                        enabled: true,
                        selectedItems,
                        columnPosition: 0,
                        onSelectionChange: setSelectedItems,
                        getItemId: (row) => {
                          if ("parentId" in row) {
                            return `${row.parentId}:${row.id}`;
                          }
                          return row.id;
                        },
                      }}
                      expansion={{
                        enabled: true,
                        expandedItems,
                        columnPosition: 1,
                        onExpansionChange: handleExpansionChange,
                        getItemId: (row) => row.id,
                        // Schedules share the inventory columns and render inline,
                        // so no nested header row is needed.
                        showChildrenHeaders: false,
                      }}
                      sorting={{
                        mode: "remote",
                        sortState,
                        onSortChange: (newSortState) => {
                          setSortState(newSortState);
                          setCurrentPage(0); // Reset to first page on sort change
                        },
                      }}
                      loading={showTableSkeleton}
                      skeletonRowsCount={pageSize}
                      emptyMessage={t("table.empty_message")}
                      density="comfortable"
                      parentRowClassName="bg-mw-neutral-50 dark:bg-mw-neutral-800"
                      hiddenColumns={hiddenColumns}
                      onColumnVisibilityChange={handleColumnVisibilityChange}
                      columnVisibilityDrawer={{
                        enabled: true,
                        storageKey: COLUMN_VISIBILITY_STORAGE_KEY,
                        hideHeaderTrigger: true,
                        isOpen: isColumnsDrawerOpen,
                        onOpenChange: setIsColumnsDrawerOpen,
                      }}
                      persistence={{
                        enabled: true,
                        storageKey: "campaign-price-management-table-state",
                      }}
                    />
                  </InlinePriceEditProvider>
                </div>
              </div>
            ) : (
              <MapAvailabilityView
                isMapView={viewType === "mapView"}
                campaignId={campaignId}
                campaignCurrency={campaignData?.data?.currency}
                campaignData={campaignData?.data}
              />
            )}
          </CardContent>
          {priceData?.data && viewType === "grid" && (
            <div
              id="campaigns-pagination-footer"
              className="flex justify-end border-t border-container-border p-4"
            >
              <TablePagination
                currentPage={currentPage + 1}
                totalPages={priceData.data.totalPages}
                pageSize={pageSize}
                totalItems={priceData.data.totalElements}
                onPageChange={(page) => setCurrentPage(page - 1)}
                onPageSizeChange={(size) => {
                  setPageSize(size);
                  setCurrentPage(0);
                }}
              />
            </div>
          )}
        </Card>
      </div>

      {/* Drawers */}
      <ApplyDiscountDrawer
        isOpen={isDiscountDrawerOpen}
        onClose={() => setIsDiscountDrawerOpen(false)}
        selectedCount={selectedParentCount}
        campaignId={campaignId}
        selectedItems={selectedItems}
        tableData={tableData}
        onSuccess={() => {
          loadPriceData();
          setSelectedItems(new Set());
        }}
      />
      <ApplyBonusDrawer
        isOpen={isBonusDrawerOpen}
        onClose={() => setIsBonusDrawerOpen(false)}
        selectedCount={selectedParentCount}
        campaignId={campaignId}
        selectedItems={selectedItems}
        tableData={tableData}
        onSuccess={() => {
          loadPriceData();
          setSelectedItems(new Set());
        }}
        bonusTypeOptions={BONUS_TYPE_OPTIONS}
      />
      <PriceManagementFiltersDrawer
        isOpen={isFiltersDrawerOpen}
        onClose={() => setIsFiltersDrawerOpen(false)}
        filters={filters}
        onApplyFilters={handleApplyFilters}
        onClearFilters={handleClearFilters}
      />
      <PriceHistoryDrawer
        isOpen={isHistoryDrawerOpen}
        onClose={() => setIsHistoryDrawerOpen(false)}
        campaignId={campaignId}
        currency={campaignData?.data?.currency}
      />
      <PricingSummaryDrawer
        isOpen={isSummaryDrawerOpen}
        onClose={() => setIsSummaryDrawerOpen(false)}
        currency={campaignData?.data?.currency}
        campaignId={campaignId}
        onSuccess={loadPriceData}
        pendingPriceEdits={pendingPriceEdits}
        onPriceEditsSaved={handlePendingPriceEditsSaved}
      />
      <Modal
        isOpen={isLeaveConfirmOpen}
        onClose={() => setIsLeaveConfirmOpen(false)}
        title={t("drawers.pricing_summary.discard_changes_title")}
        primaryButtonText={t("drawers.pricing_summary.discard_changes_confirm")}
        primaryButtonVariant="danger"
        onPrimaryAction={handleConfirmLeave}
        secondaryButtonText={tCommon("buttons.cancel")}
        onSecondaryAction={() => setIsLeaveConfirmOpen(false)}
      >
        <p className="text-sm text-mw-neutral-600 dark:text-mw-neutral-300">
          {Object.keys(pendingPriceEdits).length}{" "}
          {t("drawers.pricing_summary.discard_changes_body")}
        </p>
      </Modal>
    </div>
  );
};

export default CampaignPriceManagement;
