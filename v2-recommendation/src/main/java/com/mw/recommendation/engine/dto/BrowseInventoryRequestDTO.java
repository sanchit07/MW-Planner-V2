package com.mw.recommendation.engine.dto;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.enums.ProgrammaticDealType;
import com.mw.recommendation.engine.enums.ProgrammaticSupport;
import com.mw.recommendation.engine.util.InventoryAvailabilityUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for browsing inventories without recommendation scoring. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrowseInventoryRequestDTO {

  @NotBlank(message = "Country is required")
  private String country;

  @NotNull(message = "Start date is required")
  private LocalDate startDate;

  @NotNull(message = "End date is required")
  private LocalDate endDate;

  private String productId;
  private String companyId;
  private RecommendationRequestDTO.GeographyTargeting geographyTargeting;
  private RecommendationRequestDTO.AudienceTargeting audienceTargeting;
  private List<String> excludedInventoryIds;
  private List<String> mediaOwnerIds;
  private List<String> classifications;

  // Additional filter fields matching getRecommendationResults API
  private List<String> types;
  private List<String> formats;
  private List<String> environments;
  private List<String> venueTypes;
  private List<Inventory.Size> sizes;

  /**
   * The line item's selected spot duration(s) (seconds, e.g. [15]). EXACT-matched ($in) against the
   * screen's physical slot length {@code digitalFields.spotDuration} so browse ("View All
   * Inventories") lists only screens whose slot equals the selected duration — the same filter the
   * recommendation run applies. Digital-only (Classic panels carry no spotDuration).
   */
  private List<Integer> durations;

  private List<String> bookingMode;
  private ProgrammaticSupport programmaticSupport;
  private List<ProgrammaticDealType> dealTypes;
  private List<String> mediaOwnerNames;

  /**
   * Inclusive campaign duration in days (Jan 1 -> Jan 3 = 3). Returns null when either date is
   * missing, in which case the minDays availability filter is not applied. An inverted range
   * (endDate before startDate) is clamped to 1 rather than producing a negative duration.
   */
  public Long getDurationDays() {
    return InventoryAvailabilityUtils.inclusiveDurationDays(startDate, endDate);
  }
}
