import React, { useState, useEffect, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Crown, Trophy, Route as RouteIcon, Ticket, History, UserCog, KeyRound, MapPin, Globe, GraduationCap, FileText, ShieldQuestion, MessageCircle, LogOut, X, Instagram, ChevronRight, Camera } from 'lucide-react';
import { base44 } from '@/api/base44Client';
import { useT } from '@/lib/i18n';
import { formatTime } from '@/lib/format';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { cn } from '@/lib/utils';
import { titleLabel } from '@/lib/honors';
import TrophyCase from '@/components/profile/TrophyCase';
import TitleSelector from '@/components/profile/TitleSelector';
import RewardsSection from '@/components/profile/RewardsSection';
import RacerAvatar from '@/components/RacerAvatar';

const COUNTRIES = { Taiwan: ['Taichung', 'Changhua', 'Chiayi', 'Taipei', 'Kaohsiung', 'Nantou'], Japan: ['Tokyo', 'Osaka', 'Nagano', 'Gunma'] };

export default function Profile() {
  const { t, lang, setLang } = useT();
  const navigate = useNavigate();
  const [me, setMe] = useState(null);
  const [results, setResults] = useState([]);
  const [txns, setTxns] = useState([]);
  const [editOpen, setEditOpen] = useState(false);
  const [supportOpen, setSupportOpen] = useState(false);
  const [promo, setPromo] = useState('');
  const [sortMode, setSortMode] = useState('date');

  useEffect(() => {
    base44.auth.me().then(setMe).catch(() => {});
    base44.entities.RaceResult.list('-recorded_at', 1000).then(setResults).catch(() => {});
    base44.entities.Transaction.list('-created_date', 50).then(setTxns).catch(() => {});
  }, []);

  const refresh = () => base44.auth.me().then(setMe);
  const sorted = [...results].sort((a, b) => sortMode === 'date' ? new Date(b.recorded_at) - new Date(a.recorded_at) : (a.track_code || '').localeCompare(b.track_code || ''));

  const rPoints = me?.r_points ?? 0;
  const totalRuns = results.length;
  const myNick = me?.nickname || me?.full_name;
  const bestRank = React.useMemo(() => {
    if (!myNick) return '—';
    const official = results.filter((r) => !r.is_custom_route);
    const byTrack = {};
    official.forEach((r) => { (byTrack[r.track_code] = byTrack[r.track_code] || []).push(r); });
    let best = Infinity;
    Object.values(byTrack).forEach((arr) => {
      arr.sort((a, b) => a.finish_time_ms - b.finish_time_ms);
      const idx = arr.findIndex((r) => r.player_nickname === myNick);
      if (idx >= 0 && idx + 1 < best) best = idx + 1;
    });
    return best === Infinity ? '—' : `#${best}`;
  }, [results, myNick]);
  const favRoute = (() => {
    const counts = {};
    results.forEach((r) => { counts[r.track_code] = (counts[r.track_code] || 0) + 1; });
    return Object.entries(counts).sort((a, b) => b[1] - a[1])[0]?.[0] || '—';
  })();

  const redeem = async () => {
    if (!promo.trim()) return;
    try {
      const codes = await base44.entities.PromoCode.filter({ code: promo.trim().toUpperCase() });
      const code = codes[0];
      if (!code || code.active === false) { alert('Invalid code'); return; }
      if (code.expires_at && new Date(code.expires_at) < new Date()) { alert('Expired'); return; }
      await base44.entities.Transaction.create({ description: `Redeemed: ${code.code}`, points_delta: code.r_points_value });
      await base44.auth.updateMe({ r_points: rPoints + code.r_points_value });
      setPromo('');
      refresh();
      base44.entities.Transaction.list('-created_date', 50).then(setTxns);
    } catch (e) { alert('Failed'); }
  };

  const logout = () => base44.auth.logout('/login');

  return (
    <div className="min-h-screen bg-background">
      {/* top */}
      <div className="relative px-4 pt-10 pb-6 grid-bg border-b border-border">
        <div className="flex items-center gap-4">
          <RacerAvatar src={me?.avatar} nickname={myNick} size={64} className="border-2 border-primary red-glow-soft" />
          <div className="flex-1 min-w-0">
            <div className="text-[10px] uppercase tracking-mega text-primary">{t('welcome_back')}</div>
            <h1 className="font-display font-extrabold uppercase tracking-mega text-3xl text-white truncate">{me?.nickname || me?.full_name || 'RACER'}</h1>
            {me?.selected_title && (
              <div className="text-primary text-xs font-heading font-bold uppercase tracking-wide2 mt-1 text-glow-red">{titleLabel(me.selected_title)}</div>
            )}
          </div>
        </div>
        <div className="flex items-center gap-3 mt-4">
          <div className="flex items-center gap-2">
            <span className="text-[10px] uppercase tracking-wide2 text-muted-foreground">{t('r_points')}</span>
            <span className="font-data text-data text-2xl font-bold text-glow-green">{rPoints}</span>
          </div>
        </div>
        <div className="mt-3">
          {me?.subscription_status === 'subscribed' ? (
            <div className="inline-flex items-center gap-3 bg-primary/15 border border-primary rounded-lg px-3 py-2">
              <Crown className="w-4 h-4 text-primary" />
              <span className="text-primary text-[10px] uppercase tracking-wide2 font-heading font-bold">{t('subscribed')} · EXP {me.subscription_exp || '2029-01-31'}</span>
              <button className="text-destructive text-[10px] uppercase tracking-wide2 underline">{t('cancel_renewal')}</button>
            </div>
          ) : (
            <Button className="bg-primary red-glow font-heading uppercase tracking-wide2 h-9 text-xs">SUBSCRIBE ›</Button>
          )}
        </div>
      </div>

      <Section title={t('my_racing')}>
        <div className="grid grid-cols-3 gap-2">
          <Stat label={t('total_runs')} value={totalRuns} Icon={Trophy} />
          <Stat label={t('best_rank')} value={bestRank} Icon={Crown} />
          <Stat label={t('fav_route')} value={favRoute} Icon={RouteIcon} />
        </div>
        <div className="flex gap-2 mt-3">
          <select value={sortMode} onChange={(e) => setSortMode(e.target.value)} className="bg-card border border-border rounded px-2 py-1 text-xs text-white">
            <option value="date">By Date</option>
            <option value="track">By Track</option>
          </select>
        </div>
        <div className="mt-3 space-y-1 max-h-48 overflow-auto no-scrollbar">
          {sorted.length === 0 && <p className="text-muted-foreground text-xs uppercase tracking-wide2">{t('no_results')}</p>}
          {sorted.slice(0, 12).map((r) => (
            <div key={r.id} className="flex items-center justify-between text-sm bg-card border border-border rounded px-3 py-2">
              <div className="min-w-0">
                <div className="font-heading font-semibold text-white text-xs uppercase tracking-wide2 truncate">{r.track_code} · {r.vehicle_type}</div>
                <div className="text-muted-foreground text-[10px]">{new Date(r.recorded_at).toLocaleDateString()}</div>
              </div>
              <div className="font-data text-data font-bold">{formatTime(r.finish_time_ms)}</div>
            </div>
          ))}
        </div>
      </Section>

      <Section title={t('rewards')}>
        <div className="flex gap-2">
          <Input value={promo} onChange={(e) => setPromo(e.target.value)} placeholder={t('promo_code')} className="bg-card h-10 uppercase" />
          <Button onClick={redeem} className="bg-primary red-glow h-10 font-heading uppercase tracking-wide2">{t('redeem')}</Button>
        </div>
        <div className="mt-3 space-y-1 max-h-40 overflow-auto no-scrollbar">
          {txns.map((tx) => (
            <div key={tx.id} className="flex items-center justify-between text-xs bg-card border border-border rounded px-3 py-2">
              <div className="min-w-0">
                <div className="text-white truncate">{tx.description}</div>
                <div className="text-muted-foreground text-[10px]">{new Date(tx.created_date).toLocaleString()}</div>
              </div>
              <div className={cn('font-data font-bold', tx.points_delta >= 0 ? 'text-data' : 'text-destructive')}>{tx.points_delta >= 0 ? '+' : ''}{tx.points_delta}</div>
            </div>
          ))}
        </div>
      </Section>

      <RewardsSection me={me} onPointsChanged={refresh} />

      <Section title={t('trophy_case')}>
        <TrophyCase badges={me?.badges} />
        <div className="mt-4">
          <div className="text-[10px] uppercase tracking-wide2 text-muted-foreground mb-2">{t('select_title')}</div>
          <TitleSelector titles={me?.titles} selected={me?.selected_title} onSelect={async (id) => { await base44.auth.updateMe({ selected_title: id }); refresh(); }} />
        </div>
      </Section>

      <Section title={t('account')}>
        <Row Icon={UserCog} label={t('edit_profile')} onClick={() => setEditOpen(true)} />
        <Row Icon={KeyRound} label={t('change_password')} onClick={() => navigate('/forgot-password')} />
      </Section>

      <Section title={t('my_region')}>
        <RegionSelect me={me} onSaved={refresh} />
      </Section>

      <Section title={t('app_settings')}>
        <div className="flex items-center justify-between py-2">
          <div className="flex items-center gap-3"><Globe className="w-4 h-4 text-muted-foreground" /><span className="text-white text-sm">{t('language')}</span></div>
          <div className="flex gap-1 bg-card border border-border rounded p-0.5">
            {['en', 'zh'].map((l) => (
              <button key={l} onClick={() => setLang(l)} className={cn('px-3 py-1 rounded text-xs font-heading font-bold uppercase tracking-wide2', lang === l ? 'bg-primary text-white' : 'text-muted-foreground')}>{l === 'en' ? 'EN' : '中'}</button>
            ))}
          </div>
        </div>
        <div className="flex items-center justify-between py-2">
          <div className="flex items-center gap-3"><GraduationCap className="w-4 h-4 text-muted-foreground" /><span className="text-white text-sm">{t('gps_tutorial')}</span></div>
          <Switch defaultChecked={localStorage.getItem('revon_tutorial_off') !== '1'} onCheckedChange={(v) => localStorage.setItem('revon_tutorial_off', v ? '0' : '1')} />
        </div>
      </Section>

      <Section title={t('support_legal')}>
        <Row Icon={ShieldQuestion} label={t('privacy_policy')} to="/privacy" />
        <Row Icon={FileText} label={t('terms_of_service')} to="/terms" />
        <Row Icon={FileText} label={t('refund_policy')} to="/refund" />
        <Row Icon={MessageCircle} label={t('contact_support')} onClick={() => setSupportOpen(true)} />
      </Section>

      <div className="p-4 pb-28">
        <Button onClick={logout} variant="outline" className="w-full h-12 border-destructive text-destructive font-heading uppercase tracking-wide2 hover:bg-destructive/10">
          <LogOut className="w-4 h-4 mr-2" /> {t('log_out')}
        </Button>
      </div>

      <EditProfileDialog open={editOpen} onOpenChange={setEditOpen} me={me} onSaved={refresh} t={t} />
      <SupportDialog open={supportOpen} onOpenChange={setSupportOpen} t={t} />
    </div>
  );
}

