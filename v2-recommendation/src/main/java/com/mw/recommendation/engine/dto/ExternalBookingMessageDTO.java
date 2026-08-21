package com.mw.recommendation.engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing the booking message structure from the booking application. This matches the
 * JSON structure provided in the requirements.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalBookingMessageDTO {

  @NotNull @NotBlank private String id;

  @NotNull private Integer sspId;

  @NotNull @NotBlank private String dealId;

  private String dealName;

  private String agency;

  private String brand;

  @NotNull @NotBlank private String bookingType; // "guaranteed", etc.

  @NotNull @NotBlank private String status; // "expired", "active", etc.

  private Map<String, Object> metadata;

  private Instant expiresAt;

  private Instant createdAt;

  private UUID createdBy;

  private Instant updatedAt;

  private UUID updatedBy;

  @NotNull private List<Slot> slots;

  /** Slot information within a booking */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Slot {
    @NotNull @NotBlank private String id;

    @NotNull @NotBlank private String bookingId;

    @NotNull @NotBlank private String inventoryId;

    @NotNull @NotBlank private String timeZone;

    @NotNull private Instant startTime;

    @NotNull private Instant endTime;

    // For loop mode (digital inventory)
    private Integer loopSecondsAllocated;

    // For slot mode (digital inventory) or classic inventory
    private Integer secondsAllocated;

    private Integer creativeDuration;

    @NotNull @NotBlank private String bookingType;

    @NotNull @NotBlank private String status;

    // Optional: specific slot positions for digital inventory
    private List<Integer> slotPositions;

    private Instant expiresAt;

    private Instant createdAt;

    private UUID createdBy;
  }
}
