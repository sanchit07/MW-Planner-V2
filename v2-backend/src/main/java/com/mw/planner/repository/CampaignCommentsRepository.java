package com.mw.planner.repository;

import com.mw.planner.domain.CampaignComments;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignCommentsRepository extends MongoRepository<CampaignComments, String> {
  List<CampaignComments> findByCampaignId(String campaignId);
}
