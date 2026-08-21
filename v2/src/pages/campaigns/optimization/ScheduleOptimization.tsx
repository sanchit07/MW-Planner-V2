import { InventoryCard } from "@components/common/InventoryCard";
import {
  SelectedInventoryListContainer,
  SelectedInventoryListContainerRef,
} from "@components/common/SelectedInventoryListContainer";
import { Badge } from "@components/ui/Badge";
import { Button } from "@components/ui/Button";
import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import {
  Dropdown,
  DropdownContent,
  DropdownItem,
  DropdownSeparator,
  DropdownTrigger,
} from "@components/ui/Dropdown";
import Modal from "@components/ui/Modal";
import { Loading } from "@components/ui/Spinner";
import { useAnnounce } from "@hooks/useAnnounce";
import {
  useLazyGetSelectedInventorySchedulesQuery,
  useDeleteInventoryScheduleMutation,
  useSelectInventoryMutation,
} from "@services/inventory/inventorySlice";
import { useTranslate } from "@tolgee/react";
import { formatDisplayDate } from "@utils/dateUtils";
import { sortDaysStartingFromMonday } from "@utils/inventory.utils";
import { clearDefaultSchedule } from "@utils/scheduleDefaults";
import { Clock, Plus, SquarePen, Trash2 } from "lucide-react";
import { useCallback, useMemo, useRef, useState } from "react";
import { InventoryClassification } from "src/constants/inventory.constants";
import { InventoryItem, InventorySchedule } from "src/types/inventory.types";

import { OptimizeManuallyDrawer } from "./OptimizeManuallyDrawer";
import { ScheduleDrawer } from "./ScheduleDrawer";
import {
  INVENTORY_TYPES,
  ScheduleOptimizationComponentProps,
} from "./types/schedule.types";

