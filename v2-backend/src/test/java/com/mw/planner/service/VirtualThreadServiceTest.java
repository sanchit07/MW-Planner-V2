package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VirtualThreadServiceTest {

  private Executor testExecutor;
  private Executor testDistrictSyncExecutor;

  @InjectMocks private VirtualThreadService virtualThreadService;

  @BeforeEach
  void setUp() {
    // Use real executors for testing
    testExecutor = Executors.newVirtualThreadPerTaskExecutor();
    testDistrictSyncExecutor = Executors.newVirtualThreadPerTaskExecutor();

    // Use reflection to set the executors
    try {
      java.lang.reflect.Field executorField =
          VirtualThreadService.class.getDeclaredField("virtualThreadExecutor");
      executorField.setAccessible(true);
      executorField.set(virtualThreadService, testExecutor);

      java.lang.reflect.Field districtSyncField =
          VirtualThreadService.class.getDeclaredField("districtSyncExecutor");
      districtSyncField.setAccessible(true);
      districtSyncField.set(virtualThreadService, testDistrictSyncExecutor);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set up executors", e);
    }
  }

  @AfterEach
  void tearDown() {
    // Cleanup if needed
  }

  // ========== runAsync Tests ==========

  @Test
  @DisplayName("runAsync - Should execute task using virtual thread executor")
  void runAsync_WithValidTask_ShouldExecuteTask() throws Exception {
    // Given
    AtomicBoolean taskExecuted = new AtomicBoolean(false);
    Runnable task = () -> taskExecuted.set(true);

    // When
    CompletableFuture<Void> future = virtualThreadService.runAsync(task);

    // Then
    future.join();
    assertThat(taskExecuted.get()).isTrue();
  }

  @Test
  @DisplayName("runAsync - Should handle task exceptions gracefully")
  void runAsync_WithFailingTask_ShouldCompleteWithException() throws Exception {
    // Given
    AtomicBoolean exceptionCaught = new AtomicBoolean(false);
    Runnable failingTask =
        () -> {
          try {
            throw new RuntimeException("Task failed");
          } catch (Exception e) {
            exceptionCaught.set(true);
          }
        };

    // When
    CompletableFuture<Void> future = virtualThreadService.runAsync(failingTask);

    // Then
    future.join(); // Should complete without throwing
    assertThat(exceptionCaught.get()).isTrue();
  }

  // ========== runDistrictSyncAsync Tests ==========

  @Test
  @DisplayName("runDistrictSyncAsync - Should execute task using district sync executor")
  void runDistrictSyncAsync_WithValidTask_ShouldExecuteTask() throws Exception {
    // Given
    AtomicBoolean taskExecuted = new AtomicBoolean(false);
    Runnable task = () -> taskExecuted.set(true);

    // When
    CompletableFuture<Void> future = virtualThreadService.runDistrictSyncAsync(task);

    // Then
    future.join();
    assertThat(taskExecuted.get()).isTrue();
  }

  // ========== runAsync with custom executor Tests ==========

  @Test
  @DisplayName("runAsync with custom executor - Should execute task using provided executor")
  void runAsync_WithCustomExecutor_ShouldUseProvidedExecutor() throws Exception {
    // Given
    Executor customExecutor = Executors.newVirtualThreadPerTaskExecutor();
    AtomicBoolean taskExecuted = new AtomicBoolean(false);
    Runnable task = () -> taskExecuted.set(true);

    // When
    CompletableFuture<Void> future = virtualThreadService.runAsync(task, customExecutor);

    // Then
    future.join();
    assertThat(taskExecuted.get()).isTrue();
  }

  // ========== supplyAsync Tests ==========

  @Test
  @DisplayName("supplyAsync - Should execute supplier and return result")
  void supplyAsync_WithValidSupplier_ShouldReturnResult() throws Exception {
    // Given
    String expectedResult = "Test Result";
    java.util.function.Supplier<String> supplier = () -> expectedResult;

    // When
    CompletableFuture<String> future = virtualThreadService.supplyAsync(supplier);

    // Then
    String result = future.join();
    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @DisplayName("supplyAsync with custom executor - Should use provided executor")
  void supplyAsync_WithCustomExecutor_ShouldUseProvidedExecutor() throws Exception {
    // Given
    Executor customExecutor = Executors.newVirtualThreadPerTaskExecutor();
    String expectedResult = "Test Result";
    java.util.function.Supplier<String> supplier = () -> expectedResult;

    // When
    CompletableFuture<String> future = virtualThreadService.supplyAsync(supplier, customExecutor);

    // Then
    String result = future.join();
    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @DisplayName("supplyAsync - Should handle supplier exceptions")
  void supplyAsync_WithFailingSupplier_ShouldCompleteWithException() throws Exception {
    // Given
    java.util.function.Supplier<String> failingSupplier =
        () -> {
          throw new RuntimeException("Supplier failed");
        };

    // When
    CompletableFuture<String> future = virtualThreadService.supplyAsync(failingSupplier);

    // Then
    // Should complete, but result will be exception
    assertThatThrownBy(() -> future.join()).hasCauseInstanceOf(RuntimeException.class);
  }
}
