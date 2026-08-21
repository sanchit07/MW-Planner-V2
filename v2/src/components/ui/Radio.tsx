import { clsx } from "clsx";

export interface RadioProps
  extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "type"> {
  label?: string;
  description?: string;
  error?: string;
}

export const Radio: React.FC<RadioProps> = ({
  className,
  label,
  description,
  error,
  id,
  ...props
}) => {
  const radioId =
    id ||
    (props.name
      ? `radio-${props.name}-${props.value}`
      : `radio-${label?.toLowerCase().replace(/\s+/g, "-") || "field"}-${props.value || "option"}`);
  return (
    <div className="space-y-2">
      <div className="flex items-start space-x-2">
        <input
          type="radio"
          id={radioId}
          className={clsx(
            "mt-0.5 h-4 w-4 border-mw-gray-300 dark:border-mw-gray-600",
            "text-mw-primary-500 focus:ring-mw-primary-500 focus:ring-offset-2",
            "disabled:opacity-50 disabled:cursor-not-allowed",
            error &&
              "border-mw-error-300 text-mw-error-600 focus:ring-mw-error-500",
            className,
          )}
          {...props}
        />
        {(label || description) && (
          <div className="flex-1 min-w-0">
            {label && (
              <label
                htmlFor={radioId}
                className="text-sm font-medium text-mw-gray-700 dark:text-mw-gray-300"
              >
                {label}
              </label>
            )}
            {description && (
              <p className="text-sm text-mw-gray-500 dark:text-mw-gray-400">
                {description}
              </p>
            )}
          </div>
        )}
      </div>
      {error && (
        <p
          id={`${radioId}-error`}
          className="text-sm text-mw-error-600 dark:text-mw-error-400"
        >
          {error}
        </p>
      )}
    </div>
  );
};
