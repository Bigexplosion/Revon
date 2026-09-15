import { createClient } from '@base44/sdk';
import { appParams } from '@/lib/app-params';

const { appId, token, functionsVersion, appBaseUrl } = appParams;

export const base44 = createClient({
  appId,
  token,
  functionsVersion,
  serverUrl: '',
  requiresAuth: false,
  appBaseUrl
});

export const SEEDED_TRACKS = [
  {
    id: 'track-tms',
    code: 'TMS',
    name: 'Tai Mo Shan',
    name_zh: '大帽山',
    region: 'HONG KONG',
    difficulty: 'EXTREME',
    distance_km: 15.1,
    corners_count: 68,
    active_drivers: 42,
    cover_image: 'https://images.unsplash.com/photo-1509316975850-ff9c5deb0cd9?auto=format&fit=crop&w=1200&q=80',
  },
  {
    id: 'track-tlm',
    code: 'TLM',
    name: 'Tai Lam',
    name_zh: '大欖',
    region: 'HONG KONG',
    difficulty: 'NORMAL',
    distance_km: 8.6,
    corners_count: 32,
    active_drivers: 28,
    cover_image: 'https://images.unsplash.com/photo-1519681393784-d120267933ba?auto=format&fit=crop&w=1200&q=80',
  },
  {
    id: 'track-wil',
    code: 'WIL',
    name: 'Wilson Trail',
    name_zh: '威爾遜徑',
    region: 'HONG KONG',
    difficulty: 'HARD',
    distance_km: 12.4,
    corners_count: 54,
    active_drivers: 35,
    cover_image: 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=1200&q=80',
  },
  {
    id: 'track-fng',
    code: 'FNG',
    name: 'Fei Ngo Shan',
    name_zh: '飛鵝山',
    region: 'HONG KONG',
    difficulty: 'HARD',
    distance_km: 6.2,
    corners_count: 28,
    active_drivers: 19,
    cover_image: 'https://images.unsplash.com/photo-1492144534655-ae79c964c9d7?auto=format&fit=crop&w=1200&q=80',
  },
  {
    id: 'track-skg',
    code: 'SKG',
    name: 'Shek O',
    name_zh: '石澳',
    region: 'HONG KONG',
    difficulty: 'NORMAL',
    distance_km: 9.5,
    corners_count: 42,
    active_drivers: 22,
    cover_image: 'https://images.unsplash.com/photo-1511919884226-fd3cad34687c?auto=format&fit=crop&w=1200&q=80',
  },
  {
    id: 'track-ttl',
    code: 'TTL',
    name: 'Luk Keng',
    name_zh: '鹿頸',
    region: 'HONG KONG',
    difficulty: 'HARD',
    distance_km: 11.8,
    corners_count: 46,
    active_drivers: 31,
    cover_image: 'https://images.unsplash.com/photo-1542282088-72c9c27ed0cd?auto=format&fit=crop&w=1200&q=80',
  },
  {
    id: 'track-136',
    code: '136',
    name: '136 Pass',
    name_zh: '136 縣道',
    region: 'TAICHUNG',
    difficulty: 'HARD',
    distance_km: 14.8,
    corners_count: 48,
    active_drivers: 48,
    cover_image: 'https://images.unsplash.com/photo-1509316975850-ff9c5deb0cd9?auto=format&fit=crop&w=1200&q=80',
  },
  {
    id: 'track-139',
    code: '139',
    name: '139 Ridge Road',
    name_zh: '139 縣道',
    region: 'CHANGHUA',
    difficulty: 'NORMAL',
    distance_km: 11.2,
    corners_count: 36,
    active_drivers: 56,
    cover_image: 'https://images.unsplash.com/photo-1519681393784-d120267933ba?auto=format&fit=crop&w=1200&q=80',
  }
];

