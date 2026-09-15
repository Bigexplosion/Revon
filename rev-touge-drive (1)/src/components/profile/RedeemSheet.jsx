import React, { useState } from 'react';
import { X, Copy, Check } from 'lucide-react';
import { cn } from '@/lib/utils';

export default function RedeemSheet({ open, reward, result, balance, busy, onConfirm, onClose, t }) {
  const [copied, setCopied] = useState(false);
  if (!open || !reward) return null;
  const cost = reward.points_cost || 0;
  const after = result ? result.newBalance : balance - cost;

  const copy = () => {
    if (!result) return;
    navigator.clipboard?.writeText(result.code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center">
      <div className="absolute inset-0 bg-black/70" onClick={onClose} />
      <div className="relative w-full max-w-[430px] bg-card border-t-2 border-primary rounded-t-2xl p-5 max-h-[90dvh] overflow-y-auto no-scrollbar" style={{ paddingBottom: 'calc(2rem + env(safe-area-inset-bottom))' }}>
        <button onClick={onClose} className="absolute top-3 right-3 text-muted-foreground"><X className="w-5 h-5" /></button>

        {!result ? (
          <div className="flex flex-col items-center text-center pt-2">
            <div className="w-20 h-20 rounded-xl overflow-hidden bg-background border border-border mb-3 shrink-0">
              {reward.image ? <img src={reward.image} alt="" className="w-full h-full object-cover" /> : null}
            </div>
            <div className="font-display font-extrabold uppercase tracking-mega text-white text-lg leading-tight">{reward.name}</div>
            <div className="font-data text-data font-bold text-2xl mt-1">{cost} <span className="text-[10px] uppercase tracking-wide2 text-muted-foreground">{t('r_points_short')}</span></div>

            <div className="w-full mt-4 space-y-2">
              <div className="flex justify-between bg-background rounded-lg px-3 py-2 border border-border">
                <span className="text-[10px] uppercase tracking-wide2 text-muted-foreground">{t('balance_before')}</span>
                <span className="font-data text-white font-bold">{balance}</span>
              </div>
              <div className="flex justify-between bg-background rounded-lg px-3 py-2 border border-border">
                <span className="text-[10px] uppercase tracking-wide2 text-muted-foreground">{t('balance_after')}</span>
                <span className={cn('font-data font-bold', after < 0 ? 'text-destructive' : 'text-data')}>{after}</span>
              </div>
            </div>

            <button onClick={onConfirm} disabled={busy} className="w-full mt-4 h-12 bg-primary text-white font-heading font-bold uppercase tracking-wide2 red-glow active:scale-[0.99] disabled:opacity-60">
              {busy ? '...' : t('confirm_redeem')}
            </button>
          </div>
        ) : (
          <div className="flex flex-col items-center text-center pt-2">
            <div className="w-12 h-12 rounded-full bg-data/15 border border-data flex items-center justify-center mb-3"><Check className="w-6 h-6 text-data" /></div>
            <div className="font-display font-extrabold uppercase tracking-mega text-data text-lg">{t('redeem_success')}</div>
            <div className="text-muted-foreground text-xs mt-1">{reward.name}</div>

            <div className="w-full mt-4 border-2 border-dashed border-primary rounded-xl p-4">
              <div className="text-[9px] uppercase tracking-mega text-muted-foreground mb-1">{t('voucher_code')}</div>
              <div className="font-data text-primary font-bold text-xl tracking-wider break-all">{result.code}</div>
              <button onClick={copy} className="mt-3 w-full h-9 border border-primary/50 text-primary font-heading font-bold uppercase tracking-wide2 text-[10px] rounded-lg active:scale-[0.98] flex items-center justify-center gap-1.5">
                {copied ? <><Check className="w-3.5 h-3.5" /> {t('copied')}</> : <><Copy className="w-3.5 h-3.5" /> {t('copy')}</>}
              </button>
            </div>

            <p className="text-muted-foreground text-[11px] mt-3 leading-relaxed">{t('voucher_instructions')}</p>
            <button onClick={onClose} className="w-full mt-4 h-11 bg-primary text-white font-heading font-bold uppercase tracking-wide2 red-glow">{t('close')}</button>
          </div>
        )}
      </div>
    </div>
  );
}