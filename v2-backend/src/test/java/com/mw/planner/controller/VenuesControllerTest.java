package com.mw.planner.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.dto.VenueItemDTO;
import com.mw.planner.exception.GlobalExceptionHandler;
import com.mw.planner.service.MessageService;
import com.mw.planner.service.MetricsService;
import com.mw.planner.service.UserService;
import com.mw.planner.service.VenuesService;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class VenuesControllerTest {

  @Mock private VenuesService venuesService;
  @Mock private UserService userService;
  @Mock private MessageService messageService;
  @Mock private MetricsService metricsService;

  @InjectMocks private VenuesController venuesController;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();
    mockMvc =
        MockMvcBuilders.standaloneSetup(venuesController)
            .setControllerAdvice(
                new GlobalExceptionHandler(messageService, metricsService, userService))
            .build();
  }

  @Test
  void getVenues_ShouldReturnHierarchicalVenueList() throws Exception {
    VenueItemDTO child = new VenueItemDTO();
    child.setEnumerationId(101);
    child.setName("Arrival Hall");
    child.setStringValue("transit-airports-arrival-hall");

    VenueItemDTO root = new VenueItemDTO();
    root.setEnumerationId(1);
    root.setName("Transit");
    root.setStringValue("transit");
    root.setChildren(List.of(child));

    when(venuesService.getHierarchicalVenues(any(Locale.class))).thenReturn(List.of(root));

    mockMvc
        .perform(get("/api/v1/venues").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].name").value("Transit"))
        .andExpect(jsonPath("$.data[0].enumerationId").value(1))
        .andExpect(jsonPath("$.data[0].children[0].name").value("Arrival Hall"))
        .andExpect(jsonPath("$.data[0].children[0].enumerationId").value(101));

    verify(venuesService).getHierarchicalVenues(any(Locale.class));
  }

  @Test
  void getVenues_WhenEmpty_ShouldReturnEmptyList() throws Exception {
    when(venuesService.getHierarchicalVenues(any(Locale.class))).thenReturn(List.of());

    mockMvc
        .perform(get("/api/v1/venues").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data").isEmpty());

    verify(venuesService).getHierarchicalVenues(any(Locale.class));
  }
}
