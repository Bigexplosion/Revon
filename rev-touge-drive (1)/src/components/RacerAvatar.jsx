import React from 'react';
import { Image } from '@/components/ui/image';
import { cn } from '@/lib/utils';

/**
 * Circular racer avatar. Renders the uploaded image (server-side center-cropped
 * to a circle via the Image component) or falls back to initials on a dark circle.
 */
export default function RacerAvatar({ src, nickname, size = 36, className }) {
  const initials = (nickname || 'R').slice(0, 2).toUpperCase();
  if (src) {
    return (
      <Image
        src={src}
        fittingType="fill"
        alt={nickname || ''}
        className={cn('rounded-full overflow-hidden shrink-0', className)}
        style={{ width: size, height: size }}
      />
    );
  }
  return (
    <div
      className={cn('rounded-full bg-secondary border border-border flex items-center justify-center font-display font-extrabold text-white shrink-0', className)}
      style={{ width: size, height: size, fontSize: Math.round(size * 0.34) }}
    >
      {initials}
    </div>
  );
}