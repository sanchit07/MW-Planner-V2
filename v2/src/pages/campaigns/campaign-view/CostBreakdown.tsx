import { Button } from "@components/ui/Button";
import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import { Modal } from "@components/ui/Modal";
import { useAnnounce } from "@hooks/useAnnounce";
import {
  useBulkUpdateCustomFeesMutation,
  useLazyGetPriceSummaryQuery,
  useUpdateCustomFeeMutation,
} from "@services/inventory/inventorySlice";
import { useAppSelector } from "@store";
import { useTranslate } from "@tolgee/react";
import { formatCurrencyWithLocale } from "@utils/currency";
import { Edit, Plus, Trash2 } from "lucide-react";
import React, { useState, useCallback } from "react";
import {
  PriceSummaryResponse,
  PriceSummaryCustomFee,
} from "src/types/inventory.types";

import { AddCustomFeeDrawer } from "./AddCustomFeeDrawer";
import { CustomFee } from "../price-management/PricingSummaryDrawer";

interface CostBreakdownProps {
  costBreakDownData: PriceSummaryResponse | undefined;
  currency?: string;
  campaignId?: string;
  onRefresh?: () => void;
}

const CostBreakdown: React.FC<CostBreakdownProps> = ({
  costBreakDownData,
  currency = "",
  campaignId,
  onRefresh,
}) => {
  const { t } = useTranslate(["price"]);
  const { showSuccess, showError } = useAnnounce();
  const user = useAppSelector((s) => s.profile.profile);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [feeToDelete, setFeeToDelete] = useState<{
    id: string;
    name: string;
  } | null>(null);
  const [feeToEdit, setFeeToEdit] = useState<PriceSummaryCustomFee | null>(
    null,
  );
  const [fetchPriceSummary] = useLazyGetPriceSummaryQuery();
  const [bulkUpdateCustomFees] = useBulkUpdateCustomFeesMutation();
  const [updateCustomFee] = useUpdateCustomFeeMutation();

  // Get user's companyId
  const userCompanyId =
    user?.activeCompanyId || user?.memberships?.[0]?.company_id;

  // Helper function to check if a fee is editable (has matching campaignId and companyId)
  const isFeeEditable = useCallback(
    (fee: PriceSummaryCustomFee): boolean => {
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

  // Show all custom fees (no filtering)
  const allCustomFees = costBreakDownData?.customFees || [];

  // Helper function to map basedOn from API format to display format
  const mapBasedOnToDisplay = (basedOn: string): string => {
    const mapping: Record<string, string> = {
      BASE_COST: t("costBreakdown.baseCost"),
    };
    return mapping[basedOn] || basedOn;
  };

  // Helper function to map basedOn from display format to API format
  const mapBasedOnToAPI = (basedOn: string): "BASE_COST" => {
    const mapping: Record<string, "BASE_COST"> = {
      [t("costBreakdown.baseCost")]: "BASE_COST",
    };
    return mapping[basedOn] || "BASE_COST";
  };

  // Helper function to format fee display
  const formatFeeDisplay = (fee: PriceSummaryCustomFee): string => {
    const feeType = fee.type || "";
    const feeValue = fee.value || 0;
    const basedOn = fee.basedOn || "BASE_COST";

    if (feeType === "PERCENTAGE") {
      const basedOnDisplay = mapBasedOnToDisplay(basedOn);
      return `${feeValue}${t("costBreakdown.percentOf")} ${basedOnDisplay}`;
    } else if (feeType === "VALUE") {
      return `${formatCurrencyWithLocale(feeValue, currency)} ${t("costBreakdown.fixed")}`;
    }
    return "--";
  };

  const handleSaveCustomFees = useCallback(
    async (
      customFees: CustomFee[],
      _requiresApproval: boolean,
      campaignIdParam?: string,
    ) => {
      if (!campaignIdParam) {
        showError(
          t("errors.campaign_id_required", {
            defaultValue: "Campaign ID is required",
          }),
        );
        return;
      }

      try {
        // Only get editable fees (fees with matching campaignId and companyId)
        const editableFees = customFees.filter((fee) => {
          const hasMatchingCampaignId = fee.campaignId === campaignIdParam;
          const hasMatchingCompanyId = fee.companyId === userCompanyId;
          return hasMatchingCampaignId && hasMatchingCompanyId;
        });

        if (editableFees.length === 0) {
          showError(
            t("errors.no_editable_fees", {
              defaultValue: "No editable fees to save",
            }),
          );
          return;
        }

        // Transform to API format
        const apiCustomFees = editableFees.map((fee) => {
          const baseFee = {
            name: fee.feeName,
            description: fee.description,
            type: (fee.type === "Percentage" ? "PERCENTAGE" : "VALUE") as
              | "PERCENTAGE"
              | "VALUE",
            value: parseFloat(fee.value) || 0,
            basedOn: mapBasedOnToAPI(fee.basedOn),
            isIncludeInMediaPlan: fee.includeInMediaPlan,
            isActive: true,
            campaignId: fee.campaignId || campaignIdParam || "",
            companyId: fee.companyId || userCompanyId || "",
            createdAt: "", // Server will set this
            updatedAt: "", // Server will set this
            effectiveCustomFee: 0, // Server will calculate this
          };

          // Only include id for existing fees (not new fees)
          const isNewFee = fee.id.startsWith("fee-");
          return isNewFee ? baseFee : { ...baseFee, id: fee.id };
        });

        const data = apiCustomFees as PriceSummaryCustomFee[];

        await bulkUpdateCustomFees({ data }).unwrap();

        showSuccess(
          t("success.custom_fees_saved", {
            defaultValue: "Custom fees saved successfully",
          }),
        );

        // Refresh price summary data after successful save
        if (campaignIdParam) {
          await fetchPriceSummary({ campaignId: campaignIdParam }).unwrap();
          // Trigger parent refresh if callback provided
          if (onRefresh) {
            onRefresh();
          }
        }
      } catch (error) {
        const errorMessage =
          error && typeof error === "object" && "data" in error
            ? (error.data as { error?: { message?: string } })?.error?.message
            : t("errors.failed_to_save_custom_fees", {
                defaultValue: "Failed to save custom fees",
              });
        showError(
          errorMessage ||
            t("errors.failed_to_save_custom_fees", {
              defaultValue: "Failed to save custom fees",
            }),
        );
        throw error; // Re-throw to prevent closing drawer on error
      }
    },
    [
      bulkUpdateCustomFees,
      fetchPriceSummary,
      onRefresh,
      showError,
      showSuccess,
      t,
      userCompanyId,
    ],
  );

  const handleDeleteClick = (fee: PriceSummaryCustomFee) => {
    const feeId = fee.id;
    const feeName = fee.name || "--";
    if (feeId) {
      setFeeToDelete({ id: feeId, name: feeName });
      setIsDeleteModalOpen(true);
    }
  };

  const handleCancelDelete = () => {
    setIsDeleteModalOpen(false);
    setFeeToDelete(null);
  };

  const handleConfirmDelete = useCallback(async () => {
    if (!feeToDelete || !campaignId) {
      setIsDeleteModalOpen(false);
      setFeeToDelete(null);
      return;
    }

    try {
      // Get the fee data from costBreakDownData
      const fee = costBreakDownData?.customFees?.find(
        (f: PriceSummaryCustomFee) => f.id === feeToDelete.id,
      );

      if (!fee) {
        showError(t("costBreakdown.feeNotFound"));
        setIsDeleteModalOpen(false);
        setFeeToDelete(null);
        return;
      }

      // Transform fee to API format with isActive: false for deletion
      const apiFee: Omit<PriceSummaryCustomFee, "id"> = {
        name: fee.name || "",
        description: fee.description || "",
        type: (fee.type || "VALUE") as "PERCENTAGE" | "VALUE",
        value: fee.value || 0,
        basedOn: (fee.basedOn || "BASE_COST") as "BASE_COST",
        isIncludeInMediaPlan: fee.isIncludeInMediaPlan || false,
        isActive: false, // Set to false for deletion
        campaignId: fee.campaignId || campaignId || "",
        companyId: fee.companyId || userCompanyId || "",
        createdAt: "", // Server will ignore this on update
        updatedAt: "", // Server will ignore this on update
        effectiveCustomFee: 0, // Server will recalculate this
      };

      await updateCustomFee({ id: feeToDelete.id, data: apiFee }).unwrap();

      showSuccess(
        t("success.custom_fee_deleted", {
          defaultValue: "Custom fee deleted successfully",
        }),
      );

      // Refresh price summary data after successful deletion
      if (campaignId) {
        await fetchPriceSummary({ campaignId }).unwrap();
        // Trigger parent refresh if callback provided
        if (onRefresh) {
          onRefresh();
        }
      }

      setIsDeleteModalOpen(false);
      setFeeToDelete(null);
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
      setIsDeleteModalOpen(false);
      setFeeToDelete(null);
    }
  }, [
    feeToDelete,
    campaignId,
    costBreakDownData,
    updateCustomFee,
    fetchPriceSummary,
    onRefresh,
    showError,
    showSuccess,
    t,
  ]);
  return (
    <>
      {/* Media Cost and Custom Fees - Side by Side */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pb-4">
        {/* Media Cost Section */}
        <Card className="p-4 space-y-4">
          <CardHeader>
            <CardTitle className="text-base font-medium leading-5 border-b border-container-border pb-4 pt-2">
              {t("costBreakdown.mediaCost")}
            </CardTitle>
          </CardHeader>
          <CardContent className="pl-0 pr-0 space-y-4">
            <p className="text-sm font-medium text-black leading-4">
              {t("costBreakdown.campaignForecast")}
            </p>
            <div className="space-y-4">
              <div className="flex justify-between items-center">
                <p className="text-sm font-normal text-secondary leading-4">
                  {t("costBreakdown.mediaCost")}
                </p>
                <p className="text-sm font-medium text-black">
                  {formatCurrencyWithLocale(
                    costBreakDownData?.mediaCost,
                    currency,
                  )}
                </p>
              </div>
              <div className="flex justify-between items-center">
                <p className="text-sm font-normal text-secondary leading-4">
                  {t("costBreakdown.platformFee")}
                </p>
                <p className="text-sm font-medium text-black">
                  {formatCurrencyWithLocale(
                    costBreakDownData?.standardFees ?? 0,
                    currency,
                  )}
                </p>
              </div>
              <div className="flex justify-between items-center border-t border-container-border pt-4">
                <p className="text-sm font-normal text-secondary leading-4">
                  {t("costBreakdown.netCost")}
                </p>
                <p className="text-sm font-medium text-mw-primary-500 leading-4">
                  {formatCurrencyWithLocale(
                    costBreakDownData?.mediaCost,
                    currency,
                  )}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Custom Fees Section */}
        <Card>
          <CardHeader className="p-4">
            <div className="flex items-center justify-between border-b border-container-border pb-4">
              <CardTitle className="text-base font-medium leading-5">
                {t("costBreakdown.customFees")}
              </CardTitle>
              <Button
                variant="outline"
                size="sm"
                className="flex items-center gap-2 outline-mw-primary-500 text-mw-primary-500"
                onClick={() => {
                  setFeeToEdit(null);
                  setIsDrawerOpen(true);
                }}
              >
                <Plus className="w-4 h-4" />
                <p className="text-xs font-normal text-mw-primary-500 leading-4">
                  {t("costBreakdown.addFee")}
                </p>
              </Button>
            </div>
          </CardHeader>
          <CardContent>
            {allCustomFees && allCustomFees.length > 0 ? (
              <div className="space-y-4">
                {allCustomFees.map((fee, index) => {
                  const isEditable = isFeeEditable(fee);
                  return (
                    <div
                      key={index}
                      className="flex justify-between items-start p-3 border border-container-border rounded-lg"
                    >
                      <div className="flex-1 space-y-2">
                        <div className="flex items-center justify-between gap-3 border-b border-container-border pb-2">
                          <p className="text-sm font-medium text-mw-neutral-800">
                            {fee.name || "--"}
                          </p>
                          {isEditable && (
                            <div className="flex items-center gap-2">
                              <div className="gap-1 outline -outline-offset-1 outline-mw-primary-500 rounded">
                                <button
                                  className="p-1.5 hover:bg-mw-primary-50 rounded transition-colors"
                                  aria-label={t("costBreakdown.ariaEditFee")}
                                  onClick={() => {
                                    setFeeToEdit(fee);
                                    setIsDrawerOpen(true);
                                  }}
                                >
                                  <Edit className="w-3 h-3 text-mw-primary-500" />
                                </button>
                              </div>
                              <div className="gap-1 outline -outline-offset-1 outline-mw-error-500 rounded">
                                <button
                                  className="p-1.5 hover:bg-mw-error-50 rounded transition-colors"
                                  aria-label={t("costBreakdown.ariaDeleteFee")}
                                  onClick={() => handleDeleteClick(fee)}
                                >
                                  <Trash2 className="w-3 h-3 text-mw-error-500" />
                                </button>
                              </div>
                            </div>
                          )}
                        </div>
                        <div className="flex items-center justify-between">
                          <p className="text-sm font-medium text-black">
                            {formatFeeDisplay(fee)}
                          </p>
                          <p className="text-sm font-medium text-black text-right">
                            {formatCurrencyWithLocale(
                              fee.effectiveCustomFee || 0,
                              currency,
                            )}
                          </p>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <p className="text-sm text-mw-neutral-500 text-center py-4">
                {t("costBreakdown.noCustomFeesAdded")}
              </p>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Total Cost Summary Section */}
      <Card>
        <CardHeader className="p-4">
          <CardTitle className="text-base font-medium leading-5 border-b border-container-border pb-4">
            {t("costBreakdown.totalCostSummary")}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            {/* Media Cost Card */}
            <Card className="p-4 bg-mw-primary-50!">
              <div className="space-y-2">
                <p className="text-sm font-semibold text-mw-neutral-800 leading-4">
                  {t("costBreakdown.mediaCost")}
                </p>
                <p className="text-lg font-semibold text-mw-primary-500 leading-6">
                  {formatCurrencyWithLocale(
                    costBreakDownData?.mediaCost,
                    currency,
                  )}
                </p>
              </div>
            </Card>

            {/* Platform Fee Card */}
            <Card className="p-4 bg-mw-brown-50!">
              <div className="space-y-2">
                <p className="text-sm font-semibold text-mw-neutral-800 leading-4">
                  {t("costBreakdown.platformFee")}
                </p>
                <p className="text-lg font-semibold text-mw-brown-600 leading-6">
                  {formatCurrencyWithLocale(
                    costBreakDownData?.standardFees ?? 0,
                    currency,
                  )}
                </p>
              </div>
            </Card>

            {/* Custom Fees Card */}
            <Card className="p-4 bg-mw-smaragdine-50!">
              <div className="space-y-2">
                <p className="text-sm font-semibold text-mw-neutral-800 leading-4">
                  {t("costBreakdown.customFees")}
                </p>
                <p className="text-lg font-semibold text-mw-smaragdine-600 leading-6">
                  {formatCurrencyWithLocale(
                    allCustomFees.reduce(
                      (sum, fee) => sum + (fee.effectiveCustomFee || 0),
                      0,
                    ),
                    currency,
                  )}
                </p>
              </div>
            </Card>

            {/* Total Cost Card */}
            <Card className="p-4 bg-mw-lime-50!">
              <div className="space-y-2">
                <p className="text-sm font-semibold text-mw-neutral-800 leading-4">
                  {t("costBreakdown.totalCost")}
                </p>
                <p className="text-lg font-semibold text-mw-light-green-600 leading-6">
                  {formatCurrencyWithLocale(
                    costBreakDownData?.proposedPrice,
                    currency,
                  )}
                </p>
              </div>
            </Card>
          </div>
        </CardContent>
      </Card>

      {/* Add Custom Fee Drawer */}
      <AddCustomFeeDrawer
        isOpen={isDrawerOpen}
        onClose={() => {
          setIsDrawerOpen(false);
          setFeeToEdit(null);
        }}
        campaignId={campaignId}
        initialFee={feeToEdit}
        onSave={handleSaveCustomFees}
      />

      {/* Delete Confirmation Modal */}
      <Modal
        isOpen={isDeleteModalOpen}
        onClose={handleCancelDelete}
        title={t("costBreakdown.deleteFee")}
        primaryButtonText={t("costBreakdown.yesDelete")}
        secondaryButtonText={t("costBreakdown.dontDelete")}
        onPrimaryAction={handleConfirmDelete}
        onSecondaryAction={handleCancelDelete}
        primaryButtonVariant="danger"
        size="sm"
      >
        <p>
          {t("costBreakdown.deleteFeeConfirm")}{" "}
          <strong>"{feeToDelete?.name || t("costBreakdown.thisFee")}"</strong>?
        </p>
      </Modal>
    </>
  );
};

export default CostBreakdown;