export const SEEDED_EVENTS = [
  {
    id: 'event-update-v2',
    name: 'SYSTEM UPDATE v2.4 & NIGHT TOUGE SEASON',
    banner_image: 'https://images.unsplash.com/photo-1542282088-72c9c27ed0cd?auto=format&fit=crop&w=1200&q=80',
    start_at: new Date(Date.now() - 3600000 * 24).toISOString(),
    end_at: new Date(Date.now() + 3600000 * 24 * 7).toISOString(),
    entry_cost: 0,
    reward_pool: 50000,
    track_codes: ['TMS', 'TLM', 'WIL'],
    track_names: ['Tai Mo Shan', 'Tai Lam', 'Wilson Trail'],
    status: 'live'
  },
  {
    id: 'event-tms-challenge',
    name: 'TAI MO SHAN MIDNIGHT TIME TRIAL',
    banner_image: 'https://images.unsplash.com/photo-1509316975850-ff9c5deb0cd9?auto=format&fit=crop&w=1200&q=80',
    start_at: new Date(Date.now() - 3600000 * 12).toISOString(),
    end_at: new Date(Date.now() + 3600000 * 24 * 3).toISOString(),
    entry_cost: 100,
    reward_pool: 25000,
    track_codes: ['TMS'],
    track_names: ['Tai Mo Shan'],
    status: 'live'
  },
  {
    id: 'event-fei-ngo-sprint',
    name: 'FEI NGO SHAN HILLCLIMB SPRINT',
    banner_image: 'https://images.unsplash.com/photo-1492144534655-ae79c964c9d7?auto=format&fit=crop&w=1200&q=80',
    start_at: new Date(Date.now() + 3600000 * 24).toISOString(),
    end_at: new Date(Date.now() + 3600000 * 24 * 5).toISOString(),
    entry_cost: 50,
    reward_pool: 15000,
    track_codes: ['FNG'],
    track_names: ['Fei Ngo Shan'],
    status: 'upcoming'
  }
];

export function normalizeTrack(tr) {
  if (!tr) return tr;
  const code = (tr.code || '').toUpperCase();
  const known = SEEDED_TRACKS.find(x => x.code === code || x.id === tr.id);
  
  let difficulty = (tr.difficulty || (known && known.difficulty) || 'NORMAL').toUpperCase();
  if (difficulty === 'ROOKIE') difficulty = 'NORMAL';
  if (difficulty === 'PRO' || difficulty === 'EXPERT') difficulty = 'HARD';
  if (difficulty === 'LEGEND') difficulty = 'EXTREME';
  if (!['NORMAL', 'HARD', 'EXTREME'].includes(difficulty)) difficulty = 'NORMAL';

  const name = tr.name || (known && known.name) || tr.code || 'Track';
  const name_zh = tr.name_zh || (known && known.name_zh) || name;
  const cover_image = (tr.cover_image && !tr.cover_image.includes('example.com') && !tr.cover_image.includes('placeholder'))
    ? tr.cover_image
    : (known ? known.cover_image : 'https://images.unsplash.com/photo-1509316975850-ff9c5deb0cd9?auto=format&fit=crop&w=1200&q=80');

  return {
    ...known,
    ...tr,
    name,
    name_zh,
    difficulty,
    cover_image
  };
}

export function normalizeEvent(ev) {
  if (!ev) return ev;
  const known = SEEDED_EVENTS.find(x => x.id === ev.id || x.name === ev.name);
  const banner_image = (ev.banner_image && !ev.banner_image.includes('example.com') && !ev.banner_image.includes('placeholder'))
    ? ev.banner_image
    : (known ? known.banner_image : 'https://images.unsplash.com/photo-1542282088-72c9c27ed0cd?auto=format&fit=crop&w=1200&q=80');

  return {
    ...known,
    ...ev,
    banner_image
  };
}

const origTrackList = base44.entities.Track.list.bind(base44.entities.Track);
const origTrackGet = base44.entities.Track.get.bind(base44.entities.Track);
const origEventList = base44.entities.Event.list.bind(base44.entities.Event);
const origEventGet = base44.entities.Event.get.bind(base44.entities.Event);

base44.entities.Track.list = async (...args) => {
  try {
    const list = await origTrackList(...args);
    if (Array.isArray(list) && list.length > 0) {
      return list.map(normalizeTrack);
    }
  } catch (e) {}
  return SEEDED_TRACKS.map(normalizeTrack);
};

base44.entities.Track.get = async (id) => {
  try {
    const item = await origTrackGet(id);
    if (item) return normalizeTrack(item);
  } catch (e) {}
  const found = SEEDED_TRACKS.find(t => t.id === id || t.code === id);
  return normalizeTrack(found || SEEDED_TRACKS[0]);
};

base44.entities.Event.list = async (...args) => {
  try {
    const list = await origEventList(...args);
    if (Array.isArray(list) && list.length > 0) {
      return list.map(normalizeEvent);
    }
  } catch (e) {}
  return SEEDED_EVENTS.map(normalizeEvent);
};

base44.entities.Event.get = async (id) => {
  try {
    const item = await origEventGet(id);
    if (item) return normalizeEvent(item);
  } catch (e) {}
  const found = SEEDED_EVENTS.find(e => e.id === id);
  return normalizeEvent(found || SEEDED_EVENTS[0]);
};

