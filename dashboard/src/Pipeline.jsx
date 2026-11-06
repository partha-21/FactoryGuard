import { COLORS } from './charts.jsx';

const NODE_W = 104;
const XS = [31, 189, 347, 505];

export default function Pipeline({ started, healthy, version, eventsPerMinute }) {
  const speed = started ? Math.max(0.35, Math.min(2.5, 100 / Math.max(10, eventsPerMinute))) : 1;
  const dots = [
    started ? COLORS.good : COLORS.muted,
    started ? COLORS.good : COLORS.muted,
    started ? (healthy ? COLORS.good : COLORS.bad) : COLORS.muted,
    started ? COLORS.data : COLORS.muted,
  ];
  const nodes = [
    ['Sensors', 'simulator'],
    ['MQTT broker', 'mosquitto'],
    ['Demo app', started ? version : '-'],
    ['FactoryGuard', 'watch and recover'],
  ];
  return (
    <svg className="pipeline" viewBox="0 0 640 130" role="img" aria-label="Sensors to broker to app to FactoryGuard">
      {[0, 1, 2].map(i => {
        const x1 = XS[i] + NODE_W;
        const x2 = XS[i + 1];
        return (
          <g key={i}>
            <line className="belt" x1={x1} y1="62" x2={x2} y2="62" />
            <line className={'belt-flow' + (started ? ' run' : '')} style={{ animationDuration: speed + 's' }}
                  x1={x1} y1="62" x2={x2} y2="62" />
          </g>
        );
      })}
      {nodes.map(([title, sub], i) => (
        <g key={title}>
          <rect className="node" x={XS[i]} y="30" width={NODE_W} height="64" rx="6" />
          <text className="node-title" x={XS[i] + NODE_W / 2} y="58" textAnchor="middle">{title}</text>
          <text className="node-sub" x={XS[i] + NODE_W / 2} y="76" textAnchor="middle">{sub}</text>
          <circle cx={XS[i] + NODE_W - 9} cy="41" r="4" fill={dots[i]} />
        </g>
      ))}
      <text className="node-sub" x="320" y="116" textAnchor="middle">
        {Math.round(eventsPerMinute)} messages/min reaching the app
      </text>
    </svg>
  );
}
