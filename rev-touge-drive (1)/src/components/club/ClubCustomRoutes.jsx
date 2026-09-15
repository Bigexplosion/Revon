import React, { useState, useEffect } from 'react';
import { base44 } from '@/api/base44Client';
import { useT } from '@/lib/i18n';
import ClubCustomRouteCard from './ClubCustomRouteCard';

export default function ClubCustomRoutes({ clubId }) {
  const { t } = useT();
  const [routes, setRoutes] = useState([]);
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    base44.entities.CustomRoute.filter({ club_id: clubId }, '-created_date', 50).then(async (r) => {
      setRoutes(r);
      if (r.length) {
        const res = await base44.entities.RaceResult.filter({ club_id: clubId }, 'finish_time_ms', 500).catch(() => []);
        setResults(res.filter((x) => x.is_custom_route));
      }
      setLoading(false);
    }).catch(() => setLoading(false));
  }, [clubId]);

  const topFor = (routeId) => results.filter((r) => r.custom_route_id === routeId).sort((a, b) => a.finish_time_ms - b.finish_time_ms);

  if (loading) return <div className="flex justify-center py-10"><div className="w-7 h-7 border-4 border-primary/30 border-t-primary rounded-full animate-spin" /></div>;

  return (
    <div className="pb-32">
      <div className="px-4 mb-3">
        <h3 className="font-display font-bold uppercase tracking-mega text-sm text-white">{t('club_routes')}</h3>
      </div>
      {routes.length === 0 ? (
        <p className="text-center text-muted-foreground text-xs uppercase tracking-wide2 py-8">{t('no_club_routes')}</p>
      ) : (
        <div className="flex gap-3 overflow-x-auto no-scrollbar snap-x snap-mandatory scroll-px-4 px-4 pb-4">
          {routes.map((r) => <ClubCustomRouteCard key={r.id} route={r} topResults={topFor(r.id)} />)}
        </div>
      )}
    </div>
  );
}