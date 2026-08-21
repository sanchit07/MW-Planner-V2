package com.mw.recommendation.engine.mapper;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.PaginatedRecommendationResponseDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Maps an {@link Inventory} document to the browse/results {@code RecommendedInventory} shape.
 *
 * <p>This is the single source of truth for the "no scoring" inventory projection: {@code
 * RecommendationService.browseInventories} delegates here, and the CSV import feature reuses it so
 * uploaded inventories are byte-identical to browsed ones. The logic is stateless (no injected
 * collaborators) — it was extracted verbatim from {@code RecommendationService} with one hardening
 * change: {@link #calculateInventoryAdPlays} tolerates null campaign dates (CSV import has none)
 * and returns a null ad-play/cost estimate instead of throwing.
 */
@Slf4j
@Component
public class InventoryItemMapper {

  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

  /** Default flight window (days) used when the caller has no campaign dates (e.g. CSV import). */
  public static final long DEFAULT_TOTAL_DAYS = 30;

  /**
   * Map an inventory to a {@code RecommendedInventory}. {@code startDate}/{@code endDate} may be
   * null (no campaign window) — availability then spans {@code totalDays} and the cost estimate is
   * null.
   */
  public PaginatedRecommendationResponseDTO.RecommendedInventory toRecommendedInventory(
      Inventory inventory, long totalDays, LocalDate startDate, LocalDate endDate) {

    PaginatedRecommendationResponseDTO.InventoryLocation location = null;
    if (inventory.getLocationHierarchy() != null || inventory.getLocationCoordinates() != null) {
      PaginatedRecommendationResponseDTO.InventoryLocation.InventoryLocationBuilder lb =
          PaginatedRecommendationResponseDTO.InventoryLocation.builder();
      if (inventory.getLocationHierarchy() != null) {
        lb.countryId(inventory.getLocationHierarchy().getCountryId())
            .countryName(inventory.getLocationHierarchy().getCountryName())
            .stateId(inventory.getLocationHierarchy().getStateId())
            .stateName(inventory.getLocationHierarchy().getStateName())
            .cityId(inventory.getLocationHierarchy().getCityId())
            .cityName(inventory.getLocationHierarchy().getCityName());
      }
      if (inventory.getLocationCoordinates() != null) {
        lb.locationCoordinates(inventory.getLocationCoordinates());
      }
      location = lb.build();
    }

    String orientationStr =
        inventory.getOrientation() != null ? inventory.getOrientation().name().toLowerCase() : null;

    List<String> sizes = null;
    if (inventory.getPanels() != null && !inventory.getPanels().isEmpty()) {
      sizes =
          inventory.getPanels().stream()
              .filter(p -> p.getSize() != null)
              .map(p -> p.getSize().name())
              .distinct()
              .collect(Collectors.toList());
    }

    PaginatedRecommendationResponseDTO.InventoryDetails details =
        PaginatedRecommendationResponseDTO.InventoryDetails.builder()
            .name(inventory.getName())
            .classification(inventory.getClassification())
            .type(inventory.getType())
            .format(inventory.getFormat())
            .environment(inventory.getEnvironment())
            .venueTypes(inventory.getVenueTypes())
            .orientation(orientationStr)
            .sizes(sizes)
            .resolutions(deriveResolutions(inventory))
            .durations(deriveDurations(inventory))
            .mediaOwnerId(inventory.getMediaOwnerId())
            .mediaOwnerName(inventory.getMediaOwnerName())
            .address(inventory.getAddress())
            .location(location)
            .cpmRate(
                inventory.getPrices() != null && !inventory.getPrices().isEmpty()
                    ? inventory.getPrices().getFirst().getCpm()
                    : null)
            .spotRate(
                inventory.getPrices() != null && !inventory.getPrices().isEmpty()
                    ? inventory.getPrices().getFirst().getSpot()
                    : null)
            .programmaticDealTypes(
                inventory.getProgrammaticDealTypes() != null
                    ? inventory.getProgrammaticDealTypes().stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.toList())
                    : null)
            .venueTypeIds(inventory.getVenueTypeIds())
            .resolution(deriveResolution(inventory))
            .thumbnailUrl(inventory.getThumbnailUrl())
            .timeZone(inventory.getTimeZone())
            .bookingMode(
                inventory.getDigitalFields() != null
                    ? inventory.getDigitalFields().getBookingMode()
                    : null)
            .spotDuration(
                inventory.getDigitalFields() != null
                    ? inventory.getDigitalFields().getSpotDuration()
                    : null)
            .spotsPerLoop(
                inventory.getDigitalFields() != null
                    ? inventory.getDigitalFields().getSpotsPerLoop()
                    : null)
            .loopDuration(
                inventory.getDigitalFields() != null
                    ? inventory.getDigitalFields().getLoopDuration()
                    : null)
            .loopsPerHour(
                inventory.getDigitalFields() != null
                    ? inventory.getDigitalFields().getLoopsPerHour()
                    : null)
            .spotsPerHour(calcSpotsPerHour(inventory))
            .playerCount(
                inventory.getDigitalFields() != null
                    ? inventory.getDigitalFields().getPlayerCount()
                    : null)
            .playerSoftwareId(
                inventory.getDigitalFields() != null
                    ? inventory.getDigitalFields().getPlayerSoftwareId()
                    : null)
            .playerSoftwareName(
                inventory.getDigitalFields() != null
                    ? inventory.getDigitalFields().getPlayerSoftwareName()
                    : null)
            .externalRefIds(mapExternalRefs(inventory))
            .deviceId(extractDeviceId(inventory))
            .operatingTimes(inventory.getOperatingTimes())
            .sellingTerm(inventory.getSellingTerm())
            .panels(inventory.getPanels())
            .size(inventory.getSize())
            .inventoryCluster(inventory.getInventoryCluster())
            .build();

    Long totalAdPlays = calculateInventoryAdPlays(inventory, startDate, endDate);

    BigDecimal estimatedCost = null;
    String currency = null;
    Double monthlyRate = null;
    Double dailyRate = null;
    if (inventory.getPrices() != null && !inventory.getPrices().isEmpty()) {
      Inventory.PriceModel price = inventory.getPrices().getFirst();
      currency = price.getCurrency();
      monthlyRate = price.getMonthly();
      dailyRate = price.getDaily();
      if (totalAdPlays != null
          && totalAdPlays > 0
          && price.getSpot() != null
          && price.getSpot() > 0) {
        estimatedCost =
            BigDecimal.valueOf(price.getSpot())
                .multiply(BigDecimal.valueOf(totalAdPlays))
                .setScale(2, RoundingMode.HALF_UP);
      } else if (price.getCpm() != null && price.getCpm() > 0 && totalAdPlays != null) {
        long roughImpressions = totalAdPlays * 100L;
        estimatedCost =
            BigDecimal.valueOf(price.getCpm())
                .divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(roughImpressions))
                .setScale(2, RoundingMode.HALF_UP);
      } else if ((price.getMonthly() != null && price.getMonthly() > 0)
          || (price.getDaily() != null && price.getDaily() > 0)) {
        // Classic rate-card: whole-months@monthly + remainder-days@daily (fallback monthly/30).
        // Reuses the same helper as the scored path so browse and Fetch-Available agree.
        estimatedCost =
            com.mw.recommendation.engine.util.ClassicPricing.estimatedCost(price, totalDays);
      }
    }

    PaginatedRecommendationResponseDTO.CostEstimate cost =
        PaginatedRecommendationResponseDTO.CostEstimate.builder()
            .estimatedCost(estimatedCost)
            .currency(currency)
            .totalAdPlays(totalAdPlays)
            // Raw rate-card rates so the client can recompute cost as flight days change.
            .monthly(monthlyRate)
            .daily(dailyRate)
            .build();

    return PaginatedRecommendationResponseDTO.RecommendedInventory.builder()
        .inventoryId(inventory.getInventoryId())
        .referenceId(inventory.getReferenceId())
        .name(inventory.getName())
        .inventoryDetails(details)
        .availability(
            PaginatedRecommendationResponseDTO.AvailabilitySummary.builder()
                .totalDays((int) totalDays)
                .availableDays((int) totalDays)
                .availabilityPercentage(100.0)
                .allAvailable(true)
                .build())
        .cost(cost)
        .build();
  }

  private Long calculateInventoryAdPlays(
      Inventory inventory, LocalDate startDate, LocalDate endDate) {
    // CSV import has no campaign window — no ad-play/cost estimate is possible.
    if (startDate == null || endDate == null) {
      return null;
    }

    Inventory.DigitalFields digitalFields = inventory.getDigitalFields();
    if (digitalFields == null
        || digitalFields.getSpotDuration() == null
        || digitalFields.getSpotDuration() == 0
        || digitalFields.getSpotsPerLoop() == null
        || digitalFields.getSpotsPerLoop() == 0) {
      return null;
    }

    int loopsPerHour = (3600 / digitalFields.getSpotDuration()) / digitalFields.getSpotsPerLoop();

    long totalHourSlots = 0;
    if (inventory.getOperatingTimes() != null && !inventory.getOperatingTimes().isEmpty()) {
      for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
        Inventory.Weekday weekday = getWeekday(date);
        List<Inventory.OperatingTime> times = inventory.getOperatingTimes().get(weekday);
        if (times != null && !times.isEmpty()) {
          for (Inventory.OperatingTime time : times) {
            try {
              LocalTime start = LocalTime.parse(time.getStart(), TIME_FORMAT);
              LocalTime end = LocalTime.parse(time.getEnd(), TIME_FORMAT);
              int startH = start.getHour();
              int endH = end.getHour();
              if (startH <= endH) {
                totalHourSlots += endH - startH + 1;
              } else {
                totalHourSlots += (24 - startH) + endH + 1;
              }
            } catch (Exception e) {
              log.warn(
                  "Error parsing operating time for inventory {}: {}",
                  inventory.getReferenceId(),
                  e.getMessage());
            }
          }
        }
      }
    }

    long totalAdPlays = loopsPerHour * totalHourSlots;
    return totalAdPlays > 0 ? totalAdPlays : null;
  }

  private Inventory.Weekday getWeekday(LocalDate date) {
    return switch (date.getDayOfWeek()) {
      case MONDAY -> Inventory.Weekday.MONDAY;
      case TUESDAY -> Inventory.Weekday.TUESDAY;
      case WEDNESDAY -> Inventory.Weekday.WEDNESDAY;
      case THURSDAY -> Inventory.Weekday.THURSDAY;
      case FRIDAY -> Inventory.Weekday.FRIDAY;
      case SATURDAY -> Inventory.Weekday.SATURDAY;
      case SUNDAY -> Inventory.Weekday.SUNDAY;
    };
  }

  private String deriveResolution(Inventory inventory) {
    if (inventory.getPanels() == null || inventory.getPanels().isEmpty()) return null;
    Inventory.Panel first = inventory.getPanels().getFirst();
    if (first.getPixelWidth() != null && first.getPixelHeight() != null)
      return first.getPixelWidth() + "x" + first.getPixelHeight();
    return null;
  }

  private List<String> deriveResolutions(Inventory inventory) {
    if (inventory.getPanels() == null || inventory.getPanels().isEmpty()) return null;
    return inventory.getPanels().stream()
        .filter(p -> p.getPixelWidth() != null && p.getPixelHeight() != null)
        .map(p -> p.getPixelWidth() + "x" + p.getPixelHeight())
        .distinct()
        .collect(Collectors.toList());
  }

  private List<Integer> deriveDurations(Inventory inventory) {
    if (inventory.getPrices() == null || inventory.getPrices().isEmpty()) return null;
    return inventory.getPrices().stream()
        .map(Inventory.PriceModel::getDurationSeconds)
        .filter(duration -> duration != null)
        .distinct()
        .sorted()
        .collect(Collectors.toList());
  }

  private Integer calcSpotsPerHour(Inventory inventory) {
    Inventory.DigitalFields df = inventory.getDigitalFields();
    if (df == null
        || df.getSpotDuration() == null
        || df.getSpotDuration() == 0
        || df.getSpotsPerLoop() == null
        || df.getSpotsPerLoop() == 0) return null;
    return (3600 / df.getSpotDuration()) / df.getSpotsPerLoop();
  }

  private List<PaginatedRecommendationResponseDTO.ExternalRef> mapExternalRefs(
      Inventory inventory) {
    if (inventory.getExternalIds() == null || inventory.getExternalIds().isEmpty()) return null;
    return inventory.getExternalIds().stream()
        .map(
            e ->
                PaginatedRecommendationResponseDTO.ExternalRef.builder()
                    .source(e.getPlatform())
                    .externalRefId(e.getExternalRefId())
                    .build())
        .collect(Collectors.toList());
  }

  private String extractDeviceId(Inventory inventory) {
    if (inventory.getExternalIds() == null) return null;
    return inventory.getExternalIds().stream()
        .filter(e -> e.getPlatform() != null && e.getPlatform().equalsIgnoreCase("CMS"))
        .map(Inventory.ExternalId::getExternalRefId)
        .findFirst()
        .orElse(null);
  }
}
