package com.mw.planner.config;

import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuration for virtual threads to handle async tasks efficiently. Virtual threads provide high
 * concurrency with minimal memory overhead.
 */
@Slf4j
@EnableAsync
@Configuration
public class VirtualThreadConfig implements AsyncConfigurer {

  /**
   * Virtual thread task executor for general async operations. Uses virtual threads for high
   * concurrency with minimal memory overhead.
   */
  @Bean("virtualThreadTaskExecutor")
  public VirtualThreadTaskExecutor virtualThreadTaskExecutor() {
    return new VirtualThreadTaskExecutor("mw-planner-vt-");
  }

  /**
   * Virtual thread task executor specifically for district sync operations. Optimized for handling
   * large-scale district synchronization with external APIs.
   */
  @Bean("districtSyncTaskExecutor")
  public VirtualThreadTaskExecutor districtSyncTaskExecutor() {
    return new VirtualThreadTaskExecutor("district-sync-vt-");
  }

  /**
   * Configure the default async executor to use virtual threads. This will be used by @Async
   * annotations without specifying an executor.
   */
  @Override
  public Executor getAsyncExecutor() {
    return virtualThreadTaskExecutor();
  }

  /**
   * Configure exception handler for async operations. Provides centralized error handling for
   * virtual thread operations.
   */
  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return (ex, method, params) -> {
      log.error("Async method '{}' threw exception: {}", method.getName(), ex.getMessage(), ex);
    };
  }
}
