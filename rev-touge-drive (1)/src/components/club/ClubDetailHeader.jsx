import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ChevronLeft, Camera, ImageIcon } from 'lucide-react';
import { base44 } from '@/api/base44Client';
import { useT } from '@/lib/i18n';
import ClubBadge from '@/components/ClubBadge';

const EXPANDED_H = 144;
const COLLAPSED_H = 52;
const COLLAPSE_MS = 3000;

/**
 * Club detail header: full header on open, auto-collapses into a compact sticky
 * bar after ~3s. Tap the compact bar or scroll up to re-expand; scroll down
 * collapses it again. Admins can tap the badge to upload a badge image and use
 * the top-right control to upload a header background image.
 */
export default function ClubDetailHeader({ club, level, totalPoints, strongestTrackCode, membersCount, accent, isAdmin, onBack, onUpdated }) {
  const { t } = useT();
  const [expanded, setExpanded] = useState(true);
  const [uploading, setUploading] = useState(null);
  const badgeInputRef = useRef(null);
  const headerInputRef = useRef(null);
  const timerRef = useRef(null);
  const lastScrollY = useRef(typeof window !== 'undefined' ? window.scrollY : 0);

  const scheduleCollapse = () => {
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => setExpanded(false), COLLAPSE_MS);
  };

  useEffect(() => {
    scheduleCollapse();
    const onScroll = () => {
      const y = window.scrollY;
      if (y < lastScrollY.current - 4) {
        setExpanded(true);
      } else if (y > lastScrollY.current + 4) {
        setExpanded(false);
        clearTimeout(timerRef.current);
      }
      lastScrollY.current = y;
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => { clearTimeout(timerRef.current); window.removeEventListener('scroll', onScroll); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (expanded) scheduleCollapse();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [expanded]);

  const upload = async (e, kind) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploading(kind);
    try {
      const { file_url } = await base44.integrations.Core.UploadFile({ file });
      const field = kind === 'badge' ? { badge_image: file_url } : { header_image: file_url };
      await base44.entities.Club.update(club.id, field);
      onUpdated?.();
    } catch (err) {}
    setUploading(null);
    e.target.value = '';
  };

  return (
    <>
      <motion.header
        animate={{ height: expanded ? EXPANDED_H : COLLAPSED_H }}
        transition={{ duration: 0.35, ease: 'easeInOut' }}
        className="sticky top-7 z-30 relative overflow-hidden shrink-0"
      >
        {/* background */}
        <div className="absolute inset-0">
          {club.header_image ? (
            <img src={club.header_image} alt="" className="absolute inset-0 w-full h-full object-cover" />
          ) : (
            <div className="absolute inset-0 grid-bg bg-secondary" />
          )}
          <div className="absolute inset-0 bg-gradient-to-b from-black/75 via-black/45 to-black/85" />
        </div>

        <button onClick={onBack} className="absolute top-3 left-3 z-30 w-9 h-9 rounded-full bg-black/60 border border-border flex items-center justify-center text-white">
          <ChevronLeft className="w-5 h-5" />
        </button>

        {isAdmin && (
          <button
            onClick={() => headerInputRef.current?.click()}
            className="absolute top-3 right-3 z-30 inline-flex items-center gap-1.5 h-9 px-2.5 rounded-full bg-black/60 border border-border text-white text-[9px] font-heading font-bold uppercase tracking-wide2"
          >
            <ImageIcon className="w-3.5 h-3.5" /> {uploading === 'header' ? '…' : t('edit_banner')}
          </button>
        )}

        {/* expanded content */}
        <AnimatePresence>
          {expanded && (
            <motion.div
              key="full"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.2 }}
              className="absolute inset-0 flex flex-col items-center justify-center px-16"
            >
              <div className="relative">
                <ClubBadge club={club} size={56} accent={accent} />
                {isAdmin && (
                  <button
                    onClick={() => badgeInputRef.current?.click()}
                    className="absolute -bottom-1 -right-1 w-6 h-6 rounded-full bg-primary border-2 border-black flex items-center justify-center text-white"
                  >
                    <Camera className="w-3 h-3" />
                  </button>
                )}
                {uploading === 'badge' && (
                  <div className="absolute inset-0 rounded-full bg-black/60 flex items-center justify-center text-[8px] text-white">…</div>
                )}
              </div>
              <h1 className="font-display font-extrabold uppercase tracking-mega text-lg text-white mt-2 truncate max-w-full">{club.name}</h1>
              <div className="text-[10px] uppercase tracking-wide2 text-white/70">{membersCount} {t('members')}</div>
              <div className="text-[10px] uppercase tracking-wide2 text-white/50 mt-0.5 text-center">{t('club_level')} {level} · {totalPoints.toLocaleString()} {t('club_points')} · {t('strongest_route')} {strongestTrackCode || '—'}</div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* collapsed content */}
        <AnimatePresence>
          {!expanded && (
            <motion.div
              key="compact"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.2 }}
              onClick={() => setExpanded(true)}
              className="absolute inset-0 flex items-center gap-3 pl-14 pr-4 cursor-pointer"
            >
              <ClubBadge club={club} size={32} accent={accent} glow={false} />
              <span className="font-display font-extrabold uppercase tracking-mega text-sm text-white truncate">{club.name}</span>
            </motion.div>
          )}
        </AnimatePresence>
      </motion.header>

      <input ref={badgeInputRef} type="file" accept="image/*" className="hidden" onChange={(e) => upload(e, 'badge')} />
      <input ref={headerInputRef} type="file" accept="image/*" className="hidden" onChange={(e) => upload(e, 'header')} />
    </>
  );
}