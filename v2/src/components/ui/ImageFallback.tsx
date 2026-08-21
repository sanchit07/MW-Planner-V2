import React, { useEffect, useState } from "react";

interface ImageFallbackProps {
  src: string;
  alt: string;
  fallbackSrc?: string;
  fallbackElement?: React.ReactNode;
  className?: string;
}

const ImageWithFallback: React.FC<ImageFallbackProps> = ({
  src,
  alt,
  fallbackSrc,
  fallbackElement,
  className,
}) => {
  const [useFallback, setUseFallback] = useState(!src);

  // Sync when src prop changes (e.g. user selects a different brand)
  useEffect(() => {
    setUseFallback(!src);
  }, [src]);

  const handleError = () => setUseFallback(true);

  const handleLoad = (e: React.SyntheticEvent<HTMLImageElement>) => {
    const img = e.currentTarget;
    if (img.naturalWidth === 0 || img.naturalHeight === 0) {
      setUseFallback(true);
    }
  };

  if (useFallback) {
    if (fallbackElement) return <>{fallbackElement}</>;
    if (fallbackSrc)
      return <img src={fallbackSrc} alt={alt} className={className} />;
    return null;
  }

  return (
    <img
      src={src}
      alt={alt}
      onError={handleError}
      onLoad={handleLoad}
      className={className}
    />
  );
};

export default ImageWithFallback;
