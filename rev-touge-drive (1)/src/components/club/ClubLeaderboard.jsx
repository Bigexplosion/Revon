import React, { useMemo, useState, useEffect } from 'react';
import { useT } from '@/lib/i18n';
import { formatTime } from '@/lib/format';
import { cn } from '@/lib/utils';
import RacerAvatar from '@/components/RacerAvatar';
import { base44 } from '@/api/base44Client';

export default function ClubLeaderboard({ results }) {
  const { t } = useT();
  const weekAgo = Date.now() - 7 * 86400000;
  const [avatars, setAvatars] = useState({});
  useEffect(() => {
    base44.entities.Player.list('-created_date', 200).then((p) => {
      const m = {};
      (p || []).forEach((pl) => { m[pl.nickname] = pl.avatar || ''; });
      setAvatars(m);
    }).catch(() => {});
  }, []);

  const { ranked, mostActive } = useMemo(() => {
    const weekResults = results.filter((r) => !r.is_custom_route && r.recorded_at && new Date(r.recorded_at).getTime() > weekAgo);
    const bestByNick = {};
    const countByNick = {};
    weekResults.forEach((r) => {
      if (!bestByNick[r.player_nickname] || r.finish_time_ms < bestByNick[r.player_nickname]) bestByNick[r.player_nickname] = r.finish_time_ms;
      countByNick[r.player_nickname] = (countByNick[r.player_nickname] || 0) + 1;
    });
    const ranked = Object.entries(bestByNick).map(([nick, time]) => ({ nick, time, runs: countByNick[nick] })).sort((a, b) => a.time - b.time);
    const mostActive = Object.entries(countByNick).map(([nick, runs]) => ({ nick, runs })).sort((a, b) => b.runs - a.runs);
    return { ranked, mostActive };
  }, [results, weekAgo]);

  const podiumStyle = ['text-primary text-glow-red', 'text-white', 'text-data'];

  return (
    <div className="px-4 pb-32 space-y-4">
      <div>
        <div className="flex items-center gap-2 mb-2">
          <h3 className="font-display font-bold uppercase tracking-mega text-sm text-white">{t('weekly_ranking')}</h3>
          <span className="text-[9px] uppercase tracking-wide2 text-muted-foreground ml-auto">{t('this_week')}</span>
        </div>
        {ranked.length === 0 ? (
          <p className="text-center text-muted-foreground text-xs uppercase tracking-wide2 py-4">{t('no_weekly_data')}</p>
        ) : (
          <div className="space-y-2">
            {ranked.slice(0, 10).map((r, i) => (
              <div key={r.nick} className={cn('flex items-center gap-3 p-3 rounded-lg border bg-card', i < 3 && 'border-primary/40')}>
                <div className="w-7 text-center font-data text-sm text-muted-foreground">#{i + 1}</div>
                <RacerAvatar src={avatars[r.nick]} nickname={r.nick} size={36} />
                <div className="flex-1 min-w-0">
                  <div className="font-heading font-semibold uppercase tracking-wide2 text-sm text-white truncate">{r.nick}</div>
                  <div className="text-[10px] uppercase tracking-wide2 text-muted-foreground">{r.runs} {t('runs_unit')}</div>
                </div>
                <div className="font-data text-data font-bold">{formatTime(r.time)}</div>
              </div>
            ))}
          </div>
        )}
      </div>
      <div>
        <div className="flex items-center gap-2 mb-2">
          <h3 className="font-display font-bold uppercase tracking-mega text-sm text-white">{t('most_active')}</h3>
        </div>
        {mostActive.length === 0 ? (
          <p className="text-center text-muted-foreground text-xs uppercase tracking-wide2 py-4">{t('no_weekly_data')}</p>
        ) : (
          <div className="grid grid-cols-3 gap-2">
            {mostActive.slice(0, 3).map((m, i) => (
              <div key={m.nick} className="bg-card border border-border rounded-lg p-3 text-center">
                <div className={cn('font-display font-extrabold text-lg mb-1', podiumStyle[i])}>{i + 1}</div>
                <div className="font-heading font-bold uppercase tracking-wide2 text-xs text-white truncate">{m.nick}</div>
                <div className="font-data text-data text-sm font-bold">{m.runs} {t('runs_unit')}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}