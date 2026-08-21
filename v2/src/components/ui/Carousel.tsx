import { useTranslate } from "@tolgee/react";
import { cn } from "@utils/tailwindMerge";
import { ChevronLeft, ChevronRight, Play, Pause } from "lucide-react";
import React, { useState, useEffect, useRef, useCallback } from "react";

interface CarouselProps {
  children: React.ReactNode[];
  autoplay?: boolean;
  autoplayInterval?: number;
  showDots?: boolean;
  showArrows?: boolean;
  showPlayButton?: boolean;
  infinite?: boolean;
  slidesToShow?: number;
  slidesToScroll?: number;
  className?: string;
  aspectRatio?: "square" | "video" | "wide" | "auto";
  gap?: number;
  onSlideChange?: (index: number) => void;
  showTotalCount?: boolean;
}

interface CarouselSlideProps {
  children: React.ReactNode;
  className?: string;
}

interface CarouselControlsProps {
  currentSlide: number;
  totalSlides: number;
  onPrevious: () => void;
  onNext: () => void;
  onSlideSelect: (index: number) => void;
  showDots?: boolean;
  showArrows?: boolean;
  showPlayButton?: boolean;
  isAutoplay?: boolean;
  onToggleAutoplay?: () => void;
}

const aspectRatios = {
  square: "aspect-square",
  video: "aspect-video",
  wide: "aspect-[21/9]",
  auto: "",
};

export function CarouselSlide({ children, className }: CarouselSlideProps) {
  return (
    <div className={cn("flex-shrink-0 w-full h-full", className)}>
      {children}
    </div>
  );
}

function CarouselControls({
  currentSlide,
  totalSlides,
  onPrevious,
  onNext,
  onSlideSelect,
  showDots = true,
  showArrows = true,
  showPlayButton = false,
  isAutoplay = false,
  onToggleAutoplay,
}: CarouselControlsProps) {
  const { t } = useTranslate(["common"]);
  return (
    <>
      {/* Navigation arrows */}
      {showArrows && (
        <>
          <button
            onClick={onPrevious}
            className="absolute left-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-white/80 dark:bg-mw-neutral-800/80 text-mw-neutral-700 dark:text-mw-neutral-300 hover:bg-white dark:hover:bg-mw-neutral-800 shadow-lg transition-all duration-200 backdrop-blur-sm"
            aria-label={t("aria.previousSlide")}
          >
            <ChevronLeft className="w-5 h-5" />
          </button>

          <button
            onClick={onNext}
            className="absolute right-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-white/80 dark:bg-mw-neutral-800/80 text-mw-neutral-700 dark:text-mw-neutral-300 hover:bg-white dark:hover:bg-mw-neutral-800 shadow-lg transition-all duration-200 backdrop-blur-sm"
            aria-label={t("aria.nextSlide")}
          >
            <ChevronRight className="w-5 h-5" />
          </button>
        </>
      )}

      {/* Dots indicator */}
      {showDots && totalSlides > 1 && (
        <div className="absolute bottom-4 left-1/2 -translate-x-1/2 z-10 flex items-center space-x-2">
          {Array.from({ length: totalSlides }, (_, index) => (
            <button
              key={index}
              onClick={() => onSlideSelect(index)}
              className={cn(
                "w-2 h-2 rounded-full transition-all duration-200",
                index === currentSlide
                  ? "bg-white dark:bg-mw-neutral-200 w-6"
                  : "bg-white/50 dark:bg-mw-neutral-200/50 hover:bg-white/75 dark:hover:bg-mw-neutral-200/75",
              )}
              aria-label={t("aria.goToSlide", { n: index + 1 })}
            />
          ))}

          {/* Autoplay toggle */}
          {showPlayButton && onToggleAutoplay && (
            <button
              onClick={onToggleAutoplay}
              className="ml-4 p-1 rounded-full bg-white/80 dark:bg-mw-neutral-800/80 text-mw-neutral-700 dark:text-mw-neutral-300 hover:bg-white dark:hover:bg-mw-neutral-800 backdrop-blur-sm"
              aria-label={
                isAutoplay ? t("aria.pauseAutoplay") : t("aria.startAutoplay")
              }
            >
              {isAutoplay ? (
                <Pause className="w-3 h-3" />
              ) : (
                <Play className="w-3 h-3" />
              )}
            </button>
          )}
        </div>
      )}
    </>
  );
}

