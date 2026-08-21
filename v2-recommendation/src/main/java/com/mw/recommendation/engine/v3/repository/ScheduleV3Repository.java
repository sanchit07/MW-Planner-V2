package com.mw.recommendation.engine.v3.repository;

import com.mw.recommendation.engine.v3.domain.RunScheduleV3;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduleV3Repository extends MongoRepository<RunScheduleV3, String> {

  List<RunScheduleV3> findByRunId(String runId);

  void deleteByRunId(String runId);
}
