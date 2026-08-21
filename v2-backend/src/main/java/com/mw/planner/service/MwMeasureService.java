package com.mw.planner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.config.MwPlannerProperties;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.Inventory;
import com.mw.planner.domain.Schedule;
import com.mw.planner.dto.MeasureInventoryDTO;
import com.mw.planner.dto.MeasureReachFrequencyRequestDTO;
import com.mw.planner.dto.MeasureReachFrequencyResponseDTO;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.inventory.InventoryMeasureApiException;
import com.mw.planner.repository.ScheduleRepository;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class MwMeasureService {

  private final MwPlannerProperties mwPlannerProperties;
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final ScheduleRepository scheduleRepository;

  /**
   * Calls the MW Measure API to get reach and frequency data
   *
   * @param request The request containing inventory data and duration
   * @return Response containing reach, frequency, and impressions data
   */
  public MeasureReachFrequencyResponseDTO getReachAndFrequency(
      MeasureReachFrequencyRequestDTO request) {

    log.info(
        "Calling MW Measure API for reach and frequency calculation URL: {}",
        mwPlannerProperties.getMeasure().getFullReachAndFrequencyUrl());

    // Get the bearer token from SecurityContext
    String bearerToken = getBearerTokenFromSecurityContext();
    if (bearerToken == null) {
      throw new IllegalStateException("No authentication token found in security context");
    }

    // Prepare headers with bearer token
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(bearerToken);

    try {
      HttpEntity<String> entity =
          new HttpEntity<>(objectMapper.writeValueAsString(request), headers);
      // Make the API call
      ResponseEntity<MeasureReachFrequencyResponseDTO> response =
          restTemplate.exchange(
              mwPlannerProperties.getMeasure().getFullReachAndFrequencyUrl(),
              HttpMethod.POST,
              entity,
              MeasureReachFrequencyResponseDTO.class);

      log.info("Successfully received response from MW Measure API");
      return response.getBody();

    } catch (HttpClientErrorException e) {
      log.error("Client error calling MW Measure API: {}", e.getStatusCode(), e);
      ErrorCode errorCode = mapHttpStatusToErrorCode(e.getStatusCode());
      throw new InventoryMeasureApiException(
          errorCode,
          "Failed to get reach and frequency data from MW Measure API: " + e.getMessage(),
          e);
    } catch (HttpServerErrorException e) {
      log.error("Server error calling MW Measure API: {}", e.getStatusCode(), e);
      throw new InventoryMeasureApiException(
          ErrorCode.INVENTORY_MEASURE_API_EXCEPTION,
          "MW Influence API server error: " + e.getMessage(),
          e);
    } catch (ResourceAccessException e) {
      log.error("Timeout or connection error calling MW Influence API", e);
      throw new InventoryMeasureApiException(
          ErrorCode.INVENTORY_MEASURE_API_TIMEOUT,
          "Timeout or connection error calling MW Influence API: " + e.getMessage(),
          e);
    } catch (Exception e) {
      log.error("Unexpected error calling MW Influence API", e);
      throw new InventoryMeasureApiException(
          ErrorCode.INVENTORY_MEASURE_API_EXCEPTION,
          "Unexpected error calling MW Influence API: " + e.getMessage(),
          e);
    }
  }

  /**
   * Extracts the bearer token from the SecurityContext
   *
   * @return The bearer token or null if not found
   */
  private String getBearerTokenFromSecurityContext() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getCredentials() != null) {
      return authentication.getCredentials().toString();
    }
    return null;
  }

  /**
   * Calls the MW Measure API to get reach and frequency data based on Campaign and
   * CampaignInventorySchedules.
   *
   * @param campaignDuration total campaign Days
   * @param schedules List of CampaignInventorySchedules
   * @return Response containing reach, frequency, and impressions data
   */
  public MeasureReachFrequencyResponseDTO getReachAndFrequencyByCampaignInventorySchedules(
      Integer campaignDuration,
      List<CampaignInventorySchedules> schedules,
      Map<String, Inventory> inventoryMap) {
    return getReachAndFrequencyByCampaignInventorySchedules(
        campaignDuration, schedules, inventoryMap, null, null);
  }

  public MeasureReachFrequencyResponseDTO getReachAndFrequencyByCampaignInventorySchedules(
      Integer campaignDuration,
      List<CampaignInventorySchedules> schedules,
      Map<String, Inventory> inventoryMap,
      java.time.LocalDate campaignStartDate,
      java.time.LocalDate campaignEndDate) {

    // Convert CampaignInventorySchedules to MeasureInventoryDTO
    List<MeasureInventoryDTO> measureInventories =
        convertCampaignInventorySchedulesToMeasureInventories(
            schedules, inventoryMap, campaignStartDate, campaignEndDate);

    // Create request DTO
    MeasureReachFrequencyRequestDTO request =
        MeasureReachFrequencyRequestDTO.builder()
            .inventories(measureInventories)
            .duration(campaignDuration)
            .build();

    // Call the existing getReachAndFrequency method
    return getReachAndFrequency(request);
  }

  /**
   * Converts List<CampaignInventorySchedules> to List<MeasureInventoryDTO>. Each
   * CampaignInventorySchedules may produce multiple DTOs — one per schedule type group (LOOP and/or
   * DAYPART).
   *
   * @param schedules List of CampaignInventorySchedules
   * @param inventoryMap Map of inventoryId to Inventory
   * @return Flat list of MeasureInventoryDTO
   */
  private List<MeasureInventoryDTO> convertCampaignInventorySchedulesToMeasureInventories(
      List<CampaignInventorySchedules> schedules,
      Map<String, Inventory> inventoryMap,
      java.time.LocalDate campaignStartDate,
      java.time.LocalDate campaignEndDate) {
    return schedules.stream()
        .flatMap(
            schedule ->
                convertToMeasureInventoryDTOs(
                    schedule,
                    inventoryMap.get(schedule.getInventoryId()),
                    campaignStartDate,
                    campaignEndDate)
                    .stream())
        .collect(Collectors.toList());
  }

  /**
   * Converts a single CampaignInventorySchedules to a list of MeasureInventoryDTOs, grouped by
   * schedule type. LOOP schedules produce a DTO without dayparts; DAYPART schedules produce a DTO
   * with dayparts. Null schedule type is treated as LOOP. If no schedules exist, returns a single
   * DTO without dayparts.
   *
   * @param campaignInventorySchedule The CampaignInventorySchedules to convert
   * @param inventory The inventory for the schedule
   * @return List of MeasureInventoryDTO (one per schedule type group)
   */
  private List<MeasureInventoryDTO> convertToMeasureInventoryDTOs(
      CampaignInventorySchedules campaignInventorySchedule,
      Inventory inventory,
      java.time.LocalDate campaignStartDate,
      java.time.LocalDate campaignEndDate) {

    List<Schedule> schedules = Collections.emptyList();
    if (campaignInventorySchedule.getScheduleIds() != null
        && !campaignInventorySchedule.getScheduleIds().isEmpty()) {
      schedules = scheduleRepository.findAllById(campaignInventorySchedule.getScheduleIds());
    }

    return buildInventoryDTOs(schedules, inventory, campaignStartDate, campaignEndDate);
  }

  /**
   * Core DTO builder — shared by both the forecast path (schedules fetched from DB) and the
   * enrichment path (schedules passed in-memory, not yet saved).
   */
  public List<MeasureInventoryDTO> buildInventoryDTOs(
      List<Schedule> schedules,
      Inventory inventory,
      java.time.LocalDate campaignStartDate,
      java.time.LocalDate campaignEndDate) {

    if (schedules.isEmpty()) {
      return List.of(
          MeasureInventoryDTO.builder()
              .referenceId(inventory.getReferenceId())
              .type("billboard")
              .build());
    }

    Map<Schedule.Type, List<Schedule>> byType =
        schedules.stream()
            .collect(
                Collectors.groupingBy(s -> s.getType() != null ? s.getType() : Schedule.Type.LOOP));

    List<MeasureInventoryDTO> result = new ArrayList<>();

    // LOOP group — dayparts with scheduledDate only when schedule dates differ from campaign dates
    if (byType.containsKey(Schedule.Type.LOOP)) {
      List<Schedule> loopSchedules = byType.get(Schedule.Type.LOOP);
      Long spotsPerHourLong = calculateTotalSpotsPerHour(loopSchedules);
      Integer spotsPerHour = spotsPerHourLong != null ? spotsPerHourLong.intValue() : 0;

      List<MeasureInventoryDTO.Dayparts> loopDayparts =
          schedulesMatchCampaign(loopSchedules, campaignStartDate, campaignEndDate)
              ? null
              : buildDateOnlyDayparts(loopSchedules);

      result.add(
          MeasureInventoryDTO.builder()
              .referenceId(inventory.getReferenceId())
              .type("billboard")
              .spotsPerHour(spotsPerHour)
              .dayparts(loopDayparts)
              .build());
    }

    // DAYPART group — with dayparts
    if (byType.containsKey(Schedule.Type.DAYPART)) {
      List<Schedule> daypartSchedules = byType.get(Schedule.Type.DAYPART);
      Long spotsPerHourLong = calculateTotalSpotsPerHour(daypartSchedules);
      Integer spotsPerHour = spotsPerHourLong != null ? spotsPerHourLong.intValue() : 0;

      Map<String, List<Integer>> mergedBookingMatrix =
          mergeSchedulesBookingMatrix(daypartSchedules);

      // When every booked date covers all 24 hours, the hourly dayparts add no information beyond
      // the date range, so behave exactly like a LOOP schedule: omit dayparts when the schedule
      // spans the whole campaign, otherwise send date-only dayparts (dates, no hours).
      List<MeasureInventoryDTO.Dayparts> dayparts;
      if (allDatesFullDay(mergedBookingMatrix)) {
        log.info("All dates full date.");
        // Commented out the following logic to always build date-only dayparts for DAYPART
        // schedules,
        // even if they match the campaign dates.
        // This ensures that the Measure API receives explicit date information for DAYPART
        // schedules.
        // dayparts =
        //     schedulesMatchCampaign(daypartSchedules, campaignStartDate, campaignEndDate)
        //         ? null
        //         : buildDateOnlyDayparts(daypartSchedules);
        dayparts = buildDateOnlyDayparts(daypartSchedules);
      } else {
        log.info("Not all dates full date.");
        dayparts = convertBookingMatrixToDayparts(mergedBookingMatrix);
      }
      log.info("Dayparts for inventory {}: {}", inventory.getReferenceId(), dayparts);

      result.add(
          MeasureInventoryDTO.builder()
              .referenceId(inventory.getReferenceId())
              .type("billboard")
              .spotsPerHour(spotsPerHour)
              .dayparts(dayparts)
              .build());
    }

    return result;
  }

  /**
   * Builds payload using correct LOOP/DAYPART logic from in-memory schedules, then calls Measure
   * API with aggregate=true for per-site responses. Used by schedule enrichment (save/update).
   */
  public List<MeasureReachFrequencyResponseDTO> getReachAndFrequencyBySitesFromSchedules(
      int duration,
      Map<String, List<Schedule>> referenceIdToSchedules,
      Map<String, Inventory> referenceIdToInventory,
      java.time.LocalDate campaignStartDate,
      java.time.LocalDate campaignEndDate,
      java.time.LocalDate requestStartDate,
      java.time.LocalDate requestEndDate) {

    List<MeasureInventoryDTO> inventoryDTOs =
        referenceIdToSchedules.entrySet().stream()
            .filter(e -> referenceIdToInventory.containsKey(e.getKey()))
            .flatMap(
                e ->
                    buildInventoryDTOs(
                        e.getValue(),
                        referenceIdToInventory.get(e.getKey()),
                        campaignStartDate,
                        campaignEndDate)
                        .stream())
            .collect(Collectors.toList());

    MeasureReachFrequencyRequestDTO request =
        MeasureReachFrequencyRequestDTO.builder()
            .inventories(inventoryDTOs)
            .duration(duration)
            .startDate(
                requestStartDate != null
                    ? requestStartDate.format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    : null)
            .endDate(
                requestEndDate != null
                    ? requestEndDate.format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    : null)
            .build();

    return getReachAndFrequencyBySites(request, true);
  }

  /**
   * Merges bookingMatrix from all schedules. If multiple schedules have the same date key, merge
   * the hours (List<Integer>) without duplicates.
   *
   * <p>Example: If schedule1 has date "2025-12-25" with hours [8, 9, 10] and schedule2 has the same
   * date "2025-12-25" with hours [9, 10, 11], the merged result will be [8, 9, 10, 11] (duplicates
   * removed).
   *
   * @param schedules List of Schedule objects
   * @return Merged bookingMatrix with unique hours per date key
   */
  private Map<String, List<Integer>> mergeSchedulesBookingMatrix(List<Schedule> schedules) {
    if (schedules == null || schedules.isEmpty()) {
      return Map.of();
    }

    // Use LinkedHashSet to automatically remove duplicates while maintaining insertion order
    Map<String, LinkedHashSet<Integer>> mergedMap = new HashMap<>();

    for (Schedule schedule : schedules) {
      if (schedule.getBookingMatrix() != null) {
        schedule
            .getBookingMatrix()
            .forEach(
                (date, hours) -> {
                  if (hours != null && !hours.isEmpty()) {
                    mergedMap.computeIfAbsent(date, k -> new LinkedHashSet<>()).addAll(hours);
                  }
                });
      }
    }

    // Convert LinkedHashSet back to List to maintain order and ensure no duplicates
    return mergedMap.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> new ArrayList<>(entry.getValue())));
  }

  /**
   * Converts bookingMatrix to dayparts format for DAYPART schedules. Converts hours from Integer to
   * String format ("00", "01", etc.).
   *
   * @param bookingMatrix The merged bookingMatrix
   * @return List of Dayparts with scheduledDate and scheduledTime populated
   */
  private List<MeasureInventoryDTO.Dayparts> convertBookingMatrixToDayparts(
      Map<String, List<Integer>> bookingMatrix) {
    List<MeasureInventoryDTO.Dayparts> dayparts = new ArrayList<>();

    if (bookingMatrix == null || bookingMatrix.isEmpty()) {
      return dayparts;
    }

    bookingMatrix.forEach(
        (date, hours) -> {
          if (hours != null && !hours.isEmpty()) {
            List<String> scheduledTimes =
                hours.stream()
                    .sorted()
                    .map(hour -> String.format("%02d", hour))
                    .collect(Collectors.toList());

            dayparts.add(
                MeasureInventoryDTO.Dayparts.builder()
                    .scheduledDate(date)
                    .scheduledTime(scheduledTimes)
                    .build());
          }
        });

    return dayparts;
  }

  /**
   * True when the schedules span exactly the campaign date range (so the Measure API can rely on
   * the request duration and no per-date dayparts are needed). A null campaign range is treated as
   * a match.
   */
  private boolean schedulesMatchCampaign(
      List<Schedule> schedules,
      java.time.LocalDate campaignStartDate,
      java.time.LocalDate campaignEndDate) {
    return campaignStartDate == null
        || campaignEndDate == null
        || schedules.stream()
            .allMatch(
                s ->
                    campaignStartDate.equals(s.getStartDate())
                        && campaignEndDate.equals(s.getEndDate()));
  }

  /**
   * Build dayparts carrying only the scheduled dates (no hours), de-duplicated and sorted. Returns
   * null when no dates are present.
   */
  private List<MeasureInventoryDTO.Dayparts> buildDateOnlyDayparts(List<Schedule> schedules) {
    List<MeasureInventoryDTO.Dayparts> dayparts =
        schedules.stream()
            .filter(s -> s.getBookingMatrix() != null)
            .flatMap(s -> s.getBookingMatrix().keySet().stream())
            .distinct()
            .sorted()
            .map(date -> MeasureInventoryDTO.Dayparts.builder().scheduledDate(date).build())
            .collect(Collectors.toList());
    return dayparts.isEmpty() ? null : dayparts;
  }

  /**
   * True when every booked date in the matrix covers all 24 hours (0..23). An empty matrix returns
   * false (nothing booked is not full-day coverage).
   */
  private boolean allDatesFullDay(Map<String, List<Integer>> bookingMatrix) {
    if (bookingMatrix == null || bookingMatrix.isEmpty()) {
      return false;
    }
    for (List<Integer> hours : bookingMatrix.values()) {
      if (hours == null) {
        return false;
      }
      Set<Integer> hourSet = new HashSet<>(hours);
      for (int hour = 0; hour < 24; hour++) {
        if (!hourSet.contains(hour)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Calculates average spotsPerHour from all schedules.
   *
   * @param schedules List of Schedule objects
   * @return Average spotsPerHour as Integer
   */
  private Long calculateTotalSpotsPerHour(List<Schedule> schedules) {
    if (schedules == null || schedules.isEmpty()) {
      return null;
    }

    return schedules.stream()
        .map(Schedule::getSpotsPerHour)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  /**
   * Calls the MW Measure API to get reach and frequency data with individual site level
   * aggregation.
   *
   * @param request The request containing inventory data, duration, and optional date range
   * @param aggregate If true, returns individual site level data; if false, returns aggregated
   *     campaign level data
   * @return List of site-level responses when aggregate=true, or single aggregated response when
   *     aggregate=false
   */
  public List<MeasureReachFrequencyResponseDTO> getReachAndFrequencyBySites(
      MeasureReachFrequencyRequestDTO request, boolean aggregate) {

    log.info(
        "Calling MW Measure API with aggregate={} for {} inventories",
        aggregate,
        request.getInventories() != null ? request.getInventories().size() : 0);

    // Get the bearer token from SecurityContext
    String bearerToken = getBearerTokenFromSecurityContext();
    if (bearerToken == null) {
      throw new IllegalStateException("No authentication token found in security context");
    }

    // Prepare headers with bearer token
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(bearerToken);

    try {
      String url = mwPlannerProperties.getMeasure().getFullReachAndFrequencyUrl(aggregate);
      HttpEntity<String> entity =
          new HttpEntity<>(objectMapper.writeValueAsString(request), headers);

      // Make the API call
      ResponseEntity<List<MeasureReachFrequencyResponseDTO>> response =
          restTemplate.exchange(
              url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

      return response.getBody() != null ? response.getBody() : new ArrayList<>();

    } catch (HttpClientErrorException e) {
      log.error("Client error calling MW Measure API: {}", e.getStatusCode(), e);
      ErrorCode errorCode = mapHttpStatusToErrorCode(e.getStatusCode());
      throw new InventoryMeasureApiException(
          errorCode,
          "Failed to get reach and frequency data from MW Measure API: " + e.getMessage(),
          e);
    } catch (HttpServerErrorException e) {
      log.error("Server error calling MW Measure API: {}", e.getStatusCode(), e);
      throw new InventoryMeasureApiException(
          ErrorCode.INVENTORY_MEASURE_API_EXCEPTION,
          "MW Measure API server error: " + e.getMessage(),
          e);
    } catch (ResourceAccessException e) {
      log.error("Timeout or connection error calling MW Measure API", e);
      throw new InventoryMeasureApiException(
          ErrorCode.INVENTORY_MEASURE_API_TIMEOUT,
          "Timeout or connection error calling MW Measure API: " + e.getMessage(),
          e);
    } catch (Exception e) {
      log.error("Unexpected error calling MW Measure API", e);
      throw new InventoryMeasureApiException(
          ErrorCode.INVENTORY_MEASURE_API_EXCEPTION,
          "Unexpected error calling MW Measure API: " + e.getMessage(),
          e);
    }
  }

  /**
   * Maps HTTP status codes to appropriate error codes
   *
   * @param statusCode The HTTP status code
   * @return The corresponding ErrorCode
   */
  private ErrorCode mapHttpStatusToErrorCode(org.springframework.http.HttpStatusCode statusCode) {
    if (statusCode.value() == 401) {
      return ErrorCode.INVENTORY_MEASURE_API_UNAUTHORIZED;
    } else if (statusCode.value() == 403) {
      return ErrorCode.INVENTORY_MEASURE_API_FORBIDDEN;
    } else if (statusCode.value() == 404) {
      return ErrorCode.INVENTORY_MEASURE_API_NOT_FOUND;
    } else if (statusCode.value() == 400) {
      return ErrorCode.INVENTORY_MEASURE_API_BAD_REQUEST;
    } else {
      return ErrorCode.INVENTORY_MEASURE_API_EXCEPTION;
    }
  }
}
