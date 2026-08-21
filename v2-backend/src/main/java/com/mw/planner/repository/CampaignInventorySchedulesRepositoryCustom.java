package com.mw.planner.repository;

import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.dto.CampaignSchedulePriceFilterDTO;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Custom repository interface for advanced CampaignInventorySchedules queries. */
public interface CampaignInventorySchedulesRepositoryCustom {

  /**
   * Find campaign inventory schedules with filters applied at database level using aggregation
   * pipeline. Filters by cities, inventoryTypes, mediaOwners, and price range.
   *
   * @param campaignId Campaign ID
   * @param filter Filter criteria
   * @param pageable Pagination parameters
   * @param mediaOwnerId Optional media owner ID to filter by. If provided, only returns schedules
   *     where mediaOwnerId matches.
   * @return Page of filtered CampaignInventorySchedules
   */
  Page<CampaignInventorySchedules> findWithPriceFilters(
      String campaignId,
      CampaignSchedulePriceFilterDTO filter,
      Pageable pageable,
      String mediaOwnerId);

  /**
   * Check if any CampaignInventorySchedules for a campaign has history size > 1 and approvedBy is
   * null. This indicates that price approval is required.
   *
   * @param campaignId Campaign ID to check
   * @return true if any schedule requires price approval, false otherwise
   */
  boolean existsByCampaignIdAndHistorySizeGreaterThanOneAndApprovedByIsNull(String campaignId);

  /**
   * Find campaign inventory schedules where not all scheduleIds are in approvedScheduleIds. This
   * supports partial approval - finds schedules that still have unapproved scheduleIds. Optionally
   * filtered by scheduleIds.
   *
   * @param campaignId Campaign ID
   * @param scheduleIds Optional list of schedule IDs to filter by. If null or empty, returns all
   *     schedules with unapproved scheduleIds.
   * @return List of CampaignInventorySchedules
   */
  List<CampaignInventorySchedules> findByCampaignIdWithUnapprovedSchedules(
      String campaignId, List<String> scheduleIds);

  /**
   * Find campaign inventory schedules where not all scheduleIds are in approvedScheduleIds,
   * filtered by mediaOwnerId. This supports partial approval - finds schedules that still have
   * unapproved scheduleIds. Optionally filtered by scheduleIds.
   *
   * @param campaignId Campaign ID
   * @param mediaOwnerId Media Owner ID
   * @param scheduleIds Optional list of schedule IDs to filter by. If null or empty, returns all
   *     schedules with unapproved scheduleIds for the media owner.
   * @return List of CampaignInventorySchedules
   */
  List<CampaignInventorySchedules> findByCampaignIdAndMediaOwnerIdWithUnapprovedSchedules(
      String campaignId, String mediaOwnerId, List<String> scheduleIds);

  /**
   * Count remaining CampaignInventorySchedules for a campaign, grouped by mediaOwnerId, for a given
   * set of candidate mediaOwnerIds. Used to check in a single query which of several mediaOwnerIds
   * have no remaining schedules (instead of one count query per mediaOwnerId).
   *
   * @param campaignId Campaign ID
   * @param mediaOwnerIds Candidate media owner IDs to count
   * @return Map of mediaOwnerId to remaining schedule count. A mediaOwnerId with zero remaining
   *     schedules is absent from the map (not present with a value of 0).
   */
  Map<String, Long> countByCampaignIdGroupedByMediaOwnerIdIn(
      String campaignId, Collection<String> mediaOwnerIds);
}
