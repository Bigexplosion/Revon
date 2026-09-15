import React from 'react';
import { useT } from '@/lib/i18n';
import { cn } from '@/lib/utils';
import ClubBadge from '@/components/ClubBadge';

export default function ClubPodium({ top3, onSelect }) {
  const { t } = useT();
  if (!top3[0]) return null;
  const [first, second, third] = top3;

  const Card = ({ row, place, variant, cls }) => (
    <button
      onClick={() => onSelect(row.club)}
      className={cn(
        'absolute rounded-2xl bg-card p-3 flex flex-col items-center text-center active:scale-[0.97] transition-transform overflow-hidden',
        variant === 'first'
          ? 'border-2 border-primary red-glow animate-breath'
          : 'border border-primary/25 shadow-[0_0_10px_-8px_rgba(225,6,0,0.35)]',
        cls
      )}
    >
      <div className={cn('font-display font-extrabold leading-none mb-1.5', variant === 'first' ? 'text-primary animate-breath-text text-3xl' : 'text-white/45 text-xl')}>{place}</div>
      <ClubBadge club={row.club} size={variant === 'first' ? 48 : 36} accent="#E10600" glow={variant === 'first'} className={variant === 'first' ? 'red-glow' : ''} />
      <div className="font-heading font-bold uppercase tracking-wide2 text-white mt-1.5 leading-tight text-[11px] w-full break-words">{row.club.name}</div>
      <div className="text-[9px] uppercase tracking-wide2 text-muted-foreground mt-0.5">{row.club.member_count || 0} {t('members')}</div>
      <div className="font-data text-data font-bold text-base mt-0.5">{row.score.toLocaleString()}</div>
      {row.strongestTrackCode && (
        <span className="mt-1 px-1.5 py-0.5 rounded bg-primary/15 border border-primary/40 text-primary text-[8px] font-heading font-bold uppercase tracking-wide2">{row.strongestTrackCode} {t('dominant')}</span>
      )}
    </button>
  );

  return (
    <div className="relative h-[260px] px-4 mt-8">
      {second && <Card row={second} place={2} variant="side" cls="left-0 top-[64px] w-[33%] h-[196px] z-20" />}
      {third && <Card row={third} place={3} variant="side" cls="right-0 top-[64px] w-[33%] h-[196px] z-20" />}
      <Card row={first} place={1} variant="first" cls="left-1/2 -translate-x-1/2 top-0 w-[42%] h-[244px] z-30" />
    </div>
  );
}