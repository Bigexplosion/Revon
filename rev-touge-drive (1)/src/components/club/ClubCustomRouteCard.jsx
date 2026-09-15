import React, { useState } from 'react';
import { useT } from '@/lib/i18n';
import { formatTime } from '@/lib/format';
import { cn } from '@/lib/utils';

const diffColor = {
  NORMAL: 'border-emerald-500/50 text-emerald-400 bg-emerald-500/20',
  HARD: 'border-yellow-500/50 text-yellow-400 bg-yellow-500/20',
  EXTREME: 'border-red-500/50 text-red-400 bg-red-500/20',
  ROOKIE: 'border-emerald-500/50 text-emerald-400 bg-emerald-500/20',
  PRO: 'border-yellow-500/50 text-yellow-400 bg-yellow-500/20',
  EXPERT: 'border-yellow-500/50 text-yellow-400 bg-yellow-500/20',
  LEGEND: 'border-red-500/50 text-red-400 bg-red-500/20',
};

export default function ClubCustomRouteCard({ route, topResults }) {
  const { t, lang } = useT();
  const [imgOk, setImgOk] = useState(!!route.cover_image);
  const diffKey = (route.difficulty || 'NORMAL').toUpperCase();
  const normalizedDiff = diffKey === 'ROOKIE' ? 'NORMAL' : (diffKey === 'PRO' || diffKey === 'EXPERT') ? 'HARD' : diffKey === 'LEGEND' ? 'EXTREME' : diffKey;
  return (
    <div className="w-[75vw] max-w-[320px] shrink-0 snap-center">
      <div className="rounded-3xl overflow-hidden border border-border bg-card relative" style={{ height: '60vh' }}>
        {route.cover_image && imgOk ? (
          <img src={route.cover_image} alt="" onError={() => setImgOk(false)} className="absolute inset-0 w-full h-full object-cover" />
        ) : (
          <div className="absolute inset-0 bg-[#0A0A0A] flex items-center justify-center p-4">
            <span className="font-heading font-extrabold uppercase tracking-wide2 text-white text-lg text-center truncate">{route.name}</span>
          </div>
        )}
        <div className="absolute inset-0 vignette" />
        <div className="absolute top-3 left-3 right-3 flex items-start justify-between">
          <div>
            <div className="font-display font-extrabold text-2xl text-white text-glow-red">{route.name}</div>
            <div className="text-[10px] uppercase tracking-wide2 text-white/70">{route.club_name}</div>
          </div>
          <span className={cn('px-2.5 py-1 rounded-md border text-[10px] font-heading font-bold uppercase tracking-wide2', diffColor[normalizedDiff] || diffColor.NORMAL)}>{normalizedDiff}</span>
        </div>
        <div className="absolute bottom-3 left-3 right-3 flex items-end justify-between">
          <div>
            <div className="font-data text-data text-xl font-bold">{route.distance_km ? `${route.distance_km}km` : '—'}</div>
            <div className="text-[9px] uppercase tracking-wide2 text-white/60">{route.corners_count || 0} {lang === 'zh' ? '彎道' : 'CORNERS'}</div>
          </div>
          <span className="px-2 py-1 rounded-full bg-black/60 border border-border text-[9px] uppercase tracking-wide2 text-white/80">{t('members_only')}</span>
        </div>
      </div>
      <div className="mt-3 bg-card border border-border rounded-xl p-3">
        <div className="text-[10px] uppercase tracking-mega text-muted-foreground mb-2">TOP 5</div>
        {topResults.length === 0 ? (
          <p className="text-center text-muted-foreground text-xs uppercase tracking-wide2 py-2">{t('no_times_yet')}</p>
        ) : (
          <div className="space-y-1.5">
            {topResults.slice(0, 5).map((r, i) => (
              <div key={r.id} className="flex items-center gap-2">
                <span className="w-5 text-center font-data text-xs text-muted-foreground">#{i + 1}</span>
                <span className="flex-1 font-heading font-semibold uppercase tracking-wide2 text-xs text-white truncate">{r.player_nickname}</span>
                <span className="font-data text-data text-xs font-bold">{formatTime(r.finish_time_ms)}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}