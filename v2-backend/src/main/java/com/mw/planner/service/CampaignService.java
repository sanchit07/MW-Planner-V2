package com.mw.planner.service;

import static com.mw.planner.constants.CampaignActivityKey.*;
import static com.mw.planner.service.InventoryService.getCpm;

import com.mw.brand.lib.dto.BrandResponseDTO;
import com.mw.brand.lib.repository.BrandRepository;
import com.mw.brand.lib.service.BrandService;
import com.mw.planner.domain.*;
import com.mw.planner.dto.*;
import com.mw.planner.enums.CostSplit;
import com.mw.planner.exception.BaseException;
import com.mw.planner.exception.campaign.*;
import com.mw.planner.exception.company.CompanyNotFoundException;
import com.mw.planner.repository.CampaignCommentsRepository;
import com.mw.planner.repository.CampaignRepository;
import com.mw.planner.repository.ScheduleRepository;
import com.mw.planner.service.config.DefaultConfigurationService;
import com.mw.planner.service.storage.CloudStorageService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class CampaignService {

  private final CampaignRepository campaignRepository;
  private final SequencerService sequencerService;
  private final MessageService messageService;
  private final BrandRepository brandRepository;
  private final DefaultConfigurationService defaultConfigurationService;
  private final BrandService brandService;
  private final InventoryService inventoryService;
  private final CampaignInventorySchedulesService campaignInventorySchedulesService;
  private final CustomFeeService customFeeService;
  private final MwMeasureService mwMeasureService;
  private final CountryService countryService;
  private final AgencyService agencyService;
  private final CompanyService companyService;
  private final CampaignCommentsRepository campaignCommentsRepository;
  private final CloudStorageService cloudStorageService;
  private final CampaignApprovalWorkflowService campaignApprovalWorkflowService;
  private final CampaignActivityService campaignActivityService;
  private final StateService stateService;
  private final DistrictService districtService;
  private final ScheduleRepository scheduleRepository;
  private final CampaignProposalStatusAndCommentService campaignProposalStatusAndCommentService;
  private final UserService userService;
  private final VenuesService venuesService;
  private final ReservationService reservationService;

  @org.springframework.beans.factory.annotation.Autowired private TestModeService testModeService;

  private static final String folder = "campaign-comments";

  public CampaignService(
      CampaignRepository campaignRepository,
      SequencerService sequencerService,
      MessageService messageService,
      BrandRepository brandRepository,
      DefaultConfigurationService defaultConfigurationService,
      MwMeasureService mwMeasureService,
      @Lazy CampaignInventorySchedulesService campaignInventorySchedulesService,
      CustomFeeService customFeeService,
      BrandService brandService,
      @Lazy InventoryService inventoryService,
      CountryService countryService,
      AgencyService agencyService,
      CampaignCommentsRepository campaignCommentsRepository,
      CloudStorageService cloudStorageService,
      CompanyService companyService,
      @Lazy CampaignApprovalWorkflowService campaignApprovalWorkflowService,
      CampaignActivityService campaignActivityService,
      StateService stateService,
      DistrictService districtService,
      UserService userService,
      @Lazy CampaignProposalStatusAndCommentService campaignProposalStatusAndCommentService,
      ScheduleRepository scheduleRepository,
      VenuesService venuesService) {
    this.campaignRepository = campaignRepository;
    this.sequencerService = sequencerService;
    this.messageService = messageService;
    this.brandRepository = brandRepository;
    this.defaultConfigurationService = defaultConfigurationService;
    this.mwMeasureService = mwMeasureService;
    this.campaignInventorySchedulesService = campaignInventorySchedulesService;
    this.customFeeService = customFeeService;
    this.brandService = brandService;
    this.inventoryService = inventoryService;
    this.countryService = countryService;
    this.agencyService = agencyService;
    this.companyService = companyService;
    this.campaignCommentsRepository = campaignCommentsRepository;
    this.cloudStorageService = cloudStorageService;
    this.campaignApprovalWorkflowService = campaignApprovalWorkflowService;
    this.campaignActivityService = campaignActivityService;
    this.stateService = stateService;
    this.districtService = districtService;
    this.scheduleRepository = scheduleRepository;
    this.userService = userService;
    this.campaignProposalStatusAndCommentService = campaignProposalStatusAndCommentService;
    this.venuesService = venuesService;
  }

  /** Create a new campaign */
  @Transactional
  public CampaignResponseDTO createCampaign(CampaignRequestDTO campaignRequestDTO) {
    log.debug("Creating new campaign with name: {}", campaignRequestDTO.getName());

    // Validate campaign data
    validateCampaignData(campaignRequestDTO);

    // Check if campaign with same name already exists
    if (campaignRepository.findByNameIgnoreCase(campaignRequestDTO.getName()).isPresent()) {
      throw new CampaignAlreadyExistsException(campaignRequestDTO.getName());
    }

    // Set default values
    if (campaignRequestDTO.getStatus() == null) {
      campaignRequestDTO.setStatus(Campaign.Status.DRAFT);
    }
    if (campaignRequestDTO.getCurrency() == null) {
      campaignRequestDTO.setCurrency("USD");
    }

    Campaign campaign = campaignRequestDTO.mapToEntity();
    enrichCampaignWithUserAndCompany(campaign);
    // Stamp the data partition from the caller's Test Mode; never trust the client.
    campaign.setDataMode(testModeService.getEffectiveDataMode());
    campaign.setPlanNumber(generatePlanNumber());
    Campaign savedCampaign = save(campaign);

    // Log campaign creation activity
    try {
      Map<String, Object> changes = campaignActivityService.buildCreationChanges(savedCampaign);
      campaignActivityService.logActivity(
          savedCampaign.getId(), CampaignActivityService.OperationType.CREATED, changes);
    } catch (Exception e) {
      log.warn("Failed to log campaign creation activity: {}", e.getMessage());
    }

    // Increment sequence for the campaign name after successful creation
    try {
      Long newSequence = sequencerService.incrementSequenceForCampaignName(savedCampaign.getName());
      log.debug(
          "Sequence incremented for campaign '{}' to: {}", savedCampaign.getName(), newSequence);
    } catch (Exception e) {
      log.error(
          "Failed to increment sequence for campaign '{}': {}",
          savedCampaign.getName(),
          e.getMessage(),
          e);
      // Don't fail the campaign creation if sequence increment fails
    }

    return CampaignResponseDTO.mapToDto(savedCampaign);
  }

  @CacheEvict(value = "campaigns", key = "#result.id")
  public Campaign save(Campaign campaign) {
    Campaign savedCampaign = campaignRepository.save(campaign);
    log.debug("Campaign created successfully with ID: {}", savedCampaign.getId());
    return savedCampaign;
  }

  private static final DateTimeFormatter PLAN_NUMBER_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd");

  /** Generates a plan number dated today — for campaigns being created right now. */
  private String generatePlanNumber() {
    return generatePlanNumber(LocalDate.now());
  }

  /**
   * Generates a 12-digit human-readable plan number: the given date (yyyyMMdd) plus a 4-digit
   * sequence that resets daily, e.g. "202607210001". The sequence is obtained atomically (see
   * {@link SequencerService#getNextSequenceAtomic}) so concurrent callers can't collide.
   *
   * <p>Takes an explicit date (rather than always using today) so the one-off backfill can date
   * legacy campaigns' plan numbers by when they were actually created, not by when the backfill
   * happened to run.
   */
  private String generatePlanNumber(LocalDate date) {
    String datePrefix = date.format(PLAN_NUMBER_DATE_FORMAT);
    Long sequence = sequencerService.getNextSequenceAtomic("PLAN_" + datePrefix);
    return datePrefix + String.format("%04d", sequence);
  }

  /**
   * One-off backfill: assigns a plan number to every existing campaign that doesn't have one
   * (created before this field existed). Dates each backfilled number by the campaign's own {@code
   * createdAt} (falling back to {@code startDate} if that's somehow absent), not by today's date,
   * so numbers stay chronologically meaningful. Batched/paginated but synchronous — this is cheap
   * in-process computation, unlike {@link CampaignPerformanceBackfillService}'s per-campaign
   * external Measure API calls, so it doesn't need that job's async/Redis-lock machinery.
   *
   * @param batchSize campaigns to fetch per page
   * @return counts of campaigns processed and actually assigned a number
   */
  public PlanNumberBackfillResultDTO backfillPlanNumbers(int batchSize) {
    long processed = 0;
    long assigned = 0;
    String lastId = null;

    while (true) {
      List<Campaign> batch = campaignRepository.findByPlanNumberIsNull(lastId, batchSize);
      if (batch.isEmpty()) {
        break;
      }
      for (Campaign campaign : batch) {
        processed++;
        LocalDate planDate =
            campaign.getCreatedAt() != null
                ? campaign.getCreatedAt().toLocalDate()
                : campaign.getStartDate();
        String planNumber = generatePlanNumber(planDate);
        if (campaignRepository.setPlanNumberIfNull(campaign.getId(), planNumber)) {
          assigned++;
        }
      }
      lastId = batch.get(batch.size() - 1).getId();
    }

    log.info("Plan number backfill complete: processed={}, assigned={}", processed, assigned);
    return PlanNumberBackfillResultDTO.builder().processed(processed).assigned(assigned).build();
  }

  @Cacheable(value = "campaigns", key = "#id")
  public Campaign findById(String id) {
    log.debug("Fetching campaign entity by ID: {}", id);
    return campaignRepository.findById(id).orElseThrow(() -> new CampaignNotFoundException(id));
  }

  /**
   * Load a campaign and enforce the caller's Test Mode partition (404 on cross-mode access). Use
   * this at every externally reachable campaign-ID boundary; {@link #findById(String)} stays
   * unchecked for internal/public-access flows.
   */
  public Campaign findByIdForCurrentMode(String id) {
    Campaign campaign = findById(id);
    assertCallerModeMatches(campaign);
    // Central tenant guard for every externally reachable by-ID load: participants only
    // (owner, shared companies, media owners with schedules). No-ops for system/public
    // flows where no acting company resolves, and for global admins.
    assertActingCompanyMayAccessCampaign(campaign, false);
    return campaign;
  }

  /**
   * Guards direct by-ID/by-name campaign access against the resolved acting company. Participants
   * are the owning (buyer) company, companies the campaign was shared with ({@code companyAccess}),
   * and media owners with schedules on the campaign. Non-participants get a 404 (existence is not
   * revealed); participants other than the owner get a 403 on writes. Skipped when no acting
   * company can be resolved (internal/system flows without a request context) and for global
   * admins.
   */
  /**
   * Guarded load for mutation paths: same participation guard as {@link
   * #findByIdForCurrentMode(String)}, plus owner-only write enforcement (shared/media-owner
   * participants get 403, unrelated companies 404).
   */
  public Campaign findByIdForCurrentModeForWrite(String id) {
    Campaign campaign = findByIdForCurrentMode(id);
    assertActingCompanyMayAccessCampaign(campaign, true);
    return campaign;
  }

  private void assertActingCompanyMayAccessCampaign(Campaign campaign, boolean write) {
    String actingCompanyId = userService.getActingCompanyId();
    if (actingCompanyId == null || userService.isCurrentUserGlobalAdmin()) {
      return;
    }
    boolean owner = actingCompanyId.equals(campaign.getCompanyId());
    if (owner) {
      return;
    }
    boolean shared =
        campaign.getCompanyAccess() != null
            && campaign.getCompanyAccess().contains(actingCompanyId);
    boolean mediaOwnerParticipant =
        !shared
            && campaignInventorySchedulesService.countByCampaignIdAndMediaOwnerId(
                    campaign.getId(), actingCompanyId)
                > 0;
    if (!shared && !mediaOwnerParticipant) {
      // Hide existence from unrelated companies — same semantics as a wrong data mode.
      throw new CampaignNotFoundException("Campaign not found with ID: " + campaign.getId());
    }
    if (write) {
      // Shared/media-owner participants may read, but only the owning company mutates.
      throw new org.springframework.security.access.AccessDeniedException(
          "Only the owning company may modify this campaign");
    }
  }

  /** Get campaign by ID */
  public CampaignResponseDTO getCampaignById(String id) {
    log.debug("Fetching campaign by ID: {}", id);
    Campaign campaign = findById(id);
    assertCallerModeMatches(campaign);
    assertActingCompanyMayAccessCampaign(campaign, false);
    UserResponseDTO user = userService.getUserById(campaign.getUserId());

    // Get userCompanyId from user context
    String userCompanyId = userService.getIamUserContext().getCompanyId();
    Campaign.Status status = resolveCampaignStatus(campaign, user, userCompanyId);
    campaign.setStatus(status);

    return CampaignResponseDTO.mapToDto(campaign)
        .withInventoryCount(getSelectedInventoryCount(campaign));
  }

  /**
   * Public-safe variant of {@link #getCampaignById(String)} for unauthenticated public-access
   * callers. Resolves display status without requiring a SecurityContext: the user lookup and IAM
   * user context are best-effort and fall back to the campaign's own companyId (same approach as
   * {@link #getCampaignMediaPlanDetails(String)} / prepareHeaderInfo).
   *
   * @param id Campaign ID
   * @return CampaignResponseDTO with resolved status and inventory count
   */
  public CampaignResponseDTO getCampaignByIdForPublicAccess(String id) {
    log.debug("Fetching campaign by ID for public access: {}", id);
    Campaign campaign = findById(id);

    UserResponseDTO user = null;
    try {
      user = userService.getUserById(campaign.getUserId());
    } catch (Exception e) {
      log.debug("Could not fetch user for public campaign, userId: {}", campaign.getUserId());
    }

    // Get userCompanyId from user context; fall back to campaign owner for unauthenticated callers
    String userCompanyId;
    try {
      userCompanyId = userService.getIamUserContext().getCompanyId();
    } catch (Exception e) {
      userCompanyId = campaign.getCompanyId();
    }

    Campaign.Status status = resolveCampaignStatus(campaign, user, userCompanyId);
    campaign.setStatus(status);

    return CampaignResponseDTO.mapToDto(campaign)
        .withInventoryCount(getSelectedInventoryCount(campaign));
  }

  private long getSelectedInventoryCount(Campaign campaign) {
    return campaignInventorySchedulesService.countByCampaignId(campaign.getId());
  }

  /** Get campaign by name */
  public CampaignResponseDTO getCampaignByName(String name) {
    log.debug("Fetching campaign by name: {}", name);
    Campaign campaign =
        campaignRepository
            .findByNameIgnoreCase(name)
            .orElseThrow(
                () -> new CampaignNotFoundException("Campaign not found with name: " + name));
    assertActingCompanyMayAccessCampaign(campaign, false);
    return CampaignResponseDTO.mapToDto(campaign);
  }

  // #p0 refers to the first parameter (index 0) and evict before method execution
  /** Update campaign */
  @Transactional
  @CacheEvict(value = "campaigns", key = "#p0", beforeInvocation = true)
  public CampaignResponseDTO updateCampaign(String id, CampaignRequestDTO campaignRequestDTO) {
    log.debug("Updating campaign with ID: {}", id);

    // Validate campaign data
    validateCampaignData(campaignRequestDTO);

    Campaign existingCampaign = findByIdForCurrentMode(id);
    assertActingCompanyMayAccessCampaign(existingCampaign, true);
    // Store old campaign state for comparison (used for history/activity logging)
    Campaign oldCampaign = copyCampaign(existingCampaign);

    // If campaign PLANNED then inventory must be selected
    if (Campaign.Status.PLANNED.equals(campaignRequestDTO.getStatus())) {
      long inventoryCount = campaignInventorySchedulesService.countByCampaignId(id);
      if (inventoryCount == 0) {
        throw new CampaignValidationException("At least one inventory must be selected.");
      }
    }

    // Check if name is being changed and if new name already exists
    if (!existingCampaign.getName().equalsIgnoreCase(campaignRequestDTO.getName())) {
      Optional<Campaign> campaignWithSameName =
          campaignRepository.findByNameIgnoreCase(campaignRequestDTO.getName());
      if (campaignWithSameName.isPresent() && !campaignWithSameName.get().getId().equals(id)) {
        throw new CampaignAlreadyExistsException(campaignRequestDTO.getName());
      }
    }
    campaignRequestDTO.setUserId(existingCampaign.getUserId());
    campaignRequestDTO.setCompanyId(existingCampaign.getCompanyId());

    Campaign.Status previousStatus = existingCampaign.getStatus();

    // Update campaign fields
    updateCampaignFields(existingCampaign, campaignRequestDTO);
    Campaign updatedCampaign = campaignRepository.save(existingCampaign);
    log.debug("Campaign updated successfully with ID: {}", updatedCampaign.getId());

    // When moving from REJECTED to PLANNED, reset workflow statuses so user can resubmit for
    // approval
    if (previousStatus == Campaign.Status.REJECTED
        && Campaign.Status.PLANNED.equals(campaignRequestDTO.getStatus())) {
      campaignApprovalWorkflowService.resetWorkflowStatusForResubmission(id);
    }

    // Log campaign update activity
    try {
      Map<String, Object> changes =
          campaignActivityService.buildUpdateChanges(oldCampaign, updatedCampaign);
      if (!changes.isEmpty()) {
        campaignActivityService.logActivity(
            updatedCampaign.getId(), CampaignActivityService.OperationType.UPDATED, changes);
      }
    } catch (Exception e) {
      log.warn("Failed to log campaign update activity: {}", e.getMessage());
    }

    return CampaignResponseDTO.mapToDto(updatedCampaign);
  }

  // #p0 refers to the first parameter (index 0) and evict before method execution
  /** Delete campaign */
  @CacheEvict(value = "campaigns", key = "#p0", beforeInvocation = true)
  public void deleteCampaign(String id) {
    log.debug("Deleting campaign with ID: {}", id);
    Campaign campaign = findByIdForCurrentMode(id);
    assertActingCompanyMayAccessCampaign(campaign, true);
    if (campaignValidForDeletionByStatus(campaign)) {
      campaignRepository.delete(campaign);
      log.debug("Campaign deleted successfully with ID: {}", id);
    } else {
      throw new CampaignInvalidStatusException(campaign.getStatus(), Campaign.Status.DRAFT);
    }
  }

  private boolean campaignValidForDeletionByStatus(Campaign campaign) {
    return campaign.getStatus().equals(Campaign.Status.DRAFT)
        || campaign.getStatus().equals(Campaign.Status.ARCHIVED)
        || campaign.getStatus().equals(Campaign.Status.PLANNED);
  }

  /** Get campaigns with optional filters */
  public Page<CampaignFilterResponseDTO> getCampaignsWithFilters(
      CampaignFilterDTO filter, Pageable pageable) {
    log.debug("Fetching campaigns with filters: {} and pagination: {}", filter, pageable);
    // Constrain the list to the caller's Test Mode partition (server-side, never client-driven).
    filter.setDataMode(testModeService.getEffectiveDataMode());
    Page<Campaign> campaigns = campaignRepository.findCampaignsWithFilters(filter, pageable);

    return campaigns.map(this::convertToCampaignFilterResponseDTO);
  }

  /**
   * Cross-mode by-ID access behaves as if the record does not exist (V1 Test Mode rule): a live
   * user must never see demo plans and vice versa.
   */
  private void assertCallerModeMatches(Campaign campaign) {
    if (!testModeService.matchesCallerMode(campaign)) {
      throw new CampaignNotFoundException(campaign.getId());
    }
  }

  /**
   * Same as {@link #assertCallerModeMatches(Campaign)} but skips the check for unauthenticated
   * callers (public share links / internal flows), which have no Test Mode partition of their own.
   */
  private void assertCallerModeMatchesIfAuthenticated(Campaign campaign) {
    if (!testModeService.hasAuthenticatedCaller()) {
      return;
    }
    assertCallerModeMatches(campaign);
  }

  /** Get campaign statistics, optionally filtered by campaign creation date range. */
  public CampaignStatistics getCampaignStatistics(
      String companyId, LocalDate startDate, LocalDate endDate) {
    log.debug(
        "Fetching campaign statistics for company ID: {}, startDate: {}, endDate: {}",
        companyId,
        startDate,
        endDate);
    // Constrain dashboard statistics to the caller's Test Mode partition.
    return campaignRepository.getCampaignStatisticsByCompanyId(
        companyId, startDate, endDate, testModeService.getEffectiveDataMode());
  }

  /**
   * Get campaigns for the company created within the given date range (campaign createdAt in
   * [startDate, endDate]), optionally filtered to a set of statuses.
   */
  public List<Campaign> getCampaignsByCompanyOverlappingDateRange(
      String companyId, LocalDate startDate, LocalDate endDate, List<Campaign.Status> statuses) {
    log.debug(
        "Fetching campaigns created within date range for company ID: {}, startDate: {}, endDate:"
            + " {}, statuses: {}",
        companyId,
        startDate,
        endDate,
        statuses);
    // Constrain dashboard aggregates to the caller's Test Mode partition.
    return campaignRepository.findCampaignsByCompanyIdOverlappingDateRange(
        companyId, startDate, endDate, statuses, testModeService.getEffectiveDataMode());
  }

  /** Validate campaign data */
  private void validateCampaignData(CampaignRequestDTO campaignRequestDTO) {
    // Validate start date is not in the past
    if (campaignRequestDTO.getStartDate() != null) {
      LocalDate today = LocalDate.now();
      if (campaignRequestDTO.getStartDate().isBefore(today)) {
        throw new CampaignValidationException(
            "startDate", campaignRequestDTO.getStartDate().toString());
      }
    }

    // Validate date range
    if (campaignRequestDTO.getStartDate() != null && campaignRequestDTO.getEndDate() != null) {
      if (campaignRequestDTO.getStartDate().isAfter(campaignRequestDTO.getEndDate())) {
        throw new CampaignDateRangeException(
            campaignRequestDTO.getStartDate(), campaignRequestDTO.getEndDate());
      }
    }

    // Validate budget
    if (campaignRequestDTO.getBudget() != null && campaignRequestDTO.getBudget() < 0) {
      throw new CampaignValidationException("budget", campaignRequestDTO.getBudget().toString());
    }

    // Validate client type and agency ID
    if (campaignRequestDTO.getClientType() == Campaign.ClientType.AGENCY
        && (campaignRequestDTO.getAgency() == null
            || campaignRequestDTO.getAgency().getId() == null
            || campaignRequestDTO.getAgency().getId().trim().isEmpty())) {
      throw new CampaignAgencyNotValidException();
    }

    // Validate goal type - ensure it's a valid enum value
    if (campaignRequestDTO.getGoals() != null) {
      // Verify the goalType is one of the valid enum values
      boolean isValidGoalType =
          Arrays.stream(Campaign.Goals.GoalType.values())
              .anyMatch(type -> type.equals(campaignRequestDTO.getGoals().getGoalType()));

      if (!isValidGoalType) {
        throw new CampaignInvalidGoalTypeException();
      }
    }
  }

  /** Update campaign fields from DTO */
  private void updateCampaignFields(Campaign campaign, CampaignRequestDTO dto) {
    campaign.setName(dto.getName());
    campaign.setDescription(dto.getDescription());
    campaign.setStatus(dto.getStatus());
    campaign.setBudget(dto.getBudget());
    campaign.setCurrency(dto.getCurrency());
    campaign.setStartDate(dto.getStartDate());
    campaign.setEndDate(dto.getEndDate());
    campaign.setUserId(dto.getUserId());
    campaign.setBrand(dto.getBrand());
    campaign.setClientType(dto.getClientType());
    campaign.setAgency(dto.getAgency());
    campaign.setCompanyId(dto.getCompanyId());
    campaign.setBudgetAllocation(dto.getBudgetAllocation());
    campaign.setDsp(dto.getDsp());
    if (dto.getMediaChannels() != null) {
      campaign.setMediaChannels(dto.getMediaChannels());
    }

    // Update nested objects
    if (dto.getGoals() != null) {
      Campaign.Goals goals =
          Campaign.Goals.builder()
              .goalType(dto.getGoals().getGoalType())
              .targetName(dto.getGoals().getTargetName())
              .targetValue(dto.getGoals().getTargetValue())
              .build();
      campaign.setGoals(goals);
    }

    if (dto.getPerformance() != null) {
      campaign.setPerformance(dto.getPerformance());
    }

    if (dto.getTargeting() != null) {
      Campaign.Targeting.TargetingBuilder targetingBuilder =
          Campaign.Targeting.builder()
              .demographics(dto.getTargeting().getDemographics())
              .signals(dto.getTargeting().getSignals())
              .programmaticOnly(dto.getTargeting().getProgrammaticOnly())
              .inventoryCluster(dto.getTargeting().getInventoryCluster());

      if (dto.getTargeting().getVenueTypes() != null) {
        targetingBuilder.venueTypes(
            Campaign.Targeting.VenueTypes.builder()
                .digitalOoh(dto.getTargeting().getVenueTypes().getDigitalOoh())
                .classicOoh(dto.getTargeting().getVenueTypes().getClassicOoh())
                .build());
      }

      if (dto.getTargeting().getGeofencing() != null) {
        Campaign.Targeting.Geofencing.GeofencingBuilder geofencingBuilder =
            Campaign.Targeting.Geofencing.builder();

        // Map geometries
        if (dto.getTargeting().getGeofencing().getGeometries() != null) {
          List<Campaign.Targeting.Geofencing.Geometry> campaignGeometries =
              dto.getTargeting().getGeofencing().getGeometries().stream()
                  .map(
                      geo ->
                          Campaign.Targeting.Geofencing.Geometry.builder()
                              .name(geo.getName())
                              .type(geo.getType())
                              .coordinates(geo.getCoordinates())
                              .isIncluded(geo.isIncluded())
                              .poi(geo.getPoi())
                              .metadata(geo.getMetadata())
                              .build())
                  .collect(Collectors.toList());
          geofencingBuilder.geometries(campaignGeometries);
        }

        // Map locations
        if (dto.getTargeting().getGeofencing().getLocations() != null) {
          List<Campaign.Targeting.Geofencing.Location> campaignLocations =
              dto.getTargeting().getGeofencing().getLocations().stream()
                  .map(
                      loc ->
                          Campaign.Targeting.Geofencing.Location.builder()
                              .name(loc.getName())
                              .lat(loc.getLat())
                              .lng(loc.getLng())
                              .radius(loc.getRadius())
                              .address(loc.getAddress())
                              .isIncluded(loc.isIncluded())
                              .poi(loc.getPoi())
                              .metadata(loc.getMetadata())
                              .build())
                  .collect(Collectors.toList());
          geofencingBuilder.locations(campaignLocations);
        }

        targetingBuilder.geofencing(geofencingBuilder.build());
      }

      campaign.setTargeting(targetingBuilder.build());
    }

    if (dto.getOptimization() != null) {
      Campaign.Optimization optimization =
          Campaign.Optimization.builder()
              .budgetAllocation(dto.getOptimization().getBudgetAllocation())
              .schedule(dto.getOptimization().getSchedule())
              .autoOptimize(dto.getOptimization().getAutoOptimize())
              .build();
      campaign.setOptimization(optimization);
    }

    enrichCampaignWithUserAndCompany(campaign);
  }

  private void enrichCampaignWithUserAndCompany(Campaign campaign) {
    try {
      IamUserContext userContext = userService.getIamUserContext();
      campaign.setUserEmail(userContext.getEmail());
      // Company details must follow the acting (switched) company so plans created while
      // switched carry consistent ownership + display details, never the primary company.
      String actingCompanyId = userService.getActingCompanyId();
      String companyId = actingCompanyId != null ? actingCompanyId : userContext.getCompanyId();
      CompanyLookupResponseDTO company = companyService.getCompanyLookupWithCompanyId(companyId);
      campaign.setCompanyDetails(
          Campaign.CompanyDetails.builder()
              .id(companyId)
              .name(company.getName())
              .seatId(company.getSeatId())
              .build());
    } catch (Exception e) {
      log.warn("Failed to enrich campaign with user/company details: {}", e.getMessage());
    }
  }

  /** Perform bulk actions on campaigns */
  @Transactional
  public CampaignBulkActionResponseDTO performBulkAction(
      CampaignBulkActionRequestDTO request, IamUserContext userContext) {
    log.debug(
        "Performing bulk action {} on {} campaigns",
        request.getAction(),
        request.getCampaignIds().size());

    List<String> successfulCampaignIds = new ArrayList<>();
    List<String> failedCampaignIds = new ArrayList<>();
    List<String> errorMessages = new ArrayList<>();
    List<String> newCampaignIds = new ArrayList<>();

    for (String campaignId : request.getCampaignIds()) {
      try {
        // Tenant guard: bulk mutations only on campaigns the acting company owns.
        assertActingCompanyMayAccessCampaign(findByIdForCurrentMode(campaignId), true);
        switch (request.getAction()) {
          case DUPLICATE:
            String newCampaignId = duplicateCampaign(campaignId, userContext.getId());
            successfulCampaignIds.add(campaignId);
            newCampaignIds.add(newCampaignId);
            break;
          case ARCHIVE:
            changeCampaignStatus(campaignId, Campaign.Status.ARCHIVED);
            successfulCampaignIds.add(campaignId);
            break;
          case DELETE:
            deleteCampaign(campaignId);
            successfulCampaignIds.add(campaignId);
            break;
          default:
            throw new IllegalArgumentException("Unsupported action: " + request.getAction());
        }
      } catch (BaseException baseException) {
        String message =
            messageService.getMessage(
                baseException.getErrorCode().getMessageKey(),
                userContext.getLocale(),
                baseException.getArgs());
        log.error(
            "Failed to perform action {} on campaign {}: {}",
            request.getAction(),
            campaignId,
            message);
        failedCampaignIds.add(campaignId);
        errorMessages.add(String.format("Campaign %s: %s", campaignId, message));
      }
    }

    return CampaignBulkActionResponseDTO.builder()
        .totalProcessed(request.getCampaignIds().size())
        .successCount(successfulCampaignIds.size())
        .failureCount(failedCampaignIds.size())
        .successfulCampaignIds(successfulCampaignIds)
        .failedCampaignIds(failedCampaignIds)
        .errorMessages(errorMessages)
        .newCampaignIds(newCampaignIds)
        .build();
  }

  /** Duplicate a campaign with a new name based on the pattern */
  private String duplicateCampaign(String campaignId, String userId) {
    Campaign originalCampaign =
        campaignRepository
            .findById(campaignId)
            .orElseThrow(() -> new CampaignNotFoundException(campaignId));
    assertCallerModeMatches(originalCampaign);

    // Generate new campaign name
    String newCampaignName = generateNewCampaignName(originalCampaign.getName());

    // Set dates for duplicated campaign: start date = today, end date = start date + 30 days
    LocalDate currentDate = LocalDate.now();
    LocalDate endDate = currentDate.plusDays(30);

    // Create new campaign based on original
    Campaign newCampaign =
        Campaign.builder()
            .name(newCampaignName)
            .planNumber(generatePlanNumber())
            .description(originalCampaign.getDescription())
            .status(Campaign.Status.DRAFT) // Always start duplicated campaigns as DRAFT
            .budget(originalCampaign.getBudget())
            .currency(originalCampaign.getCurrency())
            .startDate(currentDate)
            .endDate(endDate)
            .userId(userId)
            .brand(originalCampaign.getBrand())
            .clientType(originalCampaign.getClientType())
            .agency(originalCampaign.getAgency())
            .companyId(originalCampaign.getCompanyId())
            .countryId(originalCampaign.getCountryId())
            .goals(originalCampaign.getGoals())
            .targeting(originalCampaign.getTargeting())
            .budgetAllocation(originalCampaign.getBudgetAllocation())
            .optimization(originalCampaign.getOptimization())
            .skipRecommendation(originalCampaign.getSkipRecommendation())
            .build();

    Campaign savedCampaign = campaignRepository.save(newCampaign);
    log.debug(
        "Campaign duplicated successfully. Original: {}, New: {}",
        campaignId,
        savedCampaign.getId());

    // Increment sequence for the new campaign name
    try {
      sequencerService.incrementSequenceForCampaignName(savedCampaign.getName());
    } catch (Exception e) {
      log.error(
          "Failed to increment sequence for duplicated campaign '{}': {}",
          savedCampaign.getName(),
          e.getMessage());
    }

    return savedCampaign.getId();
  }

  @CacheEvict(value = "campaigns", key = "#campaignId")
  public void campaignCacheEvict(String campaignId) {
    log.debug("Campaign Cache Evict successfully: {}", campaignId);
  }

  /** Archive a campaign */
  @CacheEvict(value = "campaigns", key = "#campaignId")
  public void changeCampaignStatus(String campaignId, Campaign.Status status) {
    Campaign campaign =
        campaignRepository
            .findById(campaignId)
            .orElseThrow(() -> new CampaignNotFoundException(campaignId));
    assertCallerModeMatches(campaign);

    Campaign.Status oldStatus = campaign.getStatus();
    campaign.setStatus(status);
    campaignRepository.save(campaign);
    log.debug(
        "Campaign status changed from {} to {} for campaignId: {}", oldStatus, status, campaignId);

    // Log status change activity
    try {
      campaignActivityService.logActivity(
          campaignId,
          CampaignActivityService.OperationType.UPDATED,
          STATUS_FROM.key(),
          oldStatus.name(),
          STATUS_TO.key(),
          status.name());
    } catch (Exception e) {
      log.warn("Failed to log campaign status change activity: {}", e.getMessage());
    }

    // Reservation lifecycle side effects (PRD §9) — centralized here since every status
    // transition in the codebase funnels through this one method. Wrapped defensively so a
    // reservation-side failure never blocks the core campaign status change.
    try {
      if (oldStatus != status) {
        if (status == Campaign.Status.REVIEWING) {
          reservationService.createHoldRequestsForCampaign(
              campaignId, userService.getIamUserContext().getUserId());
        } else if (status == Campaign.Status.APPROVED) {
          reservationService.bookReservationsForCampaign(campaignId);
        } else if (status == Campaign.Status.REJECTED) {
          reservationService.releaseReservationsForCampaign(campaignId);
        }
      }
    } catch (Exception e) {
      log.warn(
          "Failed to apply reservation side effects for campaignId={} status={}: {}",
          campaignId,
          status,
          e.getMessage());
    }
  }

  // #p0 refers to the first parameter (index 0) and evict before method execution
  /** Autosave campaign draft */
  @CacheEvict(value = "campaigns", key = "#p0", beforeInvocation = true)
  public CampaignResponseDTO autosaveCampaign(
      String id, CampaignAutosaveRequestDTO autosaveRequestDTO) {
    log.debug("Autosaving campaign draft with ID: {}", id);

    Campaign existingCampaign =
        campaignRepository.findById(id).orElseThrow(() -> new CampaignNotFoundException(id));
    assertCallerModeMatches(existingCampaign);
    assertActingCompanyMayAccessCampaign(existingCampaign, true);

    EnumSet<Campaign.Status> allowedStatuses =
        EnumSet.of(
            Campaign.Status.DRAFT,
            Campaign.Status.PLANNED,
            Campaign.Status.NEGOTIATING,
            Campaign.Status.REJECTED);

    if (!allowedStatuses.contains(existingCampaign.getStatus())) {
      throw new CampaignInvalidStatusException(
          existingCampaign.getStatus(), "DRAFT/PLANNED/NEGOTIATING/REJECTED");
    }

    // Store old campaign state for comparison (only necessary fields)
    Campaign oldCampaign = copyCampaign(existingCampaign);

    // Detect changes before updating
    boolean countryChanged = isCountryChanged(existingCampaign, autosaveRequestDTO);
    boolean datesChanged = isCampaignDatesChanged(existingCampaign, autosaveRequestDTO);

    // Update only provided fields (partial update)
    updateCampaignFieldsForAutosave(existingCampaign, autosaveRequestDTO);

    // Save campaign first
    Campaign autosavedCampaign = campaignRepository.save(existingCampaign);
    log.debug("Campaign draft autosaved successfully with ID: {}", autosavedCampaign.getId());

    // Handle schedule cleanup if country or dates changed (after saving campaign)
    if (countryChanged || datesChanged) {
      handleScheduleCleanupForCampaignChange(id, countryChanged, datesChanged);
    }

    // Log autosave activity (non-blocking)
    logCampaignActivity(oldCampaign, autosavedCampaign);

    // Build response with brand and agency names (fetch in parallel if possible)
    return buildCampaignResponseDTO(autosavedCampaign);
  }

  /**
   * Check if country is being changed in the autosave request.
   *
   * @param existingCampaign Current campaign
   * @param autosaveRequestDTO Autosave request
   * @return true if country is being changed
   */
  private boolean isCountryChanged(
      Campaign existingCampaign, CampaignAutosaveRequestDTO autosaveRequestDTO) {
    return autosaveRequestDTO.getCountryId() != null
        && !Objects.equals(existingCampaign.getCountryId(), autosaveRequestDTO.getCountryId());
  }

  /**
   * Handle schedule cleanup when country or dates change. This method ensures all schedules
   * (CampaignInventorySchedules and Schedule entities) are properly cleared.
   *
   * @param campaignId Campaign ID
   * @param countryChanged Whether country was changed
   * @param datesChanged Whether dates were changed
   */
  private void handleScheduleCleanupForCampaignChange(
      String campaignId, boolean countryChanged, boolean datesChanged) {
    if (countryChanged) {
      log.info("Country changed for campaignId: {}, clearing all schedules", campaignId);
      // This removes both CampaignInventorySchedules and Schedule entities
      campaignInventorySchedulesService.removeAllInventoriesForCampaign(campaignId);
    } else if (datesChanged) {
      log.info("Dates changed for campaignId: {}, recreating schedules", campaignId);
      recreateSchedulesIfDatesChanged(campaignId);
    }
  }

  /**
   * Log campaign activity asynchronously (non-blocking).
   *
   * @param oldCampaign Old campaign state
   * @param newCampaign New campaign state
   */
  private void logCampaignActivity(Campaign oldCampaign, Campaign newCampaign) {
    try {
      Map<String, Object> changes =
          campaignActivityService.buildUpdateChanges(oldCampaign, newCampaign);
      if (!changes.isEmpty()) {
        campaignActivityService.logActivity(
            newCampaign.getId(), CampaignActivityService.OperationType.UPDATED, changes);
      }
    } catch (Exception e) {
      log.warn("Failed to log campaign autosave activity: {}", e.getMessage());
    }
  }

  /**
   * Build campaign response DTO with brand and agency names.
   *
   * @param campaign Campaign entity
   * @return CampaignResponseDTO
   */
  private CampaignResponseDTO buildCampaignResponseDTO(Campaign campaign) {
    return CampaignResponseDTO.mapToDto(campaign);
  }

  /** Update campaign fields for autosave (only non-null fields) */
  private void updateCampaignFieldsForAutosave(Campaign campaign, CampaignAutosaveRequestDTO dto) {
    if (dto.getName() != null) {
      campaignRepository
          .findByNameIgnoreCase(dto.getName())
          .filter(
              existingCampaignWithSameName ->
                  !existingCampaignWithSameName.getId().equals(campaign.getId()))
          .ifPresent(
              existingCampaignWithSameName -> {
                throw new CampaignAlreadyExistsException(dto.getName());
              });

      campaign.setName(dto.getName());
    }
    if (dto.getDescription() != null) {
      campaign.setDescription(dto.getDescription());
    }
    if (dto.getBudget() != null) {
      campaign.setBudget(dto.getBudget());
    }
    if (dto.getCurrency() != null) {
      campaign.setCurrency(dto.getCurrency());
    }
    if (dto.getStartDate() != null) {
      campaign.setStartDate(dto.getStartDate());
    }
    if (dto.getEndDate() != null) {
      campaign.setEndDate(dto.getEndDate());
    }
    if (dto.getBrand() != null) {
      campaign.setBrand(dto.getBrand());
    }
    if (dto.getClientType() != null) {
      campaign.setClientType(dto.getClientType());
    }
    if (dto.getAgency() != null) {
      campaign.setAgency(dto.getAgency());
    }
    if (dto.getCountryId() != null) {
      campaign.setCountryId(dto.getCountryId());
    }
    if (dto.getBudgetAllocation() != null) {
      campaign.setBudgetAllocation(dto.getBudgetAllocation());
    }
    if (dto.getMediaChannels() != null) {
      campaign.setMediaChannels(dto.getMediaChannels());
    }
    if (dto.getDsp() != null) {
      campaign.setDsp(dto.getDsp().orElse(null));
    }

    // Update nested objects only if provided
    if (dto.getGoals() != null) {
      Campaign.Goals goals =
          Campaign.Goals.builder()
              .goalType(dto.getGoals().getGoalType())
              .targetName(dto.getGoals().getTargetName())
              .targetValue(dto.getGoals().getTargetValue())
              .build();
      campaign.setGoals(goals);
    }

    if (dto.getTargeting() != null) {
      Campaign.Targeting existing = campaign.getTargeting();
      Campaign.Targeting.TargetingBuilder targetingBuilder =
          Campaign.Targeting.builder()
              .demographics(dto.getTargeting().getDemographics())
              .signals(dto.getTargeting().getSignals())
              .programmaticOnly(dto.getTargeting().getProgrammaticOnly())
              .inventoryCluster(dto.getTargeting().getInventoryCluster());

      if (dto.getTargeting().getVenueTypes() != null) {
        targetingBuilder.venueTypes(
            Campaign.Targeting.VenueTypes.builder()
                .digitalOoh(dto.getTargeting().getVenueTypes().getDigitalOoh())
                .classicOoh(dto.getTargeting().getVenueTypes().getClassicOoh())
                .build());
      } else if (existing != null && existing.getVenueTypes() != null) {
        targetingBuilder.venueTypes(existing.getVenueTypes());
      }

      if (dto.getTargeting().getGeofencing() != null) {
        Campaign.Targeting.Geofencing.GeofencingBuilder geofencingBuilder =
            Campaign.Targeting.Geofencing.builder();

        // Map geometries
        if (dto.getTargeting().getGeofencing().getGeometries() != null) {
          List<Campaign.Targeting.Geofencing.Geometry> campaignGeometries =
              dto.getTargeting().getGeofencing().getGeometries().stream()
                  .map(
                      geo ->
                          Campaign.Targeting.Geofencing.Geometry.builder()
                              .name(geo.getName())
                              .type(geo.getType())
                              .coordinates(geo.getCoordinates())
                              .isIncluded(geo.isIncluded())
                              .poi(geo.getPoi())
                              .metadata(geo.getMetadata())
                              .build())
                  .collect(Collectors.toList());
          geofencingBuilder.geometries(campaignGeometries);
        }

        // Map locations
        if (dto.getTargeting().getGeofencing().getLocations() != null) {
          List<Campaign.Targeting.Geofencing.Location> campaignLocations =
              dto.getTargeting().getGeofencing().getLocations().stream()
                  .map(
                      loc ->
                          Campaign.Targeting.Geofencing.Location.builder()
                              .name(loc.getName())
                              .lat(loc.getLat())
                              .lng(loc.getLng())
                              .radius(loc.getRadius())
                              .address(loc.getAddress())
                              .isIncluded(loc.isIncluded())
                              .poi(loc.getPoi())
                              .metadata(loc.getMetadata())
                              .build())
                  .collect(Collectors.toList());
          geofencingBuilder.locations(campaignLocations);
        }

        targetingBuilder.geofencing(geofencingBuilder.build());
      } else if (existing != null && existing.getGeofencing() != null) {
        targetingBuilder.geofencing(existing.getGeofencing());
      }

      campaign.setTargeting(targetingBuilder.build());
    }

    if (dto.getOptimization() != null) {
      Campaign.Optimization optimization =
          Campaign.Optimization.builder()
              .budgetAllocation(dto.getOptimization().getBudgetAllocation())
              .schedule(dto.getOptimization().getSchedule())
              .autoOptimize(dto.getOptimization().getAutoOptimize())
              .build();
      campaign.setOptimization(optimization);
    }
    if (dto.getSkipRecommendation() != null) {
      campaign.setSkipRecommendation(dto.getSkipRecommendation());
    }
    if (dto.getPerformance() != null) {
      campaign.setPerformance(dto.getPerformance());
    }
  }

  /** Generate new campaign name based on the pattern */
  private String generateNewCampaignName(String originalName) {
    // Extract prefix from original name if it follows the pattern
    String prefix = sequencerService.extractPrefixFromCampaignName(originalName);

    if (prefix != null) {
      // Original name follows pattern, get next sequence for the prefix
      Long nextSequence = sequencerService.getSequence(prefix);
      return prefix + String.format("%04d", nextSequence);
    } else {
      // Original name doesn't follow pattern, create new pattern-based name
      LocalDate now = LocalDate.now();
      String month = now.format(DateTimeFormatter.ofPattern("MMM"));
      String day = now.format(DateTimeFormatter.ofPattern("dd"));
      String year = now.format(DateTimeFormatter.ofPattern("yy"));

      String newPrefix = String.format("Campaign_%s_%s_%s_", month, day, year);
      Long nextSequence = sequencerService.getSequence(newPrefix);

      return newPrefix + String.format("%04d", nextSequence);
    }
  }

  /** Check if campaign exists by ID */
  public boolean existsById(String campaignId) {
    return campaignRepository.existsById(campaignId);
  }

  /**
   * Convert Campaign entity to CampaignFilterResponseDTO
   *
   * @param campaign the campaign entity to convert
   * @return CampaignFilterResponseDTO with mapped fields
   */
  public CampaignFilterResponseDTO convertToCampaignFilterResponseDTO(Campaign campaign) {
    // Get user context for status calculation
    UserResponseDTO user = userService.getUserById(campaign.getUserId());
    // Get userCompanyId from user context
    String userCompanyId = userService.getIamUserContext().getCompanyId();
    Campaign.Status status = resolveCampaignStatus(campaign, user, userCompanyId);

    CampaignFilterResponseDTO.CampaignFilterResponseDTOBuilder builder =
        CampaignFilterResponseDTO.builder()
            .id(campaign.getId())
            .name(campaign.getName())
            .planNumber(campaign.getPlanNumber())
            .status(status.name())
            .startDate(campaign.getStartDate())
            .endDate(campaign.getEndDate())
            .budget(campaign.getBudget())
            .currency(campaign.getCurrency())
            .isNegotiated(campaign.getIsNegotiated())
            .dataMode(campaign.getDataMode() == null ? "live" : campaign.getDataMode());

    // Map Goals
    if (campaign.getGoals() != null && campaign.getGoals().getGoalType() != null) {
      CampaignResponseDTO.Goals goals =
          CampaignResponseDTO.Goals.builder()
              .goalType(campaign.getGoals().getGoalType())
              .targetName(campaign.getGoals().getTargetName())
              .targetValue(campaign.getGoals().getTargetValue())
              .typeName(campaign.getGoals().getTypeName())
              .build();
      builder.goals(goals);
    }

    if (campaign.getBrand() != null) {
      builder.brandName(campaign.getBrand().getName());
    }

    builder.agencyName(campaign.getAgency() != null ? campaign.getAgency().getName() : null);
    try {
      CampaignForecastDTO campaignForecastDTO = calculateCampaignForecast(campaign);
      builder.inventory(campaignForecastDTO.getTotalInventories());
      builder.totalCost(campaignForecastDTO.getTotalCost());
      builder.estimatedImpression(campaignForecastDTO.getEstimatedImpression());
      builder.estimatedReach(campaignForecastDTO.getEstimatedReach());
      builder.sov(campaignForecastDTO.getSov());
      builder.totalSot(campaignForecastDTO.getTotalSot());
      builder.plannedSot(campaignForecastDTO.getPlannedSot());
    } catch (Exception e) {
      log.warn("Unable to fetch campaign forecast details");
    }
    if (user != null) {
      builder.userName(user.getUsername());
      builder.firstName(user.getFirstName());
      builder.lastName(user.getLastName());
      if (user.getCurrentCompany() != null && user.getCurrentCompany().getName() != null) {
        builder.companyName(user.getCurrentCompany().getName());
      }
    }

    // Map current company fields from campaign
    if (campaign.getCurrentCompanyId() != null) {
      builder.currentCompanyId(campaign.getCurrentCompanyId());
    }
    if (campaign.getCurrentCompanyName() != null) {
      builder.currentCompanyName(campaign.getCurrentCompanyName());
    }

    return builder.build();
  }

  /**
   * Bulk calculate total proposed cost for dashboard ranking (no IAM/brand/status lookups).
   *
   * <p>This method batches the expensive lookups (CampaignInventorySchedules, Schedule, CustomFee,
   * Inventory) across all campaigns to avoid per-campaign DB/query fan-out.
   *
   * @param campaigns campaigns to calculate costs for
   * @return map of campaignId to totalCost (0.0 when no schedules; null when an unexpected error
   *     prevented calculation)
   */
  public Map<String, Double> calculateTotalCostsForDashboard(List<Campaign> campaigns) {
    if (campaigns == null || campaigns.isEmpty()) {
      return Map.of();
    }
    List<Campaign> valid = campaigns.stream().filter(c -> c != null && c.getId() != null).toList();
    if (valid.isEmpty()) {
      return Map.of();
    }

    try {
      List<String> campaignIds = valid.stream().map(Campaign::getId).distinct().toList();

      // 1) Load all CampaignInventorySchedules in one query.
      List<CampaignInventorySchedules> allInventorySchedules =
          campaignInventorySchedulesService.findByCampaignIds(campaignIds);
      Map<String, List<CampaignInventorySchedules>> schedulesByCampaignId =
          allInventorySchedules.stream()
              .filter(Objects::nonNull)
              .collect(Collectors.groupingBy(CampaignInventorySchedules::getCampaignId));

      // 2) Load all Inventories once (bulk).
      List<String> allInventoryIds =
          allInventorySchedules.stream()
              .map(CampaignInventorySchedules::getInventoryId)
              .filter(id -> id != null && !id.isBlank())
              .distinct()
              .toList();
      Map<String, Inventory> inventoryMap = new HashMap<>();
      if (!allInventoryIds.isEmpty()) {
        for (Inventory inv : inventoryService.findAllByIds(allInventoryIds)) {
          if (inv != null && inv.getId() != null) {
            inventoryMap.put(inv.getId(), inv);
          }
        }
      }

      // 3) Load all Schedules once (bulk), reuse via a global scheduleMap.
      List<String> allScheduleIds =
          allInventorySchedules.stream()
              .filter(s -> s.getScheduleIds() != null && !s.getScheduleIds().isEmpty())
              .flatMap(s -> s.getScheduleIds().stream())
              .filter(id -> id != null && !id.isBlank())
              .distinct()
              .toList();
      Map<String, Schedule> scheduleMap = new HashMap<>();
      if (!allScheduleIds.isEmpty()) {
        for (Schedule s : scheduleRepository.findAllById(allScheduleIds)) {
          if (s != null && s.getId() != null) {
            scheduleMap.put(s.getId(), s);
          }
        }
      }

      // 4) Load CustomFeesContext in bulk (2 repository queries total).
      Map<String, CustomFeesContext> customFeesByCampaignId =
          customFeeService.getActiveCustomFeesContextForCampaigns(valid);

      String userCompanyId = userService.getActingCompanyId();

      Map<String, Double> totals = new HashMap<>();
      for (Campaign campaign : valid) {
        List<CampaignInventorySchedules> schedules =
            schedulesByCampaignId.getOrDefault(campaign.getId(), List.of());
        if (schedules.isEmpty()) {
          totals.put(campaign.getId(), 0.0);
          continue;
        }

        CustomFeesContext customFeesContext =
            customFeesByCampaignId.getOrDefault(
                campaign.getId(), CustomFeesContext.builder().build());

        double totalCost = 0.0;
        for (CampaignInventorySchedules s : schedules) {
          Inventory inv = inventoryMap.get(s.getInventoryId());
          if (inv == null) {
            continue;
          }
          Double cost =
              campaignInventorySchedulesService.calculateCampaignInventorySchedulesProposedPrice(
                  s, inv, campaign, userCompanyId, customFeesContext, scheduleMap);
          totalCost += Optional.ofNullable(cost).orElse(0.0);
        }
        totals.put(campaign.getId(), totalCost);
      }

      return Map.copyOf(totals);
    } catch (Exception e) {
      log.warn("Unable to bulk-calculate totalCost for dashboard", e);
      return Map.of();
    }
  }

  // Resolve campaign status
  public Campaign.Status resolveCampaignStatus(
      Campaign campaign, UserResponseDTO user, String userCompanyId) {

    Campaign.Status status = campaign.getStatus();

    if (Campaign.Status.REVIEWING.equals(status)) {
      // For MediaOwners: REVIEWING → PLANNED (if not maintainer)
      if (user != null
          && user.getActiveCompanyId() != null
          && !campaignApprovalWorkflowService.isMaintainer(campaign, user.getActiveCompanyId())) {
        status = Campaign.Status.PLANNED;
      }

      // Find media owner proposal status
      Optional<CampaignProposalStatus> proposalStatus =
          Optional.ofNullable(
              campaignProposalStatusAndCommentService.getProposalsByCampaignIdAndMediaOwnerId(
                  campaign.getId(), userCompanyId));

      CampaignProposalStatus.Status mediaOwnerProposalStatus =
          proposalStatus.map(CampaignProposalStatus::getStatus).orElse(null);
      // Override campaign status based on media owner proposal status
      if (mediaOwnerProposalStatus != null) {
        switch (mediaOwnerProposalStatus) {
          case PENDING -> status = Campaign.Status.REVIEWING;
          case APPROVED -> status = Campaign.Status.APPROVED;
          case REJECTED -> status = Campaign.Status.REJECTED;
          default -> {}
        }
      }
    }

    return status;
  }

  /**
   * Calculate forecast metrics for a campaign.
   *
   * @param campaign Campaign details to calculate forecast for
   * @return ForecastResponseDTO with forecast metrics
   */
  public CampaignForecastDTO calculateCampaignForecast(Campaign campaign) {
    return calculateCampaignForecast(campaign, false);
  }

  /**
   * Calculate the campaign forecast. When {@code forceRegenerate} is {@code true}, any stored
   * performance snapshot is bypassed and the forecast is recomputed from the current schedules.
   *
   * @param forceRegenerate when true, skip the stored-snapshot short-circuit and recompute
   */
  public CampaignForecastDTO calculateCampaignForecast(Campaign campaign, boolean forceRegenerate) {
    log.info("Calculating forecast for campaignId: {}", campaign.getId());
    // Fast path: a complete, non-media-owner snapshot only needs the schedule COUNT (not the full
    // list) to be validated. This avoids loading potentially thousands of
    // CampaignInventorySchedules
    // documents per campaign (e.g. on the /campaigns list render) when the stored snapshot is still
    // valid. The count mirrors getCampaignInventorySchedulesByCampaignId's company/media-owner
    // branching so the short-circuit decision is identical to computing it from schedules.size().
    if (!forceRegenerate
        && campaign != null
        && isCompleteSnapshot(campaign.getPerformance())
        && !isMediaOwner(campaign)
        && !isCampaignNegotiated(campaign)) {
      long currentCount = countCampaignInventorySchedulesByCampaignId(campaign);
      if (campaign.getPerformance().getTotalInventories() != null
          && campaign.getPerformance().getTotalInventories() == currentCount) {
        log.info(
            "Returning stored campaign performance snapshot for campaignId: {}", campaign.getId());
        return campaign.getPerformance();
      }
    }
    // Slow path: load the full list and delegate to the core calculation.
    List<CampaignInventorySchedules> schedules =
        getCampaignInventorySchedulesByCampaignId(campaign);
    log.info("Found {} inventory schedules for campaignId: {}", schedules.size(), campaign.getId());
    return calculateCampaignForecast(campaign, schedules, forceRegenerate);
  }

  /**
   * Calculate the campaign forecast, optionally filtered to a set of media owners. When {@code
   * request.mediaOwnerIds} is non-empty the forecast is computed only from the schedules belonging
   * to those media owners (direct filter); otherwise behavior is identical to {@link
   * #calculateCampaignForecast(Campaign)}.
   *
   * @param request Optional media-owner filter (may be null)
   */
  public CampaignForecastDTO calculateCampaignForecast(
      Campaign campaign, MediaOwnerFilterRequestDTO request) {
    return calculateCampaignForecast(campaign, request, false);
  }

  /**
   * Media-owner-filtered forecast that can bypass the stored snapshot. When {@code forceRegenerate}
   * is {@code true}, the stored performance snapshot is skipped and the forecast is recomputed.
   *
   * @param forceRegenerate when true, skip the stored-snapshot short-circuit and recompute
   */
  public CampaignForecastDTO calculateCampaignForecast(
      Campaign campaign, MediaOwnerFilterRequestDTO request, boolean forceRegenerate) {
    if (request == null
        || request.getMediaOwnerIds() == null
        || request.getMediaOwnerIds().isEmpty()) {
      return calculateCampaignForecast(campaign, forceRegenerate);
    }
    log.info(
        "Calculating forecast for campaignId: {} filtered by {} mediaOwnerIds",
        campaign.getId(),
        request.getMediaOwnerIds().size());
    List<CampaignInventorySchedules> schedules =
        campaignInventorySchedulesService.findByCampaignIdAndMediaOwnerIdIn(
            campaign.getId(), request.getMediaOwnerIds());
    return calculateCampaignForecast(campaign, schedules, forceRegenerate);
  }

  /** Helper class to hold reach, impressions, and frequency calculation results. */
  private record ReachFrequencyResult(long reach, long impressions, double frequency) {}

  /**
   * Calculates total reach, impressions, and frequency from all schedules across all inventories.
   * This method aggregates impressions and reach from all Schedule entities referenced by the
   * CampaignInventorySchedules.
   *
   * <p>For each CampaignInventorySchedules:
   *
   * <ul>
   *   <li>Retrieves all Schedule entities using scheduleIds
   *   <li>Sums impressions from all schedules
   *   <li>Sums reach from all schedules
   * </ul>
   *
   * <p>Frequency is calculated as: totalImpressions / totalReach
   *
   * @param schedules List of CampaignInventorySchedules containing scheduleIds
   * @return ReachFrequencyResult with aggregated reach, impressions, and calculated frequency
   */
  /**
   * Overload with no schedule map; fetches schedules in one batch. Use when schedule map is not
   * already built.
   */
  private ReachFrequencyResult calculateReachFrequencyFromSchedules(
      List<CampaignInventorySchedules> schedules) {
    return calculateReachFrequencyFromSchedules(schedules, null);
  }

  /**
   * When scheduleMap is non-null, uses it to resolve Schedule entities (no DB call). When null,
   * fetches all schedules in one batch.
   */
  private ReachFrequencyResult calculateReachFrequencyFromSchedules(
      List<CampaignInventorySchedules> schedules, Map<String, Schedule> scheduleMap) {
    // Build inventory map for API call
    Map<String, Inventory> inventoryMap =
        schedules.stream()
            .filter(s -> s.getInventoryId() != null)
            .collect(
                Collectors.toMap(
                    CampaignInventorySchedules::getInventoryId,
                    s -> inventoryService.getById(s.getInventoryId()),
                    (a, b) -> a));

    // Calculate campaign duration (inclusive)
    int duration = 0;
    java.time.LocalDate campaignStartDate = null;
    java.time.LocalDate campaignEndDate = null;
    if (!schedules.isEmpty()) {
      CampaignInventorySchedules first = schedules.get(0);
      Campaign campaign = campaignRepository.findById(first.getCampaignId()).orElse(null);
      if (campaign != null && campaign.getStartDate() != null && campaign.getEndDate() != null) {
        campaignStartDate = campaign.getStartDate();
        campaignEndDate = campaign.getEndDate();
        duration =
            (int) java.time.temporal.ChronoUnit.DAYS.between(campaignStartDate, campaignEndDate)
                + 1;
      }
    }

    // Call MW Measure API. If the external service is unavailable, degrade gracefully to the
    // schedule-stored impressions/reach instead of failing the whole plan detail page.
    long totalImpressions;
    long totalReach;
    double frequency;
    boolean estimatedFallback = false;
    try {
      com.mw.planner.dto.MeasureReachFrequencyResponseDTO response =
          mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
              duration, schedules, inventoryMap, campaignStartDate, campaignEndDate);
      totalImpressions = response.getImpressions() != null ? response.getImpressions() : 0L;
      totalReach = response.getReach() != null ? response.getReach() : 0L;
      frequency = response.getFrequency() != null ? response.getFrequency() : 0.0;
    } catch (Exception e) {
      log.warn(
          "MW Measure API unavailable ({}); falling back to schedule-stored reach/impressions",
          e.getMessage());
      Map<String, Schedule> resolved = scheduleMap;
      if (resolved == null) {
        List<String> ids =
            schedules.stream()
                .filter(s -> s.getScheduleIds() != null)
                .flatMap(s -> s.getScheduleIds().stream())
                .distinct()
                .toList();
        resolved = new HashMap<>();
        for (Schedule sch : scheduleRepository.findAllById(ids)) {
          if (sch != null && sch.getId() != null) {
            resolved.put(sch.getId(), sch);
          }
        }
      }
      long impSum = 0L;
      long reachSum = 0L;
      for (CampaignInventorySchedules cis : schedules) {
        if (cis.getScheduleIds() == null) {
          continue;
        }
        for (String sid : cis.getScheduleIds()) {
          Schedule sch = resolved.get(sid);
          if (sch != null) {
            impSum += sch.getImpressions() != null ? sch.getImpressions() : 0L;
            reachSum += sch.getReach() != null ? sch.getReach() : 0L;
          }
        }
      }
      totalImpressions = impSum;
      // NOTE: summing per-schedule reach ignores cross-site audience overlap, so this is an
      // upper-bound ESTIMATE of deduplicated reach (and frequency a lower bound). The measured
      // values come only from the MW Measure API; this branch just keeps the page usable.
      totalReach = reachSum;
      frequency = reachSum > 0 ? (double) impSum / reachSum : 0.0;
      estimatedFallback = true;
    }

    log.debug(
        "Calculated reach: {}, impressions: {}, frequency: {} from {} ({} schedules)",
        totalReach,
        totalImpressions,
        frequency,
        estimatedFallback
            ? "schedule-sum fallback (ESTIMATED, Measure API unavailable)"
            : "MW Measure API",
        schedules.size());

    return new ReachFrequencyResult(totalReach, totalImpressions, frequency);
  }

  /**
   * @param campaign campaign id
   * @return For Agency/Internal get all inventory schedules and for MediaOwner Prepare just for
   *     their inventories
   */
  private List<CampaignInventorySchedules> getCampaignInventorySchedulesByCampaignId(
      Campaign campaign) {
    try {
      // Scope strictly by the resolved acting company: other memberships the user holds
      // must not widen visibility (dual-member user switched into a media owner).
      String actingCompanyId = userService.getActingCompanyId();
      if (actingCompanyId != null && !actingCompanyId.equals(campaign.getCompanyId())) {
        return campaignInventorySchedulesService.findByCampaignIdAndMediaOwnerId(
            campaign.getId(), actingCompanyId);
      }
    } catch (Exception e) {
      log.debug(
          "No user context available, returning all schedules for campaign: {}", campaign.getId());
    }
    return campaignInventorySchedulesService.findByCampaignId(campaign.getId());
  }

  /**
   * Counts the campaign inventory schedules that {@link
   * #getCampaignInventorySchedulesByCampaignId(Campaign)} would load, without materializing the
   * list. The company/media-owner branching is kept identical so the count equals what {@code
   * schedules.size()} would be — enabling the snapshot fast path in {@link
   * #calculateCampaignForecast(Campaign, boolean)}.
   */
  private long countCampaignInventorySchedulesByCampaignId(Campaign campaign) {
    try {
      String actingCompanyId = userService.getActingCompanyId();
      if (actingCompanyId != null && !actingCompanyId.equals(campaign.getCompanyId())) {
        return campaignInventorySchedulesService.countByCampaignIdAndMediaOwnerId(
            campaign.getId(), actingCompanyId);
      }
    } catch (Exception e) {
      log.debug(
          "No user context available, counting all schedules for campaign: {}", campaign.getId());
    }
    return campaignInventorySchedulesService.countByCampaignId(campaign.getId());
  }

  /**
   * Whether the current caller views this campaign as a media owner (a company granted access to a
   * campaign it does not own). Media owners always recompute the forecast for their own view rather
   * than returning the owner's stored snapshot.
   */
  private boolean isMediaOwner(Campaign campaign) {
    String primaryId = userService.getActingCompanyId();
    return campaign.getCompanyId() != primaryId
        && campaign.getCompanyAccess() != null
        && campaign.getCompanyAccess().contains(primaryId);
  }

  public CampaignMediaPlanResponseDTO getCampaignMediaPlanDetails(String id) {
    log.debug("Fetching campaign media plan details for campaign ID: {}", id);
    // Implementation goes here
    Campaign campaign = findById(id);
    assertCallerModeMatchesIfAuthenticated(campaign);
    // Skips automatically for unauthenticated public-token access (no acting company).
    assertActingCompanyMayAccessCampaign(campaign, false);
    CampaignMediaPlanResponseDTO campaignMediaPlanResponseDTO =
        CampaignMediaPlanResponseDTO.builder().build();

    // Get all campaign inventory schedule
    List<CampaignInventorySchedules> schedules =
        getCampaignInventorySchedulesByCampaignId(campaign);
    // Calculate forecast metrics
    CampaignForecastDTO campaignForecastDTO = calculateCampaignForecast(campaign, schedules);

    // prepare and Set campaign performance metrics
    campaignMediaPlanResponseDTO.setCampaignForecast(campaignForecastDTO);

    // prepare and set header info
    campaignMediaPlanResponseDTO.setHeaderInfoDTO(prepareHeaderInfo(campaign));

    // Set brand details from campaign
    if (campaign.getBrand() != null) {
      BrandResponseDTO brandDTO = new BrandResponseDTO();
      brandDTO.setId(campaign.getBrand().getId());
      brandDTO.setName(campaign.getBrand().getName());
      campaignMediaPlanResponseDTO.setBrandResponseDTO(brandDTO);
    }

    // prepare and set Audience Demographics Targeting Strategy
    campaignMediaPlanResponseDTO.setAudienceDemographicsTargetingStrategyDTO(
        prepareAudienceDemographicsTargetingStrategy(campaign));

    // prepare and set Schedules
    campaignMediaPlanResponseDTO.setSchedulesDTO(prepareSchedule(schedules));

    return campaignMediaPlanResponseDTO;
  }

  private HeaderInfoDTO prepareHeaderInfo(Campaign campaign) {
    UserResponseDTO user = null;
    try {
      user = userService.getUserById(campaign.getUserId());
    } catch (Exception e) {
      log.debug("Could not fetch user for media plan header, userId: {}", campaign.getUserId());
    }
    String preparedBy = user != null ? user.getFirstName() + " " + user.getLastName() : "Unknown";

    // Get userCompanyId from user context; fall back to campaign owner for unauthenticated callers
    String userCompanyId;
    try {
      userCompanyId = userService.getIamUserContext().getCompanyId();
    } catch (Exception e) {
      userCompanyId = campaign.getCompanyId();
    }
    Campaign.Status status = resolveCampaignStatus(campaign, user, userCompanyId);
    return HeaderInfoDTO.builder()
        .id(campaign.getId())
        .name(campaign.getName())
        .planNumber(campaign.getPlanNumber())
        .startDate(campaign.getStartDate().toString())
        .endDate(campaign.getEndDate().toString())
        .budget(campaign.getBudget())
        .status(status.name())
        .duration(calculateDuration(campaign))
        .currency(campaign.getCurrency())
        .preparedBy(preparedBy)
        .createdAt(campaign.getCreatedAt() != null ? campaign.getCreatedAt().toString() : null)
        .goalType(
            campaign.getGoals() != null && campaign.getGoals().getGoalType() != null
                ? campaign.getGoals().getGoalType().name()
                : null)
        .targetValue(campaign.getGoals() != null ? campaign.getGoals().getTargetValue() : null)
        .companyDetails(campaign.getCompanyDetails())
        .userEmail(campaign.getUserEmail())
        .dsp(campaign.getDsp())
        .isNegotiated(campaign.getIsNegotiated())
        .build();
  }

  private AudienceDemographicsTargetingStrategyDTO prepareAudienceDemographicsTargetingStrategy(
      Campaign campaign) {
    AudienceDemographicsTargetingStrategyDTO audienceDemographicsTargetingStrategyDTO =
        new AudienceDemographicsTargetingStrategyDTO();
    if (campaign.getTargeting() != null) {
      Map<String, List<String>> demographics = campaign.getTargeting().getDemographics();
      if (demographics != null && !demographics.isEmpty()) {
        if (demographics.containsKey("age")) {
          audienceDemographicsTargetingStrategyDTO.setAgeGroups(demographics.get("age"));
        }
        if (demographics.containsKey("interests")) {
          audienceDemographicsTargetingStrategyDTO.setInterests(demographics.get("interests"));
        }
        if (demographics.containsKey("behavior")) {
          audienceDemographicsTargetingStrategyDTO.setLifestyle(demographics.get("behavior"));
        }
        if (demographics.containsKey("income")) {
          audienceDemographicsTargetingStrategyDTO.setIncomeLevel(demographics.get("income"));
        }
      }
    }
    return audienceDemographicsTargetingStrategyDTO;
  }

  public CampaignForecastDTO calculateCampaignForecast(
      Campaign campaign, List<CampaignInventorySchedules> schedules) {
    return calculateCampaignForecast(campaign, schedules, false);
  }

  /**
   * Core forecast calculation. When {@code forceRegenerate} is {@code true}, the stored performance
   * snapshot is bypassed and the forecast is always recomputed from {@code schedules}.
   *
   * @param forceRegenerate when true, skip the stored-snapshot short-circuit and recompute
   */
  public CampaignForecastDTO calculateCampaignForecast(
      Campaign campaign, List<CampaignInventorySchedules> schedules, boolean forceRegenerate) {
    boolean isMediaOwner = isMediaOwner(campaign);

    // PL3-I17: only trust the stored snapshot when it is complete. Snapshots persisted before
    // SOV/SOT existed (or sent by an FE that omitted them) lack these fields; fall through to
    // recompute via buildForecast so the numbers stay consistent with what was calculated on save.
    // Media owners still bypass the stored snapshot (recompute for their view).
    // A caller may also force a recompute via forceRegenerate, bypassing the snapshot entirely.
    if (!forceRegenerate
        && campaign != null
        && isCompleteSnapshot(campaign.getPerformance())
        && matchesCurrentInventoryCount(campaign.getPerformance(), schedules)
        && !isMediaOwner
        && !isCampaignNegotiated(campaign)) {
      log.info(
          "Returning stored campaign performance snapshot for campaignId: {}", campaign.getId());
      return campaign.getPerformance();
    }

    if (schedules == null || schedules.isEmpty()) {
      return emptyForecast();
    }

    try {
      var context = prepareContext(campaign, schedules);
      var aggregation = aggregate(context);
      return buildForecast(context, aggregation);

    } catch (Exception e) {
      log.error("Error calculating forecast for campaignId: {}", campaign.getId(), e);
      throw new RuntimeException("Failed to calculate campaign forecast", e);
    }
  }

  /**
   * A stored performance snapshot is only safe to return verbatim when it carries the SOV/SOT
   * fields. Older snapshots predate these fields (PL3-I17) and must be recomputed.
   */
  private boolean isCompleteSnapshot(CampaignForecastDTO performance) {
    return performance != null
        && performance.getSov() != null
        && performance.getPlannedSot() != null
        && performance.getTotalSot() != null;
  }

  /**
   * A negotiated campaign's performance snapshot may be stale relative to the just-accepted prices
   * — always recompute rather than trust the stored snapshot. Null-safe: campaigns persisted before
   * this field existed deserialize it as null, which must be treated as "not negotiated".
   */
  private boolean isCampaignNegotiated(Campaign campaign) {
    return Boolean.TRUE.equals(campaign.getIsNegotiated());
  }

  /**
   * The stored snapshot is only valid if its inventory count still matches the campaign's current
   * schedules. A mismatch (or a null totalInventories) means inventories were selected/deselected
   * after the snapshot was persisted — recompute instead of returning stale numbers.
   */
  private boolean matchesCurrentInventoryCount(
      CampaignForecastDTO performance, List<CampaignInventorySchedules> schedules) {
    int currentCount = schedules == null ? 0 : schedules.size();
    return performance.getTotalInventories() != null
        && performance.getTotalInventories() == currentCount;
  }

  private ForecastContext prepareContext(
      Campaign campaign, List<CampaignInventorySchedules> schedules) {

    var inventoryMap =
        schedules.stream()
            .map(s -> inventoryService.getById(s.getInventoryId()))
            .collect(Collectors.toUnmodifiableMap(Inventory::getId, Function.identity()));

    // Build schedule map once for all schedules (one DB call; reused for reachFrequency and
    // per-schedule contribution)
    List<String> allScheduleIds =
        schedules.stream()
            .filter(s -> s.getScheduleIds() != null && !s.getScheduleIds().isEmpty())
            .flatMap(s -> s.getScheduleIds().stream())
            .distinct()
            .toList();
    Map<String, Schedule> scheduleMap = new HashMap<>();
    if (!allScheduleIds.isEmpty()) {
      List<Schedule> allSchedules = scheduleRepository.findAllById(allScheduleIds);
      for (Schedule s : allSchedules) {
        if (s != null && s.getId() != null) {
          scheduleMap.put(s.getId(), s);
        }
      }
    }

    var reachFrequency = calculateReachFrequencyFromSchedules(schedules, scheduleMap);

    CustomFeesContext customFeesContext =
        customFeeService.getActiveCustomFeesContextForCampaign(campaign);

    return new ForecastContext(
        campaign,
        List.copyOf(schedules),
        inventoryMap,
        reachFrequency,
        userService.getActingCompanyId(),
        customFeesContext,
        scheduleMap);
  }

  private ForecastAggregation aggregate(ForecastContext context) {

    return context.schedules().stream()
        .map(schedule -> calculateScheduleContribution(context, schedule))
        .reduce(
            new ForecastAggregation(0, 0, 0, 0, 0, 0, 0),
            (a, b) ->
                new ForecastAggregation(
                    a.totalCost() + b.totalCost(),
                    a.totalSot() + b.totalSot(),
                    a.plannedSot() + b.plannedSot(),
                    a.totalAdPlays() + b.totalAdPlays(),
                    a.totalCpm() + b.totalCpm(),
                    a.sovWeightedSum() + b.sovWeightedSum(),
                    a.sovWeight() + b.sovWeight()));
  }

  private ForecastAggregation calculateScheduleContribution(
      ForecastContext context, CampaignInventorySchedules schedule) {

    var inventory = context.inventoryMap().get(schedule.getInventoryId());

    var forecast =
        campaignInventorySchedulesService.prepareInventoryForecastForCampaignInventorySchedules(
            schedule, context.scheduleMap(), inventory);

    var cost =
        campaignInventorySchedulesService.calculateCampaignInventorySchedulesProposedPrice(
            schedule,
            inventory,
            context.campaign(),
            context.userCompanyId(),
            context.customFeesContext(),
            context.scheduleMap());

    var cpm = Optional.ofNullable(getCpm(inventory)).orElse(0.0);

    var totalCost = Optional.ofNullable(cost).orElse(0.0);
    var totalSot = Optional.ofNullable(forecast.getTotalSot()).orElse(0.0);
    var plannedSot = Optional.ofNullable(forecast.getPlannedSot()).orElse(0.0);
    var totalAdPlays = Optional.ofNullable(forecast.getEstimatedAdPlays()).orElse(0L);
    // This inventory's own SOV (already classification-aware), weighted by its planned airtime —
    // summed and divided in buildForecast so a mixed classic+digital campaign blends proportionally
    // to how much airtime each inventory represents, instead of averaging SOV percentages flatly.
    var inventorySov = Optional.ofNullable(forecast.getSov()).orElse(0.0);
    var sovWeightedSum = inventorySov * plannedSot;

    return new ForecastAggregation(
        totalCost, totalSot, plannedSot, totalAdPlays, cpm, sovWeightedSum, plannedSot);
  }

  private CampaignForecastDTO buildForecast(
      ForecastContext context, ForecastAggregation aggregation) {

    var totalInventories = context.schedules().size();

    var reach = context.reachFrequency().reach();
    var impressions = context.reachFrequency().impressions();
    var frequency = context.reachFrequency().frequency();

    var avgCpm = totalInventories > 0 ? aggregation.totalCpm() / totalInventories : 0.0;

    var avgECpm = impressions > 0 ? (aggregation.totalCost() / impressions) * 1000 : 0.0;

    var sov =
        aggregation.sovWeight() > 0 ? aggregation.sovWeightedSum() / aggregation.sovWeight() : 0.0;

    return CampaignForecastDTO.builder()
        .totalInventories(totalInventories)
        .avgCpm(avgCpm)
        .avgECpm(avgECpm)
        .estimatedAdPlays(aggregation.totalAdPlays())
        .estimatedFrequency(frequency)
        .estimatedImpression(impressions)
        .estimatedReach(reach)
        .sov(sov)
        .totalCost(aggregation.totalCost())
        .plannedSot(aggregation.plannedSot())
        .totalSot(aggregation.totalSot())
        .build();
  }

  private CampaignForecastDTO emptyForecast() {
    return CampaignForecastDTO.builder()
        .totalInventories(0)
        .avgCpm(0.0)
        .avgECpm(0.0)
        .estimatedAdPlays(0L)
        .estimatedFrequency(0.0)
        .estimatedImpression(0L)
        .estimatedReach(0L)
        .sov(0.0)
        .totalCost(0.0)
        .plannedSot(0.0)
        .totalSot(0.0)
        .build();
  }

  record ForecastAggregation(
      double totalCost,
      double totalSot,
      double plannedSot,
      long totalAdPlays,
      double totalCpm,
      double sovWeightedSum,
      double sovWeight) {}

  record ForecastContext(
      Campaign campaign,
      List<CampaignInventorySchedules> schedules,
      Map<String, Inventory> inventoryMap,
      ReachFrequencyResult reachFrequency,
      String userCompanyId,
      CustomFeesContext customFeesContext,
      Map<String, Schedule> scheduleMap) {}

  private SchedulesDTO prepareSchedule(List<CampaignInventorySchedules> schedules) {
    SchedulesDTO schedulesDTO = new SchedulesDTO();
    try {
      // Define dayparts with their hour ranges
      Map<String, Set<Integer>> daypartHours = new LinkedHashMap<>();
      daypartHours.put("06:00-10:00", Set.of(6, 7, 8, 9));
      daypartHours.put("10:00-14:00", Set.of(10, 11, 12, 13));
      daypartHours.put("14:00-18:00", Set.of(14, 15, 16, 17));
      daypartHours.put("18:00-22:00", Set.of(18, 19, 20, 21));
      daypartHours.put("22:00-06:00", Set.of(22, 23, 0, 1, 2, 3, 4, 5));

      // Map to store total hour counts per daypart
      Map<String, Long> daypartCounts = new LinkedHashMap<>();
      for (String daypart : daypartHours.keySet()) {
        daypartCounts.put(daypart, 0L);
      }

      // Iterate through all schedules and their booking matrices
      long totalHours = 0L;
      for (CampaignInventorySchedules schedule : schedules) {
        // Fetch schedules by IDs
        List<Schedule> scheduleItems = Collections.emptyList();
        if (schedule.getScheduleIds() != null && !schedule.getScheduleIds().isEmpty()) {
          scheduleItems = scheduleRepository.findAllById(schedule.getScheduleIds());
        }
        for (Schedule scheduleItem : scheduleItems) {
          if (scheduleItem.getBookingMatrix() != null) {
            // Iterate through all dates in booking matrix
            for (List<Integer> hours : scheduleItem.getBookingMatrix().values()) {
              if (hours != null) {
                for (Integer hour : hours) {
                  if (hour != null && hour >= 0 && hour <= 23) {
                    totalHours++;
                    // Find which daypart this hour belongs to
                    for (Map.Entry<String, Set<Integer>> entry : daypartHours.entrySet()) {
                      if (entry.getValue().contains(hour)) {
                        daypartCounts.put(entry.getKey(), daypartCounts.get(entry.getKey()) + 1);
                        break;
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Calculate percentage distribution
      Map<String, Double> dailySchedule =
          getStringDoubleMap(totalHours, daypartCounts, daypartHours);

      schedulesDTO.setDailySchedule(dailySchedule);
    } catch (Exception e) {
      log.error(
          "Error preparing schedule for campaign {}: {}",
          schedules != null && !schedules.isEmpty()
              ? schedules.getFirst().getCampaignId()
              : "unknown",
          e.getMessage(),
          e);
    }
    return schedulesDTO;
  }

  private Map<String, Double> getStringDoubleMap(
      long totalHours, Map<String, Long> daypartCounts, Map<String, Set<Integer>> daypartHours) {
    Map<String, Double> dailySchedule = new LinkedHashMap<>();
    if (totalHours > 0) {
      for (Map.Entry<String, Long> entry : daypartCounts.entrySet()) {
        double percentage = (entry.getValue().doubleValue() / totalHours) * 100.0;
        // Round to 2 decimal places
        percentage = Math.round(percentage * 100.0) / 100.0;
        dailySchedule.put(entry.getKey(), percentage);
      }
    } else {
      // If no hours found, set all dayparts to 0.0
      for (String daypart : daypartHours.keySet()) {
        dailySchedule.put(daypart, 0.0);
      }
    }
    return dailySchedule;
  }

  public CampaignViewResponseDTO getCampaignViewDetails(String id) {
    log.debug("Fetching campaign view details for campaign ID: {}", id);
    // Implementation goes here
    Campaign campaign = findById(id);
    assertCallerModeMatches(campaign);
    assertActingCompanyMayAccessCampaign(campaign, false);

    List<Inventory> inventories = new ArrayList<>();
    List<CampaignInventorySchedules> schedules =
        getCampaignInventorySchedulesByCampaignId(campaign);

    // Fetch inventories for the campaign
    if (!schedules.isEmpty()) {
      List<String> inventoryIds =
          schedules.stream().map(CampaignInventorySchedules::getInventoryId).toList();
      inventories =
          inventoryIds.stream().map(inventoryService::getById).collect(Collectors.toList());
    }

    // Calculate forecast metrics
    CampaignForecastDTO campaignForecastDTO = calculateCampaignForecast(campaign, schedules);

    // Get user context for status calculation
    UserResponseDTO user = userService.getUserById(campaign.getUserId());

    // Get userCompanyId from user context
    String userCompanyId = userService.getIamUserContext().getCompanyId();

    Campaign.Status status = resolveCampaignStatus(campaign, user, userCompanyId);

    // prepare and set basic campaign info
    CampaignViewResponseDTO campaignViewResponseDTO =
        CampaignViewResponseDTO.builder()
            .id(campaign.getId())
            .name(campaign.getName())
            .planNumber(campaign.getPlanNumber())
            .status(status.name())
            .currency(campaign.getCurrency())
            .isNegotiated(campaign.getIsNegotiated())
            .build();

    // prepare and Set campaign details
    campaignViewResponseDTO.setCampaignDetail(prepareCampaignDetails(campaign));
    // prepare and set Key Stakeholder Details
    campaignViewResponseDTO.setCampaignKeyStakeholderDetail(prepareKeyStakeholderDetails(campaign));

    // prepare and set Goals
    campaignViewResponseDTO.setGoals(prepareGoalsDetails(campaign, campaignForecastDTO, schedules));

    // prepare and set Campaign targeting strategy - Audience Demographics
    campaignViewResponseDTO.setTargeting(prepareTargetingDetails(campaign));
    // prepare and set Inventory Overview
    campaignViewResponseDTO.setInventoryOverview(prepareInventoryOverview(campaign, inventories));
    // prepare and set Campaign Performance Metrics
    campaignViewResponseDTO.setCampaignForecast(campaignForecastDTO);
    // prepare and set Cost Breakdown — only totalCost is populated; the other fields
    // (mediaCost/platformFee/netCost/customFees) have no campaign-level aggregation yet
    campaignViewResponseDTO.setCostBreakdown(
        CampaignViewResponseDTO.CostBreakdown.builder()
            .totalCost(campaignForecastDTO.getTotalCost())
            .build());

    return campaignViewResponseDTO;
  }

  private CampaignViewResponseDTO.CampaignDetail prepareCampaignDetails(Campaign campaign) {
    CampaignViewResponseDTO.CampaignDetail campaignDetail =
        CampaignViewResponseDTO.CampaignDetail.builder().build();
    try {
      campaignDetail =
          CampaignViewResponseDTO.CampaignDetail.builder()
              .country(countryService.getCountryByName(campaign.getCountryId()).getName())
              .budget(campaign.getBudget())
              .startDate(campaign.getStartDate().toString())
              .endDate(campaign.getEndDate().toString())
              .build();
    } catch (Exception e) {
      log.error(
          "Error preparing campaign details for campaign {}: {}", campaign.getId(), e.getMessage());
    }
    return campaignDetail;
  }

  private CampaignViewResponseDTO.CampaignKeyStakeholderDetail prepareKeyStakeholderDetails(
      Campaign campaign) {
    CampaignViewResponseDTO.CampaignKeyStakeholderDetail keyStakeholderDetail =
        CampaignViewResponseDTO.CampaignKeyStakeholderDetail.builder().build();
    try {
      String agencyName = null, brandName = null, brandCategory = null;

      // Fetch user from IAM API (with caching)
      UserResponseDTO user = userService.getUserById(campaign.getUserId());

      if (campaign.getAgency() != null) {
        agencyName = campaign.getAgency().getName();
      }
      if (campaign.getBrand() != null) {
        brandName = campaign.getBrand().getName();
        if (campaign.getBrand().getCategories() != null) {
          brandCategory =
              campaign.getBrand().getCategories().stream()
                  .max(Comparator.comparingInt(c -> c.getTier() != null ? c.getTier() : 0))
                  .map(Campaign.CampaignBrand.IabCategory::getFullPath)
                  .orElse(null);
        }
      }
      keyStakeholderDetail =
          CampaignViewResponseDTO.CampaignKeyStakeholderDetail.builder()
              .planner(user.getFirstName() + " " + user.getLastName())
              .agency(agencyName)
              .brand(brandName)
              .brandCategory(brandCategory)
              .build();
    } catch (Exception e) {
      log.error(
          "Error preparing key stakeholder details for campaign {}: {}",
          campaign.getId(),
          e.getMessage());
    }
    return keyStakeholderDetail;
  }

  private CampaignViewResponseDTO.Targeting prepareTargetingDetails(Campaign campaign) {

    CampaignViewResponseDTO.Targeting.TargetingBuilder targetingBuilder =
        CampaignViewResponseDTO.Targeting.builder();
    try {
      targetingBuilder.audienceDemographicsTargetingStrategyDTO(
          prepareAudienceDemographicsTargetingStrategy(campaign));
    } catch (Exception e) {
      log.error(
          "Error preparing targeting details for campaign {}: {}",
          campaign.getId(),
          e.getMessage());
      return targetingBuilder.build();
    }
    return targetingBuilder.build();
  }

  private CampaignViewResponseDTO.InventoryOverview prepareInventoryOverview(
      Campaign campaign, List<Inventory> inventories) {
    CampaignViewResponseDTO.InventoryOverview inventoryOverview =
        CampaignViewResponseDTO.InventoryOverview.builder().build();
    try {
      Set<String> formatTypes =
          inventories.stream().map(Inventory::getFormat).collect(Collectors.toSet());

      Set<String> types = inventories.stream().map(Inventory::getType).collect(Collectors.toSet());

      Map<String, List<Inventory>> byCity =
          inventories.stream()
              .collect(
                  Collectors.groupingBy(
                      inv ->
                          inv.getLocation().getCity() != null
                              ? inv.getLocation().getCity()
                              : "Unknown"));

      inventoryOverview.setTotalInventories(inventories.size());
      inventoryOverview.setTotalTypes(types.size());
      inventoryOverview.setTotalFormats(formatTypes.size());
      inventoryOverview.setTotalCity(byCity.size());
    } catch (Exception e) {
      log.error(
          "Error preparing inventory overview for campaign {}: {}",
          campaign.getId(),
          e.getMessage());
    }
    return inventoryOverview;
  }

  /**
   * Retrieves cost split details for a campaign grouped by the given split type.
   *
   * @param campaignId unique identifier of the campaign
   * @param splitBy dimension to split campaign cost by
   * @return list of cost split details for the campaign
   * @throws CampaignNotFoundException if the campaign does not exist
   */
  public List<CostSplitByResponseDTO> getCampaignCostSplitBy(
      String campaignId, CostSplit splitBy, Locale locale) {

    log.debug("Fetching campaign cost split by {} for campaign ID: {}", splitBy, campaignId);

    if (!existsById(campaignId)) {
      throw new CampaignNotFoundException("Campaign not found with id: " + campaignId);
    }

    Campaign campaign = findByIdForCurrentMode(campaignId);
    return calculateCostSplitBy(campaign, splitBy, locale);
  }

  /**
   * Calculates cost split metrics for a campaign by grouping inventory schedules using the provided
   * cost split dimension.
   *
   * @param campaign campaign domain object
   * @param splitBy dimension to split campaign cost by
   * @return list of cost split response objects
   */
  private List<CostSplitByResponseDTO> calculateCostSplitBy(
      Campaign campaign, CostSplit splitBy, Locale locale) {

    var schedules = getCampaignInventorySchedulesByCampaignId(campaign);

    if (schedules.isEmpty()) {
      log.debug("No schedules found for campaign: {}", campaign.getId());
      return List.of();
    }

    var inventoryMap = buildInventoryMap(schedules);

    var groupedSchedules = groupSchedulesBySplitField(schedules, inventoryMap, splitBy);

    CustomFeesContext customFeesContext =
        customFeeService.getActiveCustomFeesContextForCampaign(campaign);

    // Build schedule map once from all CampaignInventorySchedules (one DB call instead of N)
    List<String> allScheduleIds =
        groupedSchedules.values().stream()
            .flatMap(List::stream)
            .filter(s -> s.getScheduleIds() != null && !s.getScheduleIds().isEmpty())
            .flatMap(s -> s.getScheduleIds().stream())
            .distinct()
            .toList();
    Map<String, Schedule> scheduleMap = new HashMap<>();
    if (!allScheduleIds.isEmpty()) {
      List<Schedule> allSchedules = scheduleRepository.findAllById(allScheduleIds);
      for (Schedule s : allSchedules) {
        if (s != null && s.getId() != null) {
          scheduleMap.put(s.getId(), s);
        }
      }
    }

    var costByGroup =
        calculateCostByGroup(
            campaign, groupedSchedules, inventoryMap, customFeesContext, scheduleMap);

    return buildCostSplitResponse(campaign, splitBy, groupedSchedules, costByGroup, locale);
  }

  /**
   * Calculates total cost for each split group of inventory schedules.
   *
   * <p>Cost is calculated per group and stored in a map keyed by split value. Any null or negative
   * cost values are normalized to zero.
   *
   * @param campaign campaign domain object
   * @param groupedSchedules schedules grouped by split value
   * @param inventoryMap inventory lookup map
   * @return map of split value to total cost
   */
  private Map<String, Double> calculateCostByGroup(
      Campaign campaign,
      Map<String, List<CampaignInventorySchedules>> groupedSchedules,
      Map<String, Inventory> inventoryMap,
      CustomFeesContext customFeesContext,
      Map<String, Schedule> scheduleMap) {

    Map<String, Double> costByGroup = new HashMap<>();

    groupedSchedules.forEach(
        (splitValue, schedules) -> {
          Double cost =
              calculateGroupCost(campaign, schedules, inventoryMap, customFeesContext, scheduleMap);
          costByGroup.put(splitValue, cost != null && cost > 0 ? cost : 0.0);
        });

    return costByGroup;
  }

  /**
   * Builds cost split response objects containing cost, forecast metrics, inventory count, and
   * optional population data.
   *
   * <p>Percentage values are calculated using double precision without rounding.
   *
   * @param campaign campaign domain object
   * @param splitBy dimension used for cost split
   * @param groupedSchedules schedules grouped by split value
   * @param costByGroup cost calculated per split group
   * @return immutable list of cost split response DTOs
   */
  private List<CostSplitByResponseDTO> buildCostSplitResponse(
      Campaign campaign,
      CostSplit splitBy,
      Map<String, List<CampaignInventorySchedules>> groupedSchedules,
      Map<String, Double> costByGroup,
      Locale locale) {

    double totalCost = costByGroup.values().stream().mapToDouble(Double::doubleValue).sum();

    List<CostSplitByResponseDTO> response = new ArrayList<>();

    groupedSchedules.forEach(
        (splitValue, schedules) -> {
          double groupCost = costByGroup.getOrDefault(splitValue, 0.0);

          CampaignForecastDTO forecast = calculateCampaignForecast(campaign, schedules);

          int inventoryCount = countDistinctInventories(schedules);

          double percentage = totalCost > 0 ? (groupCost / totalCost) * 100 : 0.0;

          String displayName =
              splitBy == CostSplit.VENUE_TYPE
                  ? venuesService.getLocalizedVenueName(splitValue, locale)
                  : splitValue;

          var builder =
              CostSplitByResponseDTO.builder()
                  .name(displayName)
                  .totalAmount(groupCost)
                  .totalAmountInPercentage(percentage)
                  .impressions(forecast.getEstimatedImpression())
                  .reach(forecast.getEstimatedReach())
                  .frequency(forecast.getEstimatedFrequency())
                  .avgCpm(forecast.getAvgCpm())
                  .totalInventories(inventoryCount);

          if (splitBy.supportsPopulation()) {
            builder.population(getPopulationForSplitValue(splitValue, splitBy));
          }

          response.add(builder.build());
        });

    return List.copyOf(response);
  }

  /**
   * Counts the number of distinct inventories within a list of schedules.
   *
   * @param schedules list of campaign inventory schedules
   * @return number of unique inventory IDs
   */
  private int countDistinctInventories(List<CampaignInventorySchedules> schedules) {

    return (int)
        schedules.stream().map(CampaignInventorySchedules::getInventoryId).distinct().count();
  }

  /**
   * Builds a map of Inventory objects keyed by inventoryId from the given campaign inventory
   * schedules.
   *
   * @param inventorySchedules list of campaign inventory schedules
   * @return map of inventoryId to Inventory object
   */
  private Map<String, Inventory> buildInventoryMap(
      List<CampaignInventorySchedules> inventorySchedules) {

    return inventorySchedules.stream()
        .map(CampaignInventorySchedules::getInventoryId)
        .distinct()
        .collect(Collectors.toMap(Function.identity(), inventoryService::getById));
  }

  /**
   * Calculates the total cost for a group of schedules.
   *
   * @param campaign The campaign
   * @param schedules List of schedules in the group
   * @param inventoryMap Map of inventory ID to Inventory
   * @param scheduleMap Optional pre-loaded schedule map; when provided avoids per-item DB calls
   * @return Total cost for the group
   */
  private Double calculateGroupCost(
      Campaign campaign,
      List<CampaignInventorySchedules> schedules,
      Map<String, Inventory> inventoryMap,
      CustomFeesContext customFeesContext,
      Map<String, Schedule> scheduleMap) {
    double totalCost = 0.0;
    boolean hasAnyPrice = false;

    for (CampaignInventorySchedules schedule : schedules) {
      Inventory inventory = inventoryMap.get(schedule.getInventoryId());
      if (inventory == null) {
        continue;
      }
      String userCompanyId = userService.getActingCompanyId();
      Double baseCost =
          campaignInventorySchedulesService.calculateCampaignInventorySchedulesProposedPrice(
              schedule, inventory, campaign, userCompanyId, customFeesContext, scheduleMap);
      if (baseCost != null) {
        totalCost += baseCost;
        hasAnyPrice = true;
      }
    }

    return hasAnyPrice ? totalCost : null;
  }

  /**
   * Groups schedules by the split field value (country, state, city, etc.)
   *
   * @param schedules List of all schedules
   * @param inventoryMap Map of inventory ID to Inventory
   * @param splitBy The split type
   * @return Map of split value to list of schedules
   */
  private Map<String, List<CampaignInventorySchedules>> groupSchedulesBySplitField(
      List<CampaignInventorySchedules> schedules,
      Map<String, Inventory> inventoryMap,
      CostSplit splitBy) {
    Map<String, List<CampaignInventorySchedules>> grouped = new HashMap<>();

    for (CampaignInventorySchedules schedule : schedules) {
      Inventory inventory = inventoryMap.get(schedule.getInventoryId());
      if (inventory == null) {
        continue;
      }

      String splitValue = getSplitFieldValue(inventory, splitBy);
      if (splitValue == null) {
        splitValue = "Unknown";
      }

      grouped.computeIfAbsent(splitValue, k -> new ArrayList<>()).add(schedule);
    }

    return grouped;
  }

  /**
   * Gets the split field value from inventory based on the split type.
   *
   * @param inventory The inventory
   * @param splitBy The split type
   * @return The split field value
   */
  private String getSplitFieldValue(Inventory inventory, CostSplit splitBy) {
    return switch (splitBy) {
      case COUNTRY -> {
        if (inventory.getLocation() != null) {
          yield inventory.getLocation().getCountry();
        }
        yield null;
      }
      case STATE -> {
        if (inventory.getLocation() != null) {
          yield inventory.getLocation().getState();
        }
        yield null;
      }
      case CITY -> {
        if (inventory.getLocation() != null) {
          yield inventory.getLocation().getCity();
        }
        yield null;
      }
      case MEDIA_OWNER -> {
        if (inventory.getMediaOwnerId() != null) {
          try {
            CompanyLookupResponseDTO companyDto =
                companyService.getCompanyLookupWithCompanyId(inventory.getMediaOwnerId());
            yield companyDto.getName();
          } catch (Exception e) {
            log.warn(
                "Error getting media owner name for id {}: {}",
                inventory.getMediaOwnerId(),
                e.getMessage());
            yield inventory.getMediaOwnerId();
          }
        }
        yield null;
      }
      case SIZE -> inventory.getSize();
      case INVENTORY_TYPE -> inventory.getType();
      case CHANNEL -> inventory.getClassification();
      case VENUE_TYPE -> {
        if (inventory.getVenueType() != null && !inventory.getVenueType().isEmpty()) {
          yield inventory.getVenueType().getLast();
        }
        yield null;
      }
    };
  }

  /**
   * Gets population for a split value based on the split type.
   *
   * @param splitValue The split value (country name, state name, city name)
   * @param splitBy The split type
   * @return Population or null if not found
   */
  private Long getPopulationForSplitValue(String splitValue, CostSplit splitBy) {
    try {
      return switch (splitBy) {
        case COUNTRY -> {
          Optional<Country> country = countryService.findByName(splitValue);
          yield country.map(Country::getPopulation).orElse(null);
        }
        case STATE -> {
          Optional<State> state = stateService.findByName(splitValue);
          yield state.map(State::getPopulation).orElse(null);
        }
        case CITY -> {
          Optional<District> district = districtService.findByName(splitValue);
          yield district.map(District::getPopulation).orElse(null);
        }
        default -> null;
      };
    } catch (Exception e) {
      log.warn("Error getting population for {} {}: {}", splitBy, splitValue, e.getMessage());
      return null;
    }
  }

  private CampaignViewResponseDTO.Goals prepareGoalsDetails(
      Campaign campaign,
      CampaignForecastDTO campaignForecastDTO,
      List<CampaignInventorySchedules> schedules) {
    CampaignViewResponseDTO.Goals goals = CampaignViewResponseDTO.Goals.builder().build();
    try {
      goals.setGoalType(campaign.getGoals().getTypeName());
      goals.setTargetValue(campaign.getGoals().getTargetValue());
      // Set achieved value based on goal type
      goals.setAchievedValue(
          setAchievedValueBasedOnGoalType(campaign.getGoals().getGoalType(), campaignForecastDTO));
      goals.setWeeklyBreakdown(
          prepareWeeklyGoalsBreakdown(campaign, schedules, campaignForecastDTO));
    } catch (Exception e) {
      log.error(
          "Error preparing goals details for campaign {}: {}", campaign.getId(), e.getMessage());
    }
    return goals;
  }

  private double setAchievedValueBasedOnGoalType(
      Campaign.Goals.GoalType goalType, CampaignForecastDTO campaignForecastDTO) {
    return switch (goalType) {
      case IMPRESSIONS -> campaignForecastDTO.getEstimatedImpression().doubleValue();
      case REACH -> campaignForecastDTO.getEstimatedReach().doubleValue();
      case SOV -> campaignForecastDTO.getSov();
      case ADPLAYS ->
          campaignForecastDTO.getEstimatedAdPlays() != null
              ? campaignForecastDTO.getEstimatedAdPlays().doubleValue()
              : 0.0;
      default -> 0.0;
    };
  }

  private Map<String, Double> prepareWeeklyGoalsBreakdown(
      Campaign campaign,
      List<CampaignInventorySchedules> schedules,
      CampaignForecastDTO campaignForecastDTO) {
    Map<String, Double> weeklyBreakdown = new LinkedHashMap<>();
    try {
      // Only process for IMPRESSIONS, REACH, or ADPLAYS goal types
      Campaign.Goals.GoalType goalType = campaign.getGoals().getGoalType();
      if (goalType != Campaign.Goals.GoalType.IMPRESSIONS
          && goalType != Campaign.Goals.GoalType.REACH
          && goalType != Campaign.Goals.GoalType.ADPLAYS) {
        return weeklyBreakdown;
      }

      if (schedules == null || schedules.isEmpty()) {
        return weeklyBreakdown;
      }

      // Split campaign duration into weeks based on actual start date
      Map<String, WeekRange> weekRanges =
          splitCampaignIntoWeeks(campaign.getStartDate(), campaign.getEndDate());

      if (weekRanges.isEmpty()) {
        return weeklyBreakdown;
      }

      // Get inventory map
      Map<String, Inventory> inventoryMap =
          schedules.stream()
              .map(schedule -> inventoryService.getById(schedule.getInventoryId()))
              .collect(
                  Collectors.toMap(com.mw.planner.domain.Inventory::getId, Function.identity()));

      // Calculate average weekly forecast (total forecast divided by number of weeks)
      int numberOfWeeks = weekRanges.size();
      double averageWeeklyForecast = 0.0;
      switch (goalType) {
        case IMPRESSIONS:
          averageWeeklyForecast =
              campaignForecastDTO.getEstimatedImpression().doubleValue() / numberOfWeeks;
          break;
        case REACH:
          averageWeeklyForecast =
              campaignForecastDTO.getEstimatedReach().doubleValue() / numberOfWeeks;
          break;
        case ADPLAYS:
          averageWeeklyForecast =
              campaignForecastDTO.getEstimatedAdPlays() != null
                  ? campaignForecastDTO.getEstimatedAdPlays().doubleValue() / numberOfWeeks
                  : 0.0;
          break;
        default:
          break;
      }

      // Process each week
      for (Map.Entry<String, WeekRange> weekEntry : weekRanges.entrySet()) {
        String weekLabel = weekEntry.getKey();
        WeekRange weekRange = weekEntry.getValue();

        // Create weekly schedules with filtered bookingMatrix
        List<CampaignInventorySchedules> weeklySchedules =
            createWeeklySchedules(schedules, weekRange);

        if (weeklySchedules.isEmpty()) {
          weeklyBreakdown.put(weekLabel, 0.0);
          continue;
        }

        // Calculate weekly duration (number of days in this week)
        int weeklyDuration =
            (int) ChronoUnit.DAYS.between(weekRange.startDate, weekRange.endDate) + 1;

        // Call measure service for this week
        MeasureReachFrequencyResponseDTO weeklyMeasureResponse =
            mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
                weeklyDuration,
                weeklySchedules,
                inventoryMap,
                campaign.getStartDate(),
                campaign.getEndDate());

        // Get weekly value based on goal type
        double weeklyValue = 0.0;
        switch (goalType) {
          case IMPRESSIONS:
            weeklyValue =
                weeklyMeasureResponse.getImpressions() != null
                    ? weeklyMeasureResponse.getImpressions().doubleValue()
                    : 0.0;
            break;
          case REACH:
            weeklyValue =
                weeklyMeasureResponse.getReach() != null
                    ? weeklyMeasureResponse.getReach().doubleValue()
                    : 0.0;
            break;
          case ADPLAYS:
            // For ADPLAYS, calculate based on weekly schedules' ad plays
            weeklyValue =
                weeklySchedules.stream()
                    .flatMap(
                        s -> Optional.ofNullable(s.getScheduleIds()).stream().flatMap(List::stream))
                    .map(scheduleRepository::findById)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .mapToLong(s -> s.getAdPlays() != null ? s.getAdPlays() : 0L)
                    .sum();
            break;
          default:
            break;
        }

        // Calculate achievement percentage: (weeklyValue * 100) / averageWeeklyForecast
        double achievementPercentage = 0.0;
        if (averageWeeklyForecast > 0) {
          achievementPercentage = (weeklyValue * 100.0) / averageWeeklyForecast;
        }

        // Store the achievement percentage
        weeklyBreakdown.put(weekLabel, achievementPercentage);
      }

      return weeklyBreakdown;
    } catch (Exception e) {
      log.error(
          "Error preparing weekly goals breakdown for campaign {}: {}",
          campaign.getId(),
          e.getMessage(),
          e);
    }
    return weeklyBreakdown;
  }

  /** Helper class to represent a week range */
  private static class WeekRange {
    LocalDate startDate;
    LocalDate endDate;

    WeekRange(LocalDate startDate, LocalDate endDate) {
      this.startDate = startDate;
      this.endDate = endDate;
    }
  }

  /**
   * Splits campaign duration into weeks based on actual start date. If campaign starts on
   * Wednesday, Week 1 = Wed-Sun, Week 2 = Mon-Sun, etc.
   *
   * @param startDate Campaign start date
   * @param endDate Campaign end date
   * @return Map of week labels ("Week 1", "Week 2", etc.) to WeekRange
   */
  private Map<String, WeekRange> splitCampaignIntoWeeks(LocalDate startDate, LocalDate endDate) {
    Map<String, WeekRange> weekRanges = new LinkedHashMap<>();

    if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
      return weekRanges;
    }

    LocalDate currentDate = startDate;
    int weekNumber = 1;

    while (!currentDate.isAfter(endDate)) {
      LocalDate weekEnd;

      if (weekNumber == 1) {
        // First week: from startDate to Sunday (or endDate if earlier)
        DayOfWeek startDayOfWeek = currentDate.getDayOfWeek();
        int daysUntilSunday = DayOfWeek.SUNDAY.getValue() - startDayOfWeek.getValue();
        weekEnd = currentDate.plusDays(daysUntilSunday);
        if (weekEnd.isAfter(endDate)) {
          weekEnd = endDate;
        }
      } else {
        // Subsequent weeks: Monday to Sunday (or endDate if earlier)
        // currentDate is already the day after previous Sunday (which should be Monday)
        weekEnd = currentDate.plusDays(6); // Monday + 6 = Sunday
        if (weekEnd.isAfter(endDate)) {
          weekEnd = endDate;
        }
      }

      // Create week range
      WeekRange weekRange = new WeekRange(currentDate, weekEnd);
      weekRanges.put("Week " + weekNumber, weekRange);

      // Move to next week (day after weekEnd)
      currentDate = weekEnd.plusDays(1);
      weekNumber++;
    }

    return weekRanges;
  }

  /**
   * Creates weekly schedules by filtering bookingMatrix to only include dates within the week
   * range.
   *
   * @param schedules Original schedules
   * @param weekRange Week date range
   * @return List of CampaignInventorySchedules with filtered bookingMatrix
   */
  private List<CampaignInventorySchedules> createWeeklySchedules(
      List<CampaignInventorySchedules> schedules, WeekRange weekRange) {
    List<CampaignInventorySchedules> weeklySchedules = new ArrayList<>();
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    for (CampaignInventorySchedules schedule : schedules) {
      // Fetch schedules by IDs
      List<Schedule> scheduleItems = Collections.emptyList();
      if (schedule.getScheduleIds() != null && !schedule.getScheduleIds().isEmpty()) {
        scheduleItems = scheduleRepository.findAllById(schedule.getScheduleIds());
      }

      if (scheduleItems.isEmpty()) {
        continue;
      }

      // Create a new schedule with filtered bookingMatrix
      CampaignInventorySchedules weeklySchedule = new CampaignInventorySchedules();
      weeklySchedule.setId(schedule.getId());
      weeklySchedule.setCampaignId(schedule.getCampaignId());
      weeklySchedule.setInventoryId(schedule.getInventoryId());
      weeklySchedule.setMediaOwnerId(schedule.getMediaOwnerId());

      List<Schedule> filteredSchedules = new ArrayList<>();

      for (Schedule scheduleItem : scheduleItems) {
        if (scheduleItem.getBookingMatrix() == null || scheduleItem.getBookingMatrix().isEmpty()) {
          continue;
        }

        // Filter bookingMatrix to only include dates within the week range
        Map<String, List<Integer>> filteredBookingMatrix = new HashMap<>();

        for (Map.Entry<String, List<Integer>> entry : scheduleItem.getBookingMatrix().entrySet()) {
          String dateStr = entry.getKey();
          try {
            LocalDate date = LocalDate.parse(dateStr, dateFormatter);

            // Check if date is within week range (inclusive)
            if (!date.isBefore(weekRange.startDate) && !date.isAfter(weekRange.endDate)) {
              filteredBookingMatrix.put(dateStr, entry.getValue());
            }
          } catch (Exception e) {
            log.warn("Error parsing date {} in bookingMatrix: {}", dateStr, e.getMessage());
          }
        }

        // Only add schedule if it has dates in this week
        if (!filteredBookingMatrix.isEmpty()) {
          Schedule filteredSchedule =
              Schedule.builder()
                  .name(scheduleItem.getName())
                  .startDate(weekRange.startDate)
                  .endDate(weekRange.endDate)
                  .scheduleDays(scheduleItem.getScheduleDays())
                  .bookingMatrix(filteredBookingMatrix)
                  .type(scheduleItem.getType())
                  .duration(scheduleItem.getDuration())
                  .spotsPerLoop(scheduleItem.getSpotsPerLoop())
                  .spotsPerHour(scheduleItem.getSpotsPerHour())
                  .adPlays(scheduleItem.getAdPlays())
                  .plannedSot(scheduleItem.getPlannedSot())
                  .totalSot(scheduleItem.getTotalSot())
                  .order(scheduleItem.getOrder())
                  .basePrice(scheduleItem.getBasePrice())
                  .discount(scheduleItem.getDiscount())
                  .bonusType(scheduleItem.getBonusType())
                  .build();

          filteredSchedules.add(filteredSchedule);
        }
      }

      // Only add weekly schedule if it has filtered schedules
      if (!filteredSchedules.isEmpty()) {
        // Save filtered schedules and store their IDs
        List<Schedule> savedSchedules = scheduleRepository.saveAll(filteredSchedules);
        List<String> scheduleIds =
            savedSchedules.stream().map(Schedule::getId).collect(Collectors.toList());
        weeklySchedule.setScheduleIds(scheduleIds);
        weeklySchedules.add(weeklySchedule);
      }
    }

    return weeklySchedules;
  }

  /**
   * Create a new campaign comment with file uploads.
   *
   * @param campaignId The campaign ID
   * @param comment The comment text
   * @param files List of files to upload
   * @param taggedCompanyIds List of tagged company IDs
   * @param companyId The company ID from user context to add to taggedCompanyIds
   */
  public void createCampaignComment(
      String campaignId,
      String comment,
      List<MultipartFile> files,
      List<String> taggedCompanyIds,
      String companyId) {
    log.debug(
        "Creating campaign comment for campaignId: {} with companyId: {}", campaignId, companyId);

    // Guarded load: validates existence, data mode, and acting-company participation.
    findByIdForCurrentMode(campaignId);

    // Upload files to S3 one by one (files are optional)
    List<String> fileUrls = new ArrayList<>();
    if (files != null && !files.isEmpty()) {
      log.debug("Uploading {} files to S3", files.size());
      for (MultipartFile file : files) {
        if (file != null && !file.isEmpty()) {
          try {
            String fileUrl = cloudStorageService.uploadFile(file, folder);
            fileUrls.add(fileUrl);
            log.debug("Successfully uploaded file: {}", file.getOriginalFilename());
          } catch (Exception e) {
            log.error("Failed to upload file: {}", file.getOriginalFilename(), e);
            throw new com.mw.planner.exception.storage.StorageUploadFailedException(
                "Failed to upload file: " + file.getOriginalFilename(), e);
          }
        }
      }
      log.info("Successfully uploaded {} files to S3", fileUrls.size());
    }

    // Build taggedCompanyIds list (do not add user's companyId to this list)
    List<String> finalTaggedCompanyIds = new ArrayList<>();
    if (taggedCompanyIds != null && !taggedCompanyIds.isEmpty()) {
      finalTaggedCompanyIds.addAll(taggedCompanyIds);
    }

    // Create and save CampaignComments entity
    // Store companyId separately, not in taggedCompanyIds
    CampaignComments campaignComment =
        CampaignComments.builder()
            .comment(comment)
            .fileUrls(fileUrls)
            .taggedCompanyIds(finalTaggedCompanyIds)
            .campaignId(campaignId)
            .companyId(companyId)
            .build();

    CampaignComments savedComment = campaignCommentsRepository.save(campaignComment);
    log.debug("Campaign comment created successfully with ID: {}", savedComment.getId());

    // Log activity for campaign comment creation
    try {
      int fileCount = files != null ? files.size() : 0;
      campaignActivityService.logActivity(
          campaignId,
          CampaignActivityService.OperationType.ADDED,
          COMMENT_FILE_COUNT.key(),
          fileCount);
    } catch (Exception e) {
      log.warn("Failed to log campaign comment activity: {}", e.getMessage());
    }
  }

  /**
   * Get campaign comments by campaign ID with business type information.
   *
   * @param campaignId The campaign ID
   * @return List of campaign comment response DTOs
   */
  public List<CampaignCommentsResponseDTO> getCommentsByCampaignId(String campaignId) {
    log.debug("Getting comments for campaignId: {}", campaignId);

    // Guarded load: validates existence, data mode, and acting-company participation.
    findByIdForCurrentMode(campaignId);

    // Get all comments for the campaign
    List<CampaignComments> comments = campaignCommentsRepository.findByCampaignId(campaignId);

    // Map to response DTOs
    List<CampaignCommentsResponseDTO> responseDTOs = new ArrayList<>();
    for (CampaignComments comment : comments) {
      CampaignCommentsResponseDTO responseDTO =
          CampaignCommentsResponseDTO.builder()
              .comment(comment.getComment())
              .fileUrls(comment.getFileUrls())
              .createdBy(comment.getCreatedBy())
              .createdAt(comment.getCreatedAt())
              .build();

      // Determine businessType from companyId stored in the comment
      CompanyDto.BusinessType businessType = null;
      if (comment.getCompanyId() != null) {
        try {
          CompanyLookupResponseDTO companyDto =
              companyService.getCompanyLookupWithCompanyId(comment.getCompanyId());
          if (companyDto != null && companyDto.getCompanyType() != null) {
            businessType = CompanyDto.BusinessType.valueOf(companyDto.getCompanyType());
          }
        } catch (CompanyNotFoundException e) {
          log.warn(
              "Company not found for companyId: {} in comment: {}",
              comment.getCompanyId(),
              comment.getId());
        } catch (Exception e) {
          log.error(
              "Error fetching company for companyId: {} in comment: {}",
              comment.getCompanyId(),
              comment.getId(),
              e);
        }
      }
      responseDTO.setBusinessType(businessType);

      responseDTOs.add(responseDTO);
    }

    log.debug("Found {} comments for campaignId: {}", responseDTOs.size(), campaignId);
    return responseDTOs;
  }

  public static int calculateDuration(Campaign campaign) {
    return Math.toIntExact(ChronoUnit.DAYS.between(campaign.getStartDate(), campaign.getEndDate()))
        + 1;
  }

  /**
   * Create a deep copy of a campaign for comparison purposes
   *
   * @param campaign Original campaign
   * @return Copied campaign
   */
  private Campaign copyCampaign(Campaign campaign) {
    return Campaign.builder()
        .name(campaign.getName())
        .description(campaign.getDescription())
        .status(campaign.getStatus())
        .budget(campaign.getBudget())
        .currency(campaign.getCurrency())
        .startDate(campaign.getStartDate())
        .endDate(campaign.getEndDate())
        .userId(campaign.getUserId())
        .brand(campaign.getBrand())
        .clientType(campaign.getClientType())
        .agency(campaign.getAgency())
        .companyId(campaign.getCompanyId())
        .countryId(campaign.getCountryId())
        .goals(campaign.getGoals())
        .targeting(campaign.getTargeting())
        .budgetAllocation(campaign.getBudgetAllocation())
        .optimization(campaign.getOptimization())
        .companyAccess(campaign.getCompanyAccess())
        .skipRecommendation(campaign.getSkipRecommendation())
        .build();
  }

  private boolean isCampaignDatesChanged(
      Campaign existingCampaign, CampaignAutosaveRequestDTO autosaveRequestDTO) {

    return (autosaveRequestDTO.getStartDate() != null
            && !Objects.equals(existingCampaign.getStartDate(), autosaveRequestDTO.getStartDate()))
        || (autosaveRequestDTO.getEndDate() != null
            && !Objects.equals(existingCampaign.getEndDate(), autosaveRequestDTO.getEndDate()));
  }

  /**
   * Recreate schedules when campaign dates change. This method: 1. Collects existing inventory IDs
   * 2. Clears all schedules (CampaignInventorySchedules and Schedule entities) 3. Recreates default
   * schedules for the existing inventories
   *
   * @param campaignId Campaign ID
   */
  private void recreateSchedulesIfDatesChanged(String campaignId) {
    // Get existing schedules to extract inventory IDs before deletion
    List<CampaignInventorySchedules> existingSchedules =
        campaignInventorySchedulesService.findByCampaignId(campaignId);

    if (existingSchedules.isEmpty()) {
      log.info(
          "No existing inventories found for campaignId: {}, skipping schedule recreation",
          campaignId);
      return;
    }

    // Extract unique inventory IDs before deletion
    List<String> inventoryIds =
        existingSchedules.stream()
            .map(CampaignInventorySchedules::getInventoryId)
            .distinct()
            .toList();

    log.info(
        "Dates changed for campaignId: {}, found {} inventories to recreate schedules",
        campaignId,
        inventoryIds.size());

    // Clear all schedules (removes both CampaignInventorySchedules and Schedule entities)
    campaignInventorySchedulesService.removeAllInventoriesForCampaign(campaignId);

    // Recreate default schedules for existing inventories
    campaignInventorySchedulesService.bulkSelectInventoriesByIds(campaignId, inventoryIds);

    log.info(
        "Successfully recreated default schedules for {} inventories in campaignId: {}",
        inventoryIds.size(),
        campaignId);
  }
}
