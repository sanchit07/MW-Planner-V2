import PageHeader from "@components/PageHeader";
import { Button } from "@components/ui/Button";
import { Modal } from "@components/ui/Modal";
import { Loading } from "@components/ui/Spinner";
import { StatusBadge } from "@components/ui/StatusBadge";
import { useActiveCompany } from "@hooks/useActiveCompany";
import { useAnnounce } from "@hooks/useAnnounce";
import {
  useGetExecutionPlanQuery,
  usePushExecutionPlanMutation,
  useResetExecutionPlanMutation,
} from "@services/campaign/campaignSlice";
import { useTranslate } from "@tolgee/react";
import {
  ArrowLeft,
  Layers,
  Wallet,
  Eye,
  Lock,
  RefreshCw,
  Rocket,
  AlertTriangle,
} from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import type { ExecutionPlanLine } from "../../../types/campaign.types";

const formatCurrency = (
  value: number | null | undefined,
  currency?: string | null,
) => {
  if (value === null || value === undefined) return "—";
  try {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: currency || "USD",
      maximumFractionDigits: 0,
    }).format(value);
  } catch {
    return `${currency || ""} ${Math.round(value).toLocaleString()}`;
  }
};

const formatNumber = (value: number | null | undefined) =>
  value === null || value === undefined ? "—" : value.toLocaleString();

const HANDOFF_BADGE_STATUS: Record<ExecutionPlanLine["handoffStatus"], string> =
  {
    PENDING_HANDOFF: "pending",
    QUEUED: "pending",
    SENT: "reviewing",
    ACKNOWLEDGED: "active",
    FAILED: "rejected",
  };

/** Poll while any line is still in flight so statuses progress live. */
const IN_FLIGHT_POLL_MS = 2500;

