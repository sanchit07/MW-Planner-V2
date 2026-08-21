package com.mw.planner.service.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mw.planner.config.MwPlannerProperties;
import com.mw.planner.dto.IamUserResponse;
import com.mw.planner.dto.UserInfoResponse;
import com.mw.planner.dto.UserResponseDTO;
import com.mw.planner.exception.auth.AuthenticationException;
import com.mw.planner.exception.user.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class IamUserServiceApiClientTest {

  @Mock private MwPlannerProperties mwPlannerProperties;
  @Mock private RestTemplate restTemplate;

  @InjectMocks private IamUserServiceApiClient iamUserServiceApiClient;

  private MwPlannerProperties.IAM iamConfig;

  @BeforeEach
  void setUp() {
    iamConfig = new MwPlannerProperties.IAM();
    iamConfig.setServiceUrl("https://iam.example.com");
    lenient().when(mwPlannerProperties.getIam()).thenReturn(iamConfig);
  }

  @Test
  void getUserInfo_WithEmptyToken_ThrowsAuthenticationException() {
    assertThatThrownBy(() -> iamUserServiceApiClient.getUserInfo(""))
        .isInstanceOf(AuthenticationException.class)
        .hasMessageContaining("Token is required");
  }

  @Test
  void getUserInfo_WithNullToken_ThrowsAuthenticationException() {
    assertThatThrownBy(() -> iamUserServiceApiClient.getUserInfo(null))
        .isInstanceOf(AuthenticationException.class);
  }

  @Test
  void getUserInfo_WithValidToken_ReturnsUserInfoResponse() {
    UserInfoResponse.UserInfoData data =
        UserInfoResponse.UserInfoData.builder()
            .id("id1")
            .userId("user1")
            .username("user@example.com")
            .build();
    UserInfoResponse userInfoResponse = UserInfoResponse.builder().success(true).data(data).build();
    ResponseEntity<UserInfoResponse> responseEntity =
        ResponseEntity.status(HttpStatus.OK).body(userInfoResponse);

    when(restTemplate.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserInfoResponse.class)))
        .thenReturn(responseEntity);

    UserInfoResponse result = iamUserServiceApiClient.getUserInfo("bearer-token");

    assertThat(result).isNotNull();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData().getUsername()).isEqualTo("user@example.com");
    verify(restTemplate)
        .exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserInfoResponse.class));
  }

  @Test
  void getUserInfo_WhenApiReturnsNullBody_ThrowsAuthenticationException() {
    ResponseEntity<UserInfoResponse> responseEntity =
        ResponseEntity.status(HttpStatus.OK).body((UserInfoResponse) null);

    when(restTemplate.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserInfoResponse.class)))
        .thenReturn(responseEntity);

    assertThatThrownBy(() -> iamUserServiceApiClient.getUserInfo("token"))
        .isInstanceOf(AuthenticationException.class)
        .hasMessageContaining("Invalid response");
  }

  @Test
  void getUserInfo_WhenApiReturnsHttpClientError_ThrowsAuthenticationException() {
    when(restTemplate.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserInfoResponse.class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

    assertThatThrownBy(() -> iamUserServiceApiClient.getUserInfo("bad-token"))
        .isInstanceOf(AuthenticationException.class);
  }

  @Test
  void getUserById_WithValidUserId_ReturnsUserResponseDTO() {
    IamUserResponse.UserData userData = new IamUserResponse.UserData();
    userData.setUserId("user1");
    userData.setUsername("user1");
    IamUserResponse apiResponse = new IamUserResponse();
    apiResponse.setSuccess(true);
    apiResponse.setData(userData);
    ResponseEntity<IamUserResponse> responseEntity =
        ResponseEntity.status(HttpStatus.OK).body(apiResponse);

    when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(IamUserResponse.class),
            eq("user1")))
        .thenReturn(responseEntity);

    UserResponseDTO result = iamUserServiceApiClient.getUserById("user1", "token");

    assertThat(result).isNotNull();
    assertThat(result.getUserId()).isEqualTo("user1");
  }

  @Test
  void getUserById_WhenSuccessFalse_ThrowsUserNotFoundException() {
    IamUserResponse apiResponse = new IamUserResponse();
    apiResponse.setSuccess(false);
    apiResponse.setMessage("User not found");
    ResponseEntity<IamUserResponse> responseEntity =
        ResponseEntity.status(HttpStatus.OK).body(apiResponse);

    when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(IamUserResponse.class),
            eq("user1")))
        .thenReturn(responseEntity);

    assertThatThrownBy(() -> iamUserServiceApiClient.getUserById("user1", "token"))
        .isInstanceOf(UserNotFoundException.class)
        .hasMessageContaining("User not found");
  }

  @Test
  void getUserById_WhenDataNull_ThrowsAuthenticationException() {
    IamUserResponse apiResponse = new IamUserResponse();
    apiResponse.setSuccess(true);
    apiResponse.setData(null);
    ResponseEntity<IamUserResponse> responseEntity =
        ResponseEntity.status(HttpStatus.OK).body(apiResponse);

    when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(IamUserResponse.class),
            eq("user1")))
        .thenReturn(responseEntity);

    assertThatThrownBy(() -> iamUserServiceApiClient.getUserById("user1", "token"))
        .isInstanceOf(AuthenticationException.class)
        .hasMessageContaining("data object is null");
  }

  @Test
  void getUserById_WhenHttpServerError_ThrowsAuthenticationException() {
    when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(IamUserResponse.class),
            eq("user1")))
        .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server error"));

    assertThatThrownBy(() -> iamUserServiceApiClient.getUserById("user1", "token"))
        .isInstanceOf(AuthenticationException.class)
        .hasMessageContaining("server error");
  }
}
