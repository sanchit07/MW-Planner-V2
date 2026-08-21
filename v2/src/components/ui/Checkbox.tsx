import { clsx } from "clsx";

export interface CheckboxProps
  extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "type"> {
  label?: string;
  description?: string;
  error?: string;
  isIndeterminate?: boolean;
}

export const Checkbox: React.FC<CheckboxProps> = ({
  className,
  label,
  description,
  error,
  isIndeterminate = false,
  id,
  ...props
}) => {
  const checkboxId =
    id ||
    (props.name
      ? `checkbox-${props.name}`
      : `checkbox-${label?.toLowerCase().replace(/\s+/g, "-") || "field"}`);
  return (
    <div className="space-y-2">
      <div className="flex items-center space-x-3">
        <input
          type="checkbox"
          id={checkboxId}
          className={clsx(
            "h-4 w-4 accent-mw-primary-500 border-mw-neutral-100 rounded",
            "text-mw-primary-500 focus:ring-mw-primary-600 focus:ring-offset-2",
            "disabled:opacity-50 disabled:cursor-not-allowed",
            "peer-checked:outline peer-checked:outline-offset-[-0.50px] ",
            error &&
              "accent-mw-error-500 border-mw-error-300 text-mw-error-600 focus:ring-mw-error-500",
            className,
          )}
          ref={(el) => {
            if (el) el.indeterminate = isIndeterminate;
          }}
          {...props}
        />
        {(label || description) && (
          <div className="flex-1 min-w-0">
            {label && (
              <label
                htmlFor={checkboxId}
                className="text-sm font-medium text-mw-neutral-500 dark:text-mw-neutral-300 cursor-pointer"
                onClick={(e) => {
                  e.stopPropagation();
                }}
              >
                {label}
              </label>
            )}
            {description && (
              <div className="text-sm text-mw-neutral-400 dark:text-mw-neutral-400">
                {description}
              </div>
            )}
          </div>
        )}
      </div>
      {error && (
        <p
          id={`${checkboxId}-error`}
          className="text-sm text-mw-error-500 dark:text-mw-error-400"
        >
          {error}
        </p>
      )}
    </div>
  );
};
