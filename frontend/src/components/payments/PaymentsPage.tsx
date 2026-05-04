import React, { useState } from 'react';
import { useCampaignPayments, useCampaigns, useBulkDisbursement, useRecipients } from '../../hooks';
import { formatCurrency } from '../../utils/format';
import { formatDistanceToNow } from 'date-fns';
import type { PaymentStatus } from '../../types';
import clsx from 'clsx';

const STATUS_STYLES: Record<PaymentStatus, string> = {
  PENDING:    'badge badge--yellow',
  PROCESSING: 'badge badge--blue',
  COMPLETED:  'badge badge--green',
  FAILED:     'badge badge--red',
  REVERSED:   'badge badge--gray',
};

export const PaymentsPage: React.FC = () => {
  const [selectedCampaignId, setSelectedCampaignId] = useState('');
  const [page, setPage] = useState(0);
  const [showBulkModal, setShowBulkModal] = useState(false);

  const { data: campaigns } = useCampaigns();
  const { data: paymentsPage, isLoading } = useCampaignPayments(selectedCampaignId, page);
  const { data: recipientsPage } = useRecipients(0);
  const bulkMutation = useBulkDisbursement();

  const handleBulkDisbursement = async () => {
    if (!selectedCampaignId || !recipientsPage) return;
    const activeIds = recipientsPage.content
      .filter((r) => r.enrollmentStatus === 'ACTIVE')
      .map((r) => r.id);

    await bulkMutation.mutateAsync({ campaignId: selectedCampaignId, recipientIds: activeIds });
    setShowBulkModal(false);
  };

  return (
    <div className="payments-page">
      <header className="page-header">
        <div>
          <h1 className="page-title">Payments</h1>
          <p className="page-subtitle">Disburse and track cash transfers</p>
        </div>
        <button
          className="btn btn-primary"
          onClick={() => setShowBulkModal(true)}
          disabled={!selectedCampaignId}
        >
          Bulk Disbursement
        </button>
      </header>

      {/* Campaign filter */}
      <div className="filter-bar">
        <label className="filter-label" htmlFor="campaign-select">Filter by Campaign</label>
        <select
          id="campaign-select"
          className="select"
          value={selectedCampaignId}
          onChange={(e) => { setSelectedCampaignId(e.target.value); setPage(0); }}
        >
          <option value="">All campaigns</option>
          {campaigns?.map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </select>
      </div>

      {/* Payments table */}
      <div className="table-wrapper">
        {isLoading ? (
          <div className="skeleton-table" />
        ) : !paymentsPage || paymentsPage.content.length === 0 ? (
          <div className="empty-state">
            <p>{selectedCampaignId ? 'No payments found for this campaign.' : 'Select a campaign to view payments.'}</p>
          </div>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Recipient</th>
                <th>Campaign</th>
                <th>Amount</th>
                <th>Status</th>
                <th>Transfer ID</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {paymentsPage.content.map((payment) => (
                <tr key={payment.id}>
                  <td className="cell-primary">{payment.recipientName}</td>
                  <td>{payment.campaignName}</td>
                  <td>{formatCurrency(payment.amount)}</td>
                  <td>
                    <span className={clsx(STATUS_STYLES[payment.status])}>
                      {payment.status}
                    </span>
                  </td>
                  <td>
                    <code className="code-cell">{payment.externalTransferId ?? '—'}</code>
                  </td>
                  <td className="cell-muted">
                    {formatDistanceToNow(new Date(payment.createdAt), { addSuffix: true })}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Pagination */}
      {paymentsPage && paymentsPage.totalPages > 1 && (
        <div className="pagination">
          <button className="btn btn-ghost" onClick={() => setPage(p => p - 1)} disabled={page === 0}>
            Previous
          </button>
          <span className="pagination-info">
            Page {page + 1} of {paymentsPage.totalPages}
          </span>
          <button
            className="btn btn-ghost"
            onClick={() => setPage(p => p + 1)}
            disabled={page >= paymentsPage.totalPages - 1}
          >
            Next
          </button>
        </div>
      )}

      {/* Bulk Disbursement Modal */}
      {showBulkModal && (
        <div className="modal-overlay" onClick={() => setShowBulkModal(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h2 className="modal-title">Confirm Bulk Disbursement</h2>
            <p className="modal-body">
              This will initiate payments to all <strong>ACTIVE</strong> recipients
              ({recipientsPage?.content.filter(r => r.enrollmentStatus === 'ACTIVE').length ?? 0} recipients)
              in the selected campaign. This action cannot be undone.
            </p>
            <div className="modal-actions">
              <button className="btn btn-ghost" onClick={() => setShowBulkModal(false)}>Cancel</button>
              <button
                className="btn btn-danger"
                onClick={handleBulkDisbursement}
                disabled={bulkMutation.isPending}
              >
                {bulkMutation.isPending ? 'Processing...' : 'Confirm Disbursement'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
