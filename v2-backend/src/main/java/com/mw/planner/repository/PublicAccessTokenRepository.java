package com.mw.planner.repository;

import com.mw.planner.domain.PublicAccessToken;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PublicAccessTokenRepository extends MongoRepository<PublicAccessToken, String> {

  /** Find PublicAccessToken by campaign ID */
  Optional<PublicAccessToken> findByCampaignId(String campaignId);
}
