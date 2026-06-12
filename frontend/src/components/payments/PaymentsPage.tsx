import React, { useState, useMemo } from "react";
import {
  useCampaignPayments,
  useCampaigns,
  useBulkDisbursement,
  useRecipients,
} from "../../hooks";
import { formatCurrency } from "../../utils/format";
import { formatDistanceToNow } from "date-fns";
import type { PaymentStatus } from "../../types";
import clsx from "clsx";
import { extractErrorMessage } from "../../utils/errors";

const STATUS_STYLES: Record<PaymentStatus, string> = {
  PENDING: "badge badge--yellow",
  PROCESSING: "badge badge--blue",
  COMPLETED: "badge badge--green",
  FAILED: "badge badge--red",
  REVERSED: "badge badge--gray",
};

export const PaymentsPage: React.FC = () => {
  const [selectedCampaignId, setSelectedCampaignId] = useState("");
  const [page, setPage] = useState(0);
  const [showBulkModal, setShowBulkModal] = useState(false);
  const [selectedRecipientIds, setSelectedRecipientIds] = useState<Set<string>>(
    new Set(),
  );
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const { data: campaigns } = useCampaigns();
  const { data: paymentsPage, isLoading } = useCampaignPayments(
    selectedCampaignId,
    page,
  );
  // Fetch a larger page so the selection modal has enough recipients to choose from
  const { data: recipientsPage } = useRecipients(0, 100);
  const bulkMutation = useBulkDisbursement();

  const activeRecipients = useMemo(
    () =>
      recipientsPage?.content.filter((r) => r.enrollmentStatus === "ACTIVE") ??
      [],
    [recipientsPage],
  );

  const openBulkModal = () => {
    // Default: pre-select all active recipients, user can deselect
    setSelectedRecipientIds(new Set(activeRecipients.map((r) => r.id)));
    setErrorMessage(null);
    setShowBulkModal(true);
  };

  const toggleRecipient = (id: string) => {
    setSelectedRecipientIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const toggleAll = () => {
    if (selectedRecipientIds.size === activeRecipients.length) {
      setSelectedRecipientIds(new Set());
    } else {
      setSelectedRecipientIds(new Set(activeRecipients.map((r) => r.id)));
    }
  };

  const handleBulkDisbursement = async () => {
    if (!selectedCampaignId || selectedRecipientIds.size === 0) return;
    setErrorMessage(null);

    try {
      await bulkMutation.mutateAsync({
        campaignId: selectedCampaignId,
        recipientIds: Array.from(selectedRecipientIds),
      });
      setShowBulkModal(false);
    } catch (err) {
      setErrorMessage(extractErrorMessage(err));
      // keep modal open so user sees the error
    }
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
          onClick={openBulkModal}
          disabled={!selectedCampaignId}
        >
          Bulk Disbursement
        </button>
      </header>

      {/* Campaign filter */}
      <div className="filter-bar">
        <label className="filter-label" htmlFor="campaign-select">
          Filter by Campaign
        </label>
        <select
          id="campaign-select"
          className="select"
          value={selectedCampaignId}
          onChange={(e) => {
            setSelectedCampaignId(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All campaigns</option>
          {campaigns?.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </div>

      {/* Global error banner (e.g. failed payment from a previous action) */}
      {errorMessage && !showBulkModal && (
        <div className="alert alert--error">
          {errorMessage}
          <button
            className="alert-dismiss"
            onClick={() => setErrorMessage(null)}
          >
            ×
          </button>
        </div>
      )}

      {/* Payments table */}
      <div className="table-wrapper">
        {isLoading ? (
          <div className="skeleton-table" />
        ) : !paymentsPage || paymentsPage.content.length === 0 ? (
          <div className="empty-state">
            <p>
              {selectedCampaignId
                ? "No payments found for this campaign."
                : "Select a campaign to view payments."}
            </p>
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
                    {payment.status === "FAILED" && payment.failureReason && (
                      <span
                        className="cell-error-reason"
                        title={payment.failureReason}
                      >
                        {" "}
                        — {payment.failureReason}
                      </span>
                    )}
                  </td>
                  <td>
                    <code className="code-cell">
                      {payment.externalTransferId ?? "—"}
                    </code>
                  </td>
                  <td className="cell-muted">
                    {formatDistanceToNow(new Date(payment.createdAt), {
                      addSuffix: true,
                    })}
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
          <button
            className="btn btn-ghost"
            onClick={() => setPage((p) => p - 1)}
            disabled={page === 0}
          >
            Previous
          </button>
          <span className="pagination-info">
            Page {page + 1} of {paymentsPage.totalPages}
          </span>
          <button
            className="btn btn-ghost"
            onClick={() => setPage((p) => p + 1)}
            disabled={page >= paymentsPage.totalPages - 1}
          >
            Next
          </button>
        </div>
      )}

      {/* Bulk Disbursement Modal */}
      {showBulkModal && (
        <div className="modal-overlay" onClick={() => setShowBulkModal(false)}>
          <div className="modal modal--lg" onClick={(e) => e.stopPropagation()}>
            <h2 className="modal-title">Bulk Disbursement</h2>
            <p className="modal-body">
              Select recipients to receive payment for{" "}
              <strong>
                {campaigns?.find((c) => c.id === selectedCampaignId)?.name}
              </strong>
              . This action cannot be undone.
            </p>

            {errorMessage && (
              <div className="alert alert--error">{errorMessage}</div>
            )}

            <div className="recipient-select-header">
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={
                    selectedRecipientIds.size === activeRecipients.length &&
                    activeRecipients.length > 0
                  }
                  onChange={toggleAll}
                />
                Select all ({activeRecipients.length} active)
              </label>
              <span className="selection-count">
                {selectedRecipientIds.size} selected
              </span>
            </div>

            <div className="recipient-select-list">
              {activeRecipients.length === 0 ? (
                <p className="empty-state-inline">
                  No active recipients available.
                </p>
              ) : (
                activeRecipients.map((r) => (
                  <label key={r.id} className="recipient-row">
                    <input
                      type="checkbox"
                      checked={selectedRecipientIds.has(r.id)}
                      onChange={() => toggleRecipient(r.id)}
                    />
                    <span className="recipient-name">
                      {r.firstName} {r.lastName}
                    </span>
                    <span className="recipient-meta">
                      {r.phoneNumber} · {r.region ?? "—"}
                    </span>
                  </label>
                ))
              )}
            </div>

            <div className="modal-actions">
              <button
                className="btn btn-ghost"
                onClick={() => setShowBulkModal(false)}
              >
                Cancel
              </button>
              <button
                className="btn btn-danger"
                onClick={handleBulkDisbursement}
                disabled={
                  bulkMutation.isPending || selectedRecipientIds.size === 0
                }
              >
                {bulkMutation.isPending
                  ? "Processing..."
                  : `Disburse to ${selectedRecipientIds.size} recipient${selectedRecipientIds.size === 1 ? "" : "s"}`}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
