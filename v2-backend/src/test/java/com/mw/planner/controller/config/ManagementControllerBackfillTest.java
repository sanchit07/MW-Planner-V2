package com.mw.planner.controller.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mw.planner.domain.Campaign;
import com.mw.planner.dto.PerformanceBackfillJobStatusDTO;
import com.mw.planner.exception.GlobalExceptionHandler;
import com.mw.planner.exception.campaign.PerformanceBackfillAlreadyRunningException;
import com.mw.planner.exception.campaign.PerformanceBackfillJobNotFoundException;
import com.mw.planner.service.CampaignPerformanceBackfillService;
import com.mw.planner.service.CountryService;
import com.mw.planner.service.DistrictService;
import com.mw.planner.service.InventoryCountrySummaryService;
import com.mw.planner.service.MessageService;
import com.mw.planner.service.MetricsService;
import com.mw.planner.service.StateService;
import com.mw.planner.service.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ManagementControllerBackfillTest {

  @Mock private CountryService countryService;
  @Mock private StateService stateService;
  @Mock private DistrictService districtService;
  @Mock private InventoryCountrySummaryService inventoryCountrySummaryService;
  @Mock private CampaignPerformanceBackfillService campaignPerformanceBackfillService;
  @Mock private UserService userService;
  @Mock private MessageService messageService;
  @Mock private MetricsService metricsService;

  @InjectMocks private ManagementController managementController;

  private MockMvc mockMvc;

  private static final String BASE_PATH = "/api/v1/management/campaigns/performance-backfill";
  private static final String HEADER = "X-Measure-Authorization";

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(managementController)
            .setControllerAdvice(
                new GlobalExceptionHandler(messageService, metricsService, userService))
            .build();
  }

  private PerformanceBackfillJobStatusDTO runningJob() {
    return PerformanceBackfillJobStatusDTO.builder()
        .jobId("job-123")
        .state(PerformanceBackfillJobStatusDTO.State.RUNNING)
        .build();
  }

  @Test
  @DisplayName("POST returns 202 with the job id and strips the Bearer prefix from the header")
  void startBackfill_returns202WithJobId() throws Exception {
    when(campaignPerformanceBackfillService.startBackfill(isNull(), eq("the-jwt"), anyString()))
        .thenReturn(runningJob());

    mockMvc
        .perform(post(BASE_PATH).header(HEADER, "Bearer the-jwt"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.jobId").value("job-123"))
        .andExpect(jsonPath("$.data.state").value("RUNNING"));

    verify(campaignPerformanceBackfillService).startBackfill(isNull(), eq("the-jwt"), anyString());
  }

  @Test
  @DisplayName("POST passes an explicit statuses filter through to the service")
  void startBackfill_passesStatuses() throws Exception {
    when(campaignPerformanceBackfillService.startBackfill(
            eq(List.of(Campaign.Status.DRAFT, Campaign.Status.PLANNED)), eq("t"), anyString()))
        .thenReturn(runningJob());

    mockMvc
        .perform(post(BASE_PATH).param("statuses", "DRAFT,PLANNED").header(HEADER, "Bearer t"))
        .andExpect(status().isAccepted());

    verify(campaignPerformanceBackfillService)
        .startBackfill(
            eq(List.of(Campaign.Status.DRAFT, Campaign.Status.PLANNED)), eq("t"), anyString());
  }

  @Test
  @DisplayName("POST without the measure token header is rejected with 400")
  void startBackfill_missingHeaderIs400() throws Exception {
    mockMvc.perform(post(BASE_PATH)).andExpect(status().isBadRequest());
    verify(campaignPerformanceBackfillService, never()).startBackfill(any(), any(), any());
  }

  @Test
  @DisplayName("POST with a blank token is rejected with 400")
  void startBackfill_blankTokenIs400() throws Exception {
    mockMvc.perform(post(BASE_PATH).header(HEADER, "Bearer   ")).andExpect(status().isBadRequest());
    verify(campaignPerformanceBackfillService, never()).startBackfill(any(), any(), any());
  }

  @Test
  @DisplayName("POST while a sweep is running returns 409")
  void startBackfill_conflictWhenAlreadyRunning() throws Exception {
    when(campaignPerformanceBackfillService.startBackfill(isNull(), eq("t"), anyString()))
        .thenThrow(new PerformanceBackfillAlreadyRunningException("job-999"));

    mockMvc
        .perform(post(BASE_PATH).header(HEADER, "Bearer t"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.errorCode").value("ERR_3019"));
  }

  @Test
  @DisplayName("GET returns the job status")
  void getStatus_returnsJob() throws Exception {
    when(campaignPerformanceBackfillService.getJobStatus("job-123")).thenReturn(runningJob());

    mockMvc
        .perform(get(BASE_PATH + "/job-123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.jobId").value("job-123"));
  }

  @Test
  @DisplayName("GET for an unknown job id returns 404")
  void getStatus_unknownJobIs404() throws Exception {
    when(campaignPerformanceBackfillService.getJobStatus("nope"))
        .thenThrow(new PerformanceBackfillJobNotFoundException("nope"));

    mockMvc
        .perform(get(BASE_PATH + "/nope"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.errorCode").value("ERR_3020"));
  }
}
