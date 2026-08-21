package com.mw.planner.service;

import com.mw.planner.domain.Creative;
import com.mw.planner.dto.creative.CreativeDTO;
import com.mw.planner.exception.creative.CreativeFileTooLargeException;
import com.mw.planner.exception.creative.CreativeInvalidFormatException;
import com.mw.planner.exception.creative.CreativeNotFoundException;
import com.mw.planner.exception.creative.CreativeTier1ReasonRequiredException;
import com.mw.planner.repository.CreativeRepository;
import com.mw.planner.service.storage.CloudStorageService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Creative asset library. Uploads go through the existing {@link CloudStorageService}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeService {

  private final CreativeRepository creativeRepository;
  private final CloudStorageService cloudStorageService;
  private final UserService userService;

  private static final Set<String> ALLOWED_MIME_PREFIXES = Set.of("video/", "image/", "audio/");

  /**
   * Per-format hard caps — Inventory has no per-panel CMS spec field yet (IMS gap), so these are
   * platform-wide defaults enforced at upload time rather than a per-inventory limit.
   */
  private static final long MAX_VIDEO_BYTES = 200L * 1024 * 1024;

  private static final long MAX_STATIC_BYTES = 20L * 1024 * 1024;
  private static final long MAX_AUDIO_BYTES = 50L * 1024 * 1024;

  public List<CreativeDTO> listForCompany(String companyId) {
    return listForCompany(companyId, null);
  }

  /** {@code tier1Status} is optional — matches the library's status filter dropdown. */
  public List<CreativeDTO> listForCompany(String companyId, Creative.Tier1Status tier1Status) {
    List<Creative> creatives = creativeRepository.findByCompanyIdAndIsActiveTrue(companyId);
    return creatives.stream()
        .filter(c -> tier1Status == null || tier1Status == c.getTier1Status())
        .map(CreativeDTO::from)
        .toList();
  }

  public CreativeDTO getById(String id) {
    return CreativeDTO.from(getOwnedByActingCompany(id));
  }

  public CreativeDTO upload(
      String companyId,
      String brandId,
      String name,
      Creative.Format format,
      Integer pixelWidth,
      Integer pixelHeight,
      Integer durationSeconds,
      List<String> tags,
      MultipartFile file) {
    validateMimeType(file.getContentType());
    validateFileSize(format, file.getSize());

    String fileUrl = cloudStorageService.uploadFile(file, "creatives/" + companyId);

    Creative creative =
        Creative.builder()
            .companyId(companyId)
            .brandId(brandId)
            .name(name)
            .format(format)
            .mimeType(file.getContentType())
            .storageKey(fileUrl)
            .fileUrl(fileUrl)
            .fileSizeBytes(file.getSize())
            .pixelWidth(pixelWidth)
            .pixelHeight(pixelHeight)
            .durationSeconds(durationSeconds)
            .tags(tags)
            .isActive(true)
            .build();

    Creative saved = creativeRepository.save(creative);
    log.info("Uploaded creative {} for companyId={}", saved.getId(), companyId);
    return CreativeDTO.from(saved);
  }

  public void deactivate(String id) {
    Creative creative = getOwnedByActingCompany(id);
    creative.setActive(false);
    creativeRepository.save(creative);
  }

  /**
   * Tier 1 approval decision (PRD §11 / creative-management spec): Processing → Accepted or
   * Inadequate. Gated by {@code planner:creatives:approve} at the controller; a reason is mandatory
   * when marking a creative Inadequate so the uploader knows what to fix.
   */
  public CreativeDTO updateTier1Status(
      String id, Creative.Tier1Status newStatus, String rejectionReason) {
    Creative creative = getOwnedByActingCompany(id);
    if (newStatus == Creative.Tier1Status.INADEQUATE
        && (rejectionReason == null || rejectionReason.isBlank())) {
      throw new CreativeTier1ReasonRequiredException(id);
    }
    creative.setTier1Status(newStatus);
    creative.setTier1RejectionReason(
        newStatus == Creative.Tier1Status.INADEQUATE ? rejectionReason : null);
    if (newStatus == Creative.Tier1Status.ACCEPTED) {
      creative.setTier1ApprovedBy(currentUserIdOrNull());
      creative.setTier1ApprovedAt(LocalDateTime.now());
    }
    Creative saved = creativeRepository.save(creative);
    log.info("Creative {} tier1Status -> {}", id, newStatus);
    return CreativeDTO.from(saved);
  }

  private String currentUserIdOrNull() {
    try {
      return userService.getIamUserContext().getUserId();
    } catch (Exception e) {
      return null;
    }
  }

  /** A creative library entry is only visible/mutable by the company that owns it. */
  private Creative getOwnedByActingCompany(String id) {
    Creative creative =
        creativeRepository.findById(id).orElseThrow(() -> new CreativeNotFoundException(id));
    if (!creative.getCompanyId().equals(userService.getActingCompanyId())
        && !userService.isCurrentUserGlobalAdmin()) {
      throw new CreativeNotFoundException(id);
    }
    return creative;
  }

  private void validateMimeType(String mimeType) {
    if (mimeType == null || ALLOWED_MIME_PREFIXES.stream().noneMatch(mimeType::startsWith)) {
      throw new CreativeInvalidFormatException(String.valueOf(mimeType));
    }
  }

  private void validateFileSize(Creative.Format format, long sizeBytes) {
    long max =
        switch (format) {
          case VIDEO -> MAX_VIDEO_BYTES;
          case AUDIO -> MAX_AUDIO_BYTES;
          case STATIC, HTML5 -> MAX_STATIC_BYTES;
        };
    if (sizeBytes > max) {
      throw new CreativeFileTooLargeException(sizeBytes, max);
    }
  }
}
