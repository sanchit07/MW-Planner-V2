package com.mw.recommendation.engine.v3.config;

import com.mw.recommendation.engine.v3.support.BoundedExecutor;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * v3-owned beans. The v3 pipeline never shares v1/v2 executors — its own virtual-thread executor
 * plus bounded gates keep v3 load from affecting the existing pipelines or exhausting the shared
 * MongoDB pool.
 */
@Configuration
@EnableConfigurationProperties(V3Properties.class)
@org.springframework.data.mongodb.repository.config.EnableMongoRepositories(
    basePackages = "com.mw.recommendation.engine.v3.repository")
public class V3Config {

  /** Unbounded VT executor for CPU/light work (scoring fan-out). */
  @Bean("v3TaskExecutor")
  public VirtualThreadTaskExecutor v3TaskExecutor() {
    return new VirtualThreadTaskExecutor("v3-vt-");
  }

  /** Async executor for the pipeline entry point, propagating the caller's SecurityContext. */
  @Bean("v3AsyncExecutor")
  public Executor v3AsyncExecutor() {
    return withSecurityContext(new VirtualThreadTaskExecutor("v3-async-vt-"));
  }

  /** Bounded gate for DB-bound fan-out (audience batches, result inserts). */
  @Bean("v3DbExecutor")
  public BoundedExecutor v3DbExecutor(
      @Qualifier("v3TaskExecutor") VirtualThreadTaskExecutor executor, V3Properties props) {
    return new BoundedExecutor(executor, props.getConcurrency().getDbParallelism());
  }

  /**
   * Bounded gate for Measure API batch fan-out. SecurityContext is propagated so Measure calls
   * carry the caller's bearer token (the v1 pipeline's auto-select calls run unauthenticated).
   */
  @Bean("v3MeasureExecutor")
  public BoundedExecutor v3MeasureExecutor(
      @Qualifier("v3TaskExecutor") VirtualThreadTaskExecutor executor, V3Properties props) {
    return new BoundedExecutor(
        withSecurityContext(executor), props.getConcurrency().getMeasureParallelism());
  }

  // NOTE: no RestTemplate @Bean here on purpose — a second RestTemplate bean in the context
  // breaks existing tests that @MockBean an unqualified RestTemplate (zero-impact contract).
  // MeasureV3Client builds its own instance from RestTemplateBuilder with v3 timeouts.

  private static Executor withSecurityContext(Executor delegate) {
    return runnable -> {
      SecurityContext context = SecurityContextHolder.getContext();
      delegate.execute(
          () -> {
            try {
              SecurityContextHolder.setContext(context);
              runnable.run();
            } finally {
              SecurityContextHolder.clearContext();
            }
          });
    };
  }
}
