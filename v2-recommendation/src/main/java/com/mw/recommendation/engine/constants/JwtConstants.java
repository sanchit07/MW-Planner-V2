package com.mw.recommendation.engine.constants;

/** Constants for JWT token claims and security-related strings. */
public final class JwtConstants {

  private JwtConstants() {
    // Utility class - prevent instantiation
  }

  // JWT Claim Names
  public static final String CLAIM_SUBSCRIPTIONS = "subscriptions";
  public static final String CLAIM_IS_GLOBAL_ADMIN = "is_global_admin";
  public static final String CLAIM_HAS_SYSTEM_ROLE = "has_system_role";
  public static final String CLAIM_SYSTEM_PERMISSIONS = "system_permissions";
  public static final String CLAIM_PRIMARY_COMPANY_ID = "primary_company_id";
  public static final String CLAIM_PERMISSIONS = "permissions";

  // Role and Permission Prefixes
  public static final String ROLE_PREFIX = "ROLE_";

  // Permission Wildcard
  public static final String WILDCARD_SUFFIX = ":*";

  // Common Actions
  public static final String ACTION_READ = "read";
  public static final String ACTION_CREATE = "create";
  public static final String ACTION_UPDATE = "update";
  public static final String ACTION_DELETE = "delete";

  // Wildcard segment in permission (domain:resource:action)
  public static final String WILDCARD = "*";

  // Common CRUD actions for wildcard expansion
  public static final String[] CRUD_ACTIONS = {
    ACTION_READ, ACTION_CREATE, ACTION_UPDATE, ACTION_DELETE
  };
}
