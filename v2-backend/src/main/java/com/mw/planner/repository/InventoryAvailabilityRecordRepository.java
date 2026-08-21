package com.mw.planner.repository;

import com.mw.planner.domain.InventoryAvailabilityRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/** Repository for IMS-synced inventory availability documents. */
@Repository
public interface InventoryAvailabilityRecordRepository
    extends MongoRepository<InventoryAvailabilityRecord, String> {

  Optional<InventoryAvailabilityRecord> findByExternalId(String externalId);

  @Query("{ 'externalId': { $in: ?0 } }")
  List<InventoryAvailabilityRecord> findByExternalIdIn(List<String> externalIds);
}
