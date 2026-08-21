package com.mw.planner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.brand.lib.service.BrandService;
import com.mw.planner.config.MwPlannerProperties;
import com.mw.planner.domain.*;
import com.mw.planner.dto.*;
import com.mw.planner.dto.ExternalInventoryMessageDTO.ExternalId;
import com.mw.planner.dto.ads.*;
import com.mw.planner.dto.ads.ScheduleDTO.ValidityDTO;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.ads.AdsApiException;
import com.mw.planner.exception.campaign.CampaignNotApprovedForAdsException;
import com.mw.planner.repository.CampaignInventorySchedulesRepository;
import com.mw.planner.repository.ScheduleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/** Service for integrating with ADS (Advertising Data System) external system */
@Slf4j
@Service
@RequiredArgsConstructor
public class MWAdsService {

  private final MwPlannerProperties mwPlannerProperties;
  private final RestTemplate restTemplate;
  private final CampaignService campaignService;
  private final CampaignInventorySchedulesRepository campaignInventorySchedulesRepository;
  private final InventoryService inventoryService;
  private final CountryService countryService;
  private final BrandService brandService;
  private final CompanyService companyService;
  private final UserService userService;
  private final ObjectMapper objectMapper;
  private final ScheduleRepository scheduleRepository;
  private final AdServerRequestLogService adServerRequestLogService;
  private final SecurityContextService securityContextService;

  private static final String SOURCE_SYSTEM = "mw-planner";
  private static final DateTimeFormatter ISO_DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

  /**
   * Submit approved campaign details to ADS external system
   *
   * @param campaignId Campaign ID to submit
   * @param campaignApprovedInventoryIds Approved proposal inventoryIds
   * @return ADS submission response
   */
  public AdsSubmissionResponseDTO submitApprovedCampaignToAds(
      String campaignId, List<String> campaignApprovedInventoryIds) {
    log.info("Starting campaign submission to ADS for campaign ID: {}", campaignId);

    // 1. Fetch and validate campaign status
    Campaign campaign = campaignService.findById(campaignId);

    if (campaign.getStatus() != Campaign.Status.REVIEWING) {
      log.error(
          "Campaign {} cannot be submitted to ADS. Status: {}", campaignId, campaign.getStatus());
      throw new CampaignNotApprovedForAdsException(campaignId, campaign.getStatus());
    }

    log.info("Campaign {} is approved. Proceeding with ADS submission", campaignId);

    try {
      // 2. Build ADS payload containing all inventories
      AdsCampaignRequestDTO adsRequest = buildAdsPayload(campaign, campaignApprovedInventoryIds);
      log.info("Full ADS request built for campaign {}", campaignId);

      // Segregate inventories
      List<ExternalInventoryDTO> allInventories = adsRequest.getExternalPayload().getInventories();

      List<ExternalInventoryDTO> digitalInventories =
          allInventories.stream()
              .filter(inv -> "Digital".equals(inv.getClassification()))
              .collect(Collectors.toList());

      List<ExternalInventoryDTO> staticInventories =
          allInventories.stream()
              .filter(inv -> "Classic".equals(inv.getClassification()))
              .collect(Collectors.toList());

      AdsSubmissionResponseDTO response = new AdsSubmissionResponseDTO();
      int total = 0;
      int successful = 0;
      int failed = 0;

      // 3a. Submit Digital Inventories to ADS
      if (!digitalInventories.isEmpty()) {
        log.info("Submitting {} digital inventories to ADS", digitalInventories.size());
        adsRequest.getExternalPayload().setInventories(digitalInventories);
        log.info("ADS request: {}", objectMapper.writeValueAsString(adsRequest));
        AdsSubmissionResponseDTO digitalResponse = submitToAds(adsRequest, campaignId);
        log.info("ADS digital response: {}", objectMapper.writeValueAsString(digitalResponse));

        if (digitalResponse != null) {
          total += digitalResponse.getTotal() != null ? digitalResponse.getTotal() : 0;
          successful +=
              digitalResponse.getSuccessful() != null ? digitalResponse.getSuccessful() : 0;
          failed += digitalResponse.getFailed() != null ? digitalResponse.getFailed() : 0;
        }
      }

      // 3b. Submit Static Inventories to PosterOps
      if (!staticInventories.isEmpty()) {
        log.info("Submitting {} static inventories to PosterOps", staticInventories.size());
        adsRequest.getExternalPayload().setInventories(staticInventories);
        log.info("PosterOps request: {}", objectMapper.writeValueAsString(adsRequest));
        AdsSubmissionResponseDTO staticResponse = submitToPosterOps(adsRequest);
        log.info("PosterOps response: {}", objectMapper.writeValueAsString(staticResponse));

        if (staticResponse != null) {
          total += staticResponse.getTotal() != null ? staticResponse.getTotal() : 0;
          successful += staticResponse.getSuccessful() != null ? staticResponse.getSuccessful() : 0;
          failed += staticResponse.getFailed() != null ? staticResponse.getFailed() : 0;
        }
      }

      response.setTotal(total);
      response.setSuccessful(successful);
      response.setFailed(failed);

      log.info(
          "Successfully completed campaign {} submission. Total: {}, Successful: {}, Failed: {}",
          campaignId,
          total,
          successful,
          failed);
      return response;

    } catch (Exception e) {
      log.error("Failed to submit campaign {} to ADS: {}", campaignId, e.getMessage(), e);
      throw new AdsApiException(
          ErrorCode.ADS_CAMPAIGN_SUBMISSION_FAILED,
          "Failed to submit campaign to ADS: " + e.getMessage(),
          e,
          campaignId);
    }
  }

  /**
   * Build ADS payload from campaign data
   *
   * @param campaign Campaign entity
   * @param campaignApprovedInventoryIds Approved inventory IDs
   * @return ADS campaign request DTO
   */
  private AdsCampaignRequestDTO buildAdsPayload(
      Campaign campaign, List<String> campaignApprovedInventoryIds) {
    log.debug("Building ADS payload for campaign: {}", campaign.getId());

    // Build external campaign
    ExternalCampaignDTO externalCampaign = buildExternalCampaignDTO(campaign);

    // Build external inventories
    List<ExternalInventoryDTO> externalInventories =
        buildExternalInventoryDTOs(campaign, campaignApprovedInventoryIds);

    // Build external payload
    ExternalPayloadDTO externalPayload =
        ExternalPayloadDTO.builder()
            .campaign(externalCampaign)
            .inventories(externalInventories)
            .build();

    // Build options
    OptionsDTO options = OptionsDTO.builder().source(SOURCE_SYSTEM).build();

    return AdsCampaignRequestDTO.builder()
        .payloadType("DIRECT_PUBLISHER_SPLIT_V3")
        .externalPayload(externalPayload)
        .options(options)
        .build();
  }

