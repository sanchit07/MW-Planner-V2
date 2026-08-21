package com.mw.planner.service;

import com.mw.planner.domain.*;
import com.mw.planner.exception.campaign.CampaignNotFoundException;
import com.mw.planner.repository.CampaignProposalStatusRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CampaignProposalStatusAndCommentService {
  private final CampaignService campaignService;
  private final CampaignProposalStatusRepository campaignProposalStatusRepository;

  public CampaignProposalStatus getProposalsByCampaignIdAndMediaOwnerId(
      String campaignId, String mediaOwnerId) {
    if (!campaignService.existsById(campaignId)) {
      throw new CampaignNotFoundException(campaignId);
    }
    return campaignProposalStatusRepository.findByCampaignIdAndMediaOwnerId(
        campaignId, mediaOwnerId);
  }

  public List<CampaignProposalStatus> getProposalsByCampaignId(String campaignId) {
    if (!campaignService.existsById(campaignId)) {
      throw new CampaignNotFoundException(campaignId);
    }
    return campaignProposalStatusRepository.findByCampaignId(campaignId);
  }
}
