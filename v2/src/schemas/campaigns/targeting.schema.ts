import {
  INVENTORY_CLUSTERS_BY_CHANNEL,
  INVENTORY_CLUSTERS_COMING_SOON,
} from "@constants/inventory.constants";
import { z } from "zod";

// Demographics schema
const demographicsSchema = z.object({
  age: z.array(z.string()),
  gender: z.array(z.string()),
  income: z.array(z.string()),
  interests: z.array(z.string()),
  behavior: z.array(z.string()),
});

// Geofencing geometry schema (for drawn shapes like polygons, lines)
const geometrySchema = z.object({
  type: z.enum(["Polygon", "LineString", "Point"]),
  coordinates: z.array(z.array(z.number())), // [[lng, lat], [lng, lat], ...]
  included: z.boolean(),
  name: z.string().optional(),
  poi: z.array(z.string()).optional(),
  metadata: z
    .object({
      type: z.string().optional(),
    })
    .optional()
    .optional(),
});

// Geofencing location schema (for search-based locations and circles)
const locationSchema = z.object({
  lat: z.number(),
  lng: z.number(),
  radius: z.number().optional(), // For circles
  address: z.string(),
  included: z.boolean(),
  name: z.string().optional(), // For circle names like "Circle 1"
  poi: z.array(z.string()).optional(),
  metadata: z
    .object({
      type: z.string().optional(),
    })
    .optional(),
});

// Geofencing schema
const geofencingSchema = z.object({
  geometries: z.array(geometrySchema).default([]).optional(),
  locations: z.array(locationSchema).default([]).optional(),
});

// Signals schema - direct array of signal trigger values
const signalsSchema = z.array(z.string());

// Inventory types schema
const inventoryTypesSchema = z.object({
  digitalOoh: z.array(z.string()).default([]),
  classicOoh: z.array(z.string()).default([]),
});

// Inventory cluster schema — flat list of the inventory-cluster checkboxes
// checked in the "Inventory Types" section, across both channels (all
// checked by default except the "Coming Soon" ones, matching the section's
// default UI state).
const inventoryClusterSchema = z.array(z.string());

const DEFAULT_INVENTORY_CLUSTERS = [
  ...INVENTORY_CLUSTERS_BY_CHANNEL.digitalOoh,
  ...INVENTORY_CLUSTERS_BY_CHANNEL.classicOoh,
].filter((value) => !INVENTORY_CLUSTERS_COMING_SOON.includes(value));

// Main targeting schema
const targetingSchema = z.object({
  demographics: demographicsSchema,
  geofencing: geofencingSchema,
  signals: signalsSchema,
  venueTypes: inventoryTypesSchema.default({ digitalOoh: [], classicOoh: [] }),
  inventoryCluster: inventoryClusterSchema.default(DEFAULT_INVENTORY_CLUSTERS),
  programmaticOnly: z.boolean().default(false),
});

// Validation rules for targeting
const targetingValidationSchema = targetingSchema.refine(
  () => {
    return true;
  },
  {
    message: "At least one targeting criteria must be selected",
    path: ["demographics"], // Point to demographics as the main field
  },
);

export default targetingValidationSchema;
export type TargetingFormData = z.infer<typeof targetingValidationSchema>;