  /**
   * Build external campaign DTO from campaign
   *
   * @param campaign Campaign entity
   * @return External campaign DTO
   */
  private ExternalCampaignDTO buildExternalCampaignDTO(Campaign campaign) {
    String countryCode = "US"; // Default
    if (campaign.getCountryId() != null) {
      countryCode = campaign.getCountryId();
    }

    String currency = campaign.getCurrency() != null ? campaign.getCurrency() : "USD";

    // Format dates in ISO format with timezone
    String startDate = formatDateToISO(campaign.getStartDate().atStartOfDay(ZoneId.of("UTC")));
    String endDate =
        formatDateToISO(campaign.getEndDate().atTime(23, 59, 59).atZone(ZoneId.of("UTC")));

    // Get timezone - default to UTC, can be enhanced to get from campaign or country
    String timezoneId = "UTC";

    // // Use the first media owner ID from companyAccess
    // String mediaOwnerId = campaign.getCompanyAccess().getFirst();

    // if (mediaOwnerId == null || mediaOwnerId.isBlank()) {
    //   log.debug("Media owner ID is null or blank for campaign: {}", campaign.getId());
    //   return null;
    // }

    UserResponseDTO user = userService.getUserById(campaign.getUserId());
    // CompanyLookupResponseDTO mediaOwner =
    //     companyService.getCompanyLookupWithCompanyId(mediaOwnerId);
    String brandName =
        campaign.getBrand() != null && campaign.getBrand().getName() != null
            ? campaign.getBrand().getName()
            : "IAB1";
    CompanyLookupResponseDTO company =
        companyService.getCompanyLookupWithCompanyId(campaign.getCompanyId(), false);

    return ExternalCampaignDTO.builder()
        .externalId(campaign.getId())
        .name(campaign.getName())
        .source(SOURCE_SYSTEM)
        .status("APPROVED")
        .currency(currency)
        .brand(brandName)
        .clientType(campaign.getClientType().name())
        .approvalEmails(null) // Not available in current Campaign model
        .advertiser(buildAdvertiserDTO(campaign))
        .marketSelection(buildMarketSelectionDTO(countryCode, currency))
        .budgetSetup(buildBudgetSetupDTO(campaign, currency))
        .campaignGoal(buildCampaignGoalDTO(campaign))
        .startDate(startDate)
        .endDate(endDate)
        .country(countryCode)
        .timezoneId(timezoneId)
        .creativeType(null) // Not set by planner — user selects in Adserver
        .creativeSource("ADVERTISER") // Default, can be enhanced
        .targeting(buildExternalTargetingDTO(campaign))
        .deliveryTargeting(buildDeliveryTargetingDTO(campaign))
        .seller(buildSellerDTO(campaign, user, company))
        .account(buildAccountDTO(campaign, user, company))
        .build();
  }

  /**
   * Build advertiser DTO from campaign
   *
   * @param campaign Campaign entity
   * @return Advertiser DTO
   */
  private AdvertiserDTO buildAdvertiserDTO(Campaign campaign) {
    String companyId =
        campaign.getClientType() == Campaign.ClientType.AGENCY
            ? (campaign.getAgency() != null ? campaign.getAgency().getId() : null)
            : campaign.getCompanyId();

    if (companyId == null) {
      log.warn("No company ID found for campaign: {}", campaign.getId());
      return null;
    }

    try {
      CompanyLookupResponseDTO company =
          companyService.getCompanyLookupWithCompanyId(companyId, false);
      return AdvertiserDTO.builder()
          .id(companyId)
          .seatId(String.valueOf(company.getSeatId()))
          .name(company.getName())
          .build();
    } catch (Exception e) {
      log.warn("Failed to fetch advertiser information for {}: {}", companyId, e.getMessage());
      return AdvertiserDTO.builder().id(companyId).name(null).build();
    }
  }

  /**
   * Build seller DTO from campaign (media owner information)
   *
   * @param campaign Campaign entity
   * @param user User response DTO
   * @param company Company lookup response DTO
   * @return Seller DTO
   */
  private ExternalCampaignDTO.SellerDTO buildSellerDTO(
      Campaign campaign, UserResponseDTO user, CompanyLookupResponseDTO company) {

    // Get the first media owner from companyAccess list
    if (campaign.getCompanyAccess() == null || campaign.getCompanyAccess().isEmpty()) {
      log.debug("No media owner found in companyAccess for campaign: {}", campaign.getId());
      return null;
    }

    // Use the first media owner ID from companyAccess
    String mediaOwnerId = campaign.getCompanyAccess().getFirst();

    try {
      String fullName = String.format("%s %s", user.getFirstName(), user.getLastName());

      return ExternalCampaignDTO.SellerDTO.builder()
          .id(user.getId())
          .name(fullName)
          .publisherId(company.getId())
          .publisherName(company.getName())
          // .phone(user.getPhone())
          .email(user.getEmail())
          .externalId(company.getExternalId())
          .externalUserId(user.getExternalId())
          .build();
    } catch (Exception e) {
      log.warn("Failed to fetch media owner information for {}: {}", mediaOwnerId, e.getMessage());
      return ExternalCampaignDTO.SellerDTO.builder()
          .id(mediaOwnerId)
          .name(null)
          .publisherId(mediaOwnerId)
          .publisherName(null)
          .phone(null)
          .email(null)
          .externalId(null)
          .externalUserId(null)
          .build();
    }
  }

  /**
   * Build account DTO from campaign (media owner information)
   *
   * @param campaign Campaign entity
   * @return Account DTO
   */
  private ExternalCampaignDTO.AccountDTO buildAccountDTO(
      Campaign campaign, UserResponseDTO user, CompanyLookupResponseDTO company) {
    String companyId = campaign.getCompanyId();
    try {

      return ExternalCampaignDTO.AccountDTO.builder()
          .userId(campaign.getUserId()) // User ID from campaign
          .companyName(company.getName())
          .companyId(company.getId())
          // .email(company.getEmail()) // can be enhanced
          .externalId(company.getExternalId())
          .externalUserId(user.getExternalId())
          .build();
    } catch (Exception e) {
      log.warn(
          "Failed to fetch media owner information for account {}: {}", companyId, e.getMessage());
      return ExternalCampaignDTO.AccountDTO.builder()
          .userId(campaign.getUserId())
          .companyName(null)
          .companyId(companyId)
          .email(null)
          .externalId(null)
          .externalUserId(null)
          .build();
    }
  }

  /**
   * Build market selection DTO from campaign
   *
   * @param countryCode Country code
   * @param currency Currency code
   * @return Market selection DTO
   */
  private MarketSelectionDTO buildMarketSelectionDTO(String countryCode, String currency) {
    return MarketSelectionDTO.builder()
        .country(countryCode)
        .currency(currency)
        .region("National") // Default, can be enhanced
        .build();
  }

  /**
   * Build budget setup DTO from campaign
   *
   * @param campaign Campaign entity
   * @param currency Currency code
   * @return Budget setup DTO
   */
  private BudgetSetupDTO buildBudgetSetupDTO(Campaign campaign, String currency) {
    return BudgetSetupDTO.builder()
        .currency(currency)
        .budgetAmount(campaign.getBudget())
        .budgetType("FLEXIBLE") // Default, can be enhanced
        .build();
  }

  /**
   * Build campaign goal DTO from campaign
   *
   * @param campaign Campaign entity
   * @return Campaign goal DTO
   */
  private CampaignGoalDTO buildCampaignGoalDTO(Campaign campaign) {
    if (campaign.getGoals() == null || campaign.getGoals().getGoalType() == null) {
      return null;
    }

    String goalType = mapGoalTypeToAds(campaign.getGoals().getGoalType());
    if (goalType == null) {
      log.warn(
          "Campaign {} has goal type {} not supported by ADS — omitting campaignGoal",
          campaign.getId(),
          campaign.getGoals().getGoalType());
      return null;
    }

    return CampaignGoalDTO.builder()
        .type(goalType)
        .targetValue(campaign.getGoals().getTargetValue())
        .build();
  }

  private String mapGoalTypeToAds(Campaign.Goals.GoalType goalType) {
    return switch (goalType) {
      case IMPRESSIONS -> "IMPRESSIONS";
      case REACH -> "REACH";
      case SOV -> "SHARE_OF_VOICE";
      case ADPLAYS -> "AD_PLAYS";
      default -> null;
    };
  }

