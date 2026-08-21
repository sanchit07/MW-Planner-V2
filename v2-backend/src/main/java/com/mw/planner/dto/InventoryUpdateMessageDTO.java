package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for inventory update message envelope from external systems. Represents the wrapper structure
 * of inventory messages received via RabbitMQ. The actual inventory data is contained in the
 * detailSnapshot field.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryUpdateMessageDTO {

  @JsonProperty("id")
  private String id;

  @JsonProperty("inventoryId")
  private String inventoryId;

  @JsonProperty("operation")
  private String operation;

  @JsonProperty("occurredAt")
  private String occurredAt;

  @JsonProperty("detailSnapshot")
  private ExternalInventoryMessageDTO detailSnapshot;
}
