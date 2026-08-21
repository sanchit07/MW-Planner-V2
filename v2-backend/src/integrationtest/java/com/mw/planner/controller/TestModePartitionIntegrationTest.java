package com.mw.planner.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.TestcontainersConfiguration;
import com.mw.planner.domain.Campaign;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.repository.CampaignRepository;
import com.mw.planner.repository.UserSettingsRepository;
import com.mw.planner.service.UserService;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for the Test Mode demo/live data partition:
 *
 * <ul>
 *   <li>dataMode is stamped server-side at creation and cannot be spoofed by the client
 *   <li>the plans list only returns the caller's partition (legacy null dataMode = live)
 *   <li>cross-mode by-ID reads and writes behave as 404
 *   <li>the test-mode toggle endpoints persist per user
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Import(TestcontainersConfiguration.class)
class TestModePartitionIntegrationTest {

  private static final String LIVE_USER = "live-user";
  private static final String DEMO_USER = "demo-user";

  @Autowired private MockMvc mockMvc;
  @Autowired private CampaignRepository campaignRepository;
  @Autowired private UserSettingsRepository userSettingsRepository;

  @MockitoBean private UserService userService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws Exception {
    objectMapper.findAndRegisterModules();

    IamUserContext ctx =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .locale(Locale.ENGLISH)
            .build();
    when(userService.getIamUserContext()).thenReturn(ctx);

    campaignRepository.deleteAll();
    userSettingsRepository.deleteAll();

    // demo-user works in Test Mode; live-user does not.
    actAs(DEMO_USER);
    mockMvc
        .perform(
            put("/api/v1/users/test-mode")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"testMode\": true}"))
        .andExpect(status().isOk());
    actAs(LIVE_USER);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void actAs(String username) {
    TestingAuthenticationToken auth =
        new TestingAuthenticationToken(
            username,
            "n/a",
            List.of(
                new SimpleGrantedAuthority("ROLE_planner:plans:create"),
                new SimpleGrantedAuthority("ROLE_planner:plans:read"),
                new SimpleGrantedAuthority("ROLE_planner:plans:update"),
                new SimpleGrantedAuthority("ROLE_planner:plans:delete")));
    auth.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  private Campaign seedCampaign(String id, String name, String dataMode) {
    Campaign campaign = new Campaign();
    campaign.setId(id);
    campaign.setName(name);
    campaign.setStatus(Campaign.Status.DRAFT);
    campaign.setCompanyId("company123"); // matches the mocked caller's company
    campaign.setDataMode(dataMode); // null = legacy record
    return campaignRepository.save(campaign);
  }

  /** Minimal valid campaign JSON (clientType, startDate and endDate are mandatory). */
  private String campaignJson(String name, String extraJsonFields) {
    return "{\"name\": \""
        + name
        + "\", \"clientType\": \"DIRECT_ADVERTISER\","
        + " \"startDate\": \"2030-01-01\", \"endDate\": \"2030-02-01\""
        + (extraJsonFields.isEmpty() ? "" : ", " + extraJsonFields)
        + "}";
  }

  private JsonNode dataOf(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
  }

  // --- 1. dataMode is stamped server-side at creation ---

  @Test
  @DisplayName("Create stamps dataMode=live for a live caller and ignores a spoofed dataMode")
  void createStampsLiveModeAndIgnoresSpoof() throws Exception {
    actAs(LIVE_USER);
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/campaigns")
                    .contentType(MediaType.APPLICATION_JSON)
                    // Client tries to spoof itself into the demo partition.
                    .content(campaignJson("Spoof Attempt Plan", "\"dataMode\": \"demo\"")))
            .andExpect(status().isOk())
            .andReturn();

    String id = dataOf(result).path("id").asText();
    Campaign saved = campaignRepository.findById(id).orElseThrow();
    assertThat(saved.getDataMode()).isEqualTo("live");
  }

  @Test
  @DisplayName("Create stamps dataMode=demo for a Test Mode caller and ignores a spoofed dataMode")
  void createStampsDemoModeAndIgnoresSpoof() throws Exception {
    actAs(DEMO_USER);
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/campaigns")
                    .contentType(MediaType.APPLICATION_JSON)
                    // Client tries to spoof itself into the live partition.
                    .content(campaignJson("Demo Spoof Plan", "\"dataMode\": \"live\"")))
            .andExpect(status().isOk())
            .andReturn();

