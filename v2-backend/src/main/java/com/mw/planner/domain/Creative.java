package com.mw.planner.domain;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A creative asset in the library. V1's {@code creativeAssets} table had no real assignment
 * validation behind it (see {@link com.mw.planner.domain.CreativeAssignment}); this is the same
 * asset shape, persisted the same way, with the missing validation now built on top.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "creatives")
public class Creative extends BaseEntity<String> {

  @Indexed private String companyId;
  private String brandId;
  private String name;

  private Format format;
  private String mimeType;

  @Indexed private String storageKey;
  private String fileUrl;
  private String thumbnailUrl;

  private Long fileSizeBytes;
  private Integer pixelWidth;
  private Integer pixelHeight;

  /** Null for static creatives; required for video/audio. */
  private Integer durationSeconds;

  private List<String> tags;
  @Builder.Default private boolean isActive = true;

  /**
   * Tier 1 (internal, company-level) approval status — every upload starts at {@code PROCESSING}
   * and must reach {@code ACCEPTED} before it is eligible for Creative Assignment. Distinct from
   * {@link CreativeAssignment.BindingStatus}, which tracks per-line-item binding, not the asset
   * itself.
   */
  @Indexed @Builder.Default private Tier1Status tier1Status = Tier1Status.PROCESSING;

  private String tier1RejectionReason;
  private String tier1ApprovedBy;
  private LocalDateTime tier1ApprovedAt;

  public enum Format {
    VIDEO,
    STATIC,
    AUDIO,
    HTML5
  }

  public enum Tier1Status {
    PROCESSING,
    ACCEPTED,
    INADEQUATE,
    ARCHIVE
  }

  /** {@code "16:9"}-style label derived from pixelWidth/pixelHeight at upload time. */
  public String deriveAspectRatio() {
    if (pixelWidth == null || pixelHeight == null || pixelWidth <= 0 || pixelHeight <= 0) {
      return null;
    }
    int gcd = gcd(pixelWidth, pixelHeight);
    return (pixelWidth / gcd) + ":" + (pixelHeight / gcd);
  }

  private static int gcd(int a, int b) {
    return b == 0 ? a : gcd(b, a % b);
  }
}
