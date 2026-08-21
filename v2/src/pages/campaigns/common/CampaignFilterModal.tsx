import { Label } from "@components/ui/Label";
import { CAMPAIGN_STATUS_OPTIONS } from "@constants/campaign.constants";
import { useGetUsersQuery } from "@services/account/accountApi";
import { useTranslate } from "@tolgee/react";
import { createGoalTypes } from "@utils/budget.utils";
import React, { useState, useEffect, useMemo, useCallback } from "react";

import { Button } from "../../../components/ui/Button";
import { DateRangePicker } from "../../../components/ui/DateRangePicker";
import { ModalDrawer } from "../../../components/ui/ModalDrawer";
import MultiSelect, { TreeNode } from "../../../components/ui/MultiSelect";
import { useAppSelector } from "../../../store";

export const allCampaignColumn = [
  "serialNo",
  "campaignName",
  "brand",
  "userName",
  "flightDates",
  "goalType",
  "status",
  "inventory",
  "budget",
  "totalCost",
];

export interface FilterValues {
  status: string[];
  userName: string[];
  period: {
    from: Date | null | undefined;
    to: Date | null | undefined;
  } | null;
  campaignGoal: string[];
}

interface CampaignFilterModalProps {
  isOpen: boolean;
  onClose: () => void;
  onApply: (filters: FilterValues) => void;
  initialValues?: Partial<FilterValues>;
  id?: string;
}

