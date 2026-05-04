import axios, { AxiosError } from 'axios';
import type {
  Payment, Recipient, Campaign, Donor,
  DashboardStats, Page,
  CreatePaymentRequest, BulkDisbursementRequest,
  ApiError,
} from '../types';

const BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1';

export const apiClient = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15_000,
});

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

apiClient.interceptors.response.use(
  (res) => res,
  (error: AxiosError<ApiError>) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('auth_token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export const dashboardApi = {
  getStats: () =>
    apiClient.get<DashboardStats>('/dashboard/stats').then((r) => r.data),
};

export const paymentsApi = {
  initiate: (body: CreatePaymentRequest) =>
    apiClient.post<Payment>('/payments', body).then((r) => r.data),

  bulkDisbursement: (body: BulkDisbursementRequest) =>
    apiClient.post<Payment[]>('/payments/bulk', body).then((r) => r.data),

  getByCampaign: (campaignId: string, page = 0, size = 25) =>
    apiClient
      .get<Page<Payment>>(`/payments/campaign/${campaignId}`, { params: { page, size } })
      .then((r) => r.data),

  getByRecipient: (recipientId: string, page = 0, size = 25) =>
    apiClient
      .get<Page<Payment>>(`/payments/recipient/${recipientId}`, { params: { page, size } })
      .then((r) => r.data),
};

export const recipientsApi = {
  list: (page = 0, size = 25) =>
    apiClient.get<Page<Recipient>>('/recipients', { params: { page, size } }).then((r) => r.data),

  getById: (id: string) =>
    apiClient.get<Recipient>(`/recipients/${id}`).then((r) => r.data),

  create: (body: {
    firstName: string; lastName: string; phoneNumber: string;
    countryCode: string; region?: string;
    preferredPaymentMethod?: string; vulnerabilityScore?: number;
  }) =>
    apiClient.post<Recipient>('/recipients', body).then((r) => r.data),

  updateStatus: (id: string, status: string) =>
    apiClient.patch<Recipient>(`/recipients/${id}/status`, { status }).then((r) => r.data),
};

export const campaignsApi = {
  list: () =>
    apiClient.get<Campaign[]>('/campaigns').then((r) => r.data),

  getById: (id: string) =>
    apiClient.get<Campaign>(`/campaigns/${id}`).then((r) => r.data),

  create: (body: {
    name: string; description?: string; type: string;
    targetCountry?: string; targetRegion?: string;
    budgetUsd: number; transferAmountUsd: number;
    startDate?: string; endDate?: string;
  }) =>
    apiClient.post<Campaign>('/campaigns', body).then((r) => r.data),

  // FIX: was calling create() instead of patching status
  updateStatus: (id: string, status: string) =>
    apiClient.patch<Campaign>(`/campaigns/${id}/status`, { status }).then((r) => r.data),
};

export const donorsApi = {
  list: (page = 0, size = 25) =>
    apiClient.get<Page<Donor>>('/donors', { params: { page, size } }).then((r) => r.data),

  getById: (id: string) =>
    apiClient.get<Donor>(`/donors/${id}`).then((r) => r.data),

  // FIX: was missing — DonorsPage was using raw fetch() instead
  create: (body: {
    firstName: string; lastName: string; email: string;
    organizationName?: string; isRecurring?: boolean;
  }) =>
    apiClient.post<Donor>('/donors', body).then((r) => r.data),
};
