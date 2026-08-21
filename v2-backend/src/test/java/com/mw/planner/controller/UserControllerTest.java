package com.mw.planner.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mw.planner.dto.IamUserContext;
import com.mw.planner.dto.UserInfoResponse;
import com.mw.planner.exception.GlobalExceptionHandler;
import com.mw.planner.service.MessageService;
import com.mw.planner.service.MetricsService;
import com.mw.planner.service.SecurityContextService;
import com.mw.planner.service.UserService;
import com.mw.planner.service.iam.IamUserServiceApiClient;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

  @Mock private SecurityContextService securityContextService;
  @Mock private IamUserServiceApiClient iamUserService;
  @Mock private MessageService messageService;
  @Mock private MetricsService metricsService;
  @Mock private UserService userService;

  @InjectMocks private UserController userController;

  private MockMvc mockMvc;
  private IamUserContext testUserContext;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(userController)
            .setControllerAdvice(
                new GlobalExceptionHandler(messageService, metricsService, userService))
            .build();
    testUserContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .locale(Locale.ENGLISH)
            .build();
    lenient().when(userService.getIamUserContext()).thenReturn(testUserContext);
  }

  @Test
  void getUserInfo_WithValidToken_ReturnsUserInfo() throws Exception {
    String token = "bearer-token";
    UserInfoResponse.UserInfoData data =
        UserInfoResponse.UserInfoData.builder()
            .id("id1")
            .userId("user1")
            .sub("sub1")
            .email("user@example.com")
            .username("user1")
            .firstName("First")
            .lastName("Last")
            .build();
    UserInfoResponse userInfoResponse = UserInfoResponse.builder().success(true).data(data).build();

    when(securityContextService.getBearerToken()).thenReturn(token);
    when(iamUserService.getUserInfo(eq(token))).thenReturn(userInfoResponse);

    mockMvc
        .perform(get("/api/v1/users/userinfo").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value("id1"))
        .andExpect(jsonPath("$.data.email").value("user@example.com"))
        .andExpect(jsonPath("$.data.username").value("user1"));

    verify(securityContextService).getBearerToken();
    verify(iamUserService).getUserInfo(eq(token));
  }

  @Test
  void getUserInfo_WithChildCompanies_MapsChildCompaniesCorrectly() throws Exception {
    String token = "bearer-token";

    UserInfoResponse.CompanyType companyType =
        UserInfoResponse.CompanyType.builder()
            .id("type1")
            .name("Media Owner")
            .code("MEDIA_OWNER")
            .isSupplierSide(true)
            .isDemandSide(false)
            .build();

    UserInfoResponse.ChildCompany childCompany =
        UserInfoResponse.ChildCompany.builder()
            .id("child1")
            .name("Child Company A")
            .companyType(companyType)
            .grantedScopes(List.of())
            .scopes(List.of())
            .build();

    UserInfoResponse.ChildCompanies childCompanies =
        UserInfoResponse.ChildCompanies.builder()
            .items(List.of(childCompany))
            .totalCount(1)
            .hasMore(false)
            .build();

    UserInfoResponse.CurrentCompany currentCompany =
        UserInfoResponse.CurrentCompany.builder()
            .id("company1")
            .name("Parent Company")
            .childCompanies(childCompanies)
            .build();

    UserInfoResponse.UserInfoData data =
        UserInfoResponse.UserInfoData.builder()
            .id("id1")
            .userId("user1")
            .email("user@example.com")
            .currentCompany(currentCompany)
            .build();

    UserInfoResponse userInfoResponse = UserInfoResponse.builder().success(true).data(data).build();

    when(securityContextService.getBearerToken()).thenReturn(token);
    when(iamUserService.getUserInfo(eq(token))).thenReturn(userInfoResponse);

    mockMvc
        .perform(get("/api/v1/users/userinfo").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.current_company.childCompanies.totalCount").value(1))
        .andExpect(jsonPath("$.data.current_company.childCompanies.hasMore").value(false))
        .andExpect(jsonPath("$.data.current_company.childCompanies.items[0].id").value("child1"))
        .andExpect(
            jsonPath("$.data.current_company.childCompanies.items[0].name")
                .value("Child Company A"))
        .andExpect(
            jsonPath("$.data.current_company.childCompanies.items[0].companyType.code")
                .value("MEDIA_OWNER"))
        .andExpect(
            jsonPath("$.data.current_company.childCompanies.items[0].companyType.is_supplier_side")
                .value(true));
  }
}
