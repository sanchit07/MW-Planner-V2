package com.mw.planner.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "agencies")
public class Agency extends BaseEntity<String> {

  @NotBlank(message = "validation.agency_name_required")
  @Size(max = 100, message = "validation.agency_name_size")
  private String name;

  @Size(max = 50, message = "validation.media_owner_id_size")
  private String mediaOwnerId;

  @Email(message = "validation.company_email_format")
  @Size(max = 100, message = "validation.company_email_size")
  private String companyEmail;

  @NotBlank(message = "validation.country_id_required")
  @Size(max = 50, message = "validation.country_id_size")
  private String countryId;

  @Size(max = 50, message = "validation.company_id_size")
  private String companyId; // company_id that created in account portal

  private Integer seatId;

  private String brandRefId;

  private boolean activated;
}
