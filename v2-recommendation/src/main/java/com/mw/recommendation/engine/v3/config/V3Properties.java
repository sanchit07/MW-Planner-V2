package com.mw.recommendation.engine.v3.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All v3 pipeline tunables in one place (PRD §5 requires weights/bounds to be configurable; the
 * v1/v2 pipelines hardcode them). Every field has a PRD-conformant default so the yaml block can
 * stay minimal; per-environment overrides go under {@code mw-recommendation-engine.v3.*}.
 */
@Data
@ConfigurationProperties(prefix = "mw-recommendation-engine.v3")
public class V3Properties {

  private Scoring scoring = new Scoring();
  private Geo geo = new Geo();
  private Availability availability = new Availability();
  private Budget budget = new Budget();
  private Variation variation = new Variation();
  private Selection selection = new Selection();
  private Diversity diversity = new Diversity();
  private Tolerance tolerance = new Tolerance();
  private Measure measure = new Measure();
  private Batch batch = new Batch();
  private Concurrency concurrency = new Concurrency();
  private Audit audit = new Audit();
  private Output output = new Output();

  @Data
  public static class Scoring {
    /** Default component weights (PRD §5.11). Must sum to 1.0. */
    private double measureFitWeight = 0.20;

    private double geoFitWeight = 0.20;
    private double availabilityWeight = 0.10;
    private double budgetFitWeight = 0.20;
    private double audienceFitWeight = 0.10;
    private double brandFitWeight = 0.10;
    private double qualityFitWeight = 0.06;
    private double timeFitWeight = 0.04;

    /**
     * When measureFit is null its weight is redistributed to geo/quality/audience/brand in the PRD
     * ratio 12:8:6:4 (§5.11), scaled so the redistributed weights still sum to 1.0 (the v1
     * implementation over-allocated to 1.10).
     */
    private double redistributionGeoShare = 12;

    private double redistributionQualityShare = 8;
    private double redistributionAudienceShare = 6;
    private double redistributionBrandShare = 4;

    /** Per-day impression normalization bounds for the no-goal measureFit proxy (PRD §5.2). */
    private long impressionsNormMin = 1_000;

    private long impressionsNormMax = 1_000_000;

    /** Optional per-country overrides of the impression bounds, keyed by country name. */
    private Map<String, long[]> impressionsNormBoundsByCountry = new LinkedHashMap<>();

    /** qualityFit sub-factor weights (PRD §5.9: size 30, pitch 25, visibility 25, owner 20). */
    private double qualitySizeWeight = 30;

    private double qualityPitchWeight = 25;
    private double qualityVisibilityWeight = 25;
    private double qualityOwnerWeight = 20;
  }

  @Data
  public static class Geo {
    /** Near-boundary band: within this distance of a geofence edge scores 90 (PRD §5.4). */
    private double r1Meters = 1_000;

    /** Beyond this distance geo relevance is zero (PRD §5.4, default 50 km country scope). */
    private double r2Meters = 50_000;

    /** Bonus when a requested channel (e.g. "airport") matches inventory classification/venue. */
    private double channelBonus = 10;

    /** Default radius when a circle geofence does not specify one. */
    private double defaultCircleRadiusMeters = 50_000;

    /** Number of top audience-concentration regions treated as geoFit=100 when no geo given. */
    private int topPopulousRegions = 5;
  }

  @Data
  public static class Availability {
    /** Availability at or above this percent is treated as fully available (PRD §5.5). */
    private double fullThresholdPct = 80;

    /** Below this percent the inventory is excluded from recommendations (PRD §5.5/§6.4). */
    private double excludeBelowPct = 10;

    /** Booked percentage above which an hour/date is considered unavailable in schedules. */
    private double bookedThresholdPct = 90;
  }

  @Data
  public static class Budget {
    /**
     * budgetFit bucket mapping (PRD §5.6): proportion &lt;=0.2→95, &lt;=0.5→75, &lt;=0.8→50,
     * &lt;=1.2→30, else 10. Entries are [maxProportion, score], evaluated in order.
     */
    private List<double[]> buckets =
        List.of(
            new double[] {0.2, 95},
            new double[] {0.5, 75},
            new double[] {0.8, 50},
            new double[] {1.2, 30});

    /** Score when the proportion exceeds the last bucket boundary. */
    private double overBucketScore = 10;

    /** Neutral budgetFit when no budget is provided (PRD §5.6). */
    private double neutralScore = 50;
  }

  @Data
  public static class Variation {
    /** Multiplicative jitter half-range (PRD §5.12: v = 0.075 → ~10-15% ranking variation). */
    private double jitterRange = 0.075;

    /** Score bands for the H.3 variation pool and premium/mid/filler labelling. */
    private double bandPremium = 90;

    private double bandHigh = 80;
    private double bandGood = 70;
    private double bandAcceptable = 60;
  }

  @Data
  public static class Selection {
    /** Results below this final score are never auto-selected (v2 parity). */
    private double minScore = 10;

    /** Score-band walk thresholds used by budget selection rounds. */
    private List<Double> scoreThresholds = List.of(90d, 80d, 70d, 60d, 50d, 40d, 30d, 20d, 10d);

