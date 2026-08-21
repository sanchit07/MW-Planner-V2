import { useNamespace } from "@components/Tolgee/RouteNamespaceManager";
import { Badge } from "@components/ui/Badge";
import { Button } from "@components/ui/Button";
import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import {
  Creative,
  CreativeFormat,
  CreativeTier1Status,
  useBindCreativeMutation,
  useDeactivateCreativeMutation,
  useListAssignmentsForCampaignQuery,
  useListCreativesQuery,
  useUpdateCreativeTier1StatusMutation,
  useUploadCreativeMutation,
} from "@services/creative/creativeSlice";
import { useTranslate } from "@tolgee/react";
import { Trash2, Upload } from "lucide-react";
import React, { useState } from "react";

import PageHeader from "../../components/PageHeader";

const inputClass =
  "block w-full h-10 px-3 py-2 border rounded-md text-sm border-mw-neutral-200 dark:border-mw-neutral-600 text-mw-neutral-700 dark:text-mw-neutral-200 focus:outline-none focus:ring-1 focus:ring-mw-primary-500 dark:bg-mw-neutral-800";
const labelClass =
  "text-sm font-medium text-mw-neutral-700 dark:text-mw-neutral-200";

const bindingStatusVariant: Record<
  string,
  "success" | "warning" | "destructive" | "default"
> = {
  BOUND: "success",
  FORCED_MATCH: "warning",
  PENDING_REAPPROVAL: "warning",
  REJECTED: "destructive",
};

// Tier 1 (internal approval) status — every upload starts Processing.
const tier1StatusVariant: Record<
  CreativeTier1Status,
  "success" | "warning" | "destructive" | "secondary"
> = {
  PROCESSING: "warning",
  ACCEPTED: "success",
  INADEQUATE: "destructive",
  ARCHIVE: "secondary",
};

