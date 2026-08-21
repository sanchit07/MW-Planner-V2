import { clsx } from "clsx";
import { forwardRef, useState, useRef, useEffect, useCallback } from "react";

import {
  Dropdown,
  DropdownTrigger,
  DropdownContent,
  DropdownItem,
} from "./Dropdown";
import { Label } from "./Label";

export interface MentionOption {
  id: string;
  label: string;
  value: string;
}

export interface TextareaProps
  extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  error?: string;
  helpText?: string;
  required?: boolean;
  enableMentions?: boolean;
  mentionKey?: string;
  mentionOptions?: MentionOption[];
  onMentionSelect?: (mention: MentionOption) => void;
  showCharCount?: boolean;
  maxLength?: number;
  currentLength?: number;
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(
  (
    {
      className,
      label,
      error,
      helpText,
      required = false,
      id,
      enableMentions = false,
      mentionKey = "@",
      mentionOptions = [],
      onMentionSelect,
      value,
      onChange,
      showCharCount = false,
      maxLength,
      currentLength,
      ...props
    },
    ref,
  ) => {
    const textareaId =
      id ||
      (props.name
        ? `textarea-${props.name}`
        : `textarea-${label?.toLowerCase().replace(/\s+/g, "-") || "field"}`);

    const [internalValue, setInternalValue] = useState(value || "");
    const [showMentionDropdown, setShowMentionDropdown] = useState(false);
    const [mentionQuery, setMentionQuery] = useState("");
    const [selectedMentionIndex, setSelectedMentionIndex] = useState(0);
    const [mentionStartIndex, setMentionStartIndex] = useState(-1);

    const textareaRef = useRef<HTMLTextAreaElement>(null);
    const dropdownTriggerWrapperRef = useRef<HTMLDivElement>(null);
    const combinedRef = useCallback(
      (node: HTMLTextAreaElement | null) => {
        textareaRef.current = node;
        if (typeof ref === "function") {
          ref(node);
        } else if (ref) {
          (ref as React.MutableRefObject<HTMLTextAreaElement | null>).current =
            node;
        }
      },
      [ref],
    );

    // Filter mention options based on query
    const filteredMentions = mentionOptions.filter((option) =>
      option.label.toLowerCase().includes(mentionQuery.toLowerCase()),
    );

    // Handle textarea value changes
    const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
      const newValue = e.target.value;
      setInternalValue(newValue);

      if (onChange) {
        onChange(e);
      }

      if (!enableMentions) {
        return;
      }

      const cursorPosition = e.target.selectionStart;
      const textBeforeCursor = newValue.substring(0, cursorPosition);
      const lastMentionKeyIndex = textBeforeCursor.lastIndexOf(mentionKey);

      // Check if we're typing a mention
      if (
        lastMentionKeyIndex !== -1 &&
        (lastMentionKeyIndex === 0 ||
          textBeforeCursor[lastMentionKeyIndex - 1] === " " ||
          textBeforeCursor[lastMentionKeyIndex - 1] === "\n")
      ) {
        const textAfterMentionKey = textBeforeCursor.substring(
          lastMentionKeyIndex + mentionKey.length,
        );

        // Check if there's no space or newline after @ (still typing the mention)
        if (
          !textAfterMentionKey.includes(" ") &&
          !textAfterMentionKey.includes("\n")
        ) {
          setMentionStartIndex(lastMentionKeyIndex);
          setMentionQuery(textAfterMentionKey);
          setShowMentionDropdown(true);
          setSelectedMentionIndex(0);
          updateMentionDropdownPosition(e.target, lastMentionKeyIndex);
        } else {
          setShowMentionDropdown(false);
          setMentionStartIndex(-1);
          setMentionQuery("");
        }
      } else {
        setShowMentionDropdown(false);
        setMentionStartIndex(-1);
        setMentionQuery("");
      }
    };

    // Update dropdown position based on cursor
    const updateMentionDropdownPosition = (
      textarea: HTMLTextAreaElement,
      mentionIndex: number,
    ) => {
      // Get textarea position
      const rect = textarea.getBoundingClientRect();

      // Calculate approximate position based on textarea dimensions
      const scrollTop = textarea.scrollTop;
      const lineHeight =
        parseInt(window.getComputedStyle(textarea).lineHeight) || 20;
      const paddingLeft =
        parseInt(window.getComputedStyle(textarea).paddingLeft) || 12;

      // Estimate position (simplified - works for most cases)
      const textBeforeCursor = textarea.value.substring(0, mentionIndex);
      const lines = textBeforeCursor.split("\n");
      const currentLine = lines.length - 1;
      const currentLineText = lines[currentLine] || "";

      // Approximate horizontal position (this is simplified)
      const estimatedLeft = paddingLeft + currentLineText.length * 7; // ~7px per character estimate

      const top =
        rect.top + currentLine * lineHeight + lineHeight + 5 - scrollTop;
      const left = rect.left + Math.min(estimatedLeft, rect.width - 200);

      // Update hidden trigger wrapper position
      if (dropdownTriggerWrapperRef.current) {
        dropdownTriggerWrapperRef.current.style.position = "fixed";
        dropdownTriggerWrapperRef.current.style.top = `${top}px`;
        dropdownTriggerWrapperRef.current.style.left = `${left}px`;
        dropdownTriggerWrapperRef.current.style.width = "1px";
        dropdownTriggerWrapperRef.current.style.height = "1px";
        dropdownTriggerWrapperRef.current.style.opacity = "0";
        dropdownTriggerWrapperRef.current.style.pointerEvents = "none";
        dropdownTriggerWrapperRef.current.style.zIndex = "-1";
      }
    };

    // Handle mention selection
    const handleMentionSelect = (mention: MentionOption) => {
      if (!textareaRef.current || mentionStartIndex === -1) return;

      const textarea = textareaRef.current;
      const currentValue = internalValue.toString();
      const textBeforeMention = currentValue.substring(0, mentionStartIndex);
      const textAfterMention = currentValue.substring(
        textarea.selectionStart || currentValue.length,
      );

      const newValue =
        textBeforeMention +
        `${mentionKey}${mention.label}` +
        " " +
        textAfterMention;

      setInternalValue(newValue);
      setShowMentionDropdown(false);
      setMentionStartIndex(-1);
      setMentionQuery("");

      // Create synthetic event for onChange
      const syntheticEvent = {
        target: { value: newValue },
      } as React.ChangeEvent<HTMLTextAreaElement>;

      if (onChange) {
        onChange(syntheticEvent);
      }

      // Set cursor position after the mention
      setTimeout(() => {
        const newCursorPosition =
          textBeforeMention.length +
          mentionKey.length +
          mention.label.length +
          1;
        textarea.setSelectionRange(newCursorPosition, newCursorPosition);
        textarea.focus();
      }, 0);

      if (onMentionSelect) {
        onMentionSelect(mention);
      }
    };

    // Handle keyboard navigation in dropdown
    const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
      if (!enableMentions || !showMentionDropdown) {
        if (props.onKeyDown) {
          props.onKeyDown(e);
        }
        return;
      }

      if (e.key === "ArrowDown") {
        e.preventDefault();
        setSelectedMentionIndex((prev) =>
          prev < filteredMentions.length - 1 ? prev + 1 : prev,
        );
      } else if (e.key === "ArrowUp") {
        e.preventDefault();
        setSelectedMentionIndex((prev) => (prev > 0 ? prev - 1 : 0));
      } else if (e.key === "Enter" || e.key === "Tab") {
        e.preventDefault();
        if (filteredMentions[selectedMentionIndex]) {
          handleMentionSelect(filteredMentions[selectedMentionIndex]);
        }
      } else if (e.key === "Escape") {
        setShowMentionDropdown(false);
        setMentionStartIndex(-1);
        setMentionQuery("");
      } else {
        if (props.onKeyDown) {
          props.onKeyDown(e);
        }
      }
    };

    // Programmatically trigger dropdown when showMentionDropdown changes
    useEffect(() => {
      if (showMentionDropdown && dropdownTriggerWrapperRef.current) {
        // Find the button inside the wrapper and click it to open the dropdown
        const triggerButton = dropdownTriggerWrapperRef.current.querySelector(
          'button[type="button"]',
        ) as HTMLButtonElement;
        if (triggerButton) {
          triggerButton.click();
        }
      }
    }, [showMentionDropdown]);

    // Sync internal value with external value prop
    useEffect(() => {
      if (value !== undefined && value !== internalValue) {
        setInternalValue(value);
      }
    }, [value]);

    const displayValue = value !== undefined ? value : internalValue;

    return (
      <div className="space-y-2 relative">
        {label && (
          <Label htmlFor={textareaId} required={required}>
            {label}
          </Label>
        )}
        <div className="relative">
          <textarea
            ref={combinedRef}
            id={textareaId}
            value={displayValue}
            onChange={handleChange}
            onKeyDown={handleKeyDown}
            className={clsx(
              "block w-full px-3 py-2 border rounded-md text-sm font-normal leading-normal",
              "focus:outline-none focus:ring-1 focus:ring-mw-primary-500 focus:border-transparent",
              "hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-700",
              "disabled:bg-mw-neutral-50 disabled:cursor-not-allowed",
              "resize-y",
              showCharCount && "pb-6",
              error
                ? "border-mw-error-300 text-mw-error-500 placeholder-mw-error-300 focus:ring-mw-error-500"
                : "border-mw-neutral-100 dark:border-mw-neutral-600 text-mw-neutral-500 placeholder-mw-neutral-500",
              "dark:bg-mw-neutral-800",
              className,
            )}
            {...props}
          />
          {/* Character counter in bottom right corner */}
          {showCharCount && maxLength && (
            <div className="absolute bottom-2 right-3 text-xs text-mw-neutral-500 pointer-events-none">
              {currentLength !== undefined
                ? currentLength
                : internalValue.toString().length}
              /{maxLength}
            </div>
          )}
          {/* Mention Dropdown */}
          {enableMentions && filteredMentions.length > 0 && (
            <div ref={dropdownTriggerWrapperRef} className="absolute">
              <Dropdown
                key={showMentionDropdown ? "open" : "closed"}
                name="mention-dropdown"
              >
                <DropdownTrigger className="opacity-0 pointer-events-none w-1 h-1">
                  <span />
                </DropdownTrigger>
                {showMentionDropdown && (
                  <DropdownContent
                    align="left"
                    className="w-full max-h-[300px] overflow-y-auto"
                  >
                    {filteredMentions.map((mention, index) => (
                      <DropdownItem
                        key={mention.id}
                        value={mention.id}
                        onClick={() => handleMentionSelect(mention)}
                        className={`${
                          index === selectedMentionIndex
                            ? "bg-mw-primary-50 dark:bg-mw-primary-900/20 text-mw-primary-600 dark:text-mw-primary-400"
                            : ""
                        }`}
                      >
                        {mention.label}
                      </DropdownItem>
                    ))}
                  </DropdownContent>
                )}
              </Dropdown>
            </div>
          )}
        </div>
        {error && (
          <p
            id={`${textareaId}-error`}
            className="text-sm text-mw-error-500 dark:text-mw-error-400"
          >
            {error}
          </p>
        )}
        {helpText && !error && (
          <p
            id={`${textareaId}-help`}
            className="text-sm text-mw-neutral-500 dark:text-mw-neutral-400"
          >
            {helpText}
          </p>
        )}
      </div>
    );
  },
);

Textarea.displayName = "Textarea";
