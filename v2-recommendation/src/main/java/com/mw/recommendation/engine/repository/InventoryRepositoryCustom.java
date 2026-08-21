package com.mw.recommendation.engine.repository;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.BrowseInventoryRequestDTO;
import com.mw.recommendation.engine.dto.InventoryAttributeFilters;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InventoryRepositoryCustom {

  List<Inventory> findActiveInventoriesByCountryWithGeographyTargeting(
      String countryName,
      List<String> excludedInventoryIds,
      RecommendationRequestDTO.GeographyTargeting geographyTargeting,
      Map<String, List<String>> venueTypeIds,
      List<String> mediaOwnerIds,
      List<String> classifications,
      List<String> searchKeywords,
      Long durationDays,
      Long availableLeadDays,
      LocalDate campaignStartDate,
      LocalDate campaignEndDate,
      RecommendationRequestDTO.CampaignGoal goalType,
      List<String> dsps,
      boolean programmaticEnabled,
      List<Integer> durations,
      InventoryAttributeFilters attributeFilters);

  /**
   * Back-compat overload with the DSP + programmatic filters (Feature A) but no spot-duration or
   * attribute filters. Delegates to the canonical method with {@code durations=null,
   * attributeFilters=null}.
   */
  default List<Inventory> findActiveInventoriesByCountryWithGeographyTargeting(
      String countryName,
      List<String> excludedInventoryIds,
      RecommendationRequestDTO.GeographyTargeting geographyTargeting,
      Map<String, List<String>> venueTypeIds,
      List<String> mediaOwnerIds,
      List<String> classifications,
      List<String> searchKeywords,
      Long durationDays,
      Long availableLeadDays,
      LocalDate campaignStartDate,
      LocalDate campaignEndDate,
      RecommendationRequestDTO.CampaignGoal goalType,
      List<String> dsps,
      boolean programmaticEnabled) {
    return findActiveInventoriesByCountryWithGeographyTargeting(
        countryName,
        excludedInventoryIds,
        geographyTargeting,
        venueTypeIds,
        mediaOwnerIds,
        classifications,
        searchKeywords,
        durationDays,
        availableLeadDays,
        campaignStartDate,
        campaignEndDate,
        goalType,
        dsps,
        programmaticEnabled,
        null,
        null);
  }

  /**
   * Back-compat overload with the spot-duration + attribute filters (Feature B) but no DSP or
   * programmatic filters. Delegates to the canonical method with {@code dsps=null,
   * programmaticEnabled=false}.
   */
  default List<Inventory> findActiveInventoriesByCountryWithGeographyTargeting(
      String countryName,
      List<String> excludedInventoryIds,
      RecommendationRequestDTO.GeographyTargeting geographyTargeting,
      Map<String, List<String>> venueTypeIds,
      List<String> mediaOwnerIds,
      List<String> classifications,
      List<String> searchKeywords,
      Long durationDays,
      Long availableLeadDays,
      LocalDate campaignStartDate,
      LocalDate campaignEndDate,
      RecommendationRequestDTO.CampaignGoal goalType,
      List<Integer> durations,
      InventoryAttributeFilters attributeFilters) {
    return findActiveInventoriesByCountryWithGeographyTargeting(
        countryName,
        excludedInventoryIds,
        geographyTargeting,
        venueTypeIds,
        mediaOwnerIds,
        classifications,
        searchKeywords,
        durationDays,
        availableLeadDays,
        campaignStartDate,
        campaignEndDate,
        goalType,
        null,
        false,
        durations,
        attributeFilters);
  }

  /** Back-compat overload with a spot-duration filter but no attribute filters. */
  default List<Inventory> findActiveInventoriesByCountryWithGeographyTargeting(
      String countryName,
      List<String> excludedInventoryIds,
      RecommendationRequestDTO.GeographyTargeting geographyTargeting,
      Map<String, List<String>> venueTypeIds,
      List<String> mediaOwnerIds,
      List<String> classifications,
      List<String> searchKeywords,
      Long durationDays,
      Long availableLeadDays,
      LocalDate campaignStartDate,
      LocalDate campaignEndDate,
      RecommendationRequestDTO.CampaignGoal goalType,
      List<Integer> durations) {
    return findActiveInventoriesByCountryWithGeographyTargeting(
        countryName,
        excludedInventoryIds,
        geographyTargeting,
        venueTypeIds,
        mediaOwnerIds,
        classifications,
        searchKeywords,
        durationDays,
        availableLeadDays,
        campaignStartDate,
        campaignEndDate,
        goalType,
        null,
        false,
        durations,
        null);
  }

  /** Back-compat overload with no spot-duration and no attribute filters. */
  default List<Inventory> findActiveInventoriesByCountryWithGeographyTargeting(
      String countryName,
      List<String> excludedInventoryIds,
      RecommendationRequestDTO.GeographyTargeting geographyTargeting,
      Map<String, List<String>> venueTypeIds,
      List<String> mediaOwnerIds,
      List<String> classifications,
      List<String> searchKeywords,
      Long durationDays,
      Long availableLeadDays,
      LocalDate campaignStartDate,
      LocalDate campaignEndDate,
      RecommendationRequestDTO.CampaignGoal goalType) {
    return findActiveInventoriesByCountryWithGeographyTargeting(
        countryName,
        excludedInventoryIds,
        geographyTargeting,
        venueTypeIds,
        mediaOwnerIds,
        classifications,
        searchKeywords,
        durationDays,
        availableLeadDays,
        campaignStartDate,
        campaignEndDate,
        goalType,
        null,
        false,
        null,
        null);
  }

  Page<Inventory> findActiveInventoriesByCountryPaginated(
      String countryName,
      List<String> excludedInventoryIds,
      RecommendationRequestDTO.GeographyTargeting geographyTargeting,
      Map<String, List<String>> venueTypeIds,
      List<String> mediaOwnerIds,
      List<String> classifications,
      String search,
      BrowseInventoryRequestDTO filterRequest,
      Long availableLeadDays,
      Pageable pageable,
      RecommendationRequestDTO.CampaignGoal goalType);
}
