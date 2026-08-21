package com.mw.planner.service;

import com.mw.planner.domain.Sequencer;
import com.mw.planner.repository.SequencerRepository;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SequencerService {

  private final SequencerRepository sequencerRepository;
  private final MongoTemplate mongoTemplate;

  // Pattern to match campaign names like "Campaign_Sep_17_25_0001"
  // This will match: Campaign_MMM_DD_YY_NNNN where NNNN is 1-4 digits
  private static final Pattern CAMPAIGN_NAME_PATTERN =
      Pattern.compile("^([A-Za-z]+_[A-Za-z]{3}_\\d{2}_\\d{2}_)(\\d{1,4})$");

  // Pattern.compile("^([A-Za-z]+[A-Za-z]{3}\\d{2}\\d{2})(\\d{1,4})$");

  /**
   * This method looks up a {@link Sequencer} by its identifier (the provided {@code prefix}) using
   * {@code sequencerRepository}. If a matching record exists, it returns the current stored
   * sequence value plus one; if no record is found, it returns {@code 1L}.
   *
   * @param prefix the identifier/key used to look up the {@link Sequencer}
   * @return the next sequence number current value + 1 if present, otherwise {@code 1L}
   */
  public Long getSequence(String prefix) {
    Optional<Sequencer> sequencer = sequencerRepository.findById(prefix);
    if (sequencer.isPresent()) {
      return sequencer.get().getSequence() + 1;
    }
    return 1L;
  }

  /**
   * This method increments the sequence number for a given {@code prefix}. If a {@link Sequencer}
   * with the specified prefix exists, it increments its sequence value by one and saves the updated
   * record. If no such record exists, it creates a new {@link Sequencer} with the sequence
   * initialized to {@code 1L}.
   *
   * @param prefix the identifier/key used to look up or create the {@link Sequencer}
   * @return the updated sequence number after incrementing, or {@code 1L} if a new record was
   *     created
   */
  public Long increaseSequence(String prefix) {
    Optional<Sequencer> sequenceGenerator = sequencerRepository.findById(prefix);
    if (sequenceGenerator.isPresent()) {
      Sequencer existingSequencer = sequenceGenerator.get();
      existingSequencer.setSequence(existingSequencer.getSequence() + 1);
      sequencerRepository.save(existingSequencer);
      return existingSequencer.getSequence();
    } else {
      Sequencer newSequencer = Sequencer.builder().id(prefix).sequence(1L).build();
      sequencerRepository.save(newSequencer);
      return 1L;
    }
  }

  /**
   * Atomically increments (upserting if absent) the sequence for the given {@code prefix} via a
   * single {@code findAndModify}, and returns the new value. Unlike {@link
   * #increaseSequence(String)}'s read-then-write, this has no read-modify-write race under
   * concurrent callers for the same prefix — required for generating values (like the numeric plan
   * ID) that must actually be unique, not just usually unique.
   *
   * @param prefix the identifier/key used to look up or create the {@link Sequencer}
   * @return the sequence value after incrementing
   */
  public Long getNextSequenceAtomic(String prefix) {
    Query query = Query.query(Criteria.where("_id").is(prefix));
    Update update = new Update().inc("sequence", 1L);
    Sequencer result =
        mongoTemplate.findAndModify(
            query,
            update,
            FindAndModifyOptions.options().upsert(true).returnNew(true),
            Sequencer.class);
    return result.getSequence();
  }

  /**
   * Extracts the prefix from a campaign name that follows the pattern "Campaign_MMM_DD_YY_NNNN".
   * For example, "Campaign_Sep_17_25_0001" will return "Campaign_Sep_17_25_".
   *
   * @param campaignName the campaign name to extract prefix from
   * @return the prefix if the name matches the pattern, null otherwise
   */
  public String extractPrefixFromCampaignName(String campaignName) {
    if (campaignName == null || campaignName.trim().isEmpty()) {
      return null;
    }

    Matcher matcher = CAMPAIGN_NAME_PATTERN.matcher(campaignName.trim());
    if (matcher.matches()) {
      return matcher.group(1);
    }

    return null;
  }

  /**
   * Increments the sequence for a campaign name. This method handles both pattern-based campaign
   * names (like "Campaign_Sep_17_25_0001") and non-pattern names (like "Summer sale campaign").
   *
   * <p>For pattern-based names, it extracts the prefix and increments the sequence for that prefix.
   * For non-pattern names, it uses the full campaign name as the prefix.
   *
   * @param campaignName the campaign name to increment sequence for
   * @return the updated sequence number after incrementing
   */
  public Long incrementSequenceForCampaignName(String campaignName) {
    if (campaignName == null || campaignName.trim().isEmpty()) {
      log.warn("Campaign name is null or empty, cannot increment sequence");
      return null;
    }

    String prefix = extractPrefixFromCampaignName(campaignName);

    // If no pattern match, use the full campaign name as prefix
    if (prefix == null) {
      prefix = campaignName.trim();
      log.debug(
          "Campaign name '{}' does not match pattern, using full name as prefix", campaignName);
    } else {
      log.debug("Extracted prefix '{}' from campaign name '{}'", prefix, campaignName);
    }

    return increaseSequence(prefix);
  }
}
