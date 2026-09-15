// =============================================================================
// SIMULATION MODE — in-race vehicle movement, checkpoints, and timing are all
// simulated here. To swap in real GPS tracking later, replace the race loop in
// Race.jsx (which drives `progress` from elapsed time) with a loop driven by
// real geolocation fixes, and replace posAtProgress with a projection of the
// real lat/lng onto the route polyline. The checkpoint/finish logic (sequential
// clearance, all-cleared-to-finish rule) can stay as-is.
// =============================================================================

export const SIM_DURATION_MS = 10000; // default ~10s run at 1x

function mulberry32(a) {
  return function () {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// Generate a winding route path in SVG viewBox coords (0..400 x 0..800).
// Start at the bottom, finish at the top. Deterministic per seed.
export function genRoutePath(seed = 1) {
  const N = 60;
  const pts = [];
  const rand = mulberry32(seed * 9973 + 17);
  const ampA = 70 + rand() * 40;
  const ampB = 26 + rand() * 22;
  const freqA = 1.4 + rand() * 1.6;
  const freqB = 4 + rand() * 3;
  const phase = rand() * Math.PI * 2;
  for (let i = 0; i < N; i++) {
    const t = i / (N - 1);
    const y = 760 - t * 700;
    let x = 200 + Math.sin(t * Math.PI * freqA + phase) * ampA + Math.sin(t * Math.PI * freqB) * ampB;
    x = Math.max(44, Math.min(356, x));
    pts.push({ x, y });
  }
  pts[0] = { x: 70, y: 760 };
  pts[N - 1] = { x: 250, y: 60 };
  return pts;
}

// Cumulative arc-length table for a path.
export function buildArc(path) {
  const cum = [0];
  for (let i = 1; i < path.length; i++) {
    cum.push(cum[i - 1] + Math.hypot(path[i].x - path[i - 1].x, path[i].y - path[i - 1].y));
  }
  return { cum, total: cum[cum.length - 1] };
}

function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }

// Interpolated {x,y} at fraction p (0..1) of total arc length.
export function posAtProgress(path, arc, p) {
  const target = arc.total * clamp(p, 0, 1);
  let lo = 0, hi = arc.cum.length - 1;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (arc.cum[mid] < target) lo = mid + 1; else hi = mid;
  }
  const i = Math.max(1, lo);
  const segLen = arc.cum[i] - arc.cum[i - 1] || 1;
  const f = (target - arc.cum[i - 1]) / segLen;
  return {
    x: path[i - 1].x + (path[i].x - path[i - 1].x) * f,
    y: path[i - 1].y + (path[i].y - path[i - 1].y) * f,
  };
}

// Evenly spaced checkpoint fractions for n checkpoints.
export function checkpointFractions(n) {
  const out = [];
  for (let i = 1; i <= n; i++) out.push(i / (n + 1));
  return out;
}

// Plausible varying speed (km/h) along the route.
export function speedAtProgress(p) {
  const base = 82;
  const curve = Math.sin(p * Math.PI * 3) * 20;
  const noise = Math.sin(p * 53) * 7;
  return Math.max(38, Math.round(base + curve + noise));
}

// HH:MM:SS for the leading part of the race timer.
export function formatRaceTimer(ms) {
  const total = Math.max(0, Math.floor(ms));
  const h = Math.floor(total / 3600000);
  const m = Math.floor((total % 3600000) / 60000);
  const s = Math.floor((total % 60000) / 1000);
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

export function millisPart(ms) {
  return String(Math.max(0, Math.floor(ms)) % 1000).padStart(3, '0');
}