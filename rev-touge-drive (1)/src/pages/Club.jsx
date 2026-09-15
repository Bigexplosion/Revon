import React, { useState, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, ChevronRight } from 'lucide-react';
import { base44 } from '@/api/base44Client';
import { useT } from '@/lib/i18n';
import { Input } from '@/components/ui/input';
import { computeClubScore } from '@/lib/clubScoring';
import { countdownStr } from '@/lib/eventStatus';
import ClubPodium from '@/components/club/ClubPodium';
import ClubBadge from '@/components/ClubBadge';
import ClubBottomSheet from '@/components/club/ClubBottomSheet';
import MyClubEmptySheet from '@/components/club/MyClubEmptySheet';

export default function Club() {
  const { t } = useT();
  const navigate = useNavigate();
  const [clubs, setClubs] = useState([]);
  const [results, setResults] = useState([]);
  const [me, setMe] = useState(null);
  const [myMembers, setMyMembers] = useState([]);
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState(null);
  const [requested, setRequested] = useState({});
  const [showEmpty, setShowEmpty] = useState(false);
  const [loading, setLoading] = useState(true);
  const [season, setSeason] = useState(null);

  useEffect(() => {
    base44.auth.me().then((u) => {
      setMe(u);
      if (u?.id) base44.entities.ClubMember.filter({ status: 'joined', created_by_id: u.id }, '-created_date', 50).then(setMyMembers).catch(() => {});
    }).catch(() => {});
    Promise.all([
      base44.entities.Club.list('-created_date', 100),
      base44.entities.RaceResult.list('-created_date', 500),
    ]).then(([c, r]) => { setClubs(c); setResults(r); setLoading(false); }).catch(() => setLoading(false));
    base44.entities.ClubSeason.filter({ active: true }, '-created_date', 5).then((s) => setSeason(s[0] || null)).catch(() => {});
  }, []);

  useEffect(() => {
    if (!me?.id) return;
    const unsub = base44.entities.ClubMember.subscribe(() => {
      base44.entities.ClubMember.filter({ status: 'joined', created_by_id: me.id }, '-created_date', 50).then(setMyMembers).catch(() => {});
    });
    return unsub;
  }, [me?.id]);

  const myNick = me?.nickname || me?.full_name || 'RACER';
  const myClub = useMemo(() => {
    const own = myMembers.find((m) => m.member_nickname === myNick) || myMembers[0];
    return clubs.find((c) => c.id === own?.club_id) || null;
  }, [myMembers, clubs, myNick]);

  const allRanked = useMemo(() => {
    const byClub = {};
    results.forEach((r) => { if (r.is_custom_route || !r.club_id) return; (byClub[r.club_id] = byClub[r.club_id] || []).push(r); });
    let pool = clubs;
    if (me?.region_city) {
      const filtered = clubs.filter((c) => (c.region || '').toUpperCase() === String(me.region_city).toUpperCase());
      if (filtered.length > 0) pool = filtered;
    }
    const rows = pool.map((club) => {
      const { strongestTrackCode } = computeClubScore(byClub[club.id] || []);
      return { club, score: club.total_points || 0, strongestTrackCode };
    });
    rows.sort((a, b) => b.score - a.score);
    return rows;
  }, [clubs, results, me]);

  const ranked = useMemo(() => {
    if (!query) return allRanked;
    return allRanked.filter((row) => row.club.name?.toLowerCase().includes(query.toLowerCase()));
  }, [allRanked, query]);

  const top3 = ranked.slice(0, 3);
  const rest = ranked.slice(3, 10);

  const myClubRow = myClub ? allRanked.find((row) => row.club.id === myClub.id) : null;
  const myClubRank = myClubRow ? allRanked.indexOf(myClubRow) + 1 : null;

  const selectedRow = selected ? allRanked.find((row) => row.club.id === selected) : null;
  const selectedRank = selectedRow ? allRanked.indexOf(selectedRow) + 1 : null;
  const topMembers = selected ? results.filter((r) => r.club_id === selected && !r.is_custom_route).sort((a, b) => a.finish_time_ms - b.finish_time_ms).slice(0, 5) : [];
  const isJoined = selected ? myMembers.some((m) => m.club_id === selected && m.member_nickname === myNick) : false;

  const requestJoin = async (c) => {
    try {
      await base44.entities.ClubMember.create({ club_id: c.id, club_name: c.name, status: 'pending', member_nickname: myNick });
      setRequested((r) => ({ ...r, [c.id]: true }));
    } catch (e) {}
  };

  return (
    <div className="min-h-[100dvh] bg-background">
      <div className="sticky top-7 z-20 bg-background/95 backdrop-blur px-4 py-3 border-b border-border">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder={t('search_clubs')} className="pl-10 h-10 bg-card" />
        </div>
      </div>

      {/* pinned MY CLUB bar */}
      <div className="px-4 pt-3">
        {myClub ? (
          <button onClick={() => navigate(`/club/${myClub.id}`)} className="w-full flex items-center gap-3 p-3 rounded-xl border-2 border-primary bg-primary/10 red-glow-soft active:scale-[0.99] transition-transform">
            <ClubBadge club={myClub} size={40} />
            <div className="flex-1 min-w-0 text-left">
              <div className="text-[9px] uppercase tracking-wide2 text-primary font-heading font-bold">{t('my_club')}</div>
              <div className="font-heading font-bold uppercase tracking-wide2 text-sm text-white truncate">{myClub.name}</div>
            </div>
            <div className="text-right shrink-0">
              <div className="font-display font-bold text-primary text-lg leading-none">#{myClubRank || '—'}</div>
              <div className="font-data text-data text-xs font-bold">{(myClub.total_points || 0).toLocaleString()} {t('club_points')}</div>
            </div>
            <ChevronRight className="w-4 h-4 text-muted-foreground shrink-0" />
          </button>
        ) : (
          <button onClick={() => setShowEmpty(true)} className="w-full flex items-center gap-3 p-3 rounded-xl border border-dashed border-border bg-card text-left">
            <div className="w-10 h-10 rounded-full border border-border flex items-center justify-center text-muted-foreground shrink-0">—</div>
            <div className="flex-1 min-w-0">
              <div className="text-[9px] uppercase tracking-wide2 text-muted-foreground font-heading font-bold">{t('my_club')}</div>
              <div className="text-muted-foreground text-xs">{t('no_club_body')}</div>
            </div>
            <ChevronRight className="w-4 h-4 text-muted-foreground shrink-0" />
          </button>
        )}
      </div>

      {loading ? (
        <div className="flex justify-center py-20"><div className="w-8 h-8 border-4 border-primary/30 border-t-primary rounded-full animate-spin" /></div>
      ) : ranked.length === 0 ? (
        <p className="text-center text-muted-foreground py-16 uppercase tracking-wide2 text-sm">{t('no_clubs')}</p>
      ) : (
        <>
          {season && (
            <div className="px-4 pt-3">
              <div className="rounded-xl border border-primary/40 bg-primary/10 p-3 flex items-center justify-between">
                <div>
                  <div className="text-[9px] uppercase tracking-wide2 text-muted-foreground">{t('season_label')}</div>
                  <div className="font-display font-extrabold uppercase tracking-mega text-primary text-sm">{season.name}</div>
                </div>
                <div className="text-right">
                  <div className="text-[9px] uppercase tracking-wide2 text-muted-foreground">{t('season_ends_in')}</div>
                  <div className="font-data text-data font-bold text-sm">{countdownStr(new Date(season.end_at).getTime() - Date.now())}</div>
                </div>
              </div>
            </div>
          )}
          <ClubPodium top3={top3} onSelect={(c) => setSelected(c.id)} />
          <div className="px-4 pb-32 space-y-2">
            {rest.map((row, i) => (
              <button key={row.club.id} onClick={() => setSelected(row.club.id)} className="w-full flex items-center gap-2.5 p-3 rounded-xl border border-border bg-card text-left active:scale-[0.99] transition-transform">
                <div className="w-7 text-center font-data text-sm text-muted-foreground">#{i + 4}</div>
                <ClubBadge club={row.club} size={40} />
                <div className="flex-1 min-w-0">
                  <div className="font-heading font-bold uppercase tracking-wide2 text-sm text-white truncate">{row.club.name}</div>
                  <div className="text-muted-foreground text-[10px] uppercase tracking-wide2 truncate">{row.club.region || '—'} · {row.club.member_count || 0} {t('members')}</div>
                </div>
                {row.strongestTrackCode && <span className="px-1.5 py-1 rounded bg-primary/15 border border-primary/40 text-primary text-[8px] font-heading font-bold uppercase tracking-wide2 whitespace-nowrap">{row.strongestTrackCode} {t('dominant')}</span>}
                <div className="text-right shrink-0">
                  <div className="font-data text-data font-bold text-sm">{row.score.toLocaleString()}</div>
                  <div className="text-[7px] uppercase tracking-wide2 text-muted-foreground">{t('club_points')}</div>
                </div>
              </button>
            ))}
          </div>
        </>
      )}

      <ClubBottomSheet
        open={!!selected}
        club={selectedRow?.club}
        rank={selectedRank}
        score={selectedRow?.score || 0}
        strongestTrackCode={selectedRow?.strongestTrackCode}
        topMembers={topMembers}
        joined={isJoined}
        requested={!!requested[selected]}
        onJoin={() => selectedRow && requestJoin(selectedRow.club)}
        onClose={() => setSelected(null)}
      />
      <MyClubEmptySheet open={showEmpty} onClose={() => setShowEmpty(false)} />
    </div>
  );
}