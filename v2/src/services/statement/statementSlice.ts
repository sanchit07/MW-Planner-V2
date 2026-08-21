import axiosBaseQuery, {
  CustomErrorResponse,
  SuccessResponse,
} from "@api/axiosBaseQuery";
import { createApi } from "@reduxjs/toolkit/query/react";

export type StatementStatus =
  | "DRAFT"
  | "FINALIZED"
  | "SENT"
  | "PAID"
  | "PARTIALLY_PAID"
  | "OVERDUE"
  | "CANCELLED";

export type SplitMethod = "EQUAL" | "MONTHLY" | "WEEKLY" | "CAMPAIGN_BASED" | "CUSTOM";

export interface StatementFeeSnapshot {
  customFeeId: string;
  name: string;
  type: "PERCENTAGE" | "VALUE";
  value: number;
  isIncludeInMediaPlan: boolean;
  calculatedAmount: number;
}

export interface StatementLine {
  campaignId: string;
  mediaCost?: number;
  visibleFeesTotal?: number;
  feeSnapshot?: StatementFeeSnapshot[];
}

export interface StatementSplit {
  label: string;
  amount: number;
}

export interface Statement {
  id: string;
  statementNumber: string;
  companyId: string;
  status: StatementStatus;
  lines: StatementLine[];
  platformFeePercentage: number;
  splitConfig?: { method: SplitMethod; splits: StatementSplit[] };
  parentStatementId?: string;
  splitIdentifier?: string;
  syncStatus?: Record<
    string,
    { externalId?: string; status: string; syncedAt?: string; lastError?: string }
  >;
  locked: boolean;
  totalMediaCost?: number;
  totalFees?: number;
  totalPlatformFee?: number;
  totalAmount?: number;
}

export interface StatementCandidate {
  campaignId: string;
  campaignName?: string;
  eligible: boolean;
  exclusionReason?: string;
  mediaCost?: number;
}

export const statementApi = createApi({
  reducerPath: "statementApi",
  baseQuery: axiosBaseQuery(),
  tagTypes: ["Statement"],
  endpoints: (builder) => ({
    listStatements: builder.query<SuccessResponse<Statement[]> | CustomErrorResponse, void>({
      query: () => ({ url: "/statements", method: "GET" }),
      providesTags: ["Statement"],
    }),
    getStatement: builder.query<
      SuccessResponse<Statement> | CustomErrorResponse,
      { id: string }
    >({
      query: ({ id }) => ({ url: `/statements/${id}`, method: "GET" }),
      providesTags: ["Statement"],
    }),
    listStatementCandidates: builder.query<
      SuccessResponse<StatementCandidate[]> | CustomErrorResponse,
      { campaignIds: string[] }
    >({
      query: ({ campaignIds }) => ({
        url: "/statements/candidates",
        method: "GET",
        params: { campaignIds: campaignIds.join(",") },
      }),
    }),
    calculateStatement: builder.mutation<
      SuccessResponse<Statement> | CustomErrorResponse,
      { campaignIds: string[] }
    >({
      query: (data) => ({ url: "/statements/calculate", method: "POST", data }),
    }),
    createStatement: builder.mutation<
      SuccessResponse<Statement> | CustomErrorResponse,
      { campaignIds: string[] }
    >({
      query: (data) => ({ url: "/statements", method: "POST", data }),
      invalidatesTags: ["Statement"],
    }),
    finalizeStatement: builder.mutation<
      SuccessResponse<Statement> | CustomErrorResponse,
      { id: string }
    >({
      query: ({ id }) => ({ url: `/statements/${id}/finalize`, method: "POST" }),
      invalidatesTags: ["Statement"],
    }),
    splitStatement: builder.mutation<
      SuccessResponse<Statement[]> | CustomErrorResponse,
      { id: string; method: SplitMethod; customSplits?: StatementSplit[] }
    >({
      query: ({ id, method, customSplits }) => ({
        url: `/statements/${id}/split`,
        method: "POST",
        data: { method, customSplits },
      }),
      invalidatesTags: ["Statement"],
    }),
    markStatementSynced: builder.mutation<
      SuccessResponse<Statement> | CustomErrorResponse,
      { id: string; integration: string; externalId?: string }
    >({
      query: ({ id, integration, externalId }) => ({
        url: `/statements/${id}/sync/${integration}`,
        method: "POST",
        params: externalId ? { externalId } : undefined,
      }),
      invalidatesTags: ["Statement"],
    }),
  }),
});

export const {
  useListStatementsQuery,
  useGetStatementQuery,
  useListStatementCandidatesQuery,
  useCalculateStatementMutation,
  useCreateStatementMutation,
  useFinalizeStatementMutation,
  useSplitStatementMutation,
  useMarkStatementSyncedMutation,
} = statementApi;
