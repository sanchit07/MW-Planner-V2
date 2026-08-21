package com.mw.recommendation.engine.dto;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RecommendationRequestDTO - venueTypeIds deserialization")
class RecommendationRequestDTOVenueTypeIdsTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
  }

  @Test
  @DisplayName("venueTypeIds with digital and classic deserializes correctly")
  void venueTypeIds_withDigitalAndClassic_deserializesCorrectly() throws Exception {
    String json =
        """
        {
          "country": "MY",
          "startDate": "2025-03-01",
          "endDate": "2025-05-31",
          "audienceTargeting": {
            "audienceSegments": ["commuters"],
            "demographics": { "age": ["18-24"] },
            "venueTypeIds": {
              "digital": ["401", "402", "403"],
              "classic": ["301", "302"]
            }
          }
        }
        """;

    RecommendationRequestDTO dto = objectMapper.readValue(json, RecommendationRequestDTO.class);

    assertNotNull(dto.getAudienceTargeting());
    Map<String, List<String>> venueTypeIds = dto.getAudienceTargeting().getVenueTypeIds();
    assertNotNull(venueTypeIds);
    assertEquals(List.of("401", "402", "403"), venueTypeIds.get("digital"));
    assertEquals(List.of("301", "302"), venueTypeIds.get("classic"));
  }

  @Test
  @DisplayName("demographics still deserializes independently of venueTypeIds")
  void demographics_deserializesIndependentlyOfVenueTypeIds() throws Exception {
    String json =
        """
        {
          "country": "MY",
          "startDate": "2025-03-01",
          "endDate": "2025-05-31",
          "audienceTargeting": {
            "demographics": {
              "age": ["18-24", "25-34"],
              "gender": ["MALE", "FEMALE"],
              "income": ["Middle"],
              "interests": ["fitness"]
            },
            "venueTypeIds": { "digital": ["401"] }
          }
        }
        """;

    RecommendationRequestDTO dto = objectMapper.readValue(json, RecommendationRequestDTO.class);

    Map<String, List<String>> demographics = dto.getAudienceTargeting().getDemographics();
    assertNotNull(demographics);
    assertEquals(List.of("18-24", "25-34"), demographics.get("age"));
    assertEquals(List.of("MALE", "FEMALE"), demographics.get("gender"));
    assertFalse(demographics.containsKey("venues"), "venues key must not appear in demographics");
  }

  @Test
  @DisplayName("venueTypeIds absent → null")
  void venueTypeIds_absent_isNull() throws Exception {
    String json =
        """
        {
          "country": "MY",
          "startDate": "2025-03-01",
          "endDate": "2025-05-31",
          "audienceTargeting": {
            "audienceSegments": ["commuters"]
          }
        }
        """;

    RecommendationRequestDTO dto = objectMapper.readValue(json, RecommendationRequestDTO.class);

    assertNull(dto.getAudienceTargeting().getVenueTypeIds());
  }

  @Test
  @DisplayName("venueTypeIds with empty list for a classification deserializes correctly")
  void venueTypeIds_emptyListForClassification_deserializesCorrectly() throws Exception {
    String json =
        """
        {
          "country": "MY",
          "startDate": "2025-03-01",
          "endDate": "2025-05-31",
          "audienceTargeting": {
            "venueTypeIds": {
              "digital": [],
              "classic": ["301", "302"]
            }
          }
        }
        """;

    RecommendationRequestDTO dto = objectMapper.readValue(json, RecommendationRequestDTO.class);

    Map<String, List<String>> venueTypeIds = dto.getAudienceTargeting().getVenueTypeIds();
    assertNotNull(venueTypeIds);
    assertTrue(venueTypeIds.get("digital").isEmpty());
    assertEquals(List.of("301", "302"), venueTypeIds.get("classic"));
  }

  @Test
  @DisplayName("venueTypeIds with null value for a classification deserializes correctly")
  void venueTypeIds_nullValueForClassification_deserializesCorrectly() throws Exception {
    String json =
        """
        {
          "country": "MY",
          "startDate": "2025-03-01",
          "endDate": "2025-05-31",
          "audienceTargeting": {
            "venueTypeIds": {
              "digital": null,
              "classic": ["301", "302"]
            }
          }
        }
        """;

    RecommendationRequestDTO dto = objectMapper.readValue(json, RecommendationRequestDTO.class);

    Map<String, List<String>> venueTypeIds = dto.getAudienceTargeting().getVenueTypeIds();
    assertNotNull(venueTypeIds);
    assertNull(venueTypeIds.get("digital"));
    assertEquals(List.of("301", "302"), venueTypeIds.get("classic"));
  }
}
