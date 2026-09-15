import React, { useState, useEffect } from 'react';
import { base44 } from '@/api/base44Client';
import { useT } from '@/lib/i18n';
import { Ticket } from 'lucide-react';
import RewardCard from './RewardCard';
import RedeemSheet from './RedeemSheet';
import MyVouchers from './MyVouchers';
import { genVoucherCode } from '@/lib/voucher';

export default function RewardsSection({ me, onPointsChanged }) {
  const { t } = useT();
  const [rewards, setRewards] = useState([]);
  const [vouchers, setVouchers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [sheetReward, setSheetReward] = useState(null);
  const [sheetResult, setSheetResult] = useState(null);
  const [showVouchers, setShowVouchers] = useState(false);
  const [busy, setBusy] = useState(false);

  const country = me?.region_country || 'Taiwan';
  const city = me?.region_city || 'Taichung';
  const balance = me?.r_points ?? 0;

  const load = () => {
    base44.entities.Reward.filter({ region_country: country, region_city: city, active: true }, '-created_date', 50)
      .then((r) => { setRewards(r); setLoading(false); })
      .catch(() => setLoading(false));
    if (me?.id) base44.entities.RewardVoucher.filter({ user_id: me.id }, '-created_date', 50).then(setVouchers).catch(() => {});
  };
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [country, city, me?.id]);

  const openConfirm = (r) => { setSheetReward(r); setSheetResult(null); };
  const closeSheet = () => { setSheetReward(null); setSheetResult(null); };

  const doRedeem = async () => {
    if (!sheetReward || busy) return;
    setBusy(true);
    try {
      const reward = sheetReward;
      const code = genVoucherCode();
      const newBalance = balance - (reward.points_cost || 0);
      await base44.auth.updateMe({ r_points: newBalance });
      await base44.entities.Reward.update(reward.id, { stock: Math.max(0, (reward.stock || 0) - 1) });
      await base44.entities.RewardVoucher.create({ user_id: me.id, reward_id: reward.id, reward_name: reward.name, reward_image: reward.image, code, status: 'unused' });
      await base44.entities.Transaction.create({ description: `Redeemed: ${reward.name}`, points_delta: -(reward.points_cost || 0) });
      setSheetResult({ code, newBalance });
      onPointsChanged();
      load();
    } catch (e) {}
    setBusy(false);
  };

  const unusedCount = vouchers.filter((v) => v.status === 'unused').length;

  return (
    <div className="px-4 py-5 border-b border-border">
      <h2 className="font-display font-bold uppercase tracking-mega text-primary text-sm mb-3">{t('rewards_catalog')}</h2>

      <button onClick={() => setShowVouchers(true)} className="w-full flex items-center gap-3 p-3 rounded-xl border border-border bg-card mb-3 active:scale-[0.99] transition-transform">
        <div className="w-9 h-9 rounded-lg bg-primary/15 border border-primary/40 flex items-center justify-center shrink-0"><Ticket className="w-4 h-4 text-primary" /></div>
        <div className="flex-1 text-left">
          <div className="font-heading font-bold uppercase tracking-wide2 text-sm text-white">{t('my_vouchers')}</div>
          <div className="text-muted-foreground text-[10px] uppercase tracking-wide2">{unusedCount} {t('voucher_unused').toLowerCase()}</div>
        </div>
        <span className="text-muted-foreground text-lg leading-none">›</span>
      </button>

      {loading ? (
        <div className="flex justify-center py-10"><div className="w-7 h-7 border-4 border-primary/30 border-t-primary rounded-full animate-spin" /></div>
      ) : rewards.length === 0 ? (
        <p className="text-center text-muted-foreground py-8 uppercase tracking-wide2 text-xs">{t('no_rewards')}</p>
      ) : (
        <div className="grid grid-cols-2 gap-3">
          {rewards.map((r) => <RewardCard key={r.id} reward={r} balance={balance} onRedeem={() => openConfirm(r)} />)}
        </div>
      )}

      <RedeemSheet open={!!sheetReward || !!sheetResult} reward={sheetReward} result={sheetResult} balance={balance} busy={busy} onConfirm={doRedeem} onClose={closeSheet} t={t} />
      <MyVouchers open={showVouchers} onOpenChange={setShowVouchers} vouchers={vouchers} t={t} />
    </div>
  );
}