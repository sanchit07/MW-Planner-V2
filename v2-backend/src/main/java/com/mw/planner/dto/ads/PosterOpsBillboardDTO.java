package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosterOpsBillboardDTO {

  @JsonProperty("billboard_id")
  private String billboardId;

  @JsonProperty("face_id")
  private String faceId;
}
