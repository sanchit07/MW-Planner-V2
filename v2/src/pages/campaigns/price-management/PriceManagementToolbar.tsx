import { Button } from "@components/ui/Button";
import { Input } from "@components/ui/Input";
import { useTranslate } from "@tolgee/react";
import { clsx } from "clsx";
import {
  Calendar,
  Columns3Cog,
  History,
  Map,
  Rows3,
  Save,
  Search,
  SlidersHorizontal,
} from "lucide-react";

export type PriceManagementViewType = "grid" | "mapView" | "calender";

interface PriceManagementToolbarProps {
  searchValue: string;
  onSearchChange: (value: string) => void;
  /** Runs the search. Only fired on Enter, not on every keystroke. */
  onSearchSubmit: () => void;
  viewType: PriceManagementViewType;
  onViewChange: (viewType: PriceManagementViewType) => void;
  activeFilterCount: number;
  onOpenFilters: () => void;
  onOpenColumns: () => void;
  onOpenSummary: () => void;
  onOpenHistory: () => void;
  /** True while a table refetch is in flight - disables the controls that
   * would otherwise fire another overlapping request (search, filters, view
   * toggle). Columns/Summary stay enabled since they only open a drawer. */
  disabled?: boolean;
}

const VIEW_BUTTON_BASE = "rounded-none px-2.5 py-2";
const VIEW_BUTTON_ACTIVE =
  "bg-mw-neutral-200 dark:bg-mw-neutral-700 text-mw-neutral-700 dark:text-white";
const VIEW_BUTTON_INACTIVE = "text-mw-neutral-500 dark:text-mw-neutral-400";

/**
 * Toolbar for the campaign price management page:
 * search + filters on the left, view switcher and table actions on the right.
 */
export const PriceManagementToolbar: React.FC<PriceManagementToolbarProps> = ({
  searchValue,
  onSearchChange,
  onSearchSubmit,
  viewType,
  onViewChange,
  activeFilterCount,
  onOpenFilters,
  onOpenColumns,
  onOpenSummary,
  onOpenHistory,
  disabled = false,
}) => {
  const { t } = useTranslate(["price"]);

  const views: Array<{
    id: string;
    value: PriceManagementViewType;
    icon: React.ReactNode;
    title: string;
  }> = [
    {
      id: "campaigns-view-list-btn",
      value: "grid",
      icon: <Rows3 className="h-4 w-4" />,
      title: t("actions.view"),
    },
    {
      id: "campaigns-view-map-btn",
      value: "mapView",
      icon: <Map className="h-4 w-4" />,
      title: t("actions.map"),
    },
    {
      id: "campaigns-view-calendar-btn",
      value: "calender",
      icon: <Calendar className="h-4 w-4" />,
      title: t("actions.calendar"),
    },
  ];

  return (
    <div
      id="campaigns-header-actions"
      className="flex items-center justify-between gap-4"
    >
      {/* Left side - Search and Filters */}
      <div className="flex items-center gap-2">
        <div id="campaigns-search-container" className="relative">
          <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
            <Search className="h-4 w-4 text-mw-neutral-400" />
          </div>
          <Input
            id="campaigns-search-input"
            type="text"
            value={searchValue}
            onChange={(e) => onSearchChange(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                onSearchSubmit();
              }
            }}
            disabled={disabled}
            placeholder={t("actions.search_placeholder")}
            className="w-80 pl-10 pr-3 py-2"
          />
        </div>

        <Button
          id="campaigns-filter-btn"
          variant="outline"
          size="sm"
          onClick={onOpenFilters}
          disabled={disabled}
          className="relative inline-flex items-center gap-2 px-3 py-2.5 text-sm font-medium"
        >
          <SlidersHorizontal className="h-4 w-4" />
          {t("actions.filter")}
          {activeFilterCount > 0 && (
            <span
              id="campaigns-filter-badge"
              className="absolute -top-2 -right-2 inline-flex items-center justify-center px-2 py-1 text-xs font-bold leading-none text-white bg-mw-primary-600 rounded-full min-w-[1.25rem] h-5"
            >
              {activeFilterCount}
            </span>
          )}
        </Button>
      </div>

      {/* Right side - View toggle and table actions */}
      <div id="campaigns-toolbar" className="flex items-center gap-2">
        <div
          id="campaigns-view-toggle"
          className="inline-flex items-center outline -outline-offset-1 outline-mw-neutral-100 rounded-md overflow-hidden"
        >
          {views.map((view) => (
            <Button
              key={view.value}
              id={view.id}
              type="button"
              onClick={() => onViewChange(view.value)}
              disabled={disabled}
              variant="ghost"
              size="iconMd"
              className={clsx(
                VIEW_BUTTON_BASE,
                viewType === view.value
                  ? VIEW_BUTTON_ACTIVE
                  : VIEW_BUTTON_INACTIVE,
              )}
              title={view.title}
            >
              {view.icon}
            </Button>
          ))}
        </div>

        {/* Sits next to the calendar view, but opens a drawer rather than
            switching the view - so it stays outside the toggle group. */}
        <Button
          id="campaigns-history-btn"
          type="button"
          variant="ghost"
          size="iconMd"
          onClick={onOpenHistory}
          className="px-2.5 py-2 text-mw-neutral-500 dark:text-mw-neutral-400"
          title={t("actions.price_history")}
          aria-label={t("actions.price_history")}
          data-testid="button-price-history"
        >
          <History className="h-4 w-4" />
        </Button>

        <Button
          id="campaigns-columns-btn"
          variant="outline"
          size="sm"
          onClick={onOpenColumns}
          className="inline-flex items-center gap-2 px-3 py-2.5 text-sm font-medium"
        >
          <Columns3Cog className="h-4 w-4" />
          {t("actions.columns")}
        </Button>

        <Button
          id="campaigns-summary-btn"
          variant="primary"
          size="sm"
          onClick={onOpenSummary}
          className="inline-flex items-center gap-2 px-3 py-2.5 text-sm font-medium"
        >
          <Save className="h-4 w-4" />
          {t("actions.summary")}
        </Button>
      </div>
    </div>
  );
};

export default PriceManagementToolbar;
