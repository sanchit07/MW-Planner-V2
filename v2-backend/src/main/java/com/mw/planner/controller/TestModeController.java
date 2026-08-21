package com.mw.planner.controller;

import com.mw.planner.dto.ApiResponse;
import com.mw.planner.service.SecurityContextService;
import com.mw.planner.service.TestModeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Per-user Test Mode (demo data partition) toggle, ported from the V1 header switch. */
@RestController
@RequestMapping("/api/v1/users/test-mode")
@Tag(name = "Test Mode", description = "Per-user Test Mode (demo data partition)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class TestModeController {

  private final TestModeService testModeService;
  private final SecurityContextService securityContextService;

  @GetMapping
  @Operation(summary = "Get the current user's Test Mode state")
  public ApiResponse<Map<String, Object>> getTestMode() {
    String userId = securityContextService.getCurrentUsername();
    boolean testMode = testModeService.getTestMode(userId);
    return ApiResponse.success(
        Map.of(
            "testMode",
            testMode,
            "effectiveDataMode",
            testMode ? TestModeService.MODE_DEMO : TestModeService.MODE_LIVE,
            "locked",
            false));
  }

  @Data
  public static class UpdateTestModeRequest {
    private Boolean testMode;
  }

  @PutMapping
  @Operation(summary = "Update the current user's Test Mode state")
  public ApiResponse<Map<String, Object>> updateTestMode(
      @RequestBody UpdateTestModeRequest request) {
    if (request == null || request.getTestMode() == null) {
      throw new IllegalArgumentException("testMode boolean is required");
    }
    String userId = securityContextService.getCurrentUsername();
    boolean testMode = testModeService.setTestMode(userId, request.getTestMode());
    return ApiResponse.success(
        Map.of(
            "testMode",
            testMode,
            "effectiveDataMode",
            testMode ? TestModeService.MODE_DEMO : TestModeService.MODE_LIVE,
            "locked",
            false));
  }
}
