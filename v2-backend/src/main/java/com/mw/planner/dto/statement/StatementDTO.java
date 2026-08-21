package com.mw.planner.dto.statement;

import com.mw.planner.domain.Statement;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatementDTO {

  private String id;
  private String statementNumber;
  private String companyId;
  private Statement.Status status;
  private List<Statement.StatementLine> lines;
  private double platformFeePercentage;
  private Statement.SplitConfig splitConfig;
  private String parentStatementId;
  private String splitIdentifier;
  private Map<String, Statement.SyncStatusEntry> syncStatus;
  private boolean locked;
  private Double totalMediaCost;
  private Double totalFees;
  private Double totalPlatformFee;
  private Double totalAmount;

  public static StatementDTO from(Statement s) {
    return StatementDTO.builder()
        .id(s.getId())
        .statementNumber(s.getStatementNumber())
        .companyId(s.getCompanyId())
        .status(s.getStatus())
        .lines(s.getLines())
        .platformFeePercentage(s.getPlatformFeePercentage())
        .splitConfig(s.getSplitConfig())
        .parentStatementId(s.getParentStatementId())
        .splitIdentifier(s.getSplitIdentifier())
        .syncStatus(s.getSyncStatus())
        .locked(s.isLocked())
        .totalMediaCost(s.getTotalMediaCost())
        .totalFees(s.getTotalFees())
        .totalPlatformFee(s.getTotalPlatformFee())
        .totalAmount(s.getTotalAmount())
        .build();
  }
}
