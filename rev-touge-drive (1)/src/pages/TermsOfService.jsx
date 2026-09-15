import React from 'react';
import LegalLayout from '@/components/LegalLayout';

export default function TermsOfService() {
  return (
    <LegalLayout title="Terms of Service">
      <h2 className="text-white font-display uppercase tracking-wide2">1. Acceptance of Terms</h2>
      <p>By creating a REV-ON account, you agree to these Terms of Service. If you do not agree, do not use the application.</p>
      <h2 className="text-white font-display uppercase tracking-wide2">2. Eligibility</h2>
      <p>You must be at least 18 years old and hold a valid driver's license to use race-timing features.</p>
      <h2 className="text-white font-display uppercase tracking-wide2">3. Safety Responsibility</h2>
      <p>You are solely responsible for your safety and the safety of others. REV-ON does not organize, sponsor, or endorse any race. Never use the app while driving in a manner that violates law or endangers others. Closed-course or legal venues only.</p>
      <h2 className="text-white font-display uppercase tracking-wide2">4. Fair Play</h2>
      <p>Cheating, GPS spoofing, or manipulating timing data results in permanent ban and removal of results.</p>
      <h2 className="text-white font-display uppercase tracking-wide2">5. Subscriptions & R Points</h2>
      <p>Subscriptions renew automatically until cancelled. R Points are virtual credits with no cash value and are non-transferable. Promotional codes are subject to expiry.</p>
      <h2 className="text-white font-display uppercase tracking-wide2">6. Clubs</h2>
      <p>Clubs are user-created communities. Club operators are responsible for their conduct. REV-ON may remove clubs that violate these terms.</p>
      <h2 className="text-white font-display uppercase tracking-wide2">7. Limitation of Liability</h2>
      <p>REV-ON is provided "as is" without warranties. We are not liable for any damages arising from use of the service.</p>
      <h2 className="text-white font-display uppercase tracking-wide2">8. Termination</h2>
      <p>We may suspend or terminate accounts that violate these terms at our discretion.</p>
      <p className="text-xs text-muted-foreground pt-4">Last updated: 2026-08-07</p>
    </LegalLayout>
  );
}