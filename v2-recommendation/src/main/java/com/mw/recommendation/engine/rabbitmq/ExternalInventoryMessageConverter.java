package com.mw.recommendation.engine.rabbitmq;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.ExternalInventoryMessageDTO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.geo.GeoJsonLineString;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Component;

/** Converter service to transform external inventory messages to internal Inventory entities */
@Component
@Slf4j
public class ExternalInventoryMessageConverter {

  private static final Pattern POINT_PATTERN =
      Pattern.compile("POINT\\(([\\d.\\-]+)\\s+([\\d.\\-]+)\\)");
  private static final Pattern LINESTRING_PATTERN =
      Pattern.compile("LINESTRING\\(([\\d.\\-\\s,]+)\\)");

  /** Convert external inventory message to internal Inventory entity */
  public Inventory convertToInventory(ExternalInventoryMessageDTO externalMessage) {
    log.debug(
        "Converting external inventory message for referenceId: {}",
        externalMessage.getReferenceId() != null ? externalMessage.getReferenceId() : "unknown");

    Inventory inventory = new Inventory();

    // Core identifiers
    inventory.setInventoryId(externalMessage.getId());
    inventory.setReferenceId(externalMessage.getReferenceId());

    // Basic information
    inventory.setName(externalMessage.getName());
    inventory.setMediaOwnerId(externalMessage.getMediaOwnerId());
    inventory.setMediaOwnerName(externalMessage.getMediaOwnerName());

    // Classification and type from typeName (e.g., "Digital > OOH" -> classification="Digital",
    // type="OOH")
    if (externalMessage.getTypeName() != null) {
      String[] parts = externalMessage.getTypeName().split(">");
      if (parts.length >= 1) {
        inventory.setClassification(parts[0].trim());
      }
      if (parts.length >= 2) {
        inventory.setType(parts[1].trim());
      }
    }

    // Format from displayFormatName
    inventory.setFormat(externalMessage.getDisplayFormatName());

    // Environment
    inventory.setEnvironment(externalMessage.getEnvironment());

    // Size (top-level string from external system)
    inventory.setSize(externalMessage.getSize());

    // Viewing distance
    inventory.setViewingDistance(externalMessage.getViewingDistance());

    // Venue types - convert from List<Venue> to List<String> of venue names
    if (externalMessage.getVenues() != null) {
      inventory.setVenueTypes(
          externalMessage.getVenues().stream()
              .filter(venue -> venue != null) // Filter out null venue elements
              .map(ExternalInventoryMessageDTO.Venue::getName)
              .collect(Collectors.toList()));
      inventory.setVenueTypeIds(
          externalMessage.getVenues().stream()
              .filter(venue -> venue != null) // Filter out null venue elements
              .map(ExternalInventoryMessageDTO.Venue::getTaxonomyId)
              .filter(Objects::nonNull)
              .collect(Collectors.toList()));
    }

    // Archived (inverted from active)
    inventory.setArchived(
        externalMessage.getArchived() != null ? externalMessage.getArchived() : false);
    inventory.setActive(!inventory.getArchived());

    // Location - build from geoms, adminLevel names, and address
    convertLocation(inventory, externalMessage);

    // Location hierarchy
    Inventory.LocationHierarchy locationHierarchy = new Inventory.LocationHierarchy();
    locationHierarchy.setCountryId(externalMessage.getAdminLevel0Id());
    locationHierarchy.setCountryName(externalMessage.getAdminLevel0Name());
    locationHierarchy.setStateId(externalMessage.getAdminLevel1Id());
    locationHierarchy.setStateName(externalMessage.getAdminLevel1Name());
    locationHierarchy.setCityId(externalMessage.getAdminLevel2Id());
    locationHierarchy.setCityName(externalMessage.getAdminLevel2Name());
    inventory.setLocationHierarchy(locationHierarchy);

    // Panels - convert from message panels
    inventory.setPanels(convertPanels(externalMessage.getPanels()));

    // Thumbnail URL
    inventory.setThumbnailUrl(externalMessage.getThumbnailUrl());

    // Operating times - convert from schedule.operatingTimes
    inventory.setOperatingTimes(convertOperatingTimes(externalMessage.getSchedule()));

    // Selling term (leadDays is now part of SellingTerm)
    inventory.setSellingTerm(convertSellingTerm(externalMessage.getSellingTerm()));
    // Note: leadDays is now part of SellingTerm, not a separate field

    // Orientation
    inventory.setOrientation(
        externalMessage.getOrientation() != null
            ? mapOrientation(externalMessage.getOrientation())
            : null);

    // Time zone
    inventory.setTimeZone(externalMessage.getTimeZone());

    // Other fields
    inventory.setRequiresContentApproval(externalMessage.getRequiresContentApproval());
    inventory.setProgrammaticDealTypes(externalMessage.getProgrammaticDealTypes());
    inventory.setCreativeFormats(convertCreativeFormats(externalMessage.getCreativeFormats()));
    inventory.setPrices(convertPrices(externalMessage.getPrices()));
    inventory.setPriceTypes(generatePriceTypes(inventory.getPrices()));

    // DSPs
    if (externalMessage.getDsps() != null) {
      inventory.setDsps(
          externalMessage.getDsps().stream()
              .filter(dsp -> dsp != null) // Filter out null dsp elements
              .map(ExternalInventoryMessageDTO.Dsp::getDspName)
              .collect(Collectors.toList()));
    }

    // Digital fields
    inventory.setDigitalFields(convertDigitalFields(externalMessage.getDigitalFields()));

    // Content exclusions
    inventory.setContentExclusions(
        convertContentExclusions(externalMessage.getContentExclusions()));

    // Medias - convert List<Media> to List<String> of URLs
    if (externalMessage.getMedias() != null) {
      inventory.setMedias(
          externalMessage.getMedias().stream()
              .filter(media -> media != null) // Filter out null media elements
              .map(ExternalInventoryMessageDTO.Media::getUrl)
              .collect(Collectors.toList()));
    }

    // Tags
    inventory.setTags(convertTags(externalMessage.getTags()));

    // Blackouts
    inventory.setBlackouts(convertBlackouts(externalMessage.getBlackouts()));

    // External IDs
    if (externalMessage.getExternalIds() != null) {
      inventory.setExternalIds(
          externalMessage.getExternalIds().stream()
              .filter(e -> e != null) // Filter out null external ID elements
              .map(
                  e ->
                      Inventory.ExternalId.builder()
                          .platform(e.getPlatform())
                          .externalRefId(e.getExternalId())
                          .build())
              .collect(Collectors.toList()));
    }

    // Metadata
    inventory.setLastSyncedAt(LocalDateTime.now());

    log.debug("Successfully converted inventory for referenceId: {}", inventory.getReferenceId());
    return inventory;
  }

