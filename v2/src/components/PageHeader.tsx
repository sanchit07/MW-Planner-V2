import { T } from "@tolgee/react";
import React from "react";

interface PageHeaderProps {
  title: string;
  description?: React.ReactNode;
  titleKey?: string;
  descriptionKey?: string;
  actions?: React.ReactNode;
  leftAction?: React.ReactNode;
  className?: string;
}

export const PageHeader: React.FC<PageHeaderProps> = ({
  title,
  description,
  titleKey,
  descriptionKey,
  leftAction,
  actions,
}) => {
  const headerId = `page-header-${title.toLowerCase().replace(/\s+/g, "-")}`;
  return (
    <div
      id={headerId}
      className="bg-white flex flex-col justify-start items-start gap-4 outline outline-offset-[-0.50px] outline-neutral-200 pb-3"
    >
      <div className="self-stretch px-4 inline-flex justify-start items-start">
        <div className="pt-2 flex justify-start items-start gap-2">
          {leftAction && (
            <div
              id={`${headerId}-left-action`}
              className="p-2 flex justify-center items-center gap-1"
            >
              <div className="relative overflow-hidden">{leftAction}</div>
            </div>
          )}
          <div className="self-stretch inline-flex flex-col justify-start items-start gap-1">
            <div
              id={`${headerId}-title`}
              className="self-stretch justify-start text-xl font-semibold leading-loose"
            >
              {titleKey ? <T keyName={titleKey}>{title}</T> : title}
            </div>
            <div
              id={`${headerId}-description`}
              className="self-stretch justify-start text-neutral-500 text-sm font-normal leading-none"
            >
              {descriptionKey && (
                <T keyName={descriptionKey}>{descriptionKey}</T>
              )}
              {description && description}
            </div>
          </div>
        </div>
        <div
          id={`${headerId}-actions`}
          className="flex-1 self-stretch flex justify-end items-end gap-2"
        >
          {actions && <div className="flex items-center gap-4">{actions}</div>}
        </div>
      </div>
    </div>
  );
};

export default PageHeader;
