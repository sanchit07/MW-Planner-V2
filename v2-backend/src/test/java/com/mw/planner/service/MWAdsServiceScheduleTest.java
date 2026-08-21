package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import com.mw.planner.domain.Schedule;
import com.mw.planner.dto.ads.ScheduleDTO;
import com.mw.planner.repository.CampaignInventorySchedulesRepository;
import com.mw.planner.repository.ScheduleRepository;
import java.lang.reflect.Method;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for schedule-related methods in MWAdsService, specifically testing the LOOP schedule
 * type handling introduced in March 2026.
 */
@ExtendWith(MockitoExtension.class)
class MWAdsServiceScheduleTest {

  @Mock private com.mw.planner.config.MwPlannerProperties mwPlannerProperties;
  @Mock private org.springframework.web.client.RestTemplate restTemplate;
  @Mock private CampaignService campaignService;
  @Mock private CampaignInventorySchedulesRepository campaignInventorySchedulesRepository;
  @Mock private InventoryService inventoryService;
  @Mock private CountryService countryService;
  @Mock private com.mw.brand.lib.service.BrandService brandService;
  @Mock private CompanyService companyService;
  @Mock private UserService userService;
  @Mock private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
  @Mock private ScheduleRepository scheduleRepository;

  @InjectMocks private MWAdsService mwAdsService;

  @BeforeEach
  void setUp() {
    lenient()
        .when(mwPlannerProperties.getAds())
        .thenReturn(new com.mw.planner.config.MwPlannerProperties.Ads());
  }

  // ==================== buildLoopScheduleDTO() Tests ====================

  @Test
  @DisplayName("buildLoopScheduleDTO - Should return null for non-LOOP schedule type")
  void buildLoopScheduleDTO_WithNonLoopType_ReturnsNull() throws Exception {
    Schedule schedule =
        Schedule.builder()
            .type(Schedule.Type.DAYPART)
            .startDate(LocalDate.of(2026, 3, 1))
            .endDate(LocalDate.of(2026, 3, 31))
            .build();
    schedule.setId("sched-1");

    ScheduleDTO result = invokePrivateMethod("buildLoopScheduleDTO", schedule);

    assertThat(result).isNull();
  }

  @Test
  @DisplayName("buildLoopScheduleDTO - Should return null when type is null")
  void buildLoopScheduleDTO_WithNullType_ReturnsNull() throws Exception {
    Schedule schedule =
        Schedule.builder()
            .type(null)
            .startDate(LocalDate.of(2026, 3, 1))
            .endDate(LocalDate.of(2026, 3, 31))
            .build();
    schedule.setId("sched-1");

    ScheduleDTO result = invokePrivateMethod("buildLoopScheduleDTO", schedule);

    assertThat(result).isNull();
  }

  @Test
  @DisplayName("buildLoopScheduleDTO - Should build DTO for LOOP schedule with booking matrix")
  void buildLoopScheduleDTO_WithLoopTypeAndBookingMatrix_BuildsDTO() throws Exception {
    Map<String, List<Integer>> bookingMatrix = new LinkedHashMap<>();
    bookingMatrix.put("2026-03-15", List.of(9, 10, 11, 14, 15, 16, 17, 18, 19, 20));
    bookingMatrix.put("2026-03-16", List.of(8, 9, 10, 11, 12, 13, 14, 15, 16, 17));

    Schedule schedule =
        Schedule.builder()
            .type(Schedule.Type.LOOP)
            .startDate(LocalDate.of(2026, 3, 1))
            .endDate(LocalDate.of(2026, 3, 31))
            .bookingMatrix(bookingMatrix)
            .build();
    schedule.setId("sched-loop-1");

    ScheduleDTO result = invokePrivateMethod("buildLoopScheduleDTO", schedule);

    assertThat(result).isNotNull();
    assertThat(result.getType()).isEqualTo("DEFAULT");
    assertThat(result.getPriority()).isEqualTo(1);
    assertThat(result.getDate()).isNull();
    assertThat(result.getValidity()).isNotNull();
    assertThat(result.getValidity().getStartDate()).isEqualTo("2026-03-01");
    assertThat(result.getValidity().getEndDate()).isEqualTo("2026-03-31");
    assertThat(result.getHours()).isNotNull();
    assertThat(result.getHours()).isNotEmpty();
    assertThat(result.getDaysOfWeek()).isNull();
  }

  @Test
  @DisplayName("buildLoopScheduleDTO - Should build DTO without hours when booking matrix is empty")
  void buildLoopScheduleDTO_WithEmptyBookingMatrix_BuildsDtoWithEmptyHours() throws Exception {
    Schedule schedule =
        Schedule.builder()
            .type(Schedule.Type.LOOP)
            .startDate(LocalDate.of(2026, 3, 1))
            .endDate(LocalDate.of(2026, 3, 31))
            .bookingMatrix(new HashMap<>())
            .build();
    schedule.setId("sched-loop-2");

    ScheduleDTO result = invokePrivateMethod("buildLoopScheduleDTO", schedule);

    assertThat(result).isNotNull();
    assertThat(result.getType()).isEqualTo("DEFAULT");
    assertThat(result.getHours()).isEmpty();
    assertThat(result.getValidity().getStartDate()).isEqualTo("2026-03-01");
    assertThat(result.getValidity().getEndDate()).isEqualTo("2026-03-31");
    assertThat(result.getDaysOfWeek()).isNull();
  }

  @Test
  @DisplayName("buildLoopScheduleDTO - Should build DTO without hours when booking matrix is null")
  void buildLoopScheduleDTO_WithNullBookingMatrix_BuildsDtoWithEmptyHours() throws Exception {
    Schedule schedule =
        Schedule.builder()
            .type(Schedule.Type.LOOP)
            .startDate(LocalDate.of(2026, 4, 1))
            .endDate(LocalDate.of(2026, 4, 30))
            .bookingMatrix(null)
            .build();
    schedule.setId("sched-loop-3");

    ScheduleDTO result = invokePrivateMethod("buildLoopScheduleDTO", schedule);

    assertThat(result).isNotNull();
    assertThat(result.getType()).isEqualTo("DEFAULT");
    assertThat(result.getHours()).isEmpty();
  }

