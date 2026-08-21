package com.mw.planner.repository;

import com.mw.planner.domain.Agency;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgencyRepository extends MongoRepository<Agency, String>, AgencyRepositoryCustom {

  /** Find agency by name */
  Optional<Agency> findByName(String name);

  /** Check if agency exists by name */
  boolean existsByName(String name);

  Page<Agency> findByNameRegex(Pattern namePattern, Pageable pageable);
}
