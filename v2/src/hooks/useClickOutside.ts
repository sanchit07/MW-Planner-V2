import { useEffect, useRef } from "react";

/**
 * Listens for mousedown outside the given ref and for Escape key.
 * Only active when isActive is true. Uses a ref for onClose to avoid
 * re-attaching when callback identity changes.
 */
export function useClickOutside(
  containerRef: React.RefObject<HTMLElement | null>,
  isActive: boolean,
  onClose: () => void,
  options?: { listenEscape?: boolean },
): void {
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;
  const listenEscape = options?.listenEscape !== false;

  useEffect(() => {
    if (!isActive) return;

    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as Node;
      if (containerRef.current && !containerRef.current.contains(target)) {
        onCloseRef.current();
      }
    };

    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") onCloseRef.current();
    };

    document.addEventListener("mousedown", handleClickOutside);
    if (listenEscape) {
      document.addEventListener("keydown", handleEscape);
    }

    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      if (listenEscape) {
        document.removeEventListener("keydown", handleEscape);
      }
    };
  }, [isActive, containerRef, listenEscape]);
}