  private void convertLocation(Inventory inventory, ExternalInventoryMessageDTO externalMessage) {
    // Parse geoms - can be POINT or LINESTRING
    if (externalMessage.getGeoms() != null && !externalMessage.getGeoms().isEmpty()) {
      String geom = externalMessage.getGeoms().getFirst();

      // Try to parse as POINT first (e.g., "POINT(139.775694 35.71204)")
      Matcher pointMatcher = POINT_PATTERN.matcher(geom);
      if (pointMatcher.find()) {
        double longitude = Double.parseDouble(pointMatcher.group(1));
        double latitude = Double.parseDouble(pointMatcher.group(2));
        inventory.setLocationCoordinates(new GeoJsonPoint(longitude, latitude));
      } else {
        // Try to parse as LINESTRING
        Matcher lineStringMatcher = LINESTRING_PATTERN.matcher(geom);
        if (lineStringMatcher.find()) {
          String coordsStr = lineStringMatcher.group(1);
          List<Point> points = parseLineStringCoordinates(coordsStr);
          if (!points.isEmpty()) {
            inventory.setLocationCoordinates(new GeoJsonLineString(points));
          }
        } else {
          log.warn("Unknown geometry format: {}", geom);
        }
      }
    }

    // Set address
    inventory.setAddress(externalMessage.getAddress());
  }

  /** Parse LINESTRING coordinates string into list of Point */
  private List<Point> parseLineStringCoordinates(String coordsStr) {
    List<Point> points = new ArrayList<>();
    if (coordsStr == null || coordsStr.trim().isEmpty()) {
      return points;
    }

    try {
      String[] pairs = coordsStr.split(",");
      for (String pair : pairs) {
        String[] coords = pair.trim().split("\\s+");
        if (coords.length >= 2) {
          double longitude = Double.parseDouble(coords[0]);
          double latitude = Double.parseDouble(coords[1]);
          points.add(new Point(longitude, latitude));
        }
      }
    } catch (NumberFormatException e) {
      log.warn("Error parsing LINESTRING coordinates: {}", coordsStr, e);
    }

    return points;
  }

  private Inventory.Orientation mapOrientation(String orientation) {
    if (orientation == null) return null;
    try {
      return Inventory.Orientation.valueOf(orientation.toUpperCase());
    } catch (IllegalArgumentException e) {
      log.warn("Unknown orientation: {}", orientation);
      return null;
    }
  }

