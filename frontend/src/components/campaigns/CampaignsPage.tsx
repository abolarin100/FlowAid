import React, { useState } from 'react';
import { useCampaigns } from '../../hooks';
import type { CampaignStatus, CampaignType } from '../../types';
import { campaignsApi } from '../../api';
import { useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '../../hooks';

const STATUS_STYLES: Record<CampaignStatus, string> = {
  DRAFT:     'badge badge--gray',
  ACTIVE:    'badge badge--green',
  PAUSED:    'badge badge--yellow',
  COMPLETED: 'badge badge--blue',
  ARCHIVED:  'badge badge--red',
};

const EMPTY_FORM = {
  name: '', description: '', type: 'EMERGENCY_RELIEF' as CampaignType,
  targetCountry: '', targetRegion: '', budgetUsd: '', transferAmountUsd: '',
  startDate: '', endDate: '',
};

export const CampaignsPage: React.FC = () => {
  const { data: campaigns, isLoading } = useCampaigns();
  const qc = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async () => {
    setError('');
    setSubmitting(true);
    try {
      await campaignsApi.create({
        name: form.name,
        description: form.description || undefined,
        type: form.type,
        targetCountry: form.targetCountry || undefined,
        targetRegion: form.targetRegion || undefined,
        budgetUsd: Number(form.budgetUsd),
        transferAmountUsd: Number(form.transferAmountUsd),
        startDate: form.startDate || undefined,
        endDate: form.endDate || undefined,
      });
      qc.invalidateQueries({ queryKey: queryKeys.campaigns });
      setForm(EMPTY_FORM);
      setShowForm(false);
    } catch (e: any) {
      setError(e.response?.data?.detail || 'Failed to create campaign');
    } finally {
      setSubmitting(false);
    }
  };

  // FIX: was calling create() instead of updateStatus()
  const handleActivate = async (id: string) => {
    try {
      await campaignsApi.updateStatus(id, 'ACTIVE');
      qc.invalidateQueries({ queryKey: queryKeys.campaigns });
    } catch (e: any) {
      alert(e.response?.data?.detail || 'Failed to activate campaign');
    }
  };

  const handlePause = async (id: string) => {
    try {
      await campaignsApi.updateStatus(id, 'PAUSED');
      qc.invalidateQueries({ queryKey: queryKeys.campaigns });
    } catch (e: any) {
      alert(e.response?.data?.detail || 'Failed to pause campaign');
    }
  };

  return (
    <div className="recipients-page">
      <header className="page-header">
        <div>
          <h1 className="page-title">Campaigns</h1>
          <p className="page-subtitle">Manage cash transfer programmes</p>
        </div>
        <button className="btn btn-primary" onClick={() => setShowForm(true)}>
          New Campaign
        </button>
      </header>

      {showForm && (
        <div className="modal-overlay">
          <div className="modal">
            <h2 className="modal-title">Create Campaign</h2>
            {error && <p className="form-error">{error}</p>}
            <div className="form-grid">
              <label className="form-label">Campaign Name *
                <input className="form-input" value={form.name}
                  onChange={e => setForm(f => ({ ...f, name: e.target.value }))} />
              </label>
              <label className="form-label">Type *
                <select className="form-input" value={form.type}
                  onChange={e => setForm(f => ({ ...f, type: e.target.value as CampaignType }))}>
                  <option value="EMERGENCY_RELIEF">Emergency Relief</option>
                  <option value="LONG_TERM_TRANSFER">Long Term Transfer</option>
                  <option value="CRISIS_RESPONSE">Crisis Response</option>
                  <option value="PILOT">Pilot</option>
                </select>
              </label>
              <label className="form-label">Budget (USD) *
                <input className="form-input" type="number" value={form.budgetUsd}
                  onChange={e => setForm(f => ({ ...f, budgetUsd: e.target.value }))} />
              </label>
              <label className="form-label">Transfer Amount per Recipient (USD) *
                <input className="form-input" type="number" value={form.transferAmountUsd}
                  onChange={e => setForm(f => ({ ...f, transferAmountUsd: e.target.value }))} />
              </label>
              <label className="form-label">Target Country (2-letter code)
                <input className="form-input" maxLength={2} value={form.targetCountry}
                  onChange={e => setForm(f => ({ ...f, targetCountry: e.target.value.toUpperCase() }))} />
              </label>
              <label className="form-label">Target Region
                <input className="form-input" value={form.targetRegion}
                  onChange={e => setForm(f => ({ ...f, targetRegion: e.target.value }))} />
              </label>
              <label className="form-label">Start Date
                <input className="form-input" type="date" value={form.startDate}
                  onChange={e => setForm(f => ({ ...f, startDate: e.target.value }))} />
              </label>
              <label className="form-label">End Date
                <input className="form-input" type="date" value={form.endDate}
                  onChange={e => setForm(f => ({ ...f, endDate: e.target.value }))} />
              </label>
              <label className="form-label" style={{ gridColumn: '1 / -1' }}>Description
                <textarea className="form-input" rows={3} value={form.description}
                  onChange={e => setForm(f => ({ ...f, description: e.target.value }))} />
              </label>
            </div>
            <div className="modal-actions">
              <button className="btn btn-ghost" onClick={() => setShowForm(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={handleSubmit}
                disabled={submitting || !form.name || !form.budgetUsd || !form.transferAmountUsd}>
                {submitting ? 'Creating…' : 'Create Campaign'}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="table-wrapper">
        {isLoading ? (
          <div className="skeleton-table" />
        ) : !campaigns || campaigns.length === 0 ? (
          <p className="empty-state">No campaigns found. Create your first campaign above.</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Name</th><th>Type</th><th>Country</th>
                <th>Budget (USD)</th><th>Disbursed (USD)</th>
                <th>Per Recipient</th><th>Status</th><th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {campaigns.map((c) => (
                <tr key={c.id}>
                  <td className="cell-primary">{c.name}</td>
                  <td>{c.type.replace(/_/g, ' ')}</td>
                  <td>{c.targetCountry ?? '—'}</td>
                  <td>${Number(c.budgetUsd).toLocaleString()}</td>
                  <td>${Number(c.disbursedUsd).toLocaleString()}</td>
                  <td>${Number(c.transferAmountUsd).toLocaleString()}</td>
                  <td><span className={STATUS_STYLES[c.status]}>{c.status}</span></td>
                  <td style={{ display: 'flex', gap: '6px' }}>
                    {c.status === 'DRAFT' && (
                      <button className="btn btn-ghost btn-sm" onClick={() => handleActivate(c.id)}>
                        Activate
                      </button>
                    )}
                    {c.status === 'ACTIVE' && (
                      <button className="btn btn-ghost btn-sm" onClick={() => handlePause(c.id)}>
                        Pause
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
};
