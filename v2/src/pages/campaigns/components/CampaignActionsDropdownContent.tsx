import { DropdownContent, DropdownItem } from "@components/ui/Dropdown";
import { Modal } from "@components/ui/Modal";
import { useAnnounce } from "@hooks/useAnnounce";
import { usePermissions } from "@hooks/usePermissions";
import {
  useBulkActionsCampaignMutation,
  useDeleteCampaignMutation,
} from "@services/campaign/campaignSlice";
import { useGeneratePublicTokenMutation } from "@services/public-access/publicAccessSlice";
import { useAppSelector } from "@store";
import { useTranslate } from "@tolgee/react";
import {
  Archive,
  CalendarCheck,
  Copy,
  // Download,
  Edit,
  Eye,
  FileText,
  // Loader2,
  ReceiptText,
  Rocket,
  Share2,
  Trash2,
} from "lucide-react";
import React, { useState } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router-dom";

import { CampaignApprovalDrawer } from "./CampaignApprovalDrawer";
import { CampaignPPTDownloader } from "./CampaignPPTDownloader";
import ShareModalDrawer from "../media-plan/ShareModalDrawer";

/**
 * Optional permission flags to override default behavior
 * If not provided, permissions are calculated automatically based on campaign status
 */
export interface CampaignActionPermissions {
  view?: boolean;
  edit?: boolean;
  viewMediaPlan?: boolean;
  duplicate?: boolean;
  delete?: boolean;
  assignCreative?: boolean;
  reserveInventories?: boolean;
  campaignApproval?: boolean;
  priceManagement?: boolean;
  executionPlan?: boolean;
  archive?: boolean;
  share?: boolean;
  download?: boolean;
}

/**
 * Optional event handlers for campaign actions
 * If not provided, default navigation will be used
 */
export interface CampaignActionHandlers {
  onView?: (campaignId: string) => void;
  onEdit?: (campaignId: string) => void;
  onViewMediaPlan?: (campaignId: string) => void;
  onDuplicate?: (campaignId: string) => void;
  onDelete?: (campaignId: string) => void;
  onAssignCreative?: (campaignId: string) => void;
  onReserveInventories?: (campaignId: string) => void;
  onCampaignApproval?: (campaignId: string) => void;
  onPriceManagement?: (campaignId: string) => void;
  onArchive?: (campaignId: string) => void;
  onShare?: (campaignId: string) => void;
  onDownload?: (campaignId: string) => void;
  onRefresh?: () => void;
}

/**
 * Optional campaign data for conditional rendering
 */
export interface CampaignActionData {
  status?: string;
  createdByUsername?: string;
  [key: string]: unknown;
}

export interface CampaignActionsDropdownContentProps {
  campaignId: string;
  campaignData?: CampaignActionData;
  handlers?: CampaignActionHandlers;
  permissions?: CampaignActionPermissions;
  className?: string;
  align?: "left" | "right" | "center";
  translationNamespace?: string;
  maxHeight?: string;
  hideNavigation?: string[];
}

export const CampaignActionsDropdownContent: React.FC<
  CampaignActionsDropdownContentProps
