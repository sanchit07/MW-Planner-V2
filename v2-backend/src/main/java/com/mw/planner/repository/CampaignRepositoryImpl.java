package com.mw.planner.repository;

import com.mw.planner.domain.Campaign;
import com.mw.planner.dto.CampaignFilterDTO;
import com.mw.planner.dto.CampaignForecastDTO;
import com.mw.planner.dto.CampaignStatistics;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CampaignRepositoryImpl implements CampaignRepositoryCustom {

  private final MongoTemplate mongoTemplate;

  @Override
  public List<Campaign> findCampaignsOverlappingRange(
      String companyId, LocalDate startDate, LocalDate endDate, String dataMode) {
    Criteria criteria = buildCompanyAccessCriteria(companyId);
    Criteria dataModeCriteria = buildDataModeCriteria(dataMode);
    if (dataModeCriteria != null) {
      criteria = new Criteria().andOperator(criteria, dataModeCriteria);
    }
    if (startDate != null && endDate != null) {
      criteria =
          new Criteria()
              .andOperator(
                  criteria,
                  Criteria.where("startDate").lte(endDate),
                  Criteria.where("endDate").gte(startDate));
    } else if (startDate != null) {
      criteria = new Criteria().andOperator(criteria, Criteria.where("endDate").gte(startDate));
    } else if (endDate != null) {
      criteria = new Criteria().andOperator(criteria, Criteria.where("startDate").lte(endDate));
    }

    Query query = new Query(criteria);

    // Field projection to avoid heavy object load.
    query
        .fields()
        .include("_id")
        .include("name")
        .include("startDate")
        .include("endDate")
        .include("companyId")
        .include("companyAccess")
        .include("clientType")
        .include("agency")
        .include("brand")
        .include("userId");

    return mongoTemplate.find(query, Campaign.class);
  }

  @Override
  public Page<Campaign> findCampaignsWithFilters(CampaignFilterDTO filter, Pageable pageable) {
    List<Criteria> criteriaList = new ArrayList<>();

    // Filter by name contains (case-insensitive)
    if (filter.getNameContains() != null && !filter.getNameContains().trim().isEmpty()) {
      criteriaList.add(Criteria.where("name").regex(Pattern.quote(filter.getNameContains()), "i"));
    }

    // Filter by statuses
    if (filter.getStatuses() != null && !filter.getStatuses().isEmpty()) {
      criteriaList.add(Criteria.where("status").in(filter.getStatuses()));
    }

    // Filter by goal types
    if (filter.getGoalTypes() != null && !filter.getGoalTypes().isEmpty()) {
      criteriaList.add(Criteria.where("goals.goalType").in(filter.getGoalTypes()));
    }

    // Filter by user IDs
    if (filter.getUserIds() != null && !filter.getUserIds().isEmpty()) {
      criteriaList.add(Criteria.where("userId").in(filter.getUserIds()));
    }

    // Filter by date range
    if (filter.getStartDateFrom() != null) {
      criteriaList.add(Criteria.where("startDate").gte(filter.getStartDateFrom()));
    }
    if (filter.getStartDateTo() != null) {
      criteriaList.add(Criteria.where("startDate").lte(filter.getStartDateTo()));
    }

    // Filter by creation date range (createdAt is a LocalDateTime; widen the bounds to cover the
    // whole calendar day so the endDate day is inclusive).
    if (filter.getCreatedAtFrom() != null) {
      criteriaList.add(Criteria.where("createdAt").gte(filter.getCreatedAtFrom().atStartOfDay()));
    }
    if (filter.getCreatedAtTo() != null) {
      criteriaList.add(
          Criteria.where("createdAt").lte(filter.getCreatedAtTo().atTime(LocalTime.MAX)));
    }

    // Filter by company ID (companyId OR companyAccess contains the company ID)
    if (filter.getCompanyId() != null && !filter.getCompanyId().trim().isEmpty()) {
      criteriaList.add(buildCompanyAccessCriteria(filter.getCompanyId()));
    }

    // Filter by data-mode partition (Test Mode). Records with no dataMode are legacy live rows.
    if (filter.getDataMode() != null && !filter.getDataMode().isBlank()) {
      if ("demo".equals(filter.getDataMode())) {
        criteriaList.add(Criteria.where("dataMode").is("demo"));
      } else {
        criteriaList.add(
            new Criteria()
                .orOperator(
                    Criteria.where("dataMode").exists(false),
                    Criteria.where("dataMode").is(null),
                    Criteria.where("dataMode").is("live")));
      }
    }

    // Build query
    Query query = new Query();
    if (!criteriaList.isEmpty()) {
      query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
    }

    // If unpaged, avoid an extra count query (performance).
    if (pageable == null || pageable.isUnpaged()) {
      List<Campaign> campaigns = mongoTemplate.find(query, Campaign.class);
      return PageableExecutionUtils.getPage(
          campaigns, Pageable.unpaged(), () -> (long) campaigns.size());
    }

    // Get total count for pagination
    long total = mongoTemplate.count(query, Campaign.class);

    // Apply pagination and sorting
    query.with(pageable);

    // Execute query
    List<Campaign> campaigns = mongoTemplate.find(query, Campaign.class);

    return PageableExecutionUtils.getPage(campaigns, pageable, () -> total);
  }

  @Override
  public List<Campaign> findCampaignsByCompanyIdOverlappingDateRange(
      String companyId,
      LocalDate startDate,
      LocalDate endDate,
      List<Campaign.Status> statuses,
      String dataMode) {
    Criteria criteria = buildCompanyAccessCriteria(companyId);
    // Match campaigns created within [startDate, endDate]. createdAt is a LocalDateTime, so widen
    // the bounds to cover the whole calendar day (endDate inclusive).
    if (startDate != null && endDate != null) {
      criteria =
          criteria
              .and("createdAt")
              .gte(startDate.atStartOfDay())
              .lte(endDate.atTime(LocalTime.MAX));
    } else if (startDate != null) {
      criteria = criteria.and("createdAt").gte(startDate.atStartOfDay());
    } else if (endDate != null) {
      criteria = criteria.and("createdAt").lte(endDate.atTime(LocalTime.MAX));
    }
    if (statuses != null && !statuses.isEmpty()) {
      criteria = criteria.and("status").in(statuses);
    }
    Criteria dataModeCriteria = buildDataModeCriteria(dataMode);
    if (dataModeCriteria != null) {
      criteria = new Criteria().andOperator(criteria, dataModeCriteria);
    }
    Query query = new Query(criteria);
    return mongoTemplate.find(query, Campaign.class);
  }

  @Override
  public CampaignStatistics getCampaignStatisticsByCompanyId(
      String companyId, LocalDate startDate, LocalDate endDate, String dataMode) {
    Criteria criteria = buildCompanyAccessCriteria(companyId);

    // Match campaigns created within [startDate, endDate]. createdAt is a LocalDateTime, so widen
    // the bounds to cover the whole calendar day (endDate inclusive).
    if (startDate != null && endDate != null) {
      criteria =
          criteria
              .and("createdAt")
              .gte(startDate.atStartOfDay())
              .lte(endDate.atTime(LocalTime.MAX));
    } else if (startDate != null) {
      criteria = criteria.and("createdAt").gte(startDate.atStartOfDay());
    } else if (endDate != null) {
      criteria = criteria.and("createdAt").lte(endDate.atTime(LocalTime.MAX));
    }

    Criteria dataModeCriteria = buildDataModeCriteria(dataMode);
    if (dataModeCriteria != null) {
      criteria = new Criteria().andOperator(criteria, dataModeCriteria);
    }

    MatchOperation matchOperation = Aggregation.match(criteria);

    // Group by status and count
    GroupOperation groupOperation =
        Aggregation.group("status").count().as("count").first("status").as("status");

    // Execute single aggregation query
    Aggregation aggregation = Aggregation.newAggregation(matchOperation, groupOperation);
    AggregationResults<StatusCount> results =
        mongoTemplate.aggregate(aggregation, Campaign.class, StatusCount.class);

    // Initialize all counts to 0
    long draftCampaigns = 0;
    long plannedCampaigns = 0;
    long reviewingCampaigns = 0;
    long negotiatingCampaigns = 0;
    long pendingCampaigns = 0;
    long approvedCampaigns = 0;
    long dealRequestedCampaigns = 0;
    long activeCampaigns = 0;
    long pauseCampaigns = 0;
    long completedCampaigns = 0;
    long rejectedCampaigns = 0;
    long archivedCampaigns = 0;

    // Process aggregation results and calculate total
    long totalCampaigns = 0;
    for (StatusCount statusCount : results.getMappedResults()) {
      long count = statusCount.getCount();
      totalCampaigns += count;

      switch (statusCount.getStatus()) {
        case DRAFT:
          draftCampaigns = count;
          break;
        case PLANNED:
          plannedCampaigns = count;
          break;
        case REVIEWING:
          reviewingCampaigns = count;
          break;
        case NEGOTIATING:
          negotiatingCampaigns = count;
          break;
        case PENDING:
          pendingCampaigns = count;
          break;
        case APPROVED:
          approvedCampaigns = count;
          break;
        case DEAL_REQUESTED:
          dealRequestedCampaigns = count;
          break;
        case ACTIVE:
          activeCampaigns = count;
          break;
        case PAUSE:
          pauseCampaigns = count;
          break;
        case COMPLETED:
          completedCampaigns = count;
          break;
        case REJECTED:
          rejectedCampaigns = count;
          break;
        case ARCHIVED:
          archivedCampaigns = count;
          break;
      }
    }

    return CampaignStatistics.builder()
        .totalCampaigns(totalCampaigns)
        .draftCampaigns(draftCampaigns)
        .plannedCampaigns(plannedCampaigns)
        .reviewingCampaigns(reviewingCampaigns)
        .negotiatingCampaigns(negotiatingCampaigns)
        .pendingCampaigns(pendingCampaigns)
        .approvedCampaigns(approvedCampaigns)
        .dealRequestedCampaigns(dealRequestedCampaigns)
        .activeCampaigns(activeCampaigns)
        .pauseCampaigns(pauseCampaigns)
        .completedCampaigns(completedCampaigns)
        .rejectedCampaigns(rejectedCampaigns)
        .archivedCampaigns(archivedCampaigns)
        .build();
  }

  /**
   * Builds criteria for company access check. Matches campaigns where companyId equals the provided
   * companyId OR companyAccess array contains the companyId.
   *
   * @param companyId The company ID to match
   * @return Criteria for company access matching
   */
  private Criteria buildCompanyAccessCriteria(String companyId) {
    return new Criteria()
        .orOperator(
            Criteria.where("companyId").is(companyId),
            Criteria.where("companyAccess").in(companyId));
  }

  /**
   * Test Mode partition criteria: "demo" matches only demo campaigns; anything else matches live
   * campaigns, where records with no dataMode are legacy live rows. Returns null when dataMode is
   * blank (no partition filter, for internal/unauthenticated flows).
   */
  private Criteria buildDataModeCriteria(String dataMode) {
    if (dataMode == null || dataMode.isBlank()) {
      return null;
    }
    if ("demo".equals(dataMode)) {
      return Criteria.where("dataMode").is("demo");
    }
    return new Criteria()
        .orOperator(
            Criteria.where("dataMode").exists(false),
            Criteria.where("dataMode").is(null),
            Criteria.where("dataMode").is("live"));
  }

  @Override
  public int bulkUpdateStatus(
      Campaign.Status currentStatus,
      Campaign.Status newStatus,
      LocalDate startDate,
      LocalDate endDate) {
    Document queryDoc = new Document("status", currentStatus);

    // Handle date comparisons: dates are stored as datetime in MongoDB with timezone conversion.
    if (startDate != null) {
      // Query range: from start of previous day to start of day after target in UTC
      LocalDateTime rangeStart = startDate.minusDays(1).atStartOfDay();
      LocalDateTime rangeEnd = startDate.plusDays(1).atStartOfDay();
      queryDoc.append(
          "startDate",
          new Document("$gte", java.util.Date.from(rangeStart.toInstant(ZoneOffset.UTC)))
              .append("$lt", java.util.Date.from(rangeEnd.toInstant(ZoneOffset.UTC))));
    }

    if (endDate != null) {
      LocalDateTime rangeStart = endDate.minusDays(1).atStartOfDay();
      LocalDateTime rangeEnd = endDate.plusDays(1).atStartOfDay();
      queryDoc.append(
          "endDate",
          new Document("$gte", java.util.Date.from(rangeStart.toInstant(ZoneOffset.UTC)))
              .append("$lt", java.util.Date.from(rangeEnd.toInstant(ZoneOffset.UTC))));
    }

    Query query = new BasicQuery(queryDoc);
    Update update = new Update().set("status", newStatus);

    // Execute update using Spring Data MongoDB
    return (int) mongoTemplate.updateMulti(query, update, Campaign.class).getModifiedCount();
  }

  @Override
  public List<Campaign> findByPerformanceNullAndStatusIn(
      List<Campaign.Status> statuses, String lastId, int limit) {
    Criteria criteria = Criteria.where("performance").is(null).and("status").in(statuses);
    if (lastId != null) {
      criteria = criteria.and("_id").gt(lastId);
    }
    Query query = new Query(criteria).with(Sort.by(Sort.Direction.ASC, "_id")).limit(limit);
    return mongoTemplate.find(query, Campaign.class);
  }

  @Override
  public boolean setPerformanceIfNull(String campaignId, CampaignForecastDTO forecast) {
    // Single-field $set gated on performance still being null: never overwrites a snapshot
    // written in the meantime (e.g. by autosave), and bypasses auditing on purpose so
    // updatedAt/lastModifiedBy stay untouched.
    Query query = new Query(Criteria.where("_id").is(campaignId).and("performance").is(null));
    Update update = new Update().set("performance", forecast);
    return mongoTemplate.updateFirst(query, update, Campaign.class).getModifiedCount() == 1;
  }

  @Override
  public List<Campaign> findByPlanNumberIsNull(String lastId, int limit) {
    Criteria criteria = Criteria.where("planNumber").is(null);
    if (lastId != null) {
      criteria = criteria.and("_id").gt(lastId);
    }
    Query query = new Query(criteria).with(Sort.by(Sort.Direction.ASC, "_id")).limit(limit);
    return mongoTemplate.find(query, Campaign.class);
  }

  @Override
  public boolean setPlanNumberIfNull(String campaignId, String planNumber) {
    Query query = new Query(Criteria.where("_id").is(campaignId).and("planNumber").is(null));
    Update update = new Update().set("planNumber", planNumber);
    return mongoTemplate.updateFirst(query, update, Campaign.class).getModifiedCount() == 1;
  }

  // Helper class for aggregation results
  @Getter
  @Setter
  private static class StatusCount {
    private Campaign.Status status;
    private long count;
  }
}
