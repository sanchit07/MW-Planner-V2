package com.mw.planner.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.config.MwPlannerProperties;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.Inventory;
import com.mw.planner.domain.Schedule;
import com.mw.planner.dto.MeasureInventoryDTO;
import com.mw.planner.dto.MeasureReachFrequencyRequestDTO;
import com.mw.planner.dto.MeasureReachFrequencyResponseDTO;
import com.mw.planner.exception.inventory.InventoryMeasureApiException;
import com.mw.planner.repository.ScheduleRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class MwMeasureServiceTest {

  @Mock private MwPlannerProperties mwPlannerProperties;
  @Mock private RestTemplate restTemplate;
  @Mock private ObjectMapper objectMapper;
  @Mock private ScheduleRepository scheduleRepository;
  @Mock private SecurityContext securityContext;
  @Mock private Authentication authentication;

  @InjectMocks private MwMeasureService mwMeasureService;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.setContext(securityContext);
    when(securityContext.getAuthentication()).thenReturn(authentication);
    when(authentication.getCredentials()).thenReturn("test-bearer-token");

    // Setup mock for MwPlannerProperties
    MwPlannerProperties.Measure mockMeasure = mock(MwPlannerProperties.Measure.class);
    when(mwPlannerProperties.getMeasure()).thenReturn(mockMeasure);
    lenient()
        .when(mockMeasure.getFullReachAndFrequencyUrl())
        .thenReturn("https://test-url.com/api");
  }

  @Test
  void testGetReachAndFrequency_Success() throws JsonProcessingException {
    // Given
    MeasureReachFrequencyRequestDTO request = createTestRequest();
    MeasureReachFrequencyResponseDTO expectedResponse = createTestResponse();

    when(objectMapper.writeValueAsString(any(MeasureReachFrequencyRequestDTO.class)))
        .thenReturn(
            "{\"inventories\":[{\"referenceId\":\"USA-NEW-D-00000-03420\",\"type\":\"billboard\",\"spotsPerHour\":36}],\"duration\":30}");
    when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(MeasureReachFrequencyResponseDTO.class)))
        .thenReturn(new ResponseEntity<>(expectedResponse, HttpStatus.OK));

    // When
    MeasureReachFrequencyResponseDTO result = mwMeasureService.getReachAndFrequency(request);

    // Then
    assertNotNull(result);
    assertEquals(expectedResponse.getReach(), result.getReach());
    assertEquals(expectedResponse.getFrequency(), result.getFrequency());
    assertEquals(expectedResponse.getImpressions(), result.getImpressions());
    assertEquals(expectedResponse.getStatus(), result.getStatus());

    verify(restTemplate)
        .exchange(
            eq("https://test-url.com/api"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(MeasureReachFrequencyResponseDTO.class));
  }

  @Test
  void testGetReachAndFrequency_NoToken() {
    // Given
    when(authentication.getCredentials()).thenReturn(null);
    MeasureReachFrequencyRequestDTO request = createTestRequest();

    // When & Then
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> mwMeasureService.getReachAndFrequency(request));

    assertEquals("No authentication token found in security context", exception.getMessage());

    // Verify that the restTemplate was not called since we threw an exception before
    verifyNoInteractions(restTemplate);
  }

  @Test
  void testGetReachAndFrequency_HttpClientErrorException() throws JsonProcessingException {
    when(objectMapper.writeValueAsString(any(MeasureReachFrequencyRequestDTO.class)))
        .thenReturn("{}");
    when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(MeasureReachFrequencyResponseDTO.class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

    MeasureReachFrequencyRequestDTO request = createTestRequest();

    InventoryMeasureApiException exception =
        assertThrows(
            InventoryMeasureApiException.class,
            () -> mwMeasureService.getReachAndFrequency(request));

    assertTrue(exception.getMessage().contains("Failed to get reach and frequency data"));
  }

  @Test
  void testGetReachAndFrequency_HttpServerErrorException() throws JsonProcessingException {
    when(objectMapper.writeValueAsString(any(MeasureReachFrequencyRequestDTO.class)))
        .thenReturn("{}");
    when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(MeasureReachFrequencyResponseDTO.class)))
        .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server error"));

    MeasureReachFrequencyRequestDTO request = createTestRequest();

    InventoryMeasureApiException exception =
        assertThrows(
            InventoryMeasureApiException.class,
            () -> mwMeasureService.getReachAndFrequency(request));

    assertTrue(exception.getMessage().contains("MW Influence API server error"));
  }

  @Test
  void testGetReachAndFrequency_ResourceAccessException() throws JsonProcessingException {
    when(objectMapper.writeValueAsString(any(MeasureReachFrequencyRequestDTO.class)))
        .thenReturn("{}");
    when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(MeasureReachFrequencyResponseDTO.class)))
        .thenThrow(new ResourceAccessException("Connection timeout"));

    MeasureReachFrequencyRequestDTO request = createTestRequest();

    InventoryMeasureApiException exception =
        assertThrows(
            InventoryMeasureApiException.class,
            () -> mwMeasureService.getReachAndFrequency(request));

    assertTrue(exception.getMessage().contains("Timeout or connection error"));
  }

  // ---------------------------------------------------------------------------
  // getReachAndFrequencyByCampaignInventorySchedules — conversion logic tests
  // ---------------------------------------------------------------------------

  @Test
  void testConversion_LoopOnly_NoDaypartsInPayload() throws JsonProcessingException {
    // Given
    Schedule loopSchedule =
        Schedule.builder()
            .startDate(java.time.LocalDate.of(2026, 1, 1))
            .endDate(java.time.LocalDate.of(2026, 1, 31))
            .type(Schedule.Type.LOOP)
            .spotsPerHour(10L)
            .bookingMatrix(Map.of("2026-01-01", List.of(8, 9, 10)))
            .build();
    loopSchedule.setId("sch-loop-1");

    CampaignInventorySchedules cis = buildCis("inv-1", List.of("sch-loop-1"));
    Inventory inventory = buildInventory("inv-1", "REF-001");

    when(scheduleRepository.findAllById(List.of("sch-loop-1"))).thenReturn(List.of(loopSchedule));
    setupMeasureApiMock();

    // When
    mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
        30, List.of(cis), Map.of("inv-1", inventory));

    // Then — capture the request sent to the API
    ArgumentCaptor<MeasureReachFrequencyRequestDTO> captor =
        ArgumentCaptor.forClass(MeasureReachFrequencyRequestDTO.class);
    verify(objectMapper).writeValueAsString(captor.capture());

    List<MeasureInventoryDTO> inventories = captor.getValue().getInventories();
    assertEquals(1, inventories.size());
    assertNull(inventories.get(0).getDayparts(), "LOOP schedule must not have dayparts");
    assertEquals("REF-001", inventories.get(0).getReferenceId());
  }

  @Test
  void testConversion_DaypartOnly_DaypartsInPayload() throws JsonProcessingException {
    // Given
    Schedule daypartSchedule =
        Schedule.builder()
            .startDate(java.time.LocalDate.of(2026, 1, 1))
            .endDate(java.time.LocalDate.of(2026, 1, 31))
            .type(Schedule.Type.DAYPART)
            .spotsPerHour(5L)
            .bookingMatrix(Map.of("2026-01-01", List.of(8, 9, 10), "2026-01-02", List.of(14, 15)))
            .build();
    daypartSchedule.setId("sch-dp-1");

    CampaignInventorySchedules cis = buildCis("inv-1", List.of("sch-dp-1"));
    Inventory inventory = buildInventory("inv-1", "REF-001");

    when(scheduleRepository.findAllById(List.of("sch-dp-1"))).thenReturn(List.of(daypartSchedule));
    setupMeasureApiMock();

    // When
    mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
        30, List.of(cis), Map.of("inv-1", inventory));

    // Then
    ArgumentCaptor<MeasureReachFrequencyRequestDTO> captor =
        ArgumentCaptor.forClass(MeasureReachFrequencyRequestDTO.class);
    verify(objectMapper).writeValueAsString(captor.capture());

    List<MeasureInventoryDTO> inventories = captor.getValue().getInventories();
    assertEquals(1, inventories.size());
    assertNotNull(inventories.get(0).getDayparts(), "DAYPART schedule must have dayparts");
    assertFalse(inventories.get(0).getDayparts().isEmpty());

    // Verify scheduledTime is populated (not null)
    inventories
        .get(0)
        .getDayparts()
        .forEach(dp -> assertNotNull(dp.getScheduledTime(), "scheduledTime must be populated"));
  }

  @Test
  void testConversion_MixedTypes_InventoryAppearseTwiceInPayload() throws JsonProcessingException {
    // Given
    Schedule loopSchedule =
        Schedule.builder()
            .startDate(java.time.LocalDate.of(2026, 1, 1))
            .endDate(java.time.LocalDate.of(2026, 1, 31))
            .type(Schedule.Type.LOOP)
            .spotsPerHour(10L)
            .build();
    loopSchedule.setId("sch-loop-1");

    Schedule daypartSchedule =
        Schedule.builder()
            .startDate(java.time.LocalDate.of(2026, 1, 1))
            .endDate(java.time.LocalDate.of(2026, 1, 31))
            .type(Schedule.Type.DAYPART)
            .spotsPerHour(5L)
            .bookingMatrix(Map.of("2026-01-01", List.of(8, 9, 10)))
            .build();
    daypartSchedule.setId("sch-dp-1");

    CampaignInventorySchedules cis = buildCis("inv-1", List.of("sch-loop-1", "sch-dp-1"));
    Inventory inventory = buildInventory("inv-1", "REF-001");

    when(scheduleRepository.findAllById(List.of("sch-loop-1", "sch-dp-1")))
        .thenReturn(List.of(loopSchedule, daypartSchedule));
    setupMeasureApiMock();

    // When
    mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
        30, List.of(cis), Map.of("inv-1", inventory));

    // Then — same inventory must appear twice
    ArgumentCaptor<MeasureReachFrequencyRequestDTO> captor =
        ArgumentCaptor.forClass(MeasureReachFrequencyRequestDTO.class);
    verify(objectMapper).writeValueAsString(captor.capture());

    List<MeasureInventoryDTO> inventories = captor.getValue().getInventories();
    assertEquals(2, inventories.size(), "Mixed types must produce 2 DTOs for the same inventory");

    long loopCount = inventories.stream().filter(i -> i.getDayparts() == null).count();
    long daypartCount = inventories.stream().filter(i -> i.getDayparts() != null).count();
    assertEquals(1, loopCount, "Exactly 1 LOOP DTO (no dayparts)");
    assertEquals(1, daypartCount, "Exactly 1 DAYPART DTO (with dayparts)");
  }

  @Test
  void testConversion_NullScheduleType_TreatedAsLoop() throws JsonProcessingException {
    // Given
    Schedule nullTypeSchedule =
        Schedule.builder()
            .startDate(java.time.LocalDate.of(2026, 1, 1))
            .endDate(java.time.LocalDate.of(2026, 1, 31))
            .type(null)
            .spotsPerHour(8L)
            .bookingMatrix(Map.of("2026-01-01", List.of(8, 9)))
            .build();
    nullTypeSchedule.setId("sch-null-1");

    CampaignInventorySchedules cis = buildCis("inv-1", List.of("sch-null-1"));
    Inventory inventory = buildInventory("inv-1", "REF-001");

    when(scheduleRepository.findAllById(List.of("sch-null-1")))
        .thenReturn(List.of(nullTypeSchedule));
    setupMeasureApiMock();

    // When
    mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
        30, List.of(cis), Map.of("inv-1", inventory));

    // Then
    ArgumentCaptor<MeasureReachFrequencyRequestDTO> captor =
        ArgumentCaptor.forClass(MeasureReachFrequencyRequestDTO.class);
    verify(objectMapper).writeValueAsString(captor.capture());

    List<MeasureInventoryDTO> inventories = captor.getValue().getInventories();
    assertEquals(1, inventories.size());
    assertNull(inventories.get(0).getDayparts(), "Null type must be treated as LOOP (no dayparts)");
  }

  @Test
  void testConversion_NoScheduleIds_SingleDtoWithoutDayparts() throws JsonProcessingException {
    // Given — CIS with no scheduleIds
    CampaignInventorySchedules cis = buildCis("inv-1", null);
    Inventory inventory = buildInventory("inv-1", "REF-001");

    setupMeasureApiMock();

    // When
    mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
        30, List.of(cis), Map.of("inv-1", inventory));

    // Then
    ArgumentCaptor<MeasureReachFrequencyRequestDTO> captor =
        ArgumentCaptor.forClass(MeasureReachFrequencyRequestDTO.class);
    verify(objectMapper).writeValueAsString(captor.capture());

    List<MeasureInventoryDTO> inventories = captor.getValue().getInventories();
    assertEquals(1, inventories.size());
    assertNull(inventories.get(0).getDayparts(), "No schedules must produce DTO without dayparts");
    verifyNoInteractions(scheduleRepository);
  }

  @Test
  void testConversion_MultipleDaypartSchedules_BookingMatrixMerged()
      throws JsonProcessingException {
    // Given — two DAYPART schedules with overlapping hours on the same date
    Schedule dp1 =
        Schedule.builder()
            .startDate(java.time.LocalDate.of(2026, 1, 1))
            .endDate(java.time.LocalDate.of(2026, 1, 31))
            .type(Schedule.Type.DAYPART)
            .spotsPerHour(5L)
            .bookingMatrix(Map.of("2026-01-01", List.of(8, 9, 10)))
            .build();
    dp1.setId("sch-dp-1");

    Schedule dp2 =
        Schedule.builder()
            .startDate(java.time.LocalDate.of(2026, 1, 1))
            .endDate(java.time.LocalDate.of(2026, 1, 31))
            .type(Schedule.Type.DAYPART)
            .spotsPerHour(5L)
            .bookingMatrix(Map.of("2026-01-01", List.of(9, 10, 11))) // 9,10 overlap with dp1
            .build();
    dp2.setId("sch-dp-2");

    CampaignInventorySchedules cis = buildCis("inv-1", List.of("sch-dp-1", "sch-dp-2"));
    Inventory inventory = buildInventory("inv-1", "REF-001");

    when(scheduleRepository.findAllById(List.of("sch-dp-1", "sch-dp-2")))
        .thenReturn(List.of(dp1, dp2));
    setupMeasureApiMock();

    // When
    mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
        30, List.of(cis), Map.of("inv-1", inventory));

    // Then
    ArgumentCaptor<MeasureReachFrequencyRequestDTO> captor =
        ArgumentCaptor.forClass(MeasureReachFrequencyRequestDTO.class);
    verify(objectMapper).writeValueAsString(captor.capture());

    List<MeasureInventoryDTO> inventories = captor.getValue().getInventories();
    assertEquals(1, inventories.size());

    MeasureInventoryDTO.Dayparts daypart = inventories.get(0).getDayparts().get(0);
    assertEquals("2026-01-01", daypart.getScheduledDate());
    // Merged unique hours: 8,9,10,11 → 4 entries (deduped)
    assertEquals(4, daypart.getScheduledTime().size());
    assertEquals(List.of("08", "09", "10", "11"), daypart.getScheduledTime());
    // spotsPerHour is inventory-level — same value on all schedules for same inventory
    assertEquals(5, inventories.get(0).getSpotsPerHour());
  }

  @Test
  void testConversion_LoopScheduleDatesDifferFromCampaign_DaypartsWithScheduledDateOnly()
      throws JsonProcessingException {
    // LOOP schedule covers only a subset of the campaign period → scheduledDate-only dayparts
    Schedule loopSchedule =
        Schedule.builder()
            .startDate(java.time.LocalDate.of(2026, 1, 5))
            .endDate(java.time.LocalDate.of(2026, 1, 15))
            .type(Schedule.Type.LOOP)
            .spotsPerHour(10L)
            .bookingMatrix(
                Map.of(
                    "2026-01-05", List.of(8, 9),
                    "2026-01-06", List.of(8, 9),
                    "2026-01-07", List.of(8, 9)))
            .build();
    loopSchedule.setId("sch-loop-1");

    CampaignInventorySchedules cis = buildCis("inv-1", List.of("sch-loop-1"));
    Inventory inventory = buildInventory("inv-1", "REF-001");

    when(scheduleRepository.findAllById(List.of("sch-loop-1"))).thenReturn(List.of(loopSchedule));
    setupMeasureApiMock();

    // When — pass campaign dates that differ from schedule dates
    mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
        30,
        List.of(cis),
        Map.of("inv-1", inventory),
        java.time.LocalDate.of(2026, 1, 1),
        java.time.LocalDate.of(2026, 1, 31));

    ArgumentCaptor<MeasureReachFrequencyRequestDTO> captor =
        ArgumentCaptor.forClass(MeasureReachFrequencyRequestDTO.class);
    verify(objectMapper).writeValueAsString(captor.capture());

    List<MeasureInventoryDTO> inventories = captor.getValue().getInventories();
    assertEquals(1, inventories.size());
    assertNotNull(inventories.get(0).getDayparts(), "LOOP with subset dates must have dayparts");
    // scheduledDate only — no scheduledTime
    inventories
        .get(0)
        .getDayparts()
        .forEach(
            dp -> assertNull(dp.getScheduledTime(), "LOOP dayparts must not have scheduledTime"));
    // exactly the 3 dates from bookingMatrix
    assertEquals(3, inventories.get(0).getDayparts().size());
    assertEquals(
        List.of("2026-01-05", "2026-01-06", "2026-01-07"),
        inventories.get(0).getDayparts().stream()
            .map(MeasureInventoryDTO.Dayparts::getScheduledDate)
            .toList());
  }

  @Test
  void testConversion_LoopScheduleDatesMatchCampaign_NoDayparts() throws JsonProcessingException {
    // LOOP schedule covers exact same period as campaign → no dayparts
    Schedule loopSchedule =
        Schedule.builder()
            .startDate(java.time.LocalDate.of(2026, 1, 1))
            .endDate(java.time.LocalDate.of(2026, 1, 31))
            .type(Schedule.Type.LOOP)
            .spotsPerHour(10L)
            .bookingMatrix(Map.of("2026-01-01", List.of(8, 9, 10)))
            .build();
    loopSchedule.setId("sch-loop-1");

    CampaignInventorySchedules cis = buildCis("inv-1", List.of("sch-loop-1"));
    Inventory inventory = buildInventory("inv-1", "REF-001");

    when(scheduleRepository.findAllById(List.of("sch-loop-1"))).thenReturn(List.of(loopSchedule));
    setupMeasureApiMock();

    // When — campaign dates match schedule dates exactly
    mwMeasureService.getReachAndFrequencyByCampaignInventorySchedules(
        30,
        List.of(cis),
        Map.of("inv-1", inventory),
        java.time.LocalDate.of(2026, 1, 1),
        java.time.LocalDate.of(2026, 1, 31));

    ArgumentCaptor<MeasureReachFrequencyRequestDTO> captor =
        ArgumentCaptor.forClass(MeasureReachFrequencyRequestDTO.class);
    verify(objectMapper).writeValueAsString(captor.capture());

    List<MeasureInventoryDTO> inventories = captor.getValue().getInventories();
    assertEquals(1, inventories.size());
    assertNull(
        inventories.get(0).getDayparts(), "LOOP matching campaign dates must not have dayparts");
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private CampaignInventorySchedules buildCis(String inventoryId, List<String> scheduleIds) {
    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId("campaign-1")
            .mediaOwnerId("mo-1")
            .inventoryId(inventoryId)
            .build();
    cis.setScheduleIds(scheduleIds);
    return cis;
  }

  private Inventory buildInventory(String id, String referenceId) {
    Inventory inventory = new Inventory();
    inventory.setId(id);
    inventory.setReferenceId(referenceId);
    return inventory;
  }

  private void setupMeasureApiMock() throws JsonProcessingException {
    when(objectMapper.writeValueAsString(any(MeasureReachFrequencyRequestDTO.class)))
        .thenReturn("{}");
    when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(MeasureReachFrequencyResponseDTO.class)))
        .thenReturn(new ResponseEntity<>(createTestResponse(), HttpStatus.OK));
  }

  private MeasureReachFrequencyRequestDTO createTestRequest() {
    MeasureInventoryDTO inventory =
        MeasureInventoryDTO.builder()
            .referenceId("USA-NEW-D-00000-03420")
            .type("billboard")
            .spotsPerHour(36)
            .build();

    return MeasureReachFrequencyRequestDTO.builder()
        .inventories(Arrays.asList(inventory))
        .duration(30)
        .build();
  }

  private MeasureReachFrequencyResponseDTO createTestResponse() {
    return MeasureReachFrequencyResponseDTO.builder()
        .reach(447747L)
        .frequency(17.900019430615952)
        .impressions(8014680L)
        .status("Ok")
        .build();
  }
}
