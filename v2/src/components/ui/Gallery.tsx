import { useTranslate } from "@tolgee/react";
import { cn } from "@utils/tailwindMerge";
import { ChevronLeft, ChevronRight, Play } from "lucide-react";
import React, { useState, useRef, useEffect } from "react";

import BrokenImagePlaceholder from "./BrokenImagePlaceholder";
import { Button } from "./Button";
import ImageWithFallback from "./ImageFallback";

export type MediaType = "image" | "video";

export interface GalleryItem {
  id: string | number;
  src: string;
  type: MediaType;
  alt?: string;
  thumbnail?: string; // Optional custom thumbnail, defaults to src for images
  caption?: string;
  poster?: string; // Video poster image
  [key: string]: unknown; // Allow additional properties
}

export interface GalleryProps {
  items: GalleryItem[];
  initialIndex?: number;
  className?: string;
  mainImageClassName?: string;
  thumbnailClassName?: string;
  showThumbnails?: boolean;
  thumbnailSize?: "sm" | "md" | "lg";
  showNavigation?: boolean;
  showCounter?: boolean;
  autoPlay?: boolean;
  loop?: boolean;
  onItemChange?: (item: GalleryItem, index: number) => void;
  onThumbnailClick?: (item: GalleryItem, index: number) => void;
  renderCustomItem?: (item: GalleryItem, isActive: boolean) => React.ReactNode;
  renderCustomThumbnail?: (
    item: GalleryItem,
    isActive: boolean,
    index: number,
  ) => React.ReactNode;
}

const thumbnailSizes = {
  sm: "w-14 h-14",
  md: "w-20 h-20",
  lg: "w-24 h-24",
};

const videoExtensions = [
  ".mp4",
  ".webm",
  ".ogg",
  ".mov",
  ".avi",
  ".m4v",
  ".mkv",
];

const imageExtensions = [
  ".jpg",
  ".jpeg",
  ".png",
  ".gif",
  ".webp",
  ".svg",
  ".bmp",
  ".ico",
];

function detectMediaType(src: string, explicitType?: MediaType): MediaType {
  if (explicitType) return explicitType;

  const lowerSrc = src.toLowerCase();
  if (videoExtensions.some((ext) => lowerSrc.includes(ext))) {
    return "video";
  }
  if (imageExtensions.some((ext) => lowerSrc.includes(ext))) {
    return "image";
  }

  // Default to image if uncertain
  return "image";
}

function MediaRenderer({
  item,
  className,
}: {
  item: GalleryItem;
  className?: string;
}) {
  const mediaType = detectMediaType(item.src, item.type);

  if (mediaType === "video") {
    return (
      <div className={cn("relative w-full h-full", className)}>
        <video
          src={item.src}
          controls
          className="w-full h-full object-contain"
          poster={item.poster}
          preload="metadata"
        >
          Your browser does not support the video tag.
        </video>
        {!item.poster && (
          <div className="absolute inset-0 flex items-center justify-center bg-mw-neutral-900/50">
            <Play className="w-12 h-12 text-white" />
          </div>
        )}
      </div>
    );
  }

  return (
    <ImageWithFallback
      src={item.src}
      alt={item.alt || `Gallery image ${item.id}`}
      className={cn("w-full h-full object-contain", className)}
      fallbackElement={
        <BrokenImagePlaceholder className={cn("w-full h-full", className)} />
      }
    />
  );
}

function ThumbnailRenderer({
  item,
  isActive,
  size = "md",
  onClick,
  renderCustom,
}: {
  item: GalleryItem;
  isActive: boolean;
  size?: "sm" | "md" | "lg";
  onClick: () => void;
  renderCustom?: (
    item: GalleryItem,
    isActive: boolean,
    index: number,
  ) => React.ReactNode;
}) {
  if (renderCustom) {
    return (
      <div onClick={onClick} className="cursor-pointer">
        {renderCustom(item, isActive, 0)}
      </div>
    );
  }

  const mediaType = detectMediaType(item.src, item.type);
  const thumbnailSrc = item.thumbnail || item.src;

  return (
    <button
      onClick={onClick}
      className={cn(
        "relative overflow-hidden rounded-2xs outline transition-all duration-200 flex-shrink-0 cursor-pointer",
        isActive
          ? "outline-mw-primary-500 shadow-lg scale-105"
          : "outline-mw-neutral-100 hover:outline-mw-primary-300  opacity-75 hover:opacity-100",
        thumbnailSizes[size],
      )}
      aria-label={`View ${item.alt || `item ${item.id}`}`}
    >
      {mediaType === "video" ? (
        <>
          <img
            src={item.poster || thumbnailSrc}
            alt={item.alt || `Video thumbnail ${item.id}`}
            className="w-full h-full object-cover"
          />
          <div className="absolute inset-0 flex items-center justify-center bg-black/30">
            <Play className="w-4 h-4 text-white" />
          </div>
        </>
      ) : (
        <ImageWithFallback
          src={thumbnailSrc}
          alt={item.alt || `Thumbnail ${item.id}`}
          className="w-full h-full object-cover"
          fallbackElement={<BrokenImagePlaceholder className="w-full h-full" />}
        />
      )}
    </button>
  );
}