export function Carousel({
  children,
  autoplay = false,
  autoplayInterval = 3000,
  showDots = true,
  showArrows = true,
  showPlayButton = false,
  infinite = true,
  slidesToShow = 1,
  slidesToScroll = 1,
  className,
  aspectRatio = "auto",
  gap = 0,
  onSlideChange,
  showTotalCount = true,
}: CarouselProps) {
  const [currentSlide, setCurrentSlide] = useState(0);
  const [isAutoplay, setIsAutoplay] = useState(autoplay);
  const [isPaused, setIsPaused] = useState(false);
  const carouselRef = useRef<HTMLDivElement>(null);
  const autoplayRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const slides = React.Children.toArray(children);
  const totalSlides = Math.ceil(slides.length / slidesToShow);
  const slideWidth = 100 / slidesToShow;

  const goToSlide = useCallback(
    (index: number) => {
      let newIndex = index;

      if (infinite) {
        if (index < 0) {
          newIndex = totalSlides - 1;
        } else if (index >= totalSlides) {
          newIndex = 0;
        }
      } else {
        newIndex = Math.max(0, Math.min(index, totalSlides - 1));
      }

      setCurrentSlide(newIndex);
      onSlideChange?.(newIndex);
    },
    [totalSlides, infinite, onSlideChange],
  );

  const goToPrevious = useCallback(() => {
    goToSlide(currentSlide - slidesToScroll);
  }, [currentSlide, slidesToScroll, goToSlide]);

  const goToNext = useCallback(() => {
    goToSlide(currentSlide + slidesToScroll);
  }, [currentSlide, slidesToScroll, goToSlide]);

  const toggleAutoplay = useCallback(() => {
    setIsAutoplay(!isAutoplay);
  }, [isAutoplay]);

  // Autoplay functionality
  useEffect(() => {
    if (isAutoplay && !isPaused && totalSlides > 1) {
      autoplayRef.current = setInterval(() => {
        goToNext();
      }, autoplayInterval);
    }

    return () => {
      if (autoplayRef.current) {
        clearInterval(autoplayRef.current);
      }
    };
  }, [isAutoplay, isPaused, goToNext, autoplayInterval, totalSlides]);

  // Keyboard navigation
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "ArrowLeft") {
        goToPrevious();
      } else if (event.key === "ArrowRight") {
        goToNext();
      } else if (event.key === " ") {
        event.preventDefault();
        toggleAutoplay();
      }
    };

    const carousel = carouselRef.current;
    if (carousel) {
      carousel.addEventListener("keydown", handleKeyDown);
      return () => carousel.removeEventListener("keydown", handleKeyDown);
    }
  }, [goToPrevious, goToNext, toggleAutoplay]);

  // Touch/swipe support
  const [touchStart, setTouchStart] = useState<number | null>(null);
  const [touchEnd, setTouchEnd] = useState<number | null>(null);

  const handleTouchStart = (e: React.TouchEvent) => {
    setTouchEnd(null);
    setTouchStart(e.targetTouches[0].clientX);
  };

  const handleTouchMove = (e: React.TouchEvent) => {
    setTouchEnd(e.targetTouches[0].clientX);
  };

  const handleTouchEnd = () => {
    if (!touchStart || !touchEnd) return;

    const distance = touchStart - touchEnd;
    const isLeftSwipe = distance > 50;
    const isRightSwipe = distance < -50;

    if (isLeftSwipe) {
      goToNext();
    } else if (isRightSwipe) {
      goToPrevious();
    }
  };

  const translateX = -(currentSlide * (100 / slidesToShow));

  return (
    <div
      ref={carouselRef}
      className={cn(
        "relative overflow-hidden rounded-lg focus:outline-none",
        aspectRatios[aspectRatio],
        className,
      )}
      tabIndex={0}
      onMouseEnter={() => setIsPaused(true)}
      onMouseLeave={() => setIsPaused(false)}
      onTouchStart={handleTouchStart}
      onTouchMove={handleTouchMove}
      onTouchEnd={handleTouchEnd}
    >
      {/* Slides container */}
      <div
        className="flex transition-transform duration-300 ease-out h-full"
        style={{
          transform: `translateX(${translateX}%)`,
          gap: gap ? `${gap}px` : undefined,
        }}
      >
        {slides.map((slide, index) => (
          <div
            key={index}
            className="flex-shrink-0 h-full"
            style={{ width: `${slideWidth}%` }}
          >
            {slide}
          </div>
        ))}
      </div>

      {/* Controls */}
      <CarouselControls
        currentSlide={currentSlide}
        totalSlides={totalSlides}
        onPrevious={goToPrevious}
        onNext={goToNext}
        onSlideSelect={goToSlide}
        showDots={showDots}
        showArrows={showArrows}
        showPlayButton={showPlayButton}
        isAutoplay={isAutoplay}
        onToggleAutoplay={toggleAutoplay}
      />

      {/* Slide counter */}
      {showTotalCount && (
        <div className="absolute top-4 right-4 px-2 py-1 bg-black/50 text-white text-xs rounded backdrop-blur-sm">
          {currentSlide + 1} / {totalSlides}
        </div>
      )}
    </div>
  );
}

// Specialized carousel for images
interface ImageCarouselProps extends Omit<CarouselProps, "children"> {
  images: Array<{
    src: string;
    alt: string;
    caption?: string;
  }>;
  objectFit?: "cover" | "contain";
}

export function ImageCarousel({
  images,
  objectFit = "cover",
  ...carouselProps
}: ImageCarouselProps) {
  return (
    <Carousel {...carouselProps}>
      {images.map((image, index) => (
        <CarouselSlide key={index}>
          <div className="relative w-full h-full bg-mw-neutral-100 dark:bg-mw-neutral-800">
            <img
              src={image.src}
              alt={image.alt}
              className={cn(
                "w-full h-full",
                objectFit === "cover" ? "object-cover" : "object-contain",
              )}
            />
            {image.caption && (
              <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/60 to-transparent p-4">
                <p className="text-white text-sm font-medium">
                  {image.caption}
                </p>
              </div>
            )}
          </div>
        </CarouselSlide>
      ))}
    </Carousel>
  );
}
