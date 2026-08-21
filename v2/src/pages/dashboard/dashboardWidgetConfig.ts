export interface WidgetCategory {
  key: string;
  labelKey: string;
  children: string[];
}

export const WIDGET_CATEGORIES: WidgetCategory[] = [
  {
    key: "revenue-performance",
    labelKey: "categories.revenuePerformance",
    children: [
      "sales-overview",
      "sales-performance-summary",
      "sales-pipeline-funnel",
      // Hidden: Revenue Distribution ghost toggle (unfinished, no widget wired)
      // "revenue-distribution",
    ],
  },
  {
    key: "budget-tracker",
    labelKey: "categories.budgetTracker",
    children: ["budget-overview", "budget-performance-summary"],
  },
  // Hidden: Inventory & Utilization Summary category — ghost toggles, no
  // widget rendered (InventoryUtilizationSummary commented out in DashboardPage)
  // {
  //   key: "inventory-utilization",
  //   labelKey: "Inventory & Utilization Summary",
  //   children: ["inventory-overview", "utilization-breakdown"],
  // },
];

export const STANDALONE_WIDGET_KEYS = [
  "campaign-overview",
  "campaign-performance",
  // Hidden: Creative Status ghost toggle (CreativeStatusTracker commented out)
  // "creative-status",
  "regional-inventory-snapshot",
  "audience-reach-performance",
] as const;

const REVENUE_CHILD_KEYS = WIDGET_CATEGORIES.find(
  (c) => c.key === "revenue-performance",
)?.children ?? [
  "sales-overview",
  "sales-performance-summary",
  "sales-pipeline-funnel",
  // Hidden: Revenue Distribution ghost toggle (unfinished, no widget wired)
  // "revenue-distribution",
];

const BUDGET_CHILD_KEYS = WIDGET_CATEGORIES.find(
  (c) => c.key === "budget-tracker",
)?.children ?? ["budget-overview", "budget-performance-summary"];

export function getRevenueChildKeys(): string[] {
  return [...REVENUE_CHILD_KEYS];
}

export function getBudgetChildKeys(): string[] {
  return [...BUDGET_CHILD_KEYS];
}
