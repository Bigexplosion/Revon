import React from 'react';
import { Button } from '@/components/ui/button';

// Bottom sheet confirming race abort.
export default function AbortConfirm({ open, onConfirm, onCancel, t }) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center">
      <div className="absolute inset-0 bg-black/70" onClick={onCancel} />
      <div className="relative w-full max-w-[430px] bg-card border-t-2 border-primary rounded-t-2xl p-5" style={{ paddingBottom: 'calc(1.5rem + env(safe-area-inset-bottom))' }}>
        <div className="font-display font-extrabold uppercase tracking-mega text-primary text-lg text-center">{t('confirm_stop')}</div>
        <p className="text-muted-foreground text-xs text-center mt-2 mb-4">{t('confirm_stop_body')}</p>
        <div className="space-y-2">
          <Button onClick={onConfirm} className="w-full h-12 bg-destructive text-white font-heading font-bold uppercase tracking-mega">{t('abort_run')}</Button>
          <Button onClick={onCancel} variant="outline" className="w-full h-12 border-border font-heading font-bold uppercase tracking-wide2">{t('keep_racing')}</Button>
        </div>
      </div>
    </div>
  );
}