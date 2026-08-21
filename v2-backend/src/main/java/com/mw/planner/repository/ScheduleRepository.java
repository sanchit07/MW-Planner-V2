package com.mw.planner.repository;

import com.mw.planner.domain.Schedule;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/** Repository for Schedule entities. Provides basic CRUD operations for schedules. */
@Repository
public interface ScheduleRepository extends MongoRepository<Schedule, String> {

  /** Delete all schedules by campaign ID */
  void deleteByIdIn(List<String> scheduleIds);
}
