import React, { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ChevronLeft, Calendar, Clock, Ticket } from 'lucide-react';
import { base44 } from '@/api/base44Client';
import { useT } from '@/lib/i18n';
import { formatTime } from '@/lib/format';
import { eventStatus, countdownStr, fmtDateTime } from '@/lib/eventStatus';
import { buildHonorsMap, titleLabel } from '@/lib/honors';
import BadgeIcon from '@/components/BadgeIcon';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import RacerAvatar from '@/components/RacerAvatar';

export default function EventDetail() {
  const { eventId } = useParams();
  const navigate = useNavigate();
  const { t } = useT();
  const urlParams = new URLSearchParams(window.location.search);
  const isClub = urlParams.get('type') === 'club';
  const [event, setEvent] = useState(null);
  const [results, setResults] = useState([]);
  const [me, setMe] = useState(null);
  const [players, setPlayers] = useState([]);
  const [tracks, setTracks] = useState([]);
  const [entering, setEntering] = useState(false);
  const [imgOk, setImgOk] = useState(true);

  useEffect(() => {
    if (isClub) {
      base44.entities.ClubEvent.get(eventId).then((ce) => {
        const pool = (ce.placement_rewards || []).reduce((s, r) => s + (r.points || 0), 0) || ce.reward_points || 0;
        setEvent({
          id: ce.id, name: ce.name, banner_image: null,
          track_names: [ce.track_name].filter(Boolean), track_codes: [ce.track_code],
          start_at: ce.start_at, end_at: ce.end_at, entry_cost: 0, reward_pool: pool,
          placement_rewards: ce.placement_rewards || [], rules: '', participant_count: 0,
          _club: true, _clubId: ce.club_id, _rewardTitle: ce.reward_title,
        });
      }).catch(() => {});
      base44.entities.RaceResult.filter({ club_event_id: eventId }, 'finish_time_ms', 500).then(setResults).catch(() => {});
    } else {
      base44.entities.Event.get(eventId).then(setEvent).catch(() => {});
      base44.entities.RaceResult.filter({ event_id: eventId }, 'finish_time_ms', 500).then(setResults).catch(() => {});
    }
    base44.auth.me().then(setMe).catch(() => {});
    base44.entities.Player.list('-created_date', 200).then(setPlayers).catch(() => {});
    base44.entities.Track.list('-created_date', 50).then(setTracks).catch(() => {});
  }, [eventId, isClub]);

  useEffect(() => { setImgOk(true); }, [eventId, isClub]);

  const honors = useMemo(() => buildHonorsMap(players), [players]);

  const board = useMemo(() => {
    const best = {};
    results.forEach((r) => {
      if (!best[r.player_nickname] || r.finish_time_ms < best[r.player_nickname].finish_time_ms) best[r.player_nickname] = r;
    });
    return Object.values(best).sort((a, b) => a.finish_time_ms - b.finish_time_ms);
  }, [results]);

  const myNick = me?.nickname || me?.full_name || 'RACER';
  const myRow = board.find((r) => r.player_nickname === myNick);
  const myRank = myRow ? board.indexOf(myRow) + 1 : null;
  const status = event ? eventStatus(event) : null;
  const rPoints = me?.r_points ?? 0;
  const alreadyEntered = results.some((r) => r.player_nickname === myNick);

  const enterEvent = async () => {
    if (!event || entering) return;
    if (event.entry_cost > 0 && !alreadyEntered && rPoints < event.entry_cost) { alert(t('insufficient_points')); return; }
    setEntering(true);
    try {
      if (event.entry_cost > 0 && !alreadyEntered) {
        await base44.entities.Transaction.create({ description: `Event entry: ${event.name}`, points_delta: -event.entry_cost });
        await base44.auth.updateMe({ r_points: rPoints - event.entry_cost });
      }
      const code = (event.track_codes || [])[0];
      const tr = tracks.find((x) => x.code === code) || tracks[0];
      if (tr) navigate(`/race/${tr.id}?${isClub ? 'clubEvent' : 'event'}=${event.id}`);
    } catch (e) { alert('Failed'); }
    setEntering(false);
  };

  if (!event) return <div className="h-[100dvh] flex items-center justify-center bg-black"><div className="w-8 h-8 border-4 border-primary/30 border-t-primary rounded-full animate-spin" /></div>;

  const remaining = status === 'upcoming' ? new Date(event.start_at).getTime() - Date.now() : new Date(event.end_at).getTime() - Date.now();
  const statusColor = { upcoming: 'text-yellow-300', live: 'text-primary', ended: 'text-muted-foreground' }[status];
  const back = () => (isClub && event._clubId ? navigate(`/club/${event._clubId}`) : navigate('/ranking'));

  return (
    <div className="min-h-[100dvh] bg-background pb-28">
      <div className="relative h-44 overflow-hidden">
        {event.banner_image && imgOk ? (
          <img src={event.banner_image} alt={event.name} onError={() => setImgOk(false)} className="absolute inset-0 w-full h-full object-cover" />
        ) : (
          <div className="absolute inset-0 bg-[#0A0A0A] flex items-center justify-center p-4">
            <span className="font-heading font-extrabold uppercase tracking-wide2 text-white text-lg text-center truncate">{event.name}</span>
          </div>
        )}
        <div className="absolute inset-0 bg-gradient-to-b from-black/60 to-black" />
        <button onClick={back} className="absolute top-4 left-4 z-20 w-9 h-9 rounded-full bg-black/60 border border-border flex items-center justify-center text-white"><ChevronLeft className="w-5 h-5" /></button>
        <div className="absolute bottom-3 left-4 right-4">
          <div className={cn('inline-flex items-center gap-1.5 px-2 py-1 rounded border bg-black/70 text-[9px] font-heading font-bold uppercase tracking-wide2 mb-2', status === 'live' ? 'border-primary text-primary' : status === 'upcoming' ? 'border-yellow-400/50 text-yellow-300' : 'border-muted-foreground text-muted-foreground')}>
            {status === 'live' && <span className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />}
            {t(status)} {status !== 'ended' && `· ${countdownStr(remaining)}`}
          </div>
          <h1 className="font-display font-extrabold uppercase tracking-mega text-xl text-white">{event.name}</h1>
          <div className="text-[10px] uppercase tracking-wide2 text-white/80 mt-1">{(event.track_names || event.track_codes || []).join(' · ')}</div>
        </div>
      </div>

      <div className="px-4 -mt-4 relative z-10 space-y-4">
        <div className="grid grid-cols-3 gap-2">
          <Box label={t('entry_cost_label')} value={event.entry_cost > 0 ? `${event.entry_cost}` : t('entry_free')} accent={event.entry_cost > 0 ? 'text-primary' : 'text-data'} />
          <Box label={t('reward_pool')} value={`${(event.reward_pool || 0).toLocaleString()}`} accent="text-data" />
          {!isClub && <Box label={t('participants')} value={event.participant_count || 0} />}
          {isClub && event._rewardTitle && <Box label={t('honor_title')} value={event._rewardTitle} accent="text-primary" />}
        </div>

        <div className="bg-card border border-border rounded-xl p-3 space-y-1.5 text-xs">
          <div className="flex items-center gap-2 text-white"><Calendar className="w-3.5 h-3.5 text-primary" />{fmtDateTime(event.start_at)} → {fmtDateTime(event.end_at)}</div>
          {status !== 'ended' && <div className="flex items-center gap-2 text-muted-foreground"><Clock className="w-3.5 h-3.5" />{status === 'upcoming' ? t('starts_in') : t('ends_in')}: <span className={cn('font-data font-bold', statusColor)}>{countdownStr(remaining)}</span></div>}
        </div>

        {(event.placement_rewards || []).length > 0 && (
          <div className="bg-card border border-border rounded-xl p-3">
            <div className="text-[10px] uppercase tracking-mega text-primary font-heading font-bold mb-2">{t('per_placement')}</div>
            <div className="space-y-1.5">
              {event.placement_rewards.map((pr) => (
                <div key={pr.place} className="flex items-center gap-2 text-sm">
                  <span className={cn('w-6 h-6 rounded-full flex items-center justify-center font-heading font-bold text-xs', pr.place === 1 ? 'bg-primary/20 text-primary' : pr.place === 2 ? 'bg-white/10 text-white' : 'bg-white/5 text-white/60')}>{pr.place}</span>
                  <span className="font-data text-data font-bold">{pr.points} {t('r_points_short')}</span>
                  {pr.badge && <BadgeIcon id={pr.badge} className="w-4 h-4 text-primary" />}
                  {pr.badge && <span className="text-[10px] uppercase tracking-wide2 text-muted-foreground">{pr.badge}</span>}
                </div>
              ))}
            </div>
            <div className="text-[10px] text-muted-foreground italic mt-2">{t('redeemable_distributor')}</div>
          </div>
        )}

        {event.rules && (
          <div className="bg-card border border-border rounded-xl p-3">
            <div className="text-[10px] uppercase tracking-mega text-primary font-heading font-bold mb-1.5">{t('event_rules')}</div>
            <p className="text-white/80 text-xs leading-relaxed whitespace-pre-line">{event.rules}</p>
          </div>
        )}

        <div className="bg-card border border-border rounded-xl p-3">
          <div className="text-[10px] uppercase tracking-mega text-primary font-heading font-bold mb-2">{status === 'ended' ? t('final_standings') : t('event_leaderboard')}</div>
          {board.length === 0 ? (
            <p className="text-muted-foreground text-xs uppercase tracking-wide2 py-4 text-center">{t('no_results')}</p>
          ) : (
            <div className="space-y-1.5">
              {board.slice(0, 10).map((r, i) => {
                const h = honors[r.player_nickname];
                const top3 = i < 3;
                return (
                  <div key={r.id} className={cn('flex items-center gap-2 p-2 rounded-lg', top3 ? 'bg-primary/10 border border-primary/30' : 'bg-secondary/50', r.player_nickname === myNick && 'ring-1 ring-data')}>
                    <div className="w-6 text-center">{top3 ? <span className={cn('font-display font-extrabold text-base', i === 0 ? 'text-primary text-glow-red' : i === 1 ? 'text-white' : 'text-data')}>{i + 1}</span> : <span className="font-data text-muted-foreground text-sm">#{i + 1}</span>}</div>
                    <RacerAvatar src={h?.avatar} nickname={r.player_nickname} size={32} />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-1">
                        <span className={cn('font-heading font-semibold uppercase tracking-wide2 text-sm truncate', top3 ? 'text-white' : 'text-white/90')}>{r.player_nickname}</span>
                      </div>
                      {h?.selected_title && <div className="text-[9px] uppercase tracking-wide2 text-primary">{titleLabel(h.selected_title)}</div>}
                    </div>
                    <div className="font-data text-data font-bold text-sm">{formatTime(r.finish_time_ms)}</div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        <div className="bg-card border-2 border-primary rounded-xl p-3 red-glow flex items-center gap-3">
          <div className="text-center">
            <div className="text-[9px] uppercase tracking-wide2 text-muted-foreground">{t('your_best')}</div>
            <div className="font-data text-data font-bold">{myRow ? formatTime(myRow.finish_time_ms) : '—'}</div>
          </div>
          <div className="text-center">
            <div className="text-[9px] uppercase tracking-wide2 text-muted-foreground">{t('rank')}</div>
            <div className="font-display font-bold text-primary text-lg">{myRank ? `#${myRank}` : '—'}</div>
          </div>
        </div>
      </div>

      {status !== 'ended' && (
        <div className="fixed bottom-16 left-1/2 -translate-x-1/2 w-full max-w-[430px] px-4 z-30" style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}>
          <Button onClick={enterEvent} disabled={entering} className="w-full h-13 py-3.5 bg-primary red-glow font-heading uppercase tracking-mega text-white">
            <Ticket className="w-4 h-4 mr-2" />
            {alreadyEntered ? t('entered') + ' · ' + t('enter_event') : t('enter_event')}
            {event.entry_cost > 0 && !alreadyEntered && ` (${event.entry_cost} ${t('r_points_short')})`}
          </Button>
        </div>
      )}
      {status === 'ended' && (
        <div className="fixed bottom-16 left-1/2 -translate-x-1/2 w-full max-w-[430px] px-4 z-30">
          <div className="bg-card border border-border rounded-xl p-3 text-center text-[10px] uppercase tracking-wide2 text-muted-foreground">{t('event_finalized')}</div>
        </div>
      )}
    </div>
  );
}

function Box({ label, value, accent }) {
  return (
    <div className="bg-card border border-border rounded-xl p-2.5 text-center">
      <div className={cn('font-data font-bold text-base truncate', accent || 'text-white')}>{value}</div>
      <div className="text-[8px] uppercase tracking-wide2 text-muted-foreground">{label}</div>
    </div>
  );
}