const ExecutionPlanPage = () => {
  const navigate = useNavigate();
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { campaignId: campaignIdFromUrl } = useParams<{ campaignId: string }>();
  const campaignId = campaignIdFromUrl || "";
  const { showSuccess, showError } = useAnnounce();
  const { isMediaOwner } = useActiveCompany();

  // Media owners use the persona-specific Execution Workspace instead of the
  // buyer-side plan overview (which shows other owners' lines).
  useEffect(() => {
    if (isMediaOwner && campaignId) {
      navigate(`/campaigns/execution-workspace/${campaignId}`, {
        replace: true,
      });
    }
  }, [isMediaOwner, campaignId, navigate]);

  const [showPushModal, setShowPushModal] = useState(false);
  const [retryLineIds, setRetryLineIds] = useState<string[] | undefined>();

  const [hasInFlight, setHasInFlight] = useState(false);
  const {
    data: planData,
    isLoading,
    isError,
  } = useGetExecutionPlanQuery(campaignId, {
    skip: !campaignId,
    pollingInterval: hasInFlight ? IN_FLIGHT_POLL_MS : 0,
  });
  const [pushExecutionPlan, { isLoading: isPushing }] =
    usePushExecutionPlanMutation();
  const [resetExecutionPlan, { isLoading: isResetting }] =
    useResetExecutionPlanMutation();

  const plan = planData?.data;

  // Keep polling on while any line is queued/sent, off once everything settled.
  useEffect(() => {
    const inFlight = !!plan?.lines.some(
      (l) => l.handoffStatus === "QUEUED" || l.handoffStatus === "SENT",
    );
    setHasInFlight(inFlight);
  }, [plan]);

  const handleBack = () => navigate(`/campaigns/view/${campaignId}`);

  const openPushModal = (lineIds?: string[]) => {
    setRetryLineIds(lineIds);
    setShowPushModal(true);
  };

  const handleConfirmPush = async () => {
    setShowPushModal(false);
    try {
      const result = await pushExecutionPlan({
        campaignId,
        retryLineIds,
      }).unwrap();
      if (result.success) {
        showSuccess(tCampaigns("executionPlan.pushSuccess"));
      } else {
        showError(tCampaigns("executionPlan.pushError"));
      }
    } catch {
      showError(tCampaigns("executionPlan.pushError"));
    } finally {
      setRetryLineIds(undefined);
    }
  };

  const handleReset = async () => {
    try {
      await resetExecutionPlan(campaignId).unwrap();
      showSuccess(tCampaigns("executionPlan.resetSuccess"));
    } catch {
      showError(tCampaigns("executionPlan.resetError"));
    }
  };

  if (isLoading) {
    return <Loading overlay={true} text="" variant="primary" />;
  }

  if (isError || !plan) {
    return (
      <div className="p-4">{tCampaigns("executionPlan.errorLoadingData")}</div>
    );
  }

  const summary = plan.summary;
  const overBudget =
    plan.budget != null &&
    summary.totalPlannedCost != null &&
    summary.totalPlannedCost > plan.budget;
  const failedLines = plan.lines.filter((l) => l.handoffStatus === "FAILED");

  const summaryCards = [
    {
      icon: <Layers className="size-5 text-mw-primary-500" />,
      label: tCampaigns("executionPlan.summary.lines"),
      value: `${summary.lineCount}`,
      sub: tCampaigns("executionPlan.summary.inventories", {
        count: summary.inventoryCount,
      }),
    },
    {
      icon: <Wallet className="size-5 text-mw-primary-500" />,
      label: tCampaigns("executionPlan.summary.plannedCost"),
      value: formatCurrency(summary.totalPlannedCost, plan.currency),
      sub:
        plan.budget != null
          ? tCampaigns("executionPlan.summary.ofBudget", {
              budget: formatCurrency(plan.budget, plan.currency),
            })
          : undefined,
      warning: overBudget,
    },
    {
      icon: <Eye className="size-5 text-mw-primary-500" />,
      label: tCampaigns("executionPlan.summary.plannedImpressions"),
      value: formatNumber(summary.totalPlannedImpressions),
    },
    {
      icon: <Rocket className="size-5 text-mw-primary-500" />,
      label: tCampaigns("executionPlan.summary.handoff"),
      value: `${summary.acknowledgedCount}/${summary.lineCount}`,
      sub:
        summary.failedCount > 0
          ? tCampaigns("executionPlan.summary.failed", {
              count: summary.failedCount,
            })
          : summary.queuedCount + summary.sentCount > 0
            ? tCampaigns("executionPlan.summary.inFlight", {
                count: summary.queuedCount + summary.sentCount,
              })
            : undefined,
      warning: summary.failedCount > 0,
    },
  ];

  return (
    <div className="h-full flex flex-col">
      <PageHeader
        title={tCampaigns("executionPlan.title")}
        description={
          <div className="inline-flex items-center gap-2">
            {plan.campaignName && <p>{plan.campaignName}</p>}
            {plan.campaignStatus && (
              <StatusBadge status={plan.campaignStatus.toLowerCase()}>
                {tCampaigns(`campaignsList.status.${plan.campaignStatus}`) ||
                  plan.campaignStatus}
              </StatusBadge>
            )}
          </div>
        }
        leftAction={
          <div onClick={handleBack}>
            <ArrowLeft className="w-5 h-5" cursor="pointer" />
          </div>
        }
        actions={
          <>
            {!plan.locked && (
              <Button
                variant="outline"
                size="md"
                onClick={handleReset}
                disabled={isResetting || isPushing}
              >
                <RefreshCw className="size-4 mr-1" />
                {tCampaigns("executionPlan.reset")}
              </Button>
            )}
            {!plan.locked && plan.lines.length > 0 && (
              <Button
                size="md"
                onClick={() => openPushModal()}
                disabled={isPushing || isResetting || !plan.canPush}
              >
                <Rocket className="size-4 mr-1" />
                {tCampaigns("executionPlan.push")}
              </Button>
            )}
            {plan.locked && failedLines.length > 0 && (
              <Button
                size="md"
                onClick={() => openPushModal(failedLines.map((l) => l.id))}
                disabled={isPushing}
              >
                <RefreshCw className="size-4 mr-1" />
                {tCampaigns("executionPlan.retryFailed")}
              </Button>
            )}
          </>
        }
      />

      <div className="flex-1 overflow-y-auto scrollbar-thin p-4 space-y-4">
        {!plan.locked && !plan.canPush && plan.pushBlockedReason && (
          <div className="px-4 py-3 bg-mw-warning-50 rounded flex items-center gap-2 text-sm text-mw-warning-500">
            <AlertTriangle className="size-4 shrink-0" />
            <span>
              {tCampaigns(
                `executionPlan.blocked.${plan.pushBlockedReason}`,
              )}
            </span>
          </div>
        )}
        {plan.locked && (
          <div className="px-4 py-3 bg-mw-primary-50 rounded flex items-center gap-2 text-sm text-mw-primary-500">
            <Lock className="size-4 shrink-0" />
            <span>
              {tCampaigns("executionPlan.lockedBanner")}
              {plan.pushedAt &&
                ` ${tCampaigns("executionPlan.pushedAt", {
                  date: new Date(plan.pushedAt).toLocaleString(),
                })}`}
            </span>
          </div>
        )}

        {/* Summary cards */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          {summaryCards.map((card) => (
            <div
              key={card.label}
              className="p-4 bg-white rounded-lg border border-mw-neutral-100 flex flex-col gap-1"
            >
              <div className="flex items-center gap-2 text-sm text-mw-neutral-500">
                {card.icon}
                {card.label}
              </div>
              <div
                className={`text-xl font-medium ${card.warning ? "text-mw-error-500" : "text-mw-neutral-900"}`}
              >
                {card.value}
              </div>
              {card.sub && (
                <div
                  className={`text-xs ${card.warning ? "text-mw-error-500" : "text-mw-neutral-400"}`}
                >
                  {card.sub}
                </div>
              )}
            </div>
          ))}
        </div>

        {/* Line cards */}
        {plan.lines.length === 0 && (
          <div className="p-8 text-center text-sm text-mw-neutral-500 bg-white rounded-lg border border-mw-neutral-100">
            {tCampaigns("executionPlan.noLines")}
          </div>
        )}
        {plan.lines.map((line) => (
          <div
            key={line.id}
            className="bg-white rounded-lg border border-mw-neutral-100"
          >
            <div className="p-4 flex flex-wrap items-center gap-3 border-b border-mw-neutral-100">
              <div className="flex-1 min-w-40">
                <div className="text-sm font-medium text-mw-neutral-900">
                  {line.mediaOwnerName ||
                    tCampaigns("executionPlan.unknownMediaOwner")}
                </div>
                <div className="text-xs text-mw-neutral-400">
                  {tCampaigns(
                    `executionPlan.classification.${line.classification}`,
                  )}{" "}
                  ·{" "}
                  {tCampaigns(
                    `executionPlan.purchaseType.${line.purchaseType}`,
                  )}
                </div>
              </div>
              <StatusBadge status="reviewing">
                {tCampaigns(`executionPlan.destination.${line.destination}`)}
              </StatusBadge>
              <StatusBadge status={HANDOFF_BADGE_STATUS[line.handoffStatus]}>
                {tCampaigns(
                  `executionPlan.handoffStatus.${line.handoffStatus}`,
                )}
              </StatusBadge>
              {line.handoffStatus === "FAILED" && (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => openPushModal([line.id])}
                  disabled={isPushing}
                >
                  {tCampaigns("executionPlan.retry")}
                </Button>
              )}
            </div>
            {line.handoffError && (
              <div className="px-4 py-2 text-xs text-mw-error-500 flex items-center gap-1 border-b border-mw-neutral-100">
                <AlertTriangle className="size-3.5 shrink-0" />
                {line.handoffError}
              </div>
            )}
            <div className="p-4 grid grid-cols-2 lg:grid-cols-4 gap-4 text-sm">
              <div>
                <div className="text-xs text-mw-neutral-400">
                  {tCampaigns("executionPlan.line.inventories")}
                </div>
                <div className="text-mw-neutral-900">{line.inventoryCount}</div>
              </div>
              <div>
                <div className="text-xs text-mw-neutral-400">
                  {tCampaigns("executionPlan.line.plannedCost")}
                </div>
                <div className="text-mw-neutral-900">
                  {formatCurrency(line.plannedCost, plan.currency)}
                </div>
              </div>
              <div>
                <div className="text-xs text-mw-neutral-400">
                  {tCampaigns("executionPlan.line.plannedImpressions")}
                </div>
                <div className="text-mw-neutral-900">
                  {formatNumber(line.plannedImpressions)}
                </div>
              </div>
              {line.handedOffAt && (
                <div>
                  <div className="text-xs text-mw-neutral-400">
                    {tCampaigns("executionPlan.line.handedOffAt")}
                  </div>
                  <div className="text-mw-neutral-900">
                    {new Date(line.handedOffAt).toLocaleString()}
                  </div>
                </div>
              )}
            </div>
            {line.inventories.length > 0 && (
              <div className="px-4 pb-4">
                <div className="text-xs text-mw-neutral-400 mb-1">
                  {tCampaigns("executionPlan.line.inventoryList")}
                </div>
                <div className="flex flex-wrap gap-2">
                  {line.inventories.map((inv) => (
                    <span
                      key={inv.id}
                      className="px-2 py-1 bg-mw-neutral-50 rounded text-xs text-mw-neutral-700"
                      title={[inv.type, inv.format]
                        .filter(Boolean)
                        .join(" · ")}
                    >
                      {inv.name || inv.id}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        ))}
      </div>

      {/* Push confirmation modal */}
      <Modal
        isOpen={showPushModal}
        onClose={() => setShowPushModal(false)}
        title={
          retryLineIds?.length
            ? tCampaigns("executionPlan.pushModal.retryTitle")
            : tCampaigns("executionPlan.pushModal.title")
        }
        primaryButtonText={tCampaigns("executionPlan.pushModal.yes")}
        secondaryButtonText={tCampaigns("executionPlan.pushModal.no")}
        onPrimaryAction={handleConfirmPush}
        onSecondaryAction={() => setShowPushModal(false)}
        size="md"
      >
        <div className="space-y-3">
          <p className="text-sm text-mw-neutral-600">
            {retryLineIds?.length
              ? tCampaigns("executionPlan.pushModal.retryMessage")
              : tCampaigns("executionPlan.pushModal.message")}
          </p>
          {!retryLineIds?.length && (
            <div className="rounded bg-mw-neutral-50 p-3 text-sm text-mw-neutral-700 space-y-1">
              <div className="text-xs font-medium text-mw-neutral-500">
                {tCampaigns("executionPlan.pushModal.summaryTitle")}
              </div>
              <div className="flex justify-between">
                <span>{tCampaigns("executionPlan.summary.lines")}</span>
                <span>
                  {summary.lineCount} (
                  {tCampaigns("executionPlan.summary.inventories", {
                    count: summary.inventoryCount,
                  })}
                  )
                </span>
              </div>
              <div className="flex justify-between">
                <span>{tCampaigns("executionPlan.summary.plannedCost")}</span>
                <span className={overBudget ? "text-mw-error-500" : undefined}>
                  {formatCurrency(summary.totalPlannedCost, plan.currency)}
                </span>
              </div>
              <div className="flex justify-between">
                <span>
                  {tCampaigns("executionPlan.summary.plannedImpressions")}
                </span>
                <span>{formatNumber(summary.totalPlannedImpressions)}</span>
              </div>
            </div>
          )}
        </div>
      </Modal>
    </div>
  );
};

export default ExecutionPlanPage;