  /**
   * Build external targeting DTO from campaign
   *
   * @param campaign Campaign entity
   * @return External targeting DTO
   */
  private ExternalTargetingDTO buildExternalTargetingDTO(Campaign campaign) {
    DemographicsDTO demographics = null;
    List<String> venueTypes = new ArrayList<>();
    ExternalGeofencingDTO geofencing = null;

    if (campaign.getTargeting() != null) {
      // Build demographics
      if (campaign.getTargeting().getDemographics() != null) {
        Map<String, List<String>> demographicsMap = campaign.getTargeting().getDemographics();
        demographics = buildDemographicsDTO(demographicsMap, venueTypes);
      }

      // Build geofencing
      if (campaign.getTargeting().getGeofencing() != null) {
        geofencing = buildExternalGeofencingDTO(campaign.getTargeting().getGeofencing());
      }
    }

    return ExternalTargetingDTO.builder()
        .demographics(demographics)
        .venueTypes(venueTypes.isEmpty() ? null : venueTypes)
        .geofencing(geofencing)
        .build();
  }

  /**
   * Build demographics DTO from demographics map
   *
   * @param demographicsMap Demographics map
   * @param venueTypes List to populate with venue types
   * @return Demographics DTO
   */
  private DemographicsDTO buildDemographicsDTO(
      Map<String, List<String>> demographicsMap, List<String> venueTypes) {
    List<String> ageGroups = new ArrayList<>();
    List<String> genders = new ArrayList<>();
    List<String> incomeGroups = new ArrayList<>();
    List<String> interests = new ArrayList<>();
    List<String> audienceBehaviour = new ArrayList<>();

    demographicsMap.forEach(
        (key, value) -> {
          if (value == null || value.isEmpty()) {
            return;
          }
          String lowerKey = key.toLowerCase();
          if (lowerKey.contains("age")) {
            // Convert age group format from "18_24" to "18-24"
            List<String> convertedAgeGroups =
                value.stream().map(age -> age.replace("_", "-")).toList();
            ageGroups.addAll(convertedAgeGroups);
          } else if (lowerKey.contains("gender") || lowerKey.contains("sex")) {
            genders.addAll(value);
          } else if (lowerKey.contains("income")) {
            incomeGroups.addAll(value);
          } else if (lowerKey.contains("interest")) {
            interests.addAll(value);
          } else if (lowerKey.contains("behaviour")
              || lowerKey.contains("behavior")
              || lowerKey.contains("audience")) {
            audienceBehaviour.addAll(value);
          } else if (lowerKey.contains("venue")) {
            venueTypes.addAll(value);
          }
        });

    return DemographicsDTO.builder()
        .ageGroups(ageGroups.isEmpty() ? null : ageGroups)
        .genders(genders.isEmpty() ? null : genders)
        .incomeGroups(incomeGroups.isEmpty() ? null : incomeGroups)
        .interests(interests.isEmpty() ? null : interests)
        .audienceBehaviour(audienceBehaviour.isEmpty() ? null : audienceBehaviour)
        .build();
  }

  /**
   * Build external geofencing DTO from campaign geofencing
   *
   * @param geofencing Campaign geofencing
   * @return External geofencing DTO
   */
  private ExternalGeofencingDTO buildExternalGeofencingDTO(
      Campaign.Targeting.Geofencing geofencing) {
    List<GeometryDTO> geometrics = new ArrayList<>();
    List<LocationDTO> locations = new ArrayList<>();

    if (geofencing.getGeometries() != null) {
      for (Campaign.Targeting.Geofencing.Geometry geometry : geofencing.getGeometries()) {
        GeometryDTO geometryDTO =
            GeometryDTO.builder()
                .type(geometry.getType())
                .coordinates(convertCoordinatesToNestedList(geometry.getCoordinates()))
                .included(geometry.isIncluded())
                .build();
        geometrics.add(geometryDTO);
      }
    }

    if (geofencing.getLocations() != null) {
      for (Campaign.Targeting.Geofencing.Location location : geofencing.getLocations()) {
        LocationDTO locationDTO =
            LocationDTO.builder()
                .name(location.getName())
                .lat(location.getLat())
                .lng(location.getLng())
                .radius(location.getRadius())
                .address(location.getAddress())
                .metadata(location.getMetadata())
                .included(location.isIncluded())
                .build();
        locations.add(locationDTO);
      }
    }

    return ExternalGeofencingDTO.builder()
        .geometrics(geometrics.isEmpty() ? null : geometrics)
        .locations(locations.isEmpty() ? null : locations)
        .build();
  }

  /**
   * Convert coordinates from List<List<Double>> to List<List<List<Double>>> for MultiPolygon For
   * Polygon, wrap in an extra list. For MultiPolygon, keep as is.
   *
   * @param coordinates Original coordinates
   * @return Converted coordinates
   */
  private List<List<List<Double>>> convertCoordinatesToNestedList(List<List<Double>> coordinates) {
    // For Polygon, wrap coordinates in an extra list
    // For MultiPolygon, coordinates should already be in the right format
    // This is a simplified conversion - may need adjustment based on actual data structure
    List<List<List<Double>>> result = new ArrayList<>();
    if (coordinates != null && !coordinates.isEmpty()) {
      result.add(coordinates);
    }
    return result;
  }

  /**
   * Build delivery targeting DTO from campaign
   *
   * @param campaign Campaign entity
   * @return Delivery targeting DTO
   */
  private DeliveryTargetingDTO buildDeliveryTargetingDTO(Campaign campaign) {
    WeatherDTO weather = null;
    TrafficDTO traffic = null;
    AqiDTO aqi = null;
    FootfallDTO footfall = null;

    // If campaign has signals, populate them
    if (campaign.getTargeting() != null && campaign.getTargeting().getSignals() != null) {
      List<String> signalList = campaign.getTargeting().getSignals();

      for (String signal : signalList) {
        String lowerSignal = signal.toLowerCase();
        if (lowerSignal.contains("weather")) {
          // Create basic weather DTO - can be enhanced when Campaign model has detailed weather
          // info
          weather = WeatherDTO.builder().build();
        } else if (lowerSignal.contains("traffic")) {
          // Create basic traffic DTO - can be enhanced when Campaign model has detailed traffic
          // info
          traffic = TrafficDTO.builder().build();
        } else if (lowerSignal.contains("aqi") || lowerSignal.contains("air")) {
          // Create basic AQI DTO - can be enhanced when Campaign model has detailed AQI info
          aqi = AqiDTO.builder().build();
        } else if (lowerSignal.contains("footfall")) {
          // Create basic footfall DTO - can be enhanced when Campaign model has detailed footfall
          // info
          footfall = FootfallDTO.builder().build();
        }
      }
    }

    SignalsDTO signals =
        SignalsDTO.builder().weather(weather).traffic(traffic).aqi(aqi).footfall(footfall).build();

    return DeliveryTargetingDTO.builder().signals(signals).build();
  }

