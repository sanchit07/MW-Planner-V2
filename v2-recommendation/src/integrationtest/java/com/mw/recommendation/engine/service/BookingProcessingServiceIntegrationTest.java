package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;

import com.mw.recommendation.engine.TestcontainersConfiguration;
import com.mw.recommendation.engine.domain.BookingData;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.ExternalBookingMessageDTO;
import com.mw.recommendation.engine.repository.BookingRepository;
import com.mw.recommendation.engine.repository.InventoryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(classes = TestcontainersConfiguration.class)
class BookingProcessingServiceIntegrationTest {

  @Autowired private BookingProcessingService bookingProcessingService;

  @Autowired private BookingRepository bookingRepository;

  @Autowired private InventoryRepository inventoryRepository;

  private Inventory testInventory;
  private ExternalBookingMessageDTO testMessage;
  private String testInventoryId;

  @BeforeEach
  void setUp() {
    // Use unique inventory ID per test to avoid conflicts
    testInventoryId = "test-inventory-" + UUID.randomUUID().toString().substring(0, 8);

    // Clean up any existing test data
    inventoryRepository
        .findFirstByInventoryId(testInventoryId)
        .ifPresent(inventoryRepository::delete);

    // Create test inventory (Classic inventory - no digital fields)
    testInventory = new Inventory();
    testInventory.setInventoryId(testInventoryId);
    testInventory.setReferenceId("test-ref-" + UUID.randomUUID().toString().substring(0, 8));
    testInventory.setName("Test Inventory");
    testInventory.setClassification("Classic"); // Set as Classic inventory
    inventoryRepository.save(testInventory);

    // Create test message
    testMessage =
        ExternalBookingMessageDTO.builder()
            .id("message-" + UUID.randomUUID().toString())
            .dealId("deal-" + UUID.randomUUID().toString().substring(0, 8))
            .sspId(1)
            .bookingType("guaranteed")
            .status("active")
            .slots(new ArrayList<>())
            .build();
  }

  @AfterEach
  void tearDown() {
    // Clean up test data
    if (testInventoryId != null) {
      bookingRepository
          .findByInventoryIdAndDateRange(
              testInventoryId, LocalDate.of(2020, 1, 1), LocalDate.of(2030, 12, 31))
          .forEach(bookingRepository::delete);
      inventoryRepository
          .findFirstByInventoryId(testInventoryId)
          .ifPresent(inventoryRepository::delete);
    }
  }

  @Test
  void testProcessBookingMessage_WithValidMessage_SavesBookingData() {
    // Arrange
    ExternalBookingMessageDTO.Slot slot = new ExternalBookingMessageDTO.Slot();
    slot.setId("slot-" + UUID.randomUUID().toString());
    slot.setBookingId("booking-1");
    slot.setInventoryId(testInventoryId);
    slot.setStartTime(Instant.parse("2024-01-15T10:00:00Z"));
    slot.setEndTime(Instant.parse("2024-01-15T11:00:00Z"));
    slot.setTimeZone("UTC");
    slot.setSecondsAllocated(3600); // Required for Classic inventory
    slot.setBookingType("guaranteed");
    slot.setStatus("active");
    testMessage.getSlots().add(slot);

    // Act
    bookingProcessingService.processBookingMessage(testMessage);

    // Assert
    LocalDate expectedDate = LocalDate.of(2024, 1, 15);
    Optional<BookingData> savedBooking =
        bookingRepository.findByInventoryIdAndDate(testInventoryId, expectedDate);
    assertTrue(savedBooking.isPresent());
  }

  @Test
  void testProcessBookingMessage_WithMultipleSlots_ProcessesAll() {
    // Arrange
    ExternalBookingMessageDTO.Slot slot1 = new ExternalBookingMessageDTO.Slot();
    slot1.setId("slot-1-" + UUID.randomUUID().toString());
    slot1.setBookingId("booking-1");
    slot1.setInventoryId(testInventoryId);
    slot1.setStartTime(Instant.parse("2024-01-15T10:00:00Z"));
    slot1.setEndTime(Instant.parse("2024-01-15T11:00:00Z"));
    slot1.setTimeZone("UTC");
    slot1.setSecondsAllocated(3600); // Required for Classic inventory
    slot1.setBookingType("guaranteed");
    slot1.setStatus("active");

    ExternalBookingMessageDTO.Slot slot2 = new ExternalBookingMessageDTO.Slot();
    slot2.setId("slot-2-" + UUID.randomUUID().toString());
    slot2.setBookingId("booking-2");
    slot2.setInventoryId(testInventoryId);
    slot2.setStartTime(Instant.parse("2024-01-16T10:00:00Z"));
    slot2.setEndTime(Instant.parse("2024-01-16T11:00:00Z"));
    slot2.setTimeZone("UTC");
    slot2.setSecondsAllocated(3600); // Required for Classic inventory
    slot2.setBookingType("guaranteed");
    slot2.setStatus("active");

    testMessage.getSlots().add(slot1);
    testMessage.getSlots().add(slot2);

    // Act
    bookingProcessingService.processBookingMessage(testMessage);

    // Assert
    Optional<BookingData> booking1 =
        bookingRepository.findByInventoryIdAndDate(testInventoryId, LocalDate.of(2024, 1, 15));
    Optional<BookingData> booking2 =
        bookingRepository.findByInventoryIdAndDate(testInventoryId, LocalDate.of(2024, 1, 16));

    assertTrue(booking1.isPresent());
    assertTrue(booking2.isPresent());
  }
}