export function Gallery({
  items,
  initialIndex = 0,
  className,
  mainImageClassName,
  thumbnailClassName,
  showThumbnails = true,
  thumbnailSize = "md",
  showNavigation = true,
  showCounter = true,
  loop = true,
  onItemChange,
  onThumbnailClick,
  renderCustomItem,
  renderCustomThumbnail,
}: GalleryProps) {
  const { t } = useTranslate(["common"]);
  const [currentIndex, setCurrentIndex] = useState(
    Math.max(0, Math.min(initialIndex, items.length - 1)),
  );
  const thumbnailContainerRef = useRef<HTMLDivElement>(null);

  const currentItem = items[currentIndex];
  const hasItems = items.length > 0;

  useEffect(() => {
    if (currentIndex >= 0 && currentIndex < items.length) {
      onItemChange?.(items[currentIndex], currentIndex);
    }
  }, [currentIndex, items, onItemChange]);

  const goToIndex = (index: number) => {
    if (index < 0) {
      setCurrentIndex(loop ? items.length - 1 : 0);
    } else if (index >= items.length) {
      setCurrentIndex(loop ? 0 : items.length - 1);
    } else {
      setCurrentIndex(index);
    }
  };

  const goToPrevious = () => {
    goToIndex(currentIndex - 1);
  };

  const goToNext = () => {
    goToIndex(currentIndex + 1);
  };

  const handleThumbnailClick = (index: number) => {
    goToIndex(index);
    onThumbnailClick?.(items[index], index);
    scrollThumbnailIntoView(index);
  };

  const scrollThumbnailIntoView = (index: number) => {
    if (!thumbnailContainerRef.current) return;

    const container = thumbnailContainerRef.current;
    const thumbnailWidth = thumbnailSizes[thumbnailSize]
      ? {
          sm: 64,
          md: 80,
          lg: 96,
        }[thumbnailSize]
      : 80;

    const scrollPosition = index * (thumbnailWidth + 8); // 8px gap
    const containerWidth = container.clientWidth;
    const currentScroll = container.scrollLeft;

    // If thumbnail is before visible area
    if (scrollPosition < currentScroll) {
      container.scrollTo({
        left: scrollPosition - 16, // 16px padding
        behavior: "smooth",
      });
    }
    // If thumbnail is after visible area
    else if (scrollPosition + thumbnailWidth > currentScroll + containerWidth) {
      container.scrollTo({
        left: scrollPosition - containerWidth + thumbnailWidth + 16,
        behavior: "smooth",
      });
    }
  };

  useEffect(() => {
    if (hasItems) {
      scrollThumbnailIntoView(currentIndex);
    }
  }, [currentIndex, hasItems, items.length]);

  // Keyboard navigation
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "ArrowLeft") {
        event.preventDefault();
        goToPrevious();
      } else if (event.key === "ArrowRight") {
        event.preventDefault();
        goToNext();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [currentIndex, items.length, loop]);

  if (!hasItems) {
    return (
      <div
        className={cn(
          "flex items-center justify-center p-8 bg-mw-neutral-100 dark:bg-mw-neutral-800 rounded-lg",
          className,
        )}
      >
        <p className="text-mw-neutral-500 dark:text-mw-neutral-400">
          No items to display
        </p>
      </div>
    );
  }

  return (
    <div className={cn("flex flex-col gap-4", className)}>
      {/* Main Media Display */}
      <div className="relative w-full bg-mw-neutral-100 dark:bg-mw-neutral-900 rounded-lg overflow-hidden aspect-video">
        {showCounter && items.length > 1 && (
          <div className="absolute top-4 right-4 px-3 py-1 bg-black/60 text-white text-sm rounded-full backdrop-blur-sm z-10">
            {currentIndex + 1} / {items.length}
          </div>
        )}

        {/* Main Media Content */}
        <div className={cn("w-full h-full", mainImageClassName)}>
          {renderCustomItem ? (
            renderCustomItem(currentItem, true)
          ) : (
            <MediaRenderer item={currentItem} />
          )}
        </div>

        {/* Caption */}
        {currentItem.caption && (
          <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/70 to-transparent p-4 text-white z-10">
            <p className="text-sm font-medium">{currentItem.caption}</p>
          </div>
        )}
      </div>

      {/* Thumbnail Strip */}
      {showThumbnails && items.length > 1 && (
        <div className={cn("relative w-full", thumbnailClassName)}>
          {/* Left navigation button */}
          {showNavigation && (
            <Button
              onClick={goToPrevious}
              variant="ghost"
              size="iconMd"
              className="absolute left-0 top-1/2 -translate-y-1/2 z-10 p-2 transition-all duration-200"
              aria-label={t("aria.previousItem")}
              disabled={!loop && currentIndex === 0}
            >
              <ChevronLeft className="w-5 h-5" />
            </Button>
          )}

          {/* Thumbnail container */}
          <div
            ref={thumbnailContainerRef}
            className="flex gap-2 overflow-x-auto scrollbar-hide py-2 px-4 justify-center items-center"
          >
            {items.map((item, index) => (
              <ThumbnailRenderer
                key={item.id}
                item={item}
                isActive={index === currentIndex}
                size={thumbnailSize}
                onClick={() => handleThumbnailClick(index)}
                renderCustom={renderCustomThumbnail}
              />
            ))}
          </div>

          {/* Right navigation button */}
          {showNavigation && (
            <Button
              onClick={goToNext}
              className="absolute right-0 top-1/2 -translate-y-1/2 z-10 transition-all duration-200"
              size="sm"
              variant="ghost"
              aria-label={t("aria.nextItem")}
              disabled={!loop && currentIndex === items.length - 1}
            >
              <ChevronRight className="w-5 h-5" />
            </Button>
          )}
        </div>
      )}
    </div>
  );
}
