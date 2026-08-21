package com.mw.planner.domain;

import java.util.List;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "campaign_geo_import_file")
public class CampaignGeoImportFile extends BaseEntity<String> {

  private String fileName;
  private String countryName;
  private String companyId;
  List<GeoDetails> geoDetails;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class GeoDetails {
    private String locationName;
    private String radius;
    private String latitude;
    private String longitude;
    private String siteType;
  }
}
