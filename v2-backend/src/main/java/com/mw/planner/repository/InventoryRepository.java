package com.mw.planner.repository;

import com.mw.planner.domain.Inventory;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/** Repository for Inventory entities. Provides basic CRUD operations for inventory data. */
@Repository
public interface InventoryRepository extends MongoRepository<Inventory, String> {

  /** Find inventory by envelope inventoryId from RabbitMQ message */
  Optional<Inventory> findByInventoryId(String inventoryId);

  /** Find inventory by external ID from external system */
  Optional<Inventory> findFirstByExternalId(String externalId);

  /** Find all inventories by external IDs in a single bulk query */
  @Query("{ 'externalId': { $in: ?0 } }")
  List<Inventory> findByExternalIdIn(List<String> externalIds);

  /** Find inventory by reference ID */
  Optional<Inventory> findFirstByReferenceId(String referenceId);

  /** Find all inventories by reference IDs in a single bulk query */
  @Query("{ 'referenceId': { $in: ?0 } }")
  List<Inventory> findByReferenceIdIn(List<String> referenceIds);

  /** Find all inventories by reference IDs with pagination support */
  @Query("{ 'referenceId': { $in: ?0 } }")
  Page<Inventory> findByReferenceIdIn(List<String> referenceIds, Pageable pageable);

  /** Count inventories by country */
  Long countByLocationCountry(String country);

  /** Find inventory IDs by country name - optimized query to fetch only IDs */
  @Query(value = "{ 'location.country': ?0 }", fields = "{ '_id': 1, 'referenceId': 1}")
  List<Inventory> findInventoryIdsByLocationCountry(String country);

  /**
   * Find selected inventories by IDs with optional name filtering. Simple query with pagination
   * support.
   */
  @Query(
      value =
          "{ '_id': { $in: ?0 }, $or: [ { 'name': { $regex: ?1, $options: 'i' } }, { 'displayName': { $regex: ?1, $options: 'i' } }, { 'referenceId': { $regex: ?1, $options: 'i' } } ] }")
  Page<Inventory> findByIdInAndNameContaining(List<String> ids, String name, Pageable pageable);

  /**
   * Find selected inventories by IDs with optional inventoryType filtering. Simple query with
   * pagination support.
   */
  @Query("{ '_id': { $in: ?0 }, 'type': ?1 }")
  Page<Inventory> findByIdInAndType(List<String> ids, String inventoryType, Pageable pageable);

  /**
   * Find selected inventories by IDs with optional inventoryType filtering. Returns only IDs.
   * Optimized query to fetch only the _id field. Uses case-insensitive exact matching for type.
   */
  @Query(
      value = "{ '_id': { $in: ?0 }, 'type': { $regex: ?1, $options: 'i' } }",
      fields = "{ '_id': 1 }")
  List<Inventory> findIdByIdInAndType(List<String> ids, String inventoryType);

  /**
   * Find selected inventories by IDs with optional name and inventoryType filtering. Simple query
   * with pagination support.
   */
  @Query(
      value =
          "{ '_id': { $in: ?0 }, 'type': ?2, $or: [ { 'name': { $regex: ?1, $options: 'i' } }, { 'displayName': { $regex: ?1, $options: 'i' } }, { 'referenceId': { $regex: ?1, $options: 'i' } } ] }")
  Page<Inventory> findByIdInAndNameContainingAndType(
      List<String> ids, String name, String inventoryType, Pageable pageable);

  /** Find selected inventories by IDs without name filtering. Simple query with pagination. */
  Page<Inventory> findByIdIn(List<String> ids, Pageable pageable);

  @Query(fields = "{ 'mediaOwnerId': 1 }")
  Document findMediaOwnerIdById(String inventoryId);
}
