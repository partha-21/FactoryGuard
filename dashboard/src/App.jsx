import { useCallback, useEffect, useRef, useState } from 'react';
import { getJson, postJson } from './api.js';
import { COLORS, Donut, LineChart, Sparkline } from './charts.jsx';
import Pipeline from './Pipeline.jsx';
import { DeploymentsTable, IncidentsTable } from './Tables.jsx';

const NORMAL_RATE = 100;
const ZERO_SUMMARY = { deployments: 0, incidents: 0, recovered: 0, avg_recovery_seconds: 0 };

function describeState(state) {
  if (state === 'NOT_STARTED') return ['Not started', COLORS.muted];
  if (state === 'WARMING_UP') return ['Warming up', COLORS.warn];
  if (state === 'HEALTHY') return ['Healthy', COLORS.good];
  if (state === 'RECOVERING') return ['Rolling back', COLORS.warn];
  if (state === 'VERIFYING') return ['Verifying', COLORS.warn];
  if (state && state.startsWith('DEGRADED')) return ['Degraded ' + state.replace('DEGRADED', '').trim(), COLORS.bad];
  return [state || '-', COLORS.muted];
}

export default function App() {
  const [data, setData] = useState(null);
  const [user, setUser] = useState('');
  const [message, setMessage] = useState('');
  const [working, setWorking] = useState(false);
  const loading = useRef(false);

  const refresh = useCallback(async () => {
    if (loading.current) return;
    loading.current = true;
    try {
      const [status, summary, incidents, deployments] = await Promise.all([
        getJson('/api/status'), getJson('/api/summary'), getJson('/api/incidents'), getJson('/api/deployments'),
      ]);
      setData({ status, summary, incidents, deployments });
    } catch (e) {
      
    }
    loading.current = false;
  }, []);

  useEffect(() => {
    refresh();
    const id = setInterval(refresh, 2000);
    return () => clearInterval(id);
  }, [refresh]);

  useEffect(() => {
    getJson('/api/auth/me').then(u => setUser(u.username)).catch(() => {});
  }, []);

  async function act(path, label) {
    setMessage(label);
    setWorking(true);
    try {
      const result = await postJson(path);
      setMessage(result.message || result.error || '');
    } catch (e) {
      setMessage('Could not reach FactoryGuard.');
    }
    setWorking(false);
    await refresh();
    setTimeout(() => setMessage(''), 6000);
  }

  async function signOut() {
    try { await fetch('/api/auth/logout', { method: 'POST' }); } catch (e) { }
    window.location.href = '/login.html';
  }

  const s = data ? data.status : null;
  const summary = data ? data.summary : ZERO_SUMMARY;
  const samples = s ? s.samples || [] : [];
  const started = !!(s && s.started);
  const threshold = s ? s.threshold : 60;
  const epm = s && s.latest ? s.latest.eventsPerMinute : 0;
  const isOk = x => x.healthUp && x.eventsPerMinute >= threshold;
  const availability = samples.length ? Math.round(100 * samples.filter(isOk).length / samples.length) : 0;
  const [stateLabel, stateColor] = describeState(s ? s.state : 'NOT_STARTED');
  const warmFraction = started ? Math.min(1, s.secondsSinceDeploy / Math.max(1, s.warmupSeconds)) : 0;
  const rollbacks = s ? s.rollbacksInWindow : 0;
  const maxRollbacks = s ? s.maxRollbacks : 0;
  const healthy = !!s && (s.state === 'HEALTHY' || s.state === 'WARMING_UP');
  const rateClass = !started ? '' : epm >= threshold ? 'good' : 'bad';
  const availClass = !started || !samples.length ? '' : availability >= 90 ? 'good' : availability >= 60 ? 'warn' : 'bad';

  return (
    <>
      <header className="topbar">
        <div>
          <div className="wordmark">Factory<b>Guard</b></div>
          <div className="sub">Release health monitor &middot; simulated factory line</div>
        </div>
        <div className="who">
          <span>Signed in as <strong>{user || '...'}</strong></span>
          <button className="btn ghost" type="button" onClick={signOut}>Sign out</button>
        </div>
      </header>

      <main className="wrap">
        <section className="panel actions">
          <button className="btn primary" type="button" disabled={working}
                  onClick={() => act('/api/environment/start', 'Starting from scratch...')}>
            {started ? 'Restart from scratch' : 'Start monitoring from scratch'}
          </button>
          <button className="btn" type="button" disabled={working || !started}
                  onClick={() => act('/api/deploy/v1.0', 'Deploying v1.0...')}>Deploy v1.0 (healthy)</button>
          <button className="btn" type="button" disabled={working || !started}
                  onClick={() => act('/api/deploy/v1.1', 'Deploying v1.1...')}>Deploy v1.1 (has a bug)</button>
          <span className="spacer" />
          <span id="msg">{message}</span>
          <button className="btn ghost danger" type="button" disabled={working || !started}
                  onClick={() => act('/api/environment/stop', 'Stopping...')}>Stop and reset to zero</button>
        </section>
        {s && s.busyWithOther && (
          <div className="note" style={{ display: 'block' }}>
            Another user is running the demo right now. You can start yours once they stop it, or after 10 minutes without activity.
          </div>
        )}

        <section className="tiles">
          <div className="panel tile">
            <div className="label">Throughput</div>
            <div className={'value ' + rateClass}>{Math.round(epm)}<small>events/min</small></div>
            <div className="sub">threshold {threshold} events/min</div>
            <Sparkline values={samples.map(x => x.eventsPerMinute)} color={COLORS.data} max={120} />
          </div>
          <div className="panel tile">
            <div className="label">Availability</div>
            <div className={'value ' + availClass}>{availability}<small>%</small></div>
            <div className="sub">share of recent checks that passed</div>
            <Sparkline values={samples.map(x => (isOk(x) ? 1 : 0))} color={COLORS.good} max={1} />
          </div>
          <div className="panel tile">
            <div className="label">Incidents</div>
            <div className="value">{summary.incidents}</div>
            <div className="sub">{summary.recovered} recovered</div>
          </div>
          <div className="panel tile">
            <div className="label">Average recovery</div>
            <div className="value">{summary.avg_recovery_seconds}<small>s</small></div>
            <div className="sub">measured from your own incidents</div>
          </div>
        </section>

        <section className="hero">
          <div className="panel">
            <div className="ptitle">Current release</div>
            <div className="bigver">{s ? s.currentVersion : '-'}</div>
            <span className="pill" style={{ color: stateColor }}>{stateLabel}</span>
            <div className="bar" title="warm-up progress"><i style={{ width: Math.round(warmFraction * 100) + '%' }} /></div>
            <div style={{ marginTop: 14 }}>
              <div className="kv"><span>Last known-good</span><span>{s ? s.lastKnownGood : '-'}</span></div>
              <div className="kv"><span>Time since deploy</span><span>{s ? s.secondsSinceDeploy : 0} s</span></div>
              <div className="kv"><span>Deployments</span><span>{summary.deployments}</span></div>
            </div>
          </div>

          <div className="panel line-panel">
            <div className="ptitle">Production line</div>
            <Pipeline started={started} healthy={healthy} version={s ? s.currentVersion : '-'} eventsPerMinute={epm} />
            <div className="donuts">
              <Donut fraction={epm / NORMAL_RATE} color={epm >= threshold ? COLORS.good : COLORS.bad}
                     text={Math.round(100 * epm / NORMAL_RATE) + '%'} caption="Throughput vs normal (100)" />
              <Donut fraction={availability / 100}
                     color={availability >= 90 ? COLORS.good : availability >= 60 ? COLORS.warn : COLORS.bad}
                     text={availability + '%'} caption="Checks passing" />
              <Donut fraction={rollbacks / Math.max(1, maxRollbacks)} color={rollbacks ? COLORS.warn : COLORS.good}
                     text={rollbacks + '/' + maxRollbacks} caption="Rollback budget used" />
            </div>
            <div className={'empty' + (started ? '' : ' show')}>
              <div>
                <h3>Nothing is running yet</h3>
                <p>Every number here is zero because you have not started anything. Start monitoring to deploy a healthy v1.0 from scratch.</p>
                <button className="btn primary" type="button" disabled={working}
                        onClick={() => act('/api/environment/start', 'Starting from scratch...')}>
                  Start monitoring from scratch
                </button>
              </div>
            </div>
          </div>

          <div className="panel">
            <div className="ptitle">Recovery policy</div>
            <div className="kv"><span>Healthy at or above</span><span>{threshold} events/min</span></div>
            <div className="kv"><span>Checked every</span><span>{s ? s.checkIntervalSeconds : '-'} s</span></div>
            <div className="kv"><span>Warm-up after deploy</span><span>{s ? s.warmupSeconds : '-'} s</span></div>
            <div className="kv"><span>Bad checks in a row</span><span>{s ? s.failStreak : 0} / {s ? s.failuresBeforeRollback : '-'}</span></div>
            <div className="kv"><span>Rollbacks (10 min)</span><span>{rollbacks} / {maxRollbacks || '-'}</span></div>
            <div className="kv"><span>Last action</span><span>{(s && s.lastAction) || '-'}</span></div>
          </div>
        </section>

        <section className="panel chart-panel">
          <div className="ptitle">Sensor throughput, last 60 checks</div>
          <LineChart samples={samples} threshold={threshold} />
          <div className="legend"><i />events per minute<i className="t" />threshold: below it for several checks in a row triggers a rollback</div>
        </section>

        <section className="tables">
          <IncidentsTable incidents={data ? data.incidents : []} />
          <DeploymentsTable deployments={data ? data.deployments : []} />
        </section>
      </main>
    </>
  );
}
