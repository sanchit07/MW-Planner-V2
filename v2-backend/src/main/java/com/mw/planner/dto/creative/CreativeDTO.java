package com.mw.planner.dto.creative;

import com.mw.planner.domain.Creative;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreativeDTO {

  private String id;
  private String companyId;
  private String brandId;
  private String name;
  private Creative.Format format;
  private String mimeType;
  private String fileUrl;
  private String thumbnailUrl;
  private Long fileSizeBytes;
  private Integer pixelWidth;
  private Integer pixelHeight;
  private String aspectRatio;
  private Integer durationSeconds;
  private List<String> tags;
  private boolean isActive;
  private Creative.Tier1Status tier1Status;
  private String tier1RejectionReason;
  private String tier1ApprovedBy;
  private LocalDateTime tier1ApprovedAt;

  public static CreativeDTO from(Creative c) {
    return CreativeDTO.builder()
        .id(c.getId())
        .companyId(c.getCompanyId())
        .brandId(c.getBrandId())
        .name(c.getName())
        .format(c.getFormat())
        .mimeType(c.getMimeType())
        .fileUrl(c.getFileUrl())
        .thumbnailUrl(c.getThumbnailUrl())
        .fileSizeBytes(c.getFileSizeBytes())
        .pixelWidth(c.getPixelWidth())
        .pixelHeight(c.getPixelHeight())
        .aspectRatio(c.deriveAspectRatio())
        .durationSeconds(c.getDurationSeconds())
        .tags(c.getTags())
        .isActive(c.isActive())
        .tier1Status(c.getTier1Status())
        .tier1RejectionReason(c.getTier1RejectionReason())
        .tier1ApprovedBy(c.getTier1ApprovedBy())
        .tier1ApprovedAt(c.getTier1ApprovedAt())
        .build();
  }
}
