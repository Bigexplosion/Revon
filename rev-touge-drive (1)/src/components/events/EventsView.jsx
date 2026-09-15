import React, { useState, useEffect } from 'react';
import { base44 } from '@/api/base44Client';
import { useT } from '@/lib/i18n';
import { eventStatus, countdownStr } from '@/lib/eventStatus';
import EventCard from '@/components/events/EventCard';

export default function EventsView() {
  const { t } = useT();
  const [events, setEvents] = useState([]);
  const [me, setMe] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    base44.auth.me().then(setMe).catch(() => {});
    base44.entities.Event.list('-start_at', 50).then((d) => { setEvents(d); setLoading(false); }).catch(() => setLoading(false));
  }, []);

  const rPoints = me?.r_points ?? 0;

  const byStatus = (s) => events.filter((e) => eventStatus(e) === s);
  const live = byStatus('live');
  const upcoming = byStatus('upcoming');
  const ended = byStatus('ended');

  return (
    <div className="relative pb-32">
      {/* pinned R points */}
      <div className="flex justify-end px-4 pt-1 pb-2">
        <div className="flex items-center gap-1.5 bg-neutral-950/90 border border-emerald-500/60 rounded-full px-4 py-1.5 green-glow shadow-md">
          <span className="text-xs font-heading font-bold text-neutral-300">R 點數</span>
          <span className="font-mono text-[#00FF66] font-bold text-base tracking-wider">{rPoints.toLocaleString()}</span>
        </div>
      </div>

      {loading ? (
        <div className="flex justify-center py-16"><div className="w-8 h-8 border-4 border-red-500/30 border-t-red-500 rounded-full animate-spin" /></div>
      ) : events.length === 0 ? (
        <p className="text-center text-neutral-400 py-16 uppercase tracking-wider text-sm">{t('no_events')}</p>
      ) : (
        <div className="px-4 space-y-4">
          {live.length > 0 && (
            <Section title={t('live')} accent="text-red-500">
              {live.map((e) => <EventCard key={e.id} event={e} />)}
            </Section>
          )}
          {upcoming.length > 0 && (
            <Section title={t('upcoming')} accent="text-yellow-400">
              {upcoming.map((e) => <EventCard key={e.id} event={e} />)}
            </Section>
          )}
          {ended.length > 0 && (
            <Section title={t('ended')} accent="text-white">
              {ended.map((e) => <EventCard key={e.id} event={e} />)}
            </Section>
          )}
        </div>
      )}
    </div>
  );
}

function Section({ title, accent, children }) {
  return (
    <div>
      <h2 className={`font-heading font-extrabold uppercase tracking-wide text-lg mb-3 ${accent}`}>{title}</h2>
      <div className="space-y-3">{children}</div>
    </div>
  );
}