import React from 'react';
import { Image } from '@/components/ui/image';
import { cn } from '@/lib/utils';

/**
 * Club badge: uploaded circular image, or initials on an accent-colored circle.
 */
export default function ClubBadge({ club, size = 40, accent = '#E10600', className, glow = true }) {
  const initials = club?.badge_letters || club?.name?.slice(0, 2).toUpperCase() || 'CL';
  if (club?.badge_image) {
    return (
      <Image
        src={club.badge_image}
        fittingType="fill"
        alt={club.name || ''}
        className={cn('rounded-full overflow-hidden shrink-0', className)}
        style={{ width: size, height: size }}
      />
    );
  }
  return (
    <div
      className={cn('rounded-full flex items-center justify-center font-display font-extrabold text-white shrink-0', className)}
      style={{ width: size, height: size, fontSize: Math.round(size * 0.3), background: accent, boxShadow: glow ? `0 0 14px -3px ${accent}` : undefined }}
    >
      {initials}
    </div>
  );
}