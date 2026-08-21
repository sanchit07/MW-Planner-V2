package com.mw.planner.repository;

import com.mw.planner.domain.CampaignActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignActivityRepository extends MongoRepository<CampaignActivity, String> {

  /**
   * Find paginated campaign activities by campaign ID with sorting
   *
   * @param campaignId Campaign ID
   * @param pageable Pagination and sort criteria
   * @return Page of campaign activities
   */
  Page<CampaignActivity> findByCampaignId(String campaignId, Pageable pageable);
}
