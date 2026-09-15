import React from 'react';
import { RefreshCw, ListOrdered, Home, TrendingUp, TrendingDown, Clock } from 'lucide-react';
import { formatTime } from '@/lib/format';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

export default function ResultsScreen({ data, onAgain, onLeaderboard, onRoutes, t }) {
  const { finalMs, track, vehicle, prevBest, isFirstRun, deltaMs, splits, newRank, prevRank, eventInfo } = data;
  const isPB = !isFirstRun && deltaMs < 0;
  const rankChange = prevRank && newRank ? prevRank - newRank : null; // positive = moved up

  return (
    <div className="min-h-screen bg-black flex flex-col px-6 pt-10 pb-[calc(1.5rem+env(safe-area-inset-bottom))]">
      {isPB && (
        <div className="text-center mb-4">
          <span className="inline-block bg-primary/15 border border-primary rounded-full px-4 py-1.5 font-display font-extrabold uppercase tracking-mega text-primary text-sm red-glow-soft animate-breath-text">
            {t('new_pb')}
          </span>
        </div>
      )}

      <div className="text-center">
        <div className="text-[10px] uppercase tracking-mega text-muted-foreground mb-1">{t('results')}</div>
        <div className="font-data text-data text-6xl font-bold text-glow-green tabular-nums">{formatTime(finalMs)}</div>
        <div className="mt-2 text-xs uppercase tracking-wide2 text-white/80 font-heading">{track.name} · {t(vehicle.toLowerCase())}</div>
      </div>

      {/* PB comparison */}
      <div className="mt-5 bg-card border border-border rounded-xl p-3 text-center">
        {isFirstRun ? (
          <div className="font-heading font-bold uppercase tracking-wide2 text-data text-sm">{t('first_run_track')}</div>
        ) : (
          <div>
            <div className="text-[9px] uppercase tracking-wide2 text-muted-foreground">{t('personal_best')}</div>
            <div className="font-data text-white text-lg">{formatTime(prevBest)}</div>
            <div className={cn('font-data font-bold text-sm mt-1', isPB ? 'text-data' : 'text-primary')}>
              {isPB ? '−' : '+'}{formatTime(Math.abs(deltaMs))} {isPB ? t('faster') : t('slower')}
            </div>
          </div>
        )}
      </div>

      {/* rank */}
      {newRank > 0 && (
        <div className="mt-3 bg-card border border-border rounded-xl p-3 flex items-center justify-between">
          <div>
            <div className="text-[9px] uppercase tracking-wide2 text-muted-foreground">{t('rank')}</div>
            <div className="font-display font-extrabold text-white text-2xl">#{newRank}</div>
          </div>
          {rankChange != null && rankChange !== 0 && (
            <div className={cn('flex items-center gap-1 font-heading font-bold uppercase tracking-wide2 text-xs', rankChange > 0 ? 'text-data' : 'text-primary')}>
              {rankChange > 0 ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
              {rankChange > 0 ? `${t('rank_up')} ${rankChange}` : `${t('rank_down')} ${Math.abs(rankChange)}`}
            </div>
          )}
        </div>
      )}

      {/* splits */}
      <div className="mt-3 bg-card border border-border rounded-xl p-3">
        <div className="text-[9px] uppercase tracking-mega text-muted-foreground mb-2">{t('splits')}</div>
        <div className="space-y-1.5">
          {splits.map((s) => (
            <div key={s.index} className="flex items-center justify-between">
              <span className="text-[11px] uppercase tracking-wide2 font-heading font-bold text-white">{t('checkpoint')} {s.index}</span>
              <span className="font-data text-data text-sm">{s.ms ? formatTime(s.ms) : '—'}</span>
            </div>
          ))}
          <div className="flex items-center justify-between pt-1.5 border-t border-border">
            <span className="text-[11px] uppercase tracking-wide2 font-heading font-bold text-primary">{t('results')}</span>
            <span className="font-data text-primary text-sm font-bold">{formatTime(finalMs)}</span>
          </div>
        </div>
      </div>

      {/* event placement — pending reward, settled when the event ends */}
      {eventInfo && (
        <div className="mt-3 bg-card border border-border rounded-xl p-3">
          <div className="flex items-center justify-between mb-2">
            <div className="text-[9px] uppercase tracking-mega text-primary font-heading font-bold">{t('event_position')}</div>
            <div className="font-display font-extrabold text-white text-xl">{eventInfo.position ? `#${eventInfo.position}` : '—'}</div>
          </div>
          {eventInfo.position ? (
            <div className="flex items-center justify-between">
              <span className="text-[10px] uppercase tracking-wide2 text-muted-foreground flex items-center gap-1.5"><Clock className="w-3.5 h-3.5" />{t('pending_reward')}</span>
              <span className={cn('font-data font-bold text-sm', eventInfo.hasReward ? 'text-data' : 'text-muted-foreground')}>
                {eventInfo.hasReward ? `+${eventInfo.potentialReward} ${t('r_points_short')}` : t('no_pending')}
              </span>
            </div>
          ) : null}
          <div className="text-[9px] text-muted-foreground italic mt-2">{t('pending_note')}</div>
        </div>
      )}

      <div className="mt-auto pt-5 space-y-2.5">
        <div className="grid grid-cols-2 gap-2.5">
          <Button onClick={onAgain} variant="outline" className="h-12 border-primary text-primary font-heading uppercase tracking-wide2"><RefreshCw className="w-4 h-4 mr-2" />{t('race_again')}</Button>
          <Button onClick={onLeaderboard} variant="outline" className="h-12 border-primary text-primary font-heading uppercase tracking-wide2"><ListOrdered className="w-4 h-4 mr-2" />{t('view_leaderboard')}</Button>
        </div>
        <Button onClick={onRoutes} className="w-full h-12 bg-primary red-glow font-heading uppercase tracking-mega"><Home className="w-4 h-4 mr-2" />{t('back_routes')}</Button>
      </div>
    </div>
  );
}