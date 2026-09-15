import React, { useState } from 'react';
import { useT } from '@/lib/i18n';
import { cn } from '@/lib/utils';

export default function RewardCard({ reward, balance, onRedeem }) {
  const { t } = useT();
  const [imgOk, setImgOk] = useState(!!reward.image);
  const outOfStock = (reward.stock || 0) <= 0;
  const cost = reward.points_cost || 0;
  const insufficient = !outOfStock && balance < cost;
  const need = cost - balance;

  return (
    <div className={cn('rounded-2xl overflow-hidden border border-border bg-card flex flex-col', outOfStock && 'opacity-50')}>
      <div className="relative aspect-square bg-background">
        {reward.image && imgOk ? (
          <img src={reward.image} alt={reward.name} onError={() => setImgOk(false)} className="absolute inset-0 w-full h-full object-cover" />
        ) : (
          <div className="absolute inset-0 bg-[#0A0A0A] flex items-center justify-center p-2">
            <span className="font-heading font-extrabold uppercase tracking-wide2 text-white text-xs text-center truncate">{reward.name}</span>
          </div>
        )}
        {outOfStock && (
          <div className="absolute inset-0 bg-background/70 flex items-center justify-center">
            <span className="font-display font-extrabold uppercase tracking-mega text-destructive text-sm">{t('out_of_stock')}</span>
          </div>
        )}
      </div>
      <div className="p-2.5 flex flex-col gap-1.5 flex-1">
        <div className="font-heading font-bold uppercase tracking-wide2 text-white text-xs leading-tight line-clamp-2 min-h-[2rem]">{reward.name}</div>
        <div className="flex items-baseline gap-1">
          <span className="font-data text-data font-bold text-base">{cost}</span>
          <span className="text-[8px] uppercase tracking-wide2 text-muted-foreground">{t('r_points_short')}</span>
        </div>
        {!outOfStock && (
          <div className="text-[9px] uppercase tracking-wide2 text-muted-foreground">{reward.stock || 0} {t('stock_left_unit')}</div>
        )}
        <button
          onClick={onRedeem}
          disabled={outOfStock || insufficient}
          className={cn('mt-auto h-8 rounded-lg font-heading font-bold uppercase tracking-wide2 text-[10px] transition-transform',
            outOfStock ? 'bg-muted text-muted-foreground cursor-not-allowed' :
            insufficient ? 'bg-card border border-border text-muted-foreground cursor-not-allowed' :
            'bg-primary text-white red-glow-soft active:scale-[0.98]')}
        >
          {outOfStock ? t('out_of_stock') : insufficient ? `${t('need_more')} ${need}` : t('redeem')}
        </button>
      </div>
    </div>
  );
}