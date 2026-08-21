package com.mw.recommendation.engine.dto.csv;

import java.util.Arrays;
import java.util.List;

/**
 * Optional line-item context a caller passes to CSV verify/import so the engine can flag resolved
 * inventories that don't fit the line item. The engine has no line-item state, so all of this comes
 * from the caller (the frontend). Any null/blank field disables that check.
 *
 * @param country matched (case-insensitive) against {@code Inventory.locationHierarchy.countryName}
 * @param classification matched (case-insensitive) against {@code Inventory.classification} (e.g.
 *     "Digital"/"Classic")
 * @param mediaOwnerId matched (exact) against {@code Inventory.mediaOwnerId}
 * @param programmaticSupport "YES" requires the inventory to offer at least one programmatic deal
 *     type; "NO"/"ALL"/null apply no programmatic check (direct line items pass null)
 * @param dealType the line item's programmatic deal type (e.g. "GUARANTEED"). When {@code
 *     programmaticSupport} is "YES" and this is set, the inventory must offer THIS specific deal
 *     type (case-insensitive) — not merely any programmatic type
 * @param creativeType line item creative type mapped to the inventory taxonomy ("video"/"image"
 *     /"audio"). Matched (case-insensitive) against any {@code
 *     Inventory.creativeFormats.creativeType}
 * @param adDuration line item ad duration in seconds (as a string). Matched against any {@code
 *     Inventory.prices.durationSeconds} (the selling terms, same field browse filters on)
 * @param resolutions line item resolution(s) as "WxH" (e.g. "1920x1080"). Matched (OR) against any
 *     {@code Inventory.panels} composed as "{pixelWidth}x{pixelHeight}" — inventory passes if it
 *     supports <em>any</em> of the requested resolutions
 */
public record CsvMatchCriteria(
    String country,
    String classification,
    String mediaOwnerId,
    String programmaticSupport,
    String dealType,
    String creativeType,
    String adDuration,
    List<String> resolutions) {

  /** Backward-compatible constructor — country/classification/mediaOwner/programmatic only. */
  public CsvMatchCriteria(
      String country, String classification, String mediaOwnerId, String programmaticSupport) {
    this(country, classification, mediaOwnerId, programmaticSupport, null, null, null, null);
  }

  /** Backward-compatible constructor — adds the specific programmatic deal type. */
  public CsvMatchCriteria(
      String country,
      String classification,
      String mediaOwnerId,
      String programmaticSupport,
      String dealType) {
    this(country, classification, mediaOwnerId, programmaticSupport, dealType, null, null, null);
  }

  /**
   * No criteria — every resolved inventory passes (existence/country/etc. still apply upstream).
   */
  public static CsvMatchCriteria none() {
    return new CsvMatchCriteria(null, null, null, null, null, null, null, null);
  }

  /**
   * Parse a single form/query value into resolution list. Accepts one value ({@code "1920x1080"})
   * or a comma-separated list ({@code "1920x1080,720x1280"}). Blank / null → null (no resolution
   * check).
   */
  public static List<String> parseResolutions(String resolution) {
    if (resolution == null || resolution.isBlank()) {
      return null;
    }
    List<String> parsed =
        Arrays.stream(resolution.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    return parsed.isEmpty() ? null : parsed;
  }
}
