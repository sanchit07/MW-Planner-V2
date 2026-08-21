package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.mw.planner.dto.CompanyDto;
import com.mw.planner.exception.auth.AuthenticationException;
import com.mw.planner.exception.company.CompanyNotFoundException;
import com.mw.planner.exception.company.InvalidCompanyDataException;
import com.mw.planner.service.iam.IamCompanyApiClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

  @Mock private SecurityContextService securityContextService;
  @Mock private IamCompanyApiClient iamCompanyApiClient;

  @InjectMocks private CompanyService companyService;

  @BeforeEach
  void setUp() {
    // Self-injection not used in unit tests; we test getCompanyById(companyId, token) and
    // getCompaniesByIds
  }

  @Test
  void getCompanyById_WithNullId_ThrowsInvalidCompanyDataException() {
    assertThatThrownBy(() -> companyService.getCompanyById(null))
        .isInstanceOf(InvalidCompanyDataException.class);

    assertThatThrownBy(() -> companyService.getCompanyById(""))
        .isInstanceOf(InvalidCompanyDataException.class);
  }

  @Test
  void getCompanyById_WithValidIdAndToken_ReturnsCompanyDto() {
    com.mw.planner.dto.IamCompanyResponse.Company iamCompany =
        com.mw.planner.dto.IamCompanyResponse.Company.builder()
            .id("company-1")
            .name("Test Company")
            .isActive(true)
            .build();
    com.mw.planner.dto.IamCompanyResponse.CompanyData companyData =
        com.mw.planner.dto.IamCompanyResponse.CompanyData.builder().company(iamCompany).build();
    com.mw.planner.dto.IamCompanyResponse response =
        com.mw.planner.dto.IamCompanyResponse.builder().success(true).data(companyData).build();

    when(iamCompanyApiClient.getCompanyById(eq("company-1"), eq("token"))).thenReturn(response);

    CompanyDto result = companyService.getCompanyById("company-1", "token");

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("company-1");
    assertThat(result.getName()).isEqualTo("Test Company");
    verify(iamCompanyApiClient).getCompanyById(eq("company-1"), eq("token"));
  }

  @Test
  void getCompanyById_WhenApiReturnsNull_ThrowsCompanyNotFoundException() {
    when(iamCompanyApiClient.getCompanyById(eq("missing"), eq("token"))).thenReturn(null);

    assertThatThrownBy(() -> companyService.getCompanyById("missing", "token"))
        .isInstanceOf(CompanyNotFoundException.class);
  }

  @Test
  void getCompaniesByIds_WithNull_ReturnsEmptyList() {
    assertThat(companyService.getCompaniesByIds(null)).isEmpty();
  }

  @Test
  void getCompaniesByIds_WithEmptyList_ReturnsEmptyList() {
    assertThat(companyService.getCompaniesByIds(List.of())).isEmpty();
  }

  @Test
  void getCompaniesByIds_WithValidIds_ReturnsActivatedCompanies() {
    // Self-injection: getCompaniesByIds calls companyService.getCompanyById(id, token)
    ReflectionTestUtils.setField(companyService, "companyService", companyService);

    CompanyDto company1 = new CompanyDto();
    company1.setId("c1");
    company1.setName("Company 1");
    company1.setActivated(true);
    CompanyDto company2 = new CompanyDto();
    company2.setId("c2");
    company2.setName("Company 2");
    company2.setActivated(true);

    when(securityContextService.getBearerToken()).thenReturn("token");
    when(iamCompanyApiClient.getCompanyById(eq("c1"), eq("token")))
        .thenReturn(
            com.mw.planner.dto.IamCompanyResponse.builder()
                .success(true)
                .data(
                    com.mw.planner.dto.IamCompanyResponse.CompanyData.builder()
                        .company(
                            com.mw.planner.dto.IamCompanyResponse.Company.builder()
                                .id("c1")
                                .name("Company 1")
                                .isActive(true)
                                .build())
                        .build())
                .build());
    when(iamCompanyApiClient.getCompanyById(eq("c2"), eq("token")))
        .thenReturn(
            com.mw.planner.dto.IamCompanyResponse.builder()
                .success(true)
                .data(
                    com.mw.planner.dto.IamCompanyResponse.CompanyData.builder()
                        .company(
                            com.mw.planner.dto.IamCompanyResponse.Company.builder()
                                .id("c2")
                                .name("Company 2")
                                .isActive(true)
                                .build())
                        .build())
                .build());

    List<CompanyDto> result = companyService.getCompaniesByIds(List.of("c1", "c2"));

    assertThat(result).hasSize(2);
    assertThat(result.stream().map(CompanyDto::getId)).containsExactlyInAnyOrder("c1", "c2");
  }

  @Test
  void getCompanyById_WhenAuthenticationFails_ThrowsCompanyNotFoundException() {
    when(securityContextService.getBearerToken())
        .thenThrow(
            new AuthenticationException(com.mw.planner.enums.ErrorCode.UNAUTHORIZED, "No token"));

    assertThatThrownBy(() -> companyService.getCompanyById("c1"))
        .isInstanceOf(CompanyNotFoundException.class);
  }
}
