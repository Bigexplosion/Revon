// Badge + title catalog and helpers for the REV-ON honors system.

export const BADGE_CATALOG = [
  { id: 'gold', label: 'GOLD', desc: 'Win an official event' },
  { id: 'silver', label: 'SILVER', desc: '2nd place in an official event' },
  { id: 'bronze', label: 'BRONZE', desc: '3rd place in an official event' },
  { id: 't21_king', label: 'T21 KING', desc: 'Dominate the T21 weekly challenge' },
  { id: 'season_champ', label: 'SEASON CHAMP', desc: 'Win a club season championship' },
  { id: 'fastest_season', label: 'FASTEST', desc: 'Fastest run of the season' },
  { id: 'club_hero', label: 'CLUB HERO', desc: 'Win an internal club event' },
  { id: 'night_runner', label: 'NIGHT RUNNER', desc: 'Complete a T8 night run' },
  { id: 'first_run', label: 'FIRST RUN', desc: 'Complete your first timed run' },
];

export const TITLE_CATALOG = [
  { id: 't21_king', label: 'T21 KING' },
  { id: 'night_runner', label: 'NIGHT RUNNER' },
  { id: 'fastest_season', label: 'FASTEST THIS SEASON' },
  { id: 'season_champ', label: 'SEASON CHAMPION' },
  { id: 'club_hero', label: 'CLUB HERO' },
  { id: 'normal', label: 'NORMAL' },
];

export function getBadge(id) {
  return BADGE_CATALOG.find((b) => b.id === id) || null;
}

export function badgeIcon(id) {
  return getBadge(id)?.icon || '';
}

export function titleLabel(id) {
  return TITLE_CATALOG.find((t) => t.id === id)?.label || id || '';
}

// Build a nickname -> { badges, selected_title, display_badge } map from Player records.
export function buildHonorsMap(players) {
  const m = {};
  (players || []).forEach((p) => {
    m[p.nickname] = {
      badges: p.badges || [],
      selected_title: p.selected_title || '',
      display_badge: p.display_badge || (p.badges && p.badges[0]) || '',
      avatar: p.avatar || '',
    };
  });
  return m;
}