import React from 'react';
import { ChevronLeft } from 'lucide-react';
import { Link } from 'react-router-dom';

export default function LegalLayout({ title, children }) {
  return (
    <div className="min-h-screen bg-background grid-bg">
      <div className="sticky top-0 z-30 bg-background/90 backdrop-blur border-b border-border">
        <div className="mx-auto max-w-2xl px-4 h-14 flex items-center gap-3">
          <Link to="/profile" className="text-muted-foreground hover:text-primary"><ChevronLeft className="w-5 h-5" /></Link>
          <h1 className="font-display font-bold uppercase tracking-mega text-sm text-white">{title}</h1>
        </div>
      </div>
      <div className="mx-auto max-w-2xl px-4 py-8 prose prose-invert prose-sm max-w-none text-muted-foreground leading-relaxed space-y-4">
        {children}
      </div>
    </div>
  );
}