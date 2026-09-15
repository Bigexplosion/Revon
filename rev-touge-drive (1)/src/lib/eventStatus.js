// Event/season status + countdown helpers.

export function eventStatus(ev) {
  const now = Date.now();
  const start = new Date(ev.start_at).getTime();
  const end = new Date(ev.end_at).getTime();
  if (now < start) return 'upcoming';
  if (now > end) return 'ended';
  return 'live';
}

export function msToParts(ms) {
  const s = Math.max(0, Math.floor(ms / 1000));
  const d = Math.floor(s / 86400);
  const h = Math.floor((s % 86400) / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  return { d, h, m, s: sec };
}

export function countdownStr(ms) {
  const { d, h, m } = msToParts(ms);
  if (d > 0) return `${d}D ${h}H ${m}M`;
  if (h > 0) return `${h}H ${m}M`;
  return `${m}M`;
}

export function fmtDateTime(iso) {
  const d = new Date(iso);
  return d.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

// R points earned for a single completed run (non-event).
export function runPoints(ms) {
  if (!ms || ms <= 0) return 10;
  const p = Math.round(5000000 / ms);
  return Math.max(10, Math.min(100, p));
}