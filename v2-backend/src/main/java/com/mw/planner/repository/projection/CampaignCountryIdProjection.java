package com.mw.planner.repository.projection;

/**
 * Projection for fetching only countryId from Campaign. Used for performance. Note: In Campaign
 * this field stores the country name; resolve to actual country id via Country table by name.
 */
public interface CampaignCountryIdProjection {
  String getCountryId();
}
