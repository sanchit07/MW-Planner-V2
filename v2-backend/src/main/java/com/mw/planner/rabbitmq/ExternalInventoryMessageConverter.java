package com.mw.planner.rabbitmq;

import com.mw.planner.domain.Inventory;
import com.mw.planner.dto.ExternalInventoryMessageDTO;
import java.util.*;
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
        externalMessage.getReferenceId() != null ? externalMessage.getReferenceId() : "null");

    Inventory inventory = new Inventory();

    // Basic information
    inventory.setName(externalMessage.getName());
    inventory.setExternalId(externalMessage.getId());
    inventory.setReferenceId(externalMessage.getReferenceId());
    inventory.setSize(externalMessage.getSize());
    inventory.setExternalIds(
        externalMessage.getExternalIds() != null && !externalMessage.getExternalIds().isEmpty()
            ? externalMessage.getExternalIds()
            : null);
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

    // Environment (renamed from category)
    inventory.setEnvironment(externalMessage.getEnvironment());

    // Viewing distance
    inventory.setViewingDistance(externalMessage.getViewingDistance());

    // Venue type - convert from List<Venue> to List<String> of venue names and taxonomy IDs
    if (externalMessage.getVenues() != null) {
      inventory.setVenueType(
          externalMessage.getVenues().stream()
              .map(ExternalInventoryMessageDTO.Venue::getName)
              .filter(java.util.Objects::nonNull)
              .collect(Collectors.toList()));
      inventory.setVenueTypeIds(
          externalMessage.getVenues().stream()
              .map(ExternalInventoryMessageDTO.Venue::getTaxonomyId)
              .filter(java.util.Objects::nonNull)
              .collect(Collectors.toList()));
    }

    // Archived (inverted from active)
    inventory.setArchived(
        externalMessage.getArchived() != null ? externalMessage.getArchived() : false);

    // Location - build from geoms, adminLevel names, and address
    inventory.setLocation(convertLocation(externalMessage));

    // Panels - convert from message panels (with size calculated for each panel)
    inventory.setPanels(convertPanels(externalMessage.getPanels()));

    // Media owner ID (will be set/validated in processing service)
    inventory.setMediaOwnerId(externalMessage.getMediaOwnerId());

    // Thumbnail URL
    inventory.setThumbnailUrl(externalMessage.getThumbnailUrl());

    // Operating times - convert from schedule.operatingTimes
    inventory.setOperatingTimes(convertOperatingTimes(externalMessage.getSchedule()));

    // Selling term - new structure
    inventory.setSellingTerm(convertSellingTerm(externalMessage.getSellingTerm()));

    // New fields
    inventory.setOrientation(
        externalMessage.getOrientation() != null
            ? mapOrientation(externalMessage.getOrientation())
            : null);
    inventory.setTimeZone(externalMessage.getTimeZone());
    inventory.setRequiresContentApproval(externalMessage.getRequiresContentApproval());
    inventory.setProgrammaticDealTypes(externalMessage.getProgrammaticDealTypes());
    inventory.setCreativeFormats(convertCreativeFormats(externalMessage.getCreativeFormats()));
    inventory.setPrices(convertPrices(externalMessage.getPrices()));
    inventory.setPriceTypes(generatePriceTypes(inventory.getPrices()));
    inventory.setDigitalFields(convertDigitalFields(externalMessage.getDigitalFields()));
    inventory.setClassicFields(convertClassicFields(externalMessage.getClassicFields()));
    inventory.setTransitFields(convertTransitFields(externalMessage.getTransitFields()));

    // Content exclusions
    inventory.setContentExclusions(
        convertContentExclusions(externalMessage.getContentExclusions()));

    // Medias - convert List<Media> to List<String> of URLs
    if (externalMessage.getMedias() != null) {
      inventory.setMedias(
          externalMessage.getMedias().stream()
              .map(ExternalInventoryMessageDTO.Media::getUrl)
              .collect(Collectors.toList()));
    }

    // Tags
    inventory.setTags(convertTags(externalMessage.getTags()));

    log.debug("Successfully converted inventory for referenceId: {}", inventory.getReferenceId());
    return inventory;
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

  private Inventory.Location convertLocation(ExternalInventoryMessageDTO externalMessage) {
    Inventory.Location location = new Inventory.Location();

    // Parse geoms - can be POINT or LINESTRING
    if (externalMessage.getGeoms() != null && !externalMessage.getGeoms().isEmpty()) {
      String geom = externalMessage.getGeoms().getFirst();

      // Try to parse as POINT first (e.g., "POINT(139.775694 35.71204)")
      Matcher pointMatcher = POINT_PATTERN.matcher(geom);
      if (pointMatcher.find()) {
        double longitude = Double.parseDouble(pointMatcher.group(1));
        double latitude = Double.parseDouble(pointMatcher.group(2));
        location.setLocationCoordinates(new GeoJsonPoint(longitude, latitude));
      } else {
        // Try to parse as LINESTRING (e.g., "LINESTRING(139.775694 35.71204, 139.776 35.713)")
        Matcher lineStringMatcher = LINESTRING_PATTERN.matcher(geom);
        if (lineStringMatcher.find()) {
          String coordsStr = lineStringMatcher.group(1);
          List<Point> points = parseLineStringCoordinates(coordsStr);
          if (!points.isEmpty()) {
            // GeoJsonLineString constructor takes List<Point>
            location.setLocationCoordinates(new GeoJsonLineString(points));
          }
        } else {
          log.warn("Unknown geometry format: {}", geom);
        }
      }
    }

    // Set address information from new fields
    location.setAddress(externalMessage.getAddress());
    location.setCountry(externalMessage.getAdminLevel0Name());
    location.setState(externalMessage.getAdminLevel1Name());
    location.setCity(externalMessage.getAdminLevel2Name());

    return location;
  }

  /** Parse LINESTRING coordinates string into list of Point. Format: "lng1 lat1, lng2 lat2, ..." */
  private List<Point> parseLineStringCoordinates(String coordsStr) {
    List<Point> points = new ArrayList<>();
    if (coordsStr == null || coordsStr.trim().isEmpty()) {
      return points;
    }

    try {
      // Split by comma to get individual coordinate pairs
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

  private List<Inventory.Panel> convertPanels(
      List<ExternalInventoryMessageDTO.Panel> externalPanels) {
    if (externalPanels == null) {
      return null;
    }

    return externalPanels.stream()
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

              return Inventory.Panel.builder()
                  .pixelWidth(ep.getPixelWidth())
                  .pixelHeight(ep.getPixelHeight())
                  .physicalWidth(ep.getPhysicalWidth())
                  .physicalHeight(ep.getPhysicalHeight())
                  .panelCount(ep.getPanelCount())
                  .unit("Feet") // Default unit
                  .size(panelSize)
                  .build();
            })
        .collect(Collectors.toList());
  }

  /**
   * Calculate size for a single panel based on its sqft. Size is calculated per panel (not
   * multiplied by panelCount).
   */
  private Inventory.Size calculatePanelSize(ExternalInventoryMessageDTO.Panel panel) {
    if (panel.getPhysicalWidth() == null || panel.getPhysicalHeight() == null) {
      return null;
    }

    // Calculate sqft for this panel (single panel, not multiplied by count)
    double panelSqft = panel.getPhysicalWidth() * panel.getPhysicalHeight();

    // Determine size based on sqft
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
    // 0 = Sunday, 1 = Monday, ..., 6 = Saturday
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

    // Convert dayPartGroups
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
        .map(
            ecf ->
                Inventory.CreativeFormat.builder()
                    .format(ecf.getFormat())
                    .creativeType(ecf.getCreativeType())
                    .build())
        .collect(Collectors.toList());
  }

  private List<Inventory.Price> convertPrices(
      List<ExternalInventoryMessageDTO.Price> externalPrices) {
    if (externalPrices == null) {
      return null;
    }

    // return externalPrices.stream()
    //     .map(ep -> Inventory.Price.builder().cpm(ep.getCpm()).spot(ep.getCps()).build())
    //     .collect(Collectors.toList());
    return externalPrices.stream()
        .filter(ep -> ep != null) // Filter out null price elements
        .map(
            ep ->
                Inventory.Price.builder()
                    .cpm(ep.getCpm())
                    .spot(ep.getCps())
                    .monthly(ep.getMonthly())
                    .currency(ep.getCurrency())
                    .durationSeconds(ep.getDurationSeconds())
                    .build())
        .collect(Collectors.toList());
  }

  /**
   * Derive the internal-only {@code priceTypes} list from converted prices. Includes a type when
   * any price element has a present (non-null) and strictly positive value for that field.
   * Evaluated in fixed order: cpm, spot, monthly. Always returns a non-null list (empty when
   * nothing qualifies).
   */
  private List<String> generatePriceTypes(List<Inventory.Price> prices) {
    List<String> types = new ArrayList<>();
    if (prices == null || prices.isEmpty()) {
      return types;
    }
    if (prices.stream().anyMatch(p -> p != null && isPositive(p.getCpm()))) {
      types.add("cpm");
    }
    if (prices.stream().anyMatch(p -> p != null && isPositive(p.getSpot()))) {
      types.add("spot");
    }
    if (prices.stream().anyMatch(p -> p != null && isPositive(p.getMonthly()))) {
      types.add("monthly");
    }
    return types;
  }

  private boolean isPositive(Double value) {
    return value != null && value > 0;
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

  private Inventory.ClassicFields convertClassicFields(
      ExternalInventoryMessageDTO.ClassicFields externalClassicFields) {
    if (externalClassicFields == null) {
      return null;
    }

    return Inventory.ClassicFields.builder()
        .illuminated(externalClassicFields.getIlluminated())
        .build();
  }

  private Inventory.TransitFields convertTransitFields(
      ExternalInventoryMessageDTO.TransitFields externalTransitFields) {
    if (externalTransitFields == null) {
      return null;
    }

    return Inventory.TransitFields.builder()
        .routeId(externalTransitFields.getRouteId())
        .routeName(externalTransitFields.getRouteName())
        .build();
  }

  private List<Inventory.ContentExclusion> convertContentExclusions(
      List<ExternalInventoryMessageDTO.ContentExclusion> externalContentExclusions) {
    if (externalContentExclusions == null) {
      return null;
    }

    return externalContentExclusions.stream()
        .map(
            ece ->
                Inventory.ContentExclusion.builder()
                    .name(ece.getName())
                    .taxonomyId(ece.getTaxonomyId())
                    .version(ece.getVersion())
                    .build())
        .collect(Collectors.toList());
  }

  private List<Inventory.Tag> convertTags(List<ExternalInventoryMessageDTO.Tag> externalTags) {
    if (externalTags == null) {
      return null;
    }

    return externalTags.stream()
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
