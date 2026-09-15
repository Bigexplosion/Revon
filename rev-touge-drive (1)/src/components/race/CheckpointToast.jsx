import React, { useEffect, useState } from 'react';
import { formatTime } from '@/lib/format';

// Brief on-screen confirmation when a checkpoint is cleared.
export default function CheckpointToast({ toast, t }) {
  const [show, setShow] = useState(false);
  useEffect(() => {
    if (!toast) return;
    setShow(true);
    const iv = setTimeout(() => setShow(false), 1600);
    return () => clearTimeout(iv);
  }, [toast?.key]);

  if (!toast || !show) return null;
  return (
    <div className="absolute top-14 inset-x-0 flex justify-center pointer-events-none px-4">
      <div className="bg-data/15 border border-data rounded-lg px-4 py-2 green-glow animate-breath-text">
        <span className="font-heading font-bold uppercase tracking-wide2 text-data text-sm">
          {t('checkpoint')} {toast.index} {t('cleared')} · {formatTime(toast.ms)}
        </span>
      </div>
    </div>
  );
}