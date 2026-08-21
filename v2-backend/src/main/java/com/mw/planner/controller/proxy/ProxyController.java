package com.mw.planner.controller.proxy;

import com.mw.planner.service.proxy.ProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/proxy")
@Tag(
    name = "Proxy",
    description =
        "Proxy requests to downstream applications (e.g. integration-api / inventory-api)")
public class ProxyController {

  private final ProxyService proxyService;

  public ProxyController(ProxyService proxyService) {
    this.proxyService = proxyService;
  }

  @Operation(
      summary = "Proxy request to downstream application",
      description =
          "Forwards the request to a configured downstream application. "
              + "URL pattern: `/proxy/{applicationName}/{path}`. "
              + "Example: `GET /proxy/integration-api/api/v1/bookings/123` forwards to "
              + "`{integration-api.baseUrl}/api/v1/bookings/123` with the same HTTP method, "
              + "query parameters, and headers. Application base URLs are configured in "
              + "`mw-planner.proxy.applications` (e.g. integration-api → inventory-api base URL).")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Response passed through unaltered from downstream application")
      })
  @RequestMapping("/**")
  public ResponseEntity<String> proxyRequest(HttpServletRequest request) throws IOException {
    return proxyService.forwardRequest(request);
  }
}
