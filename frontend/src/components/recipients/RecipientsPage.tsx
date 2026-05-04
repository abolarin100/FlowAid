import React, { useState } from "react";
import { useRecipients } from "../../hooks";
import { recipientsApi } from "../../api";
import { useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "../../hooks";
import type { EnrollmentStatus } from "../../types";
import clsx from "clsx";

const STATUS_STYLES: Record<EnrollmentStatus, string> = {
  PENDING_VERIFICATION: "badge badge--yellow",
  VERIFIED: "badge badge--blue",
  ACTIVE: "badge badge--green",
  SUSPENDED: "badge badge--red",
  GRADUATED: "badge badge--gray",
};

const EMPTY_FORM = {
  firstName: "",
  lastName: "",
  phoneNumber: "",
  countryCode: "",
  region: "",
  preferredPaymentMethod: "",
  vulnerabilityScore: "",
};

export const RecipientsPage: React.FC = () => {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useRecipients(page);
  const qc = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async () => {
    setError("");
    setSubmitting(true);
    try {
      await recipientsApi.create({
        firstName: form.firstName,
        lastName: form.lastName,
        phoneNumber: form.phoneNumber,
        countryCode: form.countryCode.toUpperCase(),
        region: form.region || undefined,
        preferredPaymentMethod: form.preferredPaymentMethod || undefined,
        vulnerabilityScore: form.vulnerabilityScore
          ? Number(form.vulnerabilityScore)
          : undefined,
        enrollmentStatus: "PENDING_VERIFICATION",
      } as any);
      qc.invalidateQueries({ queryKey: queryKeys.recipients(0) });
      setForm(EMPTY_FORM);
      setShowForm(false);
    } catch (e: any) {
      setError(e.response?.data?.detail || "Failed to enroll recipient");
    } finally {
      setSubmitting(false);
    }
  };
  const handleActivate = async (id: string) => {
    try {
      await recipientsApi.updateStatus(id, "ACTIVE");
      qc.invalidateQueries({ queryKey: queryKeys.recipients(0) });
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

      {showForm && (
        <div className="modal-overlay">
          <div className="modal">
            <h2 className="modal-title">Enroll Recipient</h2>
            {error && <p className="form-error">{error}</p>}
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
                Vulnerability Score (0-100)
                <input
                  className="form-input"
                  type="number"
                  min={0}
                  max={100}
                  value={form.vulnerabilityScore}
                  onChange={(e) =>
                    setForm((f) => ({
                      ...f,
                      vulnerabilityScore: e.target.value,
                    }))
                  }
                />
              </label>
            </div>
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
                {submitting ? "Enrolling..." : "Enroll Recipient"}
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
