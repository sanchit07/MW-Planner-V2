package com.mw.planner.repository;

import com.mw.planner.domain.CustomFee;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomFeeRepository extends MongoRepository<CustomFee, String> {

  /** Find all custom fees by company ID and campaign ID (campaignId null = company-level fees). */
  List<CustomFee> findByCompanyIdAndCampaignId(String companyId, String campaignId);

  /**
   * Find all active company-level custom fees (campaignId null) for the given company IDs. Used for
   * batch loading custom fees for a campaign's creator and media owners.
   */
  List<CustomFee> findByCompanyIdInAndCampaignIdIsNullAndIsActiveTrue(List<String> companyIds);

  /**
   * Find all active campaign-level custom fees for the given campaign and company IDs. Used for
   * batch loading campaign-scoped custom fees for a campaign's creator and media owners.
   */
  List<CustomFee> findByCampaignIdAndCompanyIdInAndIsActiveTrue(
      String campaignId, List<String> companyIds);

  /**
   * Bulk load active campaign-level fees for multiple campaigns and companies.
   *
   * <p>Used by dashboard cost ranking to avoid N queries (one per campaign).
   */
  List<CustomFee> findByCampaignIdInAndCompanyIdInAndIsActiveTrue(
      List<String> campaignIds, List<String> companyIds);

  /** Check if custom fee exists by name, company ID, and campaign ID. */
  boolean existsByNameAndCompanyIdAndCampaignId(String name, String companyId, String campaignId);
}
