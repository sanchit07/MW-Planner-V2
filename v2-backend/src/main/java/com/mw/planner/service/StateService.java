package com.mw.planner.service;

import com.mw.planner.domain.State;
import com.mw.planner.dto.MwStateDTO;
import com.mw.planner.repository.StateRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StateService {

  private final MwMasterDataService mwMasterDataService;
  private final StateRepository stateRepository;

  /** Start async state sync for all states */
  @Async
  @CacheEvict(value = "states", allEntries = true)
  public CompletableFuture<Void> syncAllStatesAsync() {
    log.info("Starting async state sync");

    try {
      // Fetch all states from external API
      List<MwStateDTO> externalStates = mwMasterDataService.fetchStatesFromMasterDataApi();

      if (externalStates.isEmpty()) {
        log.warn("No states received from MW Master Data API");
        return CompletableFuture.completedFuture(null);
      }

      log.info("Retrieved {} states from MW Master Data API", externalStates.size());

      AtomicInteger totalFetched = new AtomicInteger(externalStates.size());
      AtomicInteger totalSaved = new AtomicInteger(0);
      AtomicInteger errorCount = new AtomicInteger(0);

      // Get all existing states
      List<State> existingStates = stateRepository.findAll();
      Map<String, State> existingStatesMap =
          existingStates.stream().collect(Collectors.toMap(State::getId, state -> state));

      List<State> statesToSave = new ArrayList<>();

      // Process each state
      for (MwStateDTO externalState : externalStates) {
        try {
          State existingState = existingStatesMap.get(externalState.getId());
          State processedState;

          if (existingState != null) {
            processedState = updateExistingState(existingState, externalState);
          } else {
            processedState = createNewState(externalState);
          }

          statesToSave.add(processedState);
        } catch (Exception e) {
          log.error("Failed to process state {}: {}", externalState.getStateId(), e.getMessage());
          errorCount.incrementAndGet();
        }
      }

      // Bulk save states
      if (!statesToSave.isEmpty()) {
        stateRepository.saveAll(statesToSave);
        totalSaved.addAndGet(statesToSave.size());
        log.info("Saved {} states", statesToSave.size());
      }

      log.info(
          "State sync completed. Fetched: {}, Saved: {}, Errors: {}",
          totalFetched.get(),
          totalSaved.get(),
          errorCount.get());

    } catch (Exception e) {
      log.error("Failed to sync states", e);
    }

    return CompletableFuture.completedFuture(null);
  }

  /** Update existing state with external data */
  private State updateExistingState(State existingState, MwStateDTO externalState) {
    existingState.setId(externalState.getId());
    existingState.setName(externalState.getName());
    existingState.setType(externalState.getType());
    existingState.setCountryId(externalState.getCountry().getId());
    existingState.setLatitude(externalState.getLatitude());
    existingState.setLongitude(externalState.getLongitude());
    existingState.setZoom(externalState.getZoom());
    existingState.setPopulation(externalState.getPopulation());
    existingState.setIso(externalState.getIso());
    existingState.setLocale(externalState.getLocale());
    return existingState;
  }

  /** Create new state from external data */
  private State createNewState(MwStateDTO externalState) {
    State newState = new State();
    newState.setId(externalState.getId());
    newState.setStateId(externalState.getStateId());
    newState.setName(externalState.getName());
    newState.setType(externalState.getType());
    newState.setCountryId(externalState.getCountry().getId());
    newState.setLatitude(externalState.getLatitude());
    newState.setLongitude(externalState.getLongitude());
    newState.setZoom(externalState.getZoom());
    newState.setPopulation(externalState.getPopulation());
    newState.setIso(externalState.getIso());
    newState.setLocale(externalState.getLocale());
    return newState;
  }

  /** Get state by name */
  @Cacheable(value = "states", key = "#name")
  public Optional<State> findByName(String name) {
    log.debug("Fetching state by name: {}", name);
    return stateRepository.findByName(name);
  }
}
