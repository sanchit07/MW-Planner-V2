import { AgGridTable } from "@components/ui/AgGridTable/AgGridTable";
import { Button } from "@components/ui/Button";
import { ModalDrawer } from "@components/ui/ModalDrawer";
import { useTranslate } from "@tolgee/react";
import type { ColDef } from "ag-grid-community";
import { AlertTriangle, Download } from "lucide-react";
import React, { useMemo } from "react";

import { InventoryCsvVerifyResult } from "../../../types/inventory.types";

type LogRow = InventoryCsvVerifyResult;

interface ViewLogDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  onBack?: () => void;
  logs?: InventoryCsvVerifyResult[];
}

export const ViewLogDrawer: React.FC<ViewLogDrawerProps> = ({
  isOpen,
  onClose,
  onBack,
  logs = [],
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const transformedLogs = useMemo((): LogRow[] => {
    return logs.map((log, index) => ({
      ...log,
      id: (index + 1).toString(), // Assign a unique ID based on index for AgGrid
    }));
  }, [logs]);

  const columnDefs = useMemo<ColDef<LogRow>[]>(
    () => [
      {
        colId: "sNo",
        headerName: tCampaigns("viewLogDrawer.columns.srNo"),
        sortable: false,
        width: 100,
        valueGetter: (params) => params.data?.id ?? null,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        cellRenderer: (params: any) => {
          const serialNumber = params.value;
          const logType = params.data?.type;

          const iconColorClass =
            logType === "INVALID"
              ? "text-mw-error-500"
              : logType === "DUPLICATE"
                ? "text-mw-neutral-500"
                : "text-mw-warning-500";

          return (
            <div className="relative inline-flex gap-2">
              <div className="inline-flex items-center justify-center">
                <AlertTriangle className={`size-3 ${iconColorClass}`} />
              </div>
              <p className="font-medium">{serialNumber}</p>
            </div>
          );
        },
      },
      {
        colId: "row",
        headerName: tCampaigns("viewLogDrawer.columns.row"),
        field: "row",
        minWidth: 80,
        valueFormatter: (params) => (params.value ? params.value : ""),
      },
      {
        colId: "message",
        headerName: tCampaigns("viewLogDrawer.columns.description"),
        field: "message",
        flex: 1,
        minWidth: 200,
      },
    ],
    [tCampaigns],
  );

  // Helper function to escape CSV values
  const escapeCsvValue = (value: string | number): string => {
    const stringValue = String(value);
    // If value contains comma, quote, or newline, wrap in quotes and escape quotes
    if (
      stringValue.includes(",") ||
      stringValue.includes('"') ||
      stringValue.includes("\n") ||
      stringValue.includes("\r")
    ) {
      // Escape double quotes by doubling them
      return `"${stringValue.replace(/"/g, '""')}"`;
    }
    return stringValue;
  };

  const handleDownloadErrorLog = () => {
    // Convert logs to CSV format
    const csvHeaders = [
      tCampaigns("viewLogDrawer.columns.srNo"),
      tCampaigns("viewLogDrawer.columns.row"),
      tCampaigns("viewLogDrawer.columns.description"),
    ];
    const csvRows = transformedLogs.map((log, index) => [
      index + 1,
      log.row,
      log.message,
    ]);

    const csvContent = [
      csvHeaders.map(escapeCsvValue).join(","),
      ...csvRows.map((row) => row.map(escapeCsvValue).join(",")),
    ].join("\n");

    // Create and download file
    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const link = document.createElement("a");
    const url = URL.createObjectURL(blob);
    link.setAttribute("href", url);
    link.setAttribute("download", `error_log${new Date()}.csv`);
    link.style.visibility = "hidden";
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  const handleBack = () => {
    if (onBack) {
      onBack();
    } else {
      onClose();
    }
  };

  return (
    <ModalDrawer
      isOpen={isOpen}
      onClose={handleBack}
      title={tCampaigns("viewLogDrawer.title")}
      size="3xl"
      showCloseButton={false}
      showBackButton={true}
      footer={
        <div className="flex justify-end">
          <Button variant="primary" onClick={handleDownloadErrorLog}>
            <Download className="w-4 h-4 mr-2" />
            {tCampaigns("viewLogDrawer.downloadErrorLog")}
          </Button>
        </div>
      }
    >
      <AgGridTable<LogRow>
        rowData={transformedLogs}
        columnDefs={columnDefs}
        getRowId={(row) => String(row.id)}
        emptyMessage={tCampaigns("viewLogDrawer.emptyMessage")}
        height="100%"
      />
    </ModalDrawer>
  );
};

export default ViewLogDrawer;
