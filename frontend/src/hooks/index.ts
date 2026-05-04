import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { dashboardApi, paymentsApi, recipientsApi, campaignsApi, donorsApi } from '../api';
import type { CreatePaymentRequest, BulkDisbursementRequest } from '../types';

// ── Query Keys ─────────────────────────────────────────────────────────────
export const queryKeys = {
  dashboard: ['dashboard', 'stats'] as const,
  campaigns: ['campaigns'] as const,
  campaign: (id: string) => ['campaigns', id] as const,
  campaignPayments: (id: string, page: number) => ['payments', 'campaign', id, page] as const,
  recipients: (page: number) => ['recipients', page] as const,
  recipient: (id: string) => ['recipients', id] as const,
  recipientPayments: (id: string) => ['payments', 'recipient', id] as const,
  donors: (page: number) => ['donors', page] as const,
};

// ── Dashboard ──────────────────────────────────────────────────────────────
export function useDashboardStats() {
  return useQuery({
    queryKey: queryKeys.dashboard,
    queryFn: dashboardApi.getStats,
    staleTime: 30_000, // refresh every 30s
    refetchInterval: 60_000,
  });
}

// ── Campaigns ──────────────────────────────────────────────────────────────
export function useCampaigns() {
  return useQuery({
    queryKey: queryKeys.campaigns,
    queryFn: campaignsApi.list,
    staleTime: 60_000,
  });
}

export function useCampaign(id: string) {
  return useQuery({
    queryKey: queryKeys.campaign(id),
    queryFn: () => campaignsApi.getById(id),
    enabled: !!id,
  });
}

export function useCampaignPayments(campaignId: string, page = 0) {
  return useQuery({
    queryKey: queryKeys.campaignPayments(campaignId, page),
    queryFn: () => paymentsApi.getByCampaign(campaignId, page),
    enabled: !!campaignId,
  });
}

// ── Recipients ─────────────────────────────────────────────────────────────
export function useRecipients(page = 0) {
  return useQuery({
    queryKey: queryKeys.recipients(page),
    queryFn: () => recipientsApi.list(page),
  });
}

export function useRecipient(id: string) {
  return useQuery({
    queryKey: queryKeys.recipient(id),
    queryFn: () => recipientsApi.getById(id),
    enabled: !!id,
  });
}

// ── Donors ─────────────────────────────────────────────────────────────────
export function useDonors(page = 0) {
  return useQuery({
    queryKey: queryKeys.donors(page),
    queryFn: () => donorsApi.list(page),
  });
}

// ── Mutations ──────────────────────────────────────────────────────────────
export function useInitiatePayment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreatePaymentRequest) => paymentsApi.initiate(req),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: queryKeys.campaignPayments(vars.campaignId, 0) });
      qc.invalidateQueries({ queryKey: queryKeys.dashboard });
    },
  });
}

export function useBulkDisbursement() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: BulkDisbursementRequest) => paymentsApi.bulkDisbursement(req),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: queryKeys.campaignPayments(vars.campaignId, 0) });
      qc.invalidateQueries({ queryKey: queryKeys.campaign(vars.campaignId) });
      qc.invalidateQueries({ queryKey: queryKeys.dashboard });
    },
  });
}
