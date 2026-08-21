/**
 * Inventory Type Enum
 * Represents the different types of inventory available in the system
 */
export enum InventoryType {
  TRANSIT = "Transit",
  STREET_FURNITURE = "Street Furniture",
  PLACE_BASED = "Place Based",
  DIGITAL_NETWORK = "Digital Network",
  RETAIL = "Retail",
  OOH = "OOH",
  AIRPORT = "Airport",
  BUS = "Bus",
  RAIL_METRO = "Rail & Metro",
  TAXI_RIDESHARE = "Taxi & Rideshare",
  COMMERCIAL_FLEET = "Commercial Fleet",
  CINEMA = "Cinema",
}

export enum InventoryClassification {
  CLASSIC = "Classic",
  DIGITAL = "Digital",
  CINEMA = "Cinema",
}

/**
 * Media Channel Enum
 * Channels a campaign can target. Selected during campaign creation
 * (CreateCampaignForm) and stored on campaignData.mediaChannels.
 */
export enum MediaChannel {
  DIGITAL_OOH = "DIGITAL_OOH",
  CLASSIC_OOH = "CLASSIC_OOH",
  CINEMA = "CINEMA",
}

/**
 * Cinema film genres available as buy constraints in the Step 4 filter drawer's
 * Cinema section. The cinema buy is bought against the environment
 * (operator/hall/showtime window); genres/ratings are additional constraints.
 */
export const CINEMA_GENRES: string[] = [
  "Action",
  "Adventure",
  "Comedy",
  "Crime",
  "Drama",
  "Horror",
  "Romance",
  "Sci-Fi",
  "Thriller",
];

/**
 * Cinema content ratings available as buy constraints in the Step 4 filter
 * drawer's Cinema section.
 */
export const CINEMA_RATINGS: string[] = ["U", "PG", "PG-13", "R", "NC-17"];

export enum InventoryEnvironment {
  OUTDOOR = "Outdoor",
  INDOOR = "Indoor",
  SEMI_OUTDOOR = "Semi-Outdoor",
  IN_TRANSIT = "In-Transit",
}

/**
 * Inventory Cluster values sent as `inventoryCluster` in the inventory
 * filter request. Picked via the Targeting step's Inventory Types section
 * (one checkbox per value, grouped by media channel).
 */
export const INVENTORY_CLUSTERS_BY_CHANNEL: Record<
  "digitalOoh" | "classicOoh",
  string[]
> = {
  digitalOoh: ["DIGITAL", "DIGITAL_NETWORK", "DIGITAL_TRANSIT"],
  classicOoh: ["CLASSIC", "CLASSIC_NETWORK", "CLASSIC_TRANSIT"],
};

/**
 * Inventory cluster values shown but not yet selectable in the Inventory
 * Types section — disabled with a "Coming Soon" tooltip, and excluded from
 * the default checked set.
 */
export const INVENTORY_CLUSTERS_COMING_SOON: string[] = [
  "DIGITAL_NETWORK",
  "CLASSIC_NETWORK",
];

/**
 * Inventory Type (Uppercase) Enum
 * Used for API requests and backend communication
 */
export enum InventoryTypeUppercase {
  CLASSIC = "CLASSIC",
  DIGITAL = "DIGITAL",
  TRANSIT = "TRANSIT",
  STREET_FURNITURE = "STREET_FURNITURE",
  PLACE_BASED = "PLACE_BASED",
  DIGITAL_NETWORK = "DIGITAL_NETWORK",
  RETAIL = "RETAIL",
}

/**
 * Inventory Type (Lowercase) Enum
 * Used in some API responses and analytics
 */
export enum InventoryTypeLowercase {
  CLASSIC = "classic",
  CLASSIC_NETWORK = "classic network",
  DIGITAL = "digital",
  DIGITAL_NETWORK = "digital network",
  MOBILE = "mobile",
  CINEMA = "cinema",
}

/**
 * Environment Enum
 * Represents the environment where inventory is located
 */
export enum Environment {
  OUTDOOR = "outdoor",
  INDOOR = "indoor",
}

export enum ProgrammaticSupport {
  ALL = "ALL",
  YES = "YES",
  NO = "NO",
}

export enum ProgrammaticDealType {
  GUARANTEED = "GUARANTEED",
  PREFERRED_DEAL = "PREFERRED_DEAL",
  PRIVATE_AUCTION = "PRIVATE_AUCTION",
  OPEN_AUCTION = "OPEN_AUCTION",
  EVERGREEN_PMP = "EVERGREEN_PMP",
}

/**
 * Empty Value Display Enum
 * Represents the different ways to display empty/null values
 */
export enum EmptyValueDisplay {
  NOT_APPLICABLE = "N/A",
  DASH = "--",
}

/**
 * SOT Display Values
 * Screen On Time display constants
 */
export enum SOTDisplay {
  CLASSIC_DEFAULT = "24h",
}

/**
 * Default SOV values for different inventory types
 */
export enum DefaultSOV {
  CLASSIC = 100,
  DEFAULT = 10,
}
