// API Response Types
export interface BookingSlot {
  bookingId: string;
  bookingType: string;
  createdAt: string;
  createdBy: string;
  creativeDuration: number;
  endTime: string;
  expiresAt: string;
  id: string;
  inventoryId: string;
  loopSecondsAllocated: number;
  secondsAllocated: number;
  slotPositions: number[];
  startTime: string;
  status: string;
  timeZone: string;
}

export interface Booking {
  agency: string;
  bookingType: string;
  brand: string;
  createdAt: string;
  createdBy: string;
  dealId: string;
  dealName: string;
  expirationExtendedAt: string;
  expirationExtendedBy: string;
  expiresAt: string;
  id: string;
  metadata: Record<string, unknown>;
  slots: BookingSlot[];
  sspId: number;
  status: string;
  updatedAt: string;
  updatedBy: string;
}

export interface Blackout {
  createdAt: string;
  createdBy: string;
  endDate: string;
  id: string;
  reason: string;
  startDate: string;
}

export interface OperatingTime {
  start: string;
  end: string;
}

export interface Schedule {
  createdAt: string;
  createdBy: string;
  notes?: string;
  operatingTimes: Record<string, OperatingTime[]>;
  updatedAt: string;
  updatedBy: string;
}

export interface InventoryAvailabilityData {
  allocatedLoopSeconds: number;
  availableLoopSeconds: number;
  blackouts?: Blackout[];
  bookingMode: "loop" | "time";
  bookings?: Booking[];
  id: string;
  loopDuration: number;
  name: string;
  schedule: Schedule;
  timeZone: string;
}

/** IMS sync metadata attached to availability responses. */
export interface AvailabilitySyncInfo {
  source?: string;
  lastSyncedAt: string | null;
  status: "SUCCESS" | "FAILED" | "RUNNING" | string;
  error?: string | null;
}

export interface InventoryAvailabilityResponse {
  inventories: Record<string, InventoryAvailabilityData>;
  sync?: AvailabilitySyncInfo;
}
