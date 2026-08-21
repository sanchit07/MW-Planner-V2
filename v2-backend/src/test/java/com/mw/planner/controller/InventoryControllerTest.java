package com.mw.planner.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.domain.Inventory;
import com.mw.planner.dto.InventoryAvailabilityRequestDTO;
import com.mw.planner.dto.InventoryResponseDTO;
import com.mw.planner.exception.GlobalExceptionHandler;
import com.mw.planner.service.InventoryService;
import com.mw.planner.service.MessageService;
import com.mw.planner.service.MetricsService;
import com.mw.planner.service.UserService;
import com.mw.planner.service.availability.ImsAvailabilitySyncService;
import java.time.LocalDateTime;
import java.util.List;
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
class InventoryControllerTest {

  @Mock private InventoryService inventoryService;
  @Mock private ImsAvailabilitySyncService imsAvailabilitySyncService;
  @Mock private UserService userService;
  @Mock private MessageService messageService;
  @Mock private MetricsService metricsService;

  @InjectMocks private InventoryController inventoryController;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;
  private Inventory testInventory;
  private InventoryResponseDTO testInventoryResponseDTO;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(inventoryController)
            .setControllerAdvice(
                new GlobalExceptionHandler(messageService, metricsService, userService))
            .build();
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();

    // Setup test inventory
    testInventory = new Inventory();
    testInventory.setId("inventory123");
    testInventory.setName("Test Inventory");
    testInventory.setArchived(false);

    // Setup test inventory response DTO
    testInventoryResponseDTO = new InventoryResponseDTO();
    testInventoryResponseDTO.setId("inventory123");
    testInventoryResponseDTO.setName("Test Inventory");
    testInventoryResponseDTO.setSize("48x14");
    testInventoryResponseDTO.setInventoryCluster(List.of("cluster-A"));
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(inventoryService);
    verifyNoMoreInteractions(imsAvailabilitySyncService);
  }

  @Test
  void getInventoryById_WithValidId_ShouldReturnInventory() throws Exception {
    // Given
    when(inventoryService.getInventoryResponseDTOById("inventory123"))
        .thenReturn(testInventoryResponseDTO);

    // When & Then
    mockMvc
        .perform(get("/api/v1/inventories/inventory123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("inventory123"))
        .andExpect(jsonPath("$.name").value("Test Inventory"))
        .andExpect(jsonPath("$.size").value("48x14"))
        .andExpect(jsonPath("$.inventoryCluster[0]").value("cluster-A"));

    verify(inventoryService).getInventoryResponseDTOById("inventory123");
  }

  @Test
  void getInventoryAvailability_WithValidBody_ShouldReturnResponse() throws Exception {
    // Given
    InventoryAvailabilityRequestDTO request =
        InventoryAvailabilityRequestDTO.builder()
            .startTime(LocalDateTime.parse("2024-12-14T00:00:00"))
            .endTime(LocalDateTime.parse("2026-05-02T00:00:00"))
            .inventoryIds(List.of("550e8400-e29b-41d4-a716-446655440001"))
            .build();

    when(imsAvailabilitySyncService.getAvailability(any(InventoryAvailabilityRequestDTO.class)))
        .thenReturn(java.util.Map.of("inventories", java.util.Map.of()));

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/inventories/availability")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"inventories\":{}}"));

    verify(imsAvailabilitySyncService).getAvailability(any(InventoryAvailabilityRequestDTO.class));
  }
}
