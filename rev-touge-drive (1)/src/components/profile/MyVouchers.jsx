import React from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { cn } from '@/lib/utils';

export default function MyVouchers({ open, onOpenChange, vouchers, t }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="bg-card border-border max-h-[80dvh] overflow-y-auto no-scrollbar">
        <DialogHeader><DialogTitle className="font-display uppercase tracking-mega">{t('my_vouchers')}</DialogTitle></DialogHeader>
        {vouchers.length === 0 ? (
          <p className="text-center text-muted-foreground py-8 uppercase tracking-wide2 text-xs">{t('no_vouchers')}</p>
        ) : (
          <div className="space-y-2">
            {vouchers.map((v) => {
              const redeemed = v.status === 'redeemed';
              const dateStr = new Date(redeemed ? (v.validated_at || v.redeemed_at || v.created_date) : v.created_date).toLocaleDateString();
              return (
                <div key={v.id} className={cn('flex gap-3 p-2.5 rounded-xl border bg-background', redeemed ? 'border-border opacity-50' : 'border-primary/40')}>
                  <div className="w-14 h-14 rounded-lg overflow-hidden bg-card border border-border shrink-0">
                    {v.reward_image ? <img src={v.reward_image} alt="" className="w-full h-full object-cover" /> : null}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="font-heading font-bold uppercase tracking-wide2 text-white text-xs truncate">{v.reward_name}</div>
                    <div className="font-data text-primary font-bold text-sm tracking-wider mt-0.5 break-all">{v.code}</div>
                    <div className="flex items-center gap-2 mt-1">
                      <span className={cn('text-[8px] uppercase tracking-wide2 font-heading font-bold px-1.5 py-0.5 rounded', redeemed ? 'bg-muted text-muted-foreground' : 'bg-data/15 text-data border border-data/40')}>{redeemed ? t('voucher_redeemed') : t('voucher_unused')}</span>
                      <span className="text-[9px] text-muted-foreground">{dateStr}</span>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}