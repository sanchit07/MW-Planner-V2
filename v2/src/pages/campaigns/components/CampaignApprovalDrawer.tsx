import { Badge } from "@components/ui/Badge";
import { Button } from "@components/ui/Button";
import { Card, CardContent, CardHeader } from "@components/ui/card";
import { Label } from "@components/ui/Label";
import { Modal } from "@components/ui/Modal";
import { ModalDrawer } from "@components/ui/ModalDrawer";
import { StatusBadge } from "@components/ui/StatusBadge";
import { Textarea } from "@components/ui/Textarea";
import { Tooltip } from "@components/ui/Tooltip";
import { useActiveCompany } from "@hooks/useActiveCompany";
import {
  useLazyGetCampaignApprovalDetailsQuery,
  useUpdateApprovalStatusMutation,
} from "@services/campaign/campaignSlice";
import { useTranslate } from "@tolgee/react";
import { formatCurrency } from "@utils/campaign.utils";
import { formatDisplayDate } from "@utils/dateUtils";
import {
  AlertTriangle,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Circle,
  CircleX,
  Clock,
  Info,
  ReceiptText,
  XCircle,
} from "lucide-react";
import React, { useEffect, useState } from "react";

import { useAppSelector } from "../../../store";

import type { MediaOwnerProgress } from "../../../types/campaign.types";

/** Colored chip styles per media-owner proposal status — mirrors the inbox chips. */
const ownerStatusStyle: Record<string, string> = {
  APPROVED: "bg-mw-success-50 text-mw-success-600 border-mw-success-200",
  NEGOTIATING: "bg-mw-warning-50 text-mw-warning-600 border-mw-warning-200",
  REJECTED: "bg-mw-error-50 text-mw-error-500 border-mw-error-200",
  PENDING: "bg-mw-neutral-50 text-mw-neutral-500 border-mw-neutral-200",
};

const OwnerStatusIcon = ({ status }: { status?: string | null }) => {
  switch (status) {
    case "APPROVED":
      return <CheckCircle2 className="size-3.5" />;
    case "NEGOTIATING":
      return <ReceiptText className="size-3.5" />;
    case "REJECTED":
      return <XCircle className="size-3.5" />;
    default:
      return <Clock className="size-3.5" />;
  }
};

interface CampaignApprovalDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  campaignId: string;
  onApprove?: (campaignId: string, approvalProgressId: string) => void;
  onReject?: (
    campaignId: string,
    reason: string,
    approvalProgressId: string,
  ) => void;
}