const ScheduleOptimizationComponent = ({
  campaignId,
  campaignState,
  loadForeCastData,
}: ScheduleOptimizationComponentProps) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { t: tCommon } = useTranslate(["common"]);
  const [inventoryType, setInventoryType] = useState<string>("All Inventories");
  const [selectedInventoryId, setSelectedInventoryId] =
    useState<string>("All Inventories");
  const [selectedItems, setSelectedItems] = useState<InventoryItem[]>([]);
  const [inventorySchedules, setInventorySchedules] = useState<
    InventorySchedule[]
  >([]);
  const [selectedSchedule, setSelectedSchedule] = useState<InventorySchedule>();
  const [isEditScheduleDrawerOpen, setIsEditScheduleDrawerOpen] =
    useState(false);
  const [isNewSchedule, setIsNewSchedule] = useState(false);
  const { showError, showSuccess } = useAnnounce();
  const [
    isOptimizeManuallyScheduleDrawerOpen,
    setIsOptimizeManuallyScheduleDrawerOpen,
  ] = useState(false);

  const [fetchSelectedInventorySchedules] =
    useLazyGetSelectedInventorySchedulesQuery();
  const [isSchedulesLoading, setIsSchedulesLoading] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [scheduleToDeleteId, setScheduleToDeleteId] = useState<string>();
  const [deleteSchedules] = useDeleteInventoryScheduleMutation();
  const [deselectInventory] = useSelectInventoryMutation();

  // Inventory removal (deselect from campaign)
  const inventoryListRef = useRef<SelectedInventoryListContainerRef>(null);
  const [isRemoveInventoryModalOpen, setIsRemoveInventoryModalOpen] =
    useState(false);
  const [inventoryToRemove, setInventoryToRemove] = useState<InventoryItem>();
  const [inventoryTotal, setInventoryTotal] = useState(0);

  const fetInventoryScheduleList = useCallback(
    async (id: string) => {
      try {
        setIsSchedulesLoading(true);
        const inventorySchedules = await fetchSelectedInventorySchedules({
          campaignId,
          inventories: [id],
        }).unwrap();
        setIsSchedulesLoading(false);
        if (
          inventorySchedules.success &&
          inventorySchedules.data &&
          inventorySchedules.data.length > 0
        ) {
          const schedules = inventorySchedules.data[0].schedules;
          setInventorySchedules(schedules);
          if (schedules.length > 0) {
            setSelectedSchedule(schedules[0]);
          }
        }
      } catch {
        setIsSchedulesLoading(false);
        showError(
          tCampaigns("optimization.schedulingTargeting.errorFetchingSchedules"),
        );
      }
    },
    [campaignId, fetchSelectedInventorySchedules, showError, tCampaigns],
  );

  const handleCardClick = useCallback(
    async (item: InventoryItem) => {
      setSelectedInventoryId(item.detail.id);
      fetInventoryScheduleList(item.detail.id);
    },
    [fetInventoryScheduleList],
  );

  // AI Optimization temporarily hidden (button in CardHeader below is also
  // commented out). To re-enable: uncomment this handler, restore the
  // `useAutoOptimizeSchedulesMutation` import + `autoOptimizeSchedules` hook.
  // const handleAiOptimizeClick = () => {
  //   if (campaignId) {
  //     setIsSchedulesLoading(true);
  //     autoOptimizeSchedules({ campaignId })
  //       .unwrap()
  //       .then((result) => {
  //         if (result.success) {
  //           showSuccess(
  //             tCampaigns(
  //               "optimization.schedulingTargeting.autoOptimizeSuccess",
  //             ),
  //           );
  //         }
  //       })
  //       .catch(() => {
  //         showError(
  //           tCampaigns("optimization.schedulingTargeting.errorAutoOptimize"),
  //         );
  //       })
  //       .finally(() => {
  //         setIsSchedulesLoading(false);
  //         fetInventoryScheduleList(selectedInventoryId);
  //         loadForeCastData(true);
  //       });
  //   }
  // };

  const handleInventoryTypeChange = (value: string) => {
    setInventoryType(value);
  };

  const handleInventoryScheduleChange = (schedule: InventorySchedule) => {
    setSelectedSchedule(schedule);
  };

  const filterInventoryItems = useMemo(
    () => (items: InventoryItem[], query: string) => {
      let result =
        inventoryType === "All Inventories"
          ? items
          : items.filter(
              (item) =>
                item.detail.inventoryType.toLowerCase() ===
                inventoryType.toLowerCase(),
            );
      if (query) {
        const q = query.toLowerCase();
        result = result.filter(
          (item) =>
            item.detail.name?.toLowerCase().includes(q) ||
            item.location?.location?.address?.toLowerCase().includes(q),
        );
      }
      return result;
    },
    [inventoryType],
  );

  const handleDeleteScheduleClick = () => {
    // Prevent deletion if there's only one schedule
    if (inventorySchedules.length <= 1) {
      showError(
        tCampaigns(
          "optimization.schedulingTargeting.defaultScheduleDeleteError",
        ),
      );
      return;
    }
    const selectedId = selectedSchedule?.id;
    if (!selectedId) {
      showError(tCampaigns("scheduleOptimization.errors.cannotDeleteSchedule"));
      return;
    }
    setScheduleToDeleteId(selectedId);
    setIsDeleteModalOpen(true);
  };

  const handleScheduleDelete = async () => {
    if (
      scheduleToDeleteId === undefined ||
      !selectedInventoryId ||
      selectedInventoryId === "All Inventories" ||
      inventorySchedules.length <= 1
    ) {
      return;
    }

    try {
      // Remove the schedule from the array by id
      const updatedSchedules = inventorySchedules.filter(
        (inventorySchedule) => inventorySchedule.id !== scheduleToDeleteId,
      );

      // Call API to delete schedule - send empty array or remaining schedules
      // Note: Backend may handle deletion differently, adjust based on API requirements
      const result = await deleteSchedules({
        campaignId,
        scheduleId: scheduleToDeleteId,
      }).unwrap();

      if (result.success) {
        showSuccess(
          tCampaigns("scheduleOptimization.scheduleDeletedSuccessfully"),
        );
        // If the default schedule (order 1) was deleted, drop the inventory's
        // persisted default snapshot.
        const deletedSchedule = inventorySchedules.find(
          (s) => s.id === scheduleToDeleteId,
        );
        if (deletedSchedule?.order === 1) {
          clearDefaultSchedule(campaignId, selectedInventoryId);
        }
        // Update local state
        setInventorySchedules(updatedSchedules);
        // Select first schedule if available
        if (updatedSchedules.length > 0) {
          setSelectedSchedule(updatedSchedules[0]);
        } else {
          setSelectedSchedule(undefined);
        }

        setIsDeleteModalOpen(false);
        setScheduleToDeleteId(undefined);
        loadForeCastData(true);
      } else {
        showError(
          tCampaigns("scheduleOptimization.errors.failedToDeleteSchedule"),
        );
      }
    } catch {
      showError(
        tCampaigns("scheduleOptimization.errors.failedToDeleteScheduleRetry"),
      );
    }
  };

  const selectedInventoryItem = useMemo(() => {
    if (!selectedInventoryId || selectedInventoryId === "All Inventories") {
      return null;
    }
    return (
      selectedItems.find((item) => item.detail.id === selectedInventoryId) ||
      null
    );
  }, [selectedItems, selectedInventoryId]);

  // Duration/Spots-per-Loop/Spots-per-Hour/Ad Plays are digital (hour/loop-driven)
  // concepts — Classic schedules only carry date ranges, so these tiles don't apply.
  const isDigitalSchedule =
    selectedInventoryItem?.detail.inventoryType ===
    InventoryClassification.DIGITAL;

  const handleRemoveInventoryClick = (item: InventoryItem) => {
    // Block removing the last remaining inventory
    if (selectedItems.length <= 1) {
      showError(
        tCampaigns(
          "optimization.schedulingTargeting.cannotRemoveLastInventory",
        ),
      );
      return;
    }
    setInventoryToRemove(item);
    setIsRemoveInventoryModalOpen(true);
  };

  const handleInventoryRemove = async () => {
    const inventoryId = inventoryToRemove?.detail.id;
    if (!campaignId || !inventoryId) return;

    try {
      const result = await deselectInventory({
        campaignId,
        inventoryId,
        operationType: "DESELECT",
      }).unwrap();

      if (result.success) {
        showSuccess(
          tCampaigns("optimization.schedulingTargeting.removeInventorySuccess"),
        );
        // Drop any persisted default snapshot for this inventory
        clearDefaultSchedule(campaignId, inventoryId);
        // Reload the list from the server (re-fires onInitialLoad, which
        // re-selects the first item and reloads its schedules)
        await inventoryListRef.current?.refetch();
        loadForeCastData(true);
      } else {
        showError(
          tCampaigns("optimization.schedulingTargeting.removeInventoryError"),
        );
      }
    } catch {
      showError(
        tCampaigns("optimization.schedulingTargeting.removeInventoryError"),
      );
    } finally {
      setIsRemoveInventoryModalOpen(false);
      setInventoryToRemove(undefined);
    }
  };

  // Refresh schedules after save
  const handleScheduleSaved = useCallback(async () => {
    if (selectedInventoryId && selectedInventoryId !== "All Inventories") {
      fetInventoryScheduleList(selectedInventoryId);
      loadForeCastData(true);
    }
  }, [selectedInventoryId, fetInventoryScheduleList, loadForeCastData]);

  const handleOpenEditScheduleDrawer = () => {
    if (selectedInventoryId && selectedInventoryId !== "All Inventories") {
      setIsNewSchedule(false);
      setIsEditScheduleDrawerOpen(true);
    }
  };

  const handleOpenNewScheduleDrawer = () => {
    if (selectedInventoryId && selectedInventoryId !== "All Inventories") {
      setIsNewSchedule(true);
      setIsEditScheduleDrawerOpen(true);
    }
  };

  return (
    <>
      <Card className="mt-4 px-4 gap-6">
        <CardHeader className="border-b border-mw-neutral-100 pb-4">
          <div className="flex pt-3 items-center">
            <div className="flex flex-1 items-center gap-2">
              <div className="w-10 h-10 bg-mw-success-50 rounded-lg flex items-center justify-center">
                <Clock className="w-4 h-4 text-mw-success-500" />
              </div>
              <div className="flex-1 inline-flex flex-col justify-start items-start">
                <span className="text-s">
                  {tCampaigns("optimization.configureScheduling.title")}
                </span>
                <span className="text-xs text-mw-neutral-500">
                  {tCampaigns("optimization.configureScheduling.description")}
                </span>
              </div>
            </div>
            <Button
              size="sm"
              variant="outline"
              className="outline-mw-primary-500 text-mw-primary-500 cursor-pointer mr-3"
              onClick={() => setIsOptimizeManuallyScheduleDrawerOpen(true)}
            >
              {tCampaigns(
                "optimization.configureScheduling.optimizeManuallyButtonLabel",
              )}
            </Button>
            {/* AI Optimization temporarily hidden. To re-enable: uncomment,
                re-add `Sparkles` to the lucide-react import, and restore the
                commented-out `handleAiOptimizeClick` handler above.
            <Button size="sm" variant="primary" onClick={handleAiOptimizeClick}>
              <Sparkles className="w-4 h-4 text-white mr-1" />
              {tCampaigns(
                "optimization.configureScheduling.aiOptimizeButtonLabel",
              )}
            </Button> */}
          </div>
        </CardHeader>
        <CardContent className="p-0! grid grid-cols-2 gap-4 my-4">
          <Card className="p-0!">
            <CardHeader className="p-4">
              <CardTitle className="text-sm font-medium border-b border-mw-neutral-100 pb-4">
                <div className="flex-1 inline-flex flex-col justify-start items-start">
                  <span className="text-s inline-flex items-center gap-1.5">
                    {tCampaigns("create_campaign.steps.inventories")}
                    <span className="font-normal text-mw-neutral-500">
                      {inventoryTotal}
                    </span>
                  </span>
                  <span className="text-xs text-mw-neutral-500">
                    {tCampaigns(
                      "optimization.configureScheduling.selectedInventoryInfo",
                    )}
                  </span>
                </div>
              </CardTitle>
            </CardHeader>
            <CardContent className="p-0! space-y-4">
              <SelectedInventoryListContainer
                ref={inventoryListRef}
                campaignId={campaignId}
                enabled={!!campaignId}
                onTotalElementsChange={setInventoryTotal}
                containerClassName="p-0! border-0"
                showHeader={false}
                contentBeforeList={
                  <div className="pb-4">
                    <Dropdown name="inventory-type">
                      <DropdownTrigger className="w-full justify-between">
                        {inventoryType === "All Inventories"
                          ? tCampaigns("scheduleOptimization.allInventories")
                          : tCommon(
                              `inventoryClassification.${inventoryType.toLowerCase()}`,
                            ) || inventoryType}
                      </DropdownTrigger>
                      <DropdownContent
                        align="left"
                        className="w-full max-h-[200px] overflow-y-auto scrollbar-thin"
                      >
                        {INVENTORY_TYPES.map((inventoryType) => (
                          <DropdownItem
                            key={inventoryType.value}
                            value={inventoryType.value}
                            onClick={() => {
                              handleInventoryTypeChange(inventoryType.value);
                            }}
                            className="hover:bg-mw-primary-50 hover:text-mw-primary-500"
                          >
                            <div className="flex-1 items-start flex flex-col gap-1">
                              <p>
                                {inventoryType.value === "All Inventories"
                                  ? tCampaigns(
                                      "scheduleOptimization.allInventories",
                                    )
                                  : tCommon(
                                      `inventoryClassification.${inventoryType.value.toLowerCase()}`,
                                    ) || inventoryType.label}
                              </p>
                              <DropdownSeparator />
                            </div>
                          </DropdownItem>
                        ))}
                      </DropdownContent>
                    </Dropdown>
                  </div>
                }
                useNestedScrollContainer={true}
                nestedScrollClassName="flex-1 space-y-3 max-h-[calc(100vh-400px)]"
                emptyMessage={tCampaigns(
                  "optimization.configureScheduling.noInventories",
                )}
                loadingMoreText={tCampaigns(
                  "optimization.configureScheduling.loadingMore",
                )}
                filterItems={filterInventoryItems}
                onInitialLoad={(items) => {
                  setSelectedItems(items);
                  // Auto-select first item on initial load
                  if (items.length > 0) {
                    handleCardClick(items[0]);
                  }
                }}
                onLoadMore={(items) =>
                  setSelectedItems((prev) => [...prev, ...items])
                }
                renderItem={(item) => (
                  <InventoryCard
                    key={item.detail.id}
                    item={item}
                    isSelected={selectedInventoryId === item.detail.id}
                    onClick={() => handleCardClick(item)}
                    showRemoveButton
                    onRemove={() => handleRemoveInventoryClick(item)}
                    removeButtonTitle={tCampaigns(
                      "optimization.schedulingTargeting.removeInventoryTitle",
                    )}
                  />
                )}
              />
            </CardContent>
          </Card>
          <Card className="p-4 relative">
            {isSchedulesLoading ? (
              <div className="h-full flex items-center justify-center min-h-[200px]">
                <Loading />
              </div>
            ) : (
              <>
                {inventorySchedules.length ? (
                  <>
                    <CardHeader className="pb-4 border-b border-mw-neutral-100">
                      <CardTitle>
                        <div className="text-s text-mw-neutral-700 font-medium">
                          {tCampaigns(
                            "optimization.schedulingTargeting.tabtitle",
                          )}{" "}
                          {selectedSchedule
                            ? inventorySchedules.findIndex(
                                (schedule) =>
                                  schedule.id === selectedSchedule.id,
                              ) + 1
                            : 0}{" "}
                          {tCampaigns("scheduleOptimization.of")}{" "}
                          {inventorySchedules.length}
                        </div>
                        <div className="text-s text-mw-neutral-700 font-medium flex items-center gap-6 mt-2">
                          <Dropdown name="schedule-type" className="flex-1">
                            <DropdownTrigger className="w-full justify-between">
                              {selectedSchedule?.name ||
                                (selectedSchedule?.order
                                  ? tCampaigns(
                                      "scheduleOptimization.scheduleName",
                                      { order: selectedSchedule.order },
                                    )
                                  : "")}
                              {selectedSchedule?.order === 1
                                ? ` ${tCampaigns("scheduleOptimization.scheduleDefault")}`
                                : ""}
                            </DropdownTrigger>
                            <DropdownContent
                              align="left"
                              className="w-full max-h-[200px] overflow-y-auto scrollbar-thin"
                            >
                              {inventorySchedules.map(
                                (inventorySchedule: InventorySchedule) => (
                                  <DropdownItem
                                    key={
                                      "schedule-" +
                                      (inventorySchedule.id ||
                                        inventorySchedule.order)
                                    }
                                    value={
                                      inventorySchedule.name ||
                                      `Schedule ${inventorySchedule.order}`
                                    }
                                    onClick={() => {
                                      handleInventoryScheduleChange(
                                        inventorySchedule,
                                      );
                                    }}
                                    className="hover:bg-mw-primary-50 hover:text-mw-primary-500"
                                  >
                                    <div className="flex-1 items-start flex flex-col gap-1">
                                      <p>
                                        {inventorySchedule.name ||
                                          tCampaigns(
                                            "scheduleOptimization.scheduleName",
                                            { order: inventorySchedule.order },
                                          )}
                                        {inventorySchedule.order === 1
                                          ? ` ${tCampaigns("scheduleOptimization.scheduleDefault")}`
                                          : ""}
                                      </p>
                                      <DropdownSeparator />
                                    </div>
                                  </DropdownItem>
                                ),
                              )}
                            </DropdownContent>
                          </Dropdown>
                          <div className="inline-flex justify-end flex-nowrap gap-1">
                            <Button
                              size="iconMd"
                              variant="outline"
                              className="cursor-pointer"
                              onClick={handleOpenNewScheduleDrawer}
                              disabled={
                                !selectedInventoryId ||
                                selectedInventoryId === "All Inventories"
                              }
                            >
                              <Plus className="w-4 h-4" />
                            </Button>
                            <Button
                              size="iconMd"
                              variant="outline"
                              className="cursor-pointer"
                              onClick={handleOpenEditScheduleDrawer}
                              disabled={
                                !selectedInventoryId ||
                                selectedInventoryId === "All Inventories"
                              }
                            >
                              <SquarePen className="w-4 h-4" />
                            </Button>
                            <Button
                              size="iconMd"
                              variant="outline"
                              onClick={handleDeleteScheduleClick}
                              disabled={inventorySchedules.length <= 1}
                              tooltip={
                                inventorySchedules.length <= 1
                                  ? tCampaigns(
                                      "optimization.schedulingTargeting.defaultScheduleDeleteError",
                                    )
                                  : undefined
                              }
                              className="cursor-pointer text-mw-error-100! outline-mw-error-500! bg-mw-error-50!"
                            >
                              <Trash2 className="w-4 h-4 text-mw-error-500" />
                            </Button>
                          </div>
                        </div>
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="p-0! pt-4! space-y-4">
                      <div className="schedule-dates flex gap-2">
                        <div className="text-sm text-secondary">
                          {tCampaigns(
                            "optimization.schedulingTargeting.scheduleDates",
                          )}
                        </div>
                        <div className="text-sm text-black font-medium flex-1 text-right leading-4">
                          {selectedSchedule?.startDate
                            ? formatDisplayDate(
                                selectedSchedule?.startDate,
                                tCommon,
                              )
                            : ""}{" "}
                          -{" "}
                          {selectedSchedule?.endDate
                            ? formatDisplayDate(
                                selectedSchedule?.endDate,
                                tCommon,
                              )
                            : ""}
                        </div>
                      </div>
                      <div className="schedule-days flex gap-2">
                        <div className="text-sm text-secondary">
                          {tCampaigns(
                            "optimization.schedulingTargeting.scheduleDays",
                          )}
                        </div>
                        <div className="text-sm text-black font-medium flex-1 inline-flex flex-wrap gap-2 justify-end leading-4">
                          {sortDaysStartingFromMonday(
                            selectedSchedule?.scheduleDays || [],
                          ).map((day: string) => {
                            return (
                              <Badge
                                key={day}
                                className="text-s! text-black! font-medium! outline-mw-neutral-100!"
                              >
                                {tCampaigns(
                                  `scheduleOptimization.days.${day}`,
                                ) ||
                                  day.charAt(0) + day.slice(1, 3).toLowerCase()}
                              </Badge>
                            );
                          })}
                        </div>
                      </div>
                      <div className="grid grid-cols-3 gap-2">
                        {isDigitalSchedule && (
                          <>
                            <div className="border border-mw-neutral-100 rounded-lg p-3 flex flex-col gap-1">
                              <div className="text-xs text-secondary">
                                {tCampaigns(
                                  "optimization.schedulingTargeting.scheduleDuration",
                                )}
                              </div>
                              <div className="text-sm text-black font-medium">
                                {selectedSchedule?.duration}{" "}
                                {tCampaigns(
                                  "optimization.schedulingTargeting.secondsLabel",
                                )}
                              </div>
                            </div>
                            <div className="border border-mw-neutral-100 rounded-lg p-3 flex flex-col gap-1">
                              <div className="text-xs text-secondary">
                                {tCampaigns(
                                  "optimization.schedulingTargeting.scheduleSpotsLoop",
                                )}
                              </div>
                              <div className="text-sm text-black font-medium">
                                {selectedSchedule?.spotsPerLoop}
                              </div>
                            </div>
                            <div className="border border-mw-neutral-100 rounded-lg p-3 flex flex-col gap-1">
                              <div className="text-xs text-secondary">
                                {tCampaigns(
                                  "optimization.schedulingTargeting.scheduleSpotsHour",
                                )}
                              </div>
                              <div className="text-sm text-black font-medium">
                                {selectedSchedule?.spotsPerHour}
                              </div>
                            </div>
                            <div className="border border-mw-neutral-100 rounded-lg p-3 flex flex-col gap-1">
                              <div className="text-xs text-secondary">
                                {tCampaigns(
                                  "viewCampaign.targetingTab.adPlays",
                                )}
                              </div>
                              <div className="text-sm text-black font-medium">
                                {selectedSchedule?.adPlays?.toLocaleString() ??
                                  "-"}
                              </div>
                            </div>
                          </>
                        )}
                        <div className="border border-mw-neutral-100 rounded-lg p-3 flex flex-col gap-1">
                          <div className="text-xs text-secondary">
                            {tCampaigns("card.sov")}
                          </div>
                          <div className="text-sm text-black font-medium">
                            {selectedSchedule?.sov?.toFixed(2)}%
                          </div>
                        </div>
                        <div className="border border-mw-neutral-100 rounded-lg p-3 flex flex-col gap-1">
                          <div className="text-xs text-secondary">
                            {tCampaigns("card.sot")}
                          </div>
                          <div className="text-sm text-black font-medium">
                            {selectedSchedule?.plannedSot?.toFixed(2)}H
                          </div>
                        </div>
                        <div className="border border-mw-neutral-100 rounded-lg p-3 flex flex-col gap-1">
                          <div className="text-xs text-secondary">
                            {tCampaigns("inventories.metrics.impressions")}
                          </div>
                          <div className="text-sm text-black font-medium">
                            {selectedSchedule?.impressions?.toLocaleString() ??
                              "-"}
                          </div>
                        </div>
                        <div className="border border-mw-neutral-100 rounded-lg p-3 flex flex-col gap-1">
                          <div className="text-xs text-secondary">
                            {tCampaigns("inventories.metrics.reach")}
                          </div>
                          <div className="text-sm text-black font-medium">
                            {selectedSchedule?.reach?.toLocaleString() ?? "-"}
                          </div>
                        </div>
                        <div className="border border-mw-neutral-100 rounded-lg p-3 flex flex-col gap-1">
                          <div className="text-xs text-secondary">
                            {tCampaigns("inventories.metrics.frequency")}
                          </div>
                          <div className="text-sm text-black font-medium">
                            {selectedSchedule?.frequency != null
                              ? `${selectedSchedule.frequency.toFixed(1)}x`
                              : "-"}
                          </div>
                        </div>
                        <div className="border border-mw-neutral-100 rounded-lg p-3 flex flex-col gap-1">
                          <div className="text-xs text-secondary">
                            {tCampaigns("inventories.metrics.ecpm")}
                          </div>
                          <div className="text-sm text-black font-medium">
                            {selectedSchedule?.basePrice != null &&
                            selectedSchedule?.impressions
                              ? `${campaignState.campaignData?.currency ?? ""} ${((selectedSchedule.basePrice / selectedSchedule.impressions) * 1000).toFixed(2)}`
                              : "-"}
                          </div>
                        </div>
                        <div className="border border-mw-neutral-100 rounded-lg p-3 flex flex-col gap-1">
                          <div className="text-xs text-secondary">
                            {tCampaigns("inventories.metrics.est_cost")}
                          </div>
                          <div className="text-sm text-black font-medium">
                            {selectedSchedule?.basePrice != null
                              ? `${campaignState.campaignData?.currency ?? ""} ${selectedSchedule.basePrice.toLocaleString()}`
                              : "-"}
                          </div>
                        </div>
                      </div>
                    </CardContent>
                  </>
                ) : (
                  <div className="font-medium text-m text-black absolute left-1/2 top-1/2 -translate-1/2">
                    {tCampaigns(
                      "optimization.schedulingTargeting.noScheduleText",
                    )}
                  </div>
                )}
              </>
            )}
          </Card>
        </CardContent>
      </Card>
      {/* Delete Confirmation Modal */}
      <Modal
        isOpen={isDeleteModalOpen}
        onClose={() => setIsDeleteModalOpen(false)}
        title={tCampaigns(
          "optimization.schedulingTargeting.removeScheduleTitle",
        )}
        primaryButtonText={tCampaigns(
          "optimization.schedulingTargeting.removeScheduleYesButtonLabel",
        )}
        secondaryButtonText={tCampaigns(
          "optimization.schedulingTargeting.removeScheduleNoButtonLabel",
        )}
        onPrimaryAction={handleScheduleDelete}
        onSecondaryAction={() => setIsDeleteModalOpen(false)}
        primaryButtonVariant="danger"
        size="sm"
      >
        <p>
          {tCampaigns(
            "optimization.schedulingTargeting.removeScheduleConfirmationMessage",
          )}{" "}
          <strong>
            "
            {selectedSchedule?.name ||
              (selectedSchedule?.order
                ? tCampaigns("scheduleOptimization.scheduleName", {
                    order: selectedSchedule.order,
                  })
                : "")}{" "}
            "
          </strong>
          ?
        </p>
      </Modal>

      {/* Remove Inventory Confirmation Modal */}
      <Modal
        isOpen={isRemoveInventoryModalOpen}
        onClose={() => setIsRemoveInventoryModalOpen(false)}
        title={tCampaigns(
          "optimization.schedulingTargeting.removeInventoryTitle",
        )}
        primaryButtonText={tCampaigns(
          "optimization.schedulingTargeting.removeInventoryYesButtonLabel",
        )}
        secondaryButtonText={tCampaigns(
          "optimization.schedulingTargeting.removeInventoryNoButtonLabel",
        )}
        onPrimaryAction={handleInventoryRemove}
        onSecondaryAction={() => setIsRemoveInventoryModalOpen(false)}
        primaryButtonVariant="danger"
        size="sm"
      >
        <p>
          {tCampaigns(
            "optimization.schedulingTargeting.removeInventoryConfirmationMessage",
          )}{" "}
          <strong>"{inventoryToRemove?.detail.name || ""}"</strong>?
        </p>
      </Modal>

      {/* Schedule Drawer */}
      <ScheduleDrawer
        isOpen={isEditScheduleDrawerOpen}
        onClose={() => {
          setIsEditScheduleDrawerOpen(false);
          setIsNewSchedule(false);
        }}
        selectedInventoryId={selectedInventoryId}
        selectedInventory={selectedInventoryItem}
        inventorySchedules={inventorySchedules}
        scheduleId={
          isNewSchedule
            ? undefined // undefined means new schedule
            : selectedSchedule?.id
        }
        campaignStartDate={campaignState.campaignData?.startDate}
        campaignEndDate={campaignState.campaignData?.endDate}
        campaignId={campaignId}
        onScheduleSaved={handleScheduleSaved}
      />

      {/* Schedule Drawer */}
      {campaignState.campaignData && (
        <OptimizeManuallyDrawer
          campaignId={campaignId}
          campaignState={campaignState}
          isOpen={isOptimizeManuallyScheduleDrawerOpen}
          onClose={() => {
            handleScheduleSaved();
            setIsOptimizeManuallyScheduleDrawerOpen(false);
          }}
        />
      )}
    </>
  );
};

export default ScheduleOptimizationComponent;
