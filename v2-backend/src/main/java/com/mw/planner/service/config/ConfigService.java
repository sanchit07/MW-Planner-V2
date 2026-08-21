package com.mw.planner.service.config;

import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.Demographics;
import com.mw.planner.dto.*;
import com.mw.planner.enums.DemographicsType;
import com.mw.planner.repository.DemographicsRepository;
import com.mw.planner.service.MessageService;
import com.mw.planner.service.VenuesService;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

  private final DemographicsRepository demographicsRepository;
  private final VenuesService venuesService;
  private final DefaultConfigurationService defaultConfigurationService;
  private final MessageService messageService;

  /** Get complete configuration data including demographics and planner status */
  public Map<String, Object> getConfigurationData(Locale locale) {
    log.info("Fetching config data, locale={}", locale);

    DemographicsGroupedResponseDTO demographics = getGroupedDemographics(locale);

    return Map.of("demographics", demographics, "campaign_status", Campaign.Status.values());
  }

  /** Get grouped demographics for API response with granular fallback to defaults */
  public DemographicsGroupedResponseDTO getGroupedDemographics(Locale locale) {
    log.debug("Fetching grouped demographics from database");

    // Fetch demographics and handle null/empty safely
    var allDemographics =
        Optional.ofNullable(demographicsRepository.findAll()).orElse(Collections.emptyList());

    var grouped = groupDemographics(allDemographics);
    var defaults = defaultConfigurationService.getDefaultDemographics();

    // Merge defaults where needed
    var mergedGroups = mergeWithDefaults(grouped, defaults);

    var venues = fetchVenuesWithFallback();

    DemographicsGroupedResponseDTO response = buildResponse(mergedGroups, venues);
    return applyTranslations(response, locale);
  }

  /** Group demographics by their type using EnumMap and Stream API. */
  private EnumMap<DemographicsType, List<DemographicItemDTO>> groupDemographics(
      List<Demographics> all) {
    return all.stream()
        .collect(
            Collectors.groupingBy(
                Demographics::getDemoType,
                () -> new EnumMap<>(DemographicsType.class),
                Collectors.mapping(
                    demo ->
                        new DemographicItemDTO(
                            demo.getDemoKey(), demo.getName(), demo.getDescription()),
                    Collectors.toList())));
  }

  /** Merge fetched demographics with default ones where data is missing. */
  private EnumMap<DemographicsType, List<DemographicItemDTO>> mergeWithDefaults(
      EnumMap<DemographicsType, List<DemographicItemDTO>> grouped,
      DemographicsGroupedResponseDTO defaults) {

    var merged = new EnumMap<DemographicsType, List<DemographicItemDTO>>(DemographicsType.class);

    for (var type : DemographicsType.values()) {
      var items = grouped.getOrDefault(type, getDefaultList(type, defaults));
      if (items == null || items.isEmpty()) {
        log.debug("{} demographics empty, using default values", type);
        items = getDefaultList(type, defaults);
      }
      merged.put(type, items);
    }

    return merged;
  }

  /** Fetch hierarchical venues; fallback to default if empty or null. */
  private List<VenueItemDTO> fetchVenuesWithFallback() {
    return Optional.ofNullable(venuesService.getHierarchicalVenues())
        .filter(list -> !list.isEmpty())
        .orElseGet(
            () -> {
              log.debug("No venues found, using default venues");
              return defaultConfigurationService.getDefaultVenues();
            });
  }

  /** Build final response DTO. */
  private DemographicsGroupedResponseDTO buildResponse(
      EnumMap<DemographicsType, List<DemographicItemDTO>> grouped, List<VenueItemDTO> venues) {

    return new DemographicsGroupedResponseDTO(
        grouped.getOrDefault(DemographicsType.AGE, List.of()),
        grouped.getOrDefault(DemographicsType.GENDER, List.of()),
        grouped.getOrDefault(DemographicsType.INCOME, List.of()),
        grouped.getOrDefault(DemographicsType.INTEREST, List.of()),
        grouped.getOrDefault(DemographicsType.BEHAVIOR, List.of()),
        venues);
  }

  /** Get default demographic list for given type. */
  private List<DemographicItemDTO> getDefaultList(
      DemographicsType type, DemographicsGroupedResponseDTO defaults) {
    return switch (type) {
      case AGE -> defaults.getAge();
      case GENDER -> defaults.getGender();
      case INCOME -> defaults.getIncome();
      case INTEREST -> defaults.getInterests();
      case BEHAVIOR -> defaults.getBehavior();
    };
  }

  public List<BrandIabCategory> getBrandCategoriesData(Locale locale) {
    return defaultConfigurationService.getAllIabMappings().stream()
        .map(
            c ->
                isEnglish(locale)
                    ? c
                    : BrandIabCategory.builder()
                        .code(c.code())
                        .name(t("config.iab." + c.code() + ".name", locale, c.name()))
                        .build())
        .toList();
  }

  private boolean isEnglish(Locale locale) {
    return locale == null || Locale.ENGLISH.getLanguage().equals(locale.getLanguage());
  }

  private String t(String key, Locale locale, String fallback) {
    String result = messageService.getMessage(key, locale);
    if (key.equals(result)) {
      log.warn("Missing translation: key={}, locale={}", key, locale);
      return fallback;
    }
    return result;
  }

  private static String normKey(String demoKey) {
    return demoKey
        .toLowerCase(Locale.ROOT)
        .replace("& ", "and_")
        .replace("&", "and")
        .replace(" ", "_")
        .replace("+", "plus");
  }

  private DemographicItemDTO translateDemoItem(DemographicItemDTO item, Locale locale) {
    String base = "config.demographics." + normKey(item.getDemoKey());
    return new DemographicItemDTO(
        item.getDemoKey(),
        t(base + ".name", locale, item.getName()),
        t(base + ".description", locale, item.getDescription()));
  }

  private List<DemographicItemDTO> translateDemoItems(
      List<DemographicItemDTO> items, Locale locale) {
    if (items == null) return null;
    return items.stream().map(item -> translateDemoItem(item, locale)).toList();
  }

  private VenueItemDTO translateVenue(VenueItemDTO venue, Locale locale) {
    return new VenueItemDTO(
        venue.getEnumerationId(),
        venue.getTier(),
        t("config.venue." + venue.getEnumerationId() + ".name", locale, venue.getName()),
        t(
            "config.venue." + venue.getEnumerationId() + ".definition",
            locale,
            venue.getDefinition()),
        venue.getStringValue(),
        translateVenues(venue.getChildren(), locale));
  }

  private List<VenueItemDTO> translateVenues(List<VenueItemDTO> venues, Locale locale) {
    if (venues == null) return null;
    return venues.stream().map(v -> translateVenue(v, locale)).toList();
  }

  private DemographicsGroupedResponseDTO applyTranslations(
      DemographicsGroupedResponseDTO dto, Locale locale) {
    if (isEnglish(locale)) return dto;
    return new DemographicsGroupedResponseDTO(
        translateDemoItems(dto.getAge(), locale),
        translateDemoItems(dto.getGender(), locale),
        translateDemoItems(dto.getIncome(), locale),
        translateDemoItems(dto.getInterests(), locale),
        translateDemoItems(dto.getBehavior(), locale),
        translateVenues(dto.getVenues(), locale));
  }
}