const CampaignFilterModal: React.FC<CampaignFilterModalProps> = ({
  isOpen,
  onClose,
  onApply,
  initialValues = {},
  id,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);

  // Normalize the incoming initialValues (from the campaignsUI slice — the
  // single source of truth) into a complete FilterValues shape. `?? []` / `?? null`
  // coerce both absent (undefined) and explicitly-cleared (null) fields to
  // defaults, so a page-level "Clear all" is mirrored here on reopen.
  const toFilters = useCallback(
    (initial: Partial<FilterValues>): FilterValues => ({
      status: initial.status ?? [],
      userName: initial.userName ?? [],
      period: initial.period ?? null,
      campaignGoal: initial.campaignGoal ?? [],
    }),
    [],
  );

  // Initialize filter state from initialValues (the slice)
  const [filters, setFilters] = useState<FilterValues>(() =>
    toFilters(initialValues),
  );

  // Sync filters with initialValues when the modal opens
  useEffect(() => {
    if (isOpen) {
      setFilters(toFilters(initialValues));
    }
  }, [isOpen, initialValues, toFilters]);

  // Validation state for period
  const [periodError, setPeriodError] = useState<string>("");

  // Status options from constants — labels translated at render time
  const statusOptions: TreeNode[] = useMemo(
    () =>
      CAMPAIGN_STATUS_OPTIONS.map((opt) => ({
        ...opt,
        label: tCampaigns(`campaignsList.status.${opt.value}`) || opt.label,
      })),
    [tCampaigns],
  );

  // Planned by options
  const profile = useAppSelector((s) => s.profile.profile);
  const companyId = profile?.activeCompanyId ?? "";
  const { data: usersData } = useGetUsersQuery(
    { company_id: companyId },
    { skip: !companyId || !isOpen },
  );
  const plannedByOptions: TreeNode[] = useMemo(
    () =>
      (usersData?.data ?? []).map((user) => ({
        label: `${user.first_name} ${user.last_name}`,
        value: user.id,
      })),
    [usersData],
  );

  // Goal types configuration using utility function
  const campaignGoalOptions = useMemo(
    () => createGoalTypes(tCampaigns),
    [tCampaigns],
  );

  const handleApply = () => {
    // Validate period: if from is selected, to must also be selected
    if (filters.period?.from && !filters.period?.to) {
      setPeriodError(tCampaigns("filter.selectEndDateError"));
      return; // Prevent API call
    }

    // Clear error if validation passes
    setPeriodError("");

    onApply(filters);
    onClose();
  };

  const handleReset = () => {
    // Reset all filters to empty/default values
    setFilters({
      status: [],
      userName: [],
      period: null,
      campaignGoal: [],
    });
    // Clear period error
    setPeriodError("");
    // Don't close the modal
  };

  const handleClose = () => {
    // Discard unapplied edits — revert to the slice's current values
    setFilters(toFilters(initialValues));
    setPeriodError("");
    onClose();
  };

  const modalId = id || "campaign-filter-modal";
  return (
    <ModalDrawer
      id={modalId}
      isOpen={isOpen}
      onClose={handleClose}
      title={tCampaigns("filter.title")}
      size="lg"
      footer={
        <div id={`${modalId}-footer`} className="flex justify-end gap-3">
          <Button
            id={`${modalId}-reset-btn`}
            variant="outline"
            className="text-mw-primary-500 outline-mw-primary-500"
            size="md"
            onClick={handleReset}
          >
            {tCampaigns("filter.reset")}
          </Button>
          <Button
            id={`${modalId}-apply-btn`}
            variant="primary"
            size="md"
            onClick={handleApply}
          >
            {tCampaigns("filter.apply_filters")}
          </Button>
        </div>
      }
    >
      <div id={`${modalId}-content`} className="space-y-4 pt-4">
        {/* Filter Content */}
        <div className="space-y-2">
          {/* Status Filter */}
          <div id={`${modalId}-status-filter`} className="space-y-2">
            <Label info={tCampaigns("filter.tooltip_status")}>
              {tCampaigns("filter.status")}
            </Label>
            <MultiSelect
              id={`${modalId}-status-multiselect`}
              options={statusOptions}
              value={filters.status}
              onChange={(values: string[]) =>
                setFilters((prev) => ({ ...prev, status: values }))
              }
              placeholder={tCampaigns("filter.select_options")}
              maxVisibleChips={2}
              searchable={true}
              showSelectAll={true}
              selectAllLabel={tCampaigns("filter.allStatus")}
            />
          </div>

          {/* Planned By Filter */}
          <div id={`${modalId}-planned-by-filter`} className="space-y-2">
            <Label info={tCampaigns("filter.tooltip_planned_by")}>
              {tCampaigns("filter.planned_by")}
            </Label>
            <MultiSelect
              id={`${modalId}-planned-by-multiselect`}
              options={plannedByOptions}
              value={filters.userName}
              onChange={(values: string[]) =>
                setFilters((prev) => ({ ...prev, userName: values }))
              }
              placeholder={tCampaigns("filter.select_options")}
              maxVisibleChips={2}
              searchable={true}
              showSelectAll={true}
              selectAllLabel={tCampaigns("filter.allUsers")}
            />
          </div>

          {/* Period Filter */}
          <div id={`${modalId}-period-filter`} className="space-y-2">
            <Label info={tCampaigns("filter.tooltip_period")}>
              {tCampaigns("filter.period")}
            </Label>
            <DateRangePicker
              id={`${modalId}-period-picker`}
              value={filters.period || undefined}
              onChange={(range) => {
                setFilters((prev) => ({ ...prev, period: range }));
                // Clear error when both dates are selected
                if (range?.from && range?.to) {
                  setPeriodError("");
                }
              }}
              onBlur={() => {
                // Validate on blur: if from is selected but to is not
                if (filters.period?.from && !filters.period?.to) {
                  setPeriodError(tCampaigns("filter.selectEndDateError"));
                } else {
                  setPeriodError("");
                }
              }}
              placeholder={tCampaigns("filter.select_date_range")}
              className="w-full"
              error={periodError}
            />
          </div>
          {/* Campaign Goal Filter */}
          <div id={`${modalId}-campaign-goal-filter`} className="space-y-2">
            <Label info={tCampaigns("filter.tooltip_campaign_goal")}>
              {tCampaigns("filter.campaign_goal")}
            </Label>
            <MultiSelect
              id={`${modalId}-campaign-goal-multiselect`}
              options={campaignGoalOptions}
              value={filters.campaignGoal}
              onChange={(values: string[]) =>
                setFilters((prev) => ({ ...prev, campaignGoal: values }))
              }
              placeholder={tCampaigns("filter.select_options")}
              maxVisibleChips={2}
              searchable={true}
              showSelectAll={true}
              selectAllLabel={tCampaigns("filter.allGoals")}
            />
          </div>
        </div>
      </div>
    </ModalDrawer>
  );
};

export default CampaignFilterModal;
