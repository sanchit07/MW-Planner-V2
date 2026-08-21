package com.mw.planner.repository;

import com.mw.planner.domain.Campaign;
import com.mw.planner.dto.CampaignFilterDTO;
import com.mw.planner.dto.CampaignForecastDTO;
import com.mw.planner.dto.CampaignStatistics;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CampaignRepositoryCustom {
  Page<Campaign> findCampaignsWithFilters(CampaignFilterDTO filter, Pageable pageable);

  /**
   * Finds campaigns that overlap the requested range and are accessible by the given company
   * (companyId matches OR companyAccess contains).
   *
   * <p>Overlap condition: campaign.startDate <= endDate AND campaign.endDate >= startDate.
   *
   * <p>NOTE: Implementations should prefer field projections to avoid heavy document loading.
   */
  List<Campaign> findCampaignsOverlappingRange(
      String companyId, LocalDate startDate, LocalDate endDate, String dataMode);

  /**
   * Find campaigns by company created within the given date range (campaign.createdAt in
   * [startDate, endDate], endDate inclusive of the whole day), optionally filtered to a set of
   * statuses.
   *
   * @param companyId Company ID (companyId or companyAccess)
   * @param startDate Optional creation-window start: campaign.createdAt >= startDate (start of day)
   * @param endDate Optional creation-window end: campaign.createdAt <= endDate (end of day)
   * @param statuses Optional status filter (null/empty = no filter, matching current behavior)
   * @return List of campaigns created within the range
   */
  List<Campaign> findCampaignsByCompanyIdOverlappingDateRange(
      String companyId,
      LocalDate startDate,
      LocalDate endDate,
      List<Campaign.Status> statuses,
      String dataMode);

  /**
   * Get campaign statistics by company with optional creation date range filter.
   *
   * @param companyId Company ID to filter by
   * @param startDate Optional creation-window start: campaign.createdAt >= startDate (start of day)
   * @param endDate Optional creation-window end: campaign.createdAt <= endDate (end of day)
   * @return CampaignStatistics containing counts by status
   */
  CampaignStatistics getCampaignStatisticsByCompanyId(
      String companyId, LocalDate startDate, LocalDate endDate, String dataMode);

  /**
   * Bulk update campaign status based on current status and date criteria. Uses efficient MongoDB
   * bulk update operations.
   *
   * @param currentStatus Current status to match
   * @param newStatus New status to set
   * @param startDate If provided, matches campaigns with this start date
   * @param endDate If provided, matches campaigns with this end date
   * @return Number of campaigns updated
   */
  int bulkUpdateStatus(
      Campaign.Status currentStatus,
      Campaign.Status newStatus,
      LocalDate startDate,
      LocalDate endDate);

  /**
   * Keyset-paginated fetch of campaigns whose {@code performance} snapshot is missing, restricted
   * to the given statuses. Results are sorted by {@code _id} ascending; pass the last seen id to
   * fetch the next page ({@code null} for the first page).
   *
   * @param statuses campaign statuses to include
   * @param lastId exclusive lower bound on {@code _id}, or {@code null} for the first page
   * @param limit maximum number of campaigns to return
   * @return campaigns with {@code performance = null} matching the statuses
   */
  List<Campaign> findByPerformanceNullAndStatusIn(
      List<Campaign.Status> statuses, String lastId, int limit);

  /**
   * Conditionally persists a forecast with a single-field {@code $set}: the update only applies
   * when {@code performance} is still {@code null}, so an existing snapshot (e.g. written by
   * autosave in the meantime) is never overwritten. Deliberately bypasses auditing — {@code
   * updatedAt}/{@code lastModifiedBy} are left untouched.
   *
   * @param campaignId campaign id
   * @param forecast forecast to persist
   * @return true when the document was updated, false when performance was already populated
   */
  boolean setPerformanceIfNull(String campaignId, CampaignForecastDTO forecast);

  /**
   * Keyset-paginated fetch of campaigns whose {@code planNumber} is missing (legacy campaigns
   * created before the numeric plan ID existed). Results are sorted by {@code _id} ascending
   * (roughly chronological, and consistent with the pagination cursor itself) so backfilled numbers
   * stay chronologically meaningful within a day; pass the last seen id to fetch the next page
   * ({@code null} for the first page).
   *
   * @param lastId exclusive lower bound cursor (the last campaign id seen), or {@code null} for the
   *     first page
   * @param limit maximum number of campaigns to return
   * @return campaigns with {@code planNumber = null}, oldest first
   */
  List<Campaign> findByPlanNumberIsNull(String lastId, int limit);

  /**
   * Conditionally persists a plan number with a single-field {@code $set}: the update only applies
   * when {@code planNumber} is still {@code null}, so a number already assigned (e.g. by normal
   * creation racing with the backfill) is never overwritten.
   *
   * @param campaignId campaign id
   * @param planNumber plan number to persist
   * @return true when the document was updated, false when planNumber was already populated
   */
  boolean setPlanNumberIfNull(String campaignId, String planNumber);
}
