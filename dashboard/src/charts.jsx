import { useEffect, useRef } from 'react';

export const COLORS = {
  data: '#1D6FA5', good: '#1E8E5A', warn: '#B7791F', bad: '#C53030',
  brand: '#E0540A', muted: '#5F6C7B', line: '#D9E0E8', track: '#E6EBF1', ink: '#1F2933',
};

function prepare(canvas) {
  const dpr = window.devicePixelRatio || 1;
  const rect = canvas.getBoundingClientRect();
  const w = Math.max(1, Math.round(rect.width * dpr));
  const h = Math.max(1, Math.round(rect.height * dpr));
  if (canvas.width !== w || canvas.height !== h) {
    canvas.width = w;
    canvas.height = h;
  }
  const g = canvas.getContext('2d');
  g.setTransform(dpr, 0, 0, dpr, 0, 0);
  g.clearRect(0, 0, rect.width, rect.height);
  return { g, w: rect.width, h: rect.height };
}

function useCanvas(draw) {
  const ref = useRef(null);
  useEffect(() => {
    const run = () => ref.current && draw(prepare(ref.current));
    run();
    window.addEventListener('resize', run);
    return () => window.removeEventListener('resize', run);
  });
  return ref;
}

export function Sparkline({ values, color, max }) {
  const ref = useCanvas(({ g, w, h }) => {
    g.strokeStyle = COLORS.line;
    g.lineWidth = 1;
    g.beginPath();
    g.moveTo(0, h - 0.5);
    g.lineTo(w, h - 0.5);
    g.stroke();
    if (values.length < 2) return;
    const top = max || Math.max(1, ...values);
    g.strokeStyle = color;
    g.lineWidth = 2;
    g.beginPath();
    values.forEach((v, i) => {
      const x = (i / (values.length - 1)) * (w - 2) + 1;
      const y = h - 3 - (Math.min(v, top) / top) * (h - 8);
      if (i) g.lineTo(x, y); else g.moveTo(x, y);
    });
    g.stroke();
  });
  return <canvas ref={ref} />;
}

export function Donut({ fraction, color, text, caption }) {
  const ref = useCanvas(({ g, w, h }) => {
    const cx = w / 2, cy = h / 2, r = Math.min(w, h) / 2 - 8;
    g.lineWidth = 10;
    g.lineCap = 'butt';
    g.strokeStyle = COLORS.track;
    g.beginPath();
    g.arc(cx, cy, r, 0, Math.PI * 2);
    g.stroke();
    const f = Math.max(0, Math.min(1, fraction));
    if (f > 0) {
      g.strokeStyle = color;
      g.beginPath();
      g.arc(cx, cy, r, -Math.PI / 2, -Math.PI / 2 + f * Math.PI * 2);
      g.stroke();
    }
    g.fillStyle = COLORS.ink;
    g.font = '600 20px "Segoe UI", Arial, sans-serif';
    g.textAlign = 'center';
    g.textBaseline = 'middle';
    g.fillText(text, cx, cy);
  });
  return (
    <div className="donut">
      <canvas ref={ref} />
      <div className="cap">{caption}</div>
    </div>
  );
}

export function LineChart({ samples, threshold }) {
  const ref = useCanvas(({ g, w, h }) => {
    const padL = 40, padR = 12, padT = 10, padB = 22;
    const max = Math.max(120, threshold + 30, ...samples.map(s => s.eventsPerMinute));
    const y = v => padT + (1 - v / max) * (h - padT - padB);
    g.font = '11px "Segoe UI", Arial, sans-serif';
    g.fillStyle = COLORS.muted;
    g.textAlign = 'right';
    g.textBaseline = 'middle';
    g.strokeStyle = COLORS.line;
    g.lineWidth = 1;
    for (let v = 0; v <= max; v += 40) {
      g.beginPath();
      g.moveTo(padL, y(v));
      g.lineTo(w - padR, y(v));
      g.stroke();
      g.fillText(String(v), padL - 6, y(v));
    }
    g.strokeStyle = COLORS.brand;
    g.lineWidth = 2;
    g.setLineDash([6, 5]);
    g.beginPath();
    g.moveTo(padL, y(threshold));
    g.lineTo(w - padR, y(threshold));
    g.stroke();
    g.setLineDash([]);
    if (samples.length < 2) {
      g.fillStyle = COLORS.muted;
      g.textAlign = 'center';
      g.fillText('No measurements yet', (w + padL) / 2, y(max * 0.8));
      return;
    }
    const x = i => padL + (i / (samples.length - 1)) * (w - padL - padR);
    g.strokeStyle = COLORS.data;
    g.lineWidth = 2.5;
    g.beginPath();
    samples.forEach((s, i) => {
      if (i) g.lineTo(x(i), y(s.eventsPerMinute)); else g.moveTo(x(i), y(s.eventsPerMinute));
    });
    g.stroke();
    const last = samples[samples.length - 1];
    g.fillStyle = last.eventsPerMinute >= threshold ? COLORS.good : COLORS.bad;
    g.beginPath();
    g.arc(x(samples.length - 1), y(last.eventsPerMinute), 4, 0, Math.PI * 2);
    g.fill();
  });
  return <canvas ref={ref} />;
}
