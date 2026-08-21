package com.mw.planner.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sequencer")
public class Sequencer {
  @Id private String id; // prefix: "Campaign_Sep_15_25_"
  private Long sequence; // current sequence number
}
