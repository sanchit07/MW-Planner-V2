import BrokenImagePlaceholder from "@components/ui/BrokenImagePlaceholder";
import ImageWithFallback from "@components/ui/ImageFallback";
import React from "react";

interface InventoryThumbnailProps {
  /** Image URL; when empty or broken the placeholder is shown instead. */
  src?: string | null;
  alt: string;
  /** Applied to both the image and the fallback so the box size is preserved. */
  className?: string;
}

const InventoryThumbnail: React.FC<InventoryThumbnailProps> = ({
  src,
  alt,
  className,
}) => (
  <ImageWithFallback
    src={src ?? ""}
    alt={alt}
    className={className}
    fallbackElement={<BrokenImagePlaceholder className={className} />}
  />
);

export default InventoryThumbnail;
