import React from 'react';
import LegalLayout from '@/components/LegalLayout';

export default function RefundPolicy() {
  return (
    <LegalLayout title="Return & Refund Policy">
      <h2 className="text-white font-display uppercase tracking-wide2">1. Subscriptions</h2>
      <p>Subscription fees are billed in advance. You may cancel renewal at any time from your Profile. Cancellation stops future charges but does not refund the current billing period, which remains active until expiry.</p>
      <h2 className="text-white font-display uppercase tracking-wide2">2. R Points & Promo Codes</h2>
      <p>R Points purchases are final and non-refundable except where required by law. Expired or misused promo codes cannot be redeemed.</p>
      <h2 className="text-white font-display uppercase tracking-wide2">3. Defective Deliverables</h2>
      <p>If a purchased subscription fails to activate due to a verified platform error, contact support with details for a review and possible credit.</p>
      <h2 className="text-white font-display uppercase tracking-wide2">4. Statutory Rights</h2>
      <p>Nothing in this policy affects any statutory consumer rights you may have under applicable local law.</p>
      <h2 className="text-white font-display uppercase tracking-wide2">5. How to Request</h2>
      <p>Contact support via Instagram DM with your account email and transaction details. Requests are reviewed within 7 business days.</p>
      <p className="text-xs text-muted-foreground pt-4">Last updated: 2026-08-07</p>
    </LegalLayout>
  );
}