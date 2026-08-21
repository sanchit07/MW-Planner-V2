import { describe, it, expect } from "vitest";

import {
  InventoryClassification,
  MediaChannel,
} from "../../constants/inventory.constants";
import {
  InventoryFilters,
  InventoryItem,
  InventoryLocation,
  LatLong,
} from "../../types/inventory.types";
import {
  applyChannelClassificationLock,
  buildVenueIdMap,
  buildVenueSlugToNamePath,
  buildVenueTypeIdFilter,
  filterSelectedInventoryClientSide,
  getInventoryTypeLabel,
  getOperatingWindow,
  getPrimaryCoordinates,
  getLatitude,
  getLongitude,
  getTargetingVenueTypes,
  getVenueDrivenClassificationDefault,
  isTransitVenue,
  mediaChannelsToClassifications,
} from "../inventory.utils";

describe("inventory.utils", () => {
  const mockLocation: InventoryLocation = {
    locationCoordinates: {
      coordinates: [
        {
          latitude: 40.7128,
          longitude: -74.006,
        } as LatLong,
      ],
    },
  } as InventoryLocation;

  describe("getPrimaryCoordinates", () => {
    it("should return first coordinate when available", () => {
      const result = getPrimaryCoordinates(mockLocation);
      expect(result).toEqual({
        latitude: 40.7128,
        longitude: -74.006,
      });
    });

    it("should return null when location is null", () => {
      expect(getPrimaryCoordinates(null)).toBeNull();
    });

    it("should return null when location is undefined", () => {
      expect(getPrimaryCoordinates(undefined)).toBeNull();
    });

    it("should return null when coordinates are missing", () => {
      const locationWithoutCoords = {} as InventoryLocation;
      expect(getPrimaryCoordinates(locationWithoutCoords)).toBeNull();
    });

    it("should return null when coordinates array is empty", () => {
      const locationWithEmptyCoords = {
        locationCoordinates: {
          coordinates: [],
        },
      } as unknown as InventoryLocation;
      expect(getPrimaryCoordinates(locationWithEmptyCoords)).toBeNull();
    });
  });

  describe("getLatitude", () => {
    it("should return latitude from coordinates", () => {
      expect(getLatitude(mockLocation)).toBe(40.7128);
    });

    it("should return undefined when location is null", () => {
      expect(getLatitude(null)).toBeUndefined();
    });

    it("should return undefined when coordinates are missing", () => {
      expect(getLatitude({} as InventoryLocation)).toBeUndefined();
    });
  });

  describe("getLongitude", () => {
    it("should return longitude from coordinates", () => {
      expect(getLongitude(mockLocation)).toBe(-74.006);
    });

    it("should return undefined when location is null", () => {
      expect(getLongitude(null)).toBeUndefined();
    });

    it("should return undefined when coordinates are missing", () => {
      expect(getLongitude({} as InventoryLocation)).toBeUndefined();
    });
  });

  describe("mediaChannelsToClassifications", () => {
    it("maps classic-only media channel to Classic classification", () => {
      expect(
        mediaChannelsToClassifications([MediaChannel.CLASSIC_OOH]),
      ).toEqual([InventoryClassification.CLASSIC]);
    });

    it("maps digital-only media channel to Digital classification", () => {
      expect(
        mediaChannelsToClassifications([MediaChannel.DIGITAL_OOH]),
      ).toEqual([InventoryClassification.DIGITAL]);
    });

    it("maps cinema-only media channel to Cinema classification", () => {
      expect(mediaChannelsToClassifications([MediaChannel.CINEMA])).toEqual([
        InventoryClassification.CINEMA,
      ]);
    });

    it("returns the union of two selected channels (Digital + Classic)", () => {
      expect(
        mediaChannelsToClassifications([
          MediaChannel.DIGITAL_OOH,
          MediaChannel.CLASSIC_OOH,
        ]),
      ).toEqual([
        InventoryClassification.DIGITAL,
        InventoryClassification.CLASSIC,
      ]);
    });

    it("returns the union of two selected channels (Digital + Cinema)", () => {
      expect(
        mediaChannelsToClassifications([
          MediaChannel.DIGITAL_OOH,
          MediaChannel.CINEMA,
        ]),
      ).toEqual([
        InventoryClassification.DIGITAL,
        InventoryClassification.CINEMA,
      ]);
    });

    it("returns empty array when all known channels are selected", () => {
      expect(
        mediaChannelsToClassifications([
          MediaChannel.DIGITAL_OOH,
          MediaChannel.CLASSIC_OOH,
          MediaChannel.CINEMA,
        ]),
      ).toEqual([]);
    });

    it("returns empty array for empty or missing media channels", () => {
      expect(mediaChannelsToClassifications([])).toEqual([]);
      expect(
        mediaChannelsToClassifications(undefined as unknown as string[]),
      ).toEqual([]);
    });

    it("ignores unknown media channel values", () => {
      expect(mediaChannelsToClassifications(["UNKNOWN_CHANNEL"])).toEqual([]);
    });
  });

  describe("applyChannelClassificationLock", () => {
    const baseFilters: InventoryFilters = {
      mediaOwners: [],
      venueTypes: [],
      bookingMode: [],
      sizes: [],
      latitude: "",
      longitude: "",
      searchbyquery: "",
      environments: [],
      inventoryClassification: [],
      programmaticSupport: "ALL",
      dealTypes: [],
    };

    it("forces Digital classification when only Digital OOH channel is selected", () => {
      const result = applyChannelClassificationLock(baseFilters, [
        MediaChannel.DIGITAL_OOH,
      ]);
      expect(result.inventoryClassification).toEqual([
        InventoryClassification.DIGITAL,
      ]);
    });

    it("forces Classic classification when only Classic OOH channel is selected", () => {
      const result = applyChannelClassificationLock(baseFilters, [
        MediaChannel.CLASSIC_OOH,
      ]);
      expect(result.inventoryClassification).toEqual([
        InventoryClassification.CLASSIC,
      ]);
    });

    it("overrides a stale manual classification when the channel is locked", () => {
      const result = applyChannelClassificationLock(
        {
          ...baseFilters,
          inventoryClassification: [InventoryClassification.CLASSIC],
        },
        [MediaChannel.DIGITAL_OOH],
      );
      expect(result.inventoryClassification).toEqual([
        InventoryClassification.DIGITAL,
      ]);
    });

    it("forces Cinema classification when only Cinema channel is selected", () => {
      const result = applyChannelClassificationLock(baseFilters, [
        MediaChannel.CINEMA,
      ]);
      expect(result.inventoryClassification).toEqual([
        InventoryClassification.CINEMA,
      ]);
    });

    it("constrains the user's picks to the union for a two-channel campaign", () => {
      // User has both Digital and Cinema picked; a Digital+Cinema campaign
      // keeps the intersection (both are valid).
      const result = applyChannelClassificationLock(
        {
          ...baseFilters,
          inventoryClassification: [
            InventoryClassification.DIGITAL,
            InventoryClassification.CINEMA,
          ],
        },
        [MediaChannel.DIGITAL_OOH, MediaChannel.CINEMA],
      );
      expect(result.inventoryClassification).toEqual([
        InventoryClassification.DIGITAL,
        InventoryClassification.CINEMA,
      ]);
    });

    it("drops out-of-union picks for a two-channel campaign", () => {
      // Only Classic picked, but the campaign is Digital+Cinema → Classic is
      // out of the union, so the intersection is empty and the whole union wins.
      const result = applyChannelClassificationLock(
        {
          ...baseFilters,
          inventoryClassification: [InventoryClassification.CLASSIC],
        },
        [MediaChannel.DIGITAL_OOH, MediaChannel.CINEMA],
      );
      expect(result.inventoryClassification).toEqual([
        InventoryClassification.DIGITAL,
        InventoryClassification.CINEMA,
      ]);
    });

    it("keeps a valid single pick from within the union", () => {
      const result = applyChannelClassificationLock(
        {
          ...baseFilters,
          inventoryClassification: [InventoryClassification.DIGITAL],
        },
        [MediaChannel.DIGITAL_OOH, MediaChannel.CLASSIC_OOH],
      );
      expect(result.inventoryClassification).toEqual([
        InventoryClassification.DIGITAL,
      ]);
    });

    it("returns filters unchanged when all known channels are selected", () => {
      const filters = {
        ...baseFilters,
        inventoryClassification: [InventoryClassification.CLASSIC],
      };
      expect(
        applyChannelClassificationLock(filters, [
          MediaChannel.DIGITAL_OOH,
          MediaChannel.CLASSIC_OOH,
          MediaChannel.CINEMA,
        ]),
      ).toBe(filters);
    });

    it("returns filters unchanged when media channels are missing or empty", () => {
      expect(applyChannelClassificationLock(baseFilters, undefined)).toBe(
        baseFilters,
      );
      expect(applyChannelClassificationLock(baseFilters, null)).toBe(
        baseFilters,
      );
      expect(applyChannelClassificationLock(baseFilters, [])).toBe(baseFilters);
    });
  });

  describe("getTargetingVenueTypes", () => {
    it("returns the union of digitalOoh and classicOoh venue types", () => {
      const result = getTargetingVenueTypes({
        targeting: {
          venueTypes: {
            digitalOoh: ["MALL", "AIRPORT"],
            classicOoh: ["BILLBOARD"],
          },
        },
      });
      expect(result).toEqual(["MALL", "AIRPORT", "BILLBOARD"]);
    });

    it("de-duplicates values present in both channels", () => {
      const result = getTargetingVenueTypes({
        targeting: {
          venueTypes: { digitalOoh: ["MALL"], classicOoh: ["MALL", "GYM"] },
        },
      });
      expect(result).toEqual(["MALL", "GYM"]);
    });

    it("returns an empty array when no targeting venue types are set", () => {
      expect(getTargetingVenueTypes({})).toEqual([]);
      expect(getTargetingVenueTypes(null)).toEqual([]);
      expect(getTargetingVenueTypes(undefined)).toEqual([]);
      expect(
        getTargetingVenueTypes({
          targeting: { venueTypes: { digitalOoh: [], classicOoh: [] } },
        }),
      ).toEqual([]);
    });
  });

  describe("getVenueDrivenClassificationDefault", () => {
    const bothChannels = [MediaChannel.DIGITAL_OOH, MediaChannel.CLASSIC_OOH];

    it("defaults to Digital when both channels are selected but only digital venue types are set", () => {
      expect(
        getVenueDrivenClassificationDefault({
          mediaChannels: bothChannels,
          targeting: { venueTypes: { digitalOoh: ["MALL"], classicOoh: [] } },
        }),
      ).toEqual([InventoryClassification.DIGITAL]);
    });

    it("defaults to Classic when both channels are selected but only classic venue types are set", () => {
      expect(
        getVenueDrivenClassificationDefault({
          mediaChannels: bothChannels,
          targeting: { venueTypes: { digitalOoh: [], classicOoh: ["GYM"] } },
        }),
      ).toEqual([InventoryClassification.CLASSIC]);
    });

    it("returns empty when both channels have venue types", () => {
      expect(
        getVenueDrivenClassificationDefault({
          mediaChannels: bothChannels,
          targeting: {
            venueTypes: { digitalOoh: ["MALL"], classicOoh: ["GYM"] },
          },
        }),
      ).toEqual([]);
    });

    it("returns empty when only one channel is selected (lock handles it)", () => {
      expect(
        getVenueDrivenClassificationDefault({
          mediaChannels: [MediaChannel.DIGITAL_OOH],
          targeting: { venueTypes: { digitalOoh: ["MALL"], classicOoh: [] } },
        }),
      ).toEqual([]);
    });

    it("returns empty when no venue types are set", () => {
      expect(
        getVenueDrivenClassificationDefault({
          mediaChannels: bothChannels,
          targeting: { venueTypes: { digitalOoh: [], classicOoh: [] } },
        }),
      ).toEqual([]);
      expect(getVenueDrivenClassificationDefault(null)).toEqual([]);
    });
  });

  describe("getOperatingWindow", () => {
    it("returns the day's window when all days are identical", () => {
      const times = Object.fromEntries(
        Array.from({ length: 7 }, (_, i) => [
          String(i),
          [{ start: "06:00:00", end: "23:00:00" }],
        ]),
      );
      expect(getOperatingWindow(times)).toEqual({
        startTime: "06:00:00",
        endTime: "23:00:00",
      });
    });

    it("returns the earliest start and latest end across differing days", () => {
      expect(
        getOperatingWindow({
          "0": [{ start: "06:00:00", end: "23:00:00" }],
          "1": [{ start: "08:00:00", end: "20:00:00" }],
          "2": [{ start: "07:00:00", end: "22:00:00" }],
        }),
      ).toEqual({ startTime: "06:00:00", endTime: "23:00:00" });
    });

    it("treats an end of 00:00:00 as end-of-day, selecting it as the latest end", () => {
      expect(
        getOperatingWindow({
          "0": [{ start: "05:00:00", end: "00:00:00" }],
          "1": [{ start: "05:00:00", end: "23:00:00" }],
        }),
      ).toEqual({ startTime: "05:00:00", endTime: "00:00:00" });
    });

    it("considers every slot in a day", () => {
      expect(
        getOperatingWindow({
          "0": [
            { start: "06:00:00", end: "12:00:00" },
            { start: "14:00:00", end: "22:00:00" },
          ],
        }),
      ).toEqual({ startTime: "06:00:00", endTime: "22:00:00" });
    });

    it("returns null when there are no operating times", () => {
      expect(getOperatingWindow(null)).toBeNull();
      expect(getOperatingWindow(undefined)).toBeNull();
      expect(getOperatingWindow({})).toBeNull();
      expect(getOperatingWindow({ "0": [] })).toBeNull();
    });
  });

  describe("buildVenueIdMap", () => {
    const venues = [
      {
        enumerationId: 1,
        stringValue: "MALL",
        children: [{ enumerationId: 2, stringValue: "SHOPPING_CENTER" }],
      },
      { enumerationId: 3, stringValue: "AIRPORT" },
    ];

    it("flattens the venue tree into a stringValue -> id (string) map", () => {
      expect(buildVenueIdMap(venues)).toEqual({
        MALL: "1",
        SHOPPING_CENTER: "2",
        AIRPORT: "3",
      });
    });

    it("returns an empty map for missing venues", () => {
      expect(buildVenueIdMap(null)).toEqual({});
      expect(buildVenueIdMap(undefined)).toEqual({});
    });
  });

  describe("buildVenueTypeIdFilter", () => {
    const idMap = { MALL: "1", AIRPORT: "3", GYM: "7" };
    const DIGITAL = [InventoryClassification.DIGITAL];
    const CLASSIC = [InventoryClassification.CLASSIC];

    it("buckets ids into digitalOoh only when classification is Digital", () => {
      expect(
        buildVenueTypeIdFilter(["MALL", "AIRPORT"], DIGITAL, idMap),
      ).toEqual({ digitalOoh: ["1", "3"], classicOoh: [] });
    });

    it("buckets ids into classicOoh only when classification is Classic", () => {
      expect(buildVenueTypeIdFilter(["GYM"], CLASSIC, idMap)).toEqual({
        digitalOoh: [],
        classicOoh: ["7"],
      });
    });

    it("puts ids in both buckets when both classifications are selected", () => {
      expect(
        buildVenueTypeIdFilter(
          ["MALL"],
          [InventoryClassification.DIGITAL, InventoryClassification.CLASSIC],
          idMap,
        ),
      ).toEqual({ digitalOoh: ["1"], classicOoh: ["1"] });
    });

    it("puts ids in both buckets when no classification is selected", () => {
      expect(buildVenueTypeIdFilter(["MALL"], [], idMap)).toEqual({
        digitalOoh: ["1"],
        classicOoh: ["1"],
      });
    });

    it("maps stringValues to ids and drops unknown values", () => {
      expect(
        buildVenueTypeIdFilter(["MALL", "UNKNOWN"], DIGITAL, idMap),
      ).toEqual({ digitalOoh: ["1"], classicOoh: [] });
    });

    it("returns null when nothing is selected or nothing resolves", () => {
      expect(buildVenueTypeIdFilter([], DIGITAL, idMap)).toBeNull();
      expect(buildVenueTypeIdFilter(null, DIGITAL, idMap)).toBeNull();
      expect(buildVenueTypeIdFilter(["UNKNOWN"], DIGITAL, idMap)).toBeNull();
    });
  });

  describe("buildVenueSlugToNamePath", () => {
    it("maps each node's slug to its root→node display-name path", () => {
      const tree = [
        {
          enumerationId: 1,
          stringValue: "outdoor",
          name: "Outdoor",
          children: [
            {
              enumerationId: 2,
              stringValue: "outdoor-billboards",
              name: "Billboards",
              children: [
                {
                  enumerationId: 3,
                  stringValue: "outdoor-billboards-roadside",
                  name: "Roadside",
                  children: [],
                },
              ],
            },
          ],
        },
      ];
      expect(buildVenueSlugToNamePath(tree)).toEqual({
        outdoor: ["Outdoor"],
        "outdoor-billboards": ["Outdoor", "Billboards"],
        "outdoor-billboards-roadside": ["Outdoor", "Billboards", "Roadside"],
      });
    });

    it("returns an empty map for empty/nullish input", () => {
      expect(buildVenueSlugToNamePath([])).toEqual({});
      expect(buildVenueSlugToNamePath(null)).toEqual({});
      expect(buildVenueSlugToNamePath(undefined)).toEqual({});
    });
  });

  describe("filterSelectedInventoryClientSide", () => {
    const makeItem = (
      overrides: Partial<InventoryItem["detail"]>,
    ): InventoryItem =>
      ({
        detail: {
          id: "1",
          name: "Default Name",
          mediaOwnerId: "mo-1",
          inventoryType: "Digital",
          environment: "outdoor",
          venueType: ["Outdoor", "Billboards", "Roadside"],
          bookingMode: "loop",
          panels: [{ size: "L" }],
          ...overrides,
        },
      }) as InventoryItem;

    const emptyFilters: InventoryFilters = {
      mediaOwners: [],
      venueTypes: [],
      bookingMode: [],
      sizes: [],
      latitude: "",
      longitude: "",
      searchbyquery: "",
      environments: [],
      inventoryClassification: [],
      programmaticSupport: "ALL",
      dealTypes: [],
    };

    const f = (over: Partial<InventoryFilters>): InventoryFilters => ({
      ...emptyFilters,
      ...over,
    });

    const times = makeItem({ mediaOwnerId: "times", name: "Times Roadside" });
    const adonmo = makeItem({
      mediaOwnerId: "adonmo",
      name: "Awfis Office",
      environment: "indoor",
      venueType: ["Office Buildings", "Lobby"],
      panels: [{ size: "XL" } as InventoryItem["detail"]["panels"][number]],
    });
    const items = [times, adonmo];

    it("returns all items when filters are empty (passthrough)", () => {
      expect(filterSelectedInventoryClientSide(items, emptyFilters)).toEqual(
        items,
      );
    });

    it("returns input unchanged for empty list or null filters", () => {
      expect(filterSelectedInventoryClientSide([], emptyFilters)).toEqual([]);
      expect(filterSelectedInventoryClientSide(items, null)).toEqual(items);
    });

    it("filters by mediaOwners", () => {
      expect(
        filterSelectedInventoryClientSide(items, f({ mediaOwners: ["times"] })),
      ).toEqual([times]);
    });

    it("filters by environment case-insensitively", () => {
      // filter value "Indoor" (capitalized) vs response "indoor"
      expect(
        filterSelectedInventoryClientSide(
          items,
          f({ environments: ["Indoor"] }),
        ),
      ).toEqual([adonmo]);
    });

    describe("venueTypes (slug → name-path prefix)", () => {
      // times default path ["Outdoor","Billboards","Roadside"];
      // adonmo path ["Office Buildings","Lobby"].
      const slugMap: Record<string, string[]> = {
        "outdoor-billboards": ["Outdoor", "Billboards"],
        "outdoor-billboards-roadside": ["Outdoor", "Billboards", "Roadside"],
        "office-buildings": ["Office Buildings"],
        "office-buildings-lobby": ["Office Buildings", "Lobby"],
      };

      it("type-level slug matches items of that type and its descendants", () => {
        expect(
          filterSelectedInventoryClientSide(
            items,
            f({ venueTypes: ["outdoor-billboards"] }),
            { venueSlugToNamePath: slugMap },
          ),
        ).toEqual([times]);
      });

      it("leaf slug matches the matching item", () => {
        expect(
          filterSelectedInventoryClientSide(
            items,
            f({ venueTypes: ["office-buildings-lobby"] }),
            { venueSlugToNamePath: slugMap },
          ),
        ).toEqual([adonmo]);
      });

      it("item equal to the selected node matches (item == node)", () => {
        const typeItem = makeItem({
          mediaOwnerId: "t2",
          venueType: ["Outdoor", "Billboards"],
        });
        expect(
          filterSelectedInventoryClientSide(
            [typeItem],
            f({ venueTypes: ["outdoor-billboards"] }),
            { venueSlugToNamePath: slugMap },
          ),
        ).toEqual([typeItem]);
      });

      it("subtype-only selection excludes a broader (type-level) item", () => {
        const typeItem = makeItem({
          mediaOwnerId: "t3",
          venueType: ["Outdoor", "Billboards"],
        });
        expect(
          filterSelectedInventoryClientSide(
            [typeItem],
            f({ venueTypes: ["outdoor-billboards-roadside"] }),
            { venueSlugToNamePath: slugMap },
          ),
        ).toEqual([]);
      });

      it("matches ANY of multiple selected slugs", () => {
        expect(
          filterSelectedInventoryClientSide(
            items,
            f({
              venueTypes: ["outdoor-billboards", "office-buildings-lobby"],
            }),
            { venueSlugToNamePath: slugMap },
          ),
        ).toEqual([times, adonmo]);
      });

      it("tolerates consecutive duplicate names in the response path", () => {
        const dup = makeItem({
          mediaOwnerId: "dup",
          venueType: ["Office Buildings", "Office Buildings", "Lobby"],
        });
        expect(
          filterSelectedInventoryClientSide(
            [dup],
            f({ venueTypes: ["office-buildings-lobby"] }),
            { venueSlugToNamePath: slugMap },
          ),
        ).toEqual([dup]);
      });

      it("is skipped when the slug map is absent (cannot evaluate → no hide)", () => {
        expect(
          filterSelectedInventoryClientSide(
            items,
            f({ venueTypes: ["outdoor-billboards"] }),
          ),
        ).toEqual(items);
      });
    });

    it("filters by size matching any panel", () => {
      expect(
        filterSelectedInventoryClientSide(items, f({ sizes: ["XL"] })),
      ).toEqual([adonmo]);
    });

    it("filters by bookingMode", () => {
      expect(
        filterSelectedInventoryClientSide(items, f({ bookingMode: ["loop"] })),
      ).toEqual(items);
      expect(
        filterSelectedInventoryClientSide(
          items,
          f({ bookingMode: ["takeover"] }),
        ),
      ).toEqual([]);
    });

    it("filters by classification with prefix match (Digital → 'Digital Network')", () => {
      const net = makeItem({
        mediaOwnerId: "net",
        inventoryType: "Digital Network",
      });
      const classic = makeItem({ mediaOwnerId: "c", inventoryType: "Classic" });
      const all = [times, net, classic];
      expect(
        filterSelectedInventoryClientSide(
          all,
          f({ inventoryClassification: ["Digital"] }),
        ),
      ).toEqual([times, net]);
      expect(
        filterSelectedInventoryClientSide(
          all,
          f({ inventoryClassification: ["Classic"] }),
        ),
      ).toEqual([classic]);
    });

    it("filters by name (searchbyquery) case-insensitively", () => {
      expect(
        filterSelectedInventoryClientSide(items, f({ searchbyquery: "awfis" })),
      ).toEqual([adonmo]);
    });

    it("honors searchOverride over stale searchbyquery", () => {
      expect(
        filterSelectedInventoryClientSide(
          items,
          f({ searchbyquery: "stale" }),
          {
            searchOverride: "times",
          },
        ),
      ).toEqual([times]);
    });

    it("IGNORES unsupported lat/long filters (no field to replicate)", () => {
      expect(
        filterSelectedInventoryClientSide(
          items,
          f({ latitude: "22.5", longitude: "88.3" }),
        ),
      ).toEqual(items);
    });

    describe("programmaticSupport / dealTypes (detail.programmaticDealTypes)", () => {
      const prog = makeItem({
        mediaOwnerId: "prog",
        name: "Programmatic Screen",
        // response values are lowercase
        programmaticDealTypes: ["guaranteed", "open_auction"],
      });
      const nonProg = makeItem({
        mediaOwnerId: "nonprog",
        name: "Direct Screen",
        programmaticDealTypes: [],
      });
      const all = [prog, nonProg];

      it("support=YES keeps only programmatic items", () => {
        expect(
          filterSelectedInventoryClientSide(
            all,
            f({ programmaticSupport: "YES" }),
          ),
        ).toEqual([prog]);
      });

      it("support=NO keeps only non-programmatic items", () => {
        expect(
          filterSelectedInventoryClientSide(
            all,
            f({ programmaticSupport: "NO" }),
          ),
        ).toEqual([nonProg]);
      });

      it("support=ALL ignores programmatic + dealTypes", () => {
        expect(
          filterSelectedInventoryClientSide(
            all,
            f({ programmaticSupport: "ALL", dealTypes: ["GUARANTEED"] }),
          ),
        ).toEqual(all);
      });

      it("dealTypes match case-insensitively (UPPERCASE filter vs lowercase response), gated behind YES", () => {
        expect(
          filterSelectedInventoryClientSide(
            all,
            f({ programmaticSupport: "YES", dealTypes: ["GUARANTEED"] }),
          ),
        ).toEqual([prog]);
        // deal type not present on any programmatic item → none
        expect(
          filterSelectedInventoryClientSide(
            all,
            f({ programmaticSupport: "YES", dealTypes: ["PRIVATE_AUCTION"] }),
          ),
        ).toEqual([]);
      });
    });

    it("ANDs multiple active filters", () => {
      // indoor AND size L → adonmo is indoor but XL, times is L but outdoor → none
      expect(
        filterSelectedInventoryClientSide(
          items,
          f({ environments: ["Indoor"], sizes: ["L"] }),
        ),
      ).toEqual([]);
      // indoor AND size XL → adonmo
      expect(
        filterSelectedInventoryClientSide(
          items,
          f({ environments: ["Indoor"], sizes: ["XL"] }),
        ),
      ).toEqual([adonmo]);
    });
  });

  describe("isTransitVenue", () => {
    it("returns false when venues is empty/undefined", () => {
      expect(isTransitVenue(undefined)).toBe(false);
      expect(isTransitVenue([])).toBe(false);
    });

    it("returns true when a venue name matches a transit category", () => {
      expect(isTransitVenue([{ name: "Bus" }])).toBe(true);
      expect(isTransitVenue([{ name: "Rail & Metro" }])).toBe(true);
      expect(isTransitVenue([{ name: "Taxi & Rideshare" }])).toBe(true);
      expect(isTransitVenue([{ name: "Commercial Fleet" }])).toBe(true);
      expect(isTransitVenue([{ name: "Transit" }])).toBe(true);
    });

    it("matches case-insensitively and via the taxonomy path", () => {
      expect(isTransitVenue([{ name: "bus" }])).toBe(true);
      expect(isTransitVenue([{ path: "Digital/Transit/Bus" }])).toBe(true);
    });

    it("returns false for non-transit venues", () => {
      expect(isTransitVenue([{ name: "Mall" }])).toBe(false);
      expect(isTransitVenue([{ name: "Airport" }])).toBe(false);
      expect(isTransitVenue([{ name: "Billboard" }])).toBe(false);
    });
  });

  describe("getInventoryTypeLabel", () => {
    const t = (key: string) => key;

    it("returns null when typeName is missing", () => {
      expect(getInventoryTypeLabel(undefined, [], t)).toBeNull();
      expect(getInventoryTypeLabel(null, [], t)).toBeNull();
    });

    it("returns the plain Digital label for a non-transit digital inventory", () => {
      expect(getInventoryTypeLabel("Digital", [{ name: "Mall" }], t)).toBe(
        "inventoryDetails.typeLabel.digital",
      );
    });

    it("returns Digital Transit when the venue is a transit category", () => {
      expect(getInventoryTypeLabel("Digital", [{ name: "Bus" }], t)).toBe(
        "inventoryDetails.typeLabel.digitalTransit",
      );
    });

    it("returns the plain Classic label for a non-transit classic inventory", () => {
      expect(getInventoryTypeLabel("Classic", [{ name: "Billboard" }], t)).toBe(
        "inventoryDetails.typeLabel.classic",
      );
    });

    it("returns Classic Transit when the venue is a transit category", () => {
      expect(
        getInventoryTypeLabel("Classic", [{ name: "Rail & Metro" }], t),
      ).toBe("inventoryDetails.typeLabel.classicTransit");
    });
  });
});
