import { cn } from "@utils/tailwindMerge";

export interface BadgeProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?:
    | "default"
    | "secondary"
    | "outline"
    | "destructive"
    | "success"
    | "warning";
  size?: "sm" | "md";
}

const badgeVariants = {
  default: "bg-mw-primary-500 text-white",
  secondary: "bg-mw-primary-100 text-mw-primary-500",
  outline:
    "text-mw-neutral-500 outline outline-1 outline-offset-[-1px] outline-mw-neutral-500 bg-transparent",
  destructive: "bg-mw-error-100 text-mw-error-500",
  success: "bg-mw-success-100 text-mw-success-500",
  warning: "bg-mw-warning-100 text-mw-warning-500",
};

const badgeSizes = {
  sm: "px-1.5 py-1 text-xs rounded-sm",
  md: "px-2 py-1.5 text-sm rounded-full",
};

export const Badge: React.FC<BadgeProps> = ({
  className,
  variant = "outline",
  size = "sm",
  children,
  ...props
}) => {
  return (
    <div
      className={cn(
        "flex items-center justify-start font-medium",
        badgeVariants[variant],
        badgeSizes[size],
        className,
      )}
      {...props}
    >
      {children}
    </div>
  );
};
