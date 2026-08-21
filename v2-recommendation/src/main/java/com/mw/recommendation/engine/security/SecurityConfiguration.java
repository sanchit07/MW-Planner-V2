package com.mw.recommendation.engine.security;

import com.mw.recommendation.engine.config.MwRecommendationEngineProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

  private final MwRecommendationEngineProperties mwRecommendationEngineProperties;

  public static final String SYSTEM_ADMIN_ROLE = "SYSTEM_ADMIN";
  public static final String GLOBAL_ADMIN_ROLE = "GLOBAL_ADMIN";

  private static final String[] DEFAULT_WHITELIST_URLS = {
    "/actuator/health",
    "/actuator/info",
    "/actuator/prometheus",
    "/favicon.ico",
    "/v3/api-docs/**",
    "/v3/api-docs.yaml",
    "/swagger-ui/**",
    "/swagger-resources/**",
    "/webjars/**"
  };

  private static final String[] AUTH_ADMIN = {
    "/actuator/env",
    "/actuator/threaddump",
    "/actuator/heapdump",
    "/actuator/metrics",
    "/api/v1/management/**"
  };

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.addAllowedOrigin("*");
    configuration.addAllowedMethod("*");
    configuration.addAllowedHeader("*");
    configuration.setAllowCredentials(false);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(
            sessionManagementConfigurer ->
                sessionManagementConfigurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            accessManagement ->
                accessManagement
                    .requestMatchers(DEFAULT_WHITELIST_URLS)
                    .permitAll()
                    .requestMatchers(AUTH_ADMIN)
                    .hasAnyRole(SYSTEM_ADMIN_ROLE, GLOBAL_ADMIN_ROLE)
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(
                    jwt ->
                        jwt.decoder(jwtDecoder)
                            .jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .httpBasic(Customizer.withDefaults());
    return http.build();
  }

  @Bean
  public JwtDecoder jwtDecoder() {
    String serviceUrl = mwRecommendationEngineProperties.getIam().getServiceUrl();
    String publicJwksUri = serviceUrl + "/.well-known/jwks.json";

    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(publicJwksUri).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(serviceUrl));

    return decoder;
  }

  @Bean
  public CustomJwtAuthenticationConverter jwtAuthenticationConverter() {
    return new CustomJwtAuthenticationConverter(
        mwRecommendationEngineProperties.getIam().getProductId());
  }

  @Bean
  public UserDetailsService userDetailsService() {
    MwRecommendationEngineProperties.Management.Credentials credentials =
        mwRecommendationEngineProperties.getManagement().getCredentials();
    return new InMemoryUserDetailsManager(
        User.builder()
            .username(credentials.getUsername())
            .password(passwordEncoder().encode(credentials.getPassword()))
            .roles(SYSTEM_ADMIN_ROLE)
            .build());
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