  @Test
  @DisplayName(
      "buildLoopScheduleDTO - Should extract hours from first booking matrix entry for LOOP")
  void buildLoopScheduleDTO_WithMultipleDates_UsesOnlyFirstEntry() throws Exception {
    Map<String, List<Integer>> bookingMatrix = new LinkedHashMap<>();
    bookingMatrix.put("2026-03-20", List.of(6, 7, 8, 9)); // First entry - should be used
    bookingMatrix.put("2026-03-21", List.of(18, 19, 20, 21)); // Should be ignored
    bookingMatrix.put("2026-03-22", List.of(12, 13, 14, 15)); // Should be ignored

    Schedule schedule =
        Schedule.builder()
            .type(Schedule.Type.LOOP)
            .startDate(LocalDate.of(2026, 3, 20))
            .endDate(LocalDate.of(2026, 3, 31))
            .bookingMatrix(bookingMatrix)
            .build();
    schedule.setId("sched-loop-5");

    ScheduleDTO result = invokePrivateMethod("buildLoopScheduleDTO", schedule);

    assertThat(result).isNotNull();
    assertThat(result.getHours()).isNotEmpty();
    // Should only have hours from first entry (6-9), not from other dates
    assertThat(result.getHours().size()).isLessThanOrEqualTo(2); // Max 2 ranges for hours 6-9
  }

  @Test
  @DisplayName("buildLoopScheduleDTO - Should map scheduleDays to daysOfWeek integers")
  void buildLoopScheduleDTO_WithScheduleDays_MapsToDaysOfWeek() throws Exception {
    Schedule schedule =
        Schedule.builder()
            .type(Schedule.Type.LOOP)
            .startDate(LocalDate.of(2026, 3, 1))
            .endDate(LocalDate.of(2026, 3, 31))
            .scheduleDays(
                List.of(
                    Schedule.Weekday.MONDAY,
                    Schedule.Weekday.WEDNESDAY,
                    Schedule.Weekday.FRIDAY,
                    Schedule.Weekday.SUNDAY))
            .bookingMatrix(null)
            .build();
    schedule.setId("sched-days-1");

    ScheduleDTO result = invokePrivateMethod("buildLoopScheduleDTO", schedule);

    assertThat(result).isNotNull();
    assertThat(result.getDaysOfWeek()).containsExactly(1, 3, 5, 7);
  }

  @Test
  @DisplayName("buildLoopScheduleDTO - Should set daysOfWeek to null when scheduleDays is null")
  void buildLoopScheduleDTO_WithNullScheduleDays_DaysOfWeekIsNull() throws Exception {
    Schedule schedule =
        Schedule.builder()
            .type(Schedule.Type.LOOP)
            .startDate(LocalDate.of(2026, 3, 1))
            .endDate(LocalDate.of(2026, 3, 31))
            .scheduleDays(null)
            .bookingMatrix(null)
            .build();
    schedule.setId("sched-days-2");

    ScheduleDTO result = invokePrivateMethod("buildLoopScheduleDTO", schedule);

    assertThat(result).isNotNull();
    assertThat(result.getDaysOfWeek()).isNull();
  }

  @Test
  @DisplayName("buildLoopScheduleDTO - Should map all weekdays correctly (MONDAY=1 to SUNDAY=7)")
  void buildLoopScheduleDTO_WithAllWeekdays_MapsAllCorrectly() throws Exception {
    Schedule schedule =
        Schedule.builder()
            .type(Schedule.Type.LOOP)
            .startDate(LocalDate.of(2026, 3, 1))
            .endDate(LocalDate.of(2026, 3, 31))
            .scheduleDays(
                List.of(
                    Schedule.Weekday.MONDAY,
                    Schedule.Weekday.TUESDAY,
                    Schedule.Weekday.WEDNESDAY,
                    Schedule.Weekday.THURSDAY,
                    Schedule.Weekday.FRIDAY,
                    Schedule.Weekday.SATURDAY,
                    Schedule.Weekday.SUNDAY))
            .bookingMatrix(null)
            .build();
    schedule.setId("sched-days-3");

    ScheduleDTO result = invokePrivateMethod("buildLoopScheduleDTO", schedule);

    assertThat(result).isNotNull();
    assertThat(result.getDaysOfWeek()).containsExactly(1, 2, 3, 4, 5, 6, 7);
  }

  // ==================== buildScheduleDTOs() Tests ====================

  @Test
  @DisplayName("buildScheduleDTOs - Should return null when schedule list is null")
  void buildScheduleDTOs_WithNullSchedules_ReturnsNull() throws Exception {
    List<ScheduleDTO> result = invokePrivateMethod("buildScheduleDTOs", (Object) null);

    assertThat(result).isNull();
  }

  @Test
  @DisplayName("buildScheduleDTOs - Should return null when schedule list is empty")
  void buildScheduleDTOs_WithEmptySchedules_ReturnsNull() throws Exception {
    List<Schedule> schedules = new ArrayList<>();

    List<ScheduleDTO> result = invokePrivateMethod("buildScheduleDTOs", schedules);

    assertThat(result).isNull();
  }

