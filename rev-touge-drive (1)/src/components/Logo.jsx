import React from 'react';
import { cn } from '@/lib/utils';

export default function Logo({ className, size = 'text-2xl' }) {
  return (
    <span className={cn('font-display font-extrabold uppercase tracking-mega leading-none', size, className)}>
      <span className="text-white">REV</span>
      <span className="text-primary text-glow-red">-</span>
      <span className="text-white">ON</span>
    </span>
  );
}