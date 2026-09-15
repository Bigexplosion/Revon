import React, { useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Route as Road, Trophy, Users, User } from 'lucide-react';
import { useT } from '@/lib/i18n';
import TermsGate from '@/components/TermsGate';
import ActivityTicker from '@/components/ActivityTicker';
import { cn } from '@/lib/utils';

const tabs = [
  { to: '/', labelKey: 'routes', Icon: Road },
  { to: '/ranking', labelKey: 'ranking', Icon: Trophy },
  { to: '/club', labelKey: 'club', Icon: Users },
  { to: '/profile', labelKey: 'profile', Icon: User },
];

export default function Layout({ children }) {
  const { t } = useT();
  const location = useLocation();
  const isRace = location.pathname.startsWith('/race');

  if (isRace) {
    return (
      <TermsGate>
        <div className="min-h-[100dvh] w-full bg-black flex justify-center">
          <div className="relative w-full max-w-[430px] min-h-[100dvh] bg-background overflow-hidden">
            {children}
          </div>
        </div>
      </TermsGate>
    );
  }

  return (
    <TermsGate>
      <div className="min-h-[100dvh] w-full bg-black flex justify-center">
        <div className="relative w-full max-w-[430px] min-h-[100dvh] bg-background overflow-hidden">
          <ActivityTicker />
          <main className="min-h-[100dvh] pt-6 pb-[calc(5rem+env(safe-area-inset-bottom))]">{children}</main>
          <nav className="fixed bottom-0 left-1/2 -translate-x-1/2 w-full max-w-[430px] z-40 pb-[env(safe-area-inset-bottom)] border-t border-border bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80">
            <div className="grid grid-cols-4">
            {tabs.map(({ to, labelKey, Icon }) => {
              const active = to === '/' ? location.pathname === '/' : location.pathname.startsWith(to);
              return (
                <a
                  key={to}
                  href={to}
                  className={cn(
                    'flex flex-col items-center gap-1 py-3 transition-colors',
                    active ? 'text-primary' : 'text-muted-foreground'
                  )}
                >
                  <Icon className={cn('w-5 h-5', active && 'text-glow-red')} strokeWidth={active ? 2.5 : 2} />
                  <span className={cn('text-[10px] font-heading font-semibold uppercase tracking-wide2', active && 'text-glow-red')}>
                    {t(labelKey)}
                  </span>
                  {active && <span className="h-0.5 w-6 bg-primary rounded-full" />}
                </a>
              );
            })}
            </div>
          </nav>
        </div>
      </div>
    </TermsGate>
  );
}