// Shared club scoring logic — used by Club list ranking and Club Detail.
// Only OFFICIAL track results count (is_custom_route !== true).

export function timeToPoints(ms) {
  if (!ms || ms <= 0) return 0;
  return Math.round(1000000 / ms);
}

// results: RaceResult[] (any), filtered to official + club-attributed internally.
// returns { score, strongestTrackCode, perTrack: [{trackCode, bestTime, points}] }
export function computeClubScore(results) {
  const official = results.filter((r) => !r.is_custom_route && r.club_id);
  const byTrack = {};
  official.forEach((r) => {
    if (!byTrack[r.track_code] || r.finish_time_ms < byTrack[r.track_code]) {
      byTrack[r.track_code] = r.finish_time_ms;
    }
  });
  const perTrack = Object.entries(byTrack).map(([trackCode, bestTime]) => ({
    trackCode,
    bestTime,
    points: timeToPoints(bestTime),
  }));
  perTrack.sort((a, b) => b.points - a.points);
  const score = perTrack.reduce((s, p) => s + p.points, 0);
  const strongestTrackCode = perTrack[0]?.trackCode || null;
  return { score, strongestTrackCode, perTrack };
}

// total points = sum of points across ALL official results by club members (accumulates over time)
export function computeClubTotalPoints(results) {
  const official = results.filter((r) => !r.is_custom_route && r.club_id);
  return official.reduce((s, r) => s + timeToPoints(r.finish_time_ms), 0);
}

export function levelFromPoints(points) {
  if (points >= 5000) return 5;
  if (points >= 3000) return 4;
  if (points >= 1500) return 3;
  if (points >= 500) return 2;
  return 1;
}

const ACCENTS = ['#E10600', '#E10600', '#FF6B00', '#39FF14', '#FFD700', '#00D4FF'];
export function levelAccent(level) {
  return ACCENTS[Math.min(level, ACCENTS.length - 1)] || '#E10600';
}

// rank clubs: [{ club, score, strongestTrackCode }]
// resultsByClub: { clubId: RaceResult[] }
export function rankClubs(clubs, resultsByClub) {
  const ranked = clubs.map((club) => {
    const { score, strongestTrackCode } = computeClubScore(resultsByClub[club.id] || []);
    return { club, score, strongestTrackCode };
  });
  ranked.sort((a, b) => b.score - a.score);
  return ranked;
}