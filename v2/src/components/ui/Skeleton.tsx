import { cn } from "@utils/tailwindMerge";

interface SkeletonProps {
  className?: string;
}

export const Skeleton = ({ className }: SkeletonProps) => {
  return (
    <div
      className={cn("animate-pulse rounded-md bg-mw-neutral-100", className)}
    />
  );
};

export default Skeleton;
