import React, { useState, useEffect } from 'react';
import { base44 } from '@/api/base44Client';
import { useT } from '@/lib/i18n';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';

export default function AnnouncementBoard({ clubId, isCaptain, captainNickname }) {
  const { t } = useT();
  const [ann, setAnn] = useState(null);
  const [editing, setEditing] = useState(false);
  const [body, setBody] = useState('');
  const [saving, setSaving] = useState(false);

  const load = () => {
    base44.entities.ClubAnnouncement.filter({ club_id: clubId }, '-created_date', 1).then((d) => setAnn(d[0] || null)).catch(() => {});
  };
  useEffect(load, [clubId]);

  const save = async () => {
    if (!body.trim() || saving) return;
    setSaving(true);
    try {
      if (ann) {
        await base44.entities.ClubAnnouncement.update(ann.id, { body: body.trim(), captain_nickname: captainNickname });
      } else {
        await base44.entities.ClubAnnouncement.create({ club_id: clubId, body: body.trim(), captain_nickname: captainNickname });
      }
      setEditing(false);
      load();
    } catch (e) {}
    setSaving(false);
  };

  return (
    <div className="mx-4 mt-3 rounded-xl border border-primary/40 bg-primary/5 p-3">
      <div className="flex items-center gap-2 mb-1">
        <span className="text-[10px] uppercase tracking-mega text-primary font-heading font-bold">{t('announcement')}</span>
        {isCaptain && !editing && (
          <button onClick={() => { setBody(ann?.body || ''); setEditing(true); }} className="ml-auto text-[10px] uppercase tracking-wide2 text-primary/80 underline">{ann ? t('edit_announcement') : t('pin_announcement')}</button>
        )}
      </div>
      {editing ? (
        <div className="space-y-2">
          <Textarea value={body} onChange={(e) => setBody(e.target.value)} rows={2} className="bg-black/40 border-primary/40 text-white text-sm resize-none" />
          <div className="flex gap-2">
            <Button onClick={save} disabled={saving || !body.trim()} size="sm" className="h-8 bg-primary text-white hover:bg-primary/90">{t('save')}</Button>
            <Button onClick={() => setEditing(false)} variant="outline" size="sm" className="h-8">{t('cancel')}</Button>
          </div>
        </div>
      ) : ann ? (
        <p className="text-white text-sm leading-relaxed">{ann.body}</p>
      ) : (
        <p className="text-muted-foreground text-xs italic">{t('no_announcement')}</p>
      )}
    </div>
  );
}