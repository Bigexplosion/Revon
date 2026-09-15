import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronDown } from 'lucide-react';
import { base44 } from '@/api/base44Client';
import { useT } from '@/lib/i18n';
import Logo from '@/components/Logo';
import TrackCard from '@/components/TrackCard';

export default function Routes() {
  const { t } = useT();
  const navigate = useNavigate();
  const [tracks, setTracks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [region, setRegion] = useState('TAICHUNG');
  const [me, setMe] = useState(null);

  useEffect(() => {
    base44.auth.me().then((u) => {
      setMe(u);
      if (u?.region_city) setRegion((u.region_city || '').toUpperCase());
    }).catch(() => {});
    base44.entities.Track.list('-created_date', 50).then((d) => {
      setTracks(d);
      setLoading(false);
    }).catch(() => setLoading(false));
  }, []);

  return (
    <div className="relative bg-black h-[100dvh] overflow-hidden">
      {/* Floating top header over card */}
      <header className="absolute top-2 inset-x-0 z-30 px-5 flex items-center justify-between pointer-events-none">
        {/* Left side: REV-ON Logo */}
        <div className="flex items-center gap-2 pointer-events-auto">
          <Logo size="text-2xl" />
        </div>

        {/* Right side: 賽道 / 山路 switcher */}
        <div className="flex items-center gap-2 pointer-events-auto">
          <div className="flex items-center bg-black/70 backdrop-blur border border-white/20 rounded-full p-1 text-xs font-heading font-bold uppercase tracking-wider">
            <button className="px-3 py-1 rounded-full text-white/70 hover:text-white transition-colors">賽道</button>
            <button className="px-3.5 py-1 rounded-full bg-red-600 text-white red-glow shadow">山路</button>
          </div>
        </div>
      </header>

      {loading ? (
        <div className="h-full flex items-center justify-center"><div className="w-8 h-8 border-4 border-primary/30 border-t-primary rounded-full animate-spin" /></div>
      ) : (
        <div className="h-full overflow-y-scroll scroll-snap-y no-scrollbar pt-0">
          {tracks.map((tr) => (
            <TrackCard key={tr.id} track={tr} onRace={() => navigate(`/race/${tr.id}`)} onView={() => navigate(`/race/${tr.id}`)} />
          ))}
        </div>
      )}
    </div>
  );
}