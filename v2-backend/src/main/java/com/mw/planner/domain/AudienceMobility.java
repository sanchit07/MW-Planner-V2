package com.mw.planner.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A single audience-mobility observation: a geo point with a footfall weight for a country and an
 * optional time-of-day bucket. This is the production-shaped store a real mobility vendor feed
 * would be ingested into (one document per geo cell × time bucket); the current contents are a
 * representative seeded dataset (see {@code AudienceMobilitySeeder}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "audience_mobility")
@CompoundIndex(name = "country_bucket_idx", def = "{'countryId': 1, 'timeBucket': 1}")
public class AudienceMobility extends BaseEntity<String> {

  /**
   * Country slug matching the {@code countries} master ({@code countryId} field), e.g. "malaysia".
   */
  @Indexed private String countryId;

  private Double lat;

  private Double lng;

  /** Relative footfall weight for this cell/bucket, 0..1 within the country. */
  private Double weight;

  /** Time-of-day bucket: MORNING | AFTERNOON | EVENING | NIGHT. */
  private String timeBucket;

  /** Provenance of the record (e.g. "seed", or a vendor feed id once a real feed is wired in). */
  private String source;
}
