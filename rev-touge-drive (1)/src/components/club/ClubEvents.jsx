import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Plus, X } from 'lucide-react';
import { base44 } from '@/api/base44Client';
import { useT } from '@/lib/i18n';
import EventCard from '@/components/events/EventCard';

export default function ClubEvents({ clubId, isMember, isAdmin, userNickname }) {
  const { t } = useT();
  const [events, setEvents] = useState([]);
  const [tracks, setTracks] = useState([]);
  const [sheetOpen, setSheetOpen] = useState(false);
  const [form, setForm] = useState({ name: '', track_code: '', start_at: '', end_at: '', reward_title: '', p1: '', p2: '', p3: '' });
  const [saving, setSaving] = useState(false);

  const load = () => {
    base44.entities.ClubEvent.filter({ club_id: clubId }, '-created_date', 50).then(setEvents).catch(() => {});
  };
  useEffect(() => {
    base44.entities.Track.list('-created_date', 50).then(setTracks).catch(() => {});
    load();
  }, [clubId]);

  useEffect(() => {
    if (sheetOpen) document.body.style.overflow = 'hidden'; else document.body.style.overflow = '';
    return () => { document.body.style.overflow = ''; };
  }, [sheetOpen]);

  const create = async () => {
    if (!form.name || !form.track_code || !form.start_at || !form.end_at) return;
    setSaving(true);
    try {
      const tr = tracks.find((x) => x.code === form.track_code);
      const placement_rewards = [
        { place: 1, points: Number(form.p1) || 0 },
        { place: 2, points: Number(form.p2) || 0 },
        { place: 3, points: Number(form.p3) || 0 },
      ];
      await base44.entities.ClubEvent.create({
        club_id: clubId,
        name: form.name,
        track_code: form.track_code,
        track_name: tr?.name || form.track_code,
        start_at: new Date(form.start_at).toISOString(),
        end_at: new Date(form.end_at).toISOString(),
        reward_title: form.reward_title || 'CLUB HERO',
        reward_points: Number(form.p1) || 0,
        placement_rewards,
        created_by_nickname: userNickname,
      });
      setForm({ name: '', track_code: '', start_at: '', end_at: '', reward_title: '', p1: '', p2: '', p3: '' });
      setSheetOpen(false);
      load();
    } catch (e) { alert('Failed'); }
    setSaving(false);
  };

  const normalize = (ev) => ({
    id: ev.id,
    name: ev.name,
    banner_image: null,
    track_names: [ev.track_name].filter(Boolean),
    track_codes: [ev.track_code],
    start_at: ev.start_at,
    end_at: ev.end_at,
    entry_cost: 0,
    reward_pool: (ev.placement_rewards || []).reduce((s, r) => s + (r.points || 0), 0) || ev.reward_points || 0,
    placement_rewards: ev.placement_rewards || [],
  });

  return (
    <div className="px-4 space-y-3">
      {isAdmin && (
        <button onClick={() => setSheetOpen(true)} className="w-full flex items-center justify-center gap-2 py-2.5 rounded-lg border border-primary text-primary text-xs font-heading font-bold uppercase tracking-wide2">
          <Plus className="w-4 h-4" /> {t('create_club_event')}
        </button>
      )}

      {events.length === 0 && <p className="text-center text-muted-foreground text-xs uppercase tracking-wide2 py-6">{t('no_events')}</p>}
      {events.map((ev) => (
        <EventCard key={ev.id} event={normalize(ev)} to={`/event/${ev.id}?type=club`} />
      ))}

      <AnimatePresence>
        {sheetOpen && (
          <>
            <motion.div className="fixed inset-0 bg-black/70 z-40" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={() => setSheetOpen(false)} />
            <motion.div className="fixed inset-x-0 bottom-0 z-50 mx-auto w-full max-w-[430px] bg-card border-t border-primary rounded-t-2xl p-4 pb-8 max-h-[85vh] overflow-y-auto no-scrollbar" initial={{ y: '100%' }} animate={{ y: 0 }} exit={{ y: '100%' }} transition={{ type: 'spring', damping: 28, stiffness: 280 }}>
              <div className="flex items-center justify-between mb-3">
                <h3 className="font-display font-extrabold uppercase tracking-mega text-white">{t('create_club_event')}</h3>
                <button onClick={() => setSheetOpen(false)} className="w-8 h-8 rounded-full bg-secondary border border-border flex items-center justify-center text-muted-foreground"><X className="w-4 h-4" /></button>
              </div>
              <div className="space-y-2.5">
                <Field label={t('ev_name')}><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className="w-full bg-background border border-border rounded px-2 py-1.5 text-sm text-white" /></Field>
                <Field label="Track"><select value={form.track_code} onChange={(e) => setForm({ ...form, track_code: e.target.value })} className="w-full bg-background border border-border rounded px-2 py-1.5 text-sm text-white"><option value="">—</option>{tracks.map((tr) => <option key={tr.id} value={tr.code}>{tr.code} · {tr.name}</option>)}</select></Field>
                <div className="grid grid-cols-2 gap-2">
                  <Field label={t('ev_start')}><input type="datetime-local" value={form.start_at} onChange={(e) => setForm({ ...form, start_at: e.target.value })} className="w-full bg-background border border-border rounded px-2 py-1.5 text-sm text-white" /></Field>
                  <Field label={t('ev_end')}><input type="datetime-local" value={form.end_at} onChange={(e) => setForm({ ...form, end_at: e.target.value })} className="w-full bg-background border border-border rounded px-2 py-1.5 text-sm text-white" /></Field>
                </div>
                <Field label={t('honor_title')}><input value={form.reward_title} onChange={(e) => setForm({ ...form, reward_title: e.target.value })} placeholder="CLUB HERO" className="w-full bg-background border border-border rounded px-2 py-1.5 text-sm text-white" /></Field>
                <div>
                  <div className="text-[9px] uppercase tracking-wide2 text-muted-foreground mb-1.5">{t('rewards_per_place')}</div>
                  <div className="grid grid-cols-3 gap-2">
                    <Field label={t('place_1')}><input type="number" value={form.p1} onChange={(e) => setForm({ ...form, p1: e.target.value })} placeholder="0" className="w-full bg-background border border-border rounded px-2 py-1.5 text-sm text-white" /></Field>
                    <Field label={t('place_2')}><input type="number" value={form.p2} onChange={(e) => setForm({ ...form, p2: e.target.value })} placeholder="0" className="w-full bg-background border border-border rounded px-2 py-1.5 text-sm text-white" /></Field>
                    <Field label={t('place_3')}><input type="number" value={form.p3} onChange={(e) => setForm({ ...form, p3: e.target.value })} placeholder="0" className="w-full bg-background border border-border rounded px-2 py-1.5 text-sm text-white" /></Field>
                  </div>
                </div>
                <button onClick={create} disabled={saving} className="w-full h-10 bg-primary red-glow font-heading uppercase tracking-wide2 text-white rounded mt-1">{t('create_club_event')}</button>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </div>
  );
}

function Field({ label, children }) {
  return (
    <div>
      <div className="text-[9px] uppercase tracking-wide2 text-muted-foreground mb-1">{label}</div>
      {children}
    </div>
  );
}