import type { ICellRendererParams } from "ag-grid-community";

export function isPinnedBottomRow<T>(params: ICellRendererParams<T>): boolean {
  return (params.node as { rowPinned?: string })?.rowPinned === "bottom";
}

export function getPinnedCellDisplay<T>(
  params: ICellRendererParams<T>,
  options: {
    pinnedLabel?: string;
    dataKey?: keyof T | string;
    format?: (v: unknown) => string;
  },
): string {
  if (!isPinnedBottomRow(params)) {
    const value = params.value;
    return value !== undefined && value !== null ? String(value) : "";
  }
  if (options.pinnedLabel !== undefined) {
    return options.pinnedLabel;
  }
  if (options.dataKey !== undefined && params.data) {
    const v = (params.data as Record<string, unknown>)[
      options.dataKey as string
    ];
    if (v !== undefined && v !== null) {
      return options.format ? options.format(v) : String(v);
    }
  }
  return "";
}

export function getPinnedNumberDisplay<T>(
  params: ICellRendererParams<T>,
  dataKey: keyof T | string,
  format?: (n: number) => string,
): string {
  if (!isPinnedBottomRow(params)) {
    const value = params.value;
    if (typeof value === "number") {
      return format ? format(value) : String(value);
    }
    return String(value ?? "");
  }
  if (params.data) {
    const v = (params.data as Record<string, unknown>)[dataKey as string];
    if (typeof v === "number") {
      return format ? format(v) : String(v);
    }
  }
  return "";
}
