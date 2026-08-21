package com.mw.planner.repository;

import com.mw.planner.domain.CampaignInventorySchedules;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for CampaignInventorySchedules entities. Provides basic CRUD operations for campaign
 * inventory schedules.
 */
@Repository
public interface CampaignInventorySchedulesRepository
    extends MongoRepository<CampaignInventorySchedules, String>,
        CampaignInventorySchedulesRepositoryCustom {

  /** Find schedules by campaign ID and list of inventory IDs */
  List<CampaignInventorySchedules> findByCampaignIdAndInventoryIdIn(
      String campaignId, List<String> inventoryIds);

  /** Find schedules by campaign ID and media owner ID */
  List<CampaignInventorySchedules> findByCampaignIdAndMediaOwnerId(
      String campaignId, String mediaOwnerId);

  /** Find schedules by campaign ID and a list of media owner IDs */
  List<CampaignInventorySchedules> findByCampaignIdAndMediaOwnerIdIn(
      String campaignId, List<String> mediaOwnerIds);

  /** Find schedule by campaign ID and inventory ID */
  Optional<CampaignInventorySchedules> findByCampaignIdAndInventoryId(
      String campaignId, String inventoryId);

  /** Find all schedules by campaign ID */
  List<CampaignInventorySchedules> findByCampaignId(String campaignId);

  /** Find all bookings (any campaign) on one inventory — availability/occupancy computation. */
  List<CampaignInventorySchedules> findByInventoryId(String inventoryId);

  /** Find all schedules for the given campaign IDs (bulk). */
  List<CampaignInventorySchedules> findByCampaignIdIn(List<String> campaignIds);

  /** Delete schedule by campaign ID and inventory ID */
  long deleteByCampaignIdAndInventoryId(String campaignId, String inventoryId);

  /** Delete all schedules by campaign ID */
  void deleteByCampaignId(String campaignId);

  /** Delete all schedules by campaign ID and list of inventory IDs */
  long deleteByCampaignIdAndInventoryIdIn(String campaignId, List<String> inventoryIds);

  /** Count schedules by campaign ID and media owner ID */
  long countByCampaignIdAndMediaOwnerId(String campaignId, String mediaOwnerId);

  /** Count configurations by campaign */
  long countByCampaignId(String campaignId);

  /** Check if schedule exists by campaign ID and inventory ID */
  boolean existsByCampaignIdAndInventoryId(String campaignId, String inventoryId);

  /** Find schedules by campaign ID where approvedBy is null */
  List<CampaignInventorySchedules> findByCampaignIdAndApprovedByIsNull(String campaignId);
}
