package com.mw.planner.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.PublicAccessToken;
import com.mw.planner.dto.ApiResponse;
import com.mw.planner.dto.CampaignInventoryFilterResponseDTO;
import com.mw.planner.dto.CampaignResponseDTO;
import com.mw.planner.dto.PublicAccessTokenResponseDTO;
import com.mw.planner.dto.SelectedInventorySummaryResponseDTO;
import com.mw.planner.exception.campaign.CampaignNotFoundException;
import com.mw.planner.exception.publicaccess.PublicAccessTokenInvalidException;
import com.mw.planner.exception.publicaccess.PublicAccessTokenNotFoundException;
import com.mw.planner.service.CampaignService;
import com.mw.planner.service.InventoryService;
import com.mw.planner.service.PublicAccessTokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class PublicAccessControllerTest {

  @Mock private PublicAccessTokenService publicAccessTokenService;

  @Mock private InventoryService inventoryService;

  @Mock private CampaignService campaignService;

  @Mock private HttpServletRequest httpServletRequest;

  @InjectMocks private PublicAccessController publicAccessController;

  private static final String CAMPAIGN_ID = "campaign123";
  private static final String TOKEN_ID = "token123";
  private static final String PUBLIC_TOKEN = "publicToken123";

  private PublicAccessToken testToken;
  private CampaignInventoryFilterResponseDTO testInventoryDTO;

  @BeforeEach
  void setUp() {
    testToken =
        PublicAccessToken.builder().campaignId(CAMPAIGN_ID).domainName("example.com").build();

    testInventoryDTO = new CampaignInventoryFilterResponseDTO();
    CampaignInventoryFilterResponseDTO.Detail detail =
        new CampaignInventoryFilterResponseDTO.Detail();
    detail.setId("inventory1");
    detail.setName("Test Inventory");
    testInventoryDTO.setDetail(detail);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(publicAccessTokenService, inventoryService, campaignService);
  }

  // ========== generateToken Tests ==========

  @Test
  @DisplayName("generateToken - Should generate token successfully")
  void generateToken_WithValidCampaign_ShouldReturnSuccessResponse() {
    // Given
    when(publicAccessTokenService.getOrCreatePublicAccessToken(CAMPAIGN_ID, httpServletRequest))
        .thenReturn(TOKEN_ID);

    // When
    ApiResponse<PublicAccessTokenResponseDTO> result =
        publicAccessController.generateToken(CAMPAIGN_ID, httpServletRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData().getPublicToken()).isEqualTo(TOKEN_ID);
    verify(publicAccessTokenService).getOrCreatePublicAccessToken(CAMPAIGN_ID, httpServletRequest);
  }

  @Test
  @DisplayName("generateToken - Should propagate CampaignNotFoundException")
  void generateToken_WithNonExistentCampaign_ShouldThrowCampaignNotFoundException() {
    // Given
    when(publicAccessTokenService.getOrCreatePublicAccessToken(CAMPAIGN_ID, httpServletRequest))
        .thenThrow(new CampaignNotFoundException(CAMPAIGN_ID));

    // When & Then
    assertThatThrownBy(() -> publicAccessController.generateToken(CAMPAIGN_ID, httpServletRequest))
        .isInstanceOf(CampaignNotFoundException.class)
        .hasMessageContaining(CAMPAIGN_ID);

    verify(publicAccessTokenService).getOrCreatePublicAccessToken(CAMPAIGN_ID, httpServletRequest);
  }

  // ========== getInventories Tests ==========

  @Test
  @DisplayName("getInventories - Should return inventories successfully with no filters")
  void getInventories_WithValidTokenAndNoFilters_ShouldReturnInventories() {
    // Given
    Pageable pageable = PageRequest.of(0, 10, Sort.by("name").ascending());
    Page<CampaignInventoryFilterResponseDTO> inventoryPage =
        new PageImpl<>(List.of(testInventoryDTO), pageable, 1);

    when(publicAccessTokenService.validateAndGetToken(PUBLIC_TOKEN)).thenReturn(testToken);
    doNothing().when(publicAccessTokenService).validateDomainName(testToken, httpServletRequest);
    when(inventoryService.getSelectedInventories(
            eq(CAMPAIGN_ID), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(inventoryPage);

    // When
    ApiResponse<Page<CampaignInventoryFilterResponseDTO>> result =
        publicAccessController.getInventories(
            PUBLIC_TOKEN, httpServletRequest, null, null, 0, 10, "name", "asc");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData().getContent()).hasSize(1);
    assertThat(result.getData().getTotalElements()).isEqualTo(1);
    verify(publicAccessTokenService).validateAndGetToken(PUBLIC_TOKEN);
    verify(inventoryService)
        .getSelectedInventories(eq(CAMPAIGN_ID), isNull(), isNull(), any(Pageable.class));
  }

  @Test
  @DisplayName("getInventories - Should return inventories with name filter")
  void getInventories_WithNameFilter_ShouldReturnFilteredInventories() {
    // Given
    String nameFilter = "billboard";
    Pageable pageable = PageRequest.of(0, 10, Sort.by("name").ascending());
    Page<CampaignInventoryFilterResponseDTO> inventoryPage =
        new PageImpl<>(List.of(testInventoryDTO), pageable, 1);

    when(publicAccessTokenService.validateAndGetToken(PUBLIC_TOKEN)).thenReturn(testToken);
    doNothing().when(publicAccessTokenService).validateDomainName(testToken, httpServletRequest);
    when(inventoryService.getSelectedInventories(
            eq(CAMPAIGN_ID), eq(nameFilter), isNull(), any(Pageable.class)))
        .thenReturn(inventoryPage);

    // When
    ApiResponse<Page<CampaignInventoryFilterResponseDTO>> result =
        publicAccessController.getInventories(
            PUBLIC_TOKEN, httpServletRequest, nameFilter, null, 0, 10, "name", "asc");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getData().getContent()).hasSize(1);
    verify(publicAccessTokenService).validateAndGetToken(PUBLIC_TOKEN);
    verify(inventoryService)
        .getSelectedInventories(eq(CAMPAIGN_ID), eq(nameFilter), isNull(), any(Pageable.class));
  }

  @Test
  @DisplayName("getInventories - Should return inventories with inventoryType filter")
  void getInventories_WithInventoryTypeFilter_ShouldReturnFilteredInventories() {
    // Given
    String inventoryTypeFilter = "CLASSIC";
    Pageable pageable = PageRequest.of(0, 10, Sort.by("name").ascending());
    Page<CampaignInventoryFilterResponseDTO> inventoryPage =
        new PageImpl<>(List.of(testInventoryDTO), pageable, 1);

    when(publicAccessTokenService.validateAndGetToken(PUBLIC_TOKEN)).thenReturn(testToken);
    doNothing().when(publicAccessTokenService).validateDomainName(testToken, httpServletRequest);
    when(inventoryService.getSelectedInventories(
            eq(CAMPAIGN_ID), isNull(), eq(inventoryTypeFilter), any(Pageable.class)))
        .thenReturn(inventoryPage);

    // When
    ApiResponse<Page<CampaignInventoryFilterResponseDTO>> result =
        publicAccessController.getInventories(
            PUBLIC_TOKEN, httpServletRequest, null, inventoryTypeFilter, 0, 10, "name", "asc");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getData().getContent()).hasSize(1);
    verify(publicAccessTokenService).validateAndGetToken(PUBLIC_TOKEN);
    verify(inventoryService)
        .getSelectedInventories(
            eq(CAMPAIGN_ID), isNull(), eq(inventoryTypeFilter), any(Pageable.class));
  }

  @Test
  @DisplayName("getInventories - Should return inventories with both filters")
  void getInventories_WithBothFilters_ShouldReturnFilteredInventories() {
    // Given
    String nameFilter = "billboard";
    String inventoryTypeFilter = "CLASSIC";
    Pageable pageable = PageRequest.of(0, 10, Sort.by("name").ascending());
    Page<CampaignInventoryFilterResponseDTO> inventoryPage =
        new PageImpl<>(List.of(testInventoryDTO), pageable, 1);

    when(publicAccessTokenService.validateAndGetToken(PUBLIC_TOKEN)).thenReturn(testToken);
    doNothing().when(publicAccessTokenService).validateDomainName(testToken, httpServletRequest);
    when(inventoryService.getSelectedInventories(
            eq(CAMPAIGN_ID), eq(nameFilter), eq(inventoryTypeFilter), any(Pageable.class)))
        .thenReturn(inventoryPage);

    // When
    ApiResponse<Page<CampaignInventoryFilterResponseDTO>> result =
        publicAccessController.getInventories(
            PUBLIC_TOKEN,
            httpServletRequest,
            nameFilter,
            inventoryTypeFilter,
            0,
            10,
            "name",
            "asc");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getData().getContent()).hasSize(1);
    verify(publicAccessTokenService).validateAndGetToken(PUBLIC_TOKEN);
    verify(inventoryService)
        .getSelectedInventories(
            eq(CAMPAIGN_ID), eq(nameFilter), eq(inventoryTypeFilter), any(Pageable.class));
  }

  @Test
  @DisplayName("getInventories - Should throw exception when token is invalid")
  void getInventories_WithInvalidToken_ShouldThrowPublicAccessTokenNotFoundException() {
    // Given
    when(publicAccessTokenService.validateAndGetToken(PUBLIC_TOKEN))
        .thenThrow(
            new PublicAccessTokenNotFoundException(
                "Public access token not found: " + PUBLIC_TOKEN));

    // When & Then
    assertThatThrownBy(
            () ->
                publicAccessController.getInventories(
                    PUBLIC_TOKEN, httpServletRequest, null, null, 0, 10, "name", "asc"))
        .isInstanceOf(PublicAccessTokenNotFoundException.class)
        .hasMessageContaining(PUBLIC_TOKEN);

    verify(publicAccessTokenService).validateAndGetToken(PUBLIC_TOKEN);
    verify(publicAccessTokenService, never()).validateDomainName(any(), any());
    verify(inventoryService, never()).getSelectedInventories(anyString(), any(), any(), any());
  }

  @Test
  @DisplayName("getInventories - Should throw exception when domain name doesn't match")
  void getInventories_WithInvalidDomain_ShouldThrowPublicAccessTokenInvalidException() {
    // Given
    when(publicAccessTokenService.validateAndGetToken(PUBLIC_TOKEN)).thenReturn(testToken);
    doThrow(
            new PublicAccessTokenInvalidException(
                "Invalid domain name. Expected: example.com, Actual: different.com"))
        .when(publicAccessTokenService)
        .validateDomainName(testToken, httpServletRequest);

    // When & Then
    assertThatThrownBy(
            () ->
                publicAccessController.getInventories(
                    PUBLIC_TOKEN, httpServletRequest, null, null, 0, 10, "name", "asc"))
        .isInstanceOf(PublicAccessTokenInvalidException.class)
        .hasMessageContaining("Invalid domain name");

    verify(publicAccessTokenService).validateAndGetToken(PUBLIC_TOKEN);
    verify(publicAccessTokenService).validateDomainName(testToken, httpServletRequest);
    verify(inventoryService, never()).getSelectedInventories(anyString(), any(), any(), any());
  }

  @Test
  @DisplayName("getInventories - Should handle descending sort")
  void getInventories_WithDescendingSort_ShouldCreateCorrectPageable() {
    // Given
    Pageable pageable = PageRequest.of(0, 10, Sort.by("name").descending());
    Page<CampaignInventoryFilterResponseDTO> inventoryPage =
        new PageImpl<>(List.of(testInventoryDTO), pageable, 1);

    when(publicAccessTokenService.validateAndGetToken(PUBLIC_TOKEN)).thenReturn(testToken);
    doNothing().when(publicAccessTokenService).validateDomainName(testToken, httpServletRequest);
    when(inventoryService.getSelectedInventories(
            eq(CAMPAIGN_ID), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(inventoryPage);

    // When
    publicAccessController.getInventories(
        PUBLIC_TOKEN, httpServletRequest, null, null, 0, 10, "name", "desc");

    // Then
    verify(inventoryService)
        .getSelectedInventories(
            eq(CAMPAIGN_ID),
            isNull(),
            isNull(),
            argThat(
                p ->
                    p.getSort().getOrderFor("name") != null
                        && p.getSort().getOrderFor("name").getDirection().isDescending()));
  }

  @Test
  @DisplayName("getInventories - Should handle pagination correctly")
  void getInventories_WithPagination_ShouldReturnCorrectPage() {
    // Given
    Pageable pageable = PageRequest.of(1, 5, Sort.by("name").ascending());
    Page<CampaignInventoryFilterResponseDTO> inventoryPage =
        new PageImpl<>(List.of(testInventoryDTO), pageable, 10);

    when(publicAccessTokenService.validateAndGetToken(PUBLIC_TOKEN)).thenReturn(testToken);
    doNothing().when(publicAccessTokenService).validateDomainName(testToken, httpServletRequest);
    when(inventoryService.getSelectedInventories(
            eq(CAMPAIGN_ID), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(inventoryPage);

    // When
    ApiResponse<Page<CampaignInventoryFilterResponseDTO>> result =
        publicAccessController.getInventories(
            PUBLIC_TOKEN, httpServletRequest, null, null, 1, 5, "name", "asc");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getData().getNumber()).isEqualTo(1);
    assertThat(result.getData().getSize()).isEqualTo(5);
    assertThat(result.getData().getTotalElements()).isEqualTo(10);
    verify(inventoryService)
        .getSelectedInventories(
            eq(CAMPAIGN_ID),
            isNull(),
            isNull(),
            argThat(p -> p.getPageNumber() == 1 && p.getPageSize() == 5));
  }

  @Test
  @DisplayName("getInventories - Should normalize negative page number to 0")
  void getInventories_WithNegativePage_ShouldNormalizeToZero() {
    // Given
    Pageable pageable = PageRequest.of(0, 10, Sort.by("name").ascending());
    Page<CampaignInventoryFilterResponseDTO> inventoryPage =
        new PageImpl<>(new ArrayList<>(), pageable, 0);

    when(publicAccessTokenService.validateAndGetToken(PUBLIC_TOKEN)).thenReturn(testToken);
    doNothing().when(publicAccessTokenService).validateDomainName(testToken, httpServletRequest);
    when(inventoryService.getSelectedInventories(
            eq(CAMPAIGN_ID), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(inventoryPage);

    // When
    publicAccessController.getInventories(
        PUBLIC_TOKEN, httpServletRequest, null, null, -1, 10, "name", "asc");

    // Then
    verify(inventoryService)
        .getSelectedInventories(
            eq(CAMPAIGN_ID), isNull(), isNull(), argThat(p -> p.getPageNumber() == 0));
  }

  @Test
  @DisplayName("getInventories - Should normalize zero or negative size to 1")
  void getInventories_WithZeroOrNegativeSize_ShouldNormalizeToOne() {
    // Given
    Pageable pageable = PageRequest.of(0, 1, Sort.by("name").ascending());
    Page<CampaignInventoryFilterResponseDTO> inventoryPage =
        new PageImpl<>(new ArrayList<>(), pageable, 0);

    when(publicAccessTokenService.validateAndGetToken(PUBLIC_TOKEN)).thenReturn(testToken);
    doNothing().when(publicAccessTokenService).validateDomainName(testToken, httpServletRequest);
    when(inventoryService.getSelectedInventories(
            eq(CAMPAIGN_ID), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(inventoryPage);

    // When
    publicAccessController.getInventories(
        PUBLIC_TOKEN, httpServletRequest, null, null, 0, 0, "name", "asc");

    // Then
    verify(inventoryService)
        .getSelectedInventories(
            eq(CAMPAIGN_ID), isNull(), isNull(), argThat(p -> p.getPageSize() == 1));
  }

  @Test
  @DisplayName("getInventories - Should return empty page when no inventories found")
  void getInventories_WithNoInventories_ShouldReturnEmptyPage() {
    // Given
    Pageable pageable = PageRequest.of(0, 10, Sort.by("name").ascending());
    Page<CampaignInventoryFilterResponseDTO> emptyPage =
        new PageImpl<>(new ArrayList<>(), pageable, 0);

    when(publicAccessTokenService.validateAndGetToken(PUBLIC_TOKEN)).thenReturn(testToken);
    doNothing().when(publicAccessTokenService).validateDomainName(testToken, httpServletRequest);
    when(inventoryService.getSelectedInventories(
            eq(CAMPAIGN_ID), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(emptyPage);

    // When
    ApiResponse<Page<CampaignInventoryFilterResponseDTO>> result =
        publicAccessController.getInventories(
            PUBLIC_TOKEN, httpServletRequest, null, null, 0, 10, "name", "asc");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getData().getContent()).isEmpty();
    assertThat(result.getData().getTotalElements()).isEqualTo(0);
  }

  @Test
  @DisplayName("getInventories - Should handle case-insensitive sort direction")
  void getInventories_WithUpperCaseSortDirection_ShouldHandleCorrectly() {
    // Given
    Pageable pageable = PageRequest.of(0, 10, Sort.by("name").descending());
    Page<CampaignInventoryFilterResponseDTO> inventoryPage =
        new PageImpl<>(List.of(testInventoryDTO), pageable, 1);

    when(publicAccessTokenService.validateAndGetToken(PUBLIC_TOKEN)).thenReturn(testToken);
    doNothing().when(publicAccessTokenService).validateDomainName(testToken, httpServletRequest);
    when(inventoryService.getSelectedInventories(
            eq(CAMPAIGN_ID), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(inventoryPage);

    // When
    publicAccessController.getInventories(
        PUBLIC_TOKEN, httpServletRequest, null, null, 0, 10, "name", "DESC");

    // Then
    verify(inventoryService)
        .getSelectedInventories(
            eq(CAMPAIGN_ID),
            isNull(),
            isNull(),
            argThat(
                p ->
                    p.getSort().getOrderFor("name") != null
                        && p.getSort().getOrderFor("name").getDirection().isDescending()));
  }

  // ========== getCampaign Tests ==========

  @Test
  @DisplayName("getCampaign - Should return campaign details successfully")
  void getCampaign_WithValidToken_ShouldReturnCampaign() {
    // Given
    CampaignResponseDTO campaignDTO =
        CampaignResponseDTO.builder().id(CAMPAIGN_ID).name("Test Campaign").build();
    when(publicAccessTokenService.validateAndGetToken(PUBLIC_TOKEN)).thenReturn(testToken);
    when(campaignService.getCampaignByIdForPublicAccess(CAMPAIGN_ID)).thenReturn(campaignDTO);

    // When
    ApiResponse<CampaignResponseDTO> result =
        publicAccessController.getCampaign(PUBLIC_TOKEN, httpServletRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData().getId()).isEqualTo(CAMPAIGN_ID);
    assertThat(result.getData().getName()).isEqualTo("Test Campaign");
    verify(publicAccessTokenService).validateAndGetToken(PUBLIC_TOKEN);
    verify(campaignService).getCampaignByIdForPublicAccess(CAMPAIGN_ID);
  }

  @Test
  @DisplayName("getCampaign - Should throw exception when token is invalid")
  void getCampaign_WithInvalidToken_ShouldThrowPublicAccessTokenNotFoundException() {
    // Given
    when(publicAccessTokenService.validateAndGetToken(PUBLIC_TOKEN))
        .thenThrow(
            new PublicAccessTokenNotFoundException(
                "Public access token not found: " + PUBLIC_TOKEN));

    // When & Then
    assertThatThrownBy(() -> publicAccessController.getCampaign(PUBLIC_TOKEN, httpServletRequest))
        .isInstanceOf(PublicAccessTokenNotFoundException.class)
        .hasMessageContaining(PUBLIC_TOKEN);

    verify(publicAccessTokenService).validateAndGetToken(PUBLIC_TOKEN);
    verify(publicAccessTokenService, never()).validateDomainName(any(), any());
    verify(campaignService, never()).getCampaignByIdForPublicAccess(anyString());
  }

  // ========== getAllSelectedInventories Tests ==========

  @Test
  @DisplayName("getAllSelectedInventories - Should return selected inventories successfully")
  void getAllSelectedInventories_WithValidToken_ShouldReturnInventories() {
    // Given
    List<SelectedInventorySummaryResponseDTO> summaries =
        List.of(
            SelectedInventorySummaryResponseDTO.builder()
                .inventoryId("inv1")
                .referenceId("ref1")
                .build(),
            SelectedInventorySummaryResponseDTO.builder()
                .inventoryId("inv2")
                .referenceId("ref2")
                .build());
    when(publicAccessTokenService.validateAndGetToken(PUBLIC_TOKEN)).thenReturn(testToken);
    when(inventoryService.getAllSelectedInventories(CAMPAIGN_ID)).thenReturn(summaries);

    // When
    ApiResponse<List<SelectedInventorySummaryResponseDTO>> result =
        publicAccessController.getAllSelectedInventories(PUBLIC_TOKEN, httpServletRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getData()).hasSize(2);
    assertThat(result.getData().get(0).getInventoryId()).isEqualTo("inv1");
    verify(publicAccessTokenService).validateAndGetToken(PUBLIC_TOKEN);
    verify(inventoryService).getAllSelectedInventories(CAMPAIGN_ID);
  }

  @Test
  @DisplayName("getAllSelectedInventories - Should return empty list when none selected")
  void getAllSelectedInventories_WithNoInventories_ShouldReturnEmptyList() {
    // Given
    when(publicAccessTokenService.validateAndGetToken(PUBLIC_TOKEN)).thenReturn(testToken);
    when(inventoryService.getAllSelectedInventories(CAMPAIGN_ID)).thenReturn(List.of());

    // When
    ApiResponse<List<SelectedInventorySummaryResponseDTO>> result =
        publicAccessController.getAllSelectedInventories(PUBLIC_TOKEN, httpServletRequest);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getData()).isEmpty();
    verify(inventoryService).getAllSelectedInventories(CAMPAIGN_ID);
  }

  @Test
  @DisplayName("getAllSelectedInventories - Should throw exception when token is invalid")
  void getAllSelectedInventories_WithInvalidToken_ShouldThrowPublicAccessTokenNotFoundException() {
    // Given
    when(publicAccessTokenService.validateAndGetToken(PUBLIC_TOKEN))
        .thenThrow(
            new PublicAccessTokenNotFoundException(
                "Public access token not found: " + PUBLIC_TOKEN));

    // When & Then
    assertThatThrownBy(
            () ->
                publicAccessController.getAllSelectedInventories(PUBLIC_TOKEN, httpServletRequest))
        .isInstanceOf(PublicAccessTokenNotFoundException.class)
        .hasMessageContaining(PUBLIC_TOKEN);

    verify(publicAccessTokenService).validateAndGetToken(PUBLIC_TOKEN);
    verify(publicAccessTokenService, never()).validateDomainName(any(), any());
    verify(inventoryService, never()).getAllSelectedInventories(anyString());
  }
}
