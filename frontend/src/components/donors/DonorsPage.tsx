import React, { useState } from 'react';
import { useDonors } from '../../hooks';
import type { DonorTier } from '../../types';
import { donorsApi } from '../../api';
import { useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '../../hooks';

const TIER_STYLES: Record<DonorTier, string> = {
  STANDARD:      'badge badge--gray',
  SILVER:        'badge badge--blue',
  GOLD:          'badge badge--yellow',
  PLATINUM:      'badge badge--green',
  INSTITUTIONAL: 'badge badge--purple',
};

const EMPTY_FORM = {
  firstName: '', lastName: '', email: '',
  organizationName: '', isRecurring: false,
};

export const DonorsPage: React.FC = () => {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useDonors(page);
  const qc = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async () => {
    setError('');
    setSubmitting(true);
    try {
      // FIX: use donorsApi.create() instead of raw fetch()
      await donorsApi.create({
        firstName: form.firstName,
        lastName: form.lastName,
        email: form.email,
        organizationName: form.organizationName || undefined,
        isRecurring: form.isRecurring,
      });
      qc.invalidateQueries({ queryKey: queryKeys.donors(0) });
      setForm(EMPTY_FORM);
      setShowForm(false);
    } catch (e: any) {
      setError(e.response?.data?.detail || e.message || 'Failed to register donor');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="recipients-page">
      <header className="page-header">
        <div>
          <h1 className="page-title">Donors</h1>
          <p className="page-subtitle">Individuals and organisations funding campaigns</p>
        </div>
        <button className="btn btn-primary" onClick={() => setShowForm(true)}>
          Register Donor
        </button>
      </header>

      {showForm && (
        <div className="modal-overlay">
          <div className="modal">
            <h2 className="modal-title">Register Donor</h2>
            {error && <p className="form-error">{error}</p>}
            <div className="form-grid">
              <label className="form-label">First Name *
                <input className="form-input" value={form.firstName}
                  onChange={e => setForm(f => ({ ...f, firstName: e.target.value }))} />
              </label>
              <label className="form-label">Last Name *
                <input className="form-input" value={form.lastName}
                  onChange={e => setForm(f => ({ ...f, lastName: e.target.value }))} />
              </label>
              <label className="form-label">Email *
                <input className="form-input" type="email" value={form.email}
                  onChange={e => setForm(f => ({ ...f, email: e.target.value }))} />
              </label>
              <label className="form-label">Organisation Name
                <input className="form-input" value={form.organizationName}
                  onChange={e => setForm(f => ({ ...f, organizationName: e.target.value }))} />
              </label>
              <label className="form-label checkbox-label" style={{ gridColumn: '1 / -1' }}>
                <input type="checkbox" checked={form.isRecurring}
                  onChange={e => setForm(f => ({ ...f, isRecurring: e.target.checked }))} />
                Recurring donor
              </label>
            </div>
            <div className="modal-actions">
              <button className="btn btn-ghost" onClick={() => setShowForm(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={handleSubmit}
                disabled={submitting || !form.firstName || !form.lastName || !form.email}>
                {submitting ? 'Registering…' : 'Register Donor'}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="table-wrapper">
        {isLoading ? (
          <div className="skeleton-table" />
        ) : !data || data.content.length === 0 ? (
          <p className="empty-state">No donors found. Register your first donor above.</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Organisation</th>
                <th>Tier</th>
                <th>Total Donated (USD)</th>
                <th>Recurring</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((d) => (
                <tr key={d.id}>
                  <td className="cell-primary">{d.firstName} {d.lastName}</td>
                  <td>{d.email}</td>
                  <td>{d.organizationName ?? '—'}</td>
                  <td><span className={TIER_STYLES[d.donorTier]}>{d.donorTier}</span></td>
                  <td>${Number(d.totalDonatedUsd).toLocaleString()}</td>
                  <td>{d.isRecurring ? '✓' : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {data && data.totalPages > 1 && (
        <div className="pagination">
          <button className="btn btn-ghost" onClick={() => setPage(p => p - 1)} disabled={page === 0}>
            Previous
          </button>
          <span className="pagination-info">Page {page + 1} of {data.totalPages}</span>
          <button className="btn btn-ghost" onClick={() => setPage(p => p + 1)}
            disabled={page >= data.totalPages - 1}>
            Next
          </button>
        </div>
      )}
    </div>
  );
};
