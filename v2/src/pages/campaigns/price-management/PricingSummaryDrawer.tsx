import {
  CustomFeeForm,
  type CustomFeeFormErrors,
} from "@components/campaigns/CustomFeeForm";
import { Accordion } from "@components/ui/Accordion";
import { Button } from "@components/ui/Button";
import { Card } from "@components/ui/card";
import { Checkbox } from "@components/ui/Checkbox";
import { ModalDrawer } from "@components/ui/ModalDrawer";
import { useAnnounce } from "@hooks/useAnnounce";
import {
  useLazyGetPriceSummaryQuery,
  useBulkUpdateCustomFeesMutation,
  useUpdateCustomFeeMutation,
  useUpdateInventoryDiscountMutation,
  useAcceptAllPricesMutation,
  useLazyGetCampaignSchedulePricesQuery,
} from "@services/inventory/inventorySlice";
import { useAppSelector } from "@store";
import { useTranslate } from "@tolgee/react";
import { formatCurrency } from "@utils/campaign.utils";
import {
  ArrowRight,
  Plus,
  Trash2,
  TrendingUp,
  TrendingDown,
} from "lucide-react";
import React, {
  useState,
  useEffect,
  useMemo,
  useRef,
  useCallback,
} from "react";
import { PriceSummaryCustomFee } from "src/types/inventory.types";

import { getPendingPriceDelta, PendingPriceEdits } from "./types";

export interface CustomFee {
  id: string;
  feeName: string;
  type: "Percentage" | "Fixed";
  value: string;
  basedOn: "Base Cost";
  description: string;
  includeInMediaPlan: boolean;
  companyId?: string;
  campaignId?: string;
}

export interface PricingSummaryData {
  currentPrice: number;
  proposedPrice: number;
  mediaCost: {
    current: number;
    proposed: number;
  };
  standardFees: {
    current: number;
    proposed: number;
  };
  customFees?: CustomFee[];
}

interface PricingSummaryDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  currency?: string;
  data?: PricingSummaryData;
  campaignId?: string;
  onSuccess?: () => void;
  /** Unsaved inline proposed-price edits, staged from the table. */
  pendingPriceEdits?: PendingPriceEdits;
  /** Called after the staged price edits have been persisted. */
  onPriceEditsSaved?: () => void;
}

