package com.mw.planner.repository;

import com.mw.planner.domain.CampaignApprovedWorkflowStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignApprovedWorkflowStatusRepository
    extends MongoRepository<CampaignApprovedWorkflowStatus, String> {
  Optional<CampaignApprovedWorkflowStatus> findByCampaignIdAndApprovalAuthority(
      String campaignId, CampaignApprovedWorkflowStatus.ApprovalAuthority approvalAuthority);

  List<CampaignApprovedWorkflowStatus> findByCampaignId(String campaignId);
}
