import { Button } from "@components/ui/Button";
import { Label } from "@components/ui/Label";
import { ModalDrawer } from "@components/ui/ModalDrawer";
import { Switch } from "@components/ui/Switch";
import {
  useUpdateDashboardWidgetsMutation,
  type DashboardWidget,
} from "@services/dashboard/dashboardSlice";
import { useTranslate } from "@tolgee/react";
import { ArrowRight } from "lucide-react";
import React, {
  useState,
  useEffect,
  useMemo,
  useCallback,
  useRef,
} from "react";

import {
  WIDGET_CATEGORIES,
  STANDALONE_WIDGET_KEYS,
} from "./dashboardWidgetConfig";

export interface WidgetVisibility {
  [key: string]: boolean;
}

interface CustomizeLayoutDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  widgetVisibility: WidgetVisibility;
  onWidgetVisibilityChange: (widgetVisibility: WidgetVisibility) => void;
  widgets?: DashboardWidget[];
  isLoadingWidgets?: boolean;
  id?: string;
}

const defaultWidgetVisibility: WidgetVisibility = {};

const CustomizeLayoutDrawer: React.FC<CustomizeLayoutDrawerProps> = ({
  isOpen,
  onClose,
  widgetVisibility,
  onWidgetVisibilityChange,
  widgets = [],
  isLoadingWidgets = false,
  id,
}) => {
  const { t } = useTranslate(["dashboard"]);
  const { t: tCommon } = useTranslate(["common"]);
  const [localWidgetVisibility, setLocalWidgetVisibility] =
    useState<WidgetVisibility>(widgetVisibility);
  const [localWidgets, setLocalWidgets] = useState<DashboardWidget[]>([]);
  const [applyError, setApplyError] = useState<string | null>(null);
  const prevOpenRef = useRef(false);

  const [updateWidgets, { isLoading: isUpdating }] =
    useUpdateDashboardWidgetsMutation();

  // Sync local state only when drawer opens; clear only when drawer closes (avoids infinite setState loop)
  useEffect(() => {
    const justOpened = isOpen && !prevOpenRef.current;
    const justClosed = !isOpen && prevOpenRef.current;

    if (justOpened) {
      setLocalWidgetVisibility(widgetVisibility);
      setLocalWidgets(widgets?.length ? [...widgets] : []);
      setApplyError(null);
    }
    if (justClosed) {
      setLocalWidgets([]);
      setApplyError(null);
    }
    prevOpenRef.current = isOpen;
  }, [isOpen, widgetVisibility, widgets]);

  const handleToggle = (widgetKey: string, checked: boolean) => {
    const updatedVisibility: WidgetVisibility = {
      ...localWidgetVisibility,
      [widgetKey]: checked,
    };

    // Update local widgets state
    setLocalWidgets((prev) =>
      prev.map((widget) =>
        widget.key === widgetKey ? { ...widget, isEnable: checked } : widget,
      ),
    );

    setLocalWidgetVisibility(updatedVisibility);
  };

  const handleCancel = () => {
    setLocalWidgetVisibility(widgetVisibility);
    setApplyError(null);
    onClose();
  };

  const handleApplyChanges = async () => {
    setApplyError(null);
    try {
      const widgetsToUpdate: DashboardWidget[] = localWidgets.map((widget) => ({
        key: widget.key,
        isEnable: localWidgetVisibility[widget.key] ?? widget.isEnable,
      }));

      await updateWidgets(widgetsToUpdate).unwrap();

      onWidgetVisibilityChange(localWidgetVisibility);
      onClose();
    } catch {
      setApplyError(t("customizeLayout.applyError"));
    }
  };

  const handleResetToDefault = () => {
    // Reset to API default (all enabled)
    const defaultVisibility: WidgetVisibility = {};
    localWidgets.forEach((widget) => {
      defaultVisibility[widget.key] = true;
    });
    setLocalWidgetVisibility(defaultVisibility);

    // Update local widgets state
    setLocalWidgets((prev) =>
      prev.map((widget) => ({ ...widget, isEnable: true })),
    );
  };

  const drawerId = id || "customize-layout-drawer";

  const getWidgetLabel = useCallback(
    (widgetKey: string): string => {
      const fallback = widgetKey
        .split("-")
        .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
        .join(" ");
      return t(`widgets.${widgetKey}`, { defaultValue: fallback }) || widgetKey;
    },
    [t],
  );

  const groupedWidgets = useMemo(() => {
    const widgetMap = new Map<string, DashboardWidget>();
    localWidgets.forEach((widget) => {
      widgetMap.set(widget.key, widget);
    });

    const categories = WIDGET_CATEGORIES.map((category) => ({
      category,
      widgets: category.children
        .map((key) => widgetMap.get(key))
        .filter((w): w is DashboardWidget => w !== undefined),
    })).filter(({ widgets }) => widgets.length > 0);

    const standalone = STANDALONE_WIDGET_KEYS.map((key) =>
      widgetMap.get(key),
    ).filter((w): w is DashboardWidget => w !== undefined);

    return { categories, standalone };
  }, [localWidgets]);

  const renderWidgetRow = useCallback(
    (widget: DashboardWidget, options: { showArrow?: boolean }) => {
      const isChecked = localWidgetVisibility[widget.key] ?? widget.isEnable;
      const widgetLabel = getWidgetLabel(widget.key);
      const { showArrow = false } = options;

      return (
        <div key={widget.key}>
          <div
            id={`${drawerId}-widget-${widget.key}`}
            className="inline-flex w-full items-center justify-between py-2"
          >
            <div className="items-center gap-2 flex-1 flex">
              {showArrow && (
                <ArrowRight className="w-5 h-5 text-mw-neutral-500 shrink-0" />
              )}
              <Label
                htmlFor={`${drawerId}-widget-${widget.key}-switch`}
                className="text-base font-normal leading-5"
              >
                {widgetLabel}
              </Label>
            </div>
            <Switch
              id={`${drawerId}-widget-${widget.key}-switch`}
              checked={isChecked}
              onChange={(checked) => handleToggle(widget.key, checked)}
              size="md"
              disabled={isUpdating}
            />
          </div>
        </div>
      );
    },
    [drawerId, localWidgetVisibility, getWidgetLabel, isUpdating],
  );

  return (
    <ModalDrawer
      id={drawerId}
      isOpen={isOpen}
      onClose={handleCancel}
      title={t("customizeLayout.title")}
      position="right"
      size="lg"
      footer={
        <div
          id={`${drawerId}-footer`}
          className="flex items-center justify-between w-full"
        >
          <Button
            id={`${drawerId}-reset-btn`}
            variant="ghost"
            size="md"
            onClick={handleResetToDefault}
            className="text-mw-primary-500 text-base"
          >
            {t("customizeLayout.resetToDefault")}
          </Button>
          <div className="flex gap-3">
            <Button
              id={`${drawerId}-cancel-btn`}
              variant="outline"
              size="md"
              onClick={handleCancel}
            >
              {tCommon("buttons.cancel")}
            </Button>
            <Button
              id={`${drawerId}-apply-btn`}
              variant="primary"
              size="md"
              onClick={handleApplyChanges}
              disabled={isUpdating || isLoadingWidgets}
            >
              {isUpdating
                ? t("customizeLayout.applying")
                : t("customizeLayout.applyLayout")}
            </Button>
          </div>
        </div>
      }
    >
      <div id={`${drawerId}-content`} className="space-y-4">
        <div className="text-mw-neutral-500 text-sm font-normal leading-4">
          {t("customizeLayout.description")}
        </div>
        {applyError && (
          <div
            role="alert"
            className="text-sm text-mw-error-500 bg-mw-error-50 p-3 rounded"
          >
            {applyError}
          </div>
        )}
        <div id={`${drawerId}-widget-list`} className="space-y-1">
          {isLoadingWidgets ? (
            <div className="text-center py-4 text-mw-neutral-500">
              {t("customizeLayout.loadingWidgets")}
            </div>
          ) : localWidgets.length === 0 ? (
            <div className="text-center py-4 text-mw-neutral-500">
              {t("customizeLayout.noWidgets")}
            </div>
          ) : (
            <>
              {groupedWidgets.standalone.map((widget) =>
                renderWidgetRow(widget, { showArrow: false }),
              )}
              {groupedWidgets.categories.map(({ category, widgets }) => (
                <div key={category.key} className="space-y-1">
                  <div className="py-2">
                    <div className="text-md font-normal leading-5 text-mw-neutral-700">
                      {t(category.labelKey)}
                    </div>
                  </div>
                  {widgets.map((widget) =>
                    renderWidgetRow(widget, { showArrow: true }),
                  )}
                </div>
              ))}
            </>
          )}
        </div>
      </div>
    </ModalDrawer>
  );
};

export default CustomizeLayoutDrawer;
export { defaultWidgetVisibility };
