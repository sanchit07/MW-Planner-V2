package com.mw.planner.repository;

import com.mw.planner.domain.CampaignApprovalHistory;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignApprovalHistoryRepository
    extends MongoRepository<CampaignApprovalHistory, String> {
  List<CampaignApprovalHistory> findByCampaignApprovedWorkflowStatusId(
      String campaignApprovedWorkflowStatusId);
}
