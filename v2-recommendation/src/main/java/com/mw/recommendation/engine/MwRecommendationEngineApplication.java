package com.mw.recommendation.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.mw.recommendation.engine", "com.mw.brand.lib"})
@EnableMongoRepositories(
    basePackages = {"com.mw.recommendation.engine.repository", "com.mw.brand.lib.repository"})
public class MwRecommendationEngineApplication {
  public static void main(String[] args) {
    SpringApplication.run(MwRecommendationEngineApplication.class, args);
  }
}