  @Test
  @DisplayName("buildScheduleDTOs - Should process LOOP type schedule separately")
  void buildScheduleDTOs_WithLoopSchedule_ProcessesAsLoopType() throws Exception {
    Map<String, List<Integer>> bookingMatrix = new LinkedHashMap<>();
    bookingMatrix.put("2026-03-24", List.of(10, 11, 12, 13, 14, 15));

    Schedule loopSchedule =
        Schedule.builder()
            .type(Schedule.Type.LOOP)
            .startDate(LocalDate.of(2026, 3, 24))
            .endDate(LocalDate.of(2026, 3, 31))
            .bookingMatrix(bookingMatrix)
            .build();
    loopSchedule.setId("loop-1");

    List<Schedule> schedules = List.of(loopSchedule);

    List<ScheduleDTO> result = invokePrivateMethod("buildScheduleDTOs", schedules);

    assertThat(result).isNotNull();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getType()).isEqualTo("DEFAULT");
    assertThat(result.get(0).getDate()).isNull(); // LOOP schedules have no specific date
  }

  @Test
  @DisplayName("buildScheduleDTOs - Should process DAYPART schedule normally")
  void buildScheduleDTOs_WithDaypartSchedule_ProcessesDateWise() throws Exception {
    Map<String, List<Integer>> bookingMatrix = new LinkedHashMap<>();
    bookingMatrix.put("2026-03-25", List.of(9, 10, 11));
    bookingMatrix.put("2026-03-26", List.of(14, 15, 16));

    Schedule daypartSchedule =
        Schedule.builder()
            .type(Schedule.Type.DAYPART)
            .startDate(LocalDate.of(2026, 3, 25))
            .endDate(LocalDate.of(2026, 3, 26))
            .bookingMatrix(bookingMatrix)
            .build();
    daypartSchedule.setId("daypart-1");

    List<Schedule> schedules = List.of(daypartSchedule);

    List<ScheduleDTO> result = invokePrivateMethod("buildScheduleDTOs", schedules);

    assertThat(result).isNotNull();
    assertThat(result).hasSize(2); // One DTO per date
    assertThat(result.stream().map(ScheduleDTO::getDate))
        .containsExactlyInAnyOrder("2026-03-25", "2026-03-26");
  }

  @Test
  @DisplayName("buildScheduleDTOs - Should process mixed LOOP and DAYPART schedules in same list")
  void buildScheduleDTOs_WithMixedScheduleTypes_ProcessesBothTypes() throws Exception {
    Map<String, List<Integer>> loopMatrix = new LinkedHashMap<>();
    loopMatrix.put("2026-03-24", List.of(8, 9, 10));

    Schedule loopSchedule =
        Schedule.builder()
            .type(Schedule.Type.LOOP)
            .startDate(LocalDate.of(2026, 3, 24))
            .endDate(LocalDate.of(2026, 3, 31))
            .bookingMatrix(loopMatrix)
            .build();
    loopSchedule.setId("loop-1");

    Map<String, List<Integer>> daypartMatrix = new LinkedHashMap<>();
    daypartMatrix.put("2026-03-27", List.of(14, 15, 16));

    Schedule daypartSchedule =
        Schedule.builder()
            .type(Schedule.Type.DAYPART)
            .startDate(LocalDate.of(2026, 3, 27))
            .endDate(LocalDate.of(2026, 3, 27))
            .bookingMatrix(daypartMatrix)
            .build();
    daypartSchedule.setId("daypart-1");

    List<Schedule> schedules = List.of(loopSchedule, daypartSchedule);

    List<ScheduleDTO> result = invokePrivateMethod("buildScheduleDTOs", schedules);

    assertThat(result).isNotNull();
    assertThat(result).hasSize(2); // One LOOP DTO + one DAYPART DTO
    assertThat(result.stream().filter(dto -> dto.getType().equals("DEFAULT")).count())
        .isEqualTo(1); // LOOP type
    assertThat(result.stream().filter(dto -> dto.getDate() != null).count())
        .isEqualTo(1); // DAYPART type
  }

  @Test
  @DisplayName("buildScheduleDTOs - Should handle schedule with null type as DAYPART")
  void buildScheduleDTOs_WithNullType_ProcessesAsDaypart() throws Exception {
    Map<String, List<Integer>> bookingMatrix = new LinkedHashMap<>();
    bookingMatrix.put("2026-03-28", List.of(10, 11, 12));

    Schedule schedule =
        Schedule.builder()
            .type(null) // Null type should default to DAYPART processing
            .startDate(LocalDate.of(2026, 3, 28))
            .endDate(LocalDate.of(2026, 3, 28))
            .bookingMatrix(bookingMatrix)
            .build();
    schedule.setId("null-type-1");

    List<Schedule> schedules = List.of(schedule);

    List<ScheduleDTO> result = invokePrivateMethod("buildScheduleDTOs", schedules);

    assertThat(result).isNotNull();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getDate()).isEqualTo("2026-03-28"); // Processed as DAYPART
  }

  @Test
  @DisplayName(
      "buildScheduleDTOs - Each DAYPART schedule is processed independently (no cross-schedule"
          + " merge)")
  void buildScheduleDTOs_WithMultipleDaypartSchedules_ProcessedPerSchedule() throws Exception {
    // Two single-date schedules on the SAME date with different hours. Per-schedule processing
    // emits a separate CUSTOM entry for each; overlap-union is left to ADS.
    Map<String, List<Integer>> matrix1 = new LinkedHashMap<>();
    matrix1.put("2026-03-29", List.of(8, 9, 10));

    Schedule schedule1 =
        Schedule.builder()
            .type(Schedule.Type.DAYPART)
            .startDate(LocalDate.of(2026, 3, 29))
            .endDate(LocalDate.of(2026, 3, 29))
            .bookingMatrix(matrix1)
            .build();
    schedule1.setId("daypart-1");

    Map<String, List<Integer>> matrix2 = new LinkedHashMap<>();
    matrix2.put("2026-03-29", List.of(14, 15, 16)); // Same date, different hours

    Schedule schedule2 =
        Schedule.builder()
            .type(Schedule.Type.DAYPART)
            .startDate(LocalDate.of(2026, 3, 29))
            .endDate(LocalDate.of(2026, 3, 29))
            .bookingMatrix(matrix2)
            .build();
    schedule2.setId("daypart-2");

    List<Schedule> schedules = List.of(schedule1, schedule2);

    List<ScheduleDTO> result = invokePrivateMethod("buildScheduleDTOs", schedules);

    assertThat(result).isNotNull();
    assertThat(result).hasSize(2); // One CUSTOM per schedule, NOT merged
    assertThat(result).allMatch(dto -> "CUSTOM".equals(dto.getType()));
    assertThat(result).allMatch(dto -> "2026-03-29".equals(dto.getDate()));
  }

  // ==================== convertHoursToRanges() Tests ====================

  @Test
  @DisplayName(
      "convertHoursToRanges - Should format consecutive hours as single HH:MM string range")
  void convertHoursToRanges_ConsecutiveHours_FormatsAsHHMMStrings() throws Exception {
    List<Integer> hours = List.of(11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21);

    List<ScheduleDTO.HourRangeDTO> result = invokePrivateMethod("convertHoursToRanges", hours);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getStart()).isEqualTo("11:00");
    assertThat(result.get(0).getEnd()).isEqualTo("21:00");
  }

  @Test
  @DisplayName("convertHoursToRanges - Should zero-pad single-digit hours in HH:MM format")
  void convertHoursToRanges_SingleDigitHours_ZeroPadsCorrectly() throws Exception {
    List<Integer> hours = List.of(6, 7, 8, 9);

    List<ScheduleDTO.HourRangeDTO> result = invokePrivateMethod("convertHoursToRanges", hours);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getStart()).isEqualTo("06:00");
    assertThat(result.get(0).getEnd()).isEqualTo("09:00");
  }

  @Test
  @DisplayName("convertHoursToRanges - Should produce multiple HH:MM ranges when hours have gaps")
  void convertHoursToRanges_HoursWithGaps_ProducesMultipleFormattedRanges() throws Exception {
    List<Integer> hours = List.of(9, 10, 14, 15, 16);

    List<ScheduleDTO.HourRangeDTO> result = invokePrivateMethod("convertHoursToRanges", hours);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getStart()).isEqualTo("09:00");
    assertThat(result.get(0).getEnd()).isEqualTo("10:00");
    assertThat(result.get(1).getStart()).isEqualTo("14:00");
    assertThat(result.get(1).getEnd()).isEqualTo("16:00");
  }

  @Test
  @DisplayName("convertHoursToRanges - Should return empty list for null input")
  void convertHoursToRanges_NullInput_ReturnsEmptyList() throws Exception {
    List<ScheduleDTO.HourRangeDTO> result =
        invokePrivateMethod("convertHoursToRanges", (Object) null);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("convertHoursToRanges - Should return empty list for empty input")
  void convertHoursToRanges_EmptyList_ReturnsEmptyList() throws Exception {
    List<ScheduleDTO.HourRangeDTO> result =
        invokePrivateMethod("convertHoursToRanges", new ArrayList<Integer>());

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("convertHoursToRanges - Single hour produces range where start equals end")
  void convertHoursToRanges_SingleHour_ProducesStartEqualsEndRange() throws Exception {
    List<Integer> hours = List.of(14);

    List<ScheduleDTO.HourRangeDTO> result = invokePrivateMethod("convertHoursToRanges", hours);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getStart()).isEqualTo("14:00");
    assertThat(result.get(0).getEnd()).isEqualTo("14:00");
  }

  @Test
  @DisplayName("convertHoursToRanges - Midnight hour 0 formats as 00:00")
  void convertHoursToRanges_MidnightHour_FormatsAsDoubleZero() throws Exception {
    List<Integer> hours = List.of(0, 1, 2);

    List<ScheduleDTO.HourRangeDTO> result = invokePrivateMethod("convertHoursToRanges", hours);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getStart()).isEqualTo("00:00");
    assertThat(result.get(0).getEnd()).isEqualTo("02:00");
  }

  @Test
  @DisplayName("convertHoursToRanges - Hour 23 formats as 23:00 with no zero-padding")
  void convertHoursToRanges_LastHourOfDay_FormatsCorrectly() throws Exception {
    List<Integer> hours = List.of(21, 22, 23);

    List<ScheduleDTO.HourRangeDTO> result = invokePrivateMethod("convertHoursToRanges", hours);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getStart()).isEqualTo("21:00");
    assertThat(result.get(0).getEnd()).isEqualTo("23:00");
  }

  @Test
  @DisplayName("convertHoursToRanges - All 24 hours produces single range 00:00 to 23:00")
  void convertHoursToRanges_AllHours_ProducesSingleFullDayRange() throws Exception {
    List<Integer> hours = new ArrayList<>();
    for (int i = 0; i <= 23; i++) hours.add(i);

    List<ScheduleDTO.HourRangeDTO> result = invokePrivateMethod("convertHoursToRanges", hours);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getStart()).isEqualTo("00:00");
    assertThat(result.get(0).getEnd()).isEqualTo("23:00");
  }

  @Test
  @DisplayName("convertHoursToRanges - Double-digit hours need no zero-padding")
  void convertHoursToRanges_DoubleDigitHours_NoZeroPadding() throws Exception {
    List<Integer> hours = List.of(10, 11, 12, 13);

    List<ScheduleDTO.HourRangeDTO> result = invokePrivateMethod("convertHoursToRanges", hours);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getStart()).isEqualTo("10:00");
    assertThat(result.get(0).getEnd()).isEqualTo("13:00");
  }

  @Test
  @DisplayName("convertHoursToRanges - Each isolated hour becomes its own single-hour range")
  void convertHoursToRanges_AllIsolatedHours_EachBecomesOwnRange() throws Exception {
    List<Integer> hours = List.of(1, 3, 5, 7);

    List<ScheduleDTO.HourRangeDTO> result = invokePrivateMethod("convertHoursToRanges", hours);

    assertThat(result).hasSize(4);
    assertThat(result.get(0).getStart()).isEqualTo("01:00");
    assertThat(result.get(0).getEnd()).isEqualTo("01:00");
    assertThat(result.get(1).getStart()).isEqualTo("03:00");
    assertThat(result.get(1).getEnd()).isEqualTo("03:00");
    assertThat(result.get(2).getStart()).isEqualTo("05:00");
    assertThat(result.get(2).getEnd()).isEqualTo("05:00");
    assertThat(result.get(3).getStart()).isEqualTo("07:00");
    assertThat(result.get(3).getEnd()).isEqualTo("07:00");
  }

  @Test
  @DisplayName("convertHoursToRanges - Three separate ranges from three disjoint blocks")
  void convertHoursToRanges_ThreeDisjointBlocks_ProducesThreeRanges() throws Exception {
    List<Integer> hours = List.of(6, 7, 8, 12, 13, 18, 19, 20);

    List<ScheduleDTO.HourRangeDTO> result = invokePrivateMethod("convertHoursToRanges", hours);

    assertThat(result).hasSize(3);
    assertThat(result.get(0).getStart()).isEqualTo("06:00");
    assertThat(result.get(0).getEnd()).isEqualTo("08:00");
    assertThat(result.get(1).getStart()).isEqualTo("12:00");
    assertThat(result.get(1).getEnd()).isEqualTo("13:00");
    assertThat(result.get(2).getStart()).isEqualTo("18:00");
    assertThat(result.get(2).getEnd()).isEqualTo("20:00");
  }

  @Test
  @DisplayName(
      "buildLoopScheduleDTO - Unsorted booking matrix hours are sorted before range conversion")
  void buildLoopScheduleDTO_UnsortedBookingMatrixHours_ProducesCorrectSortedRanges()
      throws Exception {
    Map<String, List<Integer>> bookingMatrix = new LinkedHashMap<>();
    // Deliberately unsorted: gaps at 13 and 17, out-of-order input
    bookingMatrix.put("2026-03-15", List.of(14, 9, 10, 16, 15, 11));

    Schedule schedule =
        Schedule.builder()
            .type(Schedule.Type.LOOP)
            .startDate(LocalDate.of(2026, 3, 15))
            .endDate(LocalDate.of(2026, 3, 31))
            .bookingMatrix(bookingMatrix)
            .build();
    schedule.setId("loop-unsorted");

    ScheduleDTO result = invokePrivateMethod("buildLoopScheduleDTO", schedule);

    assertThat(result).isNotNull();
    assertThat(result.getHours()).hasSize(2);
    // Sorted: [9,10,11,14,15,16] → two ranges: 09:00-11:00 and 14:00-16:00
    assertThat(result.getHours().get(0).getStart()).isEqualTo("09:00");
    assertThat(result.getHours().get(0).getEnd()).isEqualTo("11:00");
    assertThat(result.getHours().get(1).getStart()).isEqualTo("14:00");
    assertThat(result.getHours().get(1).getEnd()).isEqualTo("16:00");
  }

  @Test
  @DisplayName("buildScheduleDTOs - DAYPART schedule hours are formatted as HH:MM strings")
  void buildScheduleDTOs_DaypartSchedule_HoursFormattedAsHHMMStrings() throws Exception {
    Map<String, List<Integer>> bookingMatrix = new LinkedHashMap<>();
    bookingMatrix.put(
        "2026-05-01", List.of(7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23));

    Schedule schedule =
        Schedule.builder()
            .type(Schedule.Type.DAYPART)
            .startDate(LocalDate.of(2026, 5, 1))
            .endDate(LocalDate.of(2026, 5, 1))
            .bookingMatrix(bookingMatrix)
            .build();
    schedule.setId("daypart-format");

    List<ScheduleDTO> result = invokePrivateMethod("buildScheduleDTOs", List.of(schedule));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getHours()).hasSize(1);
    assertThat(result.get(0).getHours().get(0).getStart()).isEqualTo("07:00");
    assertThat(result.get(0).getHours().get(0).getEnd()).isEqualTo("23:00");
  }

  // ==================== DAYPART -> DEFAULT collapse Tests ====================

  @Test
  @DisplayName("collapse - Uniform hours every day collapses to one DEFAULT with all 7 weekdays")
  void buildScheduleDTOs_UniformAllWeekdays_CollapsesToDefault() throws Exception {
    List<Integer> hours = List.of(0, 1, 18, 19, 20, 21, 22, 23);
    Map<String, List<Integer>> matrix = new LinkedHashMap<>();
    // 2026-06-15 (Mon) .. 2026-06-21 (Sun) — every day booked, identical hours
    LocalDate d = LocalDate.of(2026, 6, 15);
    LocalDate end = LocalDate.of(2026, 6, 21);
    while (!d.isAfter(end)) {
      matrix.put(d.toString(), hours);
      d = d.plusDays(1);
    }

    Schedule schedule =
        Schedule.builder()
            .type(Schedule.Type.DAYPART)
            .startDate(LocalDate.of(2026, 6, 15))
            .endDate(LocalDate.of(2026, 6, 21))
            .bookingMatrix(matrix)
            .build();
    schedule.setId("collapse-all-days");

    List<ScheduleDTO> result = invokePrivateMethod("buildScheduleDTOs", List.of(schedule));

    assertThat(result).hasSize(1);
    ScheduleDTO dto = result.get(0);
    assertThat(dto.getType()).isEqualTo("DEFAULT");
    assertThat(dto.getDate()).isNull();
    assertThat(dto.getDaysOfWeek()).containsExactly(1, 2, 3, 4, 5, 6, 7);
    assertThat(dto.getValidity().getStartDate()).isEqualTo("2026-06-15");
    assertThat(dto.getValidity().getEndDate()).isEqualTo("2026-06-21");
    // hours [0,1,18..23] -> {00:00-01:00},{18:00-23:00}
    assertThat(dto.getHours()).hasSize(2);
    assertThat(dto.getHours().get(0).getStart()).isEqualTo("00:00");
    assertThat(dto.getHours().get(0).getEnd()).isEqualTo("01:00");
    assertThat(dto.getHours().get(1).getStart()).isEqualTo("18:00");
    assertThat(dto.getHours().get(1).getEnd()).isEqualTo("23:00");
  }

  @Test
  @DisplayName("collapse - Uniform hours on a weekday subset derives daysOfWeek from booked dates")
  void buildScheduleDTOs_UniformWeekdaySubset_CollapsesWithDerivedDays() throws Exception {
    // Only Wed (2026-06-17) and Fri (2026-06-19), identical hours.
    // scheduleDays deliberately stale/wrong (MON,WED,FRI,SUN) — must be ignored.
    List<Integer> hours = List.of(0, 1, 18, 19, 20, 21, 22, 23);
    Map<String, List<Integer>> matrix = new LinkedHashMap<>();
    matrix.put("2026-06-17", hours);
    matrix.put("2026-06-19", hours);

    Schedule schedule =
        Schedule.builder()
            .type(Schedule.Type.DAYPART)
            .startDate(LocalDate.of(2026, 6, 16))
            .endDate(LocalDate.of(2026, 6, 20))
            .scheduleDays(
                List.of(
                    Schedule.Weekday.MONDAY,
                    Schedule.Weekday.WEDNESDAY,
                    Schedule.Weekday.FRIDAY,
                    Schedule.Weekday.SUNDAY))
            .bookingMatrix(matrix)
            .build();
    schedule.setId("collapse-subset");

    List<ScheduleDTO> result = invokePrivateMethod("buildScheduleDTOs", List.of(schedule));

    assertThat(result).hasSize(1);
    ScheduleDTO dto = result.get(0);
    assertThat(dto.getType()).isEqualTo("DEFAULT");
    // Derived from booking dates {WED=3, FRI=5}, NOT from scheduleDays
    assertThat(dto.getDaysOfWeek()).containsExactly(3, 5);
    assertThat(dto.getValidity().getStartDate()).isEqualTo("2026-06-17");
    assertThat(dto.getValidity().getEndDate()).isEqualTo("2026-06-19");
  }

  @Test
  @DisplayName("collapse - Full Ex-D pattern collapses to DEFAULT with split hour ranges")
  void buildScheduleDTOs_ExDPattern_CollapsesToDefault() throws Exception {
    // 2026-06-17 .. 2026-07-16, every Mon/Wed/Thu/Fri/Sat booked (Tue/Sun excluded), uniform hours.
    List<Integer> hours = List.of(0, 1, 5, 7, 9, 12, 15, 18, 19, 20, 21, 22, 23);
    Set<DayOfWeek> excluded = Set.of(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY);
    Map<String, List<Integer>> matrix = new LinkedHashMap<>();
    LocalDate d = LocalDate.of(2026, 6, 17);
    LocalDate end = LocalDate.of(2026, 7, 16);
    while (!d.isAfter(end)) {
      if (!excluded.contains(d.getDayOfWeek())) {
        matrix.put(d.toString(), hours);
      }
      d = d.plusDays(1);
    }

    Schedule schedule =
        Schedule.builder()
            .type(Schedule.Type.DAYPART)
            .startDate(LocalDate.of(2026, 6, 16))
            .endDate(LocalDate.of(2026, 7, 16))
            .bookingMatrix(matrix)
            .build();
    schedule.setId("collapse-exd");

    List<ScheduleDTO> result = invokePrivateMethod("buildScheduleDTOs", List.of(schedule));

    assertThat(result).hasSize(1);
    ScheduleDTO dto = result.get(0);
    assertThat(dto.getType()).isEqualTo("DEFAULT");
    assertThat(dto.getDaysOfWeek()).containsExactly(1, 3, 4, 5, 6); // MON,WED,THU,FRI,SAT
    assertThat(dto.getValidity().getStartDate()).isEqualTo("2026-06-17");
    assertThat(dto.getValidity().getEndDate()).isEqualTo("2026-07-16");
    // hours -> {00:00-01:00},{05:00},{07:00},{09:00},{12:00},{15:00},{18:00-23:00}
    assertThat(dto.getHours()).hasSize(7);
    assertThat(dto.getHours().get(0).getStart()).isEqualTo("00:00");
    assertThat(dto.getHours().get(0).getEnd()).isEqualTo("01:00");
    assertThat(dto.getHours().get(6).getStart()).isEqualTo("18:00");
    assertThat(dto.getHours().get(6).getEnd()).isEqualTo("23:00");
  }

  @Test
  @DisplayName("collapse - Non-uniform hours do NOT collapse (stays per-date CUSTOM)")
  void buildScheduleDTOs_NonUniformHours_DoesNotCollapse() throws Exception {
    Map<String, List<Integer>> matrix = new LinkedHashMap<>();
    matrix.put("2026-06-15", List.of(5, 7)); // differs
    matrix.put("2026-06-16", List.of(0, 1, 2, 3)); // differs

    Schedule schedule =
        Schedule.builder()
            .type(Schedule.Type.DAYPART)
            .startDate(LocalDate.of(2026, 6, 15))
            .endDate(LocalDate.of(2026, 6, 16))
            .bookingMatrix(matrix)
            .build();
    schedule.setId("no-collapse-nonuniform");

    List<ScheduleDTO> result = invokePrivateMethod("buildScheduleDTOs", List.of(schedule));

    assertThat(result).hasSize(2);
    assertThat(result).allMatch(dto -> "CUSTOM".equals(dto.getType()));
    assertThat(result.stream().map(ScheduleDTO::getDate))
        .containsExactlyInAnyOrder("2026-06-15", "2026-06-16");
  }

  @Test
  @DisplayName("collapse - A missing weekday occurrence (gap) prevents collapse")
  void buildScheduleDTOs_GapInRecurrence_DoesNotCollapse() throws Exception {
    // Mondays 2026-06-15 and 2026-06-29, skipping 2026-06-22 (also a Monday) -> gap.
    List<Integer> hours = List.of(0, 1, 18, 19, 20, 21, 22, 23);
    Map<String, List<Integer>> matrix = new LinkedHashMap<>();
    matrix.put("2026-06-15", hours);
    matrix.put("2026-06-29", hours);

    Schedule schedule =
        Schedule.builder()
            .type(Schedule.Type.DAYPART)
            .startDate(LocalDate.of(2026, 6, 15))
            .endDate(LocalDate.of(2026, 6, 29))
            .bookingMatrix(matrix)
            .build();
    schedule.setId("no-collapse-gap");

    List<ScheduleDTO> result = invokePrivateMethod("buildScheduleDTOs", List.of(schedule));

    assertThat(result).hasSize(2);
    assertThat(result).allMatch(dto -> "CUSTOM".equals(dto.getType()));
    assertThat(result.stream().map(ScheduleDTO::getDate))
        .containsExactlyInAnyOrder("2026-06-15", "2026-06-29");
  }

  @Test
  @DisplayName("collapse - A single booked date stays CUSTOM (no recurrence)")
  void buildScheduleDTOs_SingleDate_DoesNotCollapse() throws Exception {
    Map<String, List<Integer>> matrix = new LinkedHashMap<>();
    matrix.put("2026-06-17", List.of(0, 1, 18, 19, 20, 21, 22, 23));

    Schedule schedule =
        Schedule.builder()
            .type(Schedule.Type.DAYPART)
            .startDate(LocalDate.of(2026, 6, 17))
            .endDate(LocalDate.of(2026, 6, 17))
            .bookingMatrix(matrix)
            .build();
    schedule.setId("no-collapse-single");

    List<ScheduleDTO> result = invokePrivateMethod("buildScheduleDTOs", List.of(schedule));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getType()).isEqualTo("CUSTOM");
    assertThat(result.get(0).getDate()).isEqualTo("2026-06-17");
  }

  @Test
  @DisplayName("collapse - Two collapsible schedules on one inventory produce two DEFAULT entries")
  void buildScheduleDTOs_TwoCollapsibleSchedules_ProduceTwoDefaults() throws Exception {
    // S1: every day 2026-06-17..2026-07-17, hours [7..23]
    List<Integer> s1Hours = new ArrayList<>();
    for (int h = 7; h <= 23; h++) s1Hours.add(h);
    Map<String, List<Integer>> m1 = new LinkedHashMap<>();
    for (LocalDate d = LocalDate.of(2026, 6, 17);
        !d.isAfter(LocalDate.of(2026, 7, 17));
        d = d.plusDays(1)) {
      m1.put(d.toString(), s1Hours);
    }
    Schedule s1 =
        Schedule.builder()
            .type(Schedule.Type.DAYPART)
            .startDate(LocalDate.of(2026, 6, 17))
            .endDate(LocalDate.of(2026, 7, 17))
            .spotsPerLoop(1L)
            .spotsPerHour(10L)
            .bookingMatrix(m1)
            .build();
    s1.setId("s1");

    // S2: Sat/Sun only, 2026-06-20..2026-07-12, hours [6,8,10,12,14]
    List<Integer> s2Hours = List.of(6, 8, 10, 12, 14);
    Map<String, List<Integer>> m2 = new LinkedHashMap<>();
    for (LocalDate d = LocalDate.of(2026, 6, 20);
        !d.isAfter(LocalDate.of(2026, 7, 12));
        d = d.plusDays(1)) {
      if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
        m2.put(d.toString(), s2Hours);
      }
    }
    Schedule s2 =
        Schedule.builder()
            .type(Schedule.Type.DAYPART)
            .startDate(LocalDate.of(2026, 6, 20))
            .endDate(LocalDate.of(2026, 7, 12))
            .spotsPerLoop(1L)
            .spotsPerHour(10L)
            .bookingMatrix(m2)
            .build();
    s2.setId("s2");

    List<ScheduleDTO> result = invokePrivateMethod("buildScheduleDTOs", List.of(s1, s2));

    assertThat(result).hasSize(2);
    assertThat(result).allMatch(dto -> "DEFAULT".equals(dto.getType()));

    ScheduleDTO d1 = result.get(0);
    assertThat(d1.getDaysOfWeek()).containsExactly(1, 2, 3, 4, 5, 6, 7);
    assertThat(d1.getHours()).hasSize(1);
    assertThat(d1.getHours().get(0).getStart()).isEqualTo("07:00");
    assertThat(d1.getHours().get(0).getEnd()).isEqualTo("23:00");
    assertThat(d1.getSpotsPerLoop()).isEqualTo(1L);
    assertThat(d1.getSpotsPerHour()).isEqualTo(10L);

    ScheduleDTO d2 = result.get(1);
    assertThat(d2.getDaysOfWeek()).containsExactly(6, 7);
    assertThat(d2.getHours()).hasSize(5); // 6,8,10,12,14 isolated
    assertThat(d2.getValidity().getStartDate()).isEqualTo("2026-06-20");
    assertThat(d2.getValidity().getEndDate()).isEqualTo("2026-07-12");
  }

  @Test
  @DisplayName("spots - Per-schedule spots are attached to CUSTOM and DEFAULT entries")
  void buildScheduleDTOs_AttachesPerScheduleSpots() throws Exception {
    Map<String, List<Integer>> matrix = new LinkedHashMap<>();
    matrix.put("2026-06-15", List.of(5, 7)); // non-uniform -> CUSTOM
    matrix.put("2026-06-16", List.of(0, 1, 2));

    Schedule schedule =
        Schedule.builder()
            .type(Schedule.Type.DAYPART)
            .startDate(LocalDate.of(2026, 6, 15))
            .endDate(LocalDate.of(2026, 6, 16))
            .spotsPerLoop(3L)
            .spotsPerHour(20L)
            .bookingMatrix(matrix)
            .build();
    schedule.setId("spots-1");

    List<ScheduleDTO> result = invokePrivateMethod("buildScheduleDTOs", List.of(schedule));

    assertThat(result).hasSize(2);
    assertThat(result).allMatch(dto -> "CUSTOM".equals(dto.getType()));
    assertThat(result).allMatch(dto -> dto.getSpotsPerLoop() == 3L);
    assertThat(result).allMatch(dto -> dto.getSpotsPerHour() == 20L);
  }

  @Test
  @DisplayName(
      "collapse - Three schedules (2 collapsible + 1 non-uniform) yield 2 DEFAULT + 4 CUSTOM")
  void buildScheduleDTOs_ThreeSchedulesMixed_ProduceTwoDefaultsAndFourCustom() throws Exception {
    // S1: every day 06-17..07-17, uniform [0,1,18,19,20,21,22,23] -> DEFAULT
    List<Integer> s1Hours = List.of(0, 1, 18, 19, 20, 21, 22, 23);
    Map<String, List<Integer>> m1 = new LinkedHashMap<>();
    for (LocalDate d = LocalDate.of(2026, 6, 17);
        !d.isAfter(LocalDate.of(2026, 7, 17));
        d = d.plusDays(1)) {
      m1.put(d.toString(), s1Hours);
    }
    Schedule s1 =
        Schedule.builder()
            .type(Schedule.Type.DAYPART)
            .startDate(LocalDate.of(2026, 6, 17))
            .endDate(LocalDate.of(2026, 7, 17))
            .spotsPerLoop(1L)
            .spotsPerHour(10L)
            .bookingMatrix(m1)
            .build();
    s1.setId("s1");

    // S2: every day 06-17..07-17, uniform [0,1,2,4,5,7,9,11,13,15,17,19,22,23] -> DEFAULT
    List<Integer> s2Hours = List.of(0, 1, 2, 4, 5, 7, 9, 11, 13, 15, 17, 19, 22, 23);
    Map<String, List<Integer>> m2 = new LinkedHashMap<>();
    for (LocalDate d = LocalDate.of(2026, 6, 17);
        !d.isAfter(LocalDate.of(2026, 7, 17));
        d = d.plusDays(1)) {
      m2.put(d.toString(), s2Hours);
    }
    Schedule s2 =
        Schedule.builder()
            .type(Schedule.Type.DAYPART)
            .startDate(LocalDate.of(2026, 6, 17))
            .endDate(LocalDate.of(2026, 7, 17))
            .spotsPerLoop(1L)
            .spotsPerHour(10L)
            .bookingMatrix(m2)
            .build();
    s2.setId("s2");

    // S3: 4 dates, each a DIFFERENT hour set -> non-uniform -> 4 CUSTOM
    Map<String, List<Integer>> m3 = new LinkedHashMap<>();
    m3.put("2026-06-17", List.of(0, 6, 12, 18));
    m3.put("2026-06-18", List.of(1, 5, 7, 11, 13, 17, 19, 23));
    m3.put("2026-06-19", List.of(2, 4, 8, 10, 14, 16, 20, 22));
    m3.put("2026-06-20", List.of(3, 9, 15, 21));
    Schedule s3 =
        Schedule.builder()
            .type(Schedule.Type.DAYPART)
            .startDate(LocalDate.of(2026, 6, 17))
            .endDate(LocalDate.of(2026, 6, 20))
            .spotsPerLoop(1L)
            .spotsPerHour(10L)
            .bookingMatrix(m3)
            .build();
    s3.setId("s3");

    List<ScheduleDTO> result = invokePrivateMethod("buildScheduleDTOs", List.of(s1, s2, s3));

    assertThat(result).hasSize(6);
    assertThat(result.stream().filter(d -> "DEFAULT".equals(d.getType())).count()).isEqualTo(2);
    assertThat(result.stream().filter(d -> "CUSTOM".equals(d.getType())).count()).isEqualTo(4);
    // All entries carry per-schedule spots
    assertThat(result).allMatch(d -> d.getSpotsPerLoop() == 1L && d.getSpotsPerHour() == 10L);

    // Order preserved: S1 DEFAULT, S2 DEFAULT, then S3's 4 CUSTOM (date-sorted)
    ScheduleDTO d1 = result.get(0);
    assertThat(d1.getType()).isEqualTo("DEFAULT");
    assertThat(d1.getDaysOfWeek()).containsExactly(1, 2, 3, 4, 5, 6, 7);
    assertThat(d1.getHours()).hasSize(2); // {00:00-01:00},{18:00-23:00}

    ScheduleDTO d2 = result.get(1);
    assertThat(d2.getType()).isEqualTo("DEFAULT");
    assertThat(d2.getDaysOfWeek()).containsExactly(1, 2, 3, 4, 5, 6, 7);
    assertThat(d2.getHours()).hasSize(10); // 0-2,4-5,22-23 merge; 7,9,11,13,15,17,19 isolated

    List<ScheduleDTO> custom = result.subList(2, 6);
    assertThat(custom).allMatch(d -> "CUSTOM".equals(d.getType()));
    assertThat(custom.stream().map(ScheduleDTO::getDate))
        .containsExactly("2026-06-17", "2026-06-18", "2026-06-19", "2026-06-20");
    // 06-17 has four isolated hours -> four single-hour ranges
    assertThat(custom.get(0).getHours()).hasSize(4);
    assertThat(custom.get(0).getHours().get(0).getStart()).isEqualTo("00:00");
    assertThat(custom.get(0).getHours().get(0).getEnd()).isEqualTo("00:00");
  }

  // ==================== Helper Methods ====================

  /**
   * Invokes a private method on MWAdsService using reflection for testing purposes
   *
   * @param methodName Name of the private method to invoke
   * @param args Arguments to pass to the method
   * @return Result of the method invocation
   */
  @SuppressWarnings("unchecked")
  private <T> T invokePrivateMethod(String methodName, Object... args) throws Exception {
    // Try to find the method by iterating through all declared methods
    for (Method method : MWAdsService.class.getDeclaredMethods()) {
      if (method.getName().equals(methodName) && method.getParameterCount() == args.length) {
        method.setAccessible(true);
        try {
          return (T) method.invoke(mwAdsService, args);
        } catch (IllegalArgumentException | java.lang.reflect.InvocationTargetException e) {
          // If it's an InvocationTargetException, check if the cause is NPE from our code
          if (e instanceof java.lang.reflect.InvocationTargetException
              && e.getCause() instanceof NullPointerException) {
            // This is an actual NPE from the method being tested, not from reflection
            throw (NullPointerException) e.getCause();
          }
          // Try next method with same name but different parameters
          continue;
        }
      }
    }
    throw new NoSuchMethodException(
        "Could not find method " + methodName + " with " + args.length + " parameters");
  }
}
