import React, { useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useT } from '@/lib/i18n';
import { X, Users } from 'lucide-react';

export default function MyClubEmptySheet({ open, onClose }) {
  const { t } = useT();

  useEffect(() => {
    if (!open) return;
    const scrollY = window.scrollY;
    const body = document.body;
    const prev = { position: body.style.position, top: body.style.top, left: body.style.left, right: body.style.right, width: body.style.width, overflow: body.style.overflow };
    body.style.position = 'fixed';
    body.style.top = `-${scrollY}px`;
    body.style.left = '0';
    body.style.right = '0';
    body.style.width = '100%';
    body.style.overflow = 'hidden';
    return () => {
      body.style.position = prev.position;
      body.style.top = prev.top;
      body.style.left = prev.left;
      body.style.right = prev.right;
      body.style.width = prev.width;
      body.style.overflow = prev.overflow;
      window.scrollTo(0, scrollY);
    };
  }, [open]);

  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose} className="fixed inset-0 bg-black/70 z-40" />
          <motion.div
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{ y: '100%' }}
            transition={{ type: 'spring', damping: 32, stiffness: 320 }}
            className="fixed bottom-0 left-1/2 -translate-x-1/2 w-full max-w-[430px] z-50 bg-card border-t-2 border-primary rounded-t-3xl"
            style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
          >
            <div className="sticky top-0 bg-card pt-3 pb-2 flex justify-center">
              <div className="w-10 h-1 rounded-full bg-muted-foreground/40" />
            </div>
            <button onClick={onClose} className="absolute top-3 right-3 w-8 h-8 rounded-full bg-background border border-border flex items-center justify-center text-muted-foreground z-20"><X className="w-4 h-4" /></button>
            <div className="px-6 pb-8 pt-2 flex flex-col items-center text-center">
              <div className="w-16 h-16 rounded-full bg-secondary border border-border flex items-center justify-center"><Users className="w-7 h-7 text-muted-foreground" /></div>
              <h2 className="font-display font-extrabold uppercase tracking-mega text-lg text-white mt-3">{t('no_club_title')}</h2>
              <p className="text-white/70 text-sm mt-1.5 max-w-[260px]">{t('no_club_body')}</p>
              <button onClick={onClose} className="mt-5 w-full h-12 rounded-lg bg-primary red-glow font-heading uppercase tracking-mega text-white">{t('browse_clubs')}</button>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}