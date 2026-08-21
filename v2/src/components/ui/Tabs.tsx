import { clsx } from "clsx";
import React, { useState } from "react";

export interface TabsProps {
  defaultValue?: string;
  value?: string;
  onValueChange?: (value: string) => void;
  children: React.ReactNode;
  className?: string;
  id?: string;
}

export interface TabsListProps {
  children: React.ReactNode;
  className?: string;
  id?: string;
}

export interface TabsTriggerProps {
  value: string;
  children: React.ReactNode;
  className?: string;
  disabled?: boolean;
}

export interface TabsContentProps {
  value: string;
  children: React.ReactNode;
  className?: string;
  id?: string;
}

const TabsContext = React.createContext<{
  value: string;
  onValueChange: (value: string) => void;
} | null>(null);

export function Tabs({
  defaultValue = "",
  value,
  onValueChange,
  children,
  className,
  id,
}: TabsProps) {
  const [internalValue, setInternalValue] = useState(defaultValue);
  const currentValue = value ?? internalValue;
  const handleValueChange = onValueChange ?? setInternalValue;

  return (
    <TabsContext.Provider
      value={{ value: currentValue, onValueChange: handleValueChange }}
    >
      <div id={id || "tabs-container"} className={clsx("w-full", className)}>
        {children}
      </div>
    </TabsContext.Provider>
  );
}

export function TabsList({ children, className, id }: TabsListProps) {
  return (
    <div
      id={id || "tabs-list"}
      className={clsx(
        "flex items-center justify-center rounded-md bg-mw-dark-grey-50 dark:bg-mw-neutral-800 p-1 w-full",
        className,
      )}
    >
      {children}
    </div>
  );
}

export function TabsTrigger({
  value,
  children,
  className,
  disabled,
}: TabsTriggerProps) {
  const context = React.useContext(TabsContext);
  if (!context) {
    throw new Error("TabsTrigger must be used within Tabs");
  }

  const isActive = context.value === value;

  return (
    <button
      id={`tab-trigger-${value}`}
      type="button"
      disabled={disabled}
      onClick={() => !disabled && context.onValueChange(value)}
      className={clsx(
        "flex-1 flex items-center justify-center whitespace-nowrap rounded-sm px-3 py-1.5 text-sm font-medium ring-offset-white transition-all cursor-pointer",
        "focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-mw-primary-500 focus-visible:ring-offset-2",
        "disabled:pointer-events-none disabled:opacity-50",
        isActive
          ? "bg-mw-primary-500 text-white shadow-sm dark:bg-mw-primary-600 dark:text-white"
          : "text-mw-neutral-500 dark:text-mw-neutral-400 hover:text-mw-neutral-500 dark:hover:text-mw-neutral-50 hover:bg-mw-dark-grey-100 dark:hover:bg-mw-neutral-700",
        className,
      )}
    >
      {children}
    </button>
  );
}

export function TabsContent({
  value,
  children,
  className,
  id,
}: TabsContentProps) {
  const context = React.useContext(TabsContext);
  if (!context) {
    throw new Error("TabsContent must be used within Tabs");
  }

  if (context.value !== value) {
    return null;
  }

  return (
    <div
      id={id || `tab-content-${value}`}
      className={clsx(
        "p-1 ring-offset-white focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-mw-primary-500 focus-visible:ring-offset-2",
        className,
      )}
    >
      {children}
    </div>
  );
}
