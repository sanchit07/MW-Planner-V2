import { RootState } from "@store";
import { useTranslate } from "@tolgee/react";
import { clsx } from "clsx";
import {
  SlidersHorizontal,
  Grid2X2,
  Rows3,
  Search,
  Columns3Cog,
  Archive,
  Trash2,
  X,
} from "lucide-react";
import React, { useState, useRef, useEffect, useCallback } from "react";
import { useDispatch, useSelector } from "react-redux";
import { useNavigate, useLocation } from "react-router-dom";

import CampaignFilterModal, {
  FilterValues,
} from "./common/CampaignFilterModal";
import { CampaignsGridView } from "./components/CampaignsGridView";
import { CampaignsListView } from "./components/CampaignsListView";
import { useDataSync } from "./hooks/useDataSync";
import { usePermissions } from "../../hooks/usePermissions";
import PageHeader from "../../components/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card, CardContent, CardHeader } from "../../components/ui/card";
import { Input } from "../../components/ui/Input";
import { Modal } from "../../components/ui/Modal";
// import { TablePagination } from "../../components/ui/TablePagination";
import {
  campaignApi,
  useBulkActionsCampaignMutation,
} from "../../services/campaign/campaignSlice";
import {
  setViewType,
  setSearchQuery,
  setFilters,
  setFilterModalOpen,
  clearSelection,
  selectCampaignsUI,
  setColumnCustomizationOpen,
  setPaginationPage,
  setVirtualScrollOffset,
} from "../../services/campaign/campaignsUISlice";
import { useAppSelector } from "../../store";

