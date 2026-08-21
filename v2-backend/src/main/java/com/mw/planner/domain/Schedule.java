package com.mw.planner.domain;

import com.mw.planner.enums.DiscountValueType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "schedules")
public class Schedule extends BaseEntity<String> {

  private String name;

  @NonNull private LocalDate startDate;

  @NonNull private LocalDate endDate;

  private List<Weekday> scheduleDays; // Which days of the week

  private Type type;

  // Key: Date string in format "yyyy-MM-dd" (e.g., "2025-12-25")
  // Value: List of hours (0-23) when ads are scheduled
  private Map<String, List<Integer>> bookingMatrix;

  private Long duration; // How long each ad plays (in seconds)

  private Long spotsPerLoop; // How many times your ad plays per rotation

  private Long spotsPerHour; // Total plays per hour

  private Long adPlays;

  private Double plannedSot;

  private Double totalSot;

  private Integer order;

  private Double basePrice;

  private Discount discount;

  private String bonusType;

  private Long impressions;

  private Long reach;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Discount {
    private DiscountValueType valueType;
    private String value;
  }

  public enum Weekday {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
  }

  public enum Type {
    LOOP,
    DAYPART
  }
}
