import React, { useState, useEffect, useRef } from 'react';
import { base44 } from '@/api/base44Client';
import { useT } from '@/lib/i18n';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Send } from 'lucide-react';
import { cn } from '@/lib/utils';
import RacerAvatar from '@/components/RacerAvatar';
import { buildHonorsMap, titleLabel } from '@/lib/honors';

export default function ClubChat({ clubId, userNickname, isMember }) {
  const { t } = useT();
  const [messages, setMessages] = useState([]);
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const [honors, setHonors] = useState({});
  const scrollRef = useRef(null);

  useEffect(() => {
    let alive = true;
    base44.entities.ClubChatMessage.filter({ club_id: clubId }, 'created_date', 200).then((m) => { if (alive) setMessages(m); }).catch(() => {});
    base44.entities.Player.list('-created_date', 200).then((p) => { if (alive) setHonors(buildHonorsMap(p)); }).catch(() => {});
    const unsub = base44.entities.ClubChatMessage.subscribe((event) => {
      if (!event.data || event.data.club_id !== clubId) return;
      setMessages((prev) => {
        if (prev.some((m) => m.id === event.data.id)) return prev;
        const next = [...prev, event.data];
        return next.sort((a, b) => new Date(a.created_date) - new Date(b.created_date));
      });
    });
    return () => { alive = false; unsub(); };
  }, [clubId]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages]);

  const send = async () => {
    if (!text.trim() || sending) return;
    setSending(true);
    const body = text.trim();
    setText('');
    try {
      await base44.entities.ClubChatMessage.create({ club_id: clubId, author_nickname: userNickname, body, message_type: 'text' });
    } catch (e) {}
    setSending(false);
  };

  return (
    <div className="flex flex-col">
      <div ref={scrollRef} className="overflow-y-auto no-scrollbar px-4 py-3 space-y-2" style={{ maxHeight: 'calc(100dvh - 320px)' }}>
        {messages.length === 0 && <p className="text-center text-muted-foreground text-xs uppercase tracking-wide2 py-8">{t('no_messages')}</p>}
        {messages.map((m) => {
          const mine = m.author_nickname === userNickname;
          if (m.message_type === 'race_report') {
            return (
              <div key={m.id} className="flex justify-center">
                <div className="bg-primary/10 border border-primary/40 rounded-lg px-3 py-2 text-center max-w-[90%]">
                  <span className="text-[11px] font-heading font-semibold uppercase tracking-wide2 text-primary">{m.body}</span>
                </div>
              </div>
            );
          }
          return (
            <div key={m.id} className={cn('flex items-end gap-2', mine ? 'justify-end' : 'justify-start')}>
              {!mine && <RacerAvatar src={honors[m.author_nickname]?.avatar} nickname={m.author_nickname} size={28} />}
              <div className={cn('max-w-[75%] rounded-xl px-3 py-2', mine ? 'bg-primary text-white' : 'bg-card border border-border text-white')}>
                {!mine && (
                  <div className="flex items-center gap-1 mb-0.5">
                    <div className="text-[9px] uppercase tracking-wide2 text-muted-foreground font-heading font-bold">{m.author_nickname}</div>
                    {honors[m.author_nickname]?.selected_title && <span className="text-[8px] uppercase tracking-wide2 text-primary">{titleLabel(honors[m.author_nickname].selected_title)}</span>}
                  </div>
                )}
                <div className="text-sm break-words">{m.body}</div>
              </div>
            </div>
          );
        })}
      </div>
      {isMember ? (
        <div className="fixed bottom-16 left-1/2 -translate-x-1/2 w-full max-w-[430px] px-4 z-30" style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}>
          <div className="flex gap-2 bg-card border border-border rounded-xl p-2">
            <Input value={text} onChange={(e) => setText(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') send(); }} placeholder={t('type_message')} className="flex-1 bg-transparent border-none h-9 focus-visible:ring-0" />
            <Button onClick={send} disabled={sending || !text.trim()} size="icon" className="h-9 w-9 bg-primary shrink-0"><Send className="w-4 h-4" /></Button>
          </div>
        </div>
      ) : (
        <p className="text-center text-muted-foreground text-xs uppercase tracking-wide2 px-4 py-6">{t('members_only')}</p>
      )}
    </div>
  );
}