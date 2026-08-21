package com.mw.planner.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.dto.AgencyRequestDTO;
import com.mw.planner.dto.AgencyResponseDTO;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.exception.GlobalExceptionHandler;
import com.mw.planner.service.AgencyService;
import com.mw.planner.service.MessageService;
import com.mw.planner.service.MetricsService;
import com.mw.planner.service.UserService;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AgencyControllerTest {

  @Mock private AgencyService agencyService;
  @Mock private UserService userService;
  @Mock private MessageService messageService;
  @Mock private MetricsService metricsService;

  @InjectMocks private AgencyController agencyController;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;
  private AgencyResponseDTO testAgencyResponse;
  private AgencyRequestDTO testAgencyRequest;
  private IamUserContext testIamUserContext;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(agencyController)
            .setControllerAdvice(
                new GlobalExceptionHandler(messageService, metricsService, userService))
            .build();
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();

    testAgencyResponse = createTestAgencyResponse();
    testAgencyRequest = createTestAgencyRequest();
    testIamUserContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .locale(Locale.ENGLISH)
            .build();

    // Mock the iamUserContextService.getIamUserContext() call that GlobalExceptionHandler makes
    when(userService.getIamUserContext()).thenReturn(testIamUserContext);
  }

  @AfterEach
  void tearDown() {
    // Reset mocks to clear any interactions for next test
    org.mockito.Mockito.reset(agencyService, userService, messageService, metricsService);
  }

  // ========== Create Agency Tests ==========

  @Test
  void createAgency_WithValidData_ShouldReturnCreatedAgency() throws Exception {
    // Given
    when(agencyService.createAgency(any(AgencyRequestDTO.class))).thenReturn(testAgencyResponse);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/agencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testAgencyRequest)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value("agency123"))
        .andExpect(jsonPath("$.data.name").value("Test Agency"))
        .andExpect(jsonPath("$.data.mediaOwnerId").value("MO_001"))
        .andExpect(jsonPath("$.data.activated").value(true));
  }

  @Test
  void createAgency_WithInvalidData_ShouldReturnBadRequest() throws Exception {
    // Given
    AgencyRequestDTO invalidRequest = new AgencyRequestDTO();
    invalidRequest.setName(""); // Empty name should fail validation

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/agencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createAgency_WithMissingRequiredFields_ShouldReturnBadRequest() throws Exception {
    // Given
    AgencyRequestDTO incompleteRequest = new AgencyRequestDTO();
    // Missing required name field

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/agencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(incompleteRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createAgency_WithInvalidEmail_ShouldReturnBadRequest() throws Exception {
    // Given
    AgencyRequestDTO requestWithInvalidEmail = createTestAgencyRequest();
    requestWithInvalidEmail.setCompanyEmail("invalid-email");

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/agencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestWithInvalidEmail)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createAgency_WithInvalidCountryId_ShouldReturnBadRequest() throws Exception {
    // Given
    AgencyRequestDTO requestWithInvalidCountryId = createTestAgencyRequest();
    requestWithInvalidCountryId.setCountryId(""); // Empty country ID

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/agencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestWithInvalidCountryId)))
        .andExpect(status().isBadRequest());
  }

  // ========== Get Agency by ID Tests ==========

  @Test
  void getAgencyById_WithValidId_ShouldReturnAgency() throws Exception {
    // Given
    when(agencyService.getAgencyById("agency123")).thenReturn(testAgencyResponse);

    // When & Then
    mockMvc
        .perform(get("/api/v1/agencies/agency123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value("agency123"))
        .andExpect(jsonPath("$.data.name").value("Test Agency"))
        .andExpect(jsonPath("$.data.mediaOwnerId").value("MO_001"))
        .andExpect(jsonPath("$.data.activated").value(true))
        .andExpect(jsonPath("$.data.companyEmail").value("info@testagency.com"))
        .andExpect(jsonPath("$.data.countryId").value("US"))
        .andExpect(jsonPath("$.data.countryName").value("United States"));
  }

  @Test
  void getAgencyById_WithNonExistentId_ShouldReturnNotFound() throws Exception {
    // Given
    when(agencyService.getAgencyById("nonexistent"))
        .thenThrow(new com.mw.planner.exception.agency.AgencyNotFoundException("nonexistent"));

    // When & Then
    mockMvc.perform(get("/api/v1/agencies/nonexistent")).andExpect(status().isNotFound());
  }

  @Test
  void getAgencyById_WithEmptyId_ShouldReturnNotFound() throws Exception {
    // When & Then
    mockMvc.perform(get("/api/v1/agencies/")).andExpect(status().isNotFound());
  }

  // ========== Update Agency Tests ==========

  @Test
  void updateAgency_WithValidData_ShouldReturnUpdatedAgency() throws Exception {
    // Given
    AgencyResponseDTO updatedResponse = createTestAgencyResponse();
    updatedResponse.setName("Updated Agency Name");
    updatedResponse.setMediaOwnerId("MO_002");

    when(agencyService.updateAgency(eq("agency123"), any(AgencyRequestDTO.class)))
        .thenReturn(updatedResponse);

    // When & Then
    mockMvc
        .perform(
            put("/api/v1/agencies/agency123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testAgencyRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value("agency123"))
        .andExpect(jsonPath("$.data.name").value("Updated Agency Name"))
        .andExpect(jsonPath("$.data.mediaOwnerId").value("MO_002"));
  }

  @Test
  void updateAgency_WithNonExistentId_ShouldReturnNotFound() throws Exception {
    // Given
    when(agencyService.updateAgency(eq("nonexistent"), any(AgencyRequestDTO.class)))
        .thenThrow(new com.mw.planner.exception.agency.AgencyNotFoundException("nonexistent"));

    // When & Then
    mockMvc
        .perform(
            put("/api/v1/agencies/nonexistent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testAgencyRequest)))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateAgency_WithInvalidData_ShouldReturnBadRequest() throws Exception {
    // Given
    AgencyRequestDTO invalidRequest = new AgencyRequestDTO();
    invalidRequest.setName(""); // Empty name should fail validation

    // When & Then
    mockMvc
        .perform(
            put("/api/v1/agencies/agency123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateAgency_WithDuplicateName_ShouldReturnConflict() throws Exception {
    // Given
    when(agencyService.updateAgency(eq("agency123"), any(AgencyRequestDTO.class)))
        .thenThrow(new com.mw.planner.exception.agency.AgencyAlreadyExistsException("Test Agency"));

    // When & Then
    mockMvc
        .perform(
            put("/api/v1/agencies/agency123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testAgencyRequest)))
        .andExpect(status().isConflict());
  }

  // ========== Get All Agencies Tests ==========

  @Test
  void getAllAgencies_WithDefaultPagination_ShouldReturnPagedAgencies() throws Exception {
    // Given
    Page<AgencyResponseDTO> agencyPage =
        new PageImpl<>(Collections.singletonList(testAgencyResponse), PageRequest.of(0, 10), 1);
    when(agencyService.getAllAgencies(any(Pageable.class), any())).thenReturn(agencyPage);

    // When & Then
    mockMvc
        .perform(get("/api/v1/agencies"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content[0].id").value("agency123"))
        .andExpect(jsonPath("$.data.content[0].name").value("Test Agency"))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.totalPages").value(1));
  }

  @Test
  void getAllAgencies_WithCustomPagination_ShouldReturnPagedAgencies() throws Exception {
    // Given
    Page<AgencyResponseDTO> agencyPage =
        new PageImpl<>(Collections.singletonList(testAgencyResponse), PageRequest.of(0, 10), 1);
    when(agencyService.getAllAgencies(any(Pageable.class), any())).thenReturn(agencyPage);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/agencies")
                .param("page", "0")
                .param("size", "5")
                .param("sortBy", "name")
                .param("sortDir", "asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content[0].id").value("agency123"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void getAllAgencies_WithEmptyResult_ShouldReturnEmptyPage() throws Exception {
    // Given
    Page<AgencyResponseDTO> emptyPage =
        new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
    when(agencyService.getAllAgencies(any(Pageable.class), any())).thenReturn(emptyPage);

    // When & Then
    mockMvc
        .perform(get("/api/v1/agencies"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content").isEmpty())
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  @Test
  void getAllAgencies_WithInvalidPaginationParams_ShouldReturnBadRequest() throws Exception {
    // When & Then - Invalid pagination parameters should return 400 Bad Request
    mockMvc
        .perform(
            get("/api/v1/agencies")
                .param("page", "-1") // Invalid page number
                .param("size", "0")) // Invalid size
        .andExpect(status().isBadRequest());
  }

  @Test
  void getAllAgencies_WithLargePageSize_ShouldHandleCorrectly() throws Exception {
    // Given
    Page<AgencyResponseDTO> agencyPage =
        new PageImpl<>(Collections.singletonList(testAgencyResponse), PageRequest.of(0, 10), 1);
    when(agencyService.getAllAgencies(any(Pageable.class), any())).thenReturn(agencyPage);

    // When & Then
    mockMvc
        .perform(get("/api/v1/agencies").param("page", "0").param("size", "1000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray());
  }

  @Test
  void getAllAgencies_WithSearchTerm_ShouldReturnFilteredResults() throws Exception {
    // Given
    Page<AgencyResponseDTO> agencyPage =
        new PageImpl<>(Collections.singletonList(testAgencyResponse), PageRequest.of(0, 10), 1);
    when(agencyService.getAllAgencies(any(Pageable.class), eq("Creative"))).thenReturn(agencyPage);

    // When & Then
    mockMvc
        .perform(get("/api/v1/agencies").param("search", "Creative"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content[0].id").value("agency123"))
        .andExpect(jsonPath("$.data.content[0].name").value("Test Agency"));
  }

  @Test
  void getAllAgencies_WithEmptySearchTerm_ShouldReturnAllResults() throws Exception {
    // Given
    Page<AgencyResponseDTO> agencyPage =
        new PageImpl<>(Collections.singletonList(testAgencyResponse), PageRequest.of(0, 10), 1);
    when(agencyService.getAllAgencies(any(Pageable.class), eq(""))).thenReturn(agencyPage);

    // When & Then
    mockMvc
        .perform(get("/api/v1/agencies").param("search", ""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray());
  }

  // ========== Content Type and Media Type Tests ==========

  @Test
  void createAgency_WithWrongContentType_ShouldReturnUnsupportedMediaType() throws Exception {
    // When & Then
    mockMvc
        .perform(
            post("/api/v1/agencies")
                .contentType(MediaType.APPLICATION_XML)
                .content("<agency><name>Test</name></agency>"))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void updateAgency_WithWrongContentType_ShouldReturnUnsupportedMediaType() throws Exception {
    // When & Then
    mockMvc
        .perform(
            put("/api/v1/agencies/agency123")
                .contentType(MediaType.APPLICATION_XML)
                .content("<agency><name>Test</name></agency>"))
        .andExpect(status().isUnsupportedMediaType());
  }

  // ========== JSON Serialization Tests ==========

  @Test
  void createAgency_WithComplexNestedData_ShouldSerializeCorrectly() throws Exception {
    // Given
    AgencyRequestDTO complexRequest = createComplexAgencyRequest();
    when(agencyService.createAgency(any(AgencyRequestDTO.class))).thenReturn(testAgencyResponse);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/agencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(complexRequest)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").exists());
  }

  @Test
  void getAgencyById_ShouldReturnProperlyFormattedDates() throws Exception {
    // Given
    when(agencyService.getAgencyById("agency123")).thenReturn(testAgencyResponse);

    // When & Then
    mockMvc
        .perform(get("/api/v1/agencies/agency123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.createdAt").exists())
        .andExpect(jsonPath("$.data.updatedAt").exists());
  }

  // ========== Helper Methods ==========

  private AgencyResponseDTO createTestAgencyResponse() {
    return AgencyResponseDTO.builder()
        .id("agency123")
        .name("Test Agency")
        .mediaOwnerId("MO_001")
        .companyEmail("info@testagency.com")
        .countryId("US")
        .countryName("United States")
        .companyId("COMP_001")
        .seatId(67890)
        .brandRefId("BRAND123")
        .activated(true)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  private AgencyRequestDTO createTestAgencyRequest() {
    return AgencyRequestDTO.builder()
        .name("Test Agency")
        .mediaOwnerId("MO_001")
        .companyEmail("info@testagency.com")
        .countryId("US")
        .companyId("COMP_001")
        .seatId(67890)
        .brandRefId("BRAND123")
        .build();
  }

  private AgencyRequestDTO createComplexAgencyRequest() {
    // Create a more complex request with all fields populated
    return AgencyRequestDTO.builder()
        .name("Complex Test Agency")
        .mediaOwnerId("MO_COMPLEX_001")
        .companyEmail("complex@testagency.com")
        .countryId("UK")
        .companyId("COMP_COMPLEX_001")
        .seatId(98765)
        .brandRefId("COMPLEX_BRAND_123")
        .build();
  }
}
