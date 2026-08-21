package com.mw.planner.repository;

import com.mw.planner.domain.CampaignGeoImportFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignGeoImportFileRepository
    extends MongoRepository<CampaignGeoImportFile, String> {

  /**
   * Find geo import files by company ID and country name with pagination and sorting support.
   *
   * @param companyId Company ID to filter by
   * @param countryName Country name to filter by
   * @param pageable Pagination and sorting parameters
   * @return Page of CampaignGeoImportFile matching the criteria
   */
  Page<CampaignGeoImportFile> findByCompanyIdAndCountryName(
      String companyId, String countryName, Pageable pageable);
}
