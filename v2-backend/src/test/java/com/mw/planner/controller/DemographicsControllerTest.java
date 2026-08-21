package com.mw.planner.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.dto.*;
import com.mw.planner.exception.GlobalExceptionHandler;
import com.mw.planner.service.DemographicsService;
import com.mw.planner.service.MessageService;
import com.mw.planner.service.MetricsService;
import com.mw.planner.service.UserService;
import java.util.Collections;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DemographicsControllerTest {

  @Mock private DemographicsService demographicsService;
  @Mock private UserService userService;
  @Mock private MessageService messageService;
  @Mock private MetricsService metricsService;

  @InjectMocks private DemographicsController demographicsController;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;
  private IamUserContext testUserContext;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(demographicsController)
            .setControllerAdvice(
                new GlobalExceptionHandler(messageService, metricsService, userService))
            .build();
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();
    testUserContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .locale(Locale.ENGLISH)
            .build();
    lenient().when(userService.getIamUserContext()).thenReturn(testUserContext);
  }

  @Test
  void getConfigDemographics_ReturnsSuccess() throws Exception {
    DemographicsConfigResponseDTO config =
        new DemographicsConfigResponseDTO(
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    when(demographicsService.getConfigDemographics()).thenReturn(config);

    mockMvc
        .perform(get("/api/v1/demographics").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").exists());

    verify(demographicsService).getConfigDemographics();
  }

  @Test
  void autoSaveCampaign_WithValidRequest_ReturnsSuccess() throws Exception {
    DemographicAutoSaveRequestDTO request =
        new DemographicAutoSaveRequestDTO("age", "18_24", "18-24", null);
    DemographicsResponseDTO response = new DemographicsResponseDTO();
    response.setId("demo123");
    when(demographicsService.autoSaveDemographic(any(), eq("countryId"))).thenReturn(response);

    mockMvc
        .perform(
            patch("/api/v1/demographics/autosave")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").exists());

    verify(demographicsService)
        .autoSaveDemographic(any(DemographicAutoSaveRequestDTO.class), eq("countryId"));
  }
}
