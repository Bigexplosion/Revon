import React, { useState, useEffect } from 'react';
import { Image } from '@/components/ui/image';
import { base44 } from '@/api/base44Client';
import { formatTime } from '@/lib/format';
import { cn } from '@/lib/utils';
import { useT } from '@/lib/i18n';

const diffColor = {
  NORMAL: 'bg-emerald-500 text-black',
  HARD: 'bg-yellow-400 text-black',
  EXTREME: 'bg-red-600 text-white',
  ROOKIE: 'bg-emerald-500 text-black',
  PRO: 'bg-yellow-400 text-black',
  EXPERT: 'bg-yellow-400 text-black',
  LEGEND: 'bg-red-600 text-white',
};

export default function TrackCard({ track, onRace, onView }) {
  const { t, lang } = useT();
  const name = lang === 'zh' && track.name_zh ? track.name_zh : (track.name || track.name_zh || track.code);
  const [top3, setTop3] = useState([]);
  const online = track.active_drivers || 12;

  const rawDiff = (track.difficulty || 'NORMAL').toUpperCase();
  const normalizedDiff = rawDiff === 'ROOKIE' ? 'NORMAL' : (rawDiff === 'PRO' || rawDiff === 'EXPERT') ? 'HARD' : rawDiff === 'LEGEND' ? 'EXTREME' : rawDiff;

  useEffect(() => {
    base44.entities.RaceResult.filter({ track_code: track.code }, 'finish_time_ms', 3)
      .then(setTop3).catch(() => setTop3([]));
  }, [track.code]);

  return (
    <section className="snap-card relative h-[100dvh] w-full bg-black flex items-center justify-center p-2 pt-0">
      <div className="relative w-full h-full rounded-[24px] overflow-hidden bg-black red-glow">
        <Image src={track.cover_image} alt={name} fittingType="fill" className="absolute inset-0 w-full h-full object-cover rounded-[24px] overflow-hidden" />
        <div className="absolute inset-0 vignette" />
        <div className="absolute inset-0 red-overlay" />

        {/* top-left online pill badge directly under REV-ON logo */}
        <div className="absolute top-[20px] left-4 flex items-center gap-1.5 bg-black/60 backdrop-blur border border-white/20 rounded-full px-3 py-1 text-xs z-10 shadow">
          <span className="w-2 h-2 rounded-full bg-emerald-400 green-glow shrink-0" />
          <span className="text-[#00FF66] font-mono font-bold">{online}</span>
          <span className="text-white font-heading font-medium">位車手在線</span>
        </div>

        {/* top-right region & difficulty directly under 賽道/山路 button */}
        <div className="absolute top-[20px] right-4 flex items-center gap-2 z-10">
          <div className="flex items-center gap-1 text-white font-heading font-bold text-xs uppercase tracking-wide drop-shadow">
            <span className="w-2 h-2 rounded-full bg-red-600 red-glow shrink-0" />
            <span>{track.region || 'TAICHUNG'}</span>
          </div>
          <div className={cn('px-3 py-1 rounded-full text-xs font-heading font-bold uppercase tracking-wide border border-transparent shadow', diffColor[normalizedDiff] || diffColor.NORMAL)}>
            {rawDiff === 'NORMAL' || rawDiff === 'ROOKIE' ? '一般' : t(normalizedDiff.toLowerCase())}
          </div>
        </div>

        {/* bottom info */}
        <div className="absolute bottom-0 inset-x-0 p-5 pb-24">
          <div className="flex items-end justify-between gap-4 mb-4">
            <div>
              <div className="font-display text-6xl font-extrabold text-white text-glow-red leading-none drop-shadow-[0_0_12px_rgba(229,9,20,0.6)]">{track.code}</div>
              <div className="font-heading font-semibold uppercase tracking-wide2 text-white text-base mt-1 drop-shadow-[0_0_8px_rgba(229,9,20,0.4)]">{name}</div>
              <div className="text-muted-foreground text-xs uppercase tracking-wide2">{track.region}</div>
            </div>
            <div className="text-right">
              <div className="text-data font-data text-2xl font-bold">{track.distance_km}<span className="text-sm">km</span></div>
              <div className="text-white/70 text-xs uppercase tracking-wide2">{track.corners_count} {lang === 'zh' ? '彎' : 'corners'}</div>
            </div>
          </div>

          {/* top 3 mini leaderboard */}
          <div className="mb-4 space-y-1.5">
            {top3.length === 0 ? (
              <div className="text-white/40 text-[10px] uppercase tracking-wide2">{t('no_times_yet')}</div>
            ) : (
              top3.map((r, i) => (
                <div key={r.id} className={cn('flex items-center gap-2', i === 0 && 'bg-primary/15 border border-primary/40 rounded-md px-2 py-1.5')}>
                  <span className={cn('flex items-center justify-center rounded-full font-data font-bold shrink-0', i === 0 ? 'w-7 h-7 text-xs bg-primary text-white' : 'w-5 h-5 text-[10px] bg-white/15 text-white')}>{i + 1}</span>
                  <span className={cn('font-heading uppercase tracking-wide2 truncate flex-1', i === 0 ? 'text-white text-sm font-bold' : 'text-white/80 text-xs')}>{r.player_nickname}</span>
                  <span className={cn('font-data font-bold shrink-0', i === 0 ? 'text-data text-sm' : 'text-data text-xs')}>{formatTime(r.finish_time_ms)}</span>
                </div>
              ))
            )}
          </div>

          {/* RACE — dominant CTA */}
          <button onClick={onRace} className="w-full h-16 bg-primary hover:bg-primary/90 text-white font-heading font-extrabold uppercase tracking-mega text-lg rounded-xl red-glow flex items-center justify-center gap-2">
            {t('race')} <span className="text-xl">›</span>
          </button>
        </div>
      </div>
    </section>
  );
}