export const CampaignApprovalDrawer: React.FC<CampaignApprovalDrawerProps> = ({
  isOpen,
  onClose,
  campaignId,
  onApprove,
  onReject,
}) => {
  const { t } = useTranslate(["campaigns"]);
  const { t: tCommon } = useTranslate(["common"]);
  const [getApprovalDetails, { data: approvalData, isLoading, isError }] =
    useLazyGetCampaignApprovalDetailsQuery();
  const [updateApprovalStatus] = useUpdateApprovalStatusMutation();
  const { companyId: activeCompanyId } = useActiveCompany();

  // Get user profile to check if user is super admin
  const userProfile = useAppSelector((state) => state.profile.profile);
  const isSuperAdmin = userProfile?.is_global_admin === true;

  // State to track which approval progress items are expanded
  const [expandedItems, setExpandedItems] = useState<Set<string>>(new Set());

  // State for confirmation modals
  const [isApproveModalOpen, setIsApproveModalOpen] = useState(false);
  const [isRejectModalOpen, setIsRejectModalOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [inProgressApprovalId, setInProgressApprovalId] = useState<
    string | null
  >(null);
  const [rejectError, setRejectError] = useState("");
  const [isFullyRejected, setIsFullyRejected] = useState(false);
  const [isFullyCompleted, setIsFullyCompleted] = useState(false);

  useEffect(() => {
    if (isOpen && campaignId) {
      getApprovalDetails({ campaignId, activeCompanyId });
    } else if (!isOpen) {
      // Reset expanded items when drawer closes
      setExpandedItems(new Set());
    }
  }, [isOpen, campaignId, activeCompanyId, getApprovalDetails]);

  const approvalDetails = approvalData?.data;

  // Initialize expanded state for completed/rejected items when data loads
  useEffect(() => {
    if (approvalDetails?.approvalProgress) {
      const initiallyExpanded = new Set<string>();

      // Super admin can see all approval items, regular users see only their authority
      const findAuthorityData = isSuperAdmin
        ? approvalDetails.approvalProgress
        : approvalDetails.approvalProgress.filter((item) =>
            approvalDetails?.approvalPermissions.includes(
              item.approvalAuthority,
            ),
          );

      const findRejected = findAuthorityData.find(
        (item) => item.status === "REJECTED",
      );
      if (findRejected) {
        setIsFullyRejected(true);
      }
      const findCompleted = findAuthorityData.find(
        (item) => item.status === "IN_PROGRESS" || item.status === "PENDING",
      );
      if (!findCompleted) {
        setIsFullyCompleted(true);
      }
      approvalDetails.approvalProgress.forEach((progress) => {
        if (progress.status === "COMPLETED" || progress.status === "REJECTED") {
          initiallyExpanded.add(progress.id);
        }
      });

      setExpandedItems(initiallyExpanded);
    }
  }, [approvalDetails, isSuperAdmin]);

  const toggleExpanded = (progressId: string) => {
    setExpandedItems((prev) => {
      const newSet = new Set(prev);
      if (newSet.has(progressId)) {
        newSet.delete(progressId);
      } else {
        newSet.add(progressId);
      }
      return newSet;
    });
  };

  // Find the approval progress item with IN_PROGRESS status
  const getInProgressApprovalId = (): string | null => {
    if (!approvalDetails?.approvalProgress) return null;
    const inProgressItem = approvalDetails.approvalProgress.find(
      (progress) => progress.status === "IN_PROGRESS",
    );
    return inProgressItem?.id || null;
  };

  // Check if there are any items with IN_PROGRESS status
  // Super admin can approve at any level
  const hasInProgressStatus = (): boolean => {
    if (!approvalDetails?.approvalProgress) return false;

    // Super admin can approve/reject any IN_PROGRESS item
    if (isSuperAdmin) {
      return approvalDetails.approvalProgress.some(
        (progress) => progress.status === "IN_PROGRESS",
      );
    }

    // Regular users can only approve if their authority matches
    return approvalDetails.approvalProgress.some((progress) => {
      return (
        progress.status === "IN_PROGRESS" &&
        approvalDetails.approvalPermissions.includes(progress.approvalAuthority)
      );
    });
  };

  const handleApproveClick = () => {
    const inProgressId = getInProgressApprovalId();
    setInProgressApprovalId(inProgressId);
    setIsApproveModalOpen(true);
  };

  const handleRejectClick = () => {
    const inProgressId = getInProgressApprovalId();
    setInProgressApprovalId(inProgressId);
    setIsRejectModalOpen(true);
  };

  const handleConfirmApprove = async () => {
    if (!inProgressApprovalId) return;

    try {
      await updateApprovalStatus({
        inProgressId: inProgressApprovalId,
        status: "APPROVED",
        comment: "",
        activeCompanyId,
      }).unwrap();

      // Call optional callback if provided
      if (onApprove) {
        onApprove(campaignId, inProgressApprovalId);
      }

      setIsApproveModalOpen(false);
      setInProgressApprovalId(null);
      onClose();
    } catch (error) {
      // Error is handled by axiosBaseQuery and shown via toast
      console.error("Failed to approve campaign:", error);
    }
  };

  const handleConfirmReject = async () => {
    if (!inProgressApprovalId) return;
    // Validate that comment is required for reject
    if (!rejectReason.trim()) {
      setRejectError(t("approval.rejection_reason_required"));
      return;
    }

    setRejectError("");

    try {
      await updateApprovalStatus({
        inProgressId: inProgressApprovalId,
        status: "REJECTED",
        comment: rejectReason.trim(),
        activeCompanyId,
      }).unwrap();

      // Call optional callback if provided
      if (onReject) {
        onReject(campaignId, rejectReason, inProgressApprovalId);
      }

      setIsRejectModalOpen(false);
      setRejectReason("");
      setInProgressApprovalId(null);
      onClose();
    } catch (error) {
      // Error is handled by axiosBaseQuery and shown via toast
      console.error("Failed to reject campaign:", error);
    }
  };

  const handleCancelApprove = () => {
    setIsApproveModalOpen(false);
    setInProgressApprovalId(null);
  };

  const handleCancelReject = () => {
    setIsRejectModalOpen(false);
    setRejectReason("");
    setInProgressApprovalId(null);
    setRejectError("");
  };

  const getApprovalAuthorityLabel = (authority: string) => {
    switch (authority) {
      case "AGENCY":
        return t("approval.authority.agency") || "Agency";
      case "INTERNAL":
        return t("approval.authority.internal") || "Internal";
      case "MEDIA_OWNER":
        return t("approval.authority.media_owner") || "Media Owner";
      default:
        return authority;
    }
  };

  const getProgressStatusLabel = (status: string) => {
    switch (status) {
      case "COMPLETED":
        return t("approval.status.completed") || "Completed";
      case "IN_PROGRESS":
        return t("approval.status.pending") || "Pending";
      case "PENDING":
        return t("approval.status.pending") || "Pending";
      case "REJECTED":
        return t("approval.status.rejected") || "Rejected";
      default:
        return status;
    }
  };

  const formatApprovalDate = (dateString: string): string => {
    try {
      // Handle format like "2025-12-11 10:04:36"
      const date = new Date(dateString.replace(" ", "T"));
      return date.toLocaleDateString("en-GB", {
        day: "2-digit",
        month: "short",
        year: "numeric",
      });
    } catch {
      return dateString;
    }
  };

  const getAuthorityDisplayName = (authority: string) => {
    switch (authority) {
      case "AGENCY":
        return t("approval.authority.agency_acceptance") || "Agency Acceptance";
      case "INTERNAL":
        return t("approval.authority.internal_review") || "Internal Review";
      case "MEDIA_OWNER":
        return (
          t("approval.authority.media_owner_approval") || "Media Owner Approval"
        );
      default:
        return getApprovalAuthorityLabel(authority);
    }
  };

  const isApproveRejectDisabled =
    !approvalDetails ||
    !approvalDetails.approvalProgress ||
    approvalDetails.approvalProgress.length === 0 ||
    !hasInProgressStatus();

  const getPendingMessage = (authority: string) => {
    switch (authority) {
      case "AGENCY":
        return t("approval.pending.agency") || "Awaiting Agency Response";
      case "INTERNAL":
        return (
          t("approval.pending.internal") || "Awaiting Internal team Response"
        );
      case "MEDIA_OWNER":
        return (
          t("approval.pending.media_owner") || "Pending Media Owner Approval"
        );
      default:
        return t("approval.pending.default") || "Awaiting approval";
    }
  };

  return (
    <>
      <ModalDrawer
        isOpen={isOpen}
        onClose={onClose}
        title={t("approval.title") || "Campaign Approval"}
        size="xl"
        footer={
          <div className="flex items-center justify-end gap-3">
            <Button
              variant="outline"
              disabled={isApproveRejectDisabled}
              onClick={handleRejectClick}
            >
              {t("approval.reject")}
            </Button>
            <Button
              variant="primary"
              disabled={isApproveRejectDisabled}
              onClick={handleApproveClick}
            >
              {t("approval.approve")}
            </Button>
          </div>
        }
      >
        {isLoading && (
          <div className="flex items-center justify-center py-8">
            <p className="text-mw-neutral-500">
              {t("approval.loading") || "Loading..."}
            </p>
          </div>
        )}

        {isError && (
          <div className="flex items-center justify-center py-8">
            <p className="text-mw-error-500">
              {t("approval.error") || "Error loading approval details"}
            </p>
          </div>
        )}

        {approvalDetails && !isLoading && !isError && (
          <div className="space-y-6">
            {/* Section 1: Campaign Details */}
            <div className="space-y-1">
              <h3 className="text-base font-medium text-black">
                {t("approval.sections.campaign_details") || "Campaign Details"}
              </h3>
              <div className="border border-container-border mb-4"></div>
              <div className="space-y-4">
                {/* Campaign Name */}
                <div className="space-y-2">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-normal text-mw-neutral-500">
                      {t("approval.campaign_name") || "Campaign Name"}
                    </span>
                    <Tooltip
                      content={t("approval.campaign_name") || "Campaign name"}
                    >
                      <Info className="w-4 h-4 text-mw-neutral-400" />
                    </Tooltip>
                  </div>
                  <p className="text-sm font-semibold text-black">
                    {approvalDetails.campaignName}
                  </p>
                </div>

                {/* Campaign ID */}
                {approvalDetails.planNumber && (
                  <div className="space-y-2">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-normal text-mw-neutral-500">
                        {t("approval.campaign_id") || "Campaign ID"}
                      </span>
                      <Tooltip
                        content={t("approval.campaign_id") || "Campaign ID"}
                      >
                        <Info className="w-4 h-4 text-mw-neutral-400" />
                      </Tooltip>
                    </div>
                    <p className="text-sm font-semibold text-black">
                      {approvalDetails.planNumber}
                    </p>
                  </div>
                )}

                {/* Status */}
                <div className="space-y-2">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-normal text-mw-neutral-500">
                      {t("approval.status_label") || "Status"}
                    </span>
                    <Tooltip
                      content={
                        t("approval.status_tooltip") || "Campaign status"
                      }
                    >
                      <Info className="w-4 h-4 text-mw-neutral-400" />
                    </Tooltip>
                  </div>
                  <div>
                    <StatusBadge status={approvalDetails.status.toLowerCase()}>
                      {t(`campaignsList.status.${approvalDetails.status}`) ||
                        approvalDetails.status}
                    </StatusBadge>
                  </div>
                </div>

                {/* Budget */}
                <div className="space-y-2">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-normal text-mw-neutral-500">
                      {t("approval.budget") || "Budget"}
                    </span>
                    <Tooltip
                      content={t("approval.budget") || "Campaign budget"}
                    >
                      <Info className="w-4 h-4 text-mw-neutral-400" />
                    </Tooltip>
                  </div>
                  <p className="text-sm font-semibold text-black">
                    {formatCurrency(
                      approvalDetails.budget,
                      approvalDetails.currency,
                    )}
                  </p>
                </div>

                {/* Campaign Duration */}
                <div className="space-y-2">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-normal text-mw-neutral-500">
                      {t("approval.campaign_duration") || "Campaign Duration"}
                    </span>
                    <Tooltip
                      content={
                        t("approval.campaign_duration") ||
                        "Campaign start and end dates"
                      }
                    >
                      <Info className="w-4 h-4 text-mw-neutral-400" />
                    </Tooltip>
                  </div>
                  <p className="text-sm font-semibold text-black">
                    {approvalDetails.startDate && approvalDetails.endDate
                      ? `${formatDisplayDate(approvalDetails.startDate, tCommon)} - ${formatDisplayDate(approvalDetails.endDate, tCommon)}`
                      : "NA"}
                  </p>
                </div>
              </div>
            </div>

            {/* Section 2: Approval Progress */}
            <div className="space-y-1">
              <h3 className="text-base font-medium text-black">
                {t("approval.sections.approval_progress") ||
                  "Approval Progress"}
              </h3>
              <div className="border border-container-border mb-4"></div>
              {approvalDetails.approvalProgress &&
              approvalDetails.approvalProgress.length > 0 ? (
                <div className="flex flex-col items-start gap-0 space-y-5">
                  {approvalDetails.approvalProgress.map((progress, index) => {
                    const isCompleted = progress.status === "COMPLETED";
                    const isRejected = progress.status === "REJECTED";
                    const isInProcess = progress.status === "IN_PROGRESS";
                    const isPending = progress.status === "PENDING";
                    const showDetails = isCompleted || isRejected;
                    const isLast =
                      index === approvalDetails.approvalProgress.length - 1;
                    const disabledSection =
                      approvalDetails.status === "REJECTED" &&
                      (isPending || isInProcess);
                    return (
                      <React.Fragment key={progress.id}>
                        <Card
                          className={`w-full border border-container-border rounded-lg p-4 ${disabledSection ? "bg-mw-neutral-50! opacity-50 pointer-events-none" : ""}`}
                        >
                          <CardHeader
                            className={
                              expandedItems.has(progress.id)
                                ? "border-b border-container-border pb-4"
                                : ""
                            }
                          >
                            <div className="relative flex items-center gap-2">
                              {/* Checkmark icon for completed */}

                              {isInProcess && (
                                <div className="w-4 h-4 rounded-full border-2 flex items-center justify-center shrink-0  bg-mw-primary-500 border-mw-primary-500 shadow-sm">
                                  <div className="w-1.5 h-1.5 rounded-full bg-white" />
                                </div>
                              )}

                              {isPending && (
                                <div className="w-4 h-4 relative shrink-0 ">
                                  <Circle className="w-4 h-4 text-mw-neutral-500" />
                                </div>
                              )}
                              {isCompleted && (
                                <div className="w-4 h-4 relative shrink-0 ">
                                  <CheckCircle2 className="w-4 h-4 text-mw-success-500" />
                                </div>
                              )}
                              {/* X or error icon for rejected */}
                              {isRejected && (
                                <div className="w-4 h-4 relative shrink-0 ">
                                  <div className="w-4 h-4 rounded-full border-2 border-mw-error-500 flex items-center justify-center">
                                    <CircleX className="w-4 h-4 text-mw-error-500 absolute" />
                                  </div>
                                </div>
                              )}

                              <div className="flex-1 flex flex-col gap-1">
                                <div className="flex items-center gap-1">
                                  <span
                                    className={`text-sm font-semibold ${
                                      isCompleted
                                        ? "text-mw-success-500"
                                        : isRejected
                                          ? "text-mw-error-500"
                                          : isInProcess
                                            ? "text-mw-primary-500"
                                            : "text-mw-neutral-500"
                                    }`}
                                  >
                                    {getAuthorityDisplayName(
                                      progress.approvalAuthority,
                                    )}
                                  </span>
                                  <Badge
                                    className={`ml-2 ${isCompleted ? "outline-mw-success-500! text-mw-success-500!" : isRejected ? "outline-mw-error-500! text-mw-error-500!" : ""}`}
                                    variant="outline"
                                    size="sm"
                                  >
                                    {getProgressStatusLabel(progress.status)}
                                  </Badge>
                                  {showDetails && (
                                    <Button
                                      variant="outline"
                                      size="iconMd"
                                      className="p-0 ml-auto size-7!"
                                      onClick={() =>
                                        toggleExpanded(progress.id)
                                      }
                                    >
                                      {expandedItems.has(progress.id) ? (
                                        <ChevronUp className="size-4" />
                                      ) : (
                                        <ChevronDown className="size-4" />
                                      )}
                                    </Button>
                                  )}
                                </div>
                              </div>
                            </div>
                          </CardHeader>
                          <CardContent className="p-0!">
                            {expandedItems.has(progress.id) ? (
                              // Completed/Rejected - Show full details
                              <div className="flex flex-col gap-2">
                                <div className="relative flex items-start gap-2">
                                  <div className="flex-1 flex flex-col gap-1">
                                    {/* Details section */}
                                    <div className="flex flex-col gap-2 mt-2">
                                      <div className="flex items-start justify-between gap-2">
                                        <div className="flex items-center gap-0.5">
                                          <span className="text-sm font-normal text-mw-neutral-500">
                                            {isRejected
                                              ? t("approval.rejected_by")
                                              : t("approval.approved_by")}
                                          </span>
                                          <Info className="w-3.5 h-3.5 text-mw-neutral-400" />
                                        </div>
                                        <span className="text-sm font-semibold text-black">
                                          {progress.updatedBy}
                                        </span>
                                      </div>
                                      <div className="flex items-start justify-between gap-2">
                                        <div className="flex items-center gap-0.5">
                                          <span className="text-sm font-normal text-mw-neutral-500">
                                            {isRejected
                                              ? t("approval.rejected_on")
                                              : t("approval.approved_on")}
                                          </span>
                                          <Info className="w-3.5 h-3.5 text-mw-neutral-400" />
                                        </div>
                                        <span className="text-sm font-semibold text-black">
                                          {formatApprovalDate(
                                            progress.updatedAt,
                                          )}
                                        </span>
                                      </div>
                                      {progress.comment && (
                                        <div className="mt-1">
                                          <p className="text-sm text-mw-neutral-700">
                                            {progress.comment}
                                          </p>
                                        </div>
                                      )}
                                    </div>
                                  </div>
                                </div>
                              </div>
                            ) : isPending || isInProcess ? (
                              <div className="flex items-start gap-2 mt-2 ml-6">
                                <div className="flex-1 flex flex-col gap-1">
                                  <span
                                    className={`text-xs font-normal ${isCompleted ? "text-mw-success-500" : isRejected ? "text-mw-error-500" : isInProcess ? "text-mw-primary-500" : "text-mw-neutral-500"}`}
                                  >
                                    {getPendingMessage(
                                      progress.approvalAuthority,
                                    )}
                                  </span>
                                </div>
                              </div>
                            ) : (
                              ""
                            )}
                            {/* Per-media-owner progress inside the Media Owner stage */}
                            {progress.approvalAuthority === "MEDIA_OWNER" &&
                              (approvalDetails.mediaOwners?.length ?? 0) >
                                0 && (
                                <div className="mt-3 ml-6 flex flex-col gap-1.5">
                                  <span className="text-xs font-normal text-mw-neutral-500">
                                    {t("approvals.mediaOwnersLabel") ||
                                      "Media owners"}
                                  </span>
                                  <div className="flex flex-wrap items-center gap-1.5">
                                    {(approvalDetails.mediaOwners ?? []).map(
                                      (owner: MediaOwnerProgress) => (
                                        <span
                                          key={owner.mediaOwnerId}
                                          className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium ${
                                            ownerStatusStyle[
                                              owner.status ?? "PENDING"
                                            ] ?? ownerStatusStyle.PENDING
                                          }`}
                                          title={t(
                                            `approvals.ownerStatus.${owner.status ?? "PENDING"}`,
                                          )}
                                        >
                                          <OwnerStatusIcon
                                            status={owner.status}
                                          />
                                          <span className="max-w-40 truncate">
                                            {owner.mediaOwnerName ||
                                              owner.mediaOwnerId}
                                          </span>
                                          <span className="text-[10px] opacity-70">
                                            {t(
                                              `approvals.ownerStatus.${owner.status ?? "PENDING"}`,
                                            )}
                                          </span>
                                          {owner.hasOpenCounterOffer && (
                                            <span className="inline-flex items-center gap-0.5 text-mw-warning-600">
                                              <AlertTriangle className="size-3" />
                                              {t("approvals.counterOffer")}
                                            </span>
                                          )}
                                        </span>
                                      ),
                                    )}
                                  </div>
                                </div>
                              )}
                            {/* Media-owner viewer: their own slice of the plan */}
                            {progress.approvalAuthority === "MEDIA_OWNER" &&
                              approvalDetails.viewerProposal && (
                                <div className="mt-3 ml-6 flex flex-col gap-1 text-sm">
                                  <div className="flex items-center justify-between gap-2">
                                    <span className="text-mw-neutral-500">
                                      {t("approvals.yourMediaCost", {
                                        cost: formatCurrency(
                                          approvalDetails.viewerProposal
                                            .mediaCost ?? 0,
                                          approvalDetails.currency,
                                        ),
                                      })}
                                    </span>
                                  </div>
                                  <div className="flex items-center justify-between gap-2">
                                    <span className="text-mw-neutral-500">
                                      {t("approvals.yourInventories", {
                                        count:
                                          approvalDetails.viewerProposal
                                            .inventoryCount,
                                      })}
                                    </span>
                                  </div>
                                  {approvalDetails.viewerProposal
                                    .hasOpenCounterOffer && (
                                    <span className="inline-flex items-center gap-1 text-xs text-mw-warning-600">
                                      <AlertTriangle className="size-3" />
                                      {t("approvals.counterOfferOpen")}
                                    </span>
                                  )}
                                </div>
                              )}
                          </CardContent>
                        </Card>

                        {/* Dotted line connector (except for last item) */}
                        {!isLast && (
                          <div className="px-4 flex items-center">
                            <div className="w-8 h-0 border-t-2 border-dashed border-mw-neutral-300 rotate-90 origin-center" />
                          </div>
                        )}
                      </React.Fragment>
                    );
                  })}
                </div>
              ) : (
                <p className="text-sm text-mw-neutral-500">
                  {t("approval.no_progress") ||
                    "No approval progress available"}
                </p>
              )}
              {isFullyRejected &&
                approvalDetails.approvalProgress.length > 0 && (
                  <div className="mt-4 px-4 py-2 bg-mw-error-50 rounded ">
                    <div className="flex items-start gap-2">
                      <Info className="w-4 h-4 text-mw-error-500 flex-shrink-0 mt-0.5" />
                      <p className="text-sm font-medium text-mw-error-500">
                        {t("approval.approval_completed")}
                      </p>
                    </div>
                    <p className="ml-6 text-sm font-normal text-mw-error-500 break-words leading-relaxed whitespace-normal">
                      {t("approval.already_rejected")}
                    </p>
                  </div>
                )}

              {isFullyCompleted &&
                approvalDetails.approvalProgress.length > 0 && (
                  <div className="mt-4 px-4 py-2 bg-mw-success-50 rounded ">
                    <div className="flex items-start gap-2">
                      <Info className="w-4 h-4 text-mw-success-500 flex-shrink-0 mt-0.5" />
                      <p className="text-sm font-medium text-mw-success-500">
                        {t("approval.approval_completed")}
                      </p>
                    </div>
                    <p className="ml-6 text-sm font-normal text-mw-success-500 break-words leading-relaxed whitespace-normal">
                      {t("approval.already_approved")}
                    </p>
                  </div>
                )}
            </div>
          </div>
        )}
      </ModalDrawer>

      {/* Approve Confirmation Modal */}
      <Modal
        isOpen={isApproveModalOpen}
        onClose={handleCancelApprove}
        title={t("approval.modal.approve.title") || "Confirm Approval"}
        primaryButtonText={t("approval.modal.approve.yes") || "Yes, Approve"}
        secondaryButtonText={t("approval.modal.approve.no") || "No, Don't"}
        onPrimaryAction={handleConfirmApprove}
        onSecondaryAction={handleCancelApprove}
        size="md"
      >
        <div className="space-y-4">
          <p className="text-sm text-mw-neutral-700">
            {t("approval.modal.approve.message") ||
              "Are you sure you want to approve the campaign?"}
          </p>

          {/* Warning Card */}
          <div className="px-4 py-2 bg-mw-error-50 rounded border border-mw-error-500">
            <div className="flex items-start gap-2">
              <Info className="w-4 h-4 text-mw-error-500 shrink-0 mt-0.5" />
              <div className="flex-1 flex flex-col gap-1 min-w-0">
                <div className="text-sm font-medium text-mw-error-500">
                  {t("approval.modal.note") || "Note :"}
                </div>
                <div className="text-sm font-normal text-mw-error-500 wrap-break-word leading-relaxed whitespace-normal">
                  {t("approval.modal.approve.note") ||
                    "After approval, no further cost changes will be allowed. The campaign will be locked and sent to the media owner for final approval."}
                </div>
              </div>
            </div>
          </div>
        </div>
      </Modal>

      {/* Reject Confirmation Modal */}
      <Modal
        isOpen={isRejectModalOpen}
        onClose={handleCancelReject}
        title={t("approval.modal.reject.title") || "Confirm Rejection"}
        primaryButtonText={t("approval.modal.reject.yes") || "Yes, Reject"}
        secondaryButtonText={t("approval.modal.reject.no") || "No, Don't"}
        onPrimaryAction={handleConfirmReject}
        onSecondaryAction={handleCancelReject}
        primaryButtonVariant="danger"
        primaryButtonDisabled={!rejectReason.trim()}
        size="md"
      >
        <div className="space-y-4">
          <p className="text-sm text-mw-neutral-700">
            {t("approval.modal.reject.message") ||
              "Are you sure you want to reject the campaign?"}
          </p>

          {/* Warning Card */}
          <div className="px-4 py-2 bg-mw-error-50 rounded border border-mw-error-500">
            <div className="flex items-start gap-2">
              <Info className="w-4 h-4 text-mw-error-500 shrink-0 mt-0.5" />
              <div className="flex-1 flex flex-col gap-1 min-w-0">
                <div className="text-sm font-medium text-mw-error-500">
                  {t("approval.modal.note") || "Note :"}
                </div>
                <div className="text-sm font-normal text-mw-error-500 wrap-break-word leading-relaxed whitespace-normal">
                  {t("approval.modal.reject.note") ||
                    "After rejection, the campaign will be returned for revision. Please provide a reason for rejection."}
                </div>
              </div>
            </div>
          </div>

          {/* Reject Reason Textarea */}
          <div className="space-y-2">
            <Label className="inline-flex">
              {t("approval.modal.reject.reason_label") || "Rejection Reason"}
              <Info className="ml-1 w-4 h-4 text-mw-neutral-500" />
            </Label>
            <Textarea
              value={rejectReason}
              onChange={(e) => {
                setRejectReason(e.target.value);
                if (rejectError) setRejectError("");
              }}
              placeholder={
                t("approval.modal.reject.reason_placeholder") ||
                "Please provide a reason for rejection..."
              }
              rows={4}
              className="w-full"
            />
            {rejectError && (
              <p className="text-sm text-mw-error-500">{rejectError}</p>
            )}
            <p className="wrap-break-word leading-relaxed whitespace-normal">
              {t("approval.reason_visibility_note")}
            </p>
          </div>
        </div>
      </Modal>
    </>
  );
};
