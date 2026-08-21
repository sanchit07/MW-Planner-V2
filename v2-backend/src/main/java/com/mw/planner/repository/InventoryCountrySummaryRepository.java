package com.mw.planner.repository;

import com.mw.planner.domain.InventoryCountrySummary;
import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryCountrySummaryRepository
    extends MongoRepository<InventoryCountrySummary, String> {

  /**
   * Fetch summaries for the given country names. Countries with no summary document are simply
   * absent from the result.
   */
  List<InventoryCountrySummary> findByCountryIn(Collection<String> countries);
}
