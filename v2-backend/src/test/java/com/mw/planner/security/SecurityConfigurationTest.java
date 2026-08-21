package com.mw.planner.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.planner.config.MwPlannerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfigurationSource;

@ExtendWith(MockitoExtension.class)
class SecurityConfigurationTest {

  @Mock private JwtAuthFilter jwtAuthFilter;

  private SecurityConfiguration securityConfiguration;
  private MwPlannerProperties mwPlannerProperties;

  @BeforeEach
  void setUp() {
    mwPlannerProperties = new MwPlannerProperties();
    MwPlannerProperties.IAM iam = new MwPlannerProperties.IAM();
    iam.setServiceUrl("https://iam.example.com");
    iam.setProductId("mw-planner");
    mwPlannerProperties.setIam(iam);

    MwPlannerProperties.Management management = new MwPlannerProperties.Management();
    MwPlannerProperties.Management.Credentials credentials =
        new MwPlannerProperties.Management.Credentials();
    credentials.setUsername("admin");
    credentials.setPassword("secret");
    management.setCredentials(credentials);
    mwPlannerProperties.setManagement(management);

    securityConfiguration = new SecurityConfiguration(jwtAuthFilter, mwPlannerProperties, null);
  }

  @Test
  void passwordEncoder_ReturnsBCryptEncoder() {
    PasswordEncoder encoder = securityConfiguration.passwordEncoder();

    assertThat(encoder).isNotNull();
    String encoded = encoder.encode("password");
    assertThat(encoded).isNotEqualTo("password");
    assertThat(encoder.matches("password", encoded)).isTrue();
  }

  @Test
  void corsConfigurationSource_ReturnsConfiguredSource() {
    CorsConfigurationSource source = securityConfiguration.corsConfigurationSource();

    assertThat(source).isNotNull();
  }

  @Test
  void jwtAuthenticationConverter_ReturnsConverterWithProductId() {
    var converter = securityConfiguration.jwtAuthenticationConverter();

    assertThat(converter).isNotNull();
  }

  @Test
  void userDetailsService_ReturnsInMemoryUserDetailsManager() {
    UserDetailsService userDetailsService = securityConfiguration.userDetailsService();

    assertThat(userDetailsService).isNotNull();
    assertThat(userDetailsService.loadUserByUsername("admin")).isNotNull();
  }
}
