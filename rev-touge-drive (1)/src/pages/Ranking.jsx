import React, { useState, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowRight, Crown, Trophy, Clock } from 'lucide-react';
import { base44 } from '@/api/base44Client';
import { useT } from '@/lib/i18n';
import { formatTime } from '@/lib/format';
import { cn } from '@/lib/utils';
import EventsView from '@/components/events/EventsView';
import { buildHonorsMap, titleLabel } from '@/lib/honors';
import RacerAvatar from '@/components/RacerAvatar';

export default function Ranking() {
  const { t, lang } = useT();
  const navigate = useNavigate();
  const [tracks, setTracks] = useState([]);
  const [results, setResults] = useState([]);
  const [activeCode, setActiveCode] = useState(null);
  const [filter, setFilter] = useState('all');
  const [me, setMe] = useState(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState('leaderboard');
  const [players, setPlayers] = useState([]);

  useEffect(() => {
    base44.auth.me().then(setMe).catch(() => {});
    base44.entities.Track.list('-created_date', 50).then((d) => {
      setTracks(d);
      if (d[0]) setActiveCode(d[0].code);
    });
    base44.entities.Player.list('-created_date', 200).then(setPlayers).catch(() => {});
  }, []);

  useEffect(() => {
    if (!activeCode) return;
    setLoading(true);
    base44.entities.RaceResult.filter({ track_code: activeCode }, 'finish_time_ms', 200).then((r) => {
      setResults(r);
      setLoading(false);
    }).catch(() => setLoading(false));
  }, [activeCode]);

  const ranked = useMemo(() => {
    let list = [...results];
    if (filter === 'club') {
      const myClub = me?.club_name || me?.club;
      if (!myClub || myClub === 'REV-ON 車隊') {
        list = list.filter((r) => r.player_nickname === myNickname);
      } else {
        list = list.filter((r) => (r.club_name || r.club) === myClub);
      }
    } else if (filter === 'month') {
      const cutoff = Date.now() - 30 * 86400000;
      list = list.filter((r) => new Date(r.recorded_at).getTime() > cutoff);
    }
    return list;
  }, [results, filter, me, myNickname]);

  const activeTrack = tracks.find((x) => x.code === activeCode);
  const honors = useMemo(() => buildHonorsMap(players), [players]);
  const myNickname = me?.nickname || me?.full_name;
  const myIndex = ranked.findIndex((r) => r.player_nickname === myNickname);
  const myResult = myIndex >= 0 ? ranked[myIndex] : null;

  return (
    <div className="min-h-screen bg-background">
      <header className="px-4 pt-6 pb-2">
        <div className="flex items-center gap-2">
          <span className="w-1.5 h-6 bg-red-600 rounded-sm" />
          <h1 className="font-display font-extrabold uppercase tracking-wide text-2xl text-white">{t('hall_of_fame')}</h1>
        </div>
        {activeTrack && (
          <div className="text-neutral-400 text-xs font-mono uppercase tracking-wider mt-1 ml-3.5 flex items-center gap-2 flex-wrap">
            <span>{activeTrack.code || activeTrack.name}</span>
            {activeTrack.region && (
              <>
                <span>·</span>
                <span>{activeTrack.region}</span>
              </>
            )}
            {activeTrack.location && (
              <>
                <span>·</span>
                <span>{activeTrack.location}</span>
              </>
            )}
          </div>
        )}
      </header>

      {/* main nav tabs */}
      <div className="grid grid-cols-2 gap-2 px-4 pt-3 pb-2">
        {[['leaderboard', t('leaderboard_tab')], ['events', t('events_tab')]].map(([k, label]) => (
          <button key={k} onClick={() => setTab(k)}
            className={cn('py-2.5 rounded-lg text-sm font-heading font-extrabold uppercase tracking-wider transition-all border',
              tab === k ? 'bg-red-600 border-red-500 text-white red-glow' : 'bg-neutral-900/90 border-neutral-800 text-neutral-300 hover:text-white')}>
            {label}
          </button>
        ))}
      </div>

      {tab === 'events' ? <EventsView /> : (
      <>
      {/* track tabs */}
      <div className="flex gap-2 overflow-x-auto no-scrollbar px-4 py-2 whitespace-nowrap">
        {tracks.map((tr) => {
          const trackDisplayName = lang === 'zh' && tr.name_zh ? tr.name_zh : (tr.name || tr.name_zh || tr.code);
          return (
            <button key={tr.id} onClick={() => setActiveCode(tr.code)}
              className={cn('px-5 py-1.5 rounded-full text-xs font-heading font-bold uppercase tracking-wide whitespace-nowrap border shrink-0 transition-all',
                activeCode === tr.code ? 'bg-red-600 border-red-500 text-white red-glow' : 'bg-neutral-900/80 border-neutral-800 text-neutral-400 hover:border-neutral-700 hover:text-neutral-200')}>
              {trackDisplayName}
            </button>
          );
        })}
      </div>

      {/* filter tabs */}
      <div className="grid grid-cols-3 gap-2 px-4 py-3">
        {[['all', t('all_time')], ['club', '車隊紀錄'], ['month', t('this_month')]].map(([k, label]) => (
          <button key={k} onClick={() => setFilter(k)}
            className={cn('py-2 rounded-lg text-xs font-heading font-bold uppercase tracking-wide border transition-colors',
              filter === k ? 'bg-transparent border-red-600 text-red-500' : 'bg-transparent border-neutral-800 text-neutral-400')}>
            {label}
          </button>
        ))}
      </div>

      {/* list */}
      <div className="px-4 pb-36 space-y-2.5">
        {loading && <p className="text-neutral-400 text-sm text-center py-8">…</p>}
        {!loading && ranked.length === 0 && <p className="text-neutral-400 text-sm text-center py-8 uppercase tracking-wide">{t('no_results')}</p>}
        {ranked.slice(0, 10).map((r, i) => {
          const playerClubName = r.club || (r.club_name && r.club_name !== 'REV-ON 車隊' ? r.club_name : null) || honors[r.player_nickname]?.club || '暫無車隊';

          return (
            <div key={r.id} className="flex items-center gap-3.5 p-3.5 rounded-xl border border-neutral-800/90 bg-neutral-950/80 shadow-sm">
              <div className="w-8 flex justify-center items-center shrink-0">
                {rankNum === 1 ? (
                  <span className="font-display font-extrabold text-2xl text-white leading-none">1</span>
                ) : rankNum === 2 ? (
                  <span className="font-display font-extrabold text-2xl text-white leading-none">2</span>
                ) : rankNum === 3 ? (
                  <span className="font-display font-extrabold text-2xl text-[#00FF66] leading-none">3</span>
                ) : (
                  <span className="font-mono font-bold text-sm text-neutral-400">#{rankNum}</span>
                )}
              </div>
              <RacerAvatar src={honors[r.player_nickname]?.avatar} nickname={r.player_nickname} size={38} />
              <div className="flex-1 min-w-0">
                <div className="font-heading font-bold uppercase tracking-wide text-base truncate text-white leading-tight">
                  {r.player_nickname}
                </div>
                <div className="text-[11px] font-heading font-semibold uppercase tracking-wider text-red-500 truncate mt-0.5">
                  {playerClubName}
                </div>
              </div>
              <div className="font-mono text-[#00FF66] font-bold text-lg tracking-wider shrink-0">
                {formatTime(r.finish_time_ms)}
              </div>
            </div>
          );
        })}
      </div>

      {/* your rank floating */}
      <div className="fixed bottom-20 left-1/2 -translate-x-1/2 w-full max-w-[430px] z-30 px-4">
        <div className="bg-neutral-950/95 border-2 border-red-600 rounded-2xl p-3.5 flex items-center justify-between red-glow backdrop-blur-md">
          <div className="flex items-center gap-6">
            <div>
              <div className="text-[10px] font-heading font-bold uppercase tracking-wider text-neutral-400">{t('your_rank')}</div>
              <div className="font-display font-extrabold text-red-500 text-xl leading-snug">{myIndex >= 0 ? `${myIndex + 1}` : '—'}</div>
            </div>
            <div>
              <div className="text-[10px] font-heading font-bold uppercase tracking-wider text-neutral-400">{t('personal_best')}</div>
              <div className="font-mono text-[#00FF66] font-bold text-base leading-snug">{myResult ? formatTime(myResult.finish_time_ms) : '—'}</div>
            </div>
          </div>
          <button onClick={() => navigate('/profile')} className="text-red-500 text-xs font-heading font-extrabold uppercase tracking-wider flex items-center gap-1 hover:text-red-400 transition-colors">
            {t('view_my_results')} <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
      </>
      )}
    </div>
  );
}