package com.mw.planner.repository;

import com.mw.planner.domain.Country;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CountryRepository extends MongoRepository<Country, String> {
  boolean existsByCountryId(String countryId);

  boolean existsByIso(String iso);

  Optional<Country> findByCountryId(String countryId);

  boolean existsByName(String name);

  Optional<Country> findByName(String name);

  List<Country> findByIdIn(List<String> ids);

  List<Country> findByIsoIn(List<String> isoCodes);
}