const CampaignsPage: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { t: tCommon } = useTranslate(["common"]);
  const { canCreatePlans } = usePermissions();
  const uiState = useSelector((state: RootState) => selectCampaignsUI(state));

  // Use data sync hook
  const {
    data,
    isLoading,
    isFetching,
    paginationInfo,
    handlePageChange,
    handlePageSizeChange,
    handleVirtualScrollLoadMore,
    querySignature,
    refetch,
  } = useDataSync();

  // "Reload everything from page 1": resets both view types' pagination back
  // to the start, then (once that reset has actually landed in state and the
  // effect below sees it) force-refetches and tells the grid to drop
  // everything it accumulated. Used whenever list-affecting data may have
  // changed elsewhere - archiving/deleting a plan, creating a new one and
  // navigating back, etc. - so the freshest data is always what's shown,
  // starting from the top, rather than whatever page the user happened to
  // be on.
  //
  // `reloadRequestId` (rather than a plain boolean) lets overlapping
  // requests coalesce correctly: if a second reload is requested while the
  // first's refetch is still in flight, the effect's cleanup cancels the
  // first's continuation so it can't clobber the second one's fresher data.
  const [reloadRequestId, setReloadRequestId] = useState(0);
  const handleFullReload = useCallback(() => {
    dispatch(setPaginationPage(1));
    dispatch(setVirtualScrollOffset(0));
    setReloadRequestId((n) => n + 1);
  }, [dispatch]);

  const [gridRefreshNonce, setGridRefreshNonce] = useState(0);
  useEffect(() => {
    if (reloadRequestId === 0) return;
    // Runs after the pagination reset above has committed, so `refetch()`
    // here is bound to the page-1 query - awaiting it before bumping the
    // grid's nonce means the grid never sees "reload" with stale data still
    // attached (see the CampaignsGridView race this used to hit).
    let cancelled = false;
    (async () => {
      await refetch();
      if (cancelled) return;
      setGridRefreshNonce((n) => n + 1);
    })();
    return () => {
      cancelled = true;
    };
  }, [reloadRequestId, refetch]);
  const gridQuerySignature = `${querySignature}::${gridRefreshNonce}`;

  const location = useLocation();
  useEffect(() => {
    const currentPath = location.pathname;
    if (currentPath === "/campaigns") {
      handleFullReload();
    }
  }, [location.pathname, handleFullReload]);

  // Local search input state for immediate UI update (debounced)
  const [searchInput, setSearchInput] = useState(uiState.searchQuery);
  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Sync local search input with Redux state
  useEffect(() => {
    setSearchInput(uiState.searchQuery);
  }, [uiState.searchQuery]);

  // Debounced search effect
  useEffect(() => {
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
    }

    debounceTimerRef.current = setTimeout(() => {
      const trimmedValue = searchInput.trim();
      if (trimmedValue === "" || trimmedValue.length >= 3) {
        dispatch(setSearchQuery(trimmedValue === "" ? "" : trimmedValue));
        // Refetch with page 0 and clear other page cache (works in both list and grid view)
        dispatch(campaignApi.util.invalidateTags(["CampaignsList"]));
      } else {
        dispatch(setSearchQuery(""));
        dispatch(campaignApi.util.invalidateTags(["CampaignsList"]));
      }
    }, 500);

    return () => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
    };
  }, [searchInput, dispatch]);

  // Handlers
  const handleNewCampaign = () => {
    navigate("/campaigns/create");
  };

  const handleViewChange = (view: "list" | "grid") => {
    dispatch(setViewType(view));
    // Selection is kept intact as per requirements
  };

  const handleSearchChange = (value: string) => {
    setSearchInput(value);
    dispatch(clearSelection());
  };

  const handleApplyFilters = (filterValues: FilterValues) => {
    dispatch(setFilters(filterValues));
    dispatch(clearSelection());
  };

  // ── Bulk delete ──────────────────────────────────────────────────────────────
  const profile = useAppSelector((s) => s.profile.profile);
  const [bulkActionsCampaign] = useBulkActionsCampaignMutation();
  const [bulkDeleteConfirmOpen, setBulkDeleteConfirmOpen] = useState(false);
  const [bulkDeleteBlockedNames, setBulkDeleteBlockedNames] = useState<
    string[]
  >([]);
  const [isBulkDeleting, setIsBulkDeleting] = useState(false);

  const isAdmin = profile?.current_company?.role_name === "Administrator";

  const canDelete = (campaignStatus: string, createdByUsername: string) => {
    const s = campaignStatus.toLowerCase();
    const isDeletableStatus =
      s === "draft" || s === "planned" || s === "archived";
    const isOwner =
      !createdByUsername || profile?.username === createdByUsername;
    return isDeletableStatus && (isAdmin || isOwner);
  };

  const handleBulkDeleteClick = () => {
    const selected = data.filter((c) => uiState.selectedItems.includes(c.id));
    const blocked = selected.filter((c) => !canDelete(c.status, c.userName));
    if (blocked.length > 0) {
      setBulkDeleteBlockedNames(blocked.map((c) => c.campaignName));
    } else {
      setBulkDeleteConfirmOpen(true);
    }
  };

  const handleBulkDeleteConfirm = async () => {
    setIsBulkDeleting(true);
    await bulkActionsCampaign({
      campaignIds: uiState.selectedItems,
      action: "DELETE",
    }).unwrap();
    setIsBulkDeleting(false);
    setBulkDeleteConfirmOpen(false);
    dispatch(clearSelection());
    handleFullReload();
  };

  // ── Bulk archive ─────────────────────────────────────────────────────────────
  const [bulkArchiveConfirmOpen, setBulkArchiveConfirmOpen] = useState(false);
  const [bulkArchiveBlockedNames, setBulkArchiveBlockedNames] = useState<
    string[]
  >([]);
  const [isBulkArchiving, setIsBulkArchiving] = useState(false);

  const canArchive = (campaignStatus: string) =>
    campaignStatus.toLowerCase() !== "completed";

  const handleBulkArchiveClick = () => {
    const selected = data.filter((c) => uiState.selectedItems.includes(c.id));
    const blocked = selected.filter((c) => !canArchive(c.status));
    if (blocked.length > 0) {
      setBulkArchiveBlockedNames(blocked.map((c) => c.campaignName));
    } else {
      setBulkArchiveConfirmOpen(true);
    }
  };

  const handleBulkArchiveConfirm = async () => {
    setIsBulkArchiving(true);
    await bulkActionsCampaign({
      campaignIds: uiState.selectedItems,
      action: "ARCHIVE",
    }).unwrap();
    setIsBulkArchiving(false);
    setBulkArchiveConfirmOpen(false);
    dispatch(clearSelection());
    handleFullReload();
  };

  // Count active filters for badge display
  const getActiveFilterCount = () => {
    const { filters } = uiState;
    let count = 0;
    if (filters.status.length > 0) count++;
    if (filters.userName.length > 0) count++;
    if (filters.period?.from && filters.period?.to) count++;
    if (filters.campaignGoal.length > 0) count++;
    return count;
  };

  return (
    <div id="campaigns-page" className="h-full w-full flex flex-col">
      <PageHeader
        title={tCampaigns("title")}
        descriptionKey={tCampaigns("description")}
        actions={
          canCreatePlans ? (
            <Button
              id="campaigns-page-new-campaign-btn"
              variant="primary"
              size="md"
              onClick={handleNewCampaign}
              className="flex items-center gap-2"
            >
              + {tCampaigns("new_campaign")}
            </Button>
          ) : undefined
        }
      />

      {/* Plan List Section */}
      <div
        id="campaigns-list-section"
        className="flex-1 w-full overflow-hidden dark:bg-mw-neutral-900 p-4 sm:p-6 lg:p-8"
      >
        <Card id="campaigns-card" className="flex flex-col h-full">
          <CardHeader id="campaigns-card-header" className="p-4 space-y-2">
            {/* Header Section */}
            <div
              id="campaigns-header-actions"
              className="flex items-center justify-between gap-4"
            >
              {/* Left side - Search */}
              <div
                id="campaigns-search-container"
                className="relative w-80 focus-within:w-[28rem] transition-all duration-300"
              >
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <Search className="h-4 w-4 text-mw-neutral-400" />
                </div>
                <Input
                  id="campaigns-search-input"
                  type="text"
                  value={searchInput}
                  onChange={(e) => handleSearchChange(e.target.value)}
                  placeholder={tCampaigns("table.search_placeholder")}
                  className="w-full pl-10 pr-3 py-2"
                />
              </div>

              {/* Right side - View Toggle and Filter */}
              <div id="campaigns-toolbar" className="flex items-center gap-2">
                {/* View Toggle - Grouped buttons */}
                <div
                  id="campaigns-view-toggle"
                  className="inline-flex items-center outline -outline-offset-1 outline-mw-neutral-100 rounded-md overflow-hidden"
                >
                  <Button
                    id="campaigns-view-list-btn"
                    type="button"
                    onClick={() => handleViewChange("list")}
                    variant="ghost"
                    size="iconMd"
                    className={clsx(
                      "rounded-none px-2.5 py-2",
                      uiState.viewType === "list"
                        ? "bg-mw-neutral-200 dark:bg-mw-neutral-700 text-mw-neutral-700 dark:text-white"
                        : "text-mw-neutral-500 dark:text-mw-neutral-400",
                    )}
                    title={tCampaigns("table.list_view")}
                  >
                    <Rows3 className="h-4 w-4" />
                  </Button>
                  <Button
                    id="campaigns-view-grid-btn"
                    type="button"
                    onClick={() => handleViewChange("grid")}
                    variant="ghost"
                    size="iconMd"
                    className={clsx(
                      "rounded-none px-2.5 py-2",
                      uiState.viewType === "grid"
                        ? "bg-mw-neutral-200 dark:bg-mw-neutral-700 text-mw-neutral-700 dark:text-white"
                        : "text-mw-neutral-500 dark:text-mw-neutral-400",
                    )}
                    title={tCampaigns("table.grid_view")}
                  >
                    <Grid2X2 className="h-4 w-4" />
                  </Button>
                </div>

                {/* Filter Button */}
                <Button
                  id="campaigns-filter-btn"
                  variant="outline"
                  size="sm"
                  onClick={() => dispatch(setFilterModalOpen(true))}
                  className="relative inline-flex items-center gap-2 px-3 py-2.5 text-sm font-medium"
                >
                  <SlidersHorizontal className="h-4 w-4" />
                  {tCampaigns("table.filter")}
                  {getActiveFilterCount() > 0 && (
                    <span
                      id="campaigns-filter-badge"
                      className="absolute -top-2 -right-2 inline-flex items-center justify-center px-2 py-1 text-xs font-bold leading-none text-white bg-mw-primary-600 rounded-full min-w-[1.25rem] h-5"
                    >
                      {getActiveFilterCount()}
                    </span>
                  )}
                </Button>

                <Button
                  id="campaigns-customize-columns-btn"
                  variant="outline"
                  size="sm"
                  disabled={uiState.viewType === "grid"}
                  onClick={() => dispatch(setColumnCustomizationOpen(true))}
                  className="inline-flex items-center gap-2 px-3 py-2.5"
                >
                  <Columns3Cog className="h-4 w-4" />
                  {tCampaigns("table.customize_columns")}
                </Button>
              </div>
            </div>

            {/* Active Filter Chips */}
            {getActiveFilterCount() > 0 && (
              <div
                id="campaigns-active-filters"
                className="flex items-center gap-2 flex-wrap"
              >
                <span className="text-xs text-mw-neutral-500 dark:text-mw-neutral-400">
                  {tCampaigns("filter.active_filters")}
                </span>
                {uiState.filters.status.map((s) => (
                  <span
                    key={s}
                    className="inline-flex items-center gap-1 px-2 py-0.5 text-xs font-medium rounded-full bg-mw-primary-100 dark:bg-mw-primary-900/30 text-mw-primary-700 dark:text-mw-primary-300"
                  >
                    {s.charAt(0) + s.slice(1).toLowerCase()}
                    <button
                      type="button"
                      onClick={() =>
                        dispatch(
                          setFilters({
                            ...uiState.filters,
                            status: uiState.filters.status.filter(
                              (x) => x !== s,
                            ),
                          }),
                        )
                      }
                      className="hover:text-mw-primary-900 dark:hover:text-white"
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </span>
                ))}
                {uiState.filters.campaignGoal.map((g) => (
                  <span
                    key={g}
                    className="inline-flex items-center gap-1 px-2 py-0.5 text-xs font-medium rounded-full bg-mw-secondary-100 dark:bg-mw-secondary-900/30 text-mw-secondary-700 dark:text-mw-secondary-300"
                  >
                    {g.charAt(0) + g.slice(1).toLowerCase()}
                    <button
                      type="button"
                      onClick={() =>
                        dispatch(
                          setFilters({
                            ...uiState.filters,
                            campaignGoal: uiState.filters.campaignGoal.filter(
                              (x) => x !== g,
                            ),
                          }),
                        )
                      }
                      className="hover:text-mw-secondary-900 dark:hover:text-white"
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </span>
                ))}
                {uiState.filters.period?.from && uiState.filters.period?.to && (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs font-medium rounded-full bg-mw-warning-100 dark:bg-mw-warning-900/30 text-mw-warning-700 dark:text-mw-warning-300">
                    {(() => {
                      const d = new Date(uiState.filters.period.from);
                      return tCommon("calendar.formattedShortDate", {
                        month: tCommon(
                          `calendar.monthNamesShort.${d.getMonth()}`,
                        ),
                        day: d.getDate().toString().padStart(2, "0"),
                        year: d.getFullYear(),
                      });
                    })()}{" "}
                    –{" "}
                    {(() => {
                      const d = new Date(uiState.filters.period.to);
                      return tCommon("calendar.formattedShortDate", {
                        month: tCommon(
                          `calendar.monthNamesShort.${d.getMonth()}`,
                        ),
                        day: d.getDate().toString().padStart(2, "0"),
                        year: d.getFullYear(),
                      });
                    })()}
                    <button
                      type="button"
                      onClick={() =>
                        dispatch(
                          setFilters({ ...uiState.filters, period: null }),
                        )
                      }
                      className="hover:text-mw-warning-900 dark:hover:text-white"
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </span>
                )}
                <button
                  type="button"
                  onClick={() =>
                    dispatch(
                      setFilters({
                        status: [],
                        userName: [],
                        period: null,
                        campaignGoal: [],
                      }),
                    )
                  }
                  className="text-xs text-mw-neutral-500 hover:text-mw-error-500 dark:text-mw-neutral-400 dark:hover:text-mw-error-400 underline"
                >
                  {tCampaigns("filter.clear_all")}
                </button>
              </div>
            )}
          </CardHeader>

          {/* Bulk-selection action bar */}
          {uiState.selectedItems.length > 0 && (
            <div className="flex items-center justify-between px-4 py-2 mb-4 border-t border-mw-neutral-100 dark:border-mw-neutral-700 bg-mw-neutral-50 dark:bg-mw-neutral-800">
              <span className="text-sm text-mw-neutral-600 dark:text-mw-neutral-300">
                {uiState.selectedItems.length} selected
              </span>
              <div className="flex items-center gap-3">
                <button
                  type="button"
                  className="cursor-pointer text-xs text-mw-neutral-500 hover:text-mw-neutral-700 dark:text-mw-neutral-400 dark:hover:text-mw-neutral-200 underline"
                  onClick={() => dispatch(clearSelection())}
                >
                  Clear selection
                </button>
                <Button
                  variant="outline"
                  size="sm"
                  className="flex items-center gap-2"
                  onClick={handleBulkArchiveClick}
                >
                  <Archive className="h-4 w-4" />
                  {tCampaigns("campaignActions.bulkArchive")}
                </Button>
                <Button
                  variant="destructive"
                  size="sm"
                  className="flex items-center gap-2"
                  onClick={handleBulkDeleteClick}
                >
                  <Trash2 className="h-4 w-4" />
                  Delete
                </Button>
              </div>
            </div>
          )}

          <CardContent
            id="campaigns-card-content"
            className="flex-1 overflow-hidden px-4! pb-0!"
          >
            {/* Table or Grid Section */}
            {uiState.viewType === "list" ? (
              <CampaignsListView
                data={data}
                isLoading={isLoading}
                isFetching={isFetching}
                paginationInfo={paginationInfo}
                onPageChange={handlePageChange}
                onPageSizeChange={handlePageSizeChange}
                onRefresh={refetch}
              />
            ) : (
              <CampaignsGridView
                data={data}
                isLoading={isLoading}
                isFetching={isFetching}
                paginationInfo={paginationInfo}
                onLoadMore={handleVirtualScrollLoadMore}
                querySignature={gridQuerySignature}
                onRefresh={handleFullReload}
              />
            )}
          </CardContent>
        </Card>
      </div>

      {/* Filter Modal */}
      <CampaignFilterModal
        id="campaigns-filter-modal"
        isOpen={uiState.isFilterModalOpen}
        onClose={() => dispatch(setFilterModalOpen(false))}
        onApply={handleApplyFilters}
        initialValues={uiState.filters}
      />

      {/* Bulk delete — some plans cannot be deleted */}
      <Modal
        isOpen={bulkDeleteBlockedNames.length > 0}
        onClose={() => setBulkDeleteBlockedNames([])}
        title={tCampaigns("campaignActions.bulkDeleteBlockedModal.title")}
        primaryButtonText={tCampaigns(
          "campaignActions.bulkDeleteBlockedModal.ok",
        )}
        onPrimaryAction={() => setBulkDeleteBlockedNames([])}
        size="sm"
      >
        <p className="text-sm text-mw-neutral-700 dark:text-mw-neutral-300">
          {tCampaigns("campaignActions.bulkDeleteBlockedModal.message")}
        </p>
        <ul className="mt-2 list-disc list-inside text-sm text-mw-neutral-700 dark:text-mw-neutral-300 space-y-1">
          {bulkDeleteBlockedNames.map((name) => (
            <li key={name}>{name}</li>
          ))}
        </ul>
      </Modal>

      {/* Bulk delete — all plans can be deleted, confirm */}
      <Modal
        isOpen={bulkDeleteConfirmOpen}
        onClose={() => setBulkDeleteConfirmOpen(false)}
        title={tCampaigns("campaignActions.bulkDeleteModal.title")}
        primaryButtonText={
          isBulkDeleting
            ? tCampaigns("campaignActions.bulkDeleteModal.deleting")
            : tCampaigns("campaignActions.bulkDeleteModal.primaryButton")
        }
        primaryButtonVariant="danger"
        secondaryButtonText={tCampaigns("campaignActions.cancel")}
        onPrimaryAction={handleBulkDeleteConfirm}
        onSecondaryAction={() => setBulkDeleteConfirmOpen(false)}
        size="sm"
      >
        <p className="text-sm text-mw-neutral-700 dark:text-mw-neutral-300">
          {tCampaigns("campaignActions.bulkDeleteModal.message", {
            count: uiState.selectedItems.length,
          })}
        </p>
      </Modal>

      {/* Bulk archive — some plans cannot be archived */}
      <Modal
        isOpen={bulkArchiveBlockedNames.length > 0}
        onClose={() => setBulkArchiveBlockedNames([])}
        title={tCampaigns("campaignActions.bulkArchiveBlockedModal.title")}
        primaryButtonText={tCampaigns(
          "campaignActions.bulkArchiveBlockedModal.ok",
        )}
        onPrimaryAction={() => setBulkArchiveBlockedNames([])}
        size="sm"
      >
        <p className="text-sm text-mw-neutral-700 dark:text-mw-neutral-300">
          {tCampaigns("campaignActions.bulkArchiveBlockedModal.message")}
        </p>
        <ul className="mt-2 list-disc list-inside text-sm text-mw-neutral-700 dark:text-mw-neutral-300 space-y-1">
          {bulkArchiveBlockedNames.map((name) => (
            <li key={name}>{name}</li>
          ))}
        </ul>
      </Modal>

      {/* Bulk archive — all plans can be archived, confirm */}
      <Modal
        isOpen={bulkArchiveConfirmOpen}
        onClose={() => setBulkArchiveConfirmOpen(false)}
        title={tCampaigns("campaignActions.bulkArchiveModal.title")}
        primaryButtonText={
          isBulkArchiving
            ? tCampaigns("campaignActions.bulkArchiveModal.archiving")
            : tCampaigns("campaignActions.bulkArchiveModal.primaryButton")
        }
        secondaryButtonText={tCampaigns("campaignActions.cancel")}
        onPrimaryAction={handleBulkArchiveConfirm}
        onSecondaryAction={() => setBulkArchiveConfirmOpen(false)}
        size="sm"
      >
        <p className="text-sm text-mw-neutral-700 dark:text-mw-neutral-300">
          {tCampaigns("campaignActions.bulkArchiveModal.message", {
            count: uiState.selectedItems.length,
          })}
        </p>
      </Modal>
    </div>
  );
};

export default CampaignsPage;
