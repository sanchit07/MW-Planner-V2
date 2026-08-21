package com.mw.planner.dto.statement;

import com.mw.planner.domain.Statement;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatementSplitRequestDTO {
  @NotNull private Statement.SplitMethod method;

  /** Only read for CUSTOM — Equal/Monthly/Weekly/Campaign-based are computed server-side. */
  private List<Statement.Split> customSplits;
}
