const STATUS_CONFIG = {
  SUCCESS: { label: 'Success', className: 'badge-success' },
  GRANTED: { label: 'Granted', className: 'badge-success' },
  RECEIVED: { label: 'Received', className: 'badge-success' },
  PENDING: { label: 'Pending', className: 'badge-pending' },
  FAILED: { label: 'Failed', className: 'badge-failed' },
  DENIED: { label: 'Denied', className: 'badge-failed' },
  REVOKED: { label: 'Revoked', className: 'badge-failed' },
};

const StatusBadge = ({ status }) => {
  const config = STATUS_CONFIG[status?.toUpperCase()] || {
    label: status || 'Unknown',
    className: 'badge-neutral',
  };

  return (
    <span className={`status-badge ${config.className}`}>
      <span className="badge-dot" />
      {config.label}
    </span>
  );
};

export default StatusBadge;
