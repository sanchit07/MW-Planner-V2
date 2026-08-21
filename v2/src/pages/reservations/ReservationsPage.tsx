import { useNamespace } from "@components/Tolgee/RouteNamespaceManager";
import { Badge } from "@components/ui/Badge";
import { Button } from "@components/ui/Button";
import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@components/ui/Tabs";
import {
  Reservation,
  useApproveReservationMutation,
  useApproveReservationWithConditionsMutation,
  useDeclineReservationMutation,
  useExtendReservationMutation,
  useListReservationsForCampaignQuery,
  useListReservationsForMediaOwnerQuery,
  useReleaseReservationMutation,
} from "@services/reservation/reservationSlice";
import { useTranslate } from "@tolgee/react";
import React, { useState } from "react";

import PageHeader from "../../components/PageHeader";

const inputClass =
  "block w-full h-10 px-3 py-2 border rounded-md text-sm border-mw-neutral-200 dark:border-mw-neutral-600 text-mw-neutral-700 dark:text-mw-neutral-200 focus:outline-none focus:ring-1 focus:ring-mw-primary-500 dark:bg-mw-neutral-800";

const statusVariant: Record<
  string,
  "success" | "warning" | "destructive" | "default" | "secondary"
> = {
  PENDING: "secondary",
  HOLD_REQUESTED: "warning",
  RESERVED: "success",
  EXPIRED: "destructive",
  RELEASED: "secondary",
  DECLINED: "destructive",
  BOOKED: "success",
};

function unwrap<T>(response: unknown): T[] {
  return response && typeof response === "object" && "success" in response && (response as { success: boolean }).success
    ? ((response as { data: T[] }).data ?? [])
    : [];
}

function hoursUntil(expiresAt?: string): number | null {
  if (!expiresAt) return null;
  return Math.max(0, (new Date(expiresAt).getTime() - Date.now()) / 3_600_000);
}

