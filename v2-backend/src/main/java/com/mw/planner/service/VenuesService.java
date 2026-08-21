package com.mw.planner.service;

import com.mw.planner.domain.Venues;
import com.mw.planner.dto.VenueItemDTO;
import com.mw.planner.repository.VenuesRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VenuesService {

  private final VenuesRepository venuesRepository;
  private final MessageService messageService;

  /**
   * Returns a flat map of stringValue (slug) → display name for all venues. Cached in Redis — used
   * for resolving campaign venueType slugs to inventory display names at filter time.
   */
  @Cacheable(value = "venues", key = "'slugToNameMap'")
  public Map<String, String> getVenueSlugToNameMap() {
    log.debug("Building venue slug-to-name map from database");
    return venuesRepository.findAll().stream()
        .filter(v -> v.getStringValue() != null && v.getName() != null)
        .collect(Collectors.toMap(Venues::getStringValue, Venues::getName, (a, b) -> a));
  }

  @Cacheable(value = "venues", key = "'slugToIdMap'")
  public Map<String, String> getVenueSlugToIdMap() {
    log.debug("Building venue slug-to-id map from database");
    return venuesRepository.findAll().stream()
        .filter(v -> v.getStringValue() != null && v.getEnumerationId() != null)
        .collect(
            Collectors.toMap(
                Venues::getStringValue, v -> String.valueOf(v.getEnumerationId()), (a, b) -> a));
  }

  @Cacheable(value = "venues", key = "'nameToIdMap'")
  public Map<String, String> getVenueNameToIdMap() {
    return venuesRepository.findAll().stream()
        .filter(v -> v.getName() != null && v.getEnumerationId() != null)
        .collect(
            Collectors.toMap(
                Venues::getName, v -> String.valueOf(v.getEnumerationId()), (a, b) -> a));
  }

  /** Get hierarchical venues with children */
  public List<VenueItemDTO> getHierarchicalVenues() {
    return getHierarchicalVenues(Locale.ENGLISH);
  }

  /** Get hierarchical venues with children, translated for the given locale */
  public List<VenueItemDTO> getHierarchicalVenues(Locale locale) {
    log.debug("Fetching hierarchical venues, locale={}", locale);
    List<Venues> allVenues = venuesRepository.findAll();
    log.debug("Found {} venues in database", allVenues.size());
    List<VenueItemDTO> result = buildHierarchicalVenues(allVenues, null);
    log.debug("Built {} root venue items", result.size());
    return isEnglish(locale) ? result : translateVenues(result, locale);
  }

  /** Build hierarchical venues structure using enumerationId as the join key */
  private List<VenueItemDTO> buildHierarchicalVenues(
      List<Venues> allVenues, Integer parentEnumerationId) {
    return allVenues.stream()
        .filter(v -> java.util.Objects.equals(v.getParentEnumerationId(), parentEnumerationId))
        .map(
            v -> {
              VenueItemDTO item = new VenueItemDTO();
              item.setEnumerationId(v.getEnumerationId());
              item.setTier(v.getTier());
              item.setName(v.getName());
              item.setDefinition(v.getDefinition());
              item.setStringValue(v.getStringValue());
              item.setChildren(buildHierarchicalVenues(allVenues, v.getEnumerationId()));
              return item;
            })
        .collect(Collectors.toList());
  }

  private List<VenueItemDTO> translateVenues(List<VenueItemDTO> venues, Locale locale) {
    if (venues == null) return null;
    return venues.stream().map(v -> translateVenue(v, locale)).toList();
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

  public String getLocalizedVenueName(String value, Locale locale) {
    if (value == null) return null;
    // Inventory venueType stores the English display name; venueType slugs are also possible
    String enumerationId = getVenueNameToIdMap().get(value);
    if (enumerationId == null) enumerationId = getVenueSlugToIdMap().get(value);
    if (enumerationId == null) return value;
    String englishName =
        getVenueNameToIdMap().containsKey(value)
            ? value
            : getVenueSlugToNameMap().getOrDefault(value, value);
    if (isEnglish(locale)) return englishName;
    return t("config.venue." + enumerationId + ".name", locale, englishName);
  }

  private boolean isEnglish(Locale locale) {
    return locale == null || Locale.ENGLISH.getLanguage().equals(locale.getLanguage());
  }

  private String t(String key, Locale locale, String fallback) {
    String result = messageService.getMessage(key, locale);
    return key.equals(result) ? fallback : result;
  }
}