  private List<Inventory.Panel> convertPanels(
      List<ExternalInventoryMessageDTO.Panel> externalPanels) {
    if (externalPanels == null) {
      return null;
    }

    return externalPanels.stream()
        .filter(ep -> ep != null) // Filter out null panel elements
        .map(
            ep -> {
              // Use size from message if available, otherwise calculate
              Inventory.Size panelSize = null;
              if (ep.getSize() != null) {
                try {
                  panelSize = Inventory.Size.valueOf(ep.getSize().toUpperCase());
                } catch (IllegalArgumentException e) {
                  log.warn("Unknown size value: {}, calculating instead", ep.getSize());
                  panelSize = calculatePanelSize(ep);
                }
              } else {
                panelSize = calculatePanelSize(ep);
              }

              double totalArea = 0.0;
              if (ep.getPhysicalWidth() != null && ep.getPhysicalHeight() != null) {
                totalArea = ep.getPhysicalWidth() * ep.getPhysicalHeight();
                if (ep.getPanelCount() != null) {
                  totalArea *= ep.getPanelCount();
                }
              }

              return Inventory.Panel.builder()
                  .pixelWidth(ep.getPixelWidth())
                  .pixelHeight(ep.getPixelHeight())
                  .physicalWidth(ep.getPhysicalWidth())
                  .physicalHeight(ep.getPhysicalHeight())
                  .panelCount(ep.getPanelCount())
                  .totalArea(totalArea)
                  .size(panelSize)
                  .build();
            })
        .collect(Collectors.toList());
  }

  private Inventory.Size calculatePanelSize(ExternalInventoryMessageDTO.Panel panel) {
    if (panel.getPhysicalWidth() == null || panel.getPhysicalHeight() == null) {
      return null;
    }

    double panelSqft = panel.getPhysicalWidth() * panel.getPhysicalHeight();

    if (panelSqft >= 300) {
      return Inventory.Size.XL;
    } else if (panelSqft >= 100) {
      return Inventory.Size.L;
    } else if (panelSqft >= 30) {
      return Inventory.Size.M;
    } else if (panelSqft >= 5) {
      return Inventory.Size.S;
    } else {
      return Inventory.Size.XS;
    }
  }

  private Map<Inventory.Weekday, List<Inventory.OperatingTime>> convertOperatingTimes(
      ExternalInventoryMessageDTO.Schedule schedule) {
    if (schedule == null || schedule.getOperatingTimes() == null) {
      return null;
    }

    Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimes = new HashMap<>();

    schedule
        .getOperatingTimes()
        .forEach(
            (dayStr, times) -> {
              try {
                int dayInt = Integer.parseInt(dayStr);
                Inventory.Weekday weekday = intToWeekday(dayInt);
                if (weekday != null && times != null) {
                  List<Inventory.OperatingTime> operatingTimeList =
                      times.stream()
                          .filter(ot -> ot != null) // Filter out null operating time elements
                          .map(
                              ot ->
                                  Inventory.OperatingTime.builder()
                                      .start(ot.getStart())
                                      .end(ot.getEnd())
                                      .build())
                          .collect(Collectors.toList());
                  operatingTimes.put(weekday, operatingTimeList);
                }
              } catch (NumberFormatException e) {
                log.warn("Failed to parse day number: {}", dayStr);
              }
            });

    return operatingTimes.isEmpty() ? null : operatingTimes;
  }

  private Inventory.Weekday intToWeekday(int dayInt) {
    return switch (dayInt) {
      case 0 -> Inventory.Weekday.SUNDAY;
      case 1 -> Inventory.Weekday.MONDAY;
      case 2 -> Inventory.Weekday.TUESDAY;
      case 3 -> Inventory.Weekday.WEDNESDAY;
      case 4 -> Inventory.Weekday.THURSDAY;
      case 5 -> Inventory.Weekday.FRIDAY;
      case 6 -> Inventory.Weekday.SATURDAY;
      default -> {
        log.warn("Invalid day number: {}", dayInt);
        yield null;
      }
    };
  }

  private Inventory.SellingTerm convertSellingTerm(
      ExternalInventoryMessageDTO.SellingTerm externalSellingTerm) {
    if (externalSellingTerm == null) {
      return null;
    }

    Inventory.SellingTerm sellingTerm = new Inventory.SellingTerm();
    sellingTerm.setLeadDays(externalSellingTerm.getLeadDays());
    sellingTerm.setMinHours(externalSellingTerm.getMinHours());
    sellingTerm.setMinDays(externalSellingTerm.getMinDays());

    if (externalSellingTerm.getDayPartGroups() != null) {
      Map<String, Inventory.DayPartGroup> dayPartGroups = new HashMap<>();
      externalSellingTerm
          .getDayPartGroups()
          .forEach(
              (key, value) -> {
                dayPartGroups.put(
                    key,
                    Inventory.DayPartGroup.builder()
                        .start(value.getStart())
                        .end(value.getEnd())
                        .build());
              });
      sellingTerm.setDayPartGroups(dayPartGroups);
    }

    return sellingTerm;
  }

