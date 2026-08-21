package com.mw.recommendation.engine.v3.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mw.recommendation.engine.v3.domain.RecommendationRunV3;
import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import com.mw.recommendation.engine.v3.dto.V3StatusResponseDTO;
import com.mw.recommendation.engine.v3.repository.ResultV3Repository;
import com.mw.recommendation.engine.v3.repository.RunV3Repository;
import com.mw.recommendation.engine.v3.repository.ScheduleV3Repository;
import com.mw.recommendation.engine.v3.service.RunQueryV3Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubmitV3ServiceTest {

  @Mock private RunV3Repository runRepository;
  @Mock private ResultV3Repository resultRepository;
  @Mock private ScheduleV3Repository scheduleRepository;
  @Mock private PipelineV3Service pipelineService;

  private SubmitV3Service service;

  @BeforeEach
  void setUp() {
    // Real query service (read side) wired with the same mocked repositories
    RunQueryV3Service queryService = new RunQueryV3Service(runRepository, resultRepository);
    service =
        new SubmitV3Service(
            runRepository, resultRepository, scheduleRepository, pipelineService, queryService);
  }

  private static RecommendationV3RequestDTO request() {
    return RecommendationV3RequestDTO.builder()
        .country("Malaysia")
        .startDate(LocalDate.of(2026, 8, 1))
        .endDate(LocalDate.of(2026, 8, 31))
        .build();
  }

  private static RecommendationRunV3 existingRun(RecommendationRunV3.RunStatus status) {
    return RecommendationRunV3.builder()
        .runId("run-existing")
        .campaignId("camp-1")
        .status(status)
        .completionPercentage(status == RecommendationRunV3.RunStatus.IN_PROGRESS ? 40 : 100)
        .generatedAt(LocalDateTime.now())
        .seed("seed-existing")
        .engineVersion(3)
        .warnings(new ArrayList<>())
        .build();
  }

  @Test
  void givenDedupHit_whenSubmit_thenExistingRunReturnedWithoutCreatingNewOne() {
    RecommendationRunV3 existing = existingRun(RecommendationRunV3.RunStatus.COMPLETED);
    when(runRepository.findByCampaignIdAndRequestHash(any(), any()))
        .thenReturn(Optional.of(existing));

    V3StatusResponseDTO response = service.submit("camp-1", request(), false);

    assertThat(response.getRunId()).isEqualTo("run-existing");
    verify(runRepository, never()).save(any());
    verify(runRepository, never()).delete(any());
    verify(pipelineService, never()).process(any());
  }

  @Test
  void givenForceRegenerateOnCompletedRun_whenSubmit_thenOldRunDeletedAndNewRunCreated() {
    RecommendationRunV3 existing = existingRun(RecommendationRunV3.RunStatus.COMPLETED);
    when(runRepository.findByCampaignIdAndRequestHash(any(), any()))
        .thenReturn(Optional.of(existing));

    V3StatusResponseDTO response = service.submit("camp-1", request(), true);

    verify(runRepository).delete(existing);
    verify(resultRepository).deleteByRunId("run-existing");
    verify(scheduleRepository).deleteByRunId("run-existing");
    verify(runRepository).save(any(RecommendationRunV3.class));
    verify(pipelineService).process(any(V3RunContext.class));
    assertThat(response.getRunId()).isNotEqualTo("run-existing");
  }

  @Test
  void givenForceRegenerateWhileInProgress_whenSubmit_thenIgnoredAndExistingRunReturned() {
    RecommendationRunV3 existing = existingRun(RecommendationRunV3.RunStatus.IN_PROGRESS);
    when(runRepository.findByCampaignIdAndRequestHash(any(), any()))
        .thenReturn(Optional.of(existing));

    V3StatusResponseDTO response = service.submit("camp-1", request(), true);

    assertThat(response.getRunId()).isEqualTo("run-existing");
    assertThat(response.getStatus()).isEqualTo(V3StatusResponseDTO.RunStatus.IN_PROGRESS);
    verify(runRepository, never()).delete(any());
    verify(resultRepository, never()).deleteByRunId(any());
    verify(scheduleRepository, never()).deleteByRunId(any());
    verify(runRepository, never()).save(any());
    verify(pipelineService, never()).process(any());
  }

  @Test
  void givenNewSubmission_whenSubmit_thenRunSavedWithEngineVersion3AndSeedAndPipelineKickedOff() {
    when(runRepository.findByCampaignIdAndRequestHash(any(), any())).thenReturn(Optional.empty());

    V3StatusResponseDTO response = service.submit("camp-1", request(), false);

    ArgumentCaptor<RecommendationRunV3> runCaptor =
        ArgumentCaptor.forClass(RecommendationRunV3.class);
    verify(runRepository).save(runCaptor.capture());
    RecommendationRunV3 saved = runCaptor.getValue();
    assertThat(saved.getEngineVersion()).isEqualTo(3);
    assertThat(saved.getSeed()).isNotBlank();
    assertThat(saved.getStatus()).isEqualTo(RecommendationRunV3.RunStatus.IN_PROGRESS);
    assertThat(saved.getCampaignId()).isEqualTo("camp-1");

    ArgumentCaptor<V3RunContext> ctxCaptor = ArgumentCaptor.forClass(V3RunContext.class);
    verify(pipelineService).process(ctxCaptor.capture());
    assertThat(ctxCaptor.getValue().getRunId()).isEqualTo(saved.getRunId());
    assertThat(ctxCaptor.getValue().getSeed()).isEqualTo(saved.getSeed());

    assertThat(response.getRunId()).isEqualTo(saved.getRunId());
    assertThat(response.getStatus()).isEqualTo(V3StatusResponseDTO.RunStatus.IN_PROGRESS);
  }
}
