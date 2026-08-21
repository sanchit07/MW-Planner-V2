package com.mw.planner.config;

import com.mw.planner.security.ActingCompanyHeaderInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers MVC interceptors (acting-company header membership validation). */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

  private final ActingCompanyHeaderInterceptor actingCompanyHeaderInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(actingCompanyHeaderInterceptor).addPathPatterns("/api/**");
  }
}