  private List<Inventory.CreativeFormat> convertCreativeFormats(
      List<ExternalInventoryMessageDTO.CreativeFormat> externalCreativeFormats) {
    if (externalCreativeFormats == null) {
      return null;
    }

    return externalCreativeFormats.stream()
        .filter(ecf -> ecf != null) // Filter out null creative format elements
        .map(
            ecf ->
                Inventory.CreativeFormat.builder()
                    .format(ecf.getFormat())
                    .creativeType(ecf.getCreativeType())
                    .build())
        .collect(Collectors.toList());
  }

  private List<Inventory.PriceModel> convertPrices(
      List<ExternalInventoryMessageDTO.Price> externalPrices) {
    if (externalPrices == null) {
      return null;
    }

    return externalPrices.stream()
        .filter(ep -> ep != null) // Filter out null price elements
        .map(
            ep ->
                Inventory.PriceModel.builder()
                    .cpm(ep.getCpm())
                    .spot(ep.getCps())
                    .monthly(ep.getMonthly())
                    .daily(ep.getDaily())
                    .weekly(ep.getWeekly())
                    .currency(ep.getCurrency())
                    .durationSeconds(ep.getDurationSeconds())
                    .build())
        .collect(Collectors.toList());
  }

  /**
   * Derive the set of pricing models offered by an inventory from its price list. A type is
   * included only if at least one price element has a present, positive value for it.
   */
  List<String> generatePriceTypes(List<Inventory.PriceModel> prices) {
    List<String> types = new ArrayList<>();
    if (prices == null || prices.isEmpty()) {
      return types;
    }
    if (prices.stream().anyMatch(p -> p != null && p.getCpm() != null && p.getCpm() > 0)) {
      types.add("cpm");
    }
    if (prices.stream().anyMatch(p -> p != null && p.getSpot() != null && p.getSpot() > 0)) {
      types.add("spot");
    }
    if (prices.stream().anyMatch(p -> p != null && p.getMonthly() != null && p.getMonthly() > 0)) {
      types.add("monthly");
    }
    if (prices.stream().anyMatch(p -> p != null && p.getDaily() != null && p.getDaily() > 0)) {
      types.add("daily");
    }
    if (prices.stream().anyMatch(p -> p != null && p.getWeekly() != null && p.getWeekly() > 0)) {
      types.add("weekly");
    }
    return types;
  }

  private Inventory.DigitalFields convertDigitalFields(
      ExternalInventoryMessageDTO.DigitalFields externalDigitalFields) {
    if (externalDigitalFields == null) {
      return null;
    }

    return Inventory.DigitalFields.builder()
        .playerSoftwareId(externalDigitalFields.getPlayerSoftwareId())
        .playerSoftwareName(externalDigitalFields.getPlayerSoftwareName())
        .playerCount(externalDigitalFields.getPlayerCount())
        .spotDuration(externalDigitalFields.getSpotDuration())
        .spotsPerLoop(externalDigitalFields.getSpotsPerLoop())
        .bookingMode(externalDigitalFields.getBookingMode())
        .loopDuration(externalDigitalFields.getLoopDuration())
        .loopsPerHour(externalDigitalFields.getLoopsPerHour())
        .build();
  }

  private List<Inventory.ContentExclusion> convertContentExclusions(
      List<ExternalInventoryMessageDTO.ContentExclusion> externalContentExclusions) {
    if (externalContentExclusions == null) {
      return null;
    }

    return externalContentExclusions.stream()
        .filter(ece -> ece != null) // Filter out null content exclusion elements
        .map(
            ece ->
                Inventory.ContentExclusion.builder()
                    .name(ece.getName())
                    .taxonomyId(ece.getTaxonomyId())
                    .version(ece.getVersion())
                    .build())
        .collect(Collectors.toList());
  }

  private List<Inventory.Blackout> convertBlackouts(
      List<ExternalInventoryMessageDTO.Blackout> externalBlackouts) {
    if (externalBlackouts == null) {
      return null;
    }
    return externalBlackouts.stream()
        .filter(b -> b != null)
        .map(
            b ->
                Inventory.Blackout.builder()
                    .id(b.getId())
                    .reason(b.getReason())
                    .startDate(b.getStartDate() != null ? LocalDate.parse(b.getStartDate()) : null)
                    .endDate(b.getEndDate() != null ? LocalDate.parse(b.getEndDate()) : null)
                    .build())
        .collect(Collectors.toList());
  }

  private List<Inventory.Tag> convertTags(List<ExternalInventoryMessageDTO.Tag> externalTags) {
    if (externalTags == null) {
      return null;
    }

    return externalTags.stream()
        .filter(et -> et != null) // Filter out null tag elements
        .map(
            et ->
                Inventory.Tag.builder()
                    .id(et.getId())
                    .mediaOwnerId(et.getMediaOwnerId())
                    .name(et.getName())
                    .hexColor(et.getHexColor())
                    .build())
        .collect(Collectors.toList());
  }
}
