import { InventoryClassification } from "@constants/inventory.constants";

import type {
  dayString,
  InventoryDetail,
  InventoryDetailsResponse,
  InventoryItem,
  InventoryLocationData,
  InventoryOperations,
  InventoryPerformance,
  InventorySchedule,
} from "../types/inventory.types";

/**
 * Parses the first WKT POINT string from a geoms array into lat/long.
 * Returns { lat: 0, long: 0 } when parsing fails or array is empty.
 */
export function parseFirstGeomToLatLong(geoms: string[] | undefined): {
  lat: number;
  long: number;
} {
  const out = { lat: 0, long: 0 };
  if (!geoms?.length) return out;
  const geom = geoms[0];
  const part = geom?.split("POINT(")[1];
  if (!part) return out;
  const coordsStr = part.split(")")[0]?.trim();
  if (!coordsStr) return out;
  const parts = coordsStr.split(/\s+/);
  if (parts.length < 2) return out;
  const long = Number(parts[0]);
  const lat = Number(parts[1]);
  if (Number.isNaN(lat) || Number.isNaN(long)) return out;
  out.lat = lat;
  out.long = long;
  return out;
}

/**
 * Maps API InventoryDetailsResponse to the app's InventoryItem shape for display.
 */
export function mapInventoryDetailsResponseToInventoryItem(
  res: InventoryDetailsResponse | undefined,
): InventoryItem | null {
  if (!res) return null;

  const coords = parseFirstGeomToLatLong(res.geoms);
  const coordList =
    coords.lat !== 0 || coords.long !== 0
      ? [{ latitude: coords.lat, longitude: coords.long }]
      : [];

  const detail: InventoryDetail = {
    id: res.id,
    name: res.name,
    externalId: res.id,
    referenceId: res.referenceId,
    mediaOwnerId: res.mediaOwnerId,
    mediaOwnerName: res.mediaOwnerName,
    inventoryType: res.cinemaFields
      ? InventoryClassification.CINEMA
      : res.typeName.includes("Digital")
        ? InventoryClassification.DIGITAL
        : InventoryClassification.CLASSIC,
    category: res.venues?.[0]?.name ?? "",
    venueType: res.typePath
      ? [res.typePath]
      : res.displayFormatName
        ? [res.displayFormatName]
        : [],
    thumbnail: res.thumbnailUrlOriginal ?? "",
    images: res.medias?.map((m) => m.url) ?? [],
    format: res.displayFormatName ?? "",
    environment: res.environment ?? "",
    size: res.size ?? "",
    operationMode: "",
    execution: "",
    screens: res.digitalFields?.playerCount ?? 0,
    sov: 0,
    isSelected: false,
    isCompliant: true,
    bookingMode: res.digitalFields?.bookingMode ?? "",
    panels: (res.panels ?? []).map((p) => ({
      pixelWidth: p.pixelWidth,
      pixelHeight: p.pixelHeight,
      physicalWidth: p.physicalWidth,
      physicalHeight: p.physicalHeight,
      panelCount: p.screenCount ?? 1,
      unit: "cm",
      size: "",
    })),
    cinemaFields: res.cinemaFields,
  };

  const location: InventoryLocationData = {
    location: {
      address: res.address ?? "",
      country: res.adminLevel0Name ?? "",
      state: res.adminLevel1Name ?? "",
      city: res.adminLevel2Name ?? "",
      zipCode: res.postalCode ?? "",
      locationCoordinates: {
        type: "Point",
        coordinates: coordList,
      },
    },
    poi: { types: [], nearbyPOIs: [], categories: [] },
    demographics: {
      age: "",
      gender: "",
      overall: "",
      ageGender: "",
      income: "",
      behaviour: "",
      interest: "",
      highestIndexScore: "",
    },
  };

  const performance: InventoryPerformance = {
    cpmRate: res.prices?.[0]?.cpm ?? 0,
    estimatedCost: 0,
    perDayCost: 0,
    perDayAdPlays: 0,
    totalAdPlays: 0,
    plannedSot: 0,
    totalSot: 0,
  };

  const schedule = res.schedule;
  const operationDays = (
    schedule?.operatingTimes ? Object.keys(schedule.operatingTimes) : []
  ) as dayString[];
  const operations: InventoryOperations = {
    startTime:
      schedule?.operatingTimes != null && operationDays.length > 0
        ? schedule.operatingTimes[operationDays[0]]?.[0]?.start
        : undefined,
    endTime:
      schedule?.operatingTimes != null && operationDays.length > 0
        ? schedule.operatingTimes[operationDays[0]]?.[0]?.end
        : undefined,
    operationDays,
    operatingTimes:
      schedule?.operatingTimes as InventoryOperations["operatingTimes"],
    maintenanceWindow: "",
    loopSize: res.digitalFields?.loopDuration ?? 0,
    slotDuration: res.digitalFields?.spotDuration ?? 0,
    clientPerLoop: res.digitalFields?.spotsPerLoop ?? 0,
    cycleTime: res.digitalFields?.loopDuration ?? 0,
  };

  const schedules: InventorySchedule[] = [];

  return {
    detail,
    location,
    performance,
    operations,
    scheduleDates: undefined,
    scheduleHours: undefined,
    schedules,
  };
}
