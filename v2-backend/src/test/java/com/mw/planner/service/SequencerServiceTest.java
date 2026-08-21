package com.mw.planner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.Sequencer;
import com.mw.planner.repository.SequencerRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
public class SequencerServiceTest {

  @Mock private SequencerRepository sequencerRepository;
  @Mock private MongoTemplate mongoTemplate;

  @InjectMocks private SequencerService sequencerService;

  private static final String MWP_PREFIX = "MWP_PREFIX";
  private static final String EXISTING_PREFIX = "EXISTING_PREFIX";
  private static final Long EXISTING_SEQUENCE = 1L;

  @BeforeEach
  void setUp() {}

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(sequencerRepository);
  }

  @Test
  public void getSequenceWhenPrefixExistsShouldReturnIncrementedSequence() {
    // Given
    Sequencer existingSequencer =
        Sequencer.builder().id(EXISTING_PREFIX).sequence(EXISTING_SEQUENCE).build();
    when(sequencerRepository.findById(EXISTING_PREFIX)).thenReturn(Optional.of(existingSequencer));

    // When
    Long result = sequencerService.getSequence(EXISTING_PREFIX);

    // Then
    assertEquals(EXISTING_SEQUENCE + 1, result);
    verify(sequencerRepository, times(1)).findById(EXISTING_PREFIX);
    verify(sequencerRepository, never()).save(any(Sequencer.class));
  }

  @Test
  public void getSequenceWhenSequenceIsZeroShouldReturnOne() {
    // Given
    Sequencer sequencerWithZeroSequence =
        Sequencer.builder().id(EXISTING_PREFIX).sequence(0L).build();
    when(sequencerRepository.findById(EXISTING_PREFIX))
        .thenReturn(Optional.of(sequencerWithZeroSequence));

    // When
    Long result = sequencerService.getSequence(EXISTING_PREFIX);

    // Then
    assertEquals(1L, result);
    verify(sequencerRepository, times(1)).findById(EXISTING_PREFIX);
    verify(sequencerRepository, never()).save(any(Sequencer.class));
  }

  @Test
  public void getSequenceWhenPrefixDoesNotExistShouldReturnOne() {
    // Given
    when(sequencerRepository.findById(MWP_PREFIX)).thenReturn(Optional.empty());

    // When
    Long result = sequencerService.getSequence(MWP_PREFIX);

    // Then
    assertEquals(1L, result);
    verify(sequencerRepository, times(1)).findById(MWP_PREFIX);
    verify(sequencerRepository, never()).save(any(Sequencer.class));
  }

  @Test
  public void increaseSequencerShouldUpdateSequencerCorrectly() {
    // Given
    Sequencer existingSequencer =
        Sequencer.builder().id(EXISTING_PREFIX).sequence(EXISTING_SEQUENCE).build();
    when(sequencerRepository.findById(EXISTING_PREFIX)).thenReturn(Optional.of(existingSequencer));
    when(sequencerRepository.save(any(Sequencer.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    // When
    sequencerService.increaseSequence(EXISTING_PREFIX);
    // Then
    verify(sequencerRepository, times(1)).save(existingSequencer);
    assertEquals(EXISTING_SEQUENCE + 1, existingSequencer.getSequence());
  }

  @Test
  public void getNextSequenceAtomicShouldReturnIncrementedValueFromFindAndModify() {
    // Given
    Sequencer updated = Sequencer.builder().id(MWP_PREFIX).sequence(3L).build();
    when(mongoTemplate.findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(Sequencer.class)))
        .thenReturn(updated);

    // When
    Long result = sequencerService.getNextSequenceAtomic(MWP_PREFIX);

    // Then
    assertEquals(3L, result);
    verify(mongoTemplate, times(1))
        .findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(Sequencer.class));
  }

  @Test
  public void extractPrefixFromCampaignNameShouldReturnCorrectPrefix() {
    // Given
    String campaignName = "Campaign_Sep_17_25_0001";
    String expectedPrefix = "Campaign_Sep_17_25_";

    // When
    String result = sequencerService.extractPrefixFromCampaignName(campaignName);

    // Then
    assertEquals(expectedPrefix, result);
  }

  @Test
  public void extractPrefixFromCampaignNameShouldReturnNullForNonPatternName() {
    // Given
    String campaignName = "Summer sale campaign";

    // When
    String result = sequencerService.extractPrefixFromCampaignName(campaignName);

    // Then
    assertEquals(null, result);
  }

  @Test
  public void extractPrefixFromCampaignNameShouldReturnNullForNullInput() {
    // When
    String result = sequencerService.extractPrefixFromCampaignName(null);

    // Then
    assertEquals(null, result);
  }

  @Test
  public void extractPrefixFromCampaignNameShouldReturnNullForEmptyInput() {
    // When
    String result = sequencerService.extractPrefixFromCampaignName("");

    // Then
    assertEquals(null, result);
  }

  @Test
  public void incrementSequenceForCampaignNameWithPatternShouldUseExtractedPrefix() {
    // Given
    String campaignName = "Campaign_Sep_17_25_0001";
    String expectedPrefix = "Campaign_Sep_17_25_";
    Sequencer existingSequencer = Sequencer.builder().id(expectedPrefix).sequence(5L).build();

    when(sequencerRepository.findById(expectedPrefix)).thenReturn(Optional.of(existingSequencer));
    when(sequencerRepository.save(any(Sequencer.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    Long result = sequencerService.incrementSequenceForCampaignName(campaignName);

    // Then
    assertEquals(6L, result);
    verify(sequencerRepository, times(1)).findById(expectedPrefix);
    verify(sequencerRepository, times(1)).save(existingSequencer);
  }

  @Test
  public void incrementSequenceForCampaignNameWithoutPatternShouldUseFullName() {
    // Given
    String campaignName = "Summer sale campaign";
    Sequencer existingSequencer = Sequencer.builder().id(campaignName).sequence(2L).build();

    when(sequencerRepository.findById(campaignName)).thenReturn(Optional.of(existingSequencer));
    when(sequencerRepository.save(any(Sequencer.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    Long result = sequencerService.incrementSequenceForCampaignName(campaignName);

    // Then
    assertEquals(3L, result);
    verify(sequencerRepository, times(1)).findById(campaignName);
    verify(sequencerRepository, times(1)).save(existingSequencer);
  }

  @Test
  public void incrementSequenceForCampaignNameShouldReturnNullForNullInput() {
    // When
    Long result = sequencerService.incrementSequenceForCampaignName(null);

    // Then
    assertEquals(null, result);
    verify(sequencerRepository, never()).findById(any());
    verify(sequencerRepository, never()).save(any(Sequencer.class));
  }
}
