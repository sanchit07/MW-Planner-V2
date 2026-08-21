package com.mw.planner.repository;

import com.mw.planner.domain.CampaignProposalStatus;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignProposalStatusRepository
    extends MongoRepository<CampaignProposalStatus, String> {
  List<CampaignProposalStatus> findByCampaignId(String campaignId);

  /** Bulk-load proposals for many campaigns at once (inbox batching). */
  List<CampaignProposalStatus> findByCampaignIdIn(List<String> campaignIds);

  CampaignProposalStatus findByCampaignIdAndMediaOwnerId(String campaignId, String mediaOwnerId);

  List<CampaignProposalStatus> findByMediaOwnerIdAndStatusIn(
      String mediaOwnerId, List<CampaignProposalStatus.Status> statuses);

  /**
   * Find only status field for all proposals by campaign ID. Optimized query to fetch only status
   * field instead of full objects.
   */
  @Query(value = "{ 'campaignId': ?0 }", fields = "{ 'status': 1, 'mediaOwnerId': 1 }")
  List<CampaignProposalStatus> findStatusesByCampaignId(String campaignId);
}
