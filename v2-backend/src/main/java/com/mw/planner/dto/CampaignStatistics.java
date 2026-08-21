package com.mw.planner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Campaign statistics DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignStatistics {
  private long totalCampaigns;
  private long draftCampaigns;
  private long plannedCampaigns;
  private long reviewingCampaigns;
  private long negotiatingCampaigns;
  private long pendingCampaigns;
  private long approvedCampaigns;
  private long dealRequestedCampaigns;
  private long activeCampaigns;
  private long pauseCampaigns;
  private long completedCampaigns;
  private long rejectedCampaigns;
  private long archivedCampaigns;
}
