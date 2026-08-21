package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.District;
import com.mw.planner.domain.State;
import com.mw.planner.dto.MwDistrictDTO;
import com.mw.planner.repository.DistrictRepository;
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
class DistrictServiceTest {

  @Mock private MwMasterDataService mwMasterDataService;
  @Mock private DistrictRepository districtRepository;
  @Mock private StateRepository stateRepository;
  @Mock private VirtualThreadService virtualThreadService;

  @InjectMocks private DistrictService districtService;

  private District testDistrict;
  private State testState;
  private MwDistrictDTO testMwDistrictDTO;

  @BeforeEach
  void setUp() {
    testDistrict = createTestDistrict();
    testState = createTestState();
    testMwDistrictDTO = createTestMwDistrictDTO();
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(
        mwMasterDataService, districtRepository, stateRepository, virtualThreadService);
  }

  // ========== findByName Tests ==========

  @Test
  @DisplayName("findByName - Should return district when found")
  void findByName_WithExistingDistrict_ShouldReturnDistrict() {
    // Given
    String districtName = "Los Angeles";
    when(districtRepository.findByName(districtName)).thenReturn(Optional.of(testDistrict));

    // When
    Optional<District> result = districtService.findByName(districtName);

    // Then
    assertThat(result).isPresent();
    assertThat(result.get().getName()).isEqualTo("Los Angeles");
    assertThat(result.get().getStateId()).isEqualTo("CA");
    verify(districtRepository).findByName(districtName);
  }

  @Test
  @DisplayName("findByName - Should return empty when district not found")
  void findByName_WithNonExistentDistrict_ShouldReturnEmpty() {
    // Given
    String districtName = "NonExistent";
    when(districtRepository.findByName(districtName)).thenReturn(Optional.empty());

    // When
    Optional<District> result = districtService.findByName(districtName);

    // Then
    assertThat(result).isEmpty();
    verify(districtRepository).findByName(districtName);
  }

  // ========== syncAllDistrictsAsync Tests ==========

  @Test
  @DisplayName("syncAllDistrictsAsync - Should sync districts successfully")
  void syncAllDistrictsAsync_WithValidData_ShouldSyncDistricts() throws Exception {
    // Given
    when(stateRepository.findAll()).thenReturn(List.of(testState));
    when(virtualThreadService.runDistrictSyncAsync(any(Runnable.class)))
        .thenAnswer(
            invocation -> {
              Runnable task = invocation.getArgument(0);
              task.run();
              return CompletableFuture.completedFuture(null);
            });
    when(mwMasterDataService.fetchDistrictsFromMasterDataApi(anyString()))
        .thenReturn(List.of(testMwDistrictDTO));
    when(districtRepository.findByStateId(anyString())).thenReturn(List.of());
    when(districtRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

    // When
    CompletableFuture<Void> future = districtService.syncAllDistrictsAsync();

    // Then
    future.join(); // Wait for completion
    verify(stateRepository).findAll();
    verify(virtualThreadService).runDistrictSyncAsync(any(Runnable.class));
  }

  @Test
  @DisplayName("syncAllDistrictsAsync - Should handle empty states")
  void syncAllDistrictsAsync_WithNoStates_ShouldCompleteWithoutError() throws Exception {
    // Given
    when(stateRepository.findAll()).thenReturn(List.of());

    // When
    CompletableFuture<Void> future = districtService.syncAllDistrictsAsync();

    // Then
    future.join(); // Wait for completion
    verify(stateRepository).findAll();
    verify(virtualThreadService, never()).runDistrictSyncAsync(any(Runnable.class));
  }

  @Test
  @DisplayName("syncAllDistrictsAsync - Should handle empty districts for state")
  void syncAllDistrictsAsync_WithEmptyDistricts_ShouldCompleteWithoutError() throws Exception {
    // Given
    when(stateRepository.findAll()).thenReturn(List.of(testState));
    when(virtualThreadService.runDistrictSyncAsync(any(Runnable.class)))
        .thenAnswer(
            invocation -> {
              Runnable task = invocation.getArgument(0);
              task.run();
              return CompletableFuture.completedFuture(null);
            });
    when(mwMasterDataService.fetchDistrictsFromMasterDataApi(anyString())).thenReturn(List.of());

    // When
    CompletableFuture<Void> future = districtService.syncAllDistrictsAsync();

    // Then
    future.join(); // Wait for completion
    verify(stateRepository).findAll();
    verify(virtualThreadService).runDistrictSyncAsync(any(Runnable.class));
    verify(districtRepository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("syncAllDistrictsAsync - Should handle exceptions gracefully")
  void syncAllDistrictsAsync_WithException_ShouldCompleteWithoutThrowing() throws Exception {
    // Given
    when(stateRepository.findAll()).thenThrow(new RuntimeException("Database Error"));

    // When
    CompletableFuture<Void> future = districtService.syncAllDistrictsAsync();

    // Then
    future.join(); // Should complete without throwing
    verify(stateRepository).findAll();
  }

  // ========== Helper Methods ==========

  private District createTestDistrict() {
    District district = new District();
    district.setId("district123");
    district.setName("Los Angeles");
    district.setStateId("CA");
    district.setType("DISTRICT");
    district.setLatitude(34.0522);
    district.setLongitude(-118.2437);
    return district;
  }

  private State createTestState() {
    State state = new State();
    state.setId("state123");
    state.setStateId("CA");
    state.setName("California");
    state.setType("STATE");
    state.setCountryId("US");
    return state;
  }

  private MwDistrictDTO createTestMwDistrictDTO() {
    MwDistrictDTO dto = new MwDistrictDTO();
    dto.setId("district123");
    dto.setName("Los Angeles");
    dto.setType("DISTRICT");
    dto.setLatitude(34.0522);
    dto.setLongitude(-118.2437);
    return dto;
  }
}
