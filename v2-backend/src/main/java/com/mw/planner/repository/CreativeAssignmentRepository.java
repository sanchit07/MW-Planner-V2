package com.mw.planner.repository;

import com.mw.planner.domain.CreativeAssignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CreativeAssignmentRepository extends MongoRepository<CreativeAssignment, String> {
  Optional<CreativeAssignment> findByLineItemId(String lineItemId);

  List<CreativeAssignment> findByCampaignId(String campaignId);

  List<CreativeAssignment> findByCampaignIdIn(List<String> campaignIds);
}
