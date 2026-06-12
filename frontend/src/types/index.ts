// ── Enums ─────────────────────────────────────────────────────────────────────

export type PaymentStatus =
  | "PENDING"
  | "PROCESSING"
  | "COMPLETED"
  | "FAILED"
  | "REVERSED";

export type EnrollmentStatus =
  | "PENDING_VERIFICATION"
  | "VERIFIED"
  | "ACTIVE"
  | "SUSPENDED"
  | "GRADUATED";

export type CampaignType =
  | "EMERGENCY_RELIEF"
  | "LONG_TERM_TRANSFER"
  | "CRISIS_RESPONSE"
  | "PILOT";

export type CampaignStatus =
  | "DRAFT"
  | "ACTIVE"
  | "PAUSED"
  | "COMPLETED"
  | "ARCHIVED";

export type DonorTier =
  | "STANDARD"
  | "SILVER"
  | "GOLD"
  | "PLATINUM"
  | "INSTITUTIONAL";

// ── Domain Models ─────────────────────────────────────────────────────────────

export interface Payment {
  id: string;
  recipientId: string;
  recipientName: string;
  campaignId: string;
  campaignName: string;
  amount: number;
  currency: string;
  status: PaymentStatus;
  externalTransferId?: string;
  failureReason?: string;
  initiatedAt?: string;
  completedAt?: string;
  createdAt: string;
}

export interface Recipient {
  id: string;
  firstName: string;
  lastName: string;
  phoneNumber: string;
  countryCode: string;
  region?: string;
  preferredPaymentMethod?: string;
  enrollmentStatus: EnrollmentStatus;
  vulnerabilityScore?: number;
  createdAt: string;
  updatedAt: string;
}

export interface Campaign {
  id: string;
  name: string;
  description?: string;
  type: CampaignType;
  status: CampaignStatus;
  targetCountry?: string;
  targetRegion?: string;
  budgetUsd: number;
  disbursedUsd: number;
  transferAmountUsd: number;
  startDate?: string;
  endDate?: string;
  createdAt: string;
}

export interface Donor {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  organizationName?: string;
  donorTier: DonorTier;
  totalDonatedUsd: number;
  isRecurring: boolean;
  createdAt: string;
}

export interface DashboardStats {
  activeRecipients: number;
  activeCampaigns: number;
  totalDisbursedUsd: number;
  completedPayments: number;
  paymentSuccessRate: number;
  totalDonors: number;
}

// ── API Shapes ────────────────────────────────────────────────────────────────

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CreatePaymentRequest {
  recipientId: string;
  campaignId: string;
  amount: number;
  currency: string;
}

export interface BulkDisbursementRequest {
  campaignId: string;
  recipientIds: string[];
}

export interface ApiError {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  fieldErrors?: Record<string, string>;
  errorId?: string;
  timestamp?: string;
}
