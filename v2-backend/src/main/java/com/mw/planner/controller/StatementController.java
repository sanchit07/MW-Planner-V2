package com.mw.planner.controller;

import com.mw.planner.dto.ApiResponse;
import com.mw.planner.dto.statement.StatementCandidateDTO;
import com.mw.planner.dto.statement.StatementCreateRequestDTO;
import com.mw.planner.dto.statement.StatementDTO;
import com.mw.planner.dto.statement.StatementSplitRequestDTO;
import com.mw.planner.service.StatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/statements")
@Tag(
    name = "Statements",
    description = "Billing statements bundling one or more campaigns (PRD §12)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class StatementController {

  private final StatementService statementService;

  @GetMapping
  @PreAuthorize("hasRole('planner:statements:read')")
  @Operation(summary = "List statements for the acting company")
  public ApiResponse<List<StatementDTO>> list() {
    return ApiResponse.success(statementService.listForActingCompany());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('planner:statements:read')")
  @Operation(summary = "Get a statement")
  public ApiResponse<StatementDTO> getById(@PathVariable String id) {
    return ApiResponse.success(statementService.getById(id));
  }

  @GetMapping("/candidates")
  @PreAuthorize("hasRole('planner:statements:read')")
  @Operation(
      summary = "Check which campaigns are eligible for billing",
      description =
          "Real eligibility check — billable status AND every line item's schedules approved, "
              + "not just a status-string match.")
  public ApiResponse<List<StatementCandidateDTO>> listCandidates(
      @RequestParam List<String> campaignIds) {
    return ApiResponse.success(statementService.listCandidates(campaignIds));
  }

  @PostMapping("/calculate")
  @PreAuthorize("hasRole('planner:statements:read')")
  @Operation(summary = "Live preview of a statement's cost cascade, without persisting")
  public ApiResponse<StatementDTO> calculate(
      @Valid @RequestBody StatementCreateRequestDTO request) {
    return ApiResponse.success(statementService.calculate(request.getCampaignIds()));
  }

  @PostMapping
  @PreAuthorize("hasRole('planner:statements:create')")
  @Operation(summary = "Create a draft statement from eligible campaigns")
  public ApiResponse<StatementDTO> create(@Valid @RequestBody StatementCreateRequestDTO request) {
    return ApiResponse.success(statementService.create(request.getCampaignIds()));
  }

  @PostMapping("/{id}/finalize")
  @PreAuthorize("hasRole('planner:statements:update')")
  @Operation(
      summary = "Finalize a statement",
      description = "Freezes the fee snapshot and moves the statement out of Draft.")
  public ApiResponse<StatementDTO> finalizeStatement(@PathVariable String id) {
    return ApiResponse.success(statementService.finalizeStatement(id));
  }

  @PostMapping("/{id}/split")
  @PreAuthorize("hasRole('planner:statements:update')")
  @Operation(
      summary = "Split a statement",
      description =
          "Equal/Monthly/Weekly/Campaign-based are computed server-side; Custom uses caller amounts.")
  public ApiResponse<List<StatementDTO>> split(
      @PathVariable String id, @Valid @RequestBody StatementSplitRequestDTO request) {
    return ApiResponse.success(statementService.split(id, request));
  }

  @PostMapping("/{id}/sync/{integration}")
  @PreAuthorize("hasRole('planner:statements:update')")
  @Operation(
      summary = "Mark a statement synced with a finance integration",
      description =
          "Manual admin action — data model/status only. Real NetSuite/Zoho/QuickBooks outbound "
              + "sync is separate follow-up work; this records confirmation and locks the statement.")
  public ApiResponse<StatementDTO> markSynced(
      @PathVariable String id,
      @PathVariable String integration,
      @RequestParam(required = false) String externalId) {
    return ApiResponse.success(statementService.markSynced(id, integration, externalId));
  }
}
