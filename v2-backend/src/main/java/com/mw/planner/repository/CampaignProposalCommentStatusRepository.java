package com.mw.planner.repository;

import com.mw.planner.domain.CampaignProposalCommentStatus;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignProposalCommentStatusRepository
    extends MongoRepository<CampaignProposalCommentStatus, String> {
  List<CampaignProposalCommentStatus> findByProposalId(String proposalId);
}