> = ({
  campaignId,
  campaignData,
  handlers = {},
  permissions,
  className,
  align = "right",
  translationNamespace = "campaigns",
  maxHeight = "400px",
  hideNavigation = [],
}) => {
  const { t } = useTranslate([translationNamespace]);
  const navigate = useNavigate();
  const { showSuccess, showError } = useAnnounce();
  const profile = useAppSelector((s) => s.profile.profile);
  const { canEditPlans, canDeletePlans, canCreatePlans } = usePermissions();
  const [bulkActionsCampaign] = useBulkActionsCampaignMutation();
  const [deleteCampaign] = useDeleteCampaignMutation();
  const [generatePublicToken] = useGeneratePublicTokenMutation();
  const [isApprovalDrawerOpen, setIsApprovalDrawerOpen] = useState(false);
  const [confirmationAction, setConfirmationAction] = useState<
    "DUPLICATE" | "ARCHIVE" | "DELETE" | "EDIT" | null
  >(null);
  const [isShareModalOpen, setIsShareModalOpen] = useState(false);
  const [shareUrl, setShareUrl] = useState("");
  const [isDownloadTriggered, setIsDownloadTriggered] = useState(false);
  const [, setIsDownloading] = useState(false);

  // Helper function to check if an item should be hidden based on keywords
  const shouldHideItem = (keyword: string): boolean => {
    if (!hideNavigation || hideNavigation.length === 0) return false;
    return hideNavigation.some(
      (hideKeyword) => hideKeyword.toLowerCase() === keyword.toLowerCase(),
    );
  };

  // Get campaign status
  const status = campaignData?.status?.toLowerCase() || "";

  // Calculate permissions based on status and user role
  const getDefaultPermissions = () => {
    const isDraft =
      status === "draft" || status === "planned" || status === "archived";
    const isAdmin = profile?.current_company?.role_name === "Administrator";
    const createdByUsername = campaignData?.createdByUsername;
    // When the creator is unknown (e.g. detail pages), default to owner so
    // we don't accidentally block deletion there.
    const isOwner =
      !createdByUsername || profile?.username === createdByUsername;
    return {
      view: status !== "draft",
      viewMediaPlan: status !== "draft",
      edit: isDraft || status === "rejected",
      assignCreative:
        status !== "draft" && status !== "reviewing" && status !== "rejected",
      campaignApproval: status === "planned" || status === "reviewing",
      // PL3-I12: Reserve Inventories is not implemented (no handler/route/API
      // is wired anywhere), so clicking it does nothing. Disabled until the
      // feature is built. Re-enable by restoring the status check.
      reserveInventories: false,
      priceManagement:
        status === "planned" ||
        status === "rejected" ||
        status === "negotiating",
      executionPlan:
        status === "approved" || status === "active" || status === "completed",
      archive: status !== "completed",
      duplicate: true,
      share: status !== "draft",
      download: status !== "draft",
      delete: isDraft && (isAdmin || isOwner),
    };
  };

  const defaultPermissions = getDefaultPermissions();
  // Role-based permissions from the Admin Console (per active company).
  // These are hard caps: status/ownership logic can never re-enable an
  // action the user's role in the acting company doesn't grant.
  const roleCaps = {
    edit: canEditPlans,
    delete: canDeletePlans,
    duplicate: canCreatePlans,
  };
  // Merge with provided permissions (provided permissions override defaults)
  const finalPermissions = {
    view: permissions?.view ?? defaultPermissions.view,
    edit: roleCaps.edit && (permissions?.edit ?? defaultPermissions.edit),
    viewMediaPlan:
      permissions?.viewMediaPlan ?? defaultPermissions.viewMediaPlan,
    duplicate:
      roleCaps.duplicate &&
      (permissions?.duplicate ?? defaultPermissions.duplicate),
    delete: roleCaps.delete && (permissions?.delete ?? defaultPermissions.delete),
    assignCreative:
      permissions?.assignCreative ?? defaultPermissions.assignCreative,
    reserveInventories:
      permissions?.reserveInventories ?? defaultPermissions.reserveInventories,
    campaignApproval:
      permissions?.campaignApproval ?? defaultPermissions.campaignApproval,
    priceManagement:
      permissions?.priceManagement ?? defaultPermissions.priceManagement,
    executionPlan:
      permissions?.executionPlan ?? defaultPermissions.executionPlan,
    archive: permissions?.archive ?? defaultPermissions.archive,
    share: permissions?.share ?? defaultPermissions.share,
    download: permissions?.download ?? defaultPermissions.download,
  };

  // Default navigation handlers
  const handleView = (id: string) => {
    navigate(`/campaigns/view/${id}`);
  };

  const handleEdit = (_id: string) => {
    setConfirmationAction("EDIT");
  };

  const handleViewMediaPlan = (id: string) => {
    navigate(`/campaigns/media-plan/${id}`);
  };

  const handleDuplicate = (_id: string) => {
    setConfirmationAction("DUPLICATE");
  };

  const handleDelete = (_id: string) => {
    setConfirmationAction("DELETE");
  };

  const handleConfirmAction = async () => {
    if (!confirmationAction) return;

    try {
      if (confirmationAction === "EDIT") {
        setConfirmationAction(null);
        navigate(`/campaigns/edit/${campaignId}`);
        return;
      }

      if (confirmationAction === "DELETE") {
        await deleteCampaign(campaignId).unwrap();
        showSuccess(t("campaignActions.deleteSuccess"));
      } else {
        await bulkActionsCampaign({
          campaignIds: [campaignId],
          action: confirmationAction,
        }).unwrap();
        showSuccess(
          confirmationAction === "DUPLICATE"
            ? t("campaignActions.duplicatedSuccess")
            : t("campaignActions.archivedSuccess"),
        );
        if (confirmationAction === "DUPLICATE") {
          navigate("/campaigns");
        }
      }
      setConfirmationAction(null);
      handlers?.onRefresh?.();
    } catch (error) {
      showError(
        confirmationAction === "DELETE"
          ? t("campaignActions.deleteError")
          : confirmationAction === "DUPLICATE"
            ? t("campaignActions.duplicateError")
            : t("campaignActions.archiveError"),
      );
      console.error(`${confirmationAction} campaign error:`, error);
    }
  };

  const handleCancelAction = () => {
    setConfirmationAction(null);
  };

  // Get modal configuration based on action type
  const getModalConfig = () => {
    switch (confirmationAction) {
      case "EDIT":
        return {
          title: t("campaignActions.editModal.title"),
          message: (
            <>
              <p className="mb-3">{t("campaignActions.editModal.message1")}</p>
              <ul className="list-disc list-inside space-y-1 mb-3 text-sm">
                <li>{t("campaignActions.editModal.message2")}</li>
                <li>{t("campaignActions.editModal.message3")}</li>
              </ul>
              <p className="font-medium">
                {t("campaignActions.editModal.message4")}
              </p>
            </>
          ),
          primaryButtonText: t("campaignActions.editModal.primaryButton"),
          primaryButtonVariant: "default" as const,
        };
      case "DUPLICATE":
        return {
          title: t("campaignActions.duplicateModal.title"),
          message: t("campaignActions.duplicateModal.message"),
          primaryButtonText: t("campaignActions.duplicateModal.primaryButton"),
          primaryButtonVariant: "default" as const,
        };
      case "ARCHIVE":
        return {
          title: t("campaignActions.archiveModal.title"),
          message: t("campaignActions.archiveModal.message"),
          primaryButtonText: t("campaignActions.archiveModal.primaryButton"),
          primaryButtonVariant: "default" as const,
        };
      case "DELETE":
        return {
          title: t("campaignActions.deleteModal.title"),
          message: t("campaignActions.deleteModal.message"),
          primaryButtonText: t("campaignActions.deleteModal.primaryButton"),
          primaryButtonVariant: "danger" as const,
        };
      default:
        return null;
    }
  };

  const modalConfig = getModalConfig();

  // Hidden: Assign Creative & Reserve Inventories are unimplemented (no
  // route/API wired). Restore with the menu items + Image/Calendar imports.
  // const handleAssignCreative = (id: string) => {
  //   handlers?.onAssignCreative?.(id);
  // };

  // const handleReserveInventories = (id: string) => {
  //   handlers?.onReserveInventories?.(id);
  // };

  const handleCampaignApproval = (id: string) => {
    // If custom handler is provided, use it; otherwise open the drawer
    if (handlers?.onCampaignApproval) {
      handlers.onCampaignApproval(id);
    } else {
      setIsApprovalDrawerOpen(true);
    }
  };

  const handleCloseApprovalDrawer = () => {
    setIsApprovalDrawerOpen(false);
  };

  const handleApprove = () => {
    setIsApprovalDrawerOpen(false);
    // Refresh the list after successful approval
    handlers?.onRefresh?.();
  };

  const handleReject = () => {
    setIsApprovalDrawerOpen(false);
    // Refresh the list after successful rejection
    handlers?.onRefresh?.();
  };

  const handlePriceManagement = (id: string) => {
    navigate(`/campaigns/price-management/${id}`);
  };

  const handleExecutionPlan = (id: string) => {
    navigate(`/campaigns/execution-plan/${id}`);
  };

  const handleArchive = (_id: string) => {
    setConfirmationAction("ARCHIVE");
  };

  const handleShare = async (id: string) => {
    const fallbackUrl = `${window.location.origin}/campaigns/media-plan/${id}`;
    try {
      const tokenResponse = await generatePublicToken(id).unwrap();
      const publicToken = tokenResponse.data?.publicToken;
      if (!publicToken) {
        throw new Error("No publicToken in generate-token response");
      }
      setShareUrl(
        `${window.location.origin}/public/media-plan/view/${id}?token=${publicToken}`,
      );
    } catch (error) {
      console.error("Error generating public share link:", error);
      showError(t("media_plan.errors.publicTokenFailed"));
      setShareUrl(fallbackUrl);
    }
    setIsShareModalOpen(true);
    handlers?.onShare?.(id);
  };

  // const handleDownload = (id: string) => {
  //   if (isDownloading) return;
  //   setIsDownloadTriggered(true);
  //   setIsDownloading(true);
  //   handlers?.onDownload?.(id);
  // };

  const handleDownloadComplete = () => {
    showSuccess(t("campaignActions.downloadPPTSuccess"));
    setIsDownloadTriggered(false);
    setIsDownloading(false);
  };

  const handleDownloadError = () => {
    showError(t("campaignActions.downloadPPTFailed"));
    setIsDownloadTriggered(false);
    setIsDownloading(false);
  };

  return (
    <>
      <DropdownContent
        className={`overflow-y-auto scrollbar-thin scrollbar-thumb-mw-neutral-300 scrollbar-track-transparent ${className || ""}`}
        align={align}
        maxHeight={maxHeight}
        minWidth="250px"
      >
        {!shouldHideItem("view") && (
          <DropdownItem
            onClick={() => handleView(campaignId)}
            disabled={!finalPermissions.view}
          >
            <span className="flex items-center gap-2">
              <Eye className="size-4" /> {t("card.view_details")}
            </span>
          </DropdownItem>
        )}

        {!shouldHideItem("viewMediaPlan") && (
          <DropdownItem
            onClick={() => handleViewMediaPlan(campaignId)}
            disabled={!finalPermissions.viewMediaPlan}
          >
            <span className="flex items-center gap-2">
              <FileText className="size-4" /> {t("card.view_media_plan")}
            </span>
          </DropdownItem>
        )}

        {!shouldHideItem("edit") && (
          <DropdownItem
            value="edit"
            onClick={() => handleEdit(campaignId)}
            disabled={!finalPermissions.edit}
          >
            <span className="flex items-center gap-2">
              <Edit className="size-4" /> {t("card.edit")}
            </span>
          </DropdownItem>
        )}

        {/* Hidden: Assign Creative — unimplemented (no route/API wired).
            Restore with the handleAssignCreative handler + Image import. */}
        {/* {!shouldHideItem("assignCreative") && (
          <DropdownItem
            onClick={() => handleAssignCreative(campaignId)}
            disabled={!finalPermissions.assignCreative}
          >
            <span className="flex items-center gap-2">
              <Image className="size-4" /> {t("card.assign_creative")}
            </span>
          </DropdownItem>
        )} */}
        {!shouldHideItem("campaignApproval") && (
          <DropdownItem
            onClick={() => handleCampaignApproval(campaignId)}
            disabled={!finalPermissions.campaignApproval}
          >
            <span className="flex items-center gap-2">
              <CalendarCheck className="size-4" /> {t("card.campaign_approval")}
            </span>
          </DropdownItem>
        )}

        {/* Hidden: Reserve Inventories — unimplemented (no route/API wired).
            Restore with the handleReserveInventories handler + Calendar import. */}
        {/* {!shouldHideItem("reserveInventories") && (
          <DropdownItem
            onClick={() => handleReserveInventories(campaignId)}
            disabled={!finalPermissions.reserveInventories}
          >
            <span className="flex items-center gap-2">
              <Calendar className="size-4" /> {t("card.reserve_inventories")}
            </span>
          </DropdownItem>
        )} */}

        {!shouldHideItem("priceManagement") && (
          <DropdownItem
            onClick={() => handlePriceManagement(campaignId)}
            disabled={!finalPermissions.priceManagement}
          >
            <span className="flex items-center gap-2">
              <ReceiptText className="size-4" /> {t("card.price_management")}
            </span>
          </DropdownItem>
        )}

        {!shouldHideItem("executionPlan") && (
          <DropdownItem
            onClick={() => handleExecutionPlan(campaignId)}
            disabled={!finalPermissions.executionPlan}
          >
            <span className="flex items-center gap-2">
              <Rocket className="size-4" /> {t("card.execution_plan")}
            </span>
          </DropdownItem>
        )}

        {!shouldHideItem("archive") && (
          <DropdownItem
            onClick={() => handleArchive(campaignId)}
            disabled={!finalPermissions.archive}
          >
            <span className="flex items-center gap-2">
              <Archive className="size-4" /> {t("card.archive")}
            </span>
          </DropdownItem>
        )}

        {!shouldHideItem("duplicate") && (
          <DropdownItem
            onClick={() => handleDuplicate(campaignId)}
            disabled={!finalPermissions.duplicate}
          >
            <span className="flex items-center gap-2">
              <Copy className="size-4" /> {t("card.duplicate")}
            </span>
          </DropdownItem>
        )}

        {!shouldHideItem("share") && (
          <DropdownItem
            onClick={() => handleShare(campaignId)}
            disabled={!finalPermissions.share}
          >
            <span className="flex items-center gap-2">
              <Share2 className="size-4" /> {t("card.share")}
            </span>
          </DropdownItem>
        )}

        {/* {!shouldHideItem("download") && (
          <DropdownItem
            onClick={() => handleDownload(campaignId)}
            disabled={!finalPermissions.download || isDownloading}
          >
            <span className="flex items-center gap-2">
              {isDownloading ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Download className="size-4" />
              )}
              {isDownloading
                ? t("media_plan.actions.processing")
                : t("card.download")}
            </span>
          </DropdownItem>
        )} */}

        {!shouldHideItem("delete") && (
          <DropdownItem
            onClick={() => handleDelete(campaignId)}
            disabled={!finalPermissions.delete}
          >
            <span className="flex items-center gap-2 text-mw-error-500">
              <Trash2 className="size-4" /> {t("card.delete")}
            </span>
          </DropdownItem>
        )}
      </DropdownContent>

      {/* Campaign Approval Drawer - portaled to body so it opens on screen, not clipped by row */}
      {typeof document !== "undefined" &&
        createPortal(
          <CampaignApprovalDrawer
            isOpen={isApprovalDrawerOpen}
            onClose={handleCloseApprovalDrawer}
            campaignId={campaignId}
            onApprove={handleApprove}
            onReject={handleReject}
          />,
          document.body,
        )}

      {/* Confirmation Modal - portaled to body so it opens on screen, not clipped by row */}
      {modalConfig &&
        typeof document !== "undefined" &&
        createPortal(
          <Modal
            isOpen={!!confirmationAction}
            onClose={handleCancelAction}
            title={modalConfig.title}
            primaryButtonText={modalConfig.primaryButtonText}
            secondaryButtonText={t("campaignActions.cancel")}
            onPrimaryAction={handleConfirmAction}
            onSecondaryAction={handleCancelAction}
            primaryButtonVariant={modalConfig.primaryButtonVariant}
            size="sm"
          >
            <div className="wrap-break-word whitespace-normal">
              {modalConfig.message}
            </div>
          </Modal>,
          document.body,
        )}

      {/* Share Modal - portaled to body so it is not clipped by the dropdown */}
      {typeof document !== "undefined" &&
        createPortal(
          <ShareModalDrawer
            isOpen={isShareModalOpen}
            onClose={() => setIsShareModalOpen(false)}
            shareUrl={shareUrl}
          />,
          document.body,
        )}

      {/* PPT Downloader - headless, only mounted when a download is in progress */}
      {isDownloadTriggered &&
        typeof document !== "undefined" &&
        createPortal(
          <CampaignPPTDownloader
            campaignId={campaignId}
            onComplete={handleDownloadComplete}
            onError={handleDownloadError}
          />,
          document.body,
        )}
    </>
  );
};
