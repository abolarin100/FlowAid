import React from 'react';
import { useDashboardStats, useCampaigns } from '../../hooks';
import { formatCurrency, formatPercent } from '../../utils/format';
import { CampaignStatusBadge } from '../campaigns/CampaignStatusBadge';

const StatCard: React.FC<{
  label: string;
  value: string | number;
  sub?: string;
  accent?: boolean;
}> = ({ label, value, sub, accent }) => (
  <div className={`stat-card ${accent ? 'stat-card--accent' : ''}`}>
    <span className="stat-label">{label}</span>
    <span className="stat-value">{value}</span>
    {sub && <span className="stat-sub">{sub}</span>}
  </div>
);

export const DashboardPage: React.FC = () => {
  const { data: stats, isLoading: statsLoading } = useDashboardStats();
  const { data: campaigns, isLoading: campaignsLoading } = useCampaigns();

  const activeCampaigns = campaigns?.filter((c) => c.status === 'ACTIVE') ?? [];

  if (statsLoading) {
    return (
      <div className="page-loading">
        <div className="spinner" />
        <p>Loading platform data...</p>
      </div>
    );
  }

  return (
    <div className="dashboard-page">
      <header className="page-header">
        <div>
          <h1 className="page-title">Operations Dashboard</h1>
          <p className="page-subtitle">Real-time overview of disbursements &amp; program health</p>
        </div>
      </header>

      {/* KPI Grid */}
      <section className="stats-grid">
        <StatCard
          label="Total Disbursed"
          value={formatCurrency(stats?.totalDisbursedUsd ?? 0)}
          sub="Lifetime USD"
          accent
        />
        <StatCard
          label="Completed Payments"
          value={(stats?.completedPayments ?? 0).toLocaleString()}
          sub="All time"
        />
        <StatCard
          label="Success Rate"
          value={formatPercent(stats?.paymentSuccessRate ?? 0)}
          sub="Last 30 days"
        />
        <StatCard
          label="Active Recipients"
          value={(stats?.activeRecipients ?? 0).toLocaleString()}
          sub="Enrolled &amp; eligible"
        />
        <StatCard
          label="Active Campaigns"
          value={stats?.activeCampaigns ?? 0}
          sub="Currently running"
        />
        <StatCard
          label="Total Donors"
          value={(stats?.totalDonors ?? 0).toLocaleString()}
          sub="Individual &amp; institutional"
        />
      </section>

      {/* Active Campaigns Table */}
      <section className="section">
        <h2 className="section-title">Active Campaigns</h2>
        {campaignsLoading ? (
          <div className="skeleton-table" />
        ) : activeCampaigns.length === 0 ? (
          <p className="empty-state">No active campaigns.</p>
        ) : (
          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Campaign</th>
                  <th>Country</th>
                  <th>Type</th>
                  <th>Status</th>
                  <th>Budget</th>
                  <th>Disbursed</th>
                  <th>Progress</th>
                </tr>
              </thead>
              <tbody>
                {activeCampaigns.map((campaign) => {
                  const pct = campaign.budgetUsd > 0
                    ? Math.min(100, (campaign.disbursedUsd / campaign.budgetUsd) * 100)
                    : 0;
                  return (
                    <tr key={campaign.id}>
                      <td className="cell-primary">{campaign.name}</td>
                      <td>{campaign.targetCountry ?? '—'}</td>
                      <td><span className="type-badge">{campaign.type.replace('_', ' ')}</span></td>
                      <td><CampaignStatusBadge status={campaign.status} /></td>
                      <td>{formatCurrency(campaign.budgetUsd)}</td>
                      <td>{formatCurrency(campaign.disbursedUsd)}</td>
                      <td>
                        <div className="progress-bar">
                          <div className="progress-fill" style={{ width: `${pct}%` }} />
                          <span className="progress-label">{pct.toFixed(1)}%</span>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
};
