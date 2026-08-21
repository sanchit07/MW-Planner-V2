package com.mw.recommendation.engine.dto;

import com.mw.recommendation.engine.enums.ProgrammaticSupport;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Optional inventory-attribute filters applied at the RECOMMENDATIONS (scored) fetch, alongside
 * {@code durations}. Each is EXACT-matched against a real field on the {@code inventories}
 * collection so the run only scores screens matching the line item's selection. All null/empty by
 * default — a filter adds a pipeline stage only when the user actually picked a value.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAttributeFilters {

  /** Inventory Format → top-level {@code format} (e.g. "ATM Screen", from displayFormatName). */
  private List<String> formats;

  /** Resolution as "WxH" strings (e.g. "1920x1080") → matched against a {@code panels} entry. */
  private List<String> resolutions;

  /** Creative Type → {@code creativeFormats.creativeType} (e.g. "video", "image", "audio"). */
  private List<String> creativeTypes;

  /** DSP → {@code dsps} (e.g. "LMX-ECOMMERCE"). */
  private List<String> dsps;

  /**
   * Purchase type for programmatic → {@code programmaticDealTypes} (lowercased on match, e.g.
   * "guaranteed", "preferred_deal"). Raw strings (not the enum) so an unknown token never fails the
   * whole request — it simply matches nothing.
   */
  private List<String> dealTypes;

  /**
   * Programmatic support toggle: YES = inventory must offer ANY programmatic deal type, NO = it
   * must offer none, ALL/null = no filter. Broader than {@link #dealTypes} (which requires a
   * SPECIFIC deal type) — useful when the deal-type value and the support flag disagree.
   */
  private ProgrammaticSupport programmaticSupport;

  /** Inventory cluster grouping → top-level {@code inventoryCluster} array (match-any, $in). */
  private List<String> inventoryCluster;

  /** True when every filter is null/empty (no stage should be added). */
  public boolean isEmpty() {
    return isBlank(formats)
        && isBlank(resolutions)
        && isBlank(creativeTypes)
        && isBlank(dsps)
        && isBlank(dealTypes)
        && isBlank(inventoryCluster)
        && (programmaticSupport == null || programmaticSupport == ProgrammaticSupport.ALL);
  }

  private static boolean isBlank(List<?> list) {
    return list == null || list.isEmpty();
  }
}
