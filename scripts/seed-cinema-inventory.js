// Seed script: cinema-hall inventory for the mw-planner Mongo `inventories` collection.
//
// Run against the running local mongo with the LEGACY mongo shell:
//   mongo --quiet 127.0.0.1:27017/mw-planner scripts/seed-cinema-inventory.js
//
// Idempotent: deletes every existing classification=="Cinema" doc, then reinserts.
// Each document = ONE cinema hall (one screen/auditorium). The buy unit is the
// environment (operator -> cinema -> hall -> showtime window + genre/rating
// constraints); films are only an indicative read-only preview, never a buy unit,
// so no film data is stored here.
//
// Shape mirrors existing Digital docs so pricing/forecast code produces non-zero
// price + impressions in the Step 4 list and forecasts:
//   - prices[] with cpm + spot (INR/MYR), priceTypes ["cpm","spot"] (goalType filter)
//   - digitalFields with spotDuration/spotsPerLoop/loopsPerHour (booking matrix / adPlays)
// venueType/venueTypeIds use the real cinema taxonomy (Leisure=8, Movie Theaters=802).

(function () {
  var NOW = new Date("2026-08-14T08:46:01Z");

  // ---- helpers ------------------------------------------------------------
  // Deterministic UUID-v4-ish generator seeded per-doc so re-runs are stable-ish.
  function uuid() {
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (c) {
      var r = (Math.random() * 16) | 0;
      var v = c === "x" ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    });
  }
  function pick(arr, i) {
    return arr[i % arr.length];
  }
  function pad(n, w) {
    var s = "" + n;
    while (s.length < w) s = "0" + s;
    return s;
  }
  function jitter(base, spread) {
    return base + (Math.random() - 0.5) * spread;
  }

  // ---- catalogs (mirrors shared/cinema-operators.ts) ----------------------
  // Stable slug ids: "mo-cinema-<operator-slug>".
  var OPERATORS = {
    IN: [
      { slug: "pvr-inox", name: "PVR INOX", premium: "PVR Director's Cut" },
      { slug: "cinepolis-india", name: "Cinepolis India", premium: "Cinepolis VIP" },
      { slug: "carnival", name: "Carnival Cinemas" },
      { slug: "miraj", name: "Miraj Cinemas" },
    ],
    MY: [
      { slug: "gsc", name: "Golden Screen Cinemas (GSC)", premium: "GSC Maxx" },
      { slug: "tgv", name: "TGV Cinemas", premium: "TGV Indulge" },
      { slug: "mbo", name: "MBO Cinemas" },
    ],
  };

  // Cities per country with realistic-ish coordinates + malls.
  var CITIES = {
    IN: [
      {
        city: "Chennai",
        state: "Tamil Nadu",
        lat: 13.0827,
        lng: 80.2707,
        malls: ["Phoenix MarketCity", "Express Avenue", "VR Chennai", "Forum Vijaya Mall"],
      },
      {
        city: "Mumbai",
        state: "Maharashtra",
        lat: 19.076,
        lng: 72.8777,
        malls: ["Phoenix Palladium", "Inorbit Mall", "R City Mall", "Oberoi Mall"],
      },
      {
        city: "Delhi",
        state: "Delhi",
        lat: 28.6139,
        lng: 77.209,
        malls: ["Select Citywalk", "DLF Promenade", "Pacific Mall", "Ambience Mall"],
      },
      {
        city: "Bengaluru",
        state: "Karnataka",
        lat: 12.9716,
        lng: 77.5946,
        malls: ["Phoenix Mall of Asia", "Orion Mall", "Forum Mall", "Mantri Square"],
      },
    ],
    MY: [
      {
        city: "Kuala Lumpur",
        state: "Kuala Lumpur",
        lat: 3.139,
        lng: 101.6869,
        malls: ["Pavilion KL", "Mid Valley Megamall", "Suria KLCC", "Berjaya Times Square"],
      },
      {
        city: "Penang",
        state: "Penang",
        lat: 5.4141,
        lng: 100.3288,
        malls: ["Gurney Plaza", "Queensbay Mall", "Gurney Paragon", "1st Avenue Mall"],
      },
    ],
  };

  var COUNTRY_META = {
    IN: {
      country: "India",
      countryCode: "IND",
      currency: "INR",
      timeZone: "Asia/Kolkata",
      ratings: ["U", "UA", "A"],
      // realistic INR cpm/spot for cinema on-screen
      cpmBase: 350,
      spotBase: 4500,
    },
    MY: {
      country: "Malaysia",
      countryCode: "MYS",
      currency: "MYR",
      timeZone: "Asia/Kuala_Lumpur",
      ratings: ["U", "P12", "18"],
      cpmBase: 40,
      spotBase: 550,
    },
  };

  var GENRES = [
    "Action",
    "Drama",
    "Comedy",
    "Thriller",
    "Romance",
    "Sci-Fi",
    "Animation",
    "Horror",
  ];
  var SCREEN_FORMATS = ["2D", "3D", "IMAX"];

  var SHOWTIME_WINDOWS = [
    { label: "Matinee", start: "11:00", end: "14:00" },
    { label: "Evening", start: "17:00", end: "20:00" },
    { label: "Late Night", start: "21:00", end: "24:00" },
  ];

  // Operating times: open late-morning to midnight, all 7 days.
  // Keys MUST be Weekday enum names (SUNDAY..SATURDAY) — the backend maps
  // operatingTimes to Map<Inventory.Weekday, ...>; numeric keys ("0".."6")
  // blow up entity hydration with "No enum constant Weekday.0" (filter 500s).
  var WEEKDAY_NAMES = [
    "SUNDAY",
    "MONDAY",
    "TUESDAY",
    "WEDNESDAY",
    "THURSDAY",
    "FRIDAY",
    "SATURDAY",
  ];
  function buildOperatingTimes() {
    var ot = {};
    for (var d = 0; d <= 6; d++) {
      ot[WEEKDAY_NAMES[d]] = [{ start: "10:00:00", end: "23:59:00" }];
    }
    return ot;
  }

  // ---- generation ---------------------------------------------------------
  var TARGET = 50;
  // Country split roughly 3:2 (India:Malaysia).
  var PLAN = [
    { code: "IN", count: 30 },
    { code: "MY", count: 20 },
  ];

  var docs = [];
  var seq = 0;

  PLAN.forEach(function (plan) {
    var code = plan.code;
    var meta = COUNTRY_META[code];
    var ops = OPERATORS[code];
    var cities = CITIES[code];

    for (var i = 0; i < plan.count; i++) {
      seq++;
      var op = pick(ops, i);
      var cityInfo = pick(cities, i);
      var mall = pick(cityInfo.malls, i);
      var hallNumber = (i % 8) + 1;
      var screenFormat = pick(SCREEN_FORMATS, i + hallNumber);
      var seats = 120 + ((i * 37) % 201); // 120..320
      var id = uuid();
      var refId =
        meta.countryCode + "-CIN-D-" + pad(seq, 5) + "-" + Math.floor(Math.random() * 90000 + 10000);

      // 2-4 showtime windows (deterministic per doc).
      var winCount = 2 + (i % 3); // 2,3,4
      if (winCount > SHOWTIME_WINDOWS.length) winCount = SHOWTIME_WINDOWS.length;
      var windows = SHOWTIME_WINDOWS.slice(0, winCount);

      // Genres: 3-5 of the set, deterministic.
      var gCount = 3 + (i % 3);
      var genres = [];
      for (var g = 0; g < gCount; g++) genres.push(pick(GENRES, i + g));
      genres = genres.filter(function (v, idx, self) {
        return self.indexOf(v) === idx;
      });

      var cinemaName = op.name + " " + mall;
      var hallName = "Audi " + hallNumber;
      var name = cinemaName + " - " + hallName;

      var cpm = Math.round(jitter(meta.cpmBase, meta.cpmBase * 0.3) * 100) / 100;
      var spot = Math.round(jitter(meta.spotBase, meta.spotBase * 0.3) * 100) / 100;

      var doc = {
        _id: id,
        inventoryId: id,
        referenceId: refId,
        externalId: null,
        name: name,
        classification: "Cinema",
        type: "Cinema",
        format: "Cinema Screen",
        environment: "indoor",
        venueType: ["Leisure", "Movie Theaters"],
        venueTypeIds: ["8", "802"],
        viewingDistance: null,
        archived: false,
        requiresContentApproval: true,
        location: {
          locationCoordinates: {
            type: "Point",
            coordinates: [
              Math.round(jitter(cityInfo.lng, 0.08) * 1e6) / 1e6,
              Math.round(jitter(cityInfo.lat, 0.08) * 1e6) / 1e6,
            ],
          },
          address: mall + ", " + cityInfo.city + ", " + meta.country,
          country: meta.country,
          state: cityInfo.state,
          city: cityInfo.city,
          zipCode: null,
        },
        panels: [
          {
            id: uuid(),
            pixelWidth: 2048,
            pixelHeight: 858,
            orientation: "landscape",
            physicalWidth: 20.0,
            physicalHeight: 8.4,
            screenCount: 1,
            panelCount: 1,
            unit: "Feet",
            supportsAudio: true,
            supportsTouch: false,
            cardinalDirection: null,
          },
        ],
        mediaOwnerId: "mo-cinema-" + op.slug,
        mediaOwnerName: op.name,
        thumbnailUrl: null,
        operatingTimes: buildOperatingTimes(),
        sellingTerm: {
          minDays: 1,
          leadDays: 3,
          minHours: null,
          dayPartGroups: null,
        },
        orientation: "landscape",
        timeZone: meta.timeZone,
        programmaticDealTypes: [],
        creativeFormats: [
          { format: "mp4", creativeType: "video" },
          { format: "mov", creativeType: "video" },
        ],
        // Pricing: cpm + spot so preview/forecast produce non-zero cost + impressions.
        prices: [
          {
            id: uuid(),
            cpm: cpm,
            spot: spot,
            monthly: null,
            daily: null,
            weekly: null,
            currency: meta.currency,
            durationSeconds: 30,
          },
        ],
        // Internal-only: mirrors ExternalInventoryMessageConverter.generatePriceTypes so the
        // goalType pricing filter (priceTypes IN cpm/spot/monthly) does NOT exclude cinema.
        priceTypes: ["cpm", "spot"],
        // digitalFields drives the booking-matrix / adPlays / impressions preview path.
        // bookingMode "slot": one advertiser per pre-show slot.
        digitalFields: {
          bookingMode: "slot",
          playerCount: 1,
          loopDuration: 300,
          loopsPerHour: 4,
          spotDuration: 30,
          spotsPerLoop: 10,
          playerSoftwareId: null,
          playerSoftwareName: "Cinema DCP",
        },
        contentExclusions: [],
        medias: [],
        tags: [],
        externalIds: [],
        size: null,
        inventoryCluster: [],
        // Cinema buy attributes per shared contract.
        cinemaFields: {
          operator: op.name,
          operatorId: "mo-cinema-" + op.slug,
          cinemaName: cinemaName,
          hallName: hallName,
          hallNumber: hallNumber,
          seats: seats,
          screenFormat: screenFormat,
          showtimeWindows: windows,
          genres: genres,
          ratings: meta.ratings.slice(),
        },
        createdAt: NOW,
        updatedAt: NOW,
      };

      docs.push(doc);
    }
  });

  // ---- persist (idempotent: delete + reinsert cinema docs) ----------------
  var removed = db.inventories.remove({ classification: "Cinema" });
  print("Removed existing Cinema docs: " + (removed.nRemoved !== undefined ? removed.nRemoved : removed));

  var inserted = 0;
  docs.forEach(function (d) {
    db.inventories.insert(d);
    inserted++;
  });
  print("Inserted Cinema docs: " + inserted);

  // ---- verify -------------------------------------------------------------
  var count = db.inventories.count({ classification: "Cinema" });
  print("Total classification=='Cinema' count: " + count);
  print("Sample doc:");
  printjson(db.inventories.findOne({ classification: "Cinema" }));
})();
