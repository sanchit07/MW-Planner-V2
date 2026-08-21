import PageHeader from "@components/PageHeader";
import { Button } from "@components/ui/Button";
import { Loading } from "@components/ui/Spinner";
import { StatusBadge } from "@components/ui/StatusBadge";
import { useActiveCompany } from "@hooks/useActiveCompany";
import { useGetApprovalInboxQuery } from "@services/campaign/campaignSlice";
import { useTranslate } from "@tolgee/react";
import { formatCurrency } from "@utils/campaign.utils";
import { formatDisplayDate } from "@utils/dateUtils";
import {
  AlertTriangle,
  Building2,
  CalendarCheck,
  CheckCircle2,
  Clock,
  Eye,
  FileText,
  Inbox,
  MonitorPlay,
  ReceiptText,
  Search,
  XCircle,
} from "lucide-react";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

import { CampaignApprovalDrawer } from "../campaigns/components/CampaignApprovalDrawer";

import type {
  ApprovalInboxItem,
  MediaOwnerProgress,
} from "../../types/campaign.types";

type TabKey = "all" | "action" | "negotiating";

/** Small colored chip summarizing one media owner's proposal status. */
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

const ApprovalsPage = () => {
  const navigate = useNavigate();
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { companyId: activeCompanyId } = useActiveCompany();

  const {
    data: inboxData,
    isLoading,
    isError,
  } = useGetApprovalInboxQuery(
    { activeCompanyId },
    { refetchOnMountOrArgChange: true },
  );

  const [drawerCampaignId, setDrawerCampaignId] = useState<string | null>(null);
  const [tab, setTab] = useState<TabKey>("all");
  const [search, setSearch] = useState("");

  const items = useMemo(() => inboxData?.data ?? [], [inboxData]);
  const awaitingMe = items.filter((i) => i.canAct).length;
  const negotiatingCount = items.filter(
    (i) =>
      (i.status || "").toLowerCase() === "negotiating" ||
      i.hasUnacceptedPrices ||
      (i.mediaOwners ?? []).some((o) => o.status === "NEGOTIATING"),
  ).length;

  const filtered = useMemo(() => {
    let list = items;
    if (tab === "action") list = list.filter((i) => i.canAct);
    if (tab === "negotiating")
      list = list.filter(
        (i) =>
          (i.status || "").toLowerCase() === "negotiating" ||
          i.hasUnacceptedPrices ||
          (i.mediaOwners ?? []).some((o) => o.status === "NEGOTIATING"),
      );
    const q = search.trim().toLowerCase();
    if (q) {
      list = list.filter(
        (i) =>
          i.campaignName?.toLowerCase().includes(q) ||
          i.planNumber?.toLowerCase().includes(q) ||
          i.createdByCompanyName?.toLowerCase().includes(q) ||
          (i.mediaOwners ?? []).some((o) =>
            o.mediaOwnerName?.toLowerCase().includes(q),
          ),
      );
    }
    return list;
  }, [items, tab, search]);

  const authorityLabel = (item: ApprovalInboxItem) =>
    item.awaitingAuthority
      ? tCampaigns(`approvals.authority.${item.awaitingAuthority}`)
      : null;

  const ownerChip = (item: ApprovalInboxItem, owner: MediaOwnerProgress) => (
    <span
      key={owner.mediaOwnerId}
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium ${
        ownerStatusStyle[owner.status ?? "PENDING"] ?? ownerStatusStyle.PENDING
      }`}
      title={tCampaigns(`approvals.ownerStatus.${owner.status ?? "PENDING"}`)}
    >
      <OwnerStatusIcon status={owner.status} />
      <span className="max-w-40 truncate">
        {owner.mediaOwnerName || owner.mediaOwnerId}
      </span>
      <span className="text-[10px] opacity-70">
        {tCampaigns(`approvals.ownerStatus.${owner.status ?? "PENDING"}`)}
      </span>
      {owner.hasOpenCounterOffer && (
        <button
          type="button"
          className="inline-flex items-center gap-0.5 text-mw-warning-600 underline decoration-dotted"
          onClick={(e) => {
            e.stopPropagation();
            navigate(`/campaigns/price-management/${item.campaignId}`);
          }}
        >
          <AlertTriangle className="size-3" />
          {tCampaigns("approvals.counterOffer")}
        </button>
      )}
    </span>
  );

  if (isLoading) {
    return <Loading overlay={true} text="" variant="primary" />;
  }

  const tabs: { key: TabKey; label: string; count: number }[] = [
    { key: "all", label: tCampaigns("approvals.tabAll"), count: items.length },
    {
      key: "action",
      label: tCampaigns("approvals.tabNeedsAction"),
      count: awaitingMe,
    },
    {
      key: "negotiating",
      label: tCampaigns("approvals.tabNegotiating"),
      count: negotiatingCount,
    },
  ];

  return (
    <div className="h-full flex flex-col">
      <PageHeader
        title={tCampaigns("approvals.title")}
        description={
          items.length > 0
            ? tCampaigns("approvals.subtitle", {
                total: items.length,
                awaiting: awaitingMe,
              })
            : undefined
        }
      />

      <div className="flex-1 overflow-y-auto scrollbar-thin p-4 space-y-3">
        {/* Toolbar: tabs + search */}
        {(items.length > 0 || search) && (
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-1 bg-white border border-mw-neutral-100 rounded-lg p-1">
              {tabs.map((t) => (
                <button
                  key={t.key}
                  type="button"
                  onClick={() => setTab(t.key)}
                  className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
                    tab === t.key
                      ? "bg-mw-primary-500 text-white"
                      : "text-mw-neutral-600 hover:bg-mw-neutral-50"
                  }`}
                >
                  {t.label}
                  <span
                    className={`ml-1.5 ${tab === t.key ? "text-white/80" : "text-mw-neutral-400"}`}
                  >
                    {t.count}
                  </span>
                </button>
              ))}
            </div>
            <div className="relative flex-1 min-w-56 max-w-96">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-mw-neutral-400" />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder={tCampaigns("approvals.searchPlaceholder")}
                className="w-full rounded-lg border border-mw-neutral-100 bg-white pl-9 pr-3 py-2 text-sm outline-none focus:border-mw-primary-300"
              />
            </div>
          </div>
        )}

        {isError && (
          <div className="p-4 text-sm text-mw-error-500 bg-white rounded-lg border border-mw-neutral-100">
            {tCampaigns("approvals.errorLoadingData")}
          </div>
        )}

        {!isError && items.length === 0 && (
          <div className="p-10 text-center bg-white rounded-lg border border-mw-neutral-100 flex flex-col items-center gap-2">
            <Inbox className="size-8 text-mw-neutral-300" />
            <div className="text-sm font-medium text-mw-neutral-700">
              {tCampaigns("approvals.emptyTitle")}
            </div>
            <div className="text-xs text-mw-neutral-400">
              {tCampaigns("approvals.emptyDescription")}
            </div>
          </div>
        )}

        {!isError && items.length > 0 && filtered.length === 0 && (
          <div className="p-8 text-center bg-white rounded-lg border border-mw-neutral-100 text-sm text-mw-neutral-500">
            {tCampaigns("approvals.noMatches")}
          </div>
        )}

        {filtered.map((item) => {
          const status = (item.status || "").toLowerCase();
          const negotiating = status === "negotiating";
          const isOwnerView = item.viewerIsMediaOwner === true;
          return (
            <div
              key={item.campaignId}
              className={`bg-white rounded-lg border p-4 space-y-3 ${
                item.canAct ? "border-mw-primary-200" : "border-mw-neutral-100"
              }`}
            >
              <div className="flex flex-wrap items-center gap-4">
                <div className="flex-1 min-w-52">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-sm font-medium text-mw-neutral-900">
                      {item.campaignName}
                    </span>
                    {item.planNumber && (
                      <span className="text-xs text-mw-neutral-400">
                        {item.planNumber}
                      </span>
                    )}
                    <StatusBadge status={status}>
                      {tCampaigns(`campaignsList.status.${item.status}`) ||
                        item.status}
                    </StatusBadge>
                    {item.canAct && (
                      <StatusBadge status="pending">
                        {tCampaigns("approvals.awaitingYou")}
                      </StatusBadge>
                    )}
                    {!item.canAct && item.awaitingAuthority && !negotiating && (
                      <span className="text-xs text-mw-neutral-400">
                        {tCampaigns("approvals.awaitingStage", {
                          stage: authorityLabel(item) ?? "",
                        })}
                      </span>
                    )}
                  </div>
                  <div className="mt-1 text-xs text-mw-neutral-500 flex items-center gap-3 flex-wrap">
                    {isOwnerView && item.createdByCompanyName && (
                      <span className="inline-flex items-center gap-1">
                        <Building2 className="size-3.5" />
                        {tCampaigns("approvals.fromCompany", {
                          company: item.createdByCompanyName,
                        })}
                      </span>
                    )}
                    {/* Buyer sees full plan budget; media owner sees only their media cost */}
                    {!isOwnerView && item.budget != null && (
                      <span>
                        {formatCurrency(
                          item.budget,
                          item.currency || undefined,
                        )}
                      </span>
                    )}
                    {isOwnerView && item.viewerProposal?.mediaCost != null && (
                      <span className="font-medium text-mw-neutral-700">
                        {tCampaigns("approvals.yourMediaCost", {
                          cost: formatCurrency(
                            item.viewerProposal.mediaCost,
                            item.currency || undefined,
                          ),
                        })}
                      </span>
                    )}
                    {isOwnerView && item.viewerProposal && (
                      <span className="inline-flex items-center gap-1">
                        <MonitorPlay className="size-3.5" />
                        {tCampaigns("approvals.yourInventories", {
                          count: item.viewerProposal.inventoryCount,
                        })}
                      </span>
                    )}
                    {item.startDate && item.endDate && (
                      <span>
                        {formatDisplayDate(item.startDate)} –{" "}
                        {formatDisplayDate(item.endDate)}
                      </span>
                    )}
                    {negotiating && (
                      <span className="text-mw-warning-500">
                        {tCampaigns("approvals.inNegotiation")}
                      </span>
                    )}
                    {item.hasUnacceptedPrices && (
                      <span className="inline-flex items-center gap-1 text-mw-warning-500">
                        <AlertTriangle className="size-3.5" />
                        {tCampaigns("approvals.pricesPending")}
                      </span>
                    )}
                  </div>
                </div>

                <div className="flex items-center gap-2 flex-wrap">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() =>
                      navigate(`/campaigns/view/${item.campaignId}`)
                    }
                  >
                    <Eye className="size-4 mr-1" />
                    {tCampaigns("card.view_details")}
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() =>
                      navigate(`/campaigns/media-plan/${item.campaignId}`)
                    }
                  >
                    <FileText className="size-4 mr-1" />
                    {tCampaigns("card.view_media_plan")}
                  </Button>
                  {(negotiating || item.hasUnacceptedPrices) && (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() =>
                        navigate(
                          `/campaigns/price-management/${item.campaignId}`,
                        )
                      }
                    >
                      <ReceiptText className="size-4 mr-1" />
                      {tCampaigns("card.price_management")}
                    </Button>
                  )}
                  <Button
                    size="sm"
                    onClick={() => setDrawerCampaignId(item.campaignId)}
                  >
                    <CalendarCheck className="size-4 mr-1" />
                    {item.canAct
                      ? tCampaigns("approvals.review")
                      : tCampaigns("approvals.viewProgress")}
                  </Button>
                </div>
              </div>

              {/* Buyer-side: per-media-owner progress chips */}
              {!isOwnerView &&
                (item.mediaOwners ?? []).length > 0 && (
                  <div className="pt-2 border-t border-mw-neutral-50 flex items-center gap-2 flex-wrap">
                    <span className="text-[11px] uppercase tracking-wide text-mw-neutral-400 font-medium">
                      {tCampaigns("approvals.mediaOwnersLabel")}
                    </span>
                    {(item.mediaOwners ?? []).map((o) => ownerChip(item, o))}
                  </div>
                )}

              {/* Media-owner view: own status strip */}
              {isOwnerView && item.viewerProposal && (
                <div className="pt-2 border-t border-mw-neutral-50 flex items-center gap-2 flex-wrap">
                  <span className="text-[11px] uppercase tracking-wide text-mw-neutral-400 font-medium">
                    {tCampaigns("approvals.yourStatusLabel")}
                  </span>
                  <span
                    className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium ${
                      ownerStatusStyle[item.viewerProposal.status ?? "PENDING"] ??
                      ownerStatusStyle.PENDING
                    }`}
                  >
                    <OwnerStatusIcon status={item.viewerProposal.status} />
                    {tCampaigns(
                      `approvals.ownerStatus.${item.viewerProposal.status ?? "PENDING"}`,
                    )}
                  </span>
                  {item.viewerProposal.hasOpenCounterOffer && (
                    <span className="inline-flex items-center gap-1 text-xs text-mw-warning-500">
                      <AlertTriangle className="size-3.5" />
                      {tCampaigns("approvals.counterOfferOpen")}
                    </span>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>

      <CampaignApprovalDrawer
        isOpen={drawerCampaignId !== null}
        onClose={() => setDrawerCampaignId(null)}
        campaignId={drawerCampaignId ?? ""}
      />
    </div>
  );
};

export default ApprovalsPage;
