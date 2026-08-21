package com.mw.planner.repository;

import com.mw.planner.domain.Agency;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Custom repository interface for Agency-specific queries This separates complex aggregation logic
 * from the main repository
 */
public interface AgencyRepositoryCustom {

  /**
   * Find agencies by name or country name using optimized aggregation
   *
   * @param searchTerm the search term to match against agency name or country name
   * @param pageable pagination information
   * @return page of agencies matching the search criteria
   */
  Page<Agency> findByNameOrCountryNameContainingIgnoreCase(String searchTerm, Pageable pageable);
}
