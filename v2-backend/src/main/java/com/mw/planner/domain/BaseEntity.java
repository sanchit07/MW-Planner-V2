package com.mw.planner.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.*;

@Getter
@Setter
@EqualsAndHashCode
@ToString
public class BaseEntity<T> implements Serializable {

  @Id private T id;

  @CreatedBy private String createdBy;

  @LastModifiedBy private String lastModifiedBy;

  @CreatedDate private LocalDateTime createdAt;

  @LastModifiedDate private LocalDateTime updatedAt;
}
