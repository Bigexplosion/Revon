import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useT } from '@/lib/i18n';
import { formatTime } from '@/lib/format';
import { Button } from '@/components/ui/button';
import { X, MapPin, Users, Trophy } from 'lucide-react';
import { base44 } from '@/api/base44Client';
import ClubBadge from '@/components/ClubBadge';
import RacerAvatar from '@/components/RacerAvatar';

function Stat({ icon: Icon, label, value, data }) {
  return (
    <div className="flex items-center gap-2 bg-background rounded-lg p-2.5 border border-border">
      {Icon && <Icon className="w-4 h-4 text-muted-foreground shrink-0" />}
      <div className="min-w-0">
        <div className="text-[8px] uppercase tracking-wide2 text-muted-foreground">{label}</div>
        <div className={data ? 'font-data text-data font-bold text-sm' : 'font-heading font-bold text-sm text-white truncate'}>{value}</div>
      </div>
    </div>
  );
}

export default function ClubBottomSheet({ open, club, rank, score, strongestTrackCode, topMembers, joined, requested, onJoin, onClose }) {
  const { t, lang } = useT();
  const [avatars, setAvatars] = useState({});

  useEffect(() => {
    if (open && club) {
      document.body.style.overflow = 'hidden';
      base44.entities.Player.list('-created_date', 200).then((p) => {
        const m = {};
        (p || []).forEach((pl) => { m[pl.nickname] = pl.avatar || ''; });
        setAvatars(m);
      }).catch(() => {});
    } else {
      document.body.style.overflow = '';
    }
    return () => { document.body.style.overflow = ''; };
  }, [open, club]);

  return (
    <AnimatePresence>
      {open && club && (
        <>
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose} className="fixed inset-0 bg-black/70 z-40" />
          <motion.div
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{ y: '100%' }}
            transition={{ type: 'spring', damping: 32, stiffness: 320 }}
            className="fixed inset-x-0 bottom-0 z-50 mx-auto w-full max-w-[430px] bg-card border-t-2 border-primary rounded-t-3xl max-h-[82vh] overflow-y-auto no-scrollbar"
            style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
          >
            <div className="sticky top-0 bg-card pt-3 pb-2 flex justify-center z-10">
              <div className="w-10 h-1 rounded-full bg-muted-foreground/40" />
            </div>
            <button onClick={onClose} className="absolute top-3 right-3 w-8 h-8 rounded-full bg-background border border-border flex items-center justify-center text-muted-foreground z-20"><X className="w-4 h-4" /></button>
            <div className="px-5 pb-6">
              <div className="flex flex-col items-center text-center">
                <ClubBadge club={club} size={64} className="red-glow" />
                <h2 className="font-display font-extrabold uppercase tracking-mega text-xl text-white mt-2">{club.name}</h2>
                {club.motto && <p className="text-white/70 italic text-sm mt-1">"{club.motto}"</p>}
              </div>
              <div className="grid grid-cols-2 gap-2 mt-4">
                <Stat icon={Trophy} label={t('rank')} value={rank ? `#${rank}` : '—'} />
                <Stat icon={Users} label={t('members')} value={club.member_count || 0} />
                <Stat icon={MapPin} label={t('region')} value={club.region || '—'} />
                <Stat label={t('club_points')} value={score.toLocaleString()} data />
              </div>
              {strongestTrackCode && (
                <div className="mt-3 flex justify-center">
                  <span className="px-3 py-1.5 rounded-lg bg-primary/15 border border-primary/40 text-primary text-xs font-heading font-bold uppercase tracking-wide2">{strongestTrackCode} {t('dominant')}</span>
                </div>
              )}
              <div className="mt-4">
                <div className="text-[10px] uppercase tracking-mega text-muted-foreground mb-2">{lang === 'zh' ? '頂尖成績' : 'TOP RESULTS'}</div>
                {topMembers.length === 0 ? (
                  <p className="text-center text-muted-foreground text-xs uppercase tracking-wide2 py-2">{t('no_results')}</p>
                ) : (
                  <div className="space-y-1.5">
                    {topMembers.map((r, i) => (
                      <div key={r.id} className="flex items-center gap-2 bg-background rounded-lg p-2 border border-border">
                        <span className="w-5 text-center font-data text-xs text-muted-foreground">#{i + 1}</span>
                        <RacerAvatar src={avatars[r.player_nickname]} nickname={r.player_nickname} size={26} />
                        <span className="flex-1 font-heading font-semibold uppercase tracking-wide2 text-xs text-white truncate">{r.player_nickname}</span>
                        <span className="text-[9px] uppercase text-muted-foreground">{r.track_code}</span>
                        <span className="font-data text-data text-xs font-bold">{formatTime(r.finish_time_ms)}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
              <Button onClick={onJoin} disabled={joined || requested} className="w-full h-12 mt-5 bg-primary red-glow font-heading uppercase tracking-mega">
                {joined || requested ? t('entered') : `${t('request_join')} ›`}
              </Button>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}