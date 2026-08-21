package com.mw.planner.service;

import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.UserSettings;
import com.mw.planner.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Test Mode (demo data partition), ported from V1. A user with Test Mode ON creates and sees
 * "demo"-partition plans only; with it OFF they create and see "live" plans only. Campaigns with a
 * missing dataMode are treated as live (legacy records). Cross-mode by-ID access must behave like
 * the record does not exist.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestModeService {

  public static final String MODE_LIVE = "live";
  public static final String MODE_DEMO = "demo";

  private final UserSettingsRepository userSettingsRepository;
  private final SecurityContextService securityContextService;

  public boolean getTestMode(String userId) {
    return userSettingsRepository
        .findById(userId)
        .map(s -> Boolean.TRUE.equals(s.getTestMode()))
        .orElse(false);
  }

  public boolean setTestMode(String userId, boolean testMode) {
    UserSettings settings =
        userSettingsRepository
            .findById(userId)
            .orElse(UserSettings.builder().userId(userId).build());
    settings.setTestMode(testMode);
    userSettingsRepository.save(settings);
    return testMode;
  }

  /** Effective data mode of the current authenticated caller ("live" or "demo"). */
  public String getEffectiveDataMode() {
    try {
      String userId = securityContextService.getCurrentUsername();
      return getTestMode(userId) ? MODE_DEMO : MODE_LIVE;
    } catch (Exception e) {
      // No authenticated caller (public access, internal jobs): treat as live.
      return MODE_LIVE;
    }
  }

  /** True when there is an authenticated caller in the current security context. */
  public boolean hasAuthenticatedCaller() {
    try {
      String username = securityContextService.getCurrentUsername();
      return username != null && !username.isBlank();
    } catch (Exception e) {
      return false;
    }
  }

  /** True when the given campaign belongs to the caller's current data-mode partition. */
  public boolean matchesCallerMode(Campaign campaign) {
    String campaignMode =
        campaign.getDataMode() == null || campaign.getDataMode().isBlank()
            ? MODE_LIVE
            : campaign.getDataMode();
    return campaignMode.equals(getEffectiveDataMode());
  }
}
