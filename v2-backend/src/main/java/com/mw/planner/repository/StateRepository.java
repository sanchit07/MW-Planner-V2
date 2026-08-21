package com.mw.planner.repository;

import com.mw.planner.domain.State;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StateRepository extends MongoRepository<State, String> {

  /** Find state ids by country (id-only projection for performance). */
  @Query(value = "{ 'countryId': ?0 }", fields = "{ '_id': 1 }")
  List<State> findByCountryId(String countryId);

  Optional<State> findByName(String name);
}
