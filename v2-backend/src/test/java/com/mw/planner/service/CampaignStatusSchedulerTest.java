package com.mw.planner.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.Campaign;
import com.mw.planner.repository.CampaignRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CampaignStatusSchedulerTest {

  @Mock private CampaignRepository campaignRepository;

  @InjectMocks private CampaignStatusScheduler campaignStatusScheduler;

  @Test
  void updateCampaignStatuses_ShouldActivateApprovedCampaignsStartingToday() {
    // Given
    LocalDate today = LocalDate.now();
    when(campaignRepository.bulkUpdateStatus(
            Campaign.Status.APPROVED, Campaign.Status.ACTIVE, today, null))
        .thenReturn(3);

    // When
    campaignStatusScheduler.updateCampaignStatuses();

    // Then
    verify(campaignRepository)
        .bulkUpdateStatus(Campaign.Status.APPROVED, Campaign.Status.ACTIVE, today, null);
  }

  @Test
  void updateCampaignStatuses_ShouldCompleteApprovedCampaignsEndingYesterday() {
    // Given
    LocalDate yesterday = LocalDate.now().minusDays(1);
    when(campaignRepository.bulkUpdateStatus(
            Campaign.Status.APPROVED, Campaign.Status.ACTIVE, LocalDate.now(), null))
        .thenReturn(0);
    when(campaignRepository.bulkUpdateStatus(
            Campaign.Status.APPROVED, Campaign.Status.COMPLETED, null, yesterday))
        .thenReturn(2);

    // When
    campaignStatusScheduler.updateCampaignStatuses();

    // Then
    verify(campaignRepository)
        .bulkUpdateStatus(Campaign.Status.APPROVED, Campaign.Status.COMPLETED, null, yesterday);
  }

  @Test
  void updateCampaignStatuses_ShouldArchiveCompletedCampaignsEnding30DaysAgo() {
    // Given
    LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
    when(campaignRepository.bulkUpdateStatus(
            Campaign.Status.APPROVED, Campaign.Status.ACTIVE, LocalDate.now(), null))
        .thenReturn(0);
    when(campaignRepository.bulkUpdateStatus(
            Campaign.Status.APPROVED,
            Campaign.Status.COMPLETED,
            null,
            LocalDate.now().minusDays(1)))
        .thenReturn(0);
    when(campaignRepository.bulkUpdateStatus(
            Campaign.Status.COMPLETED, Campaign.Status.ARCHIVED, null, thirtyDaysAgo))
        .thenReturn(5);

    // When
    campaignStatusScheduler.updateCampaignStatuses();

    // Then
    verify(campaignRepository)
        .bulkUpdateStatus(Campaign.Status.COMPLETED, Campaign.Status.ARCHIVED, null, thirtyDaysAgo);
  }

  @Test
  void updateCampaignStatuses_ShouldHandleAllThreeOperations() {
    // Given
    LocalDate today = LocalDate.now();
    LocalDate yesterday = today.minusDays(1);
    LocalDate thirtyDaysAgo = today.minusDays(30);

    when(campaignRepository.bulkUpdateStatus(
            Campaign.Status.APPROVED, Campaign.Status.ACTIVE, today, null))
        .thenReturn(2);
    when(campaignRepository.bulkUpdateStatus(
            Campaign.Status.APPROVED, Campaign.Status.COMPLETED, null, yesterday))
        .thenReturn(3);
    when(campaignRepository.bulkUpdateStatus(
            Campaign.Status.COMPLETED, Campaign.Status.ARCHIVED, null, thirtyDaysAgo))
        .thenReturn(1);

    // When
    campaignStatusScheduler.updateCampaignStatuses();

    // Then
    verify(campaignRepository)
        .bulkUpdateStatus(Campaign.Status.APPROVED, Campaign.Status.ACTIVE, today, null);
    verify(campaignRepository)
        .bulkUpdateStatus(Campaign.Status.APPROVED, Campaign.Status.COMPLETED, null, yesterday);
    verify(campaignRepository)
        .bulkUpdateStatus(Campaign.Status.COMPLETED, Campaign.Status.ARCHIVED, null, thirtyDaysAgo);
  }

  @Test
  void updateCampaignStatuses_ShouldHandleZeroUpdates() {
    // Given
    LocalDate today = LocalDate.now();
    LocalDate yesterday = today.minusDays(1);
    LocalDate thirtyDaysAgo = today.minusDays(30);

    when(campaignRepository.bulkUpdateStatus(any(), any(), any(), any())).thenReturn(0);

    // When
    campaignStatusScheduler.updateCampaignStatuses();

    // Then
    verify(campaignRepository)
        .bulkUpdateStatus(Campaign.Status.APPROVED, Campaign.Status.ACTIVE, today, null);
    verify(campaignRepository)
        .bulkUpdateStatus(Campaign.Status.APPROVED, Campaign.Status.COMPLETED, null, yesterday);
    verify(campaignRepository)
        .bulkUpdateStatus(Campaign.Status.COMPLETED, Campaign.Status.ARCHIVED, null, thirtyDaysAgo);
  }

  @Test
  void updateCampaignStatuses_ShouldHandleExceptionsGracefully() {
    // Given
    LocalDate today = LocalDate.now();
    when(campaignRepository.bulkUpdateStatus(
            Campaign.Status.APPROVED, Campaign.Status.ACTIVE, today, null))
        .thenThrow(new RuntimeException("Database error"));

    // When
    campaignStatusScheduler.updateCampaignStatuses();

    // Then - Should not throw exception, should log error
    verify(campaignRepository)
        .bulkUpdateStatus(Campaign.Status.APPROVED, Campaign.Status.ACTIVE, today, null);
  }
}