export const PricingSummaryDrawer: React.FC<PricingSummaryDrawerProps> = ({
  isOpen,
  onClose,
  currency = "MYR",
  data,
  campaignId,
  onSuccess,
  pendingPriceEdits,
  onPriceEditsSaved,
}) => {
  const { t } = useTranslate(["price"]);
  const { t: tCommon } = useTranslate(["common"]);
  const { showSuccess, showError } = useAnnounce();
  const user = useAppSelector((s) => s.profile.profile);
  const [fetchPriceSummary] = useLazyGetPriceSummaryQuery();
  const [bulkUpdateCustomFees] = useBulkUpdateCustomFeesMutation();
  const [updateCustomFee] = useUpdateCustomFeeMutation();
  const [updateInventoryDiscount] = useUpdateInventoryDiscountMutation();
  const [acceptAllPrices] = useAcceptAllPricesMutation();
  const [fetchSchedulePrices] = useLazyGetCampaignSchedulePricesQuery();

  const pendingPriceEditEntries = useMemo(
    () => Object.entries(pendingPriceEdits ?? {}),
    [pendingPriceEdits],
  );
  const hasPendingPriceEdits = pendingPriceEditEntries.length > 0;

  // Get user's companyId
  const userCompanyId =
    user?.activeCompanyId || user?.memberships?.[0]?.company_id;
  const [customFees, setCustomFees] = useState<CustomFee[]>([]);
  const [requiresApproval, setRequiresApproval] = useState(false);
  const [originalCustomFees, setOriginalCustomFees] = useState<CustomFee[]>([]);
  const [customFeeErrors, setCustomFeeErrors] = useState<
    Record<string, CustomFeeFormErrors>
  >({});
  const [priceSummaryData, setPriceSummaryData] = useState<
    PricingSummaryData | undefined
  >(data);
  const priceSummaryFetchedRef = useRef(false);

  // Transform API response to PricingSummaryData format
  const transformPriceSummaryData = useCallback(
    (apiData: {
      currentPrice: number;
      proposedPrice: number;
      changeInPrice: number;
      changeInPercentage: number;
      mediaCost: number;
      discountedMediaCost: number;
      standardFees: number;
      customFees: Array<{
        id: string;
        name: string;
        description: string;
        type: string;
        value: number;
        basedOn: string;
        isIncludeInMediaPlan: boolean;
        effectiveCustomFee: number;
        companyId: string;
        campaignId: string;
      }>;
      isAllApproved: boolean;
    }): PricingSummaryData => {
      // Transform custom fees
      const transformedCustomFees: CustomFee[] = apiData.customFees.map(
        (fee) => ({
          id: fee.id,
          feeName: fee.name,
          type: fee.type === "PERCENTAGE" ? "Percentage" : "Fixed",
          value: fee.value.toString(),
          basedOn: "Base Cost",
          description: fee.description,
          includeInMediaPlan: fee.isIncludeInMediaPlan,
          companyId: fee.companyId,
          campaignId: fee.campaignId,
        }),
      );

      return {
        currentPrice: apiData.currentPrice,
        proposedPrice: apiData.proposedPrice,
        mediaCost: {
          current: apiData.mediaCost,
          proposed: apiData.discountedMediaCost,
        },
        standardFees: {
          // API only provides one standardFees value, use it for both current and proposed
          current: apiData.standardFees,
          proposed: apiData.standardFees,
        },
        customFees: transformedCustomFees,
      };
    },
    [],
  );

  // Load price summary data when drawer opens (only if we don't have data)
  useEffect(() => {
    if (isOpen && campaignId) {
      // Only fetch if we don't already have data for this campaign
      if (!priceSummaryData) {
        // Check if we're already fetching to avoid duplicate calls
        if (!priceSummaryFetchedRef.current) {
          priceSummaryFetchedRef.current = true;
          fetchPriceSummary({ campaignId })
            .unwrap()
            .then((response) => {
              if (response.success && response.data) {
                const transformedData = transformPriceSummaryData(
                  response.data,
                );
                setPriceSummaryData(transformedData);
              }
              priceSummaryFetchedRef.current = false;
            })
            .catch((error) => {
              console.error("Error loading price summary:", error);
              showError(
                t("errors.failed_to_load_summary", {
                  defaultValue: "Failed to load price summary",
                }),
              );
              // Reset ref on error so we can retry
              priceSummaryFetchedRef.current = false;
            });
        }
      }
    }
    // Don't reset data when drawer closes - keep it cached for next time
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, campaignId]);

  // Update local state when data prop changes
  useEffect(() => {
    if (data) {
      setPriceSummaryData(data);
    }
  }, [data]);

  const summaryData: PricingSummaryData =
    priceSummaryData || ({} as PricingSummaryData);

  // /price-summary only knows about saved prices. The drawer is where staged
  // inline edits get committed, so the proposed total has to reflect what the
  // user is about to save - otherwise they approve a number that contradicts
  // the table. Applied as a delta so server-side fees stay intact.
  const pendingPriceDelta = useMemo(
    () => getPendingPriceDelta(pendingPriceEdits ?? {}),
    [pendingPriceEdits],
  );
  const proposedPriceWithPendingEdits =
    (summaryData.proposedPrice ?? 0) + pendingPriceDelta;

  /**
   * Every campaignInventoryScheduleId in the campaign.
   *
   * /accept takes an explicit id list, but the table is paginated and only ever
   * holds one page - so the ids have to be gathered here. A large page size
   * keeps this to a single request for typical campaigns; the loop is the
   * safety net for larger ones.
   */
  const collectAllScheduleIds = useCallback(
    async (id: string): Promise<string[]> => {
      const PAGE_SIZE = 200;
      const ids: string[] = [];
      let page = 0;
      let totalPages = 1;

      do {
        const response = await fetchSchedulePrices({
          campaignId: id,
          params: { page, size: PAGE_SIZE },
        }).unwrap();

        const content = response.data?.content ?? [];
        content.forEach((item) => {
          if (item.id) ids.push(item.id);
        });

        totalPages = response.data?.totalPages ?? 1;
        page += 1;
      } while (page < totalPages);

      return ids;
    },
    [fetchSchedulePrices],
  );

  // Helper function to check if a fee is editable (has matching campaignId and companyId)
  const isFeeEditable = useCallback(
    (fee: CustomFee): boolean => {
      const hasMatchingCampaignId = Boolean(
        fee.campaignId && fee.campaignId === campaignId,
      );
      const hasMatchingCompanyId = Boolean(
        fee.companyId && fee.companyId === userCompanyId,
      );
      return hasMatchingCampaignId && hasMatchingCompanyId;
    },
    [campaignId, userCompanyId],
  );

  // Store all custom fees when data loads (show all fees)
  useEffect(() => {
    if (
      priceSummaryData?.customFees &&
      Array.isArray(priceSummaryData.customFees)
    ) {
      setCustomFees(priceSummaryData.customFees);
      setOriginalCustomFees(priceSummaryData.customFees);
    } else if (!priceSummaryData?.customFees) {
      setCustomFees([]);
      setOriginalCustomFees([]);
    }
  }, [priceSummaryData?.customFees]);

  // Reset state when drawer closes
  useEffect(() => {
    if (!isOpen) {
      setCustomFees(priceSummaryData?.customFees || []);
      setRequiresApproval(false);
      setCustomFeeErrors({});
    }
  }, [isOpen, priceSummaryData]);

  const calculateChange = (current: number, proposed: number) => {
    const change = proposed - current;
    const percentage = current > 0 ? (change / current) * 100 : 0;
    return { change, percentage };
  };

  const priceChange = calculateChange(
    summaryData.currentPrice,
    proposedPriceWithPendingEdits,
  );

  const handleAddFee = () => {
    const newFee: CustomFee = {
      id: `fee-${Date.now()}`,
      feeName: "",
      type: "Percentage",
      value: "",
      basedOn: "Base Cost",
      description: "",
      includeInMediaPlan: false,
      campaignId: campaignId,
      companyId: userCompanyId,
    };
    setCustomFees([...customFees, newFee]);
  };

  const handleRemoveFee = async (id: string) => {
    const feeToDelete = customFees.find((fee) => fee.id === id);
    if (!feeToDelete) return;

    // Only allow deletion of editable fees
    if (!isFeeEditable(feeToDelete)) {
      showError(
        t("errors.cannot_delete_readonly_fee", {
          defaultValue: "Cannot delete read-only custom fee",
        }),
      );
      return;
    }

    // If it's a new fee (starts with "fee-"), just remove it from state
    if (id.startsWith("fee-")) {
      setCustomFees(customFees.filter((fee) => fee.id !== id));
      return;
    }

    // If it's an existing fee, call the delete API
    try {
      // Transform fee to API format with isActive: false for deletion
      const apiFee: Omit<PriceSummaryCustomFee, "id"> = {
        name: feeToDelete.feeName,
        description: feeToDelete.description,
        type: (feeToDelete.type === "Percentage" ? "PERCENTAGE" : "VALUE") as
          | "PERCENTAGE"
          | "VALUE",
        value: parseFloat(feeToDelete.value) || 0,
        basedOn: "BASE_COST",
        isIncludeInMediaPlan: feeToDelete.includeInMediaPlan,
        isActive: false, // Set to false for deletion
        campaignId: feeToDelete.campaignId || campaignId || "",
        companyId: feeToDelete.companyId || userCompanyId || "",
        createdAt: "", // Server will ignore this on update
        updatedAt: "", // Server will ignore this on update
        effectiveCustomFee: 0, // Server will recalculate this
      };

      await updateCustomFee({ id, data: apiFee }).unwrap();

      showSuccess(
        t("success.custom_fee_deleted", {
          defaultValue: "Custom fee deleted successfully",
        }),
      );

      // Refresh price summary data after successful deletion
      if (campaignId) {
        priceSummaryFetchedRef.current = false;
        const response = await fetchPriceSummary({ campaignId }).unwrap();
        if (response.success && response.data) {
          const transformedData = transformPriceSummaryData(response.data);
          setPriceSummaryData(transformedData);
        }
      }

      // Remove from local state after successful deletion
      setCustomFees(customFees.filter((fee) => fee.id !== id));
    } catch (error) {
      const errorMessage =
        error && typeof error === "object" && "data" in error
          ? (error.data as { error?: { message?: string } })?.error?.message
          : t("errors.failed_to_delete_custom_fee", {
              defaultValue: "Failed to delete custom fee",
            });
      showError(
        errorMessage ||
          t("errors.failed_to_delete_custom_fee", {
            defaultValue: "Failed to delete custom fee",
          }),
      );
      throw error; // Re-throw to prevent removing from UI if API call fails
    }
  };

  const handleFeeChange = (
    id: string,
    field: keyof CustomFee,
    value: string | boolean,
  ) => {
    const fee = customFees.find((f) => f.id === id);
    // Only allow changes to editable fees
    if (!fee || !isFeeEditable(fee)) {
      return;
    }
    setCustomFees(
      customFees.map((fee) =>
        fee.id === id ? { ...fee, [field]: value } : fee,
      ),
    );
    if (field === "feeName" || field === "value") {
      setCustomFeeErrors((prev) => {
        const next = { ...prev };
        if (!next[id]) return next;
        const updated = { ...next[id] };
        if (field === "feeName") delete updated.feeName;
        if (field === "value") delete updated.value;
        if (Object.keys(updated).length === 0) {
          const { [id]: _, ...rest } = next;
          return rest;
        }
        next[id] = updated;
        return next;
      });
    }
  };

  const handleSave = async () => {
    if (!campaignId) {
      showError(
        t("errors.campaign_id_required", {
          defaultValue: "Campaign ID is required",
        }),
      );
      return;
    }

    // Only get editable fees that have been added or edited
    const editableFees = customFees.filter((fee) => isFeeEditable(fee));

    // Find new fees (fees with IDs starting with "fee-")
    const newFees = editableFees.filter((fee) => fee.id.startsWith("fee-"));

    // Find edited fees (existing fees that have changed)
    const editedFees = editableFees.filter((fee) => {
      if (fee.id.startsWith("fee-")) return false; // Skip new fees
      const originalFee = originalCustomFees.find((of) => of.id === fee.id);
      if (!originalFee) return false;

      return (
        fee.feeName !== originalFee.feeName ||
        fee.type !== originalFee.type ||
        fee.value !== originalFee.value ||
        fee.basedOn !== originalFee.basedOn ||
        fee.description !== originalFee.description ||
        fee.includeInMediaPlan !== originalFee.includeInMediaPlan
      );
    });

    // Combine new and edited fees
    const feesToSave = [...newFees, ...editedFees];
    const hasFeeChanges = feesToSave.length > 0;

    if (!hasFeeChanges && !hasPendingPriceEdits) {
      showError(
        t("errors.no_changes_to_save", {
          defaultValue: "No changes to save",
        }),
      );
      return;
    }

    const validationErrors: Record<string, CustomFeeFormErrors> = {};
    for (const fee of feesToSave) {
      const feeErrors: CustomFeeFormErrors = {};
      const nameTrimmed = (fee.feeName ?? "").trim();
      const valueTrimmed = (fee.value ?? "").trim();
      if (!nameTrimmed) {
        feeErrors.feeName = t(
          "drawers.pricing_summary.validation_fee_name_required",
          { defaultValue: "Fee name is required" },
        );
      }
      if (!valueTrimmed || Number.isNaN(parseFloat(valueTrimmed))) {
        feeErrors.value = t(
          "drawers.pricing_summary.validation_value_required",
          { defaultValue: "Value is required" },
        );
      }
      if (
        fee.type === "Percentage" &&
        valueTrimmed &&
        !Number.isNaN(parseFloat(valueTrimmed)) &&
        parseFloat(valueTrimmed) > 100
      ) {
        feeErrors.value = t(
          "drawers.pricing_summary.validation_percentage_max",
          { defaultValue: "Percentage must be between 0 and 100" },
        );
      }
      if (Object.keys(feeErrors).length > 0) {
        validationErrors[fee.id] = feeErrors;
      }
    }

    if (Object.keys(validationErrors).length > 0) {
      setCustomFeeErrors(validationErrors);
      showError(
        t("drawers.pricing_summary.validation_fill_required", {
          defaultValue:
            "Please fill in fee name and value for all custom fees.",
        }),
      );
      return;
    }

    setCustomFeeErrors({});

    try {
      if (hasFeeChanges) {
        // Transform to API format
        const apiCustomFees = feesToSave.map((fee) => {
          const baseFeeData = {
            name: fee.feeName,
            description: fee.description,
            type: fee.type === "Percentage" ? "PERCENTAGE" : "VALUE",
            value: parseFloat(fee.value) || 0,
            basedOn: "BASE_COST",
            isIncludeInMediaPlan: fee.includeInMediaPlan,
            isActive: true,
            campaignId: fee.campaignId || campaignId || "",
            companyId: fee.companyId || userCompanyId || "",
            createdAt: "", // Server will set this on create
            updatedAt: "", // Server will set this on update
            effectiveCustomFee: 0, // Server will calculate this
          };

          // Only include id for existing fees (not new fees)
          const isNewFee = fee.id.startsWith("fee-");
          return isNewFee ? baseFeeData : { ...baseFeeData, id: fee.id };
        });

        const data = apiCustomFees as PriceSummaryCustomFee[];

        await bulkUpdateCustomFees({ data }).unwrap();
      }

      if (hasPendingPriceEdits) {
        // No bulk endpoint for proposed prices yet - persist each staged
        // edit with its own request.
        await Promise.all(
          pendingPriceEditEntries.map(([, edit]) =>
            updateInventoryDiscount({
              campaignInventoryScheduleId: edit.campaignInventoryScheduleId,
              data: edit.isInventoryRow
                ? { proposedPrice: edit.newPrice }
                : { proposedPrice: edit.newPrice, scheduleId: edit.scheduleId },
            }).unwrap(),
          ),
        );

        // Setting a price and agreeing to it are separate server-side states,
        // so /accept runs last - after every price is persisted - and covers
        // the whole campaign, not just the rows that were edited.
        //
        // Best-effort on purpose: the prices are already saved by this point, so
        // a failure here must not be reported as a failed save or surface a
        // toast (the endpoint also sets suppressErrorToast). Acceptance is
        // reconciled by a manual step downstream.
        try {
          const scheduleIds = await collectAllScheduleIds(campaignId);
          await acceptAllPrices({
            campaignId,
            data: { campaignInventorySchedulesIds: scheduleIds },
          }).unwrap();
        } catch (acceptError) {
          console.error("Error accepting prices:", acceptError);
        }

        onPriceEditsSaved?.();
      }

      const successMessage =
        hasFeeChanges && hasPendingPriceEdits
          ? t("success.changes_saved", {
              defaultValue: "Changes saved successfully",
            })
          : hasPendingPriceEdits
            ? t("success.price_changes_saved", {
                defaultValue: "Price changes saved successfully",
              })
            : t("success.custom_fees_saved", {
                defaultValue: "Custom fees saved successfully",
              });
      showSuccess(successMessage);

      // Refresh price summary data after successful save
      if (campaignId) {
        // Reset ref to allow fetching updated data
        priceSummaryFetchedRef.current = false;
        const response = await fetchPriceSummary({ campaignId }).unwrap();
        if (response.success && response.data) {
          const transformedData = transformPriceSummaryData(response.data);
          setPriceSummaryData(transformedData);
        }
      }

      // Call success callback to refresh data
      onSuccess?.();
      onClose();
    } catch (error) {
      const errorMessage =
        error && typeof error === "object" && "data" in error
          ? (error.data as { error?: { message?: string } })?.error?.message
          : t("errors.failed_to_save_changes", {
              defaultValue: "Failed to save changes",
            });
      showError(
        errorMessage ||
          t("errors.failed_to_save_changes", {
            defaultValue: "Failed to save changes",
          }),
      );
    }
  };

  // Closing (Cancel/X) only reverts unsaved custom-fee edits - staged price
  // edits stay intact so the table keeps showing the edited value. They're
  // only discarded from the price-management page's leave action - see
  // CampaignPriceManagement's handleBack.
  const handleClose = () => {
    setCustomFees(priceSummaryData?.customFees || []);
    setOriginalCustomFees(priceSummaryData?.customFees || []);
    setRequiresApproval(false);
    setCustomFeeErrors({});
    onClose();
  };

  // Check if editable custom fees have been modified (added or edited)
  const hasCustomFeesChanges = useMemo(() => {
    const editableFees = customFees.filter((fee) => isFeeEditable(fee));

    // Check if new editable fees were added
    const hasNewFees = editableFees.some((fee) => fee.id.startsWith("fee-"));

    // Check if existing editable fees were edited
    const hasEditedFees = editableFees.some((fee) => {
      if (fee.id.startsWith("fee-")) return false;
      const originalFee = originalCustomFees.find((of) => of.id === fee.id);
      if (!originalFee) return false;

      return (
        fee.feeName !== originalFee.feeName ||
        fee.type !== originalFee.type ||
        fee.value !== originalFee.value ||
        fee.basedOn !== originalFee.basedOn ||
        fee.description !== originalFee.description ||
        fee.includeInMediaPlan !== originalFee.includeInMediaPlan
      );
    });

    // Check if editable fees were removed
    const editableOriginalFees = originalCustomFees.filter((fee) =>
      isFeeEditable(fee),
    );
    const hasRemovedFees =
      editableOriginalFees.length > editableFees.length ||
      editableOriginalFees.some(
        (originalFee) => !editableFees.find((cf) => cf.id === originalFee.id),
      );

    return hasNewFees || hasEditedFees || hasRemovedFees;
  }, [customFees, originalCustomFees, isFeeEditable]);

  // Enable save button only when:
  // 1. Approval checkbox is checked AND
  // 2. Custom fees have been added or edited
  const isSaveDisabled =
    !requiresApproval || (!hasCustomFeesChanges && !hasPendingPriceEdits);

  return (
    <ModalDrawer
      isOpen={isOpen}
      onClose={handleClose}
      title={t("drawers.pricing_summary.title")}
      size="lg"
      position="right"
      id="pricing-summary-drawer"
      showBackButton={false}
      footer={
        <div className="flex flex-col gap-4">
          {hasPendingPriceEdits && (
            <p className="text-sm font-medium text-mw-primary-500">
              {pendingPriceEditEntries.length}{" "}
              {t("drawers.pricing_summary.pending_price_changes")}
            </p>
          )}
          <div className="flex justify-end gap-3">
            <Button variant="outline" onClick={handleClose}>
              {tCommon("buttons.cancel")}
            </Button>
            <Button
              variant="primary"
              onClick={handleSave}
              disabled={isSaveDisabled}
            >
              {t("drawers.pricing_summary.save_changes")}
            </Button>
          </div>
        </div>
      }
    >
      <div className="space-y-4">
        {/* Pricing Comparison Section */}
        <div className="space-y-4">
          <p className="text-sm font-normal text-mw-neutral-500 leading-4">
            {t("drawers.pricing_summary.description")}
          </p>
          <div className="flex items-center gap-4">
            {/* Current Price */}
            <Card className="p-4 flex-1">
              <div className="space-y-1">
                <p className="text-sm font-normal text-mw-neutral-700 dark:text-mw-neutral-400 mb-2 leading-4">
                  {t("drawers.pricing_summary.current_price")}
                </p>
                <p className="text-lg font-semibold text-mw-primary-500 leading-6">
                  {formatCurrency(summaryData.currentPrice, currency)}
                </p>
              </div>
            </Card>

            {/* Arrow */}
            <ArrowRight className="w-6 h-6 text-mw-black" />

            {/* Proposed Price */}
            <Card className="p-4 flex-1">
              <div className="space-y-1">
                <p className="text-sm font-normal text-mw-neutral-700 dark:text-mw-neutral-400 mb-2 leading-4">
                  {t("drawers.pricing_summary.proposed_price")}
                </p>
                <p className="text-lg font-semibold text-mw-primary-500 leading-6">
                  {formatCurrency(proposedPriceWithPendingEdits, currency)}
                </p>
              </div>
            </Card>
          </div>

          {/* Change in Price */}
          <div className="flex items-center gap-2">
            <p className="text-sm font-normal text-mw-neutral-500 dark:text-mw-neutral-300 leading-4">
              {t("drawers.pricing_summary.change_in_price")}
            </p>
            <p className="text-sm font-normal text-mw-neutral-900 dark:text-white leading-4">
              {formatCurrency(Math.abs(priceChange.change), currency)}
            </p>
            <p
              className={`text-sm font-normal leading-4 flex items-center gap-1 ${
                priceChange.change < 0
                  ? "text-mw-error-500"
                  : priceChange.change > 0
                    ? "text-mw-success-500"
                    : "text-mw-neutral-500"
              }`}
            >
              {Math.abs(priceChange.percentage).toFixed(0)}%
              {priceChange.change < 0 && (
                <TrendingDown className="w-4 h-4 text-mw-error-500" />
              )}
              {priceChange.change > 0 && (
                <TrendingUp className="w-4 h-4 text-mw-success-500" />
              )}
            </p>
          </div>
        </div>
        <div className="border-t border-container-border"></div>
        {/* Breakdown Section */}
        <div className="space-y-4">
          <h3 className="text-base font-medium text-mw-neutral-500 dark:text-white">
            {t("drawers.pricing_summary.breakdown")}
          </h3>
          <div className="space-y-4">
            {/* Media Cost */}
            <div className="flex items-center justify-between">
              <p className="text-sm font-normal text-mw-neutral-500 dark:text-mw-neutral-300">
                {t("drawers.pricing_summary.media_cost")}
              </p>
              <div className="flex items-center gap-2">
                <p className="text-sm font-normal text-mw-neutral-700 dark:text-mw-neutral-400">
                  {formatCurrency(summaryData.mediaCost?.current, currency)}
                </p>
                <ArrowRight className="w-3 h-3 text-mw-neutral-300" />
                <p className="text-sm font-normal text-mw-neutral-700 dark:text-white">
                  {formatCurrency(summaryData.mediaCost?.proposed, currency)}
                </p>
              </div>
            </div>

            {/* Standard Fees */}
            <div className="flex items-center justify-between">
              <p className="text-sm font-normal text-mw-neutral-500 dark:text-mw-neutral-300">
                {t("drawers.pricing_summary.standard_fees")}
              </p>
              <div className="flex items-center gap-2">
                <p className="text-sm font-normal text-mw-neutral-700 dark:text-mw-neutral-400">
                  {formatCurrency(0, currency)}
                </p>
                <ArrowRight className="w-3 h-3 text-mw-neutral-300" />
                <p className="text-sm font-normal text-mw-neutral-700 dark:text-white">
                  {formatCurrency(summaryData.standardFees?.proposed, currency)}
                </p>
              </div>
            </div>
            <div className="border-t border-container-border"></div>
          </div>
        </div>

        {/* Custom Fees Section */}
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-base font-medium text-mw-neutral-900 dark:text-white">
              {t("drawers.pricing_summary.custom_fees")}
            </h3>
            <Button
              variant="outline"
              size="sm"
              onClick={handleAddFee}
              className="text-mw-primary-500 outline-mw-primary-500"
            >
              <Plus className="h-4 w-4 mr-2" />
              {t("drawers.pricing_summary.add_fee")}
            </Button>
          </div>

          {customFees.length === 0 ? (
            <p className="text-sm text-mw-neutral-500 text-center py-4">
              {t("drawers.pricing_summary.no_custom_fees")}
            </p>
          ) : (
            <div className="space-y-4">
              {customFees.map((fee, index) => {
                const isEditable = isFeeEditable(fee);
                return (
                  <Accordion
                    key={fee.id}
                    title={`${t("drawers.pricing_summary.fee_number")}${index + 1}`}
                    defaultExpanded={true}
                    headerActions={
                      isEditable ? (
                        <button
                          onClick={() => handleRemoveFee(fee.id)}
                          className="p-1.5 hover:bg-mw-error-50 rounded transition-colors"
                          aria-label={t("costBreakdown.ariaDeleteFee")}
                        >
                          <Trash2 className="w-3 h-3 text-mw-error-500" />
                        </button>
                      ) : null
                    }
                  >
                    <CustomFeeForm
                      feeName={fee.feeName}
                      type={fee.type}
                      value={fee.value}
                      basedOn={fee.basedOn}
                      description={fee.description}
                      includeInMediaPlan={fee.includeInMediaPlan}
                      onFeeNameChange={(val) =>
                        handleFeeChange(fee.id, "feeName", val)
                      }
                      onTypeChange={(val) =>
                        handleFeeChange(fee.id, "type", val)
                      }
                      onValueChange={(val) =>
                        handleFeeChange(fee.id, "value", val)
                      }
                      onBasedOnChange={(val) =>
                        handleFeeChange(fee.id, "basedOn", val)
                      }
                      onDescriptionChange={(val) =>
                        handleFeeChange(fee.id, "description", val)
                      }
                      onIncludeInMediaPlanChange={(checked) =>
                        handleFeeChange(fee.id, "includeInMediaPlan", checked)
                      }
                      disabled={!isEditable}
                      readOnly={!isEditable}
                      errors={customFeeErrors[fee.id]}
                      translationNamespace="price"
                      idPrefix={`fee-${fee.id}`}
                    />
                  </Accordion>
                );
              })}
            </div>
          )}
        </div>

        <div className="flex flex-col gap-1 bg-mw-neutral-50 pl-4 p-2 rounded-sm">
          <Checkbox
            label={t("drawers.pricing_summary.campaign_approval_required")}
            id="campaign-approval-checkbox"
            checked={requiresApproval}
            onChange={(e) => setRequiresApproval(e.target.checked)}
          />
          {/* Reassures the user that saving here already pushes the prices to
              the media plan - the approval step happens separately, after. */}
          <p className="pl-6 text-xs text-mw-neutral-500 dark:text-mw-neutral-400">
            {t("drawers.pricing_summary.campaign_approval_note")}
          </p>
        </div>
      </div>
    </ModalDrawer>
  );
};
