package com.mw.recommendation.engine.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Utility class for hashing recommendation requests for duplicate detection */
@Slf4j
@Component
public class RequestHashUtils {

  private static final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JavaTimeModule());

  /**
   * Generate a hash of the recommendation request payload (excluding topN for duplicate detection)
   *
   * @param request Recommendation request
   * @return MD5 hash of the request payload
   */
  public static String hashRequest(RecommendationRequestDTO request) {
    try {
      // Create a copy without topN for hashing (topN doesn't affect the actual recommendations,
      // just the limit)
      RecommendationRequestDTO requestForHash = new RecommendationRequestDTO();
      requestForHash.setCountry(request.getCountry());
      requestForHash.setStartDate(request.getStartDate());
      requestForHash.setEndDate(request.getEndDate());
      requestForHash.setProductId(request.getProductId());
      requestForHash.setCompanyId(request.getCompanyId());
      requestForHash.setGeographyTargeting(request.getGeographyTargeting());
      requestForHash.setAudienceTargeting(request.getAudienceTargeting());
      requestForHash.setBrandId(request.getBrandId());
      requestForHash.setBudget(request.getBudget());
      requestForHash.setGoal(request.getGoal());
      requestForHash.setGoalValue(request.getGoalValue());
      requestForHash.setExcludedInventoryIds(request.getExcludedInventoryIds());
      requestForHash.setExcludedIabCategories(request.getExcludedIabCategories());
      requestForHash.setBudgetAllocation(request.getBudgetAllocation());
      requestForHash.setSearchKeywords(normalizeSearchKeywords(request.getSearchKeywords()));
      // dsps participates in dedup, normalized (dedupe/sort/case) so equivalent DSP sets hash the
      // same. This single normalized call supersedes the raw setDsps from the attribute-filter
      // branch — same field, and normalization is a strict superset.
      requestForHash.setDsps(normalizeDsps(request.getDsps()));
      // false hashes identically to absent; only true creates a distinct run
      requestForHash.setProgrammaticEnabled(
          Boolean.TRUE.equals(request.getProgrammaticEnabled()) ? Boolean.TRUE : null);
      // Selected spot durations participate in dedup: different durations scope the run to
      // different inventory, so they must not share cached results. NON_NULL, so a duration-less
      // request serializes (and hashes) exactly as before.
      requestForHash.setDurations(request.getDurations());
      // Inventory-attribute filters also scope the run, so they participate in dedup. All NON_NULL,
      // so a request that omits them serializes (and hashes) exactly as before.
      requestForHash.setFormats(request.getFormats());
      requestForHash.setResolutions(request.getResolutions());
      requestForHash.setCreativeTypes(request.getCreativeTypes());
      requestForHash.setDealTypes(request.getDealTypes());
      requestForHash.setProgrammaticSupport(request.getProgrammaticSupport());
      // Media owner and classification scope the run to a different inventory pool, so they MUST
      // participate in dedup — otherwise two requests that differ only by owner (or Digital vs
      // Classic) collide on the same hash and the second is served the first's cached run.
      requestForHash.setMediaOwnerIds(request.getMediaOwnerIds());
      requestForHash.setClassifications(request.getClassifications());
      // topN is intentionally excluded

      // Serialize to JSON and hash
      String json = objectMapper.writeValueAsString(requestForHash);
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] hashBytes = md.digest(json.getBytes(StandardCharsets.UTF_8));

      // Convert to hex string
      StringBuilder hexString = new StringBuilder();
      for (byte b : hashBytes) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (Exception e) {
      log.error("Error hashing request: {}", e.getMessage(), e);
      // Fallback: use a hash based on key fields
      return String.valueOf(
          (request.getCountry() + request.getStartDate() + request.getEndDate()).hashCode());
    }
  }

  /**
   * Normalize keywords for hashing: trim, drop blanks, lowercase, dedupe, sort. Empty result maps
   * to null so null / empty / all-blank lists hash identically to requests without the field (field
   * is @JsonInclude(NON_NULL)). Keyword order and case never create distinct runs.
   */
  static List<String> normalizeSearchKeywords(List<String> keywords) {
    if (keywords == null || keywords.isEmpty()) return null;
    List<String> normalized =
        keywords.stream()
            .filter(k -> k != null && !k.isBlank())
            .map(k -> k.trim().toLowerCase())
            .distinct()
            .sorted()
            .toList();
    return normalized.isEmpty() ? null : normalized;
  }

  /**
   * Normalize DSPs for hashing: trim, drop blanks, dedupe, sort. Empty result maps to null so null
   * / empty / all-blank lists hash identically to requests without the field (field is
   * {@code @JsonInclude(NON_NULL)}). DSP order and duplicates never create distinct runs. Case is
   * preserved (DSP names are stored verbatim, e.g. "MAX").
   */
  static List<String> normalizeDsps(List<String> dsps) {
    if (dsps == null || dsps.isEmpty()) return null;
    List<String> normalized =
        dsps.stream()
            .filter(d -> d != null && !d.isBlank())
            .map(String::trim)
            .distinct()
            .sorted()
            .toList();
    return normalized.isEmpty() ? null : normalized;
  }
}
