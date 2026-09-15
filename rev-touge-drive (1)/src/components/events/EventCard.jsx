import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Clock, Trophy } from 'lucide-react';
import { useT } from '@/lib/i18n';
import { eventStatus, countdownStr } from '@/lib/eventStatus';
import { cn } from '@/lib/utils';

export default function EventCard({ event, to }) {
  const { t } = useT();
  const navigate = useNavigate();
  const [imgOk, setImgOk] = useState(true);
  const status = eventStatus(event);
  const now = Date.now();
  const remaining = status === 'upcoming' ? new Date(event.start_at).getTime() - now : new Date(event.end_at).getTime() - now;

  const statusStyle = {
    upcoming: 'border-yellow-500/50 text-yellow-400 bg-neutral-950/80',
    live: 'border-red-500/60 text-red-500 bg-neutral-950/80 animate-breath-text',
    ended: 'border-neutral-700/60 text-neutral-300 bg-neutral-950/80',
  }[status];

  const cardStyle = status === 'ended' ? 'border-neutral-800' : status === 'live' ? 'border-red-600/60 red-glow-soft' : 'border-neutral-800';
  const dest = to || `/event/${event.id}`;
  const showImg = event.banner_image && imgOk;

  return (
    <button onClick={() => navigate(dest)} className={cn('w-full text-left rounded-2xl overflow-hidden border bg-neutral-950 active:scale-[0.99] transition-transform shadow-md', cardStyle)}>
      <div className="relative h-32">
        {showImg ? (
          <img src={event.banner_image} alt={event.name} onError={() => setImgOk(false)} className="absolute inset-0 w-full h-full object-cover" />
        ) : (
          <div className="absolute inset-0 bg-[#0A0A0A] flex items-center justify-center p-3">
            <span className="font-heading font-extrabold uppercase tracking-wide text-white text-base text-center truncate">{event.name}</span>
          </div>
        )}
        <div className="absolute inset-0 bg-gradient-to-t from-black via-black/40 to-transparent" />
        <div className={cn('absolute top-3 left-3 max-w-[calc(100%-1.5rem)] px-2.5 py-1 rounded-lg border text-[10px] font-heading font-bold uppercase tracking-wider flex items-center gap-1.5 overflow-hidden backdrop-blur-sm', statusStyle)}>
          {status === 'live' && <span className="w-1.5 h-1.5 rounded-full bg-red-500 animate-pulse" />}
          {status === 'upcoming' && <Clock className="w-3 h-3 text-yellow-400" />}
          {status === 'ended' && <Trophy className="w-3 h-3 text-neutral-300" />}
          {t(status)} {status !== 'ended' && `· ${countdownStr(remaining)}`}
        </div>
        <div className="absolute bottom-3 left-3 right-3">
          <div className="font-display font-extrabold uppercase tracking-wide text-white text-lg leading-tight">{event.name}</div>
          <div className="text-xs uppercase tracking-wider text-neutral-300 mt-0.5 font-medium">{(event.track_names || event.track_codes || []).join(' · ')}</div>
        </div>
      </div>
      <div className="p-3.5 flex items-center justify-between border-t border-neutral-800/80 bg-neutral-950">
        <div>
          <div className="text-[10px] font-heading font-semibold uppercase tracking-wider text-neutral-400">{t('entry_cost_label')}</div>
          <div className="font-heading font-bold text-sm text-[#00FF66] mt-0.5">
            {event.entry_cost > 0 ? `${event.entry_cost} ${t('r_points_short')}` : t('entry_free')}
          </div>
        </div>
        <div className="text-right">
          <div className="text-[10px] font-heading font-semibold uppercase tracking-wider text-neutral-400">{t('reward_pool')}</div>
          <div className="font-mono text-[#00FF66] font-bold text-sm tracking-wider mt-0.5">
            {(event.reward_pool || 0).toLocaleString()} {t('r_points_short')}
          </div>
        </div>
      </div>
    </button>
  );
}