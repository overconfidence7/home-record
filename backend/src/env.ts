export interface Bindings {
  DB: D1Database;
  MEDIA: R2Bucket;
  CACHE: KVNamespace;
  ENVIRONMENT: string;
  JWT_SECRET: string;
  GOOGLE_CLIENT_ID?: string;
  RESEND_API_KEY?: string;
  R2_ACCOUNT_ID?: string;
  R2_ACCESS_KEY_ID?: string;
  R2_SECRET_ACCESS_KEY?: string;
  R2_BUCKET?: string;
  CLAUDE_API_KEY?: string;
  FCM_SERVICE_ACCOUNT?: string;
  REVENUECAT_WEBHOOK_SECRET?: string;
}

export type TenantKind = "company" | "home";

export interface Membership {
  tenantKind: TenantKind;
  tenantId: string;
  role: string;
}

export interface Session {
  userId: string;
  memberships: Membership[];
}

export type AppEnv = { Bindings: Bindings; Variables: { session: Session } };