const ReservationsPage: React.FC = () => {
  const { namespace } = useNamespace();
  const { t } = useTranslate([namespace]);

  const [campaignId, setCampaignId] = useState("");
  const { data: myHoldsResponse } = useListReservationsForCampaignQuery(
    { campaignId },
    { skip: !campaignId },
  );
  const myHolds = unwrap<Reservation>(myHoldsResponse);

  const { data: holdRequestsResponse } = useListReservationsForMediaOwnerQuery();
  const holdRequests = unwrap<Reservation>(holdRequestsResponse);

  const [extendReservation] = useExtendReservationMutation();
  const [releaseReservation] = useReleaseReservationMutation();
  const [approveReservation] = useApproveReservationMutation();
  const [approveWithConditions] = useApproveReservationWithConditionsMutation();
  const [declineReservation] = useDeclineReservationMutation();

  const [conditionDraft, setConditionDraft] = useState<Record<string, string>>({});
  const [declineDraft, setDeclineDraft] = useState<Record<string, string>>({});

  const renderStatusBadge = (status: string) => (
    <Badge variant={statusVariant[status] ?? "default"} size="sm">
      {t(`status.${status}`)}
    </Badge>
  );

  // "Approve with conditions" doesn't change status — the condition only exists as a comment,
  // so both sides need to see it here or the negotiation has nowhere to surface.
  const renderComments = (reservation: Reservation) =>
    reservation.comments && reservation.comments.length > 0 && (
      <div className="space-y-1 pt-1 border-t border-mw-neutral-100 dark:border-mw-neutral-800">
        <p className="text-xs font-medium text-mw-neutral-500">{t("comments.title")}</p>
        {reservation.comments.map((comment, i) => (
          <p key={i} className="text-xs text-mw-neutral-600 dark:text-mw-neutral-300">
            {comment.text}
          </p>
        ))}
      </div>
    );

  return (
    <div id="reservations-page" className="h-full overflow-y-auto">
      <PageHeader title={t("title")} descriptionKey={t("description")} />

      <div className="max-w-[65rem] mx-auto py-6 px-6">
        <Tabs defaultValue="myHolds">
          <TabsList className="w-full">
            <TabsTrigger value="myHolds">{t("tabs.myHolds")}</TabsTrigger>
            <TabsTrigger value="holdRequests">{t("tabs.holdRequests")}</TabsTrigger>
          </TabsList>

          <TabsContent value="myHolds" className="mt-4">
            <Card>
              <CardHeader className="p-6 pb-4 border-b border-container-border">
                <CardTitle>
                  <span className="text-lg font-semibold">{t("tabs.myHolds")}</span>
                </CardTitle>
              </CardHeader>
              <CardContent className="p-6 pt-6 space-y-4">
                <div className="space-y-1 max-w-sm">
                  <label className="text-sm font-medium text-mw-neutral-700 dark:text-mw-neutral-200">
                    {t("myHolds.campaignId")}
                  </label>
                  <input
                    className={inputClass}
                    value={campaignId}
                    onChange={(e) => setCampaignId(e.target.value)}
                    placeholder={t("myHolds.campaignIdPlaceholder")}
                  />
                </div>

                {campaignId &&
                  (myHolds.length === 0 ? (
                    <p className="text-sm text-mw-neutral-500">{t("myHolds.empty")}</p>
                  ) : (
                    <div className="space-y-2">
                      {myHolds.map((reservation) => {
                        const hrsLeft = hoursUntil(reservation.expiresAt);
                        return (
                          <div
                            key={reservation.id}
                            className="border border-mw-neutral-200 dark:border-mw-neutral-700 rounded-md p-3 space-y-2"
                          >
                            <div className="flex items-center justify-between">
                              <div className="space-y-1">
                                <p className="text-sm font-mono">{reservation.inventoryId}</p>
                                {hrsLeft != null && (
                                  <p className="text-xs text-mw-neutral-500">
                                    {hrsLeft > 0
                                      ? t("expiresIn", {
                                          days: Math.floor(hrsLeft / 24),
                                          hours: Math.floor(hrsLeft % 24),
                                        })
                                      : t("expired")}
                                  </p>
                                )}
                              </div>
                              <div className="flex items-center gap-2">
                                {renderStatusBadge(reservation.status)}
                                {reservation.status === "RESERVED" && (
                                  <>
                                    <Button
                                      type="button"
                                      variant="outline"
                                      size="sm"
                                      onClick={() =>
                                        extendReservation({ id: reservation.id, additionalDays: 7 })
                                      }
                                    >
                                      {t("myHolds.extend")}
                                    </Button>
                                    <Button
                                      type="button"
                                      variant="outline"
                                      size="sm"
                                      onClick={() => releaseReservation({ id: reservation.id })}
                                    >
                                      {t("myHolds.release")}
                                    </Button>
                                  </>
                                )}
                              </div>
                            </div>
                            {renderComments(reservation)}
                          </div>
                        );
                      })}
                    </div>
                  ))}
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="holdRequests" className="mt-4">
            <Card>
              <CardHeader className="p-6 pb-4 border-b border-container-border">
                <CardTitle>
                  <span className="text-lg font-semibold">{t("tabs.holdRequests")}</span>
                </CardTitle>
              </CardHeader>
              <CardContent className="p-6 pt-6 space-y-3">
                {holdRequests.length === 0 ? (
                  <p className="text-sm text-mw-neutral-500">{t("holdRequests.empty")}</p>
                ) : (
                  holdRequests.map((reservation) => (
                    <div
                      key={reservation.id}
                      className="border border-mw-neutral-200 dark:border-mw-neutral-700 rounded-md p-3 space-y-3"
                    >
                      <div className="flex items-center justify-between">
                        <p className="text-sm font-mono">
                          {reservation.campaignId} · {reservation.inventoryId}
                        </p>
                        {renderStatusBadge(reservation.status)}
                      </div>
                      {renderComments(reservation)}
                      {reservation.status === "HOLD_REQUESTED" && (
                        <div className="flex flex-wrap items-center gap-2">
                          <Button
                            type="button"
                            variant="primary"
                            size="sm"
                            onClick={() => approveReservation({ id: reservation.id })}
                          >
                            {t("holdRequests.approve")}
                          </Button>
                          <div className="space-y-1">
                            <input
                              className={`${inputClass} max-w-xs`}
                              placeholder={t("holdRequests.conditionPlaceholder")}
                              value={conditionDraft[reservation.id] ?? ""}
                              onChange={(e) =>
                                setConditionDraft((d) => ({ ...d, [reservation.id]: e.target.value }))
                              }
                            />
                            {conditionDraft[reservation.id] && (
                              <p className="text-xs text-mw-neutral-500 max-w-xs">
                                {t("holdRequests.conditionHint")}
                              </p>
                            )}
                          </div>
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            disabled={!conditionDraft[reservation.id]}
                            onClick={() =>
                              approveWithConditions({
                                id: reservation.id,
                                comment: conditionDraft[reservation.id],
                              })
                            }
                          >
                            {t("holdRequests.approveWithConditions")}
                          </Button>
                          <input
                            className={`${inputClass} max-w-xs`}
                            placeholder={t("holdRequests.declineReasonPlaceholder")}
                            value={declineDraft[reservation.id] ?? ""}
                            onChange={(e) =>
                              setDeclineDraft((d) => ({ ...d, [reservation.id]: e.target.value }))
                            }
                          />
                          <Button
                            type="button"
                            variant="destructive"
                            size="sm"
                            disabled={!declineDraft[reservation.id]}
                            onClick={() =>
                              declineReservation({
                                id: reservation.id,
                                reason: declineDraft[reservation.id],
                              })
                            }
                          >
                            {t("holdRequests.decline")}
                          </Button>
                        </div>
                      )}
                    </div>
                  ))
                )}
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
};

export default ReservationsPage;
