const time = t => (t ? new Date(t).toLocaleTimeString() : '');

const stamp = t => (t ? new Date(t).toLocaleString() : '');

export function IncidentsTable({ incidents }) {
  return (
    <div className="panel">
      <div className="ptitle">Incident timeline</div>
      <table>
        <thead>
          <tr>
            <th>Detected</th>
            <th>Failed release</th>
            <th>What was detected</th>
            <th>Time to detect</th>
            <th>Recovery time</th>
            <th>Final verification</th>
            <th>Result</th>
          </tr>
        </thead>
        <tbody>
          {incidents.length === 0 && <tr className="none"><td colSpan="7">No incidents yet.</td></tr>}
          {incidents.map(i => {
            const cls = i.outcome === 'RECOVERED' ? 'good' : i.outcome === 'OPEN' ? 'warn' : 'bad';
            const measured = i.detected_events_per_minute != null
              ? i.detected_events_per_minute.toFixed(1) + ' events/min (threshold ' + i.threshold + '), health ' + (i.detected_health_up ? 'UP' : 'DOWN')
              : i.metrics;
            return (
              <tr key={i.id}>
                <td>{stamp(i.detected_at)}</td>
                <td>
                  <strong>{i.version}</strong>
                  <small>rolled back to {i.rollback_target || 'nothing (no target)'}</small>
                </td>
                <td>
                  {i.reason}
                  <small>{measured}</small>
                </td>
                <td>{i.detection_seconds != null ? i.detection_seconds + ' s after deploy' : ''}</td>
                <td>{i.recovery_seconds != null ? i.recovery_seconds + ' s' : ''}</td>
                <td>{i.verification || ''}</td>
                <td className={'tag ' + cls}>{i.outcome}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

export function DeploymentsTable({ deployments }) {
  return (
    <div className="panel">
      <div className="ptitle">Deployments</div>
      <table>
        <thead><tr><th>Time</th><th>Version</th><th>Status</th><th>Note</th></tr></thead>
        <tbody>
          {deployments.length === 0 && <tr className="none"><td colSpan="4">No deployments yet.</td></tr>}
          {deployments.map(d => {
            const cls = d.status === 'HEALTHY' ? 'good' : d.status === 'ROLLED_BACK' ? 'bad' : 'warn';
            return (
              <tr key={d.id}>
                <td>{time(d.created_at)}</td>
                <td>{d.version}</td>
                <td className={'tag ' + cls}>{d.status}</td>
                <td>{d.note}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
