package com.mw.planner.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mw.planner.TestcontainersConfiguration;
import com.mw.planner.dto.CompanyDto;
import com.mw.planner.dto.IamCompanyResponse;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.dto.UserInfoResponse;
import com.mw.planner.exception.user.UserNotFoundException;
import com.mw.planner.service.CompanyService;
import com.mw.planner.service.UserService;
import com.mw.planner.service.iam.IamCompanyApiClient;
import com.mw.planner.service.iam.IamUserServiceApiClient;
import java.util.Locale;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(classes = TestcontainersConfiguration.class)
class RedisCacheIntegrationTest {

  @Autowired private CacheManager cacheManager;

  @Autowired private RedisTemplate<String, Object> redisTemplate;

  @Autowired private UserService userService;

  @Autowired private CompanyService companyService;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private IamUserServiceApiClient iamUserService;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private IamCompanyApiClient iamCompanyApiClient;

  private IamUserContext testIamUserContext;

  @BeforeEach
  void setUp() {
    // Clear all caches before each test
    cacheManager
        .getCacheNames()
        .forEach(
            cacheName -> {
              var cache = cacheManager.getCache(cacheName);
              if (cache != null) {
                cache.clear();
              }
            });

    // Clear Redis
    var connectionFactory = redisTemplate.getConnectionFactory();
    if (connectionFactory != null) {
      try (var connection = connectionFactory.getConnection()) {
        connection.serverCommands().flushAll();
      }
    }

    // Setup test data
    testIamUserContext =
        IamUserContext.builder()
            .id("user-1")
            .userId("user-1")
            .username("testuser")
            .email("test@example.com")
            .firstName("Test")
            .lastName("User")
            .locale(Locale.ENGLISH)
            .companyId("company-1")
            .build();
  }

  @Test
  @DisplayName(
      "Should cache IAM user context by username on first call and return from cache on subsequent calls")
  void shouldCacheIamUserContextByUsername() {
    // Given
    String username = "testuser";
    String token = "test-token";
    UserInfoResponse.UserInfoData userInfoData = new UserInfoResponse.UserInfoData();
    userInfoData.setId("user-1");
    userInfoData.setUserId("user-1");
    userInfoData.setUsername(username);
    userInfoData.setEmail("test@example.com");
    userInfoData.setFirstName("Test");
    userInfoData.setLastName("User");

    UserInfoResponse userInfoResponse = new UserInfoResponse();
    userInfoResponse.setData(userInfoData);

    when(iamUserService.getUserInfo(token)).thenReturn(userInfoResponse);

    // When - First call (cache miss)
    IamUserContext firstCall = userService.initIamUserContext(username, token);

    // When - Second call (cache hit)
    IamUserContext secondCall = userService.initIamUserContext(username, token);

    // Then
    assertThat(firstCall).isNotNull();
    assertThat(secondCall).isNotNull();
    assertThat(firstCall.getUsername()).isEqualTo(secondCall.getUsername());

    // Verify IAM service was called only once (second call should hit cache)
    verify(iamUserService, times(1)).getUserInfo(token);
  }

  @Test
  @DisplayName("Should handle cache miss gracefully when user not found")
  void shouldHandleCacheMissWhenUserNotFound() {
    // Given
    String username = "nonexistent";
    String token = "test-token";
    when(iamUserService.getUserInfo(token))
        .thenThrow(new UserNotFoundException("User not found in IAM system: " + username));

    // When & Then
    UserNotFoundException exception =
        assertThrows(
            UserNotFoundException.class, () -> userService.initIamUserContext(username, token));

    Assertions.assertTrue(exception.getMessage().contains("User not found"));

    // Verify IAM service was called
    verify(iamUserService, times(1)).getUserInfo(token);
  }

  @Test
  @DisplayName("Should use different cache keys for different users")
  void shouldUseDifferentCacheKeysForDifferentUsers() {
    // Given
    String username1 = "testuser";
    String username2 = "testuser2";
    String token1 = "token1";
    String token2 = "token2";

    UserInfoResponse.UserInfoData userInfoData1 = new UserInfoResponse.UserInfoData();
    userInfoData1.setId("user-1");
    userInfoData1.setUserId("user-1");
    userInfoData1.setUsername(username1);
    userInfoData1.setEmail("test@example.com");

    UserInfoResponse.UserInfoData userInfoData2 = new UserInfoResponse.UserInfoData();
    userInfoData2.setId("user-2");
    userInfoData2.setUserId("user-2");
    userInfoData2.setUsername(username2);
    userInfoData2.setEmail("test2@example.com");

    UserInfoResponse response1 = new UserInfoResponse();
    response1.setData(userInfoData1);

    UserInfoResponse response2 = new UserInfoResponse();
    response2.setData(userInfoData2);

    when(iamUserService.getUserInfo(token1)).thenReturn(response1);
    when(iamUserService.getUserInfo(token2)).thenReturn(response2);

    // When - Call both users
    IamUserContext user1Result = userService.initIamUserContext(username1, token1);
    IamUserContext user2Result = userService.initIamUserContext(username2, token2);

    // When - Call again to verify cache hits
    userService.initIamUserContext(username1, token1);
    userService.initIamUserContext(username2, token2);

    // Then
    assertThat(user1Result.getUsername()).isEqualTo(username1);
    assertThat(user2Result.getUsername()).isEqualTo(username2);

    // Each IAM service method should be called only once (cache hit on second call)
    verify(iamUserService, times(1)).getUserInfo(token1);
    verify(iamUserService, times(1)).getUserInfo(token2);
  }

  @Test
  @DisplayName("Should cache company by companyId and only call IAM API once")
  void shouldCacheCompanyByCompanyId() {
    // Given
    String companyId = "company-1";
    String token = "test-token";

    IamCompanyResponse.Company iamCompany = new IamCompanyResponse.Company();
    iamCompany.setId(companyId);
    iamCompany.setExternalId("ext-123");
    iamCompany.setName("Test Company");
    iamCompany.setIsActive(true);

    IamCompanyResponse.CompanyData data = new IamCompanyResponse.CompanyData();
    data.setCompany(iamCompany);

    IamCompanyResponse response = new IamCompanyResponse();
    response.setSuccess(true);
    response.setData(data);

    when(iamCompanyApiClient.getCompanyById(companyId, token)).thenReturn(response);

    // When - first call (cache miss) + second call (cache hit)
    CompanyDto first = companyService.getCompanyById(companyId, token);
    CompanyDto second = companyService.getCompanyById(companyId, token);

    // Then
    assertThat(first).isNotNull();
    assertThat(second).isNotNull();
    assertThat(first.getId()).isEqualTo(companyId);
    assertThat(second.getName()).isEqualTo("Test Company");

    verify(iamCompanyApiClient, times(1)).getCompanyById(companyId, token);
  }
}
