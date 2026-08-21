package com.mw.planner.controller.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mw.planner.dto.ApiResponse;
import com.mw.planner.dto.CountrySyncResponseDTO;
import com.mw.planner.dto.PlanNumberBackfillResultDTO;
import com.mw.planner.dto.SyncResponseDTO;
import com.mw.planner.service.CampaignService;
import com.mw.planner.service.CountryService;
import com.mw.planner.service.DistrictService;
import com.mw.planner.service.InventoryCountrySummaryService;
import com.mw.planner.service.StateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManagementControllerTest {

  @Mock private CountryService countryService;
  @Mock private StateService stateService;
  @Mock private DistrictService districtService;
  @Mock private InventoryCountrySummaryService inventoryCountrySummaryService;
  @Mock private CampaignService campaignService;

  @InjectMocks private ManagementController managementController;

  private CountrySyncResponseDTO sampleSyncResponse;

  @BeforeEach
  void setUp() {
    sampleSyncResponse =
        new CountrySyncResponseDTO(195, 12, 3, "Country sync completed successfully");
  }

  @Test
  void testSyncCountries_Success() {
    // Arrange
    when(countryService.syncCountriesFromExternalApi()).thenReturn(sampleSyncResponse);

    // Act
    ApiResponse<CountrySyncResponseDTO> result = managementController.syncCountries();

    // Assert
    assertNotNull(result);
    assertTrue(result.isSuccess());
    assertNotNull(result.getData());
    assertEquals(195, result.getData().getSyncedCount());
    assertEquals(12, result.getData().getUpdatedCount());
    assertEquals(3, result.getData().getCreatedCount());
    assertEquals("Country sync completed successfully", result.getData().getMessage());
    assertNull(result.getError());

    verify(countryService, times(1)).syncCountriesFromExternalApi();
  }

  @Test
  void testSyncCountries_WithEmptyResult() {
    // Arrange
    CountrySyncResponseDTO emptyResponse =
        new CountrySyncResponseDTO(0, 0, 0, "No countries found");
    when(countryService.syncCountriesFromExternalApi()).thenReturn(emptyResponse);

    // Act
    ApiResponse<CountrySyncResponseDTO> result = managementController.syncCountries();

    // Assert
    assertNotNull(result);
    assertTrue(result.isSuccess());
    assertNotNull(result.getData());
    assertEquals(0, result.getData().getSyncedCount());
    assertEquals(0, result.getData().getUpdatedCount());
    assertEquals(0, result.getData().getCreatedCount());
    assertEquals("No countries found", result.getData().getMessage());
    assertNull(result.getError());

    verify(countryService, times(1)).syncCountriesFromExternalApi();
  }

  @Test
  void testSyncCountries_WithLargeDataset() {
    // Arrange
    CountrySyncResponseDTO largeResponse =
        new CountrySyncResponseDTO(1000, 500, 500, "Large dataset sync completed");
    when(countryService.syncCountriesFromExternalApi()).thenReturn(largeResponse);

    // Act
    ApiResponse<CountrySyncResponseDTO> result = managementController.syncCountries();

    // Assert
    assertNotNull(result);
    assertTrue(result.isSuccess());
    assertNotNull(result.getData());
    assertEquals(1000, result.getData().getSyncedCount());
    assertEquals(500, result.getData().getUpdatedCount());
    assertEquals(500, result.getData().getCreatedCount());
    assertEquals("Large dataset sync completed", result.getData().getMessage());
    assertNull(result.getError());

    verify(countryService, times(1)).syncCountriesFromExternalApi();
  }

  // ========== Sync States Tests ==========

  @Test
  void testSyncStates_Success() {
    // Arrange
    when(stateService.syncAllStatesAsync())
        .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

    // Act
    ApiResponse<SyncResponseDTO> result = managementController.syncStates();

    // Assert
    assertNotNull(result);
    assertTrue(result.isSuccess());
    assertNotNull(result.getData());
    assertEquals("State sync started successfully", result.getData().getMessage());
    assertEquals("STATE", result.getData().getType());
    assertNull(result.getError());

    verify(stateService, times(1)).syncAllStatesAsync();
  }

  @Test
  void testSyncStates_WhenServiceThrowsException() {
    // Arrange
    doThrow(new RuntimeException("State sync service failed"))
        .when(stateService)
        .syncAllStatesAsync();

    // Act & Assert
    assertThrows(RuntimeException.class, () -> managementController.syncStates());
    verify(stateService, times(1)).syncAllStatesAsync();
  }

  // ========== Sync Districts Tests ==========

  @Test
  void testSyncDistricts_Success() {
    // Arrange
    when(districtService.syncAllDistrictsAsync())
        .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

    // Act
    ApiResponse<SyncResponseDTO> result = managementController.syncDistricts();

    // Assert
    assertNotNull(result);
    assertTrue(result.isSuccess());
    assertNotNull(result.getData());
    assertEquals("District sync started successfully", result.getData().getMessage());
    assertEquals("DISTRICT", result.getData().getType());
    assertNull(result.getError());

    verify(districtService, times(1)).syncAllDistrictsAsync();
  }

  @Test
  void testSyncDistricts_WhenServiceThrowsException() {
    // Arrange
    doThrow(new RuntimeException("District sync service failed"))
        .when(districtService)
        .syncAllDistrictsAsync();

    // Act & Assert
    assertThrows(RuntimeException.class, () -> managementController.syncDistricts());
    verify(districtService, times(1)).syncAllDistrictsAsync();
  }

  // ========== Rebuild Inventory Summary Tests ==========

  @Test
  void testRebuildInventorySummary_Success() {
    // Arrange
    when(inventoryCountrySummaryService.rebuildAll()).thenReturn(257);

    // Act
    ApiResponse<SyncResponseDTO> result = managementController.rebuildInventorySummary();

    // Assert
    assertNotNull(result);
    assertTrue(result.isSuccess());
    assertNotNull(result.getData());
    assertEquals(
        "Rebuilt inventory country summary for 257 countries", result.getData().getMessage());
    assertEquals("INVENTORY_SUMMARY", result.getData().getType());
    assertNull(result.getError());

    verify(inventoryCountrySummaryService, times(1)).rebuildAll();
  }

  @Test
  void testBackfillPlanNumbers_Success() {
    // Arrange
    PlanNumberBackfillResultDTO backfillResult =
        PlanNumberBackfillResultDTO.builder().processed(142).assigned(140).build();
    when(campaignService.backfillPlanNumbers(500)).thenReturn(backfillResult);

    // Act
    ApiResponse<PlanNumberBackfillResultDTO> result = managementController.backfillPlanNumbers(500);

    // Assert
    assertNotNull(result);
    assertTrue(result.isSuccess());
    assertEquals(142, result.getData().getProcessed());
    assertEquals(140, result.getData().getAssigned());
    verify(campaignService, times(1)).backfillPlanNumbers(500);
  }
}