function Section({ title, children }) {
  return (
    <div className="px-4 py-5 border-b border-border">
      <h2 className="font-display font-bold uppercase tracking-mega text-primary text-sm mb-3">{title}</h2>
      {children}
    </div>
  );
}
function Stat({ label, value, Icon }) {
  return (
    <div className="bg-card border border-border rounded-lg p-3 text-center">
      <Icon className="w-4 h-4 text-primary mx-auto mb-1" />
      <div className="font-data text-white text-lg font-bold">{value}</div>
      <div className="text-[9px] uppercase tracking-wide2 text-muted-foreground">{label}</div>
    </div>
  );
}
function Row({ Icon, label, to, onClick }) {
  const content = (
    <div className="flex items-center gap-3 py-3 border-b border-border last:border-0">
      <Icon className="w-4 h-4 text-muted-foreground" />
      <span className="text-white text-sm flex-1">{label}</span>
      <ChevronRight className="w-4 h-4 text-muted-foreground" />
    </div>
  );
  return to ? <Link to={to}>{content}</Link> : <button onClick={onClick} className="w-full text-left">{content}</button>;
}

function RegionSelect({ me, onSaved }) {
  const [country, setCountry] = useState(me?.region_country || 'Taiwan');
  const [city, setCity] = useState(me?.region_city || 'Taichung');
  useEffect(() => { if (me) { setCountry(me.region_country || 'Taiwan'); setCity(me.region_city || 'Taichung'); } }, [me]);
  const save = async () => { await base44.auth.updateMe({ region_country: country, region_city: city }); onSaved(); };
  return (
    <div className="space-y-3">
      <div>
        <Label className="text-[10px] uppercase tracking-wide2 text-muted-foreground">Country</Label>
        <Select value={country} onValueChange={(v) => { setCountry(v); setCity(COUNTRIES[v][0]); }}>
          <SelectTrigger className="bg-card h-10"><SelectValue /></SelectTrigger>
          <SelectContent>{Object.keys(COUNTRIES).map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}</SelectContent>
        </Select>
      </div>
      <div>
        <Label className="text-[10px] uppercase tracking-wide2 text-muted-foreground">City</Label>
        <Select value={city} onValueChange={setCity}>
          <SelectTrigger className="bg-card h-10"><SelectValue /></SelectTrigger>
          <SelectContent>{(COUNTRIES[country] || []).map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}</SelectContent>
        </Select>
      </div>
      <Button onClick={save} className="bg-primary red-glow h-10 font-heading uppercase tracking-wide2 w-full">Save</Button>
    </div>
  );
}

