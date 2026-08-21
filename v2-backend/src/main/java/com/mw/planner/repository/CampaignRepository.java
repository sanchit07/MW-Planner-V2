package com.mw.planner.repository;

import com.mw.planner.domain.Campaign;
import com.mw.planner.repository.projection.CampaignCountryIdProjection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignRepository
    extends MongoRepository<Campaign, String>, CampaignRepositoryCustom {

  /** Find only countryId by campaign id (projection for performance). */
  @Query(value = "{ '_id': ?0 }", fields = "{ 'countryId': 1 }")
  Optional<CampaignCountryIdProjection> findCountryIdById(String id);

  /** Find campaigns by name (case-insensitive) */
  Optional<Campaign> findByNameIgnoreCase(String name);

  /** Find campaigns currently in any of the given statuses (approval inbox, admin view). */
  List<Campaign> findByStatusIn(List<Campaign.Status> statuses);

  /** Approval inbox scoped server-side: campaigns the company created or has shared access to. */
  @Query("{ 'status': { $in: ?0 }, '$or': [ { 'companyId': ?1 }, { 'companyAccess': ?1 } ] }")
  List<Campaign> findByStatusInAndCompanyInvolved(List<Campaign.Status> statuses, String companyId);

  /** Count campaigns by status */
  long countByStatus(Campaign.Status status);

  /** Count campaigns by company ID */
  long countByCompanyId(String companyId);

  /** Count campaigns by user ID */
  long countByUserId(String userId);
}
