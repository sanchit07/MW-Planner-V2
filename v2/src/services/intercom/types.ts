export interface IntercomTokenResponse {
  /** Server-signed HS256 JWT, passed as intercom_user_jwt when booting the Messenger. */
  token: string;
  /** Intercom workspace App ID. */
  app_id: string;
}
