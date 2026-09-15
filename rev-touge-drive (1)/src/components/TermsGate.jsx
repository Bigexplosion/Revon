import React, { useState, useEffect } from 'react';
import { ShieldCheck, ExternalLink } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useT } from '@/lib/i18n';
import { base44 } from '@/api/base44Client';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';

export default function TermsGate({ children }) {
  const { t } = useT();
  const [agreed, setAgreed] = useState(() => localStorage.getItem('revon_terms_agreed') === '1');
  const [checked, setChecked] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    base44.auth.me().then((u) => {
      if (u?.terms_agreed) {
        localStorage.setItem('revon_terms_agreed', '1');
        setAgreed(true);
      }
    }).catch(() => {});
  }, []);

  if (agreed) return children;

  const handleContinue = async () => {
    if (!checked) return;
    setSaving(true);
    try {
      await base44.auth.updateMe({ terms_agreed: true });
    } catch (e) { /* non-blocking */ }
    localStorage.setItem('revon_terms_agreed', '1');
    setAgreed(true);
    setSaving(false);
  };

  return (
    <div className="fixed inset-0 z-50 bg-background grid-bg flex items-center justify-center p-6">
      <div className="w-full max-w-md text-center">
        <div className="mx-auto mb-6 w-16 h-16 rounded-full bg-primary/15 flex items-center justify-center red-glow">
          <ShieldCheck className="w-8 h-8 text-primary" />
        </div>
        <h1 className="font-display text-3xl font-extrabold uppercase tracking-mega text-white mb-3">
          REV<span className="text-primary">-</span>ON
        </h1>
        <p className="text-muted-foreground uppercase tracking-wide2 text-xs mb-8">{t('important_notice')}</p>

        <label className="flex items-start gap-3 text-left bg-card border border-border rounded-lg p-4 mb-6 cursor-pointer">
          <Checkbox checked={checked} onCheckedChange={setChecked} className="mt-0.5 border-primary data-[state=checked]:bg-primary data-[state=checked]:text-white" />
          <span className="text-sm leading-relaxed">
            {t('agree_terms')}{' '}
            <Link to="/terms" className="text-primary underline">{t('terms_of_service')}</Link>{' '}
            &{' '}
            <Link to="/privacy" className="text-primary underline">{t('privacy_policy')}</Link>
          </span>
        </label>

        <Button
          onClick={handleContinue}
          disabled={!checked || saving}
          className="w-full h-12 bg-primary hover:bg-primary/90 text-white font-heading font-bold uppercase tracking-wide2 red-glow"
        >
          {t('continue')}
        </Button>
        {!checked && <p className="text-destructive text-xs mt-3 uppercase tracking-wide2">{t('must_agree')}</p>}
      </div>
    </div>
  );
}