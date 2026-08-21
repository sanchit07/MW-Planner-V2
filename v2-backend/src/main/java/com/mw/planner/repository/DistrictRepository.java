package com.mw.planner.repository;

import com.mw.planner.domain.District;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DistrictRepository extends MongoRepository<District, String> {

  /** Fields projection for list API (performance). */
  String LIST_FIELDS =
      "{ '_id': 1, 'name': 1, 'stateId': 1, 'type': 1, 'latitude': 1, 'longitude': 1, 'zoom': 1,"
          + " 'population': 1, 'iso': 1, 'locale': 1, 'createdAt': 1, 'updatedAt': 1 }";

  List<District> findByStateId(String stateId);

  Optional<District> findByName(String name);

  /** Paginated districts by state ids (projection for performance). */
  @Query(value = "{ 'stateId': { $in: ?0 } }", fields = LIST_FIELDS)
  Page<District> findByStateIdIn(List<String> stateIds, Pageable pageable);

  /**
   * Paginated districts by state ids and name filter (projection for performance). Name is a regex
   * pattern for "contains" (e.g. ".*Kabupaten.*").
   */
  @Query(
      value = "{ 'stateId': { $in: ?0 }, 'name': { $regex: ?1, $options: 'i' } }",
      fields = LIST_FIELDS)
  Page<District> findByStateIdInAndNameRegex(
      List<String> stateIds, String nameRegex, Pageable pageable);

  Page<District> findByNameRegex(String nameRegex, Pageable pageable);
}
