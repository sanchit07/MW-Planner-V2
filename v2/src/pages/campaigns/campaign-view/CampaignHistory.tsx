import { AgGridTable } from "@components/ui/AgGridTable";
import { Card, CardContent } from "@components/ui/card";
import { useLazyGetCampaignHistoryQuery } from "@services/campaign/campaignSlice";
import { useTranslate, useTolgee } from "@tolgee/react";
import type { ColDef } from "ag-grid-community";
import { useCallback, useEffect, useMemo, useState } from "react";

import { CampaignHistoryItem } from "../../../types/campaign.types";
import { formatAuditMessage } from "../../../utils/auditMessage.utils";

interface CampaignHistoryProps {
  campaignId: string;
}

interface HistoryTableRow extends CampaignHistoryItem {
  serialNumber: number;
  formattedDate: string;
}

interface HistoryRowPlaceholder {
  id: string;
  __isPlaceholder: true;
}

function isPlaceholder(
  row: HistoryTableRow | HistoryRowPlaceholder,
): row is HistoryRowPlaceholder {
  return (row as HistoryRowPlaceholder).__isPlaceholder === true;
}

const CampaignHistory = ({ campaignId }: CampaignHistoryProps) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { t: tCommon } = useTranslate(["common"]);
  const language = useTolgee(["language"]).getLanguage();
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);

  const [
    getCampaignHistory,
    { data: historyData, isLoading: isHistoryLoading, isError: isHistoryError },
  ] = useLazyGetCampaignHistoryQuery();

  useEffect(() => {
    if (campaignId) {
      getCampaignHistory({
        campaignId,
        params: {
          page,
          size: pageSize,
        },
        language,
      });
    }
  }, [campaignId, page, pageSize, language, getCampaignHistory]);

  const handlePageChange = useCallback((newPage: number) => {
    setPage(newPage - 1);
  }, []);

  const handlePageSizeChange = useCallback((newPageSize: number) => {
    setPageSize(newPageSize);
    setPage(0);
  }, []);

  const totalItems = historyData?.data?.totalElements || 0;
  const currentPage = (historyData?.data?.number ?? 0) + 1;

  const tableData: HistoryTableRow[] = useMemo(() => {
    const historyItems = historyData?.data?.content || [];
    return historyItems.map((item, index) => {
      const serialNumber = totalItems - (page * pageSize + index);
      const date = new Date(item.createdAt);
      const month = tCommon(`calendar.monthNamesShort.${date.getMonth()}`);
      const day = date.getDate().toString().padStart(2, "0");
      const year = date.getFullYear();
      const formattedDate = tCommon("calendar.formattedShortDate", {
        month,
        day,
        year,
      });
      return {
        ...item,
        serialNumber,
        formattedDate,
      };
    });
  }, [historyData?.data?.content, totalItems, page, pageSize, tCommon]);

  const fullRowData = useMemo((): (
    | HistoryTableRow
    | HistoryRowPlaceholder
  )[] => {
    if (totalItems === 0) return [];
    const start = page * pageSize;
    const rows: (HistoryTableRow | HistoryRowPlaceholder)[] = Array.from(
      { length: totalItems },
      (_, i) => ({
        id: `history-placeholder-${i}`,
        __isPlaceholder: true as const,
      }),
    );
    tableData.forEach((row, i) => {
      rows[start + i] = row;
    });
    return rows;
  }, [tableData, totalItems, page, pageSize]);

  const columnDefs = useMemo(
    (): ColDef<HistoryTableRow | HistoryRowPlaceholder>[] => [
      {
        colId: "serialNumber",
        headerName: tCampaigns("campaignHistory.columns.serialNumber"),
        sortable: false,
        width: 80,
        minWidth: 64,
        valueGetter: (params: {
          data?: HistoryTableRow | HistoryRowPlaceholder;
        }) =>
          params.data && !isPlaceholder(params.data)
            ? params.data.serialNumber
            : null,
        cellRenderer: (params: {
          data?: HistoryTableRow | HistoryRowPlaceholder;
          value?: number | null;
        }) => {
          if (!params.data || isPlaceholder(params.data))
            return (
              <p className="text-black text-xs font-normal leading-4">—</p>
            );
          return (
            <p className="text-black text-xs font-normal leading-4">
              {String(params.value ?? "").padStart(2, "0")}
            </p>
          );
        },
      },
      {
        colId: "formattedDate",
        headerName: tCampaigns("campaignHistory.columns.date"),
        sortable: false,
        field: "formattedDate",
        minWidth: 120,
        cellRenderer: (params: {
          data?: HistoryTableRow | HistoryRowPlaceholder;
          value?: string | null;
        }) => {
          if (!params.data || isPlaceholder(params.data))
            return (
              <p className="text-black text-xs font-normal leading-4">—</p>
            );
          return (
            <p className="text-black text-xs font-normal leading-4">
              {params.value ?? ""}
            </p>
          );
        },
      },
      {
        colId: "userDisplay",
        headerName: tCampaigns("campaignHistory.columns.user"),
        sortable: false,
        minWidth: 140,
        cellRenderer: (params: {
          data?: HistoryTableRow | HistoryRowPlaceholder;
        }) => {
          if (!params.data || isPlaceholder(params.data))
            return (
              <p className="text-black text-xs font-normal leading-4">—</p>
            );
          const row = params.data;
          return (
            <div className="space-y-1">
              <p className="text-black text-xs font-normal leading-4">
                {row.createdBy}
              </p>
              {row.role && (
                <p className="text-secondary text-[10px] font-normal leading-4">
                  {row.role}
                </p>
              )}
            </div>
          );
        },
      },
      {
        colId: "message",
        headerName: tCampaigns("campaignHistory.columns.action"),
        sortable: false,
        flex: 1,
        field: "message",
        minWidth: 200,
        wrapText: true,
        autoHeight: true,
        cellRenderer: (params: {
          data?: HistoryTableRow | HistoryRowPlaceholder;
          value?: string | null;
        }) => {
          if (!params.data || isPlaceholder(params.data))
            return (
              <p className="text-black text-xs font-normal leading-4">—</p>
            );
          const message = params.value;
          if (message == null) return null;
          return (
            <p className="text-black text-xs font-normal leading-relaxed whitespace-normal break-words py-2">
              {formatAuditMessage(message)}
            </p>
          );
        },
      },
    ],
    [tCampaigns],
  );

  const emptyMessage = isHistoryError
    ? tCampaigns("campaignHistory.errorLoading")
    : tCampaigns("campaignHistory.noHistory");

  return (
    <div className="space-y-4 h-full">
      <Card className="flex flex-col h-full">
        <CardContent className="flex-1 overflow-hidden pt-4 flex flex-col min-h-0">
          <div
            id="campaign-history-table-container"
            className="flex-1 min-h-0 rounded-lg"
          >
            <AgGridTable<HistoryTableRow | HistoryRowPlaceholder>
              rowData={fullRowData}
              columnDefs={columnDefs}
              getRowId={(row) => row.id}
              loading={isHistoryLoading}
              emptyMessage={emptyMessage}
              height="100%"
              serverSidePagination
              cacheBlockSize={pageSize}
              serverSideConfig={{
                totalCount: totalItems,
                currentPage,
                pageSize,
                onPageChange: handlePageChange,
                onPageSizeChange: handlePageSizeChange,
                pageSizeSelector: [10, 25, 50, 100],
              }}
            />
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export default CampaignHistory;
