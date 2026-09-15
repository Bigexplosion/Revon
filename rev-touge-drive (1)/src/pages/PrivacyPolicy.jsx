import React from 'react';
import LegalLayout from '@/components/LegalLayout';

export default function PrivacyPolicy() {
  return (
    <LegalLayout title="Privacy Policy">
      <h2 className="text-white font-display uppercase tracking-wide2">1. Information We Collect</h2>
      <p>REV-ON collects account information (email, nickname), GPS location data during active races, device identifiers, and usage analytics. GPS data is only recorded while a race session is active and is used to compute timing and rankings.</p>
      <h2 className="text-white font-display uppercase tracking-wide2">2. How We Use Your Data</h2>
      <p>Your data is used to provide timing, leaderboards, club features, and to improve the service. Race results (time, track, vehicle) are displayed publicly on leaderboards. Your real-time location is never shared with other users.</p>
      <h2 className="text-white font-display uppercase tracking-wide2">3. Data Retention</h2>
      <p>Race results are retained indefinitely to maintain leaderboard integrity. Raw GPS traces are discarded after timing computation. You may request deletion of your account data at any time.</p>
      <h2 className="text-white font-display uppercase tracking-wide2">4. Data Security</h2>
      <p>We use industry-standard encryption for data in transit and at rest. Access to personal data is restricted to authorized personnel only.</p>
      <h2 className="text-white font-display uppercase tracking-wide2">5. Your Rights</h2>
      <p>You have the right to access, correct, or delete your personal information. Contact support to exercise these rights.</p>
      <h2 className="text-white font-display uppercase tracking-wide2">6. Disclaimer</h2>
      <p>Mountain-road racing is dangerous. REV-ON is a timing and community tool only and does not encourage illegal or reckless driving. Always obey local traffic laws.</p>
      <p className="text-xs text-muted-foreground pt-4">Last updated: 2026-08-07</p>
    </LegalLayout>
  );
}