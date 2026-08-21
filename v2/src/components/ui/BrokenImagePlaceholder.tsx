import { cn } from "@utils/tailwindMerge";
import React, { useId } from "react";

interface BrokenImagePlaceholderProps {
  /** Applied to the tile so it keeps the surrounding image box size/shape. */
  className?: string;
}

/**
 * Placeholder shown when an image is missing or fails to load: a light-blue
 * tile with a bordered rounded box and a centered broken-image glyph. Fills
 * whatever box (`className`) it is given.
 */
const BrokenImagePlaceholder: React.FC<BrokenImagePlaceholderProps> = ({
  className,
}) => {
  // Unique per instance — many placeholders can render at once, so the mask/clip
  // ids in the SVG must not collide across them.
  const uid = useId();
  const maskId = `bi-mask-${uid}`;
  const clipId = `bi-clip-${uid}`;
  return (
    <div
      data-testid="inventory-thumbnail-fallback"
      className={cn(
        className,
        "flex items-center justify-center overflow-hidden",
      )}
    >
      <svg
        className="w-full h-full"
        preserveAspectRatio="xMidYMid meet"
        viewBox="0 0 24 24"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        aria-hidden="true"
      >
        <mask id={maskId} fill="white">
          <path d="M0 4C0 1.79086 1.79086 0 4 0H20C22.2091 0 24 1.79086 24 4V20C24 22.2091 22.2091 24 20 24H4C1.79086 24 0 22.2091 0 20V4Z" />
        </mask>
        <path
          d="M0 4C0 1.79086 1.79086 0 4 0H20C22.2091 0 24 1.79086 24 4V20C24 22.2091 22.2091 24 20 24H4C1.79086 24 0 22.2091 0 20V4Z"
          fill="#E6F3FA"
        />
        <path
          d="M4 0V1H20V0V-1H4V0ZM24 4H23V20H24H25V4H24ZM20 24V23H4V24V25H20V24ZM0 20H1V4H0H-1V20H0ZM4 24V23C2.34315 23 1 21.6569 1 20H0H-1C-1 22.7614 1.23858 25 4 25V24ZM24 20H23C23 21.6569 21.6569 23 20 23V24V25C22.7614 25 25 22.7614 25 20H24ZM20 0V1C21.6569 1 23 2.34315 23 4H24H25C25 1.23858 22.7614 -1 20 -1V0ZM4 0V-1C1.23858 -1 -1 1.23858 -1 4H0H1C1 2.34315 2.34315 1 4 1V0Z"
          fill="#D3D3D3"
          mask={`url(#${maskId})`}
        />
        <g clipPath={`url(#${clipId})`}>
          <path
            d="M7 7L17 17"
            stroke="#A0A0A0"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <path
            d="M11.205 11.205C11.1121 11.2979 11.0018 11.3716 10.8804 11.4219C10.759 11.4722 10.6289 11.4981 10.4975 11.4981C10.3661 11.4981 10.236 11.4722 10.1146 11.4219C9.99321 11.3716 9.88291 11.2979 9.79 11.205C9.69709 11.1121 9.62339 11.0018 9.57311 10.8804C9.52282 10.759 9.49694 10.6289 9.49694 10.4975C9.49694 10.3661 9.52282 10.236 9.57311 10.1146C9.62339 9.99321 9.69709 9.88291 9.79 9.79"
            stroke="#A0A0A0"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <path
            d="M12.75 12.75L9 16.5"
            stroke="#A0A0A0"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <path
            d="M15 12L16.5 13.5"
            stroke="#A0A0A0"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <path
            d="M7.795 7.795C7.70179 7.88727 7.62774 7.99706 7.57712 8.11805C7.52649 8.23904 7.50028 8.36884 7.5 8.5V15.5C7.5 15.7652 7.60536 16.0196 7.79289 16.2071C7.98043 16.3946 8.23478 16.5 8.5 16.5H15.5C15.775 16.5 16.026 16.39 16.205 16.205"
            stroke="#A0A0A0"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <path
            d="M16.5 13.5V8.5C16.5 8.23478 16.3946 7.98043 16.2071 7.79289C16.0196 7.60536 15.7652 7.5 15.5 7.5H10.5"
            stroke="#A0A0A0"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </g>
        <defs>
          <clipPath id={clipId}>
            <rect
              width="12"
              height="12"
              fill="white"
              transform="translate(6 6)"
            />
          </clipPath>
        </defs>
      </svg>
    </div>
  );
};

export default BrokenImagePlaceholder;
