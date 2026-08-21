import InventoryThumbnail from "@components/common/InventoryThumbnail";
import { Badge } from "@components/ui/Badge";
import { formatInventoryLocation } from "@utils/inventory-display.utils";
import React from "react";

import type { InventoryItem } from "../../../types/inventory.types";

/**
 * Inventory map marker popup — thumbnail, name, location, and type/format/
 * environment/size badges. Shared by the inventory manual-edit map
 * (`InventoryMapPanel`) and the media-plan Audience Map so both look identical.
 */
const InventoryMapPopup: React.FC<{ item: InventoryItem }> = ({ item }) => {
  return (
    <div className="flex-1 min-w-0 space-y-1">
      <InventoryThumbnail
        src={item.detail.thumbnail}
        alt={item.detail.mediaOwnerName}
        className="w-full h-32 rounded-md object-cover"
      />
      <div className="inline-flex justify-start items-center">
        <h3 className="text-xs font-semibold leading-4 truncate max-w-[250px]">
          {item.detail.name || ""}
        </h3>
      </div>
      <p className="text-xs text-secondary leading-4">
        {formatInventoryLocation(item.location.location)}
      </p>
      <div className="flex flex-wrap gap-1.5">
        <Badge variant="outline" size="sm">
          {item.detail.inventoryType}
        </Badge>
        {item.detail.format && (
          <Badge
            className="outline-mw-rose-warning-400 text-mw-rose-warning-400"
            variant="outline"
            size="sm"
          >
            {item.detail.format}
          </Badge>
        )}
        {item.detail.environment && (
          <Badge
            className="outline-mw-neutral-400 text-mw-neutral-400"
            variant="outline"
            size="sm"
          >
            {item.detail.environment}
          </Badge>
        )}
        {item.detail.size && (
          <Badge
            className="outline-mw-orange-warning-500 text-mw-orange-warning-500"
            variant="outline"
            size="sm"
          >
            {item.detail.size.toUpperCase()}
          </Badge>
        )}
      </div>
    </div>
  );
};

export default InventoryMapPopup;
