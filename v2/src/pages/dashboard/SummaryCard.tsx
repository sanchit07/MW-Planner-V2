import { Card } from "@components/ui/card";
import clsx from "clsx";
import { TrendingDown, TrendingUp } from "lucide-react";

// Summary Card Component
interface SummaryCardProps {
  icon: React.ReactNode;
  iconBgColor: string;
  title: string;
  value: string | number;
  subtitle?: string;
  trend?: {
    value: number;
    isPositive: boolean;
  };
  /** Overrides trend rendering with this label (e.g. "New") when there's no
   * previous-period data to compare against — caller supplies the translated
   * text since this component has no i18n namespace of its own. */
  trendLabel?: string;
}

const SummaryCard: React.FC<SummaryCardProps> = ({
  icon,
  iconBgColor,
  title,
  value,
  subtitle,
  trend,
  trendLabel,
}) => {
  return (
    <Card className="p-4 flex-1 inline-flex justify-start items-start gap-4 relative">
      {/* Icon */}
      <div
        className={clsx(
          "w-12 h-12 p-2.5 rounded flex justify-center items-center",
          iconBgColor,
        )}
      >
        {icon}
      </div>

      {/* Content */}
      <div className="flex-1 flex flex-col gap-1">
        <div className="text-s font-semibold text-black">{title}</div>
        <div className="text-l font-semibold text-mw-primary-500">{value}</div>
        {subtitle && (
          <div className="flex items-center justify-between gap-2">
            <span className="text-xs text-mw-neutral-500">{subtitle}</span>
            {trendLabel ? (
              <span className="text-sm font-medium text-mw-neutral-500">
                {trendLabel}
              </span>
            ) : (
              trend && (
                <div className="flex items-center gap-0.5">
                  <span
                    className={clsx(
                      "text-sm font-medium",
                      trend.isPositive
                        ? "text-mw-success-500"
                        : "text-mw-error-500",
                    )}
                  >
                    {trend.value}%
                  </span>
                  {trend.isPositive ? (
                    <TrendingUp className="w-3 h-3 text-mw-success-500" />
                  ) : (
                    <TrendingDown className="w-3 h-3 text-mw-error-500" />
                  )}
                </div>
              )
            )}
          </div>
        )}
      </div>
    </Card>
  );
};
export default SummaryCard;
