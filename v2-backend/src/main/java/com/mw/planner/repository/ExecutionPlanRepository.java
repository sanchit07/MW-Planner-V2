package com.mw.planner.repository;

import com.mw.planner.domain.ExecutionPlan;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExecutionPlanRepository extends MongoRepository<ExecutionPlan, String> {
  Optional<ExecutionPlan> findByCampaignId(String campaignId);

  void deleteByCampaignId(String campaignId);

  /** Deletes only an unlocked plan — a locked (pushed) plan is never removed, even in races. */
  long deleteByCampaignIdAndLockedIsFalse(String campaignId);
}
