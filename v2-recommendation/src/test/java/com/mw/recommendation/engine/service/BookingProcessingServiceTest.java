package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.domain.BookingData;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.ExternalBookingMessageDTO;
import com.mw.recommendation.engine.rabbitmq.BookingMessageConverter;
import com.mw.recommendation.engine.repository.BookingRepository;
import com.mw.recommendation.engine.repository.InventoryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingProcessingServiceTest {

  @Mock private BookingMessageConverter messageConverter;

  @Mock private BookingRepository bookingRepository;

  @Mock private InventoryRepository inventoryRepository;

  @InjectMocks private BookingProcessingService bookingProcessingService;

  private ExternalBookingMessageDTO testMessage;
  private Inventory testInventory;

  @BeforeEach
  void setUp() {
    testMessage =
        ExternalBookingMessageDTO.builder()
            .id("message-1")
            .dealId("deal-123")
            .slots(new ArrayList<>())
            .build();

    testInventory = new Inventory();
    testInventory.setInventoryId("inventory-123");
    testInventory.setReferenceId("ref-123");
  }

  @Test
  void testProcessBookingMessage_WithValidMessage_ProcessesSuccessfully() {
    // Arrange
    ExternalBookingMessageDTO.Slot slot = new ExternalBookingMessageDTO.Slot();
    slot.setInventoryId("inventory-123");
    slot.setStartTime(Instant.now());
    slot.setEndTime(Instant.now().plusSeconds(3600));
    slot.setTimeZone("UTC");
    testMessage.getSlots().add(slot);

    when(inventoryRepository.findFirstByInventoryId("inventory-123"))
        .thenReturn(Optional.of(testInventory));

    BookingData bookingData = new BookingData();
    bookingData.setInventoryId("inventory-123");
    bookingData.setDate(LocalDate.now());
    List<BookingData> bookingDataList = List.of(bookingData);

    when(messageConverter.convertToBookingData(any(), any())).thenReturn(bookingDataList);
    when(bookingRepository.findByInventoryIdAndDate(anyString(), any(LocalDate.class)))
        .thenReturn(Optional.empty());
    when(bookingRepository.save(any(BookingData.class))).thenReturn(bookingData);

    // Act
    bookingProcessingService.processBookingMessage(testMessage);

    // Assert
    verify(inventoryRepository).findFirstByInventoryId("inventory-123");
    verify(messageConverter).convertToBookingData(any(), any());
    verify(bookingRepository, atLeastOnce()).save(any(BookingData.class));
  }

  @Test
  void testProcessBookingMessage_WithEmptySlots_DoesNotProcess() {
    // Arrange - slots list is empty
    testMessage.setSlots(new ArrayList<>());

    // Act
    bookingProcessingService.processBookingMessage(testMessage);

    // Assert
    verify(inventoryRepository, never()).findFirstByInventoryId(anyString());
    verify(messageConverter, never()).convertToBookingData(any(), any());
  }

  @Test
  void testProcessBookingMessage_WithNullSlots_DoesNotProcess() {
    // Arrange
    testMessage.setSlots(null);

    // Act
    bookingProcessingService.processBookingMessage(testMessage);

    // Assert
    verify(inventoryRepository, never()).findFirstByInventoryId(anyString());
    verify(messageConverter, never()).convertToBookingData(any(), any());
  }

  @Test
  void testProcessBookingMessage_WithNonExistentInventory_SkipsSlot() {
    // Arrange
    ExternalBookingMessageDTO.Slot slot = new ExternalBookingMessageDTO.Slot();
    slot.setInventoryId("non-existent-inventory");
    slot.setStartTime(Instant.now());
    slot.setEndTime(Instant.now().plusSeconds(3600));
    slot.setTimeZone("UTC");
    testMessage.getSlots().add(slot);

    when(inventoryRepository.findFirstByInventoryId("non-existent-inventory"))
        .thenReturn(Optional.empty());

    // Act
    bookingProcessingService.processBookingMessage(testMessage);

    // Assert
    verify(inventoryRepository).findFirstByInventoryId("non-existent-inventory");
    verify(messageConverter, never()).convertToBookingData(any(), any());
  }

  @Test
  void testProcessBookingMessage_WithExistingBookingData_MergesData() {
    // Arrange
    ExternalBookingMessageDTO.Slot slot = new ExternalBookingMessageDTO.Slot();
    slot.setInventoryId("inventory-123");
    slot.setStartTime(Instant.now());
    slot.setEndTime(Instant.now().plusSeconds(3600));
    slot.setTimeZone("UTC");
    testMessage.getSlots().add(slot);

    when(inventoryRepository.findFirstByInventoryId("inventory-123"))
        .thenReturn(Optional.of(testInventory));

    BookingData existingBookingData = new BookingData();
    existingBookingData.setInventoryId("inventory-123");
    existingBookingData.setDate(LocalDate.now());

    BookingData newBookingData = new BookingData();
    newBookingData.setInventoryId("inventory-123");
    newBookingData.setDate(LocalDate.now());

    when(messageConverter.convertToBookingData(any(), any())).thenReturn(List.of(newBookingData));
    when(bookingRepository.findByInventoryIdAndDate(anyString(), any(LocalDate.class)))
        .thenReturn(Optional.of(existingBookingData));
    when(bookingRepository.save(any(BookingData.class))).thenReturn(existingBookingData);

    // Act
    bookingProcessingService.processBookingMessage(testMessage);

    // Assert
    verify(bookingRepository).findByInventoryIdAndDate(anyString(), any(LocalDate.class));
    verify(bookingRepository).save(existingBookingData);
  }

  @Test
  void testProcessBookingMessage_WithException_ThrowsRuntimeException() {
    // Arrange
    ExternalBookingMessageDTO.Slot slot = new ExternalBookingMessageDTO.Slot();
    slot.setInventoryId("inventory-123");
    testMessage.getSlots().add(slot);

    when(inventoryRepository.findFirstByInventoryId("inventory-123"))
        .thenThrow(new RuntimeException("Database error"));

    // Act & Assert
    assertThrows(
        RuntimeException.class, () -> bookingProcessingService.processBookingMessage(testMessage));
  }
}
