package com.mw.planner.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.domain.AvailabilitySyncStatus;
import com.mw.planner.dto.InventoryAvailabilityRequestDTO;
import com.mw.planner.service.availability.ImsAvailabilitySyncService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InventoryAvailabilityControllerTest {

  private static final String BASE = "/proxy/inventory-api/api/v1/inventories/availability";

  @Mock private ImsAvailabilitySyncService imsAvailabilitySyncService;

  @InjectMocks private InventoryAvailabilityController controller;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(imsAvailabilitySyncService);
  }

  private AvailabilitySyncStatus successStatus() {
    return AvailabilitySyncStatus.builder()
        .id(AvailabilitySyncStatus.IMS_AVAILABILITY_ID)
        .state(AvailabilitySyncStatus.State.SUCCESS)
        .trigger(AvailabilitySyncStatus.Trigger.MANUAL)
        .startedAt(Instant.parse("2026-08-14T10:00:00Z"))
        .completedAt(Instant.parse("2026-08-14T10:05:00Z"))
        .lastSuccessAt(Instant.parse("2026-08-14T10:05:00Z"))
        .inventoryCount(42)
        .build();
  }

  @Test
  void getAvailability_ServesStoreBackedResponseWithSyncMetadata() throws Exception {
    InventoryAvailabilityRequestDTO request =
        InventoryAvailabilityRequestDTO.builder()
            .startTime(LocalDateTime.parse("2026-08-01T00:00:00"))
            .endTime(LocalDateTime.parse("2026-09-01T00:00:00"))
            .inventoryIds(List.of("EXT-1"))
            .build();

    Map<String, Object> serviceResponse = new LinkedHashMap<>();
    serviceResponse.put("inventories", Map.of("EXT-1", Map.of("id", "EXT-1")));
    Map<String, Object> sync = new LinkedHashMap<>();
    sync.put("source", "IMS");
    sync.put("lastSyncedAt", "2026-08-14T10:05:00Z");
    sync.put("status", "SUCCESS");
    sync.put("error", null);
    serviceResponse.put("sync", sync);
    when(imsAvailabilitySyncService.getAvailability(any(InventoryAvailabilityRequestDTO.class)))
        .thenReturn(serviceResponse);

    mockMvc
        .perform(
            post(BASE)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.inventories['EXT-1'].id").value("EXT-1"))
        .andExpect(jsonPath("$.sync.source").value("IMS"))
        .andExpect(jsonPath("$.sync.status").value("SUCCESS"))
        .andExpect(jsonPath("$.sync.lastSyncedAt").value("2026-08-14T10:05:00Z"));

    verify(imsAvailabilitySyncService).getAvailability(any(InventoryAvailabilityRequestDTO.class));
  }

  @Test
  void triggerSync_WhenStarted_Returns202WithStatusAndStartedTrue() throws Exception {
    when(imsAvailabilitySyncService.syncAllAsync(AvailabilitySyncStatus.Trigger.MANUAL))
        .thenReturn(true);
    when(imsAvailabilitySyncService.getStatus()).thenReturn(successStatus());

    mockMvc
        .perform(post(BASE + "/sync"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.started").value(true))
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.trigger").value("MANUAL"))
        .andExpect(jsonPath("$.inventoryCount").value(42));

    verify(imsAvailabilitySyncService).syncAllAsync(AvailabilitySyncStatus.Trigger.MANUAL);
    verify(imsAvailabilitySyncService).getStatus();
  }

  @Test
  void triggerSync_WhenAlreadyRunning_Returns409WithStartedFalse() throws Exception {
    when(imsAvailabilitySyncService.syncAllAsync(AvailabilitySyncStatus.Trigger.MANUAL))
        .thenReturn(false);
    AvailabilitySyncStatus running = successStatus();
    running.setState(AvailabilitySyncStatus.State.RUNNING);
    running.setCompletedAt(null);
    when(imsAvailabilitySyncService.getStatus()).thenReturn(running);

    mockMvc
        .perform(post(BASE + "/sync"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.started").value(false))
        .andExpect(jsonPath("$.status").value("RUNNING"));

    verify(imsAvailabilitySyncService).syncAllAsync(AvailabilitySyncStatus.Trigger.MANUAL);
    verify(imsAvailabilitySyncService).getStatus();
  }

  @Test
  void getSyncStatus_WhenNeverRun_ReturnsNeverRun() throws Exception {
    when(imsAvailabilitySyncService.getStatus()).thenReturn(null);

    mockMvc
        .perform(get(BASE + "/sync-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("NEVER_RUN"));

    verify(imsAvailabilitySyncService).getStatus();
  }

  @Test
  void getSyncStatus_WithFailedRun_SurfacesErrorAndTimestamps() throws Exception {
    AvailabilitySyncStatus failed = successStatus();
    failed.setState(AvailabilitySyncStatus.State.FAILED);
    failed.setError("Failed for 3 of 10 inventories: EXT-9: feed down");
    when(imsAvailabilitySyncService.getStatus()).thenReturn(failed);

    mockMvc
        .perform(get(BASE + "/sync-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FAILED"))
        .andExpect(jsonPath("$.error").value("Failed for 3 of 10 inventories: EXT-9: feed down"))
        .andExpect(jsonPath("$.startedAt").value("2026-08-14T10:00:00Z"))
        .andExpect(jsonPath("$.lastSuccessAt").value("2026-08-14T10:05:00Z"));

    verify(imsAvailabilitySyncService).getStatus();
  }
}
