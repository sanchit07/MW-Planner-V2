package com.mw.recommendation.engine.service;

import com.mw.brand.lib.dto.BrandResponseDTO;
import com.mw.recommendation.engine.domain.AudienceData;
import com.mw.recommendation.engine.domain.BookingData;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Service interface for calculating scoring components and final scores for inventory
 * recommendations
 */
public interface ScoringService {

  /**
   * Calculate final score for an inventory given campaign requirements
   *
   * @param inventory The inventory to score
   * @param audienceData The audience data for the inventory (may be null)
   * @param request The recommendation request
   * @return InventoryScore with all component scores and final score
   */
  InventoryScore calculateScore(
      Inventory inventory, AudienceData audienceData, RecommendationRequestDTO request);

  /**
   * Calculate final score with pre-fetched cached data (Phase 1.5 optimization). This method
   * accepts pre-fetched booking and brand data to eliminate N+1 queries. Business logic remains
   * identical to calculateScore() above.
   *
   * @param inventory The inventory to score
   * @param audienceData The audience data for the inventory (may be null)
   * @param request The recommendation request
   * @param bookingDataCache Pre-fetched booking data map (inventoryId -> List<BookingData>)
   * @param brandDataCache Pre-fetched brand data map (brandId -> BrandResponseDTO)
   * @return InventoryScore with all component scores and final score
   */
  InventoryScore calculateScore(
      Inventory inventory,
      AudienceData audienceData,
      RecommendationRequestDTO request,
      Map<String, List<BookingData>> bookingDataCache,
      Map<String, BrandResponseDTO> brandDataCache);

  /**
   * Calculate measure_fit component
   *
   * @param audienceData Audience data for the inventory
   * @param startDate Campaign start date
   * @param endDate Campaign end date
   * @param goal Campaign goal (IMPRESSIONS, REACH, SOV)
   * @param goalValue Target value for the goal
   * @return measure_fit score (0-100) or null if no audience data
   */
  Double calculateMeasureFit(
      AudienceData audienceData,
      LocalDate startDate,
      LocalDate endDate,
      RecommendationRequestDTO.CampaignGoal goal,
      Long goalValue);

  /**
   * Calculate geo_fit component
   *
   * @param inventory The inventory
   * @param targeting Geography targeting from request
   * @return geo_fit score (0-100)
   */
  Double calculateGeoFit(
      Inventory inventory, RecommendationRequestDTO.GeographyTargeting targeting);

  /**
   * Calculate availability component
   *
   * @param inventory The inventory
   * @param startDate Campaign start date
   * @param endDate Campaign end date
   * @return availability score (0-100)
   */
  Double calculateAvailability(Inventory inventory, LocalDate startDate, LocalDate endDate);

  /**
   * Calculate budget_fit component
   *
   * @param inventory The inventory
   * @param startDate Campaign start date
   * @param endDate Campaign end date
   * @param budget Campaign budget
   * @return budget_fit score (0-100)
   */
  Double calculateBudgetFit(
      Inventory inventory, LocalDate startDate, LocalDate endDate, BigDecimal budget);

  /**
   * Calculate audience_fit component
   *
   * @param audienceData Audience data for the inventory
   * @param targeting Audience targeting from request
   * @return audience_fit score (0-100)
   */
  Double calculateAudienceFit(
      AudienceData audienceData, RecommendationRequestDTO.AudienceTargeting targeting);

  /**
   * Calculate brand_fit component (Phase 1: simple category matching)
   *
   * @param inventory The inventory
   * @param excludedIabCategories List of IAB categories to exclude
   * @return brand_fit score (0-100, or 50 if no brand provided)
   */
  Double calculateBrandFit(Inventory inventory, String brandId, List<String> excludedIabCategories);

  /**
   * Calculate quality_fit component
   *
   * @param inventory The inventory
   * @return quality_fit score (0-100)
   */
  Double calculateQualityFit(Inventory inventory);

  /**
   * Calculate time_fit component
   *
   * @param audienceData Audience data for the inventory
   * @param startDate Campaign start date
   * @param endDate Campaign end date
   * @param daypartWeights Optional daypart weights
   * @return time_fit score (0-100)
   */
  Double calculateTimeFit(
      AudienceData audienceData,
      LocalDate startDate,
      LocalDate endDate,
      Map<String, Double> daypartWeights);

  /**
   * Calculate raw SOV (Share of Voice) percentage for a digital inventory. Returns null for
   * non-digital inventories or when required data is missing.
   *
   * @param inventory The inventory to calculate SOV for
   * @param country Country name for total ad plays lookup
   * @param startDate Campaign start date
   * @param endDate Campaign end date
   * @return raw SOV percentage (0-100) or null
   */
  Double calculateRawSov(
      Inventory inventory, String country, LocalDate startDate, LocalDate endDate);

  /**
   * Batch fetch booking data for multiple inventories to avoid N+1 queries. Single database query
   * replaces individual queries per inventory. Performance optimization: replaces 1000+ individual
   * queries with 1 batch query.
   *
   * @param inventoryIds List of inventory IDs to fetch booking data for
   * @param startDate Campaign start date
   * @param endDate Campaign end date
   * @return Map of inventoryId to list of BookingData for that inventory
   */
  Map<String, List<BookingData>> batchFetchBookingData(
      List<String> inventoryIds, LocalDate startDate, LocalDate endDate);

  /**
   * Batch fetch brand data for multiple brand IDs to avoid N+1 API calls. Single API call or cache
   * lookup replaces individual calls per inventory. Performance optimization: replaces 1000+ API
   * calls with 1 batch call.
   *
   * @param brandIds List of brand IDs to fetch data for
   * @return Map of brandId to BrandResponseDTO
   */
  Map<String, BrandResponseDTO> batchFetchBrandData(List<String> brandIds);
}
