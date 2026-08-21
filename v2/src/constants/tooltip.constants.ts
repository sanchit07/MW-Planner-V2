export const TOOLTIP_CONTENT = {
  // ── Inventory metrics (card expanded section) ──────────────────────────────
  inventory: {
    impressions: "tooltips.performance.totalImpressions",
    reach: "tooltips.performance.estimatedReach",
    frequency: "tooltips.performance.frequency",
    cpmRate: "tooltips.performance.avgCpmCost",
    cpsRate: "tooltips.inventory.cpsRate",
    estimatedCost: "tooltips.inventory.estimatedCost",
    adPlays: "tooltips.performance.plannedAdPlays",
    screens: "tooltips.inventory.screens",
    poi: "tooltips.inventory.poi",
    owner: "tooltips.inventory.owner",
  },

  // ── Schedule tab (campaign view) ────────────────────────────────────────────
  schedule: {
    days: "tooltips.schedule.days",
    hours: "tooltips.schedule.hours",
    slotDuration: "tooltips.schedule.slotDuration",
    expectedImpressions: "tooltips.schedule.expectedImpressions",
    adPlays: "tooltips.performance.plannedAdPlays",
    sov: "tooltips.performance.sov",
    trafficLevel: "tooltips.schedule.trafficLevel",
  },

  // ── Performance metrics (campaign view) ────────────────────────────────────
  performance: {
    totalImpressions: "tooltips.performance.totalImpressions",
    estimatedReach: "tooltips.performance.estimatedReach",
    frequency: "tooltips.performance.frequency",
    plannedAdPlays: "tooltips.performance.plannedAdPlays",
    avgCpmCost: "tooltips.performance.avgCpmCost",
    eCpm: "tooltips.performance.eCpm",
    sov: "tooltips.performance.sov",
    sot: "tooltips.performance.sot",
  },

  // ── Plan Summary forecast tiles (Inventories step) ─────────────────────────
  planSummary: {
    inventories: "tooltips.planSummary.inventories",
    impressions: "tooltips.planSummary.impressions",
    reach: "tooltips.planSummary.reach",
    frequency: "tooltips.planSummary.frequency",
    adPlays: "tooltips.planSummary.adPlays",
    co2PerPlay: "tooltips.planSummary.co2PerPlay",
    co2PerPlayNoData: "tooltips.planSummary.co2PerPlayNoData",
    avgCpm: "tooltips.planSummary.avgCpm",
    eCpm: "tooltips.planSummary.eCpm",
    sov: "tooltips.planSummary.sov",
    sot: "tooltips.planSummary.sot",
    cost: "tooltips.planSummary.cost",
    remaining: "tooltips.planSummary.remaining",
  },

  // ── Inventory details drawer — Details tab ──────────────────────────────────
  inventoryDetails: {
    mediaOwner: "tooltips.inventoryDetails.mediaOwner",
    type: "tooltips.inventoryDetails.type",
    format: "tooltips.inventoryDetails.format",
    size: "tooltips.inventoryDetails.size",
    screens: "tooltips.inventoryDetails.screens",
    operationMode: "tooltips.inventoryDetails.operationMode",
    execution: "tooltips.inventoryDetails.execution",
  },

  // ── Inventory details drawer — Location & Map tab ──────────────────────────
  inventoryLocation: {
    address: "tooltips.inventoryLocation.address",
    coordinates: "tooltips.inventoryLocation.coordinates",
    visibility: "tooltips.inventoryLocation.visibility",
    venueType: "tooltips.inventoryLocation.venueType",
  },

  // ── Inventory details drawer — Operations tab ───────────────────────────────
  inventoryOperations: {
    startTime: "tooltips.inventoryOperations.startTime",
    endTime: "tooltips.inventoryOperations.endTime",
    operatingDays: "tooltips.inventoryOperations.operatingDays",
    maintenanceWindow: "tooltips.inventoryOperations.maintenanceWindow",
    loopSize: "tooltips.inventoryOperations.loopSize",
    slotDuration: "tooltips.inventoryOperations.slotDuration",
    clientsPerLoop: "tooltips.inventoryOperations.clientsPerLoop",
    cycleTime: "tooltips.inventoryOperations.cycleTime",
  },
} as const;
