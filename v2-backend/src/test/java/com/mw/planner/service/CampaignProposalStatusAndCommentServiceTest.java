package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mw.planner.domain.CampaignProposalStatus;
import com.mw.planner.exception.campaign.CampaignNotFoundException;
import com.mw.planner.repository.CampaignProposalStatusRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CampaignProposalStatusAndCommentServiceTest {

  @Mock private CampaignService campaignService;
  @Mock private CampaignProposalStatusRepository campaignProposalStatusRepository;

  @InjectMocks private CampaignProposalStatusAndCommentService service;

  @Test
  void getProposalsByCampaignIdAndMediaOwnerId_WhenCampaignExists_ReturnsProposalStatus() {
    String campaignId = "campaign-1";
    String mediaOwnerId = "owner-1";
    CampaignProposalStatus status = new CampaignProposalStatus();

    when(campaignService.existsById(campaignId)).thenReturn(true);
    when(campaignProposalStatusRepository.findByCampaignIdAndMediaOwnerId(
            eq(campaignId), eq(mediaOwnerId)))
        .thenReturn(status);

    CampaignProposalStatus result =
        service.getProposalsByCampaignIdAndMediaOwnerId(campaignId, mediaOwnerId);

    assertThat(result).isEqualTo(status);
    verify(campaignService).existsById(campaignId);
    verify(campaignProposalStatusRepository)
        .findByCampaignIdAndMediaOwnerId(eq(campaignId), eq(mediaOwnerId));
  }

  @Test
  void getProposalsByCampaignIdAndMediaOwnerId_WhenCampaignDoesNotExist_ThrowsNotFoundException() {
    String campaignId = "missing-campaign";
    String mediaOwnerId = "owner-1";

    when(campaignService.existsById(campaignId)).thenReturn(false);

    assertThatThrownBy(
            () -> service.getProposalsByCampaignIdAndMediaOwnerId(campaignId, mediaOwnerId))
        .isInstanceOf(CampaignNotFoundException.class)
        .hasMessageContaining(campaignId);

    verify(campaignService).existsById(campaignId);
    verify(campaignProposalStatusRepository, never()).findByCampaignIdAndMediaOwnerId(any(), any());
  }
}
