package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.State;
import com.mw.planner.dto.MwStateDTO;
import com.mw.planner.repository.StateRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StateServiceTest {

  @Mock private MwMasterDataService mwMasterDataService;
  @Mock private StateRepository stateRepository;

  @InjectMocks private StateService stateService;

  private State testState;
  private MwStateDTO testMwStateDTO;

  @BeforeEach
  void setUp() {
    testState = createTestState();
    testMwStateDTO = createTestMwStateDTO();
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(mwMasterDataService, stateRepository);
  }

  // ========== findByName Tests ==========

  @Test
  @DisplayName("findByName - Should return state when found")
  void findByName_WithExistingState_ShouldReturnState() {
    // Given
    String stateName = "California";
    when(stateRepository.findByName(stateName)).thenReturn(Optional.of(testState));

    // When
    Optional<State> result = stateService.findByName(stateName);

    // Then
    assertThat(result).isPresent();
    assertThat(result.get().getName()).isEqualTo("California");
    assertThat(result.get().getStateId()).isEqualTo("CA");
    verify(stateRepository).findByName(stateName);
  }

  @Test
  @DisplayName("findByName - Should return empty when state not found")
  void findByName_WithNonExistentState_ShouldReturnEmpty() {
    // Given
    String stateName = "NonExistent";
    when(stateRepository.findByName(stateName)).thenReturn(Optional.empty());

    // When
    Optional<State> result = stateService.findByName(stateName);

    // Then
    assertThat(result).isEmpty();
    verify(stateRepository).findByName(stateName);
  }

  // ========== syncAllStatesAsync Tests ==========

  @Test
  @DisplayName("syncAllStatesAsync - Should sync states successfully")
  void syncAllStatesAsync_WithValidData_ShouldSyncStates() throws Exception {
    // Given
    List<MwStateDTO> externalStates = List.of(testMwStateDTO);
    when(mwMasterDataService.fetchStatesFromMasterDataApi()).thenReturn(externalStates);
    when(stateRepository.findAll()).thenReturn(List.of());
    when(stateRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

    // When
    CompletableFuture<Void> future = stateService.syncAllStatesAsync();

    // Then
    future.join(); // Wait for completion
    verify(mwMasterDataService).fetchStatesFromMasterDataApi();
    verify(stateRepository).findAll();
    verify(stateRepository).saveAll(anyList());
  }

  @Test
  @DisplayName("syncAllStatesAsync - Should handle empty external states")
  void syncAllStatesAsync_WithEmptyExternalStates_ShouldCompleteWithoutError() throws Exception {
    // Given
    when(mwMasterDataService.fetchStatesFromMasterDataApi()).thenReturn(List.of());

    // When
    CompletableFuture<Void> future = stateService.syncAllStatesAsync();

    // Then
    future.join(); // Wait for completion
    verify(mwMasterDataService).fetchStatesFromMasterDataApi();
    verify(stateRepository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("syncAllStatesAsync - Should update existing states")
  void syncAllStatesAsync_WithExistingStates_ShouldUpdateStates() throws Exception {
    // Given
    List<MwStateDTO> externalStates = List.of(testMwStateDTO);
    State existingState = createTestState();
    existingState.setName("Old Name");
    when(mwMasterDataService.fetchStatesFromMasterDataApi()).thenReturn(externalStates);
    when(stateRepository.findAll()).thenReturn(List.of(existingState));
    when(stateRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

    // When
    CompletableFuture<Void> future = stateService.syncAllStatesAsync();

    // Then
    future.join(); // Wait for completion
    verify(mwMasterDataService).fetchStatesFromMasterDataApi();
    verify(stateRepository).findAll();
    verify(stateRepository).saveAll(anyList());
  }

  @Test
  @DisplayName("syncAllStatesAsync - Should handle exceptions gracefully")
  void syncAllStatesAsync_WithException_ShouldCompleteWithoutThrowing() throws Exception {
    // Given
    when(mwMasterDataService.fetchStatesFromMasterDataApi())
        .thenThrow(new RuntimeException("API Error"));

    // When
    CompletableFuture<Void> future = stateService.syncAllStatesAsync();

    // Then
    future.join(); // Should complete without throwing
    verify(mwMasterDataService).fetchStatesFromMasterDataApi();
  }

  // ========== Helper Methods ==========

  private State createTestState() {
    State state = new State();
    state.setId("state123");
    state.setStateId("CA");
    state.setName("California");
    state.setType("STATE");
    state.setCountryId("US");
    state.setLatitude(36.7783);
    state.setLongitude(-119.4179);
    return state;
  }

  private MwStateDTO createTestMwStateDTO() {
    MwStateDTO dto = new MwStateDTO();
    dto.setId("state123");
    dto.setStateId("CA");
    dto.setName("California");
    dto.setType("STATE");
    MwStateDTO.Country country = new MwStateDTO.Country();
    country.setId("US");
    dto.setCountry(country);
    dto.setLatitude(36.7783);
    dto.setLongitude(-119.4179);
    return dto;
  }
}
