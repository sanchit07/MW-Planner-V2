package com.mw.recommendation.engine.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.mw.recommendation.engine.dto.RunSchedulesResponseDTO;
import com.mw.recommendation.engine.exception.GlobalExceptionHandler;
import com.mw.recommendation.engine.service.ScheduleRecommendationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ScheduleRecommendationControllerTest {

  @Mock private ScheduleRecommendationService scheduleRecommendationService;
  @Mock private MessageSource messageSource;

  @InjectMocks private ScheduleRecommendationController scheduleRecommendationController;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(scheduleRecommendationController)
            .setControllerAdvice(new GlobalExceptionHandler(messageSource))
            .build();
  }

  @Test
  void testGetSchedulesByRunId_ReturnsSuccess() throws Exception {
    RunSchedulesResponseDTO response =
        RunSchedulesResponseDTO.builder().runId("run-123").schedules(List.of()).build();

    when(scheduleRecommendationService.getSchedulesByRunId("run-123")).thenReturn(response);

    mockMvc
        .perform(get("/api/v1/recommendation/schedules/runs/run-123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.runId").value("run-123"));

    verify(scheduleRecommendationService).getSchedulesByRunId("run-123");
  }

  @Test
  void testGetAutoSelectedSchedulesByRunId_ReturnsSuccess() throws Exception {
    RunSchedulesResponseDTO response =
        RunSchedulesResponseDTO.builder().runId("run-123").schedules(List.of()).build();

    when(scheduleRecommendationService.getSchedulesByRunIdAndSelectionModeAuto("run-123"))
        .thenReturn(response);

    mockMvc
        .perform(get("/api/v1/recommendation/schedules/runs/run-123/auto-selection"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.runId").value("run-123"));

    verify(scheduleRecommendationService).getSchedulesByRunIdAndSelectionModeAuto("run-123");
  }

  @Test
  void testAutoOptimizeSchedules_ReturnsSuccess() throws Exception {
    RunSchedulesResponseDTO response =
        RunSchedulesResponseDTO.builder().runId("run-456").schedules(List.of()).build();

    when(scheduleRecommendationService.autoOptimizeSchedules(eq("run-456"), anyList()))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/recommendation/schedules/runs/run-456/auto-optimize")
                .contentType("application/json")
                .content("{\"inventoryIds\":[\"inv-1\",\"inv-2\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.runId").value("run-456"));

    verify(scheduleRecommendationService).autoOptimizeSchedules(eq("run-456"), anyList());
  }
}
