import {
  Dropdown,
  DropdownContent,
  DropdownItem,
  DropdownScrollableContent,
  DropdownTrigger,
} from "@components/ui/Dropdown";
import { MapPin } from "lucide-react";

export interface MapJumpOption {
  id: string;
  label: string;
  lng: number;
  lat: number;
}

interface MapJumpToDropdownProps {
  label: string;
  options: MapJumpOption[];
  emptyText: string;
  onSelect: (coords: { lng: number; lat: number }) => void;
  className?: string;
}

/**
 * Read-only "jump-to" dropdown for the manual-edit map. Lists geofencing
 * locations / POIs (view only — no edit/delete); picking one flies the map to
 * its coordinates.
 */
const MapJumpToDropdown = ({
  label,
  options,
  emptyText,
  onSelect,
  className = "w-64",
}: MapJumpToDropdownProps) => (
  <Dropdown className={className}>
    <DropdownTrigger className="bg-white shadow-sm">
      <span className="flex items-center gap-2 truncate">
        <MapPin className="w-4 h-4 shrink-0 text-mw-primary-500" />
        <span className="truncate">
          {label}
          {options.length > 0 ? ` (${options.length})` : ""}
        </span>
      </span>
    </DropdownTrigger>
    <DropdownContent align="left" maxWidth="20rem">
      <DropdownScrollableContent maxHeight="240px">
        {options.length === 0 ? (
          <div className="px-2 py-2 text-sm text-mw-neutral-400">
            {emptyText}
          </div>
        ) : (
          options.map((o) => (
            <DropdownItem
              key={o.id}
              onClick={() => onSelect({ lng: o.lng, lat: o.lat })}
            >
              <span className="truncate">{o.label}</span>
            </DropdownItem>
          ))
        )}
      </DropdownScrollableContent>
    </DropdownContent>
  </Dropdown>
);

export default MapJumpToDropdown;
