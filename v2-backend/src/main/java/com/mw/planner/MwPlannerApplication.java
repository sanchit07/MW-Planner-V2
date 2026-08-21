package com.mw.planner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {"com.mw.planner", "com.mw.brand.lib"})
@EnableMongoRepositories(
    basePackages = {"com.mw.planner.repository", "com.mw.brand.lib.repository"})
@EnableScheduling
public class MwPlannerApplication {
  public static void main(String[] args) {
    SpringApplication.run(MwPlannerApplication.class, args);
  }
}
