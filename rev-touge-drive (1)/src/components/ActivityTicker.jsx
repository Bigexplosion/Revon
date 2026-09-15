import React, { useState, useEffect, useRef } from 'react';
import { base44 } from '@/api/base44Client';
import { useT } from '@/lib/i18n';

export default function ActivityTicker() {
  const { lang } = useT();
  const [total, setTotal] = useState(0);
  const [items, setItems] = useState([]);

  useEffect(() => {
    let alive = true;
    const load = async () => {
      try {
        const [tracks, recent, events] = await Promise.all([
          base44.entities.Track.list('-created_date', 50),
          base44.entities.RaceResult.list('-recorded_at', 12),
          base44.entities.Event.list('-created_date', 50),
        ]);
        if (!alive) return;
        const sumActive = tracks.reduce((s, tr) => s + (tr.active_drivers || 0), 0);
        setTotal(sumActive || 0);
        const wins = (events || []).filter((e) => e.winner_nickname).slice(0, 6).map((e) => ({ win: true, nick: e.winner_nickname, name: e.name }));
        const finishes = recent.map((r) => ({ nick: r.player_nickname, code: r.track_code, time: r.finish_time_display }));
        setItems([...wins, ...finishes]);
      } catch {}
    };
    load();
    const refresh = setInterval(load, 20000);
    return () => { alive = false; clearInterval(refresh); };
  }, []);

  const feed = items;

  return (
    <div className="fixed top-0 left-1/2 -translate-x-1/2 w-full max-w-[430px] z-50 h-7 flex items-center bg-black border-b border-red-900/60 overflow-hidden select-none">
      <div className="flex items-center gap-1.5 h-full px-2.5 shrink-0 border-r border-red-900/60 bg-red-950/40 w-max whitespace-nowrap z-10">
        <span className="w-2 h-2 rounded-full bg-red-600 animate-pulse shrink-0 red-glow" />
        <span className="font-mono text-[#00FF66] text-xs font-bold whitespace-nowrap leading-none">{total}</span>
        <span className="text-white text-[11px] font-heading font-extrabold tracking-wide whitespace-nowrap leading-none">{lang === 'zh' ? '競速中' : 'RACING'}</span>
      </div>
      <div className="flex-1 overflow-hidden">
        <div className="flex animate-marquee-slow whitespace-nowrap will-change-transform">
          {[0, 1].map((k) => (
            <span key={k} className="flex items-center">
              {feed.map((it, i) => it.win ? (
                <span key={i} className="px-3 text-[11px] uppercase tracking-wide font-heading font-bold whitespace-nowrap flex items-center gap-1.5 text-white">
                  <span className="w-1.5 h-1.5 rounded-full bg-red-600 shrink-0" />
                  <span className="text-red-500 font-extrabold">{it.nick}</span>
                  <span className="text-white">{lang === 'zh' ? '奪冠' : 'WINS'}</span>
                  <span className="text-[#00FF66] font-mono">{it.name}</span>
                </span>
              ) : (
                <span key={i} className="px-3 text-[11px] uppercase tracking-wide font-heading font-bold whitespace-nowrap flex items-center gap-1.5 text-white">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 shrink-0" />
                  <span className="text-white font-extrabold">{it.nick}</span>
                  <span className="text-neutral-400">{lang === 'zh' ? '完成了' : 'finished'}</span>
                  <span className="text-[#00FF66] font-mono">{it.code}</span>
                  <span className="text-neutral-500">·</span>
                  <span className="font-mono text-[#00FF66]">{it.time}</span>
                </span>
              ))}
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}