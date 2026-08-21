import PageHeader from "@components/PageHeader";
import { Button } from "@components/ui/Button";
import { Modal } from "@components/ui/Modal";
import { Loading } from "@components/ui/Spinner";
import { StatusBadge } from "@components/ui/StatusBadge";
import { useActiveCompany } from "@hooks/useActiveCompany";
import { useAnnounce } from "@hooks/useAnnounce";
import {
  useCreateExecutionLineMutation,
  useDeleteExecutionLineMutation,
  useGetExecutionWorkspaceQuery,
  useMoveExecutionInventoryMutation,
  usePushExecutionPlanMutation,
  useUpdateExecutionLineMutation,
} from "@services/campaign/campaignSlice";
import { useTranslate } from "@tolgee/react";
import {
  AlertTriangle,
  ArrowLeft,
  CalendarDays,
  CheckCircle2,
  Gauge,
  Layers,
  MonitorPlay,
  Plus,
  Rocket,
  Target,
  Trash2,
  Wallet,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import type {
  ExecutionWorkspaceInventory,
  ExecutionWorkspaceLine,
} from "../../../types/campaign.types";

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

const HANDOFF_BADGE_STATUS: Record<
  ExecutionWorkspaceLine["handoffStatus"],
  string
> = {
  PENDING_HANDOFF: "pending",
  QUEUED: "pending",
  SENT: "reviewing",
  ACKNOWLEDGED: "active",
  FAILED: "rejected",
};

const IN_FLIGHT_POLL_MS = 2500;

/** One day cell of the availability timeline: stacked own/other/free spots. */
const TimelineCell = ({
  day,
  labels,
}: {
  day: ExecutionWorkspaceInventory["timeline"][number];
  labels: { own: string; other: string; free: string };
}) => {
  const cap = Math.max(day.capacity, 1);
  const pct = (n: number) => `${(n / cap) * 100}%`;
  return (
    <div
      className="flex-1 min-w-[6px] h-10 flex flex-col-reverse rounded-[2px] overflow-hidden bg-mw-gray-100"
      title={`${day.date}\n${labels.own}: ${day.bookedOwn}\n${labels.other}: ${day.bookedOther}\n${labels.free}: ${day.free}/${day.capacity}`}
      data-testid={`timeline-cell-${day.date}`}
    >
      <div className="bg-mw-primary-500" style={{ height: pct(day.bookedOwn) }} />
      <div className="bg-mw-gray-400" style={{ height: pct(day.bookedOther) }} />
      <div className="bg-mw-success-300" style={{ height: pct(day.free) }} />
    </div>
  );
};

const ExecutionWorkspacePage = () => {
  const navigate = useNavigate();
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { campaignId: campaignIdFromUrl } = useParams<{ campaignId: string }>();
  const campaignId = campaignIdFromUrl || "";
  const { showSuccess, showError } = useAnnounce();
  const { companyId: activeCompanyId } = useActiveCompany();

  const [hasInFlight, setHasInFlight] = useState(false);
  const { data, isLoading, isError } = useGetExecutionWorkspaceQuery(
    { campaignId, activeCompanyId },
    { skip: !campaignId, pollingInterval: hasInFlight ? IN_FLIGHT_POLL_MS : 0 },
  );
  const ws = data?.data;

  const [updateLine, { isLoading: isUpdating }] =
    useUpdateExecutionLineMutation();
  const [createLine, { isLoading: isCreating }] =
    useCreateExecutionLineMutation();
  const [moveInventory, { isLoading: isMoving }] =
    useMoveExecutionInventoryMutation();
  const [deleteLine] = useDeleteExecutionLineMutation();
  const [pushExecutionPlan, { isLoading: isPushing }] =
    usePushExecutionPlanMutation();

  const [showPushModal, setShowPushModal] = useState(false);
  // Local draft values for numeric inputs (committed on blur).
  const [drafts, setDrafts] = useState<Record<string, string>>({});

  useEffect(() => {
    setHasInFlight(
      !!ws?.lines?.some(
        (l) => l.handoffStatus === "QUEUED" || l.handoffStatus === "SENT",
      ),
    );
  }, [ws]);

  const inventoryById = useMemo(() => {
    const map = new Map<string, ExecutionWorkspaceInventory>();
    ws?.inventories?.forEach((i) => map.set(i.id, i));
    return map;
  }, [ws]);

  const t = (key: string, params?: Record<string, unknown>) =>
    tCampaigns(`executionWorkspace.${key}`, params);

  const mutationError = (e: unknown) => {
    const msg =
      (e as { data?: { error?: { message?: string } } })?.data?.error
        ?.message || t("actionError");
    showError(msg);
  };

  const commitDraft = async (
    line: ExecutionWorkspaceLine,
    field: "floorRate" | "targetImpressions",
  ) => {
    const key = `${line.id}:${field}`;
    const raw = drafts[key];
    if (raw === undefined) return;
    const value = raw === "" ? 0 : Number(raw);
    if (Number.isNaN(value) || value < 0) {
      setDrafts((d) => ({ ...d, [key]: String(line[field] ?? "") }));
      return;
    }
    if (value === (line[field] ?? 0)) return;
    try {
      await updateLine({
        campaignId,
        lineId: line.id,
        activeCompanyId,
        [field]: value,
      }).unwrap();
      showSuccess(t("lineUpdated"));
    } catch (e) {
      mutationError(e);
      setDrafts((d) => ({ ...d, [key]: String(line[field] ?? "") }));
    }
  };

  const handlePush = async () => {
    setShowPushModal(false);
    try {
      await pushExecutionPlan({
        campaignId,
        activeCompanyId,
        lineIds: ws?.lines?.map((l) => l.id) || [],
      }).unwrap();
      showSuccess(t("pushSuccess"));
    } catch (e) {
      mutationError(e);
    }
  };

  if (isLoading) return <Loading overlay={true} text="" variant="primary" />;
  if (isError || !ws)
    return <div className="p-4">{t("errorLoadingData")}</div>;

  const editable = !ws.locked && ws.approvedByViewer && ws.hasInfluenceAccess;
  const summary = ws.summary;
  const committed = summary?.committedImpressions ?? null;
  const planned = summary?.plannedImpressions ?? null;
  const potential = summary?.potentialImpressions ?? null;
  const deliveryGap =
    committed !== null && planned !== null ? committed - planned : null;

  const kpis = [
    {
      icon: <Wallet className="size-5 text-mw-primary-500" />,
      label: t("kpi.approvedCost"),
      value: formatCurrency(summary?.approvedCost, ws.currency),
    },
    {
      icon: <Target className="size-5 text-mw-primary-500" />,
      label: t("kpi.plannedImpressions"),
      value: formatNumber(planned),
      sub:
        summary?.plannedAdPlays != null
          ? t("kpi.adPlays", { count: formatNumber(summary.plannedAdPlays) })
          : undefined,
    },
    {
      icon: <Gauge className="size-5 text-mw-primary-500" />,
      label: t("kpi.potentialImpressions"),
      value: formatNumber(potential),
      sub: t("kpi.potentialHint"),
    },
    {
      icon: <Layers className="size-5 text-mw-primary-500" />,
      label: t("kpi.linesInventories"),
      value: `${summary?.lineCount ?? 0} / ${summary?.inventoryCount ?? 0}`,
      sub: t("kpi.linesInventoriesHint"),
    },
  ];

  return (
    <div className="h-full flex flex-col">
      <PageHeader
        title={t("title")}
        description={
          <div className="inline-flex items-center gap-2 flex-wrap">
            <p>
              {ws.campaignName}
              {ws.planNumber ? ` · ${ws.planNumber}` : ""}
            </p>
            {ws.campaignStatus && (
              <StatusBadge status={ws.campaignStatus.toLowerCase()}>
                {tCampaigns(`campaignsList.status.${ws.campaignStatus}`) ||
                  ws.campaignStatus}
              </StatusBadge>
            )}
            <span className="text-mw-gray-500 text-sm inline-flex items-center gap-1">
              <CalendarDays className="size-4" />
              {ws.startDate} → {ws.endDate}
            </span>
            {ws.agencyName && (
              <span className="text-mw-gray-500 text-sm">
                {t("fromAgency", { agency: ws.agencyName })}
              </span>
            )}
            {ws.goalType && (
              <span className="text-mw-gray-500 text-sm">
                {t("goal", {
                  goal: ws.goalType,
                  target: formatNumber(ws.goalTarget),
                })}
              </span>
            )}
          </div>
        }
        leftAction={
          <div onClick={() => navigate(`/campaigns/view/${campaignId}`)}>
            <ArrowLeft className="w-5 h-5" cursor="pointer" />
          </div>
        }
        actions={
          editable && (
            <Button
              size="md"
              onClick={() => setShowPushModal(true)}
              disabled={isPushing || !ws.canPush}
              data-testid="button-push-influence"
            >
              <Rocket className="size-4 mr-1" />
              {t("pushToInfluence")}
            </Button>
          )
        }
      />

      <div className="flex-1 overflow-y-auto scrollbar-thin p-4 space-y-4">
        {/* --- gates --- */}
        {!ws.approvedByViewer && (
          <div className="px-4 py-3 bg-mw-warning-50 rounded flex items-center gap-2 text-sm text-mw-warning-500">
            <AlertTriangle className="size-4 shrink-0" />
            <span>{t("notApprovedYet")}</span>
          </div>
        )}
        {ws.approvedByViewer && !ws.hasInfluenceAccess && (
          <div
            className="px-4 py-3 bg-mw-warning-50 rounded flex items-center gap-2 text-sm text-mw-warning-500"
            data-testid="banner-offline-execution"
          >
            <AlertTriangle className="size-4 shrink-0" />
            <span>{t("noInfluenceAccess")}</span>
          </div>
        )}
        {ws.locked && (
          <div className="px-4 py-3 bg-mw-success-50 rounded flex items-center gap-2 text-sm text-mw-success-600">
            <CheckCircle2 className="size-4 shrink-0" />
            <span>{t("pushedAt", { date: ws.pushedAt })}</span>
          </div>
        )}

        {ws.approvedByViewer && (
          <>
            {/* --- KPIs --- */}
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
              {kpis.map((c) => (
                <div
                  key={c.label}
                  className="bg-white border border-mw-gray-200 rounded-lg p-4"
                >
                  <div className="flex items-center gap-2 text-sm text-mw-gray-500">
                    {c.icon}
                    {c.label}
                  </div>
                  <div className="mt-1 text-xl font-semibold text-mw-gray-900">
                    {c.value}
                  </div>
                  {c.sub && (
                    <div className="text-xs text-mw-gray-400 mt-0.5">{c.sub}</div>
                  )}
                </div>
              ))}
            </div>

            {/* --- delivery check --- */}
            {committed !== null && (
              <div
                className={`px-4 py-3 rounded flex items-center gap-2 text-sm ${
                  deliveryGap !== null && deliveryGap < 0
                    ? "bg-mw-warning-50 text-mw-warning-500"
                    : "bg-mw-success-50 text-mw-success-600"
                }`}
                data-testid="delivery-check"
              >
                {deliveryGap !== null && deliveryGap < 0 ? (
                  <AlertTriangle className="size-4 shrink-0" />
                ) : (
                  <CheckCircle2 className="size-4 shrink-0" />
                )}
                <span>
                  {deliveryGap !== null && deliveryGap < 0
                    ? t("deliveryShort", {
                        committed: formatNumber(committed),
                        planned: formatNumber(planned),
                        gap: formatNumber(Math.abs(deliveryGap)),
                      })
                    : t("deliveryOk", {
                        committed: formatNumber(committed),
                        planned: formatNumber(planned),
                      })}
                </span>
              </div>
            )}

            {/* --- inventories & availability --- */}
            <div className="space-y-3">
              <h2 className="text-sm font-semibold text-mw-gray-700">
                {t("inventorySection")}
              </h2>
              {ws.inventories?.map((inv) => (
                <div
                  key={inv.id}
                  className="bg-white border border-mw-gray-200 rounded-lg p-4"
                  data-testid={`inventory-card-${inv.id}`}
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <MonitorPlay className="size-4 text-mw-primary-500" />
                      <span className="font-medium text-mw-gray-900">
                        {inv.name || inv.id}
                      </span>
                      <span className="text-xs px-2 py-0.5 rounded-full bg-mw-gray-100 text-mw-gray-600">
                        {inv.classification}
                        {inv.format ? ` · ${inv.format}` : ""}
                      </span>
                      {inv.spotsPerLoop != null && (
                        <span className="text-xs text-mw-gray-500">
                          {t("loopOf", { count: inv.spotsPerLoop })}
                        </span>
                      )}
                    </div>
                    <div className="flex gap-4 text-sm text-mw-gray-600">
                      <span>
                        {t("invCost")}:{" "}
                        <b>{formatCurrency(inv.approvedCost, ws.currency)}</b>
                      </span>
                      <span>
                        {t("invPlanned")}:{" "}
                        <b>{formatNumber(inv.plannedImpressions)}</b>
                      </span>
                      <span>
                        {t("invPotential")}:{" "}
                        <b>{formatNumber(inv.potentialImpressions)}</b>
                      </span>
                      {inv.impressionsPerSpotPerDay != null && (
                        <span>
                          {t("perSpotDay")}:{" "}
                          <b>{formatNumber(inv.impressionsPerSpotPerDay)}</b>
                        </span>
                      )}
                    </div>
                  </div>
                  {inv.timeline?.length > 0 && (
                    <div className="mt-3">
                      <div className="flex gap-[2px]">
                        {inv.timeline.map((d) => (
                          <TimelineCell
                            key={d.date}
                            day={d}
                            labels={{
                              own: t("legendOwn"),
                              other: t("legendOther"),
                              free: t("legendFree"),
                            }}
                          />
                        ))}
                      </div>
                      <div className="flex justify-between mt-1 text-[10px] text-mw-gray-400">
                        <span>{inv.timeline[0].date}</span>
                        <span className="flex gap-3">
                          <span className="inline-flex items-center gap-1">
                            <span className="size-2 rounded-[2px] bg-mw-primary-500 inline-block" />
                            {t("legendOwn")}
                          </span>
                          <span className="inline-flex items-center gap-1">
                            <span className="size-2 rounded-[2px] bg-mw-gray-400 inline-block" />
                            {t("legendOther")}
                          </span>
                          <span className="inline-flex items-center gap-1">
                            <span className="size-2 rounded-[2px] bg-mw-success-300 inline-block" />
                            {t("legendFree")}
                          </span>
                        </span>
                        <span>{inv.timeline[inv.timeline.length - 1].date}</span>
                      </div>
                    </div>
                  )}
                </div>
              ))}
            </div>

            {/* --- line items --- */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-semibold text-mw-gray-700">
                  {t("lineSection")}
                </h2>
                {editable && (
                  <div className="flex gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={isCreating}
                      onClick={() =>
                        createLine({
                          campaignId,
                          activeCompanyId,
                          classification: "DIGITAL",
                        })
                          .unwrap()
                          .catch(mutationError)
                      }
                      data-testid="button-add-digital-line"
                    >
                      <Plus className="size-4 mr-1" />
                      {t("addDigitalLine")}
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={isCreating}
                      onClick={() =>
                        createLine({
                          campaignId,
                          activeCompanyId,
                          classification: "CLASSIC",
                        })
                          .unwrap()
                          .catch(mutationError)
                      }
                      data-testid="button-add-classic-line"
                    >
                      <Plus className="size-4 mr-1" />
                      {t("addClassicLine")}
                    </Button>
                  </div>
                )}
              </div>

              {ws.lines?.map((line) => {
                const capacity = line.capacityImpressions ?? 0;
                const target = line.targetImpressions ?? 0;
                const fillPct =
                  capacity > 0 ? Math.min(100, (target / capacity) * 100) : 0;
                const otherLines =
                  ws.lines?.filter(
                    (l) =>
                      l.id !== line.id &&
                      l.classification === line.classification,
                  ) || [];
                return (
                  <div
                    key={line.id}
                    className="bg-white border border-mw-gray-200 rounded-lg p-4 space-y-3"
                    data-testid={`line-card-${line.id}`}
                  >
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-xs px-2 py-0.5 rounded-full bg-mw-primary-50 text-mw-primary-600 font-medium">
                        {line.destination === "INFLUENCE"
                          ? t("destInfluence")
                          : t("destOms")}
                      </span>
                      <span className="text-xs px-2 py-0.5 rounded-full bg-mw-gray-100 text-mw-gray-600">
                        {line.classification}
                      </span>
                      {editable && line.classification === "DIGITAL" ? (
                        <select
                          className="text-xs border border-mw-gray-200 rounded px-2 py-1"
                          value={line.purchaseType}
                          disabled={isUpdating}
                          onChange={(e) =>
                            updateLine({
                              campaignId,
                              lineId: line.id,
                              activeCompanyId,
                              purchaseType: e.target.value,
                            })
                              .unwrap()
                              .catch(mutationError)
                          }
                          data-testid={`select-purchase-type-${line.id}`}
                        >
                          <option value="GUARANTEED">
                            {t("purchase.GUARANTEED")}
                          </option>
                          <option value="DIRECT">{t("purchase.DIRECT")}</option>
                        </select>
                      ) : (
                        <span className="text-xs px-2 py-0.5 rounded-full bg-mw-gray-100 text-mw-gray-600">
                          {t(`purchase.${line.purchaseType}`)}
                        </span>
                      )}
                      <StatusBadge
                        status={HANDOFF_BADGE_STATUS[line.handoffStatus]}
                      >
                        {tCampaigns(
                          `executionPlan.handoffStatus.${line.handoffStatus}`,
                        ) || line.handoffStatus}
                      </StatusBadge>
                      <span className="ml-auto text-sm text-mw-gray-600">
                        {t("lineCost")}:{" "}
                        <b>{formatCurrency(line.plannedCost, ws.currency)}</b>
                      </span>
                      {editable &&
                        (line.inventoryIds?.length ?? 0) === 0 && (
                          <button
                            className="text-mw-gray-400 hover:text-mw-danger-500"
                            onClick={() =>
                              deleteLine({
                                campaignId,
                                lineId: line.id,
                                activeCompanyId,
                              })
                                .unwrap()
                                .catch(mutationError)
                            }
                            title={t("deleteLine")}
                            data-testid={`button-delete-line-${line.id}`}
                          >
                            <Trash2 className="size-4" />
                          </button>
                        )}
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                      <label className="text-sm text-mw-gray-600">
                        {t("floorRate")}
                        <input
                          type="number"
                          min={0}
                          className="mt-1 w-full border border-mw-gray-200 rounded px-2 py-1.5 text-sm disabled:bg-mw-gray-50"
                          disabled={!editable}
                          value={
                            drafts[`${line.id}:floorRate`] ??
                            String(line.floorRate ?? "")
                          }
                          onChange={(e) =>
                            setDrafts((d) => ({
                              ...d,
                              [`${line.id}:floorRate`]: e.target.value,
                            }))
                          }
                          onBlur={() => commitDraft(line, "floorRate")}
                          data-testid={`input-floor-rate-${line.id}`}
                        />
                      </label>
                      <label className="text-sm text-mw-gray-600 md:col-span-2">
                        {t("targetImpressions")}
                        <input
                          type="number"
                          min={0}
                          className="mt-1 w-full border border-mw-gray-200 rounded px-2 py-1.5 text-sm disabled:bg-mw-gray-50"
                          disabled={!editable}
                          value={
                            drafts[`${line.id}:targetImpressions`] ??
                            String(line.targetImpressions ?? "")
                          }
                          onChange={(e) =>
                            setDrafts((d) => ({
                              ...d,
                              [`${line.id}:targetImpressions`]: e.target.value,
                            }))
                          }
                          onBlur={() => commitDraft(line, "targetImpressions")}
                          data-testid={`input-target-impressions-${line.id}`}
                        />
                        {line.classification === "DIGITAL" && (
                          <div className="mt-1">
                            <div className="h-1.5 bg-mw-gray-100 rounded overflow-hidden">
                              <div
                                className={`h-full ${
                                  target > capacity
                                    ? "bg-mw-danger-500"
                                    : "bg-mw-primary-500"
                                }`}
                                style={{ width: `${fillPct}%` }}
                              />
                            </div>
                            <span className="text-xs text-mw-gray-400">
                              {t("capacityOf", {
                                capacity: formatNumber(capacity),
                              })}
                            </span>
                          </div>
                        )}
                      </label>
                    </div>

                    <div className="flex flex-wrap gap-2">
                      {line.inventoryIds?.map((invId) => {
                        const inv = inventoryById.get(invId);
                        return (
                          <span
                            key={invId}
                            className="inline-flex items-center gap-2 text-xs px-2 py-1 rounded bg-mw-gray-50 border border-mw-gray-200 text-mw-gray-700"
                            data-testid={`chip-inventory-${line.id}-${invId}`}
                          >
                            {inv?.name || invId}
                            {editable && otherLines.length > 0 && (
                              <select
                                className="text-[10px] border border-mw-gray-200 rounded px-1 py-0.5 bg-white"
                                value=""
                                disabled={isMoving}
                                onChange={(e) => {
                                  if (!e.target.value) return;
                                  moveInventory({
                                    campaignId,
                                    toLineId: e.target.value,
                                    fromLineId: line.id,
                                    inventoryId: invId,
                                    activeCompanyId,
                                  })
                                    .unwrap()
                                    .catch(mutationError);
                                }}
                                data-testid={`select-move-${line.id}-${invId}`}
                              >
                                <option value="">{t("moveTo")}</option>
                                {otherLines.map((l, idx) => (
                                  <option key={l.id} value={l.id}>
                                    {t("lineOption", {
                                      type: t(`purchase.${l.purchaseType}`),
                                      index: idx + 1,
                                    })}
                                  </option>
                                ))}
                              </select>
                            )}
                          </span>
                        );
                      })}
                      {(line.inventoryIds?.length ?? 0) === 0 && (
                        <span className="text-xs text-mw-gray-400">
                          {t("emptyLine")}
                        </span>
                      )}
                    </div>

                    {line.handoffError && (
                      <div className="text-xs text-mw-danger-500">
                        {line.handoffError}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </>
        )}
      </div>

      <Modal
        isOpen={showPushModal}
        onClose={() => setShowPushModal(false)}
        title={t("pushModalTitle")}
      >
        <div className="space-y-4">
          <p className="text-sm text-mw-gray-600">
            {t("pushModalBody", {
              lines: ws.lines?.length ?? 0,
              impressions: formatNumber(committed ?? planned),
            })}
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setShowPushModal(false)}>
              {t("cancel")}
            </Button>
            <Button onClick={handlePush} disabled={isPushing}>
              <Rocket className="size-4 mr-1" />
              {t("confirmPush")}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default ExecutionWorkspacePage;
