package com.mw.planner.service;

import com.mw.planner.domain.District;
import com.mw.planner.domain.State;
import com.mw.planner.dto.DistrictResponseDTO;
import com.mw.planner.dto.MwDistrictDTO;
import com.mw.planner.repository.DistrictRepository;
import com.mw.planner.repository.StateRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistrictService {

  private final MwMasterDataService mwMasterDataService;
  private final DistrictRepository districtRepository;
  private final StateRepository stateRepository;
  private final VirtualThreadService virtualThreadService;
  private final CountryService countryService;

  /** Start async district sync for all states using Virtual Threads. */
  @Async
  @CacheEvict(value = "districts", allEntries = true)
  public CompletableFuture<Void> syncAllDistrictsAsync() {
    log.info("Starting async district sync with Virtual Threads");

    try {
      // Get all states
      List<State> states = stateRepository.findAll();
      log.info("Found {} states to process", states.size());

      AtomicInteger totalFetched = new AtomicInteger(0);
      AtomicInteger totalSaved = new AtomicInteger(0);
      AtomicInteger errorCount = new AtomicInteger(0);

      // Semaphore to control concurrent API calls (prevents overwhelming external API)
      // Allows 50 concurrent operations - adjust based on your API rate limits
      int maxConcurrentApiCalls = 50;
      Semaphore apiRateLimiter = new Semaphore(maxConcurrentApiCalls);

      log.info(
          "Processing {} states with max {} concurrent API calls using virtual threads",
          states.size(),
          maxConcurrentApiCalls);

      // Submit all state processing tasks to virtual thread executor via VirtualThreadService
      List<CompletableFuture<Void>> futures = new ArrayList<>();

      for (State state : states) {
        CompletableFuture<Void> future =
            virtualThreadService.runDistrictSyncAsync(
                () -> {
                  try {
                    // Acquire permit before making API call
                    apiRateLimiter.acquire();
                    try {
                      processStateDistricts(state.getId(), totalFetched, totalSaved, errorCount);
                    } finally {
                      // Release permit after API call completes
                      apiRateLimiter.release();
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Thread interrupted while processing state {}", state.getId());
                    errorCount.incrementAndGet();
                  } catch (Exception e) {
                    log.error("Error processing state {}: {}", state.getId(), e.getMessage(), e);
                    errorCount.incrementAndGet();
                  }
                });

        futures.add(future);
      }

      // Wait for all tasks to complete
      CompletableFuture<Void> allFutures =
          CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

      // Block until all processing is complete
      allFutures.join();

      log.info(
          "District sync completed successfully. Fetched: {}, Saved: {}, Errors: {}",
          totalFetched.get(),
          totalSaved.get(),
          errorCount.get());

    } catch (Exception e) {
      log.error("Failed to sync districts", e);
    }

    return CompletableFuture.completedFuture(null);
  }

  /** Process districts for a single state with optimized batch saving */
  private void processStateDistricts(
      String stateId,
      AtomicInteger totalFetched,
      AtomicInteger totalSaved,
      AtomicInteger errorCount) {
    int maxRetries = 3;
    int retryCount = 0;

    while (retryCount < maxRetries) {
      try {
        log.debug("Processing districts for state: {} (attempt {})", stateId, retryCount + 1);

        // Fetch districts from external API
        List<MwDistrictDTO> externalDistricts =
            mwMasterDataService.fetchDistrictsFromMasterDataApi(stateId);

        if (externalDistricts == null || externalDistricts.isEmpty()) {
          log.debug("No districts found for state: {}", stateId);
          return;
        }

        totalFetched.addAndGet(externalDistricts.size());
        log.debug("Fetched {} districts for state: {}", externalDistricts.size(), stateId);

        // Get existing districts for this state
        List<District> existingDistricts = districtRepository.findByStateId(stateId);
        Map<String, District> existingDistrictsMap =
            existingDistricts.stream()
                .collect(Collectors.toMap(District::getId, district -> district));

        // Process districts in batches to avoid memory issues with large datasets
        int batchSize = 500; // Process 500 districts at a time
        List<List<MwDistrictDTO>> districtBatches = partitionList(externalDistricts, batchSize);

        log.debug(
            "Processing {} districts in {} batches for state: {}",
            externalDistricts.size(),
            districtBatches.size(),
            stateId);

        int savedCount = 0;

        for (List<MwDistrictDTO> batch : districtBatches) {
          List<District> districtsToSave = new ArrayList<>();

          // Process each district in the batch
          for (MwDistrictDTO externalDistrict : batch) {
            try {
              String districtId = externalDistrict.getId();

              District existingDistrict = existingDistrictsMap.get(districtId);
              District processedDistrict;

              if (existingDistrict != null) {
                // Update existing district
                processedDistrict = updateExistingDistrict(existingDistrict, externalDistrict);
              } else {
                // Create new district
                processedDistrict = createNewDistrict(externalDistrict, stateId);
              }

              districtsToSave.add(processedDistrict);
            } catch (Exception e) {
              log.error(
                  "Failed to process district {} for state {}: {}",
                  externalDistrict.getName(),
                  stateId,
                  e.getMessage());
              errorCount.incrementAndGet();
            }
          }

          // Bulk save districts for this batch
          if (!districtsToSave.isEmpty()) {
            districtRepository.saveAll(districtsToSave);
            savedCount += districtsToSave.size();
            log.debug(
                "Saved batch of {} districts for state: {} (total: {})",
                districtsToSave.size(),
                stateId,
                savedCount);
          }
        }

        totalSaved.addAndGet(savedCount);
        log.debug("Completed saving {} districts for state: {}", savedCount, stateId);

        // Success - break out of retry loop
        return;

      } catch (Exception e) {
        retryCount++;
        log.warn(
            "Error processing districts for state {} (attempt {}): {}",
            stateId,
            retryCount,
            e.getMessage());

        if (retryCount >= maxRetries) {
          log.error(
              "Failed to process districts for state {} after {} attempts: {}",
              stateId,
              maxRetries,
              e.getMessage(),
              e);
          errorCount.incrementAndGet();
        } else {
          // Wait before retry (exponential backoff)
          try {
            Thread.sleep(1000L * retryCount); // 1s, 2s, 3s delays
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Retry interrupted for state: {}", stateId);
            break;
          }
        }
      }
    }
  }

  /** Update existing district with external data */
  private District updateExistingDistrict(
      District existingDistrict, MwDistrictDTO externalDistrict) {
    existingDistrict.setName(externalDistrict.getName());
    existingDistrict.setType(externalDistrict.getType());
    existingDistrict.setLatitude(externalDistrict.getLatitude());
    existingDistrict.setLongitude(externalDistrict.getLongitude());
    existingDistrict.setZoom(externalDistrict.getZoom());
    existingDistrict.setPopulation(externalDistrict.getPopulation());
    existingDistrict.setIso(externalDistrict.getIso());
    existingDistrict.setLocale(externalDistrict.getLocale());
    return existingDistrict;
  }

  /** Create new district from external data */
  private District createNewDistrict(MwDistrictDTO externalDistrict, String stateId) {
    District newDistrict = new District();
    newDistrict.setId(externalDistrict.getId());
    newDistrict.setName(externalDistrict.getName());
    newDistrict.setStateId(stateId);
    newDistrict.setType(externalDistrict.getType());
    newDistrict.setLatitude(externalDistrict.getLatitude());
    newDistrict.setLongitude(externalDistrict.getLongitude());
    newDistrict.setZoom(externalDistrict.getZoom());
    newDistrict.setPopulation(externalDistrict.getPopulation());
    newDistrict.setIso(externalDistrict.getIso());
    newDistrict.setLocale(externalDistrict.getLocale());
    return newDistrict;
  }

  /** Partition a list into smaller batches */
  private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
    List<List<T>> batches = new ArrayList<>();
    for (int i = 0; i < list.size(); i += batchSize) {
      int end = Math.min(i + batchSize, list.size());
      batches.add(list.subList(i, end));
    }
    return batches;
  }

  /** Get district by name */
  @Cacheable(value = "districts", key = "#name")
  public Optional<District> findByName(String name) {
    log.debug("Fetching district by name: {}", name);
    return districtRepository.findByName(name);
  }

  /**
   * Returns paginated districts optionally filtered by: - name (case-insensitive contains) -
   * country scope (districts under the given country name)
   */
  public Page<DistrictResponseDTO> getAllDistricts(
      String name, Pageable pageable, String countryName) {

    var normalizedName = normalize(name);
    var stateIds = resolveStateIdsByCountryName(countryName);

    var districts = searchDistricts(normalizedName, stateIds, pageable);

    return districts.map(this::toResponse);
  }

  /**
   * Resolves state IDs for the given country name. Returns null when countryName is null/blank (no
   * filtering). Returns empty list when country not found (no results).
   */
  private List<String> resolveStateIdsByCountryName(String countryName) {

    if (countryName == null || countryName.isBlank()) {
      return null; // no country filter
    }

    var country = countryService.findByName(countryName.trim()).orElse(null);
    if (country == null) {
      return List.of(); // country not found → empty result
    }

    return stateRepository.findByCountryId(country.getId()).stream().map(State::getId).toList();
  }

  private static String normalize(String value) {
    return (value == null || value.isBlank()) ? null : value.trim();
  }

  private DistrictResponseDTO toResponse(District d) {
    return DistrictResponseDTO.builder()
        .id(d.getId())
        .name(d.getName())
        .stateId(d.getStateId())
        .type(d.getType())
        .latitude(d.getLatitude())
        .longitude(d.getLongitude())
        .zoom(d.getZoom())
        .population(d.getPopulation())
        .iso(d.getIso())
        .locale(d.getLocale())
        .createdAt(d.getCreatedAt())
        .updatedAt(d.getUpdatedAt())
        .build();
  }

  public Page<District> searchDistricts(String name, List<String> stateIds, Pageable pageable) {

    var hasName = name != null;
    var hasStates = stateIds != null;

    if (hasStates) {
      if (stateIds.isEmpty()) {
        return Page.empty(pageable);
      }
      return hasName
          ? districtRepository.findByStateIdInAndNameRegex(stateIds, buildRegex(name), pageable)
          : districtRepository.findByStateIdIn(stateIds, pageable);
    }

    return hasName
        ? districtRepository.findByNameRegex(buildRegex(name), pageable)
        : districtRepository.findAll(pageable);
  }

  private static String buildRegex(String value) {
    return ".*" + Pattern.quote(value) + ".*";
  }
}
