package com.mw.planner.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "mw-planner")
public class MwPlannerProperties {
  private List<String> supportedLanguages = new ArrayList<>();

  private MasterData masterData = new MasterData();
  private Measure measure = new Measure();
  private Account account = new Account();
  private Company company = new Company();
  private Management management = new Management();
  private Cache cache = new Cache();
  private Ads ads = new Ads();
  private Posterops posterops = new Posterops();
  private IAM iam = new IAM();
  private Proxy proxy = new Proxy();
  private RecommendationEngine recommendationEngine = new RecommendationEngine();
  private PerformanceBackfill performanceBackfill = new PerformanceBackfill();

  @Data
  public static class PerformanceBackfill {
    private int batchSize = 50;
    private int maxConcurrentMeasureCalls = 5;
    private long lockTtlSeconds = 7200;
    private long jobStatusTtlSeconds = 604800;
  }

  @Data
  public static class MasterData {
    private String baseUrl;
    private Endpoints endpoints = new Endpoints();
    private Defaults defaults = new Defaults();

    @Data
    public static class Endpoints {
      private String country;
      private String state;
      private String district;
    }

    @Data
    public static class Defaults {
      private int pageSize = 500;
      private int pageNumber = 0;
    }

    public String getFullCountryUrl() {
      return baseUrl
          + endpoints.getCountry()
          + String.format("?page=%d&size=%d", defaults.getPageNumber(), defaults.getPageSize());
    }

    public String getFullStateUrl() {
      return baseUrl + endpoints.getState() + String.format("?size=%d", defaults.getPageSize());
    }

    public String getFullDistrictUrl(String stateIds) {
      return baseUrl
          + endpoints.getDistrict()
          + String.format("?stateIds=%s&size=%d", stateIds, defaults.getPageSize());
    }
  }

  @Data
  public static class Measure {
    private String baseUrl;
    private Endpoints endpoints = new Endpoints();

    @Data
    public static class Endpoints {
      private String reachAndFrequency;
    }

    public String getFullReachAndFrequencyUrl() {
      return baseUrl + endpoints.getReachAndFrequency();
    }

    /**
     * Get the full reach and frequency URL with optional aggregate query parameter
     *
     * @param aggregate If true, returns individual site level data; if false, returns aggregated
     *     campaign level data
     * @return Full URL with query parameter
     */
    public String getFullReachAndFrequencyUrl(boolean aggregate) {
      String url = baseUrl + endpoints.getReachAndFrequency();
      if (aggregate) {
        url += "?aggregate=true";
      }
      return url;
    }
  }

  @Data
  public static class Account {
    private String baseUrl;
    private Endpoints endpoints = new Endpoints();

    @Data
    public static class Endpoints {
      private String login;
      private String account;
    }

    /** Get the full account URL by combining base URL and account URL */
    public String getFullAccountUrl() {
      return baseUrl + endpoints.getAccount();
    }

    /** Get the full login URL by combining base URL and login URL */
    public String getFullLoginUrl() {
      return baseUrl + endpoints.getLogin();
    }
  }

  @Data
  public static class Company {
    private String baseUrl;
    private Endpoints endpoints = new Endpoints();

    @Data
    public static class Endpoints {
      private String children;
      private String subscription;
      private String creation;
    }

    /** Get the full company children URL by combining base URL and company children URL */
    public String getFullCompanyChildrenUrl() {
      return baseUrl + endpoints.getChildren();
    }

    /** Get the full company subscription URL by combining base URL and company subscription URL */
    public String getFullCompanySubscriptionUrl() {
      return baseUrl + endpoints.getSubscription();
    }

    /** Get the full company creation URL by combining base URL and company creation URL */
    public String getFullCompanyCreationUrl() {
      return baseUrl + endpoints.getCreation();
    }
  }

  @Data
  public static class Management {
    private Credentials credentials = new Credentials();

    @Data
    public static class Credentials {
      private String username;
      private String password;
    }
  }

  @Data
  public static class Cache {
    private long ttlSeconds = 3600;
  }

  @Data
  public static class Ads {
    private String baseUrl;
    private Endpoints endpoints = new Endpoints();

    @Data
    public static class Endpoints {
      private String submitCampaign;
    }

    /** Get the full ADS submit campaign URL by combining base URL and submit campaign URL */
    public String getFullSubmitCampaignUrl() {
      return baseUrl + endpoints.getSubmitCampaign();
    }
  }

