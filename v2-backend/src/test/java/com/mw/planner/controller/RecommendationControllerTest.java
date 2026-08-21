package com.mw.planner.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.dto.recommendation.GenerateRecommendationRequestDTO;
import com.mw.planner.dto.recommendation.RecommendationStatusResponseDTO;
import com.mw.planner.exception.GlobalExceptionHandler;
import com.mw.planner.service.CampaignService;
import com.mw.planner.service.MessageService;
import com.mw.planner.service.MetricsService;
import com.mw.planner.service.UserService;
import com.mw.planner.service.recommendation.RecommendationService;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RecommendationControllerTest {

  @Mock private RecommendationService recommendationService;
  @Mock private CampaignService campaignService;
  @Mock private UserService userService;
  @Mock private MessageService messageService;
  @Mock private MetricsService metricsService;

  @InjectMocks private RecommendationController recommendationController;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(recommendationController)
            .setControllerAdvice(
                new GlobalExceptionHandler(messageService, metricsService, userService))
            .build();
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();
  }

  @Test
  void generateRecommendation_noBody_shouldSucceedWithNullRequest() throws Exception {
    // Given
    RecommendationStatusResponseDTO statusResponse = new RecommendationStatusResponseDTO();
    statusResponse.setRunId("run123");
    statusResponse.setStatus(RecommendationStatusResponseDTO.RunStatus.IN_PROGRESS);
    when(recommendationService.generateRecommendation(eq("campaign123"), isNull(), eq(false)))
        .thenReturn(statusResponse);

    // When / Then
    mockMvc
        .perform(post("/api/v1/recommendation/campaigns/{campaignId}/generate", "campaign123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.runId").value("run123"));

    verify(recommendationService).generateRecommendation(eq("campaign123"), isNull(), eq(false));
  }

  @Test
  void generateRecommendation_withMediaOwnerIds_shouldPassThroughToService() throws Exception {
    // Given
    RecommendationStatusResponseDTO statusResponse = new RecommendationStatusResponseDTO();
    statusResponse.setRunId("run123");
    statusResponse.setStatus(RecommendationStatusResponseDTO.RunStatus.IN_PROGRESS);
    when(recommendationService.generateRecommendation(eq("campaign123"), any(), eq(false)))
        .thenReturn(statusResponse);

    GenerateRecommendationRequestDTO requestBody =
        GenerateRecommendationRequestDTO.builder()
            .mediaOwnerIds(Arrays.asList("mo1", "mo2"))
            .build();

    // When / Then
    mockMvc
        .perform(
            post("/api/v1/recommendation/campaigns/{campaignId}/generate", "campaign123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.runId").value("run123"));

    ArgumentCaptor<GenerateRecommendationRequestDTO> captor =
        ArgumentCaptor.forClass(GenerateRecommendationRequestDTO.class);
    verify(recommendationService)
        .generateRecommendation(eq("campaign123"), captor.capture(), eq(false));
    org.assertj.core.api.Assertions.assertThat(captor.getValue().getMediaOwnerIds())
        .containsExactly("mo1", "mo2");
  }

  @Test
  void generateRecommendation_forceRegenerateTrue_shouldPassTrueToService() throws Exception {
    // Given
    RecommendationStatusResponseDTO statusResponse = new RecommendationStatusResponseDTO();
    statusResponse.setRunId("run123");
    statusResponse.setStatus(RecommendationStatusResponseDTO.RunStatus.IN_PROGRESS);
    when(recommendationService.generateRecommendation(eq("campaign123"), isNull(), eq(true)))
        .thenReturn(statusResponse);

    // When / Then
    mockMvc
        .perform(
            post("/api/v1/recommendation/campaigns/{campaignId}/generate", "campaign123")
                .param("forceRegenerate", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.runId").value("run123"));

    verify(recommendationService).generateRecommendation(eq("campaign123"), isNull(), eq(true));
  }

  @Test
  void generateRecommendation_forceRegenerateFalse_shouldPassFalseToService() throws Exception {
    // Given
    RecommendationStatusResponseDTO statusResponse = new RecommendationStatusResponseDTO();
    statusResponse.setRunId("run123");
    statusResponse.setStatus(RecommendationStatusResponseDTO.RunStatus.IN_PROGRESS);
    when(recommendationService.generateRecommendation(eq("campaign123"), isNull(), eq(false)))
        .thenReturn(statusResponse);

    // When / Then
    mockMvc
        .perform(
            post("/api/v1/recommendation/campaigns/{campaignId}/generate", "campaign123")
                .param("forceRegenerate", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.runId").value("run123"));

    verify(recommendationService).generateRecommendation(eq("campaign123"), isNull(), eq(false));
  }

  @Test
  void generateRecommendation_forceRegenerateEmpty_shouldPassFalseToService() throws Exception {
    // Given
    RecommendationStatusResponseDTO statusResponse = new RecommendationStatusResponseDTO();
    statusResponse.setRunId("run123");
    statusResponse.setStatus(RecommendationStatusResponseDTO.RunStatus.IN_PROGRESS);
    when(recommendationService.generateRecommendation(eq("campaign123"), isNull(), eq(false)))
        .thenReturn(statusResponse);

    // When / Then
    mockMvc
        .perform(
            post("/api/v1/recommendation/campaigns/{campaignId}/generate", "campaign123")
                .param("forceRegenerate", ""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.runId").value("run123"));

    verify(recommendationService).generateRecommendation(eq("campaign123"), isNull(), eq(false));
  }
}
