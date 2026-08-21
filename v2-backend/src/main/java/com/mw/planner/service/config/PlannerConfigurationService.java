package com.mw.planner.service.config;

import com.mw.planner.domain.PlannerConfiguration;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.dto.config.PlannerConfigurationDTO;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.configuration.ConfigurationForbiddenException;
import com.mw.planner.repository.PlannerConfigurationRepository;
import com.mw.planner.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Tenant platform configuration (ports V1's mock-only {@code workflow-config-page.tsx} to a real,
 * persisted feature). Unset sections fall back to {@link DefaultConfigurationService} defaults at
 * read time — nothing is written until a tenant actually saves a change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlannerConfigurationService {

  private final PlannerConfigurationRepository repository;
  private final DefaultConfigurationService defaultConfigurationService;
  private final UserService userService;

  public PlannerConfigurationDTO getForCompany(String companyId) {
    userService.assertCanActForCompany(companyId);
    PlannerConfiguration saved = repository.findByCompanyId(companyId).orElse(null);
    PlannerConfiguration defaults =
        defaultConfigurationService.getDefaultPlannerConfiguration(companyId);
    return PlannerConfigurationDTO.from(mergeWithDefaults(saved, defaults));
  }

  /** Per-section fallback: a section the tenant never saved is served from platform defaults. */
  private PlannerConfiguration mergeWithDefaults(
      PlannerConfiguration saved, PlannerConfiguration defaults) {
    if (saved == null) return defaults;
    return PlannerConfiguration.builder()
        .companyId(defaults.getCompanyId())
        .general(saved.getGeneral() != null ? saved.getGeneral() : defaults.getGeneral())
        .terminology(
            saved.getTerminology() != null ? saved.getTerminology() : defaults.getTerminology())
        .targeting(saved.getTargeting() != null ? saved.getTargeting() : defaults.getTargeting())
        .numberFormats(
            saved.getNumberFormats() != null
                ? saved.getNumberFormats()
                : defaults.getNumberFormats())
        .dashboard(saved.getDashboard() != null ? saved.getDashboard() : defaults.getDashboard())
        .campaign(saved.getCampaign() != null ? saved.getCampaign() : defaults.getCampaign())
        .inventory(saved.getInventory() != null ? saved.getInventory() : defaults.getInventory())
        .poi(saved.getPoi() != null ? saved.getPoi() : defaults.getPoi())
        .schedule(saved.getSchedule() != null ? saved.getSchedule() : defaults.getSchedule())
        .reports(saved.getReports() != null ? saved.getReports() : defaults.getReports())
        .filters(saved.getFilters() != null ? saved.getFilters() : defaults.getFilters())
        .approvals(saved.getApprovals() != null ? saved.getApprovals() : defaults.getApprovals())
        .bonusWorkflow(
            saved.getBonusWorkflow() != null
                ? saved.getBonusWorkflow()
                : defaults.getBonusWorkflow())
        .build();
  }

  /**
   * Upserts a tenant's configuration. The {@code bonusWorkflow} section additionally requires the
   * caller's acting company to be supplier-side (media owner) or a global admin — V1 only hid this
   * section client-side; this is the server-side enforcement that was missing.
   */
  public PlannerConfigurationDTO update(String companyId, PlannerConfigurationDTO update) {
    userService.assertCanActForCompany(companyId);

    if (update.getBonusWorkflow() != null) {
      IamUserContext context = userService.getIamUserContext();
      boolean allowed =
          Boolean.TRUE.equals(context.getIsGlobalAdmin())
              || Boolean.TRUE.equals(context.getIsSupplierSide());
      if (!allowed) {
        throw new ConfigurationForbiddenException(
            ErrorCode.CONFIGURATION_BONUS_WORKFLOW_FORBIDDEN,
            "Only media owner or admin users may configure the bonus workflow section");
      }
    }

    PlannerConfiguration existing =
        repository
            .findByCompanyId(companyId)
            .orElseGet(() -> PlannerConfiguration.builder().companyId(companyId).build());

    if (update.getGeneral() != null) existing.setGeneral(update.getGeneral().toDomain());
    if (update.getTerminology() != null)
      existing.setTerminology(update.getTerminology().toDomain());
    if (update.getTargeting() != null) existing.setTargeting(update.getTargeting().toDomain());
    if (update.getNumberFormats() != null)
      existing.setNumberFormats(update.getNumberFormats().toDomain());
    if (update.getDashboard() != null) existing.setDashboard(update.getDashboard().toDomain());
    if (update.getCampaign() != null) existing.setCampaign(update.getCampaign().toDomain());
    if (update.getInventory() != null) existing.setInventory(update.getInventory().toDomain());
    if (update.getPoi() != null) existing.setPoi(update.getPoi().toDomain());
    if (update.getSchedule() != null) existing.setSchedule(update.getSchedule().toDomain());
    if (update.getReports() != null) existing.setReports(update.getReports().toDomain());
    if (update.getFilters() != null) existing.setFilters(update.getFilters().toDomain());
    if (update.getApprovals() != null) existing.setApprovals(update.getApprovals().toDomain());
    if (update.getBonusWorkflow() != null)
      existing.setBonusWorkflow(update.getBonusWorkflow().toDomain());

    PlannerConfiguration saved = repository.save(existing);
    log.info("Updated planner configuration for companyId={}", companyId);
    return getForCompany(saved.getCompanyId());
  }
}
