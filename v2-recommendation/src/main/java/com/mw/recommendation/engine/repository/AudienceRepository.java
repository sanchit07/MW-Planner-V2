package com.mw.recommendation.engine.repository;

import com.mw.recommendation.engine.domain.AudienceData;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/** Repository for AudienceData entities. Provides basic CRUD operations for audience data. */
@Repository
public interface AudienceRepository extends MongoRepository<AudienceData, String> {

  /** Find audience data by inventory ID */
  Optional<AudienceData> findByInventoryId(String inventoryId);

  /** Find audience data by reference ID */
  Optional<AudienceData> findByReferenceId(String referenceId);

  /** Find all audience data by inventory IDs in a single bulk query */
  @Query("{ 'inventoryId': { $in: ?0 } }")
  List<AudienceData> findByInventoryIdIn(List<String> inventoryIds);

  /** Find all audience data by reference IDs in a single bulk query */
  @Query("{ 'referenceId': { $in: ?0 } }")
  List<AudienceData> findByReferenceIdIn(List<String> referenceIds);

  /**
   * Find audience data by inventory IDs OR reference IDs in a single query. Phase 1.6 Optimization:
   * Reduces network round trips and eliminates duplicate data transfer.
   */
  @Query("{ $or: [ { 'inventoryId': { $in: ?0 } }, { 'referenceId': { $in: ?1 } } ] }")
  List<AudienceData> findByInventoryIdInOrReferenceIdIn(
      List<String> inventoryIds, List<String> referenceIds);
}