function EditProfileDialog({ open, onOpenChange, me, onSaved, t }) {
  const [nickname, setNickname] = useState('');
  const [avatar, setAvatar] = useState('');
  const [uploading, setUploading] = useState(false);
  const fileRef = useRef(null);
  useEffect(() => { if (me && open) { setNickname(me.nickname || ''); setAvatar(me.avatar || ''); } }, [me, open]);
  const onFile = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploading(true);
    try {
      const { file_url } = await base44.integrations.Core.UploadFile({ file });
      setAvatar(file_url);
    } catch (err) {}
    setUploading(false);
    e.target.value = '';
  };
  const save = async () => {
    await base44.auth.updateMe({ nickname, avatar });
    try {
      if (me?.id) {
        const byNick = await base44.entities.Player.filter({ nickname: me.nickname || nickname });
        const byUid = await base44.entities.Player.filter({ user_id: me.id });
        const target = byNick[0] || byUid[0];
        if (target) await base44.entities.Player.update(target.id, { nickname, avatar, user_id: me.id });
        else await base44.entities.Player.create({ nickname: nickname || me.nickname || 'RACER', user_id: me.id, avatar });
      }
    } catch (e) {}
    onSaved();
    onOpenChange(false);
  };
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="bg-card border-border">
        <DialogHeader><DialogTitle className="font-display uppercase tracking-mega">{t('edit_profile')}</DialogTitle></DialogHeader>
        <div className="space-y-4">
          <div className="flex flex-col items-center gap-2">
            <button onClick={() => fileRef.current?.click()} className="relative">
              <RacerAvatar src={avatar} nickname={nickname || me?.nickname || me?.full_name} size={80} className="border-2 border-primary red-glow-soft" />
              <span className="absolute bottom-0 right-0 w-7 h-7 rounded-full bg-primary border-2 border-card flex items-center justify-center text-white"><Camera className="w-3.5 h-3.5" /></span>
              {uploading && <span className="absolute inset-0 rounded-full bg-black/60 flex items-center justify-center text-[9px] text-white">…</span>}
            </button>
            <span className="text-[9px] uppercase tracking-wide2 text-muted-foreground">{t('avatar')}</span>
            <input ref={fileRef} type="file" accept="image/*" className="hidden" onChange={onFile} />
          </div>
          <div><Label>{t('nickname')}</Label><Input value={nickname} onChange={(e) => setNickname(e.target.value)} className="bg-background" /></div>
          <div><Label>{t('email')}</Label><Input value={me?.email || ''} disabled className="bg-background" /></div>
          <Button onClick={save} disabled={uploading} className="w-full bg-primary red-glow font-heading uppercase tracking-wide2">{t('save')}</Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function SupportDialog({ open, onOpenChange, t }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="bg-card border-border">
        <DialogHeader><DialogTitle className="font-display uppercase tracking-mega">{t('contact_support')}</DialogTitle></DialogHeader>
        <div className="flex items-center gap-3 mb-4">
          <div className="w-12 h-12 rounded-full bg-primary/15 flex items-center justify-center"><Instagram className="w-6 h-6 text-primary" /></div>
          <p className="text-muted-foreground text-sm">{t('support_text')}</p>
        </div>
        <a href="https://instagram.com" target="_blank" rel="noreferrer" className="w-full">
          <Button className="w-full bg-primary red-glow font-heading uppercase tracking-wide2">{t('go_ig')} ›</Button>
        </a>
        <Button variant="outline" onClick={() => onOpenChange(false)} className="w-full font-heading uppercase tracking-wide2">{t('close')}</Button>
      </DialogContent>
    </Dialog>
  );
}