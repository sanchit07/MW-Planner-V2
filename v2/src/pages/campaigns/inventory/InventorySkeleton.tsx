import { Card, CardFooter, CardHeader } from "@components/ui/card";
import { Skeleton } from "@components/ui/Skeleton";

interface InventorySkeletonProps {
  count?: number;
}

const InventorySkeletonCard = () => {
  return (
    <Card className="hover:bg-mw-primary-50">
      <CardHeader className="p-2">
        <div className="self-stretch inline-flex justify-between items-start border-b border-mw-neutral-100 pb-2">
          <div className="flex justify-start items-start gap-2">
            {/* Checkbox skeleton */}
            <div className="flex items-center">
              <Skeleton className="h-5 w-5 rounded" />
            </div>

            {/* Image skeleton */}
            <Skeleton className="size-14 rounded-sm" />

            {/* Content skeleton */}
            <div className="flex flex-col justify-start items-start gap-2">
              <div className="flex justify-start items-center gap-2">
                <Skeleton className="h-4 w-40" />
                <Skeleton className="size-4 rounded-full" />
              </div>
              <Skeleton className="h-3 w-64" />
              <div className="flex justify-start items-start gap-1">
                <Skeleton className="h-6 w-16 rounded-full" />
                <Skeleton className="h-6 w-20 rounded-full" />
                <Skeleton className="h-6 w-24 rounded-full" />
              </div>
            </div>
          </div>

          {/* Action Button skeleton */}
          <div className="flex items-center gap-2 justify-end">
            <Skeleton className="h-8 w-8 rounded-md" />
          </div>
        </div>
      </CardHeader>

      <CardFooter>
        <div className="flex-1 flex justify-center items-center gap-2.5">
          <Skeleton className="h-4 w-48" />
        </div>
        <div className="flex-1 flex justify-end items-center gap-1">
          <Skeleton className="h-9 w-28 rounded-sm" />
        </div>
      </CardFooter>
    </Card>
  );
};

export const InventorySkeleton = ({ count = 5 }: InventorySkeletonProps) => {
  return (
    <>
      {Array.from({ length: count }).map((_, index) => (
        <InventorySkeletonCard key={index} />
      ))}
    </>
  );
};

export default InventorySkeleton;
