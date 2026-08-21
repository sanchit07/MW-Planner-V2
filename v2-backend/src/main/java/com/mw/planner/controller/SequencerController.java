package com.mw.planner.controller;

import com.mw.planner.dto.ApiResponse;
import com.mw.planner.service.SequencerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sequencer")
@Tag(name = "Sequencer", description = "Provide next sequence number for given prefix")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class SequencerController {

  private final SequencerService sequencerService;

  @Operation(
      summary = "Get sequence number by prefix",
      description = "Returns next sequence number for the given prefix")
  @GetMapping("/{prefix}")
  public ApiResponse<Long> getSequence(@PathVariable("prefix") String prefix) {
    Long sequence = sequencerService.getSequence(prefix);
    return ApiResponse.success(sequence);
  }
}
