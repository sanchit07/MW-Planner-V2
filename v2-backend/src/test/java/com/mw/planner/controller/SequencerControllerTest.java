package com.mw.planner.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.dto.ApiResponse;
import com.mw.planner.service.SequencerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SequencerControllerTest {

  @Mock private SequencerService sequencerService;

  @InjectMocks private SequencerController sequencerController;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(sequencerController).build();
    objectMapper = new ObjectMapper();
  }

  @Test
  void getSequence_ReturnsNextSequenceFromService() throws Exception {
    String prefix = "MWP_PREFIX";
    when(sequencerService.getSequence(prefix)).thenReturn(1L);

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/sequencer/{prefix}", prefix).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

    ApiResponse<Long> apiResponse =
        objectMapper.readValue(
            result.getResponse().getContentAsString(), new TypeReference<ApiResponse<Long>>() {});
    assert apiResponse.getData() != null;
    assert apiResponse.getData() == 1L;

    verify(sequencerService).getSequence(eq(prefix));
  }

  @Test
  void getSequence_WhenServiceReturnsHigherValue_ReturnsThatValue() throws Exception {
    String prefix = "CAMPAIGN";
    when(sequencerService.getSequence(prefix)).thenReturn(42L);

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/sequencer/{prefix}", prefix).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

    ApiResponse<Long> apiResponse =
        objectMapper.readValue(
            result.getResponse().getContentAsString(), new TypeReference<ApiResponse<Long>>() {});
    assert apiResponse.getData() != null;
    assert apiResponse.getData() == 42L;
  }
}
