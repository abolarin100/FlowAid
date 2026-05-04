import React from 'react';
import type { CampaignStatus } from '../../types';
import clsx from 'clsx';

const STATUS_CONFIG: Record<CampaignStatus, { label: string; className: string }> = {
  ACTIVE:    { label: 'Active',    className: 'badge badge--green'  },
  DRAFT:     { label: 'Draft',     className: 'badge badge--gray'   },
  PAUSED:    { label: 'Paused',    className: 'badge badge--yellow' },
  COMPLETED: { label: 'Completed', className: 'badge badge--blue'   },
  ARCHIVED:  { label: 'Archived',  className: 'badge badge--gray'   },
};

export const CampaignStatusBadge: React.FC<{ status: CampaignStatus }> = ({ status }) => {
  const config = STATUS_CONFIG[status] ?? { label: status, className: 'badge badge--gray' };
  return <span className={clsx(config.className)}>{config.label}</span>;
};
