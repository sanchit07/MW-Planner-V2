package com.mw.recommendation.engine.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import com.mw.recommendation.engine.dto.RecommendationStatusResponseDTO;
import com.mw.recommendation.engine.exception.GlobalExceptionHandler;
import com.mw.recommendation.engine.service.RecommendationService;
import com.mw.recommendation.engine.service.RecommendationV2Service;
import com.mw.recommendation.engine.service.ScheduleRecommendationService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Tests the additive v2 submit endpoint. The v2 endpoint mirrors v1 but delegates to {@link
 * RecommendationV2Service} (the optimized pipeline entry point). v1 behavior is unchanged.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationV2ControllerTest {

  @Mock private RecommendationService recommendationService;
  @Mock private RecommendationV2Service recommendationV2Service;
  @Mock private ScheduleRecommendationService scheduleRecommendationService;
  @Mock private MessageSource messageSource;

  @InjectMocks private RecommendationController recommendationController;

  private ObjectMapper objectMapper;
  private MockMvc mockMvc;

  private RecommendationRequestDTO testRequest;
  private RecommendationStatusResponseDTO testStatusResponse;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(recommendationController)
            .setControllerAdvice(new GlobalExceptionHandler(messageSource))
            .build();
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();

    testRequest = new RecommendationRequestDTO();
    testRequest.setCountry("US");
    testRequest.setStartDate(LocalDate.of(2024, 1, 1));
    testRequest.setEndDate(LocalDate.of(2024, 1, 31));
    testRequest.setProductId("product-1");
    testRequest.setCompanyId("company-1");

    testStatusResponse =
        RecommendationStatusResponseDTO.builder()
            .runId("run-v2-123")
            .status(RecommendationStatusResponseDTO.RunStatus.IN_PROGRESS)
            .completionPercentage(0)
            .campaignId("campaign-1")
            .build();
  }

  @Test
  void submitRecommendationV2_withValidRequest_returnsRunIdFromV2Service() throws Exception {
    when(recommendationV2Service.submitRecommendation(
            anyString(), any(RecommendationRequestDTO.class), anyBoolean()))
        .thenReturn(testStatusResponse);

    mockMvc
        .perform(
            post("/api/v1/recommendation/campaigns/{campaignId}/recommendations/v2", "campaign-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.runId").value("run-v2-123"));

    verify(recommendationV2Service)
        .submitRecommendation(eq("campaign-1"), any(RecommendationRequestDTO.class), eq(false));
    // v2 route must not touch the v1 service
    verify(recommendationService, never()).submitRecommendation(anyString(), any(), anyBoolean());
  }

  @Test
  void submitRecommendationV2_forceRegenerateTrue_passesTrueToV2Service() throws Exception {
    when(recommendationV2Service.submitRecommendation(
            anyString(), any(RecommendationRequestDTO.class), anyBoolean()))
        .thenReturn(testStatusResponse);

    mockMvc
        .perform(
            post("/api/v1/recommendation/campaigns/{campaignId}/recommendations/v2", "campaign-1")
                .param("forceRegenerate", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.runId").value("run-v2-123"));

    verify(recommendationV2Service)
        .submitRecommendation(eq("campaign-1"), any(RecommendationRequestDTO.class), eq(true));
  }
}
