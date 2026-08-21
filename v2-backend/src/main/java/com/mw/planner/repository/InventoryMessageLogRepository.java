package com.mw.planner.repository;

import com.mw.planner.domain.InventoryMessageLog;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryMessageLogRepository
    extends MongoRepository<InventoryMessageLog, String> {

  /** Most-recent-first audit trail for a given inventory id. */
  List<InventoryMessageLog> findByInventoryIdOrderByReceivedAtDesc(String inventoryId);
}
