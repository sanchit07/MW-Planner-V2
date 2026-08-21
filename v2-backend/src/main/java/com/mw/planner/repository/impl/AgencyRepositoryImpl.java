package com.mw.planner.repository.impl;

import com.mw.planner.domain.Agency;
import com.mw.planner.repository.AgencyRepositoryCustom;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AgencyRepositoryImpl implements AgencyRepositoryCustom {

  private final MongoTemplate mongoTemplate;

  /** Find agencies by name or country name using MongoDB aggregation pipeline */
  public Page<Agency> findByNameOrCountryNameContainingIgnoreCase(
      String searchTerm, Pageable pageable) {

    // Step 1: Convert string countryId to ObjectId
    AddFieldsOperation convertId =
        Aggregation.addFields()
            .addField("countryIdObj")
            .withValue(ConvertOperators.ToObjectId.toObjectId("$countryId"))
            .build();

    // Step 2: Join with countries collection
    LookupOperation lookupOperation =
        LookupOperation.newLookup()
            .from("countries")
            .localField("countryIdObj")
            .foreignField("_id")
            .as("country");

    // Step 3: Unwind the country array
    UnwindOperation unwindOperation = Aggregation.unwind("country", true);

    // Step 4: Match by name or country name (case insensitive)
    MatchOperation matchOperation =
        new MatchOperation(
            new Criteria()
                .orOperator(
                    Criteria.where("name").regex(searchTerm, "i"),
                    Criteria.where("country.name").regex(searchTerm, "i")));

    // Step 5: Add pagination
    SkipOperation skipOperation = Aggregation.skip(pageable.getOffset());
    LimitOperation limitOperation = Aggregation.limit(pageable.getPageSize());

    // Build main aggregation
    Aggregation aggregation =
        Aggregation.newAggregation(
            convertId,
            lookupOperation,
            unwindOperation,
            matchOperation,
            skipOperation,
            limitOperation);

    AggregationResults<Agency> results =
        mongoTemplate.aggregate(aggregation, "agencies", Agency.class);
    List<Agency> agencies = results.getMappedResults();

    // Total count aggregation
    Aggregation countAggregation =
        Aggregation.newAggregation(
            convertId,
            lookupOperation,
            unwindOperation,
            matchOperation,
            Aggregation.count().as("total"));

    AggregationResults<CountResult> countResults =
        mongoTemplate.aggregate(countAggregation, "agencies", CountResult.class);

    long totalCount =
        countResults.getMappedResults().isEmpty()
            ? 0
            : countResults.getMappedResults().get(0).getTotal();

    return new PageImpl<>(agencies, pageable, totalCount);
  }

  @Setter
  @Getter
  public static class CountResult {
    private long total;
  }
}
