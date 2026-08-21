import { useNamespace } from "@components/Tolgee/RouteNamespaceManager";
import { Badge } from "@components/ui/Badge";
import { Button } from "@components/ui/Button";
import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import {
  Statement,
  StatementCandidate,
  useCalculateStatementMutation,
  useCreateStatementMutation,
  useListStatementCandidatesQuery,
  useListStatementsQuery,
} from "@services/statement/statementSlice";
import { useTranslate } from "@tolgee/react";
import React, { useState } from "react";

import PageHeader from "../../components/PageHeader";

const inputClass =
  "block w-full h-10 px-3 py-2 border rounded-md text-sm border-mw-neutral-200 dark:border-mw-neutral-600 text-mw-neutral-700 dark:text-mw-neutral-200 focus:outline-none focus:ring-1 focus:ring-mw-primary-500 dark:bg-mw-neutral-800";

const statusVariant: Record<string, "success" | "warning" | "destructive" | "secondary"> = {
  DRAFT: "secondary",
  FINALIZED: "warning",
  SENT: "warning",
  PAID: "success",
  PARTIALLY_PAID: "warning",
  OVERDUE: "destructive",
  CANCELLED: "destructive",
};

function unwrap<T>(response: unknown): T[] {
  return response && typeof response === "object" && "success" in response && (response as { success: boolean }).success
    ? ((response as { data: T[] }).data ?? [])
    : [];
}

// Sums the frozen customFee amounts across all lines — the actual "Custom Fees" figure, as
// distinct from cascade.standardFees (the same value Cost Breakdown's "Platform Fee" card shows).
function totalCustomFees(statement: Statement): number {
  return (statement.lines ?? []).reduce(
    (sum, line) =>
      sum + (line.feeSnapshot ?? []).reduce((s, fee) => s + (fee.calculatedAmount ?? 0), 0),
    0,
  );
}

function unwrapOne<T>(response: unknown): T | undefined {
  return response && typeof response === "object" && "success" in response && (response as { success: boolean }).success
    ? (response as { data: T }).data
    : undefined;
}

const StatementsPage: React.FC = () => {
  const { namespace } = useNamespace();
  const { t } = useTranslate([namespace]);

  const { data: listResponse } = useListStatementsQuery();
  const statements = unwrap<Statement>(listResponse);

  const [campaignIdsInput, setCampaignIdsInput] = useState("");
  const campaignIds = campaignIdsInput
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);

  const { data: candidatesResponse } = useListStatementCandidatesQuery(
    { campaignIds },
    { skip: campaignIds.length === 0 },
  );
  const candidates = unwrap<StatementCandidate>(candidatesResponse);

  const [calculateStatement, { data: previewResponse, isLoading: isCalculating }] =
    useCalculateStatementMutation();
  const preview = unwrapOne<Statement>(previewResponse);

  const [createStatement, { isLoading: isCreating }] = useCreateStatementMutation();

  return (
    <div id="statements-page" className="h-full overflow-y-auto">
      <PageHeader title={t("title")} descriptionKey={t("description")} />

      <div className="max-w-[65rem] mx-auto py-6 px-6 space-y-6">
        <Card>
          <CardHeader className="p-6 pb-4 border-b border-container-border">
            <CardTitle>
              <span className="text-lg font-semibold">{t("builder.title")}</span>
            </CardTitle>
          </CardHeader>
          <CardContent className="p-6 pt-6 space-y-4">
            <div className="space-y-1">
              <label className="text-sm font-medium text-mw-neutral-700 dark:text-mw-neutral-200">
                {t("builder.campaignIds")}
              </label>
              <input
                className={inputClass}
                value={campaignIdsInput}
                onChange={(e) => setCampaignIdsInput(e.target.value)}
                placeholder={t("builder.campaignIdsPlaceholder")}
              />
            </div>

            {candidates.length > 0 && (
              <div className="space-y-2">
                {candidates.map((c) => (
                  <div
                    key={c.campaignId}
                    className="flex items-center justify-between border border-mw-neutral-200 dark:border-mw-neutral-700 rounded-md p-3"
                  >
                    <div>
                      <p className="text-sm font-medium">{c.campaignName ?? c.campaignId}</p>
                      {!c.eligible && (
                        <p className="text-xs text-amber-600">{c.exclusionReason}</p>
                      )}
                    </div>
                    <Badge variant={c.eligible ? "success" : "secondary"} size="sm">
                      {c.eligible ? t("builder.eligible") : t("builder.excluded")}
                    </Badge>
                  </div>
                ))}
              </div>
            )}

            <div className="flex gap-2 pt-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={campaignIds.length === 0 || isCalculating}
                onClick={() => calculateStatement({ campaignIds })}
              >
                {isCalculating ? t("builder.calculating") : t("builder.preview")}
              </Button>
              <Button
                type="button"
                variant="primary"
                size="sm"
                disabled={campaignIds.length === 0 || isCreating}
                onClick={() => createStatement({ campaignIds })}
              >
                {isCreating ? t("builder.creating") : t("builder.create")}
              </Button>
            </div>

            {preview && (
              <div className="rounded-lg border border-mw-neutral-200 dark:border-mw-neutral-700 p-4 space-y-2 mt-2">
                <p className="text-sm font-semibold">{t("builder.previewTitle")}</p>
                <div className="grid grid-cols-2 gap-2 text-sm">
                  <span className="text-mw-neutral-500">{t("cascade.mediaCost")}</span>
                  <span className="text-right">{preview.totalMediaCost?.toFixed(2)}</span>
                  {/* Same figure as Cost Breakdown's "Platform Fee" card (standardFees) — kept
                      under the same label so the two screens agree for the same campaign. */}
                  <span className="text-mw-neutral-500">{t("cascade.standardFees")}</span>
                  <span className="text-right">{preview.totalFees?.toFixed(2)}</span>
                  <span className="text-mw-neutral-500">{t("cascade.customFees")}</span>
                  <span className="text-right">{totalCustomFees(preview).toFixed(2)}</span>
                  <span className="text-mw-neutral-500">{t("cascade.statementFee")}</span>
                  <span className="text-right">{preview.totalPlatformFee?.toFixed(2)}</span>
                  <span className="font-semibold">{t("cascade.total")}</span>
                  <span className="text-right font-semibold">
                    {preview.totalAmount?.toFixed(2)}
                  </span>
                </div>
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="p-6 pb-4 border-b border-container-border">
            <CardTitle>
              <span className="text-lg font-semibold">{t("list.title")}</span>
            </CardTitle>
          </CardHeader>
          <CardContent className="p-6 pt-6">
            {statements.length === 0 ? (
              <p className="text-sm text-mw-neutral-500">{t("list.empty")}</p>
            ) : (
              <div className="space-y-2">
                {statements.map((statement) => (
                  <div
                    key={statement.id}
                    className="flex items-center justify-between border border-mw-neutral-200 dark:border-mw-neutral-700 rounded-md p-3"
                  >
                    <div>
                      <p className="text-sm font-medium">{statement.statementNumber}</p>
                      <p className="text-xs text-mw-neutral-500">
                        {statement.lines.length} {t("list.campaigns")}
                      </p>
                    </div>
                    <div className="flex items-center gap-3">
                      <span className="text-sm font-semibold">
                        {statement.totalAmount?.toFixed(2)}
                      </span>
                      <Badge variant={statusVariant[statement.status] ?? "secondary"} size="sm">
                        {statement.status}
                      </Badge>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default StatementsPage;
