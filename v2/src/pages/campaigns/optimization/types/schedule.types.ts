import {
  InventoryType,
  InventoryClassification,
  InventoryTypeUppercase,
} from "src/constants/inventory.constants";
import { InventoryItem, InventorySchedule } from "src/types/inventory.types";

export interface ScheduleDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  selectedInventoryId: string | null;
  selectedInventory: InventoryItem | null;
  inventorySchedules: InventorySchedule[]; // Used for overlap checking only
  scheduleId?: string; // ID of schedule to edit, undefined for new schedule
  campaignStartDate?: string;
  campaignEndDate?: string;
  campaignId: string;
  onScheduleSaved?: () => void; // Callback to refresh schedules after save
}

export interface ScheduleOptimizationComponentProps {
  campaignId: string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  campaignState: any;
  loadForeCastData: (forceRegenerate?: boolean) => void;
}

export const INVENTORY_TYPES = [
  {
    value: "All Inventories",
    label: "All Inventories",
  },
  {
    value: InventoryTypeUppercase.DIGITAL,
    label: InventoryClassification.DIGITAL,
  },
  {
    value: InventoryTypeUppercase.CLASSIC,
    label: InventoryClassification.CLASSIC,
  },
  {
    value: InventoryTypeUppercase.TRANSIT,
    label: InventoryType.TRANSIT,
  },
  {
    value: InventoryTypeUppercase.STREET_FURNITURE,
    label: InventoryType.STREET_FURNITURE,
  },
  {
    value: InventoryTypeUppercase.PLACE_BASED,
    label: InventoryType.PLACE_BASED,
  },
  {
    value: InventoryTypeUppercase.DIGITAL_NETWORK,
    label: InventoryType.DIGITAL_NETWORK,
  },
  {
    value: InventoryTypeUppercase.RETAIL,
    label: InventoryType.RETAIL,
  },
] as const;
