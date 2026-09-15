import React from 'react';
import { Lock } from 'lucide-react';
import { BADGE_CATALOG } from '@/lib/honors';
import BadgeIcon from '@/components/BadgeIcon';
import { cn } from '@/lib/utils';

export default function TrophyCase({ badges }) {
  const earned = new Set(badges || []);
  return (
    <div className="grid grid-cols-4 gap-2">
      {BADGE_CATALOG.map((b) => {
        const has = earned.has(b.id);
        return (
          <div key={b.id} className={cn('aspect-square rounded-xl border flex flex-col items-center justify-center p-1 text-center', has ? 'border-primary bg-primary/15 red-glow-soft' : 'border-border/60 bg-secondary/20')}>
            <div className="flex items-center justify-center h-6">
              {has ? <BadgeIcon id={b.id} className="w-6 h-6 text-primary" /> : <Lock className="w-5 h-5 text-muted-foreground/30" />}
            </div>
            <div className={cn('text-[7px] uppercase tracking-wide2 font-heading font-bold mt-1', has ? 'text-primary' : 'text-muted-foreground/40')}>{b.label}</div>
          </div>
        );
      })}
    </div>
  );
}