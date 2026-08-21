package com.mw.planner.service.config;

import com.mw.planner.domain.CompanyBranding;
import com.mw.planner.dto.config.CompanyBrandingDTO;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.configuration.ConfigurationForbiddenException;
import com.mw.planner.repository.CompanyBrandingRepository;
import com.mw.planner.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Planner-side tenant branding (white-label + logo) — ports V1's {@code
 * configurations/admin-console-page.tsx}. Admin-only: gated on {@link
 * com.mw.planner.dto.IamUserContext#getIsGlobalAdmin()} / {@code hasSystemRole}, since there is no
 * local Company domain to attach an ownership check to (company data is proxied live from IAM).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyBrandingService {

  private final CompanyBrandingRepository repository;
  private final UserService userService;

  public CompanyBrandingDTO getForCompany(String companyId) {
    userService.assertCanActForCompany(companyId);
    return repository
        .findByCompanyId(companyId)
        .map(CompanyBrandingDTO::from)
        .orElseGet(() -> CompanyBrandingDTO.defaults(companyId));
  }

  public CompanyBrandingDTO update(String companyId, CompanyBrandingDTO update) {
    assertIsAdmin();
    CompanyBranding existing =
        repository
            .findByCompanyId(companyId)
            .orElseGet(() -> CompanyBranding.builder().companyId(companyId).build());
    existing.setWhiteLabel(update.isWhiteLabel());
    existing.setLogoUrl(update.getLogoUrl());
    CompanyBranding saved = repository.save(existing);
    log.info("Updated company branding for companyId={}", companyId);
    return CompanyBrandingDTO.from(saved);
  }

  private void assertIsAdmin() {
    var context = userService.getIamUserContext();
    boolean allowed =
        Boolean.TRUE.equals(context.getIsGlobalAdmin())
            || Boolean.TRUE.equals(context.getHasSystemRole());
    if (!allowed) {
      throw new ConfigurationForbiddenException(
          ErrorCode.COMPANY_BRANDING_FORBIDDEN,
          "Only global/system admins may edit company branding");
    }
  }
}
