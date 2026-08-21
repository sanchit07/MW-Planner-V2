package com.mw.planner.repository;

import com.mw.planner.domain.SelectInventoryImports;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SelectInventoryImportsRepository
    extends MongoRepository<SelectInventoryImports, String> {

  /**
   * Find inventory imports by company ID and country name with pagination and sorting support.
   *
   * @param companyId Company ID to filter by
   * @param countryName Country name to filter by
   * @param pageable Pagination and sorting parameters
   * @return Page of SelectInventoryImports matching the criteria
   */
  Page<SelectInventoryImports> findByCompanyIdAndCountryName(
      String companyId, String countryName, Pageable pageable);
}
