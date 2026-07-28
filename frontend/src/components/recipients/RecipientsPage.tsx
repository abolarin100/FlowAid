import React, { useState } from "react";
import { useRecipients } from "../../hooks";
import { recipientsApi } from "../../api";
import { useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "../../hooks";
import { useOfflineRecipientSync } from "../../hooks/useOfflineRecipientSync";
import type { EnrollmentStatus } from "../../types";
import clsx from "clsx";

const STATUS_STYLES: Record<EnrollmentStatus, string> = {
  PENDING_VERIFICATION: "badge badge--yellow",
  VERIFIED: "badge badge--blue",
  ACTIVE: "badge badge--green",
  SUSPENDED: "badge badge--red",
  GRADUATED: "badge badge--gray",
};

const ELIGIBILITY_STYLES: Record<string, string> = {
  ELIGIBLE: "badge badge--green",
  NEEDS_REVIEW: "badge badge--yellow",
  INELIGIBLE: "badge badge--red",
};

const EMPTY_FORM = {
  firstName: "",
  lastName: "",
  phoneNumber: "",
  countryCode: "",
  region: "",
  preferredPaymentMethod: "",
  monthlyIncomeUsd: "",
  householdSize: "",
};

export const RecipientsPage: React.FC = () => {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useRecipients(page);
  const qc = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [queuedNotice, setQueuedNotice] = useState("");

  const refresh = () => qc.invalidateQueries({ queryKey: queryKeys.recipients(0) });

  // Offline-first: syncs any locally-queued enrollments automatically once
  // connectivity returns, and exposes the current online/pending state.
  const { isOnline, pending, syncing, sync, queueForLater } =
    useOfflineRecipientSync(refresh);

  const buildPayload = () => ({
    firstName: form.firstName,
    lastName: form.lastName,
    phoneNumber: form.phoneNumber,
    countryCode: form.countryCode.toUpperCase(),
    region: form.region || undefined,
    preferredPaymentMethod: form.preferredPaymentMethod || undefined,
    monthlyIncomeUsd: form.monthlyIncomeUsd
      ? Number(form.monthlyIncomeUsd)
      : undefined,
    householdSize: form.householdSize ? Number(form.householdSize) : undefined,
  });

  const handleSubmit = async () => {
    setError("");
    setQueuedNotice("");

    // Low-connectivity constraint: if the browser is already offline, don't
    // even attempt the request — queue it straight away.
    if (!isOnline) {
      queueForLater(buildPayload());
      setQueuedNotice(
        "You're offline — this enrollment has been saved on this device and will sync automatically once you're back online.",
      );
      setForm(EMPTY_FORM);
      setShowForm(false);
      return;
    }

    setSubmitting(true);
    try {
      await recipientsApi.create(buildPayload());
      refresh();
      setForm(EMPTY_FORM);
      setShowForm(false);
    } catch (e: any) {
      // Covers the case where isOnline is stale (e.g. flaky mobile network
      // that hasn't fired the 'offline' event yet) — a network-level failure
      // gets queued for retry instead of losing the caseworker's data entry.
      if (!e.response) {
        queueForLater(buildPayload());
        setQueuedNotice(
          "Couldn't reach the server — this enrollment has been saved on this device and will retry automatically.",
        );
        setForm(EMPTY_FORM);
        setShowForm(false);
      } else {
        setError(e.response?.data?.detail || "Failed to enroll recipient");
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleActivate = async (id: string) => {
    try {
      await recipientsApi.updateStatus(id, "ACTIVE");
      refresh();
    } catch (e: any) {
      alert(e.response?.data?.detail || "Failed to activate recipient");
    }
  };

  return (
    <div className="recipients-page">
      <header className="page-header">
        <div>
          <h1 className="page-title">Recipients</h1>
          <p className="page-subtitle">
            Enrolled individuals eligible for cash transfers
          </p>
        </div>
        <button className="btn btn-primary" onClick={() => setShowForm(true)}>
          Enroll Recipient
        </button>
      </header>

      {/* Connectivity / offline-queue status bar */}
      <div
        className="offline-status-bar"
        style={{
          display: "flex",
          alignItems: "center",
          gap: "10px",
          padding: "8px 12px",
          marginBottom: "12px",
          borderRadius: "8px",
          fontSize: "13px",
          background: isOnline ? "var(--color-green-bg, #103a1f)" : "var(--color-yellow-bg, #3a2f10)",
        }}
      >
        <span>{isOnline ? "🟢 Online" : "🟡 Offline — enrollments will queue on this device"}</span>
        {pending.length > 0 && (
          <>
            <span>· {pending.length} enrollment{pending.length === 1 ? "" : "s"} pending sync</span>
            <button
              className="btn btn-ghost btn-sm"
              onClick={sync}
              disabled={syncing || !isOnline}
            >
              {syncing ? "Syncing…" : "Sync now"}
            </button>
          </>
        )}
      </div>

      {queuedNotice && <p className="form-success" style={{ marginBottom: 12 }}>{queuedNotice}</p>}

      {showForm && (
        <div className="modal-overlay">
          <div className="modal">
            <h2 className="modal-title">Enroll Recipient</h2>
            {error && <p className="form-error">{error}</p>}
            {!isOnline && (
              <p className="form-hint" style={{ marginBottom: 8 }}>
                You're offline — this will be saved locally and synced automatically.
              </p>
            )}
            <div className="form-grid">
              <label className="form-label">
                First Name *
                <input
                  className="form-input"
                  value={form.firstName}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, firstName: e.target.value }))
                  }
                />
              </label>
              <label className="form-label">
                Last Name *
                <input
                  className="form-input"
                  value={form.lastName}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, lastName: e.target.value }))
                  }
                />
              </label>
              <label className="form-label">
                Phone Number *
                <input
                  className="form-input"
                  placeholder="+2348012345678"
                  value={form.phoneNumber}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, phoneNumber: e.target.value }))
                  }
                />
              </label>
              <label className="form-label">
                Country Code * (e.g. NG)
                <input
                  className="form-input"
                  maxLength={2}
                  value={form.countryCode}
                  onChange={(e) =>
                    setForm((f) => ({
                      ...f,
                      countryCode: e.target.value.toUpperCase(),
                    }))
                  }
                />
              </label>
              <label className="form-label">
                Region
                <input
                  className="form-input"
                  value={form.region}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, region: e.target.value }))
                  }
                />
              </label>
              <label className="form-label">
                Preferred Payment Method
                <select
                  className="form-input"
                  value={form.preferredPaymentMethod}
                  onChange={(e) =>
                    setForm((f) => ({
                      ...f,
                      preferredPaymentMethod: e.target.value,
                    }))
                  }
                >
                  <option value="">Select</option>
                  <option value="MPESA">M-Pesa</option>
                  <option value="WAVE">Wave</option>
                  <option value="BANK_TRANSFER">Bank Transfer</option>
                  <option value="CASH">Cash</option>
                </select>
              </label>
              <label className="form-label">
                Monthly Income (USD)
                <input
                  className="form-input"
                  type="number"
                  min={0}
                  value={form.monthlyIncomeUsd}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, monthlyIncomeUsd: e.target.value }))
                  }
                />
              </label>
              <label className="form-label">
                Household Size
                <input
                  className="form-input"
                  type="number"
                  min={1}
                  max={30}
                  value={form.householdSize}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, householdSize: e.target.value }))
                  }
                />
              </label>
            </div>
            <p className="form-hint">
              Vulnerability score and eligibility are computed automatically
              from income, region, and household size — no manual score entry.
            </p>
            <div className="modal-actions">
              <button
                className="btn btn-ghost"
                onClick={() => setShowForm(false)}
              >
                Cancel
              </button>
              <button
                className="btn btn-primary"
                onClick={handleSubmit}
                disabled={
                  submitting ||
                  !form.firstName ||
                  !form.lastName ||
                  !form.phoneNumber ||
                  !form.countryCode
                }
              >
                {submitting
                  ? "Enrolling..."
                  : isOnline
                    ? "Enroll Recipient"
                    : "Save for later (offline)"}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="table-wrapper">
        {isLoading ? (
          <div className="skeleton-table" />
        ) : !data || data.content.length === 0 ? (
          <p className="empty-state">
            No recipients found. Enroll your first recipient above.
          </p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Phone</th>
                <th>Country</th>
                <th>Region</th>
                <th>Status</th>
                <th>Vulnerability Score</th>
                <th>Eligibility</th>
                <th>Payment Method</th> <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((r) => (
                <tr key={r.id}>
                  <td className="cell-primary">
                    {r.firstName} {r.lastName}
                  </td>
                  <td>
                    <code className="code-cell">{r.phoneNumber}</code>
                  </td>
                  <td>{r.countryCode}</td>
                  <td>{r.region ?? "—"}</td>
                  <td>
                    <span className={clsx(STATUS_STYLES[r.enrollmentStatus])}>
                      {r.enrollmentStatus.replace(/_/g, " ")}
                    </span>
                  </td>
                  <td>
                    {r.vulnerabilityScore != null ? (
                      <div className="score-bar">
                        <div
                          className="score-fill"
                          style={{
                            width: `${r.vulnerabilityScore}%`,
                            background:
                              r.vulnerabilityScore > 70
                                ? "var(--color-red)"
                                : r.vulnerabilityScore > 40
                                  ? "var(--color-yellow)"
                                  : "var(--color-green)",
                          }}
                        />
                        <span>{r.vulnerabilityScore}</span>
                      </div>
                    ) : (
                      "—"
                    )}
                  </td>
                  <td title={r.eligibilityReason ?? undefined}>
                    {r.eligibilityDecision ? (
                      <span className={clsx(ELIGIBILITY_STYLES[r.eligibilityDecision])}>
                        {r.eligibilityDecision.replace(/_/g, " ")}
                      </span>
                    ) : (
                      "—"
                    )}
                  </td>
                  <td>{r.preferredPaymentMethod ?? "Not set"}</td>
                  <td style={{ display: "flex", gap: "6px" }}>
                    {r.enrollmentStatus !== "ACTIVE" && (
                      <button
                        className="btn btn-ghost btn-sm"
                        onClick={() => handleActivate(r.id)}
                      >
                        Activate
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {data && data.totalPages > 1 && (
        <div className="pagination">
          <button
            className="btn btn-ghost"
            onClick={() => setPage((p) => p - 1)}
            disabled={page === 0}
          >
            Previous
          </button>
          <span className="pagination-info">
            Page {page + 1} of {data.totalPages}
          </span>
          <button
            className="btn btn-ghost"
            onClick={() => setPage((p) => p + 1)}
            disabled={page >= data.totalPages - 1}
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
};