    String id = dataOf(result).path("id").asText();
    Campaign saved = campaignRepository.findById(id).orElseThrow();
    assertThat(saved.getDataMode()).isEqualTo("demo");
  }

  // --- 2. Plans list only returns the caller's partition ---

  @Test
  @DisplayName("Live caller's list returns live + legacy(null) plans only")
  void listForLiveCallerReturnsLiveAndLegacyOnly() throws Exception {
    seedCampaign("c-live", "Live Plan", "live");
    seedCampaign("c-legacy", "Legacy Plan", null);
    seedCampaign("c-demo", "Demo Plan", "demo");

    actAs(LIVE_USER);
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/campaigns").param("size", "50"))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode content = dataOf(result).path("content");
    List<String> ids = content.findValuesAsText("id");
    assertThat(ids).contains("c-live", "c-legacy").doesNotContain("c-demo");
  }

  @Test
  @DisplayName("Test Mode caller's list returns demo plans only")
  void listForDemoCallerReturnsDemoOnly() throws Exception {
    seedCampaign("c-live", "Live Plan", "live");
    seedCampaign("c-legacy", "Legacy Plan", null);
    seedCampaign("c-demo", "Demo Plan", "demo");

    actAs(DEMO_USER);
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/campaigns").param("size", "50"))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode content = dataOf(result).path("content");
    List<String> ids = content.findValuesAsText("id");
    assertThat(ids).contains("c-demo").doesNotContain("c-live", "c-legacy");
  }

  // --- 3. Cross-mode by-ID reads and writes 404 ---

  @Test
  @DisplayName("Live caller: demo plan by ID reads/writes 404; live and legacy plans are visible")
  void crossModeAccessForLiveCaller() throws Exception {
    seedCampaign("c-live", "Live Plan", "live");
    seedCampaign("c-legacy", "Legacy Plan", null);
    seedCampaign("c-demo", "Demo Plan", "demo");

    actAs(LIVE_USER);
    mockMvc.perform(get("/api/v1/campaigns/c-live")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/campaigns/c-legacy")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/campaigns/c-demo")).andExpect(status().isNotFound());

    // Cross-mode write (update) 404s and does not modify the record.
    mockMvc
        .perform(
            put("/api/v1/campaigns/c-demo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(campaignJson("Hijacked Name", "")))
        .andExpect(status().isNotFound());
    assertThat(campaignRepository.findById("c-demo").orElseThrow().getName())
        .isEqualTo("Demo Plan");

    // Cross-mode delete 404s and the record survives.
    mockMvc.perform(delete("/api/v1/campaigns/c-demo")).andExpect(status().isNotFound());
    assertThat(campaignRepository.findById("c-demo")).isPresent();
  }

  @Test
  @DisplayName("Test Mode caller: live and legacy plans by ID read/write 404; demo plan is visible")
  void crossModeAccessForDemoCaller() throws Exception {
    seedCampaign("c-live", "Live Plan", "live");
    seedCampaign("c-legacy", "Legacy Plan", null);
    seedCampaign("c-demo", "Demo Plan", "demo");

    actAs(DEMO_USER);
    mockMvc.perform(get("/api/v1/campaigns/c-demo")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/campaigns/c-live")).andExpect(status().isNotFound());
    mockMvc.perform(get("/api/v1/campaigns/c-legacy")).andExpect(status().isNotFound());

    mockMvc
        .perform(
            put("/api/v1/campaigns/c-live")
                .contentType(MediaType.APPLICATION_JSON)
                .content(campaignJson("Hijacked Name", "")))
        .andExpect(status().isNotFound());
    assertThat(campaignRepository.findById("c-live").orElseThrow().getName())
        .isEqualTo("Live Plan");

    mockMvc.perform(delete("/api/v1/campaigns/c-legacy")).andExpect(status().isNotFound());
    assertThat(campaignRepository.findById("c-legacy")).isPresent();
  }

  // --- 4. Test-mode toggle endpoints persist per user ---

  @Test
  @DisplayName("Test Mode toggle persists per user and defaults to off (live)")
  void testModeTogglePersistsPerUser() throws Exception {
    // demo-user was switched on in setUp and the state persisted in user_settings.
    actAs(DEMO_USER);
    mockMvc
        .perform(get("/api/v1/users/test-mode"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.testMode").value(true))
        .andExpect(jsonPath("$.data.effectiveDataMode").value("demo"));

    // live-user has no settings record: defaults to off / live.
    actAs(LIVE_USER);
    mockMvc
        .perform(get("/api/v1/users/test-mode"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.testMode").value(false))
        .andExpect(jsonPath("$.data.effectiveDataMode").value("live"));

    // Turning it on for live-user does not affect demo-user's stored state.
    mockMvc
        .perform(
            put("/api/v1/users/test-mode")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"testMode\": true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.testMode").value(true))
        .andExpect(jsonPath("$.data.effectiveDataMode").value("demo"));

    // Turning demo-user off persists too.
    actAs(DEMO_USER);
    mockMvc
        .perform(
            put("/api/v1/users/test-mode")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"testMode\": false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.testMode").value(false));
    mockMvc
        .perform(get("/api/v1/users/test-mode"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.testMode").value(false))
        .andExpect(jsonPath("$.data.effectiveDataMode").value("live"));

    // live-user's state is untouched by demo-user's toggle.
    actAs(LIVE_USER);
    mockMvc
        .perform(get("/api/v1/users/test-mode"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.testMode").value(true));

    // Both states are persisted in the user_settings collection.
    assertThat(userSettingsRepository.findById(DEMO_USER).orElseThrow().getTestMode()).isFalse();
    assertThat(userSettingsRepository.findById(LIVE_USER).orElseThrow().getTestMode()).isTrue();
  }
}
