import React from 'react';
import { useT } from '@/lib/i18n';
import { TITLE_CATALOG } from '@/lib/honors';
import { cn } from '@/lib/utils';

export default function TitleSelector({ titles, selected, onSelect }) {
  const { t } = useT();
  const owned = new Set(titles || []);
  const opts = [{ id: '', label: t('no_title') }, ...TITLE_CATALOG.filter((x) => owned.has(x.id))];
  return (
    <div className="flex flex-wrap gap-1.5">
      {opts.map((o) => {
        const active = (o.id || '') === (selected || '');
        return (
          <button key={o.id || 'none'} onClick={() => onSelect(o.id)} className={cn('px-2.5 py-1.5 rounded-full text-[10px] font-heading font-bold uppercase tracking-wide2 border', active ? 'bg-primary border-primary text-white red-glow-soft' : 'border-border text-muted-foreground')}>{o.label}</button>
        );
      })}
    </div>
  );
}