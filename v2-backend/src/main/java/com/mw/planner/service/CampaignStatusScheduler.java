package com.mw.planner.service;

import com.mw.planner.domain.Campaign;
import com.mw.planner.repository.CampaignRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled service for updating campaign statuses automatically. Runs daily at midnight to: -
 * Activate campaigns that start today - Complete campaigns that ended yesterday - Archive campaigns
 * that completed 30 days ago
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignStatusScheduler {

  private final CampaignRepository campaignRepository;
  private final CampaignActivityService campaignActivityService;

  /**
   * Runs daily at midnight (00:00:00) to update campaign statuses. Activates campaigns that start
   * today, completes campaigns that ended yesterday, and archives campaigns that completed 30 days
   * ago.
   */
  @Scheduled(cron = "${mw-planner.scheduler.campaign-status-update.cron}")
  public void updateCampaignStatuses() {
    log.info("Starting scheduled campaign status update at midnight");
    LocalDate today = LocalDate.now();
    LocalDate yesterday = today.minusDays(1);
    LocalDate thirtyDaysAgo = today.minusDays(30);

    try {
      activateCampaigns(today);
      completeCampaigns(yesterday);
      archiveCampaigns(thirtyDaysAgo);
      log.info("Completed scheduled campaign status update");
    } catch (Exception e) {
      log.error("Error during scheduled campaign status update", e);
    }
  }

  /**
   * Activates campaigns that are approved and start today.
   *
   * @param today Today's date
   */
  private void activateCampaigns(LocalDate today) {
    int count =
        campaignRepository.bulkUpdateStatus(
            Campaign.Status.APPROVED, Campaign.Status.ACTIVE, today, null);
    if (count > 0) {
      log.info("Activated {} campaigns with start date: {}", count, today);
      // Log activity for activated campaigns
      logCronStatusUpdate(Campaign.Status.APPROVED, Campaign.Status.ACTIVE, today, null);
    }
  }

  /**
   * Completes campaigns that are approved and ended yesterday.
   *
   * @param yesterday Yesterday's date
   */
  private void completeCampaigns(LocalDate yesterday) {
    int count =
        campaignRepository.bulkUpdateStatus(
            Campaign.Status.APPROVED, Campaign.Status.COMPLETED, null, yesterday);
    if (count > 0) {
      log.info("Completed {} campaigns with end date: {}", count, yesterday);
      // Log activity for completed campaigns
      logCronStatusUpdate(Campaign.Status.APPROVED, Campaign.Status.COMPLETED, null, yesterday);
    }
  }

  /**
   * Archives campaigns that are completed and ended 30 days ago.
   *
   * @param thirtyDaysAgo Date 30 days before today
   */
  private void archiveCampaigns(LocalDate thirtyDaysAgo) {
    int count =
        campaignRepository.bulkUpdateStatus(
            Campaign.Status.COMPLETED, Campaign.Status.ARCHIVED, null, thirtyDaysAgo);
    if (count > 0) {
      log.info("Archived {} campaigns with end date: {}", count, thirtyDaysAgo);
      // Log activity for archived campaigns
      logCronStatusUpdate(Campaign.Status.COMPLETED, Campaign.Status.ARCHIVED, null, thirtyDaysAgo);
    }
  }

  /**
   * Log cron job status update activity for campaigns
   *
   * @param fromStatus Previous status
   * @param toStatus New status
   * @param startDate Start date filter (if applicable)
   * @param endDate End date filter (if applicable)
   */
  private void logCronStatusUpdate(
      Campaign.Status fromStatus,
      Campaign.Status toStatus,
      LocalDate startDate,
      LocalDate endDate) {
    try {
      // Query campaigns that match the criteria (these should be the ones just updated)
      List<Campaign> updatedCampaigns =
          campaignRepository.findAll().stream()
              .filter(
                  c ->
                      c.getStatus() == toStatus
                          && (startDate == null || c.getStartDate().equals(startDate))
                          && (endDate == null || c.getEndDate().equals(endDate)))
              .toList();

      // Log activity for each updated campaign
      for (Campaign campaign : updatedCampaigns) {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("Status", fromStatus.name() + " → " + toStatus.name() + " (Automated)");
        campaignActivityService.logCronActivity(
            campaign.getId(), CampaignActivityService.OperationType.UPDATED, changes);
      }
    } catch (Exception e) {
      log.warn("Failed to log cron status update activity: {}", e.getMessage());
    }
  }
}
