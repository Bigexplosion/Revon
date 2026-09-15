import React, { useState, useEffect } from 'react';
import { Plus, Trash2, MapPin, X } from 'lucide-react';
import { useT } from '@/lib/i18n';
import { base44 } from '@/api/base44Client';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

export default function CustomRoutesOverlay({ open, onClose }) {
  const { t } = useT();
  const [routes, setRoutes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState('');
  const [points, setPoints] = useState([]);

  const load = async () => {
    setLoading(true);
    try {
      const data = await base44.entities.CustomRoute.list('-created_date', 50);
      setRoutes(data);
    } catch (e) {} finally { setLoading(false); }
  };

  useEffect(() => { if (open) load(); }, [open]);

  const addWaypoint = () => {
    if (!navigator.geolocation) { setPoints((p) => [...p, { lat: 24.15 + p.length * 0.001, lng: 120.68 + p.length * 0.001 }]); return; }
    navigator.geolocation.getCurrentPosition(
      (pos) => setPoints((p) => [...p, { lat: pos.coords.latitude, lng: pos.coords.longitude }]),
      () => setPoints((p) => [...p, { lat: 24.15 + p.length * 0.001, lng: 120.68 + p.length * 0.001 }])
    );
  };

  const save = async () => {
    if (!name.trim() || points.length < 2) return;
    await base44.entities.CustomRoute.create({ name: name.trim(), waypoints: points });
    setName(''); setPoints([]); setAdding(false);
    load();
  };

  const remove = async (id) => {
    await base44.entities.CustomRoute.delete(id);
    load();
  };

  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 bg-background/95 backdrop-blur flex flex-col">
      <div className="flex items-center justify-between p-4 border-b border-border">
        <h2 className="font-display font-bold uppercase tracking-mega text-white">{t('custom_routes')}</h2>
        <button onClick={onClose} className="text-muted-foreground hover:text-primary"><X className="w-5 h-5" /></button>
      </div>

      {!adding ? (
        <div className="flex-1 overflow-auto p-4 space-y-3">
          {loading && <p className="text-muted-foreground text-sm">…</p>}
          {!loading && routes.length === 0 && <p className="text-muted-foreground text-sm uppercase tracking-wide2">{t('no_results')}</p>}
          {routes.map((r) => (
            <div key={r.id} className="flex items-center gap-3 bg-card border border-border rounded-lg p-3">
              <div className="w-14 h-14 rounded bg-secondary flex items-center justify-center">
                <svg viewBox="0 0 40 40" className="w-10 h-10"><path d="M5 30 Q12 5 20 20 T35 10" fill="none" stroke="#39FF14" strokeWidth="2" /></svg>
              </div>
              <div className="flex-1">
                <div className="font-heading font-semibold text-white uppercase tracking-wide2 text-sm">{r.name}</div>
                <div className="text-data text-xs font-data">{r.waypoints?.length || 0} {t('waypoints')}</div>
              </div>
              <span className="text-[10px] px-2 py-0.5 rounded bg-primary/20 text-primary uppercase tracking-wide2">{t('custom')}</span>
              <button onClick={() => remove(r.id)} className="text-muted-foreground hover:text-destructive"><Trash2 className="w-4 h-4" /></button>
            </div>
          ))}
        </div>
      ) : (
        <div className="flex-1 overflow-auto p-4 space-y-4">
          <Input value={name} onChange={(e) => setName(e.target.value)} placeholder={t('route_name')} className="h-12" />
          <div className="space-y-2">
            {points.map((p, i) => (
              <div key={i} className="flex items-center gap-2 text-xs text-muted-foreground bg-card border border-border rounded p-2">
                <MapPin className="w-3 h-3 text-primary" /> {p.lat.toFixed(5)}, {p.lng.toFixed(5)}
              </div>
            ))}
          </div>
          <Button variant="outline" onClick={addWaypoint} className="w-full h-11 border-primary text-primary"><Plus className="w-4 h-4 mr-2" /> {t('waypoints')}</Button>
        </div>
      )}

      <div className="p-4 border-t border-border">
        {!adding ? (
          <Button onClick={() => setAdding(true)} className="w-full h-12 bg-primary red-glow font-heading uppercase tracking-wide2"><Plus className="w-4 h-4 mr-2" /> {t('add_custom')}</Button>
        ) : (
          <div className="flex gap-3">
            <Button variant="outline" onClick={() => setAdding(false)} className="flex-1 h-12">{t('cancel')}</Button>
            <Button onClick={save} disabled={!name.trim() || points.length < 2} className="flex-1 h-12 bg-primary red-glow font-heading uppercase tracking-wide2">{t('save_route')}</Button>
          </div>
        )}
      </div>
    </div>
  );
}