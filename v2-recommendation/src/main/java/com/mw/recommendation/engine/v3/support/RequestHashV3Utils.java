package com.mw.recommendation.engine.v3.support;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Dedup hash for v3 requests. MD5 over a canonicalized JSON view of the request:
 *
 * <ul>
 *   <li>{@code topN} is excluded — it only limits how many results are returned, not what they are.
 *   <li>{@code seed} and {@code disableVariation} ARE included: they change the produced ranking,
 *       so requests differing in them must not dedup to the same run.
 *   <li>{@code searchKeywords} are normalized (trim, lowercase, distinct, sorted) so order/case
 *       variants dedup together.
 *   <li>Map keys and properties are serialized in sorted order for stability.
 * </ul>
 */
@Slf4j
public final class RequestHashV3Utils {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  private RequestHashV3Utils() {}

  public static String hashRequest(RecommendationV3RequestDTO request) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> view = MAPPER.convertValue(request, Map.class);
      view.remove("topN");

      Object keywords = view.get("searchKeywords");
      if (keywords instanceof java.util.List<?> list) {
        var normalized =
            list.stream()
                .filter(k -> k instanceof String s && !s.isBlank())
                .map(k -> ((String) k).trim().toLowerCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
        if (normalized.isEmpty()) {
          view.remove("searchKeywords");
        } else {
          view.put("searchKeywords", normalized);
        }
      }

      String json = MAPPER.writeValueAsString(view);
      MessageDigest md5 = MessageDigest.getInstance("MD5");
      return HexFormat.of().formatHex(md5.digest(json.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      log.warn("v3 request hash failed, falling back to coarse key: {}", e.getMessage());
      String fallback =
          request.getCountry() + "|" + request.getStartDate() + "|" + request.getEndDate();
      return Integer.toHexString(fallback.hashCode());
    }
  }
}
