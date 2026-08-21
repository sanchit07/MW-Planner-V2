package com.mw.planner.controller.proxy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mw.planner.service.proxy.ProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ProxyControllerTest {

  @Mock private ProxyService proxyService;

  @InjectMocks private ProxyController proxyController;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(proxyController).build();
  }

  @Test
  void proxyRequest_DelegatesToProxyService_ReturnsResponse() throws Exception {
    ResponseEntity<String> expected =
        ResponseEntity.status(HttpStatus.OK).body("{\"result\":\"ok\"}");
    when(proxyService.forwardRequest(any(HttpServletRequest.class))).thenReturn(expected);

    mockMvc.perform(get("/proxy/integration-api/api/v1/bookings/123")).andExpect(status().isOk());

    verify(proxyService).forwardRequest(any(HttpServletRequest.class));
  }
}
