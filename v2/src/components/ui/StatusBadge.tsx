import clsx from "clsx";
import React from "react";
// Status Badge Component for table cells
export interface StatusBadgeProps {
  status:
    | "draft"
    | "planned"
    | "reviewing"
    | "negotiating"
    | "pending"
    | "approved"
    | "active"
    | "rejected"
    | "completed"
    | "archived"
    | string;
  children: React.ReactNode;
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({
  status,
  children,
}) => {
  const statusClasses = {
    draft: "bg-mw-neutral-50 text-mw-neutral-700",
    planned: "bg-mw-purple-50 text-mw-purple-500",
    reviewing: "bg-mw-primary-50 text-mw-primary-500",
    negotiating: "bg-mw-orange-warning-50 text-mw-orange-warning-800",
    pending: "bg-mw-warning-50 text-mw-warning-500",
    approved: "bg-mw-success-100 text-mw-success-500",
    rejected: "bg-mw-error-100 text-mw-error-500",
    active: "bg-mw-light-green-50 text-mw-light-green-500",
    archived: "bg-mw-blush-grey-50 text-mw-blush-grey-500 ",
    completed: "bg-mw-purple-warning-50 text-mw-purple-warning-500",
  };

  return (
    <span
      className={clsx(
        "inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium",
        statusClasses[status as keyof typeof statusClasses] ||
          "bg-mw-neutral-50 text-mw-neutral-500",
      )}
    >
      <div
        className={clsx("w-2 h-2 rounded-full mr-1", {
          "bg-mw-neutral-700": status === "draft",
          "bg-mw-purple-500": status === "planned",
          "bg-mw-primary-500": status === "reviewing",
          "bg-mw-orange-warning-800": status === "negotiating",
          "bg-mw-warning-500": status === "pending",
          "bg-mw-success-500": status === "approved",
          "bg-mw-error-500": status === "rejected",
          "bg-mw-light-green-500": status === "active",
          "bg-mw-blush-grey-500": status === "archived",
          "bg-mw-purple-warning-500": status === "completed",
        })}
      />
      {children}
    </span>
  );
};