const CreativesPage: React.FC = () => {
  const { namespace } = useNamespace();
  const { t } = useTranslate([namespace]);

  const [tier1Filter, setTier1Filter] = useState<CreativeTier1Status | "">("");
  const { data: listResponse, isLoading } = useListCreativesQuery(
    tier1Filter ? { tier1Status: tier1Filter } : undefined,
  );
  const creatives =
    listResponse && "success" in listResponse && listResponse.success
      ? (listResponse as { data: Creative[] }).data
      : [];

  const [uploadCreative, { isLoading: isUploading }] = useUploadCreativeMutation();
  const [deactivateCreative] = useDeactivateCreativeMutation();
  const [bindCreative, { isLoading: isBinding }] = useBindCreativeMutation();
  const [updateTier1Status] = useUpdateCreativeTier1StatusMutation();
  const [inadequateDraft, setInadequateDraft] = useState<Record<string, string>>({});

  const [form, setForm] = useState<{
    file: File | null;
    name: string;
    format: CreativeFormat;
    durationSeconds: string;
  }>({ file: null, name: "", format: "STATIC", durationSeconds: "" });

  const [campaignId, setCampaignId] = useState("");
  const { data: assignmentsResponse, refetch: refetchAssignments } =
    useListAssignmentsForCampaignQuery({ campaignId }, { skip: !campaignId });
  const assignments =
    assignmentsResponse && "success" in assignmentsResponse && assignmentsResponse.success
      ? (assignmentsResponse as { data: { lineItemId: string; bindingStatus: string; creativeId: string }[] })
          .data
      : [];

  const [draggedCreativeId, setDraggedCreativeId] = useState<string | null>(null);
  const [bindError, setBindError] = useState<string | null>(null);

  const handleUpload = async () => {
    if (!form.file || !form.name) return;
    await uploadCreative({
      file: form.file,
      name: form.name,
      format: form.format,
      durationSeconds: form.durationSeconds ? Number(form.durationSeconds) : undefined,
    });
    setForm({ file: null, name: "", format: "STATIC", durationSeconds: "" });
  };

  const handleDrop = async (lineItemId: string, forceMatch = false) => {
    if (!draggedCreativeId) return;
    setBindError(null);
    try {
      await bindCreative({ creativeId: draggedCreativeId, lineItemId, forceMatch }).unwrap();
      refetchAssignments();
    } catch (err) {
      const message =
        (err as { data?: { error?: { message?: string } } })?.data?.error?.message ??
        t("assignment.bindFailed");
      setBindError(message);
    }
    setDraggedCreativeId(null);
  };

  return (
    <div id="creatives-page" className="h-full overflow-y-auto">
      <PageHeader title={t("title")} descriptionKey={t("description")} />

      <div className="max-w-[75rem] mx-auto py-6 px-6 space-y-6">
        <Card>
          <CardHeader className="p-6 pb-4 border-b border-container-border">
            <CardTitle>
              <span className="text-lg font-semibold">{t("upload.title")}</span>
            </CardTitle>
          </CardHeader>
          <CardContent className="p-6 pt-6">
            <div className="grid grid-cols-4 gap-4 items-end">
              <div className="space-y-1">
                <label className={labelClass}>{t("upload.name")}</label>
                <input
                  className={inputClass}
                  value={form.name}
                  onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                />
              </div>
              <div className="space-y-1">
                <label className={labelClass}>{t("upload.format")}</label>
                <select
                  className={inputClass}
                  value={form.format}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, format: e.target.value as CreativeFormat }))
                  }
                >
                  <option value="STATIC">{t("upload.formatStatic")}</option>
                  <option value="VIDEO">{t("upload.formatVideo")}</option>
                  <option value="AUDIO">{t("upload.formatAudio")}</option>
                  <option value="HTML5">HTML5</option>
                </select>
              </div>
              <div className="space-y-1">
                <label className={labelClass}>{t("upload.duration")}</label>
                <input
                  type="number"
                  className={inputClass}
                  value={form.durationSeconds}
                  onChange={(e) => setForm((f) => ({ ...f, durationSeconds: e.target.value }))}
                  disabled={form.format === "STATIC"}
                />
              </div>
              <div className="space-y-1">
                <label className={labelClass}>{t("upload.file")}</label>
                <input
                  type="file"
                  className={inputClass}
                  onChange={(e) => setForm((f) => ({ ...f, file: e.target.files?.[0] ?? null }))}
                />
              </div>
            </div>
            <div className="pt-4">
              <Button
                type="button"
                variant="primary"
                size="sm"
                disabled={isUploading || !form.file || !form.name}
                onClick={handleUpload}
              >
                <Upload className="w-4 h-4 mr-1.5" />
                {isUploading ? t("upload.uploading") : t("upload.submit")}
              </Button>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="p-6 pb-4 border-b border-container-border flex items-center justify-between">
            <CardTitle>
              <span className="text-lg font-semibold">{t("library.title")}</span>
            </CardTitle>
            <select
              className={`${inputClass} max-w-[12rem]`}
              value={tier1Filter}
              onChange={(e) => setTier1Filter(e.target.value as CreativeTier1Status | "")}
            >
              <option value="">{t("library.filterAll")}</option>
              <option value="PROCESSING">{t("library.tier1Status.PROCESSING")}</option>
              <option value="ACCEPTED">{t("library.tier1Status.ACCEPTED")}</option>
              <option value="INADEQUATE">{t("library.tier1Status.INADEQUATE")}</option>
            </select>
          </CardHeader>
          <CardContent className="p-6 pt-6">
            {isLoading ? (
              <p className="text-sm text-mw-neutral-500">{t("library.loading")}</p>
            ) : creatives.length === 0 ? (
              <p className="text-sm text-mw-neutral-500">{t("library.empty")}</p>
            ) : (
              <div className="grid grid-cols-4 gap-4">
                {creatives.map((creative) => {
                  const canAssign = creative.tier1Status === "ACCEPTED";
                  return (
                    <div
                      key={creative.id}
                      draggable={canAssign}
                      onDragStart={() => canAssign && setDraggedCreativeId(creative.id)}
                      title={canAssign ? undefined : t("library.notAssignableHint")}
                      className={`border border-mw-neutral-200 dark:border-mw-neutral-700 rounded-lg p-3 space-y-2 ${
                        canAssign ? "cursor-grab active:cursor-grabbing" : "opacity-70"
                      }`}
                    >
                      <div className="aspect-video bg-mw-neutral-100 dark:bg-mw-neutral-800 rounded flex items-center justify-center overflow-hidden relative">
                        {creative.thumbnailUrl ? (
                          <img
                            src={creative.thumbnailUrl}
                            alt={creative.name}
                            className="w-full h-full object-cover"
                          />
                        ) : (
                          <span className="text-xs text-mw-neutral-400">{creative.format}</span>
                        )}
                        <span className="absolute top-1.5 right-1.5">
                          <Badge variant={tier1StatusVariant[creative.tier1Status]} size="sm">
                            {t(`library.tier1Status.${creative.tier1Status}`)}
                          </Badge>
                        </span>
                      </div>
                      <p className="text-sm font-medium truncate">{creative.name}</p>
                      <p className="text-xs text-mw-neutral-500">
                        {creative.aspectRatio ?? "—"}
                        {creative.durationSeconds ? ` · ${creative.durationSeconds}s` : ""}
                      </p>
                      {creative.tier1Status === "INADEQUATE" && creative.tier1RejectionReason && (
                        <p className="text-xs text-red-500">{creative.tier1RejectionReason}</p>
                      )}
                      {creative.tier1Status === "PROCESSING" && (
                        <div className="space-y-1.5 pt-1">
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            className="w-full"
                            onClick={() =>
                              updateTier1Status({ id: creative.id, tier1Status: "ACCEPTED" })
                            }
                          >
                            {t("library.accept")}
                          </Button>
                          <input
                            className={`${inputClass} h-8 text-xs`}
                            placeholder={t("library.inadequateReasonPlaceholder")}
                            value={inadequateDraft[creative.id] ?? ""}
                            onChange={(e) =>
                              setInadequateDraft((d) => ({ ...d, [creative.id]: e.target.value }))
                            }
                          />
                          <Button
                            type="button"
                            variant="destructive"
                            size="sm"
                            className="w-full"
                            disabled={!inadequateDraft[creative.id]}
                            onClick={() =>
                              updateTier1Status({
                                id: creative.id,
                                tier1Status: "INADEQUATE",
                                rejectionReason: inadequateDraft[creative.id],
                              })
                            }
                          >
                            {t("library.markInadequate")}
                          </Button>
                        </div>
                      )}
                      <button
                        type="button"
                        className="text-xs text-red-500 flex items-center gap-1"
                        onClick={() => deactivateCreative({ id: creative.id })}
                      >
                        <Trash2 className="w-3 h-3" />
                        {t("library.remove")}
                      </button>
                    </div>
                  );
                })}
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="p-6 pb-4 border-b border-container-border">
            <CardTitle>
              <span className="text-lg font-semibold">{t("assignment.title")}</span>
            </CardTitle>
            <p className="text-sm text-mw-neutral-500 dark:text-mw-neutral-400 mt-1">
              {t("assignment.description")}
            </p>
          </CardHeader>
          <CardContent className="p-6 pt-6 space-y-4">
            <div className="space-y-1 max-w-sm">
              <label className={labelClass}>{t("assignment.campaignId")}</label>
              <input
                className={inputClass}
                value={campaignId}
                onChange={(e) => setCampaignId(e.target.value)}
                placeholder={t("assignment.campaignIdPlaceholder")}
              />
            </div>

            {bindError && <p className="text-sm text-red-500">{bindError}</p>}

            {campaignId && (
              <div className="space-y-2">
                {assignments.length === 0 ? (
                  <p className="text-sm text-mw-neutral-500">{t("assignment.noneYet")}</p>
                ) : (
                  assignments.map((a) => (
                    <div
                      key={a.lineItemId}
                      onDragOver={(e) => e.preventDefault()}
                      onDrop={() => handleDrop(a.lineItemId)}
                      className="flex items-center justify-between border border-dashed border-mw-neutral-300 dark:border-mw-neutral-600 rounded-md p-3"
                    >
                      <span className="text-sm font-mono">{a.lineItemId}</span>
                      <Badge variant={bindingStatusVariant[a.bindingStatus] ?? "default"} size="sm">
                        {a.bindingStatus}
                      </Badge>
                    </div>
                  ))
                )}
                <div
                  onDragOver={(e) => e.preventDefault()}
                  onDrop={(e) => {
                    e.preventDefault();
                    const lineItemId = window.prompt(t("assignment.dropPromptLineItemId"));
                    if (lineItemId) handleDrop(lineItemId);
                  }}
                  className="flex items-center justify-center border-2 border-dashed border-mw-neutral-300 dark:border-mw-neutral-600 rounded-md p-6 text-sm text-mw-neutral-400"
                >
                  {isBinding ? t("assignment.binding") : t("assignment.dropZone")}
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default CreativesPage;