  /**
   * Build external inventory DTOs from campaign
   *
   * @param campaign Campaign entity
   * @param campaignApprovedInventoryIds Approved inventory IDs
   * @return List of external inventory DTOs
   */
  private List<ExternalInventoryDTO> buildExternalInventoryDTOs(
      Campaign campaign, List<String> campaignApprovedInventoryIds) {
    // Get all schedule configs for this campaign
    List<CampaignInventorySchedules> scheduleConfigs =
        campaignInventorySchedulesRepository.findByCampaignIdAndInventoryIdIn(
            campaign.getId(), campaignApprovedInventoryIds);

    if (scheduleConfigs.isEmpty()) {
      log.warn("No inventory schedules found for campaign: {}", campaign.getId());
      return Collections.emptyList();
    }

    // Create a map for quick lookup of schedules by inventory ID
    Map<String, CampaignInventorySchedules> scheduleMap =
        scheduleConfigs.stream()
            .collect(
                Collectors.toMap(
                    CampaignInventorySchedules::getInventoryId, schedules -> schedules));

    // Extract inventory IDs and fetch inventories
    List<String> inventoryIds =
        scheduleConfigs.stream().map(CampaignInventorySchedules::getInventoryId).toList();

    // Build external inventory DTOs
    return inventoryIds.stream()
        .map(
            inventoryId -> {
              try {
                Inventory inventory = inventoryService.getById(inventoryId);
                CampaignInventorySchedules schedules = scheduleMap.get(inventoryId);
                return buildExternalInventoryDTO(inventory, schedules, campaign);
              } catch (Exception e) {
                log.error("Failed to fetch inventory {}: {}", inventoryId, e.getMessage());
                return null;
              }
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * Build external inventory DTO from inventory, schedules, and campaign
   *
   * @param inventory Inventory entity
   * @param schedules Campaign inventory schedules
   * @param campaign Campaign entity
   * @return External inventory DTO
   */
  private ExternalInventoryDTO buildExternalInventoryDTO(
      Inventory inventory, CampaignInventorySchedules schedules, Campaign campaign) {
    String inventoryId = inventory.getId() != null ? inventory.getId() : inventory.getReferenceId();
    String adUnitCode = inventory.getExternalId() != null ? inventory.getExternalId() : inventoryId;
    String deviceId =
        inventory.getExternalIds() != null
            ? inventory.getExternalIds().stream()
                .filter(e -> "CMS".equals(e.getPlatform()))
                .map(ExternalId::getExternalId)
                .findFirst()
                .orElse(null)
            : null;

    // Extract size from panels
    String size = null;
    if (inventory.getPanels() != null && !inventory.getPanels().isEmpty()) {
      Inventory.Panel firstPanel = inventory.getPanels().getFirst();
      if (firstPanel.getPixelWidth() != null && firstPanel.getPixelHeight() != null) {
        size = String.format("%dx%d", firstPanel.getPixelWidth(), firstPanel.getPixelHeight());
      } else if (firstPanel.getPhysicalWidth() != null && firstPanel.getPhysicalHeight() != null) {
        size =
            String.format(
                "%.0fx%.0f", firstPanel.getPhysicalWidth(), firstPanel.getPhysicalHeight());
      }
    }

    // Extract location coordinates
    Double latitude = null;
    Double longitude = null;

    if (inventory.getLocation() != null
        && inventory.getLocation().getLocationCoordinates() != null) {

      Object coordinates = inventory.getLocation().getLocationCoordinates();

      // MongoDB Map / Document case
      if (coordinates instanceof Map<?, ?> geoMap) {
        Object coords = geoMap.get("coordinates");

        if (coords instanceof List && ((List<?>) coords).size() >= 2) {
          longitude = ((Number) ((List<?>) coords).get(0)).doubleValue();
          latitude = ((Number) ((List<?>) coords).get(1)).doubleValue();
        }
      }
    }

    // Get publisher information
    ExternalInventoryDTO.PublisherDTO publisher = buildPublisherDTO(inventory.getMediaOwnerId());

    // Get venue type information
    String venueType = null;
    List<String> venueTypeIds = null;
    if (inventory.getVenueType() != null && !inventory.getVenueType().isEmpty()) {
      venueTypeIds = new ArrayList<>(inventory.getVenueType());
      venueType = venueTypeIds.getLast(); // Last element is the most specific
    }

    // Get country information
    String countryIso2 = null;
    String countryIso3 = null;
    if (inventory.getLocation() != null && inventory.getLocation().getCountry() != null) {
      try {
        CountryResponseDTO country =
            countryService.getCountryByName(inventory.getLocation().getCountry());
        countryIso2 = country.getIso(); // countryId is typically ISO2

        try {
          countryIso3 = new Locale("", countryIso2).getISO3Country();
        } catch (MissingResourceException e) {
          countryIso3 = countryIso2; // fallback if invalid
        }
      } catch (Exception e) {
        log.warn(
            "Failed to fetch country information for {}: {}",
            inventory.getLocation().getCountry(),
            e.getMessage());
      }
    }

    // Get spotsPerHour and spotDuration from digitalFields
    Integer spotsPerHour = null;
    Integer spotDuration = null;
    Integer clients = null;

    // Fetch Schedule entities if scheduleIds exist
    List<Schedule> scheduleEntities = Collections.emptyList();
    if (schedules != null
        && schedules.getScheduleIds() != null
        && !schedules.getScheduleIds().isEmpty()) {
      scheduleEntities = scheduleRepository.findAllById(schedules.getScheduleIds());
    }

    if (inventory.getDigitalFields() != null) {
      // Calculate spotsPerHour from spotsPerLoop and loops per hour
      // For now, use spotsPerLoop if available, or default calculation
      if (!scheduleEntities.isEmpty()) {
        Schedule firstSchedule = scheduleEntities.getFirst();
        if (firstSchedule.getSpotsPerHour() != null) {
          spotsPerHour = firstSchedule.getSpotsPerHour().intValue();
        }
        if (firstSchedule.getSpotsPerLoop() != null) {
          clients = firstSchedule.getSpotsPerLoop().intValue();
        }
      }
      spotDuration = inventory.getDigitalFields().getSpotDuration();
    }

    // Build aspect ratio information
    Boolean enableAspectRatio = null;
    String displayAspectRatio = null;
    if (inventory.getPanels() != null && !inventory.getPanels().isEmpty()) {
      Inventory.Panel firstPanel = inventory.getPanels().getFirst();
      if (firstPanel.getPixelWidth() != null && firstPanel.getPixelHeight() != null) {
        enableAspectRatio = true;
        // Calculate aspect ratio
        int width = firstPanel.getPixelWidth();
        int height = firstPanel.getPixelHeight();
        int gcd = calculateGCD(width, height);
        displayAspectRatio = String.format("%d:%d", width / gcd, height / gcd);
      }
    }

    // Build schedule information from schedules
    List<ScheduleDTO> schedule = buildScheduleDTOs(scheduleEntities);

    // Build planning information
    ExternalInventoryDTO.PlanningDTO planning =
        buildPlanningDTO(campaign, inventory, scheduleEntities);
    ExternalInventoryDTO.MetadataDTO metadata = buildMetadataDTO(inventory);

    // Ensure inventory type is set based on classification
    String inventoryType =
        inventory.getClassification() != null ? inventory.getClassification().toUpperCase() : "";

    return ExternalInventoryDTO.builder()
        .classification(inventory.getClassification())
        .id(inventoryId)
        .adUnitCode(adUnitCode)
        .name(inventory.getName())
        .referenceId(inventory.getReferenceId())
        .deviceId(deviceId)
        .size(size)
        .publisher(publisher)
        .publisherExternalId(publisher != null ? publisher.getExternalId() : null)
        .latitude(latitude)
        .longitude(longitude)
        .venueType(venueType)
        .venueTypeIds(venueTypeIds)
        .thumbnail(inventory.getThumbnailUrl())
        .timezone(inventory.getTimeZone())
        .countryIso2(countryIso2)
        .countryIso3(countryIso3)
        .spotsPerHour(spotsPerHour)
        .spotDuration(spotDuration)
        .clients(clients) // Not available in current Inventory model
        .group(null) // Not available in current Inventory model
        .networkId(null) // Not available in current Inventory model
        .packageId(null) // Not available in current Inventory model
        .bcat(null) // Not available in current Inventory model
        .publisherDomain(null) // Not available in current Inventory model
        .bookingMode(
            Optional.ofNullable(inventory.getDigitalFields())
                .map(Inventory.DigitalFields::getBookingMode)
                .orElse(null))
        .enableAspectRatio(enableAspectRatio)
        .displayAspectRatio(displayAspectRatio)
        .schedule(schedule)
        .planning(planning)
        .metadata(metadata)
        .inventoryType(inventoryType)
        .build();
  }

  /**
   * Build planning DTO with allocation, estimates, and pricing
   *
   * @param inventory Inventory entity
   * @param schedules List of Schedule entities
   * @return Planning DTO
   */
  private ExternalInventoryDTO.PlanningDTO buildPlanningDTO(
      Campaign campaign, Inventory inventory, List<Schedule> schedules) {

    // No planning possible without schedules
    if (schedules == null || schedules.isEmpty()) {
      return null;
    }

    // Calculate allocation metrics (slots, playtime, SOV, SOT)
    ExternalInventoryDTO.AllocationDTO allocation = buildAllocation(inventory, schedules);

    // Calculate impression, reach, and frequency estimates
    ExternalInventoryDTO.EstimatesDTO estimates = buildEstimates(schedules, inventory.getId());

    // Calculate pricing based on CPM and impressions
    ExternalInventoryDTO.PricingDTO pricing =
        buildPricing(campaign, inventory, estimates.getImpressions());

    // Assemble final planning DTO
    return ExternalInventoryDTO.PlanningDTO.builder()
        .allocation(allocation)
        .estimates(estimates)
        .pricing(pricing)
        .build();
  }

  // Computes inventory allocation metrics from schedules
  private ExternalInventoryDTO.AllocationDTO buildAllocation(
      Inventory inventory, List<Schedule> schedules) {

    // Total ad plays across all schedules
    long slots =
        schedules.stream()
            .map(Schedule::getAdPlays)
            .filter(Objects::nonNull)
            .mapToLong(Long::longValue)
            .sum();

    // Spot duration in seconds for digital inventories
    int spotDuration =
        Optional.ofNullable(inventory.getDigitalFields())
            .map(Inventory.DigitalFields::getSpotDuration)
            .orElse(0);

    // Loop duration in seconds from digital fields
    Integer loopDuration =
        Optional.ofNullable(inventory.getDigitalFields())
            .map(Inventory.DigitalFields::getLoopDuration)
            .orElse(null);

    // Spots per loop from digital fields
    Integer spotsPerLoop =
        Optional.ofNullable(inventory.getDigitalFields())
            .map(Inventory.DigitalFields::getSpotsPerLoop)
            .orElse(null);

    // Total play time based on slots and spot duration
    long playTimeSec = slots * spotDuration;

    // Planned share of time across selected schedules
    double plannedSot = CampaignInventorySchedulesService.calculatePlannedSot(schedules);

    // Total available share of time for the inventory
    double totalSot = CampaignInventorySchedulesService.calculateTotalSot(schedules);

    // SOV = (plannedSpots / totalSpots) * 100
    // plannedSpots = schedule.spotsPerLoop (spots the campaign booked per loop)
    // totalSpots   = inventory.digitalFields.spotsPerLoop (total capacity per loop)
    long plannedSpots =
        schedules.stream()
            .filter(s -> s.getSpotsPerLoop() != null)
            .mapToLong(Schedule::getSpotsPerLoop)
            .findFirst()
            .orElse(0L);
    double sov =
        (spotsPerLoop != null && spotsPerLoop > 0)
            ? ((double) plannedSpots / spotsPerLoop) * 100
            : 0.0;

    // Share of time ratio for the campaign
    double sot = totalSot > 0 ? plannedSot / totalSot : 0.0;

    return ExternalInventoryDTO.AllocationDTO.builder()
        .slots(slots)
        .playTimeSec(playTimeSec)
        .sov(sov)
        .sot(sot)
        .loopDuration(loopDuration)
        .spotsPerLoop(spotsPerLoop)
        .bookedSpotsPerLoop(1)
        .bookedSpotsPerHour(
            loopDuration != null && loopDuration > 0 ? (3600 / loopDuration) * 1 : null)
        .build();
  }

  // Aggregates impression, reach, and frequency estimates safely
  private ExternalInventoryDTO.EstimatesDTO buildEstimates(
      List<Schedule> schedules, String inventoryId) {

    try {
      // Total impressions across schedules
      long impressions =
          schedules.stream()
              .map(Schedule::getImpressions)
              .filter(Objects::nonNull)
              .mapToLong(Long::longValue)
              .sum();

      // Total reach across schedules
      long reach =
          schedules.stream()
              .map(Schedule::getReach)
              .filter(Objects::nonNull)
              .mapToLong(Long::longValue)
              .sum();

      // Frequency derived from impressions and reach
      double frequency = reach > 0 ? (double) impressions / reach : 0.0;

      return ExternalInventoryDTO.EstimatesDTO.builder()
          .impressions(impressions)
          .reach(reach)
          .frequency(frequency)
          .build();

    } catch (Exception ex) {
      // Fallback to safe defaults if estimate calculation fails
      log.warn("Failed to calculate estimates for inventory {}: {}", inventoryId, ex.getMessage());

      return ExternalInventoryDTO.EstimatesDTO.builder()
          .impressions(0L)
          .reach(0L)
          .frequency(0.0)
          .build();
    }
  }

  // Calculates estimated campaign cost using CPM pricing model
  private ExternalInventoryDTO.PricingDTO buildPricing(
      Campaign campaign, Inventory inventory, long impressions) {
    // Check the goal type to determine if pricing should be calculated
    if (isCpmPricingModel(campaign)) {
      return buildCpmPricing(campaign, inventory, impressions);
    } else if (isCpsPricingModel(campaign)) {
      // CPS pricing model can be implemented here when needed
      return null; // Placeholder for CPS pricing
    } else {
      // If goal type is not recognized, return null or default pricing
      log.warn(
          "Unrecognized goal type for campaign {}: {}",
          campaign.getId(),
          campaign.getGoals() != null ? campaign.getGoals().getGoalType() : "null");
      return buildCpmPricing(campaign, inventory, impressions);
    }

    // CPM configured for the inventory
    // double cpm = Optional.ofNullable(InventoryService.getCpm(inventory)).orElse(0.0);

    // // Estimated cost derived from CPM and impressions
    // double estimatedCost = impressions > 0 ? (impressions * cpm) / 1_000 : 0.0;

    // return
    // ExternalInventoryDTO.PricingDTO.builder().cpm(cpm).estimatedCost(estimatedCost).build();
  }

  private ExternalInventoryDTO.PricingDTO buildCpmPricing(
      Campaign campaign, Inventory inventory, long impressions) {
    // CPM configured for the inventory
    double cpm = Optional.ofNullable(InventoryService.getCpm(inventory)).orElse(0.0);

    // Estimated cost derived from CPM and impressions
    // Standard CPM formula: Cost = (Impressions × CPM) / 1000
    // double estimatedCost = impressions > 0 ? (impressions * cpm) / 1_000 : 0.0;
    double estimatedCost = impressions > 0 ? cpm / 1_000 * impressions : 0.0;

    // Round to 2 decimal places using BigDecimal for precision
    estimatedCost =
        BigDecimal.valueOf(estimatedCost).setScale(2, RoundingMode.HALF_UP).doubleValue();

    return ExternalInventoryDTO.PricingDTO.builder().cpm(cpm).estimatedCost(estimatedCost).build();
  }

  private ExternalInventoryDTO.PricingDTO buildCpsPricing(
      Campaign campaign, Inventory inventory, long impressions) {
    // CPS Rate / Default Duration x user selected duration x planned ad plays.
    // double cps = Optional.ofNullable(InventoryService.getSpotRate(inventory)).orElse(0.0);

    // // Estimated cost derived from CPM and impressions
    // double estimatedCost = impressions > 0 ? cpm / (1000 * impressions) : 0.0; // Formula update
    // as of March 24, 2026 given by Sanchit

    // return
    // ExternalInventoryDTO.PricingDTO.builder().cpm(cpm).estimatedCost(estimatedCost).build();
    return null;
  }

  private boolean isCpmPricingModel(Campaign campaign) {
    // Goal Type is either IMPRESSION or REACH (which is typically priced on CPM basis)
    return campaign.getGoals() != null
        && campaign.getGoals().getGoalType() != null
        && (campaign.getGoals().getGoalType() == Campaign.Goals.GoalType.IMPRESSIONS
            || campaign.getGoals().getGoalType() == Campaign.Goals.GoalType.REACH);
  }

  private boolean isCpsPricingModel(Campaign campaign) {
    // Goal Type is either ADPLAYS or SOV (which is typically priced on CPS basis)
    return campaign.getGoals() != null
        && campaign.getGoals().getGoalType() != null
        && (campaign.getGoals().getGoalType() == Campaign.Goals.GoalType.ADPLAYS
            || campaign.getGoals().getGoalType() == Campaign.Goals.GoalType.SOV);
  }

  /**
   * Build metadata DTO from inventory external IDs
   *
   * @param inventory Inventory entity
   * @return Metadata DTO
   */
  private ExternalInventoryDTO.MetadataDTO buildMetadataDTO(Inventory inventory) {
    if (inventory.getExternalIds() == null || inventory.getExternalIds().isEmpty()) {
      return null;
    }

    List<ExternalInventoryDTO.ExternalRefIdDTO> externalRefIds =
        inventory.getExternalIds().stream()
            .map(
                extId ->
                    ExternalInventoryDTO.ExternalRefIdDTO.builder()
                        .source(extId.getPlatform())
                        .externalRefId(extId.getExternalId())
                        .build())
            .collect(Collectors.toList());

    return ExternalInventoryDTO.MetadataDTO.builder().externalRefIds(externalRefIds).build();
  }

  /**
   * Build publisher DTO from media owner ID
   *
   * @param mediaOwnerId Media owner ID
   * @return Publisher DTO
   */
  private ExternalInventoryDTO.PublisherDTO buildPublisherDTO(String mediaOwnerId) {
    if (mediaOwnerId == null || mediaOwnerId.isBlank()) {
      return null;
    }

    try {
      CompanyLookupResponseDTO companyDto =
          companyService.getCompanyLookupWithCompanyId(mediaOwnerId, false);
      return ExternalInventoryDTO.PublisherDTO.builder()
          .id(companyDto.getId())
          .name(companyDto.getName())
          .externalId(companyDto.getExternalId())
          .build();
    } catch (Exception e) {
      log.warn("Failed to fetch publisher information for {}: {}", mediaOwnerId, e.getMessage());
      return ExternalInventoryDTO.PublisherDTO.builder()
          .id(mediaOwnerId)
          .name(null)
          .externalId(null)
          .build();
    }
  }

  /**
   * Build schedule DTOs from Schedule entities. Groups schedules by date and merges hours from all
   * schedules for each date
   *
   * @param scheduleEntities List of Schedule entities
   * @return List of schedule DTOs grouped by date
   */
  private List<ScheduleDTO> buildScheduleDTOs(List<Schedule> scheduleEntities) {
    if (scheduleEntities == null || scheduleEntities.isEmpty()) {
      return null;
    }

    // Each Schedule is processed INDEPENDENTLY (no cross-schedule merge): a schedule either
    // collapses to a single DEFAULT recurring entry, or emits one CUSTOM entry per booked date.
    // Overlaps between schedules on the same date are intentionally left for ADS to union.
    List<ScheduleDTO> scheduleDTOs = new ArrayList<>();

    for (Schedule schedule : scheduleEntities) {
      if (schedule.getType() != null && schedule.getType().toString().equalsIgnoreCase("LOOP")) {
        ScheduleDTO loopScheduleDTO = buildLoopScheduleDTO(schedule);
        if (loopScheduleDTO != null) {
          applySpots(loopScheduleDTO, schedule);
          scheduleDTOs.add(loopScheduleDTO);
        }
        continue;
      }

      // DAYPART: collect this schedule's own hours by date (booking matrix keys are unique)
      Map<String, Set<Integer>> hoursByDate = new TreeMap<>();
      Map<String, List<Integer>> bookingMatrix = schedule.getBookingMatrix();
      if (bookingMatrix != null) {
        for (Map.Entry<String, List<Integer>> entry : bookingMatrix.entrySet()) {
          List<Integer> hours = entry.getValue();
          if (hours != null && !hours.isEmpty()) {
            hoursByDate.computeIfAbsent(entry.getKey(), k -> new TreeSet<>()).addAll(hours);
          }
        }
      }
      if (hoursByDate.isEmpty()) {
        continue;
      }

      // Collapse this schedule into a single DEFAULT recurring entry when possible; otherwise
      // emit one CUSTOM entry per date. All-or-nothing per schedule (no intra-schedule hybrid).
      ScheduleDTO collapsed = tryCollapseDaypart(hoursByDate);
      if (collapsed != null) {
        applySpots(collapsed, schedule);
        scheduleDTOs.add(collapsed);
      } else {
        for (Map.Entry<String, Set<Integer>> entry : hoursByDate.entrySet()) {
          List<Integer> sortedHours = new ArrayList<>(entry.getValue());
          Collections.sort(sortedHours);
          ScheduleDTO scheduleDTO =
              ScheduleDTO.builder()
                  .type("CUSTOM")
                  .priority(1)
                  .date(entry.getKey())
                  .hours(convertHoursToRanges(sortedHours))
                  .build();
          applySpots(scheduleDTO, schedule);
          scheduleDTOs.add(scheduleDTO);
        }
      }
    }

    return scheduleDTOs.isEmpty() ? null : scheduleDTOs;
  }

  /** Copies the per-schedule spots fields onto a built schedule DTO. */
  private void applySpots(ScheduleDTO dto, Schedule schedule) {
    dto.setSpotsPerLoop(schedule.getSpotsPerLoop());
    dto.setSpotsPerHour(schedule.getSpotsPerHour());
  }

  /**
   * Attempts to collapse a uniform DAYPART booking map into a single DEFAULT recurring schedule.
   *
   * <p>Collapses only when ALL of the following hold:
   *
   * <ol>
   *   <li>There are at least two booked dates (a single date stays CUSTOM for clarity).
   *   <li>Every booked date shares the identical hour set.
   *   <li>Every occurrence of each booked weekday, within the booked date range, is present (no
   *       gaps). A missing occurrence would cause the recurring DEFAULT to over-book that date.
   * </ol>
   *
   * <p>Weekdays are derived from the booking dates themselves, never from {@code
   * Schedule.scheduleDays}, which may be stale/inconsistent with the booking matrix. Validity uses
   * the actual booked extent (min/max booked date), not {@code Schedule.startDate/endDate}.
   *
   * @param hoursByDate aggregated hours keyed by date string ("yyyy-MM-dd")
   * @return a single DEFAULT {@link ScheduleDTO} when collapsible, otherwise {@code null}
   */
  private ScheduleDTO tryCollapseDaypart(Map<String, Set<Integer>> hoursByDate) {
    // (1) Need at least two dates to justify a recurrence
    if (hoursByDate == null || hoursByDate.size() < 2) {
      return null;
    }

    // (2) Uniform hours across all dates
    Set<Integer> uniformHours = null;
    for (Set<Integer> hours : hoursByDate.values()) {
      if (hours == null || hours.isEmpty()) {
        return null;
      }
      if (uniformHours == null) {
        uniformHours = hours;
      } else if (!uniformHours.equals(hours)) {
        return null;
      }
    }

    // Parse and sort booked dates; derive the booked weekday set from the dates themselves
    List<LocalDate> dates = hoursByDate.keySet().stream().map(LocalDate::parse).sorted().toList();
    LocalDate min = dates.getFirst();
    LocalDate max = dates.getLast();

    Set<DayOfWeek> bookedDays = EnumSet.noneOf(DayOfWeek.class);
    for (LocalDate date : dates) {
      bookedDays.add(date.getDayOfWeek());
    }

    // (3) Gap check: every matching weekday within [min, max] must be booked
    Set<String> bookedKeys = hoursByDate.keySet();
    for (LocalDate cursor = min; !cursor.isAfter(max); cursor = cursor.plusDays(1)) {
      if (bookedDays.contains(cursor.getDayOfWeek()) && !bookedKeys.contains(cursor.toString())) {
        return null; // missing occurrence -> recurring DEFAULT would over-book this date
      }
    }

    List<Integer> daysOfWeek = bookedDays.stream().map(DayOfWeek::getValue).sorted().toList();

    List<Integer> sortedHours = new ArrayList<>(uniformHours);
    Collections.sort(sortedHours);

    ValidityDTO validity = new ValidityDTO();
    validity.setStartDate(min.toString());
    validity.setEndDate(max.toString());

    return ScheduleDTO.builder()
        .type("DEFAULT")
        .priority(1)
        .validity(validity)
        .daysOfWeek(daysOfWeek)
        .date(null)
        .hours(convertHoursToRanges(sortedHours))
        .build();
  }

  private ScheduleDTO buildLoopScheduleDTO(Schedule schedule) {
    if (schedule.getType() == null || !schedule.getType().toString().equalsIgnoreCase("LOOP")) {
      return null;
    }
    List<ScheduleDTO.HourRangeDTO> hourRanges = new ArrayList<>();

    if (schedule.getBookingMatrix() != null && !schedule.getBookingMatrix().isEmpty()) {
      // Get the first entry in the booking matrix to extract hours (if any)
      Map.Entry<String, List<Integer>> entry =
          schedule.getBookingMatrix().entrySet().iterator().next();
      List<Integer> bookedHours = new ArrayList<>(entry.getValue());
      Collections.sort(bookedHours);
      hourRanges = convertHoursToRanges(bookedHours);
    }

    ValidityDTO validity = new ValidityDTO();
    if (schedule.getStartDate() != null) {
      validity.setStartDate(schedule.getStartDate().toString());
    }
    if (schedule.getEndDate() != null) {
      validity.setEndDate(schedule.getEndDate().toString());
    }

    List<Integer> daysOfWeek =
        schedule.getScheduleDays() == null
            ? null
            : schedule.getScheduleDays().stream().map(day -> day.ordinal() + 1).toList();

    return ScheduleDTO.builder()
        .type("DEFAULT")
        .priority(1)
        .validity(validity)
        .daysOfWeek(daysOfWeek)
        .date(null)
        .hours(hourRanges)
        .build();
  }

  /**
   * Convert a sorted list of hours to consecutive hour ranges
   *
   * @param sortedHours Sorted list of hours (0-23)
   * @return List of hour range DTOs
   */
  // Converts a sorted list of hours into consecutive hour ranges
  private List<ScheduleDTO.HourRangeDTO> convertHoursToRanges(List<Integer> sortedHours) {

    // No ranges possible without valid hour input
    if (sortedHours == null || sortedHours.isEmpty()) {
      return List.of();
    }

    List<ScheduleDTO.HourRangeDTO> ranges = new ArrayList<>();

    // Initialize the first range boundary
    int rangeStart = sortedHours.getFirst();
    int rangeEnd = rangeStart;

    // Iterate from the second hour onward
    for (int i = 1; i < sortedHours.size(); i++) {
      int currentHour = sortedHours.get(i);

      // Extend range if the hour is consecutive
      if (currentHour == rangeEnd + 1) {
        rangeEnd = currentHour;
        continue;
      }

      // Close current range when a gap is detected
      ranges.add(
          ScheduleDTO.HourRangeDTO.builder()
              .start(String.format("%02d:00", rangeStart))
              .end(String.format("%02d:00", rangeEnd))
              .build());

      // Start a new range from the current hour
      rangeStart = currentHour;
      rangeEnd = currentHour;
    }

    // Add the final open range
    ranges.add(
        ScheduleDTO.HourRangeDTO.builder()
            .start(String.format("%02d:00", rangeStart))
            .end(String.format("%02d:00", rangeEnd))
            .build());

    return ranges;
  }

  /**
   * Calculate greatest common divisor for aspect ratio calculation
   *
   * @param a First number
   * @param b Second number
   * @return GCD
   */
  private int calculateGCD(int a, int b) {
    while (b != 0) {
      int temp = b;
      b = a % b;
      a = temp;
    }
    return a;
  }

  /**
   * Format ZonedDateTime to ISO string
   *
   * @param zonedDateTime ZonedDateTime to format
   * @return ISO formatted string
   */
  private String formatDateToISO(ZonedDateTime zonedDateTime) {
    return zonedDateTime.format(ISO_DATE_TIME_FORMATTER);
  }

  /**
   * Submit payload to ADS system
   *
   * @param adsRequest ADS campaign request
   * @return ADS submission response
   */
  /**
   * Submit campaign to ADS and log the request/response to MongoDB. Handles all error scenarios
   * (client errors, server errors, network failures) and ensures logging never disrupts the main
   * business flow.
   *
   * @param adsRequest ADS campaign request payload
   * @param campaignId Campaign ID for log correlation
   * @return ADS submission response
   */
  private AdsSubmissionResponseDTO submitToAds(
      AdsCampaignRequestDTO adsRequest, String campaignId) {
    String url = mwPlannerProperties.getAds().getFullSubmitCampaignUrl();
    log.info("Submitting campaign to ADS URL: {}", url);

    // Prepare headers
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(securityContextService.getBearerToken());

    // Create HTTP entity
    HttpEntity<AdsCampaignRequestDTO> entity = new HttpEntity<>(adsRequest, headers);

    // Initialize response tracking for logging
    Integer responseCode = null;
    Object responseBody = null;

    try {
      long startTime = System.currentTimeMillis();

      ResponseEntity<AdsSubmissionResponseDTO> response =
          restTemplate.exchange(url, HttpMethod.POST, entity, AdsSubmissionResponseDTO.class);

      long endTime = System.currentTimeMillis();
      long duration = endTime - startTime;

      log.info("Successfully received response from ADS API in {} ms", duration);

      // Extract response details for logging
      responseCode = response.getStatusCode().value();
      AdsSubmissionResponseDTO actualResponse = response.getBody();
      responseBody = actualResponse;

      // Log to MongoDB (success case)
      adServerRequestLogService.saveLog(
          url, headers, adsRequest, responseCode, responseBody, campaignId);

      // Return the actual response
      if (actualResponse != null) {
        log.info(
            "ADS API Response - Total: {}, Successful: {}, Failed: {}",
            actualResponse.getTotal(),
            actualResponse.getSuccessful(),
            actualResponse.getFailed());
      }
      return actualResponse;

    } catch (HttpClientErrorException e) {
      // 4xx client errors
      responseCode = e.getStatusCode().value();
      responseBody = extractErrorBody(e);

      // Log to MongoDB (client error case)
      adServerRequestLogService.saveLog(
          url, headers, adsRequest, responseCode, responseBody, campaignId);

      log.error("Client error calling ADS API: {}", e.getStatusCode(), e);
      ErrorCode errorCode = mapHttpStatusToErrorCode(e.getStatusCode());
      throw new AdsApiException(
          errorCode, "Failed to submit campaign to ADS: " + e.getMessage(), e);

    } catch (HttpServerErrorException e) {
      // 5xx server errors
      responseCode = e.getStatusCode().value();
      responseBody = extractErrorBody(e);

      // Log to MongoDB (server error case)
      adServerRequestLogService.saveLog(
          url, headers, adsRequest, responseCode, responseBody, campaignId);

      log.error("Server error calling ADS API: {}", e.getStatusCode(), e);
      throw new AdsApiException(
          ErrorCode.ADS_API_ERROR, "ADS API server error: " + e.getMessage(), e);

    } catch (ResourceAccessException e) {
      // Network/timeout errors - no HTTP response available
      responseCode = 0; // Special code indicating network failure
      responseBody = Map.of("error", "NetworkError", "message", e.getMessage());

      // Log to MongoDB (network error case)
      adServerRequestLogService.saveLog(
          url, headers, adsRequest, responseCode, responseBody, campaignId);

      log.error("Timeout or connection error calling ADS API", e);
      throw new AdsApiException(
          ErrorCode.ADS_API_TIMEOUT,
          "Timeout or connection error calling ADS API: " + e.getMessage(),
          e);

    } catch (Exception e) {
      // Unexpected errors
      responseCode = -1; // Special code indicating unexpected error
      responseBody = Map.of("error", "UnexpectedError", "message", e.getMessage());

      // Log to MongoDB (unexpected error case)
      adServerRequestLogService.saveLog(
          url, headers, adsRequest, responseCode, responseBody, campaignId);

      log.error("Unexpected error calling ADS API", e);
      throw new AdsApiException(
          ErrorCode.ADS_API_ERROR, "Unexpected error calling ADS API: " + e.getMessage(), e);
    }
  }

  /**
   * Submit payload to PosterOps system
   *
   * @param adsRequest ADS campaign request containing static inventories
   * @return ADS submission response
   */
  private AdsSubmissionResponseDTO submitToPosterOps(AdsCampaignRequestDTO adsRequest) {
    String url = mwPlannerProperties.getPosterops().getFullIntakeUrl();
    log.info("Submitting campaign to PosterOps URL: {}", url);

    // Transform AdsCampaignRequestDTO to PosterOpsRequestDTO
    ExternalCampaignDTO campaign = adsRequest.getExternalPayload().getCampaign();
    List<ExternalInventoryDTO> inventories = adsRequest.getExternalPayload().getInventories();

    String startDate =
        campaign.getStartDate() != null && campaign.getStartDate().length() >= 10
            ? campaign.getStartDate().substring(0, 10)
            : null;

    String endDate =
        campaign.getEndDate() != null && campaign.getEndDate().length() >= 10
            ? campaign.getEndDate().substring(0, 10)
            : null;

    Double totalValue = 0.0;
    if (campaign.getBudgetSetup() != null && campaign.getBudgetSetup().getBudgetAmount() != null) {
      totalValue = campaign.getBudgetSetup().getBudgetAmount();
    }

    String clientName = "";
    if (campaign.getAdvertiser() != null && campaign.getAdvertiser().getName() != null) {
      clientName = campaign.getAdvertiser().getName();
    }

    String createdBy = "";
    if (campaign.getAccount() != null && campaign.getAccount().getUserId() != null) {
      createdBy = campaign.getAccount().getUserId();
    }

    String companyId = "";
    if (campaign.getAccount() != null && campaign.getAccount().getCompanyId() != null) {
      companyId = campaign.getAccount().getCompanyId();
    }

    List<PosterOpsBillboardDTO> billboards =
        inventories.stream()
            .map(
                inv -> {
                  String billboardId = inv.getAdUnitCode();
                  return PosterOpsBillboardDTO.builder().billboardId(billboardId).build();
                })
            .collect(Collectors.toList());

    PosterOpsRequestDTO posterOpsRequest =
        PosterOpsRequestDTO.builder()
            .plannerOrderId(campaign.getExternalId())
            .companyId(companyId)
            .campaignName(campaign.getName())
            .brandName(campaign.getBrand() != null ? campaign.getBrand() : "")
            .clientName(clientName)
            .clientType(campaign.getClientType())
            .campaignNote("")
            .startDate(startDate)
            .endDate(endDate)
            .totalValue(totalValue)
            .currency(campaign.getCurrency())
            .action("create")
            .callbackUrl(null)
            .billboards(billboards)
            .createdBy(createdBy)
            .build();

    try {
      // Serialize the body first, then sign the exact bytes that will be transmitted
      String requestBody = objectMapper.writeValueAsString(posterOpsRequest);
      String signature =
          computeHmacSignature(requestBody, mwPlannerProperties.getPosterops().getHmacSecret());

      // Prepare headers
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("X-MW-Signature", signature);

      // Create HTTP entity from the serialized body so the signed bytes match what is sent
      HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

      long startTime = System.currentTimeMillis();

      ResponseEntity<AdsSubmissionResponseDTO> response =
          restTemplate.exchange(url, HttpMethod.POST, entity, AdsSubmissionResponseDTO.class);

      long endTime = System.currentTimeMillis();
      long duration = endTime - startTime;

      log.info("Successfully received response from PosterOps API in {} ms", duration);

      AdsSubmissionResponseDTO responseBody = response.getBody();

      if (responseBody != null) {
        log.info(
            "PosterOps API Response - Total: {}, Successful: {}, Failed: {}",
            responseBody.getTotal(),
            responseBody.getSuccessful(),
            responseBody.getFailed());
      }
      return responseBody;

    } catch (HttpClientErrorException e) {
      log.error("Client error calling PosterOps API: {}", e.getStatusCode(), e);
      ErrorCode errorCode = mapHttpStatusToErrorCode(e.getStatusCode());
      throw new AdsApiException(
          errorCode, "Failed to submit campaign to PosterOps: " + e.getMessage(), e);
    } catch (HttpServerErrorException e) {
      log.error("Server error calling PosterOps API: {}", e.getStatusCode(), e);
      throw new AdsApiException(
          ErrorCode.ADS_API_ERROR, "PosterOps API server error: " + e.getMessage(), e);
    } catch (ResourceAccessException e) {
      log.error("Timeout or connection error calling PosterOps API", e);
      throw new AdsApiException(
          ErrorCode.ADS_API_TIMEOUT,
          "Timeout or connection error calling PosterOps API: " + e.getMessage(),
          e);
    } catch (Exception e) {
      log.error("Unexpected error calling PosterOps API", e);
      throw new AdsApiException(
          ErrorCode.ADS_API_ERROR, "Unexpected error calling PosterOps API: " + e.getMessage(), e);
    }
  }

  // ==================== Helper Methods ====================

  /**
   * Extract error body from HTTP exception for logging purposes. Attempts to parse as JSON, falls
   * back to raw string if parsing fails.
   *
   * @param e HTTP status code exception (4xx or 5xx)
   * @return Parsed error body as Object, or structured error map
   */
  private Object extractErrorBody(HttpStatusCodeException e) {
    String rawBody = e.getResponseBodyAsString();

    if (rawBody == null || rawBody.isBlank()) {
      return Map.of("error", e.getStatusCode().toString(), "message", e.getMessage());
    }

    try {
      // Try to parse as JSON for structured storage
      return objectMapper.readValue(rawBody, Object.class);
    } catch (Exception parseEx) {
      // If not valid JSON, return as string in a structured map
      return Map.of("rawError", rawBody);
    }
  }

  /*
   * Compute an HMAC-SHA256 signature of the given payload using the configured secret.
   *
   * @param payload the exact request body to sign
   * @param secret the shared HMAC secret
   * @return the signature as a lowercase hex string
   */
  private String computeHmacSignature(String payload, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(raw);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new AdsApiException(
          ErrorCode.ADS_API_ERROR,
          "Failed to compute PosterOps request signature: " + e.getMessage(),
          e);
    }
  }

  /** Map HTTP status to error code */
  private ErrorCode mapHttpStatusToErrorCode(HttpStatusCode statusCode) {
    if (statusCode.value() == 400) {
      return ErrorCode.ADS_API_BAD_REQUEST;
    } else if (statusCode.value() == 401) {
      return ErrorCode.ADS_API_UNAUTHORIZED;
    } else if (statusCode.value() == 403) {
      return ErrorCode.ADS_API_FORBIDDEN;
    } else if (statusCode.value() == 404) {
      return ErrorCode.ADS_API_NOT_FOUND;
    } else if (statusCode.value() == 408) {
      return ErrorCode.ADS_API_TIMEOUT;
    } else {
      return ErrorCode.ADS_API_ERROR;
    }
  }
}
