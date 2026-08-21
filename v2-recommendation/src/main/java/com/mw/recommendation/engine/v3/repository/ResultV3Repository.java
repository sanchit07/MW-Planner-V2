package com.mw.recommendation.engine.v3.repository;

import com.mw.recommendation.engine.v3.domain.RecommendationResultV3;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ResultV3Repository extends MongoRepository<RecommendationResultV3, String> {

  Page<RecommendationResultV3> findByRunId(String runId, Pageable pageable);

  List<RecommendationResultV3> findByRunId(String runId);

  void deleteByRunId(String runId);
}
