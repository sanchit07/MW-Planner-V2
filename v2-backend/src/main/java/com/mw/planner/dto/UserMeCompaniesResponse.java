package com.mw.planner.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for IAM /api/v1/users/me/companies endpoint. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMeCompaniesResponse {
  private Boolean success;
  private String message;
  private List<UserInfoResponse.Membership> data;
}