    /** Two candidates within this score distance are tie-broken by goal contribution (Part D). */
    private double contributionTieBreakWindow = 5;

    /**
     * Default budget allocation percentages per goal and bucket (PRD Part H.2). Keys: impressions,
     * reach, ad_plays, sov, carbon, none. Buckets: digital, classic, transit, retail, network,
     * audio, experiential. Radio/experiential are opt-in (0 default, never redistributed into).
     */
    private Map<String, Map<String, Double>> allocations = defaultAllocations();

    private static Map<String, Map<String, Double>> defaultAllocations() {
      Map<String, Map<String, Double>> a = new LinkedHashMap<>();
      a.put(
          "impressions",
          Map.of(
              "digital",
              35d,
              "classic",
              25d,
              "transit",
              20d,
              "retail",
              10d,
              "network",
              7d,
              "audio",
              0d,
              "experiential",
              3d));
      a.put(
          "reach",
          Map.of(
              "classic",
              30d,
              "transit",
              25d,
              "retail",
              15d,
              "digital",
              15d,
              "network",
              10d,
              "audio",
              0d,
              "experiential",
              5d));
      a.put(
          "ad_plays",
          Map.of(
              "digital",
              40d,
              "network",
              30d,
              "retail",
              15d,
              "transit",
              10d,
              "classic",
              5d,
              "audio",
              0d,
              "experiential",
              0d));
      a.put(
          "sov",
          Map.of(
              "digital",
              40d,
              "network",
              25d,
              "transit",
              20d,
              "retail",
              10d,
              "classic",
              5d,
              "audio",
              0d,
              "experiential",
              0d));
      a.put(
          "carbon",
          Map.of(
              "classic",
              45d,
              "transit",
              30d,
              "retail",
              15d,
              "digital",
              5d,
              "network",
              5d,
              "audio",
              0d,
              "experiential",
              0d));
      a.put(
          "none",
          Map.of(
              "digital",
              35d,
              "classic",
              25d,
              "transit",
              20d,
              "retail",
              10d,
              "network",
              7d,
              "audio",
              0d,
              "experiential",
              3d));
      return a;
    }
  }

  @Data
  public static class Diversity {
    /** No single city may exceed this percent of budget when 2+ cities are in play (Part H.1). */
    private double cityCapPct = 50;

    /** Minimum budget percent per city tier when that city is included (Part H.1). */
    private Map<Integer, Double> tierMinimumPct = Map.of(1, 15d, 2, 10d, 3, 5d);

    /** Predefined city tiers per country (Part H.1 — no AI). country → city → tier. */
    private Map<String, Map<String, Integer>> cityTiers = new LinkedHashMap<>();
  }

  @Data
  public static class Tolerance {
    /**
     * Tiered plan tolerance (PRD Part F): entries are [maxBudgetUsd, tolerancePct, absCapUsd],
     * evaluated in order; the last entry is the enterprise tier.
     */
    private List<double[]> tiers =
        List.of(
            new double[] {10_000, 10, 10_000},
            new double[] {100_000, 7, 10_000},
            new double[] {500_000, 5, 25_000},
            new double[] {Double.MAX_VALUE, 3, 50_000});

    /** Hard ceiling regardless of tier (PRD Part F: never exceed 15% over budget). */
    private double hardCapPct = 15;

    /** Static currency→USD conversion table (interim until a live FX source exists). */
    private Map<String, Double> usdRates =
        Map.of("USD", 1.0, "MYR", 0.22, "SGD", 0.74, "INR", 0.012, "JPY", 0.0064, "IDR", 0.000062);

    private String defaultCurrency = "USD";
  }

  @Data
  public static class Measure {
    /** Defaults to the shared measure URL; overridable independently for v3. */
    private String apiUrl = "";

    private int retryAttempts = 1;
    private int connectTimeoutMs = 5_000;
    private int readTimeoutMs = 30_000;

    /** PRD §5.10 accuracy flag: include dayparts in Measure requests (off = v2 parity). */
    private boolean includeDayparts = false;
  }

  @Data
  public static class Batch {
    private int audienceBatchSize = 1_000;
    private int insertBatchSize = 1_500;
    private int measureBatchSize = 1_500;

    /** Number of completion-percentage milestones written during scoring (v2 optimization). */
    private int progressMilestones = 4;
  }

  @Data
  public static class Concurrency {
    /**
     * Cap on concurrent DB-bound tasks (audience batches, inserts). Protects the shared MongoDB
     * connection pool from unbounded virtual-thread fan-out (v2 weakness W1).
     */
    private int dbParallelism = 8;

    /** Cap on concurrent Measure API batch calls. */
    private int measureParallelism = 4;
  }

  @Data
  public static class Audit {
    /** Persist the raw→normalized→component score audit trail on each result (PRD §5.2). */
    private boolean enabled = true;
  }

  @Data
  public static class Output {
    /** Maximum length of the per-result "why" explanation (PRD §7.1). */
    private int whyMaxLength = 120;

    /** Number of nearest-alternative suggestions returned on NO_MATCHES (PRD §6.6). */
    private int noMatchAlternatives = 5;

    /** Candidate count above which a country-scale run also returns city clusters (AC-12). */
    private int cityClusterThreshold = 500;
  }
}