  @Data
  public static class Posterops {
    private String baseUrl;
    private String hmacSecret;
    private Endpoints endpoints = new Endpoints();

    @Data
    public static class Endpoints {
      private String intake;
    }

    /** Get the full PosterOps intake URL by combining base URL and intake URL */
    public String getFullIntakeUrl() {
      return baseUrl + endpoints.getIntake();
    }
  }

  @Data
  public static class IAM {
    private String serviceUrl;
    private String productId;
    private String clientId;
    private String clientSecret;
    private String scope;
    private String redirectUri;
    private String responseType;
    private String state;
    private String codeChallenge;
    private String codeChallengeMethod;
    private Endpoints endpoints = new Endpoints();

    @Data
    public static class Endpoints {
      private String userinfo;
      private String userById;
      private String companyById;
      private String companyLookup;
      private String userMeCompanies;
      private String companyChildren;
      private String authorize;
      private String token;
      private String logout;
    }

    /** Get the full OAuth authorization URL by combining base URL and authorize endpoint */
    public String getFullAuthorizeUrl() {
      return serviceUrl + endpoints.getAuthorize();
    }

    /** Get the full OAuth token URL by combining base URL and token endpoint */
    public String getFullTokenUrl() {
      return serviceUrl + endpoints.getToken();
    }

    /** Get the full OAuth logout URL by combining base URL and logout endpoint */
    public String getFullLogoutUrl() {
      return serviceUrl + endpoints.getLogout();
    }

    /** Get the full IAM userinfo URL by combining service URL and userinfo endpoint */
    public String getFullUserinfoUrl() {
      return serviceUrl + endpoints.getUserinfo();
    }

    /** Get the full IAM user by ID URL by combining service URL and userById endpoint */
    public String getFullUserByIdUrl() {
      return serviceUrl + endpoints.getUserById();
    }

    /** Get the full IAM company by ID URL by combining service URL and companyById endpoint */
    public String getFullCompanyByIdUrl() {
      return serviceUrl + endpoints.getCompanyById();
    }

    /** Get the full IAM company lookup URL by combining service URL and companyLookup endpoint */
    public String getFullCompanyLookupUrl() {
      return serviceUrl + endpoints.getCompanyLookup();
    }

    /** Get the full IAM user-me-companies URL for fetching all user memberships */
    public String getFullUserMeCompaniesUrl() {
      return serviceUrl + endpoints.getUserMeCompanies();
    }

    /** Get the full IAM company children URL */
    public String getFullIamCompanyChildrenUrl() {
      return serviceUrl + endpoints.getCompanyChildren();
    }
  }

  @Data
  public static class Proxy {
    /** Map of application identifier (e.g. integration-api) to base URL */
    private Map<String, String> applications = new HashMap<>();

    /** API key used to build Authorization header for inventory-api proxy requests. */
    private String inventoryApiKey;
  }

  @Data
  public static class RecommendationEngine {
    private String baseUrl;
    private Endpoints endpoints = new Endpoints();

    @Data
    public static class Endpoints {
      private String generateRecommendation =
          "/api/v1/recommendation/campaigns/{campaignId}/recommendations";
      private String getResults = "/api/v1/recommendation/runs/{runId}/selected-results";
      // private String getResults = "/api/v1/recommendation/runs/{runId}/results";
      private String getSchedules = "/api/v1/recommendation/schedules/runs/{runId}";
      private String selectedInventories =
          "/api/v1/recommendation/runs/{runId}/selected-inventories";
      private String autoOptimizeSchedules =
          "/api/v1/recommendation/schedules/runs/{runId}/auto-optimize";
    }

    public String getGenerateRecommendationUrl(String campaignId) {
      return baseUrl + endpoints.getGenerateRecommendation().replace("{campaignId}", campaignId);
    }

    public String getResultsUrl(String runId) {
      return baseUrl + endpoints.getGetResults().replace("{runId}", runId);
    }

    public String getSchedulesUrl(String runId) {
      return baseUrl + endpoints.getGetSchedules().replace("{runId}", runId);
    }

    public String getSelectedInventoriesUrl(String runId) {
      return baseUrl + endpoints.getSelectedInventories().replace("{runId}", runId);
    }

    public String getAutoOptimizeSchedulesUrl(String runId) {
      return baseUrl + endpoints.getAutoOptimizeSchedules().replace("{runId}", runId);
    }
  }
}
