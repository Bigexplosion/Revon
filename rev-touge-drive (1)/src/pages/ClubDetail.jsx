import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { MessageCircle, BarChart3, Route as RouteIcon, Trophy } from 'lucide-react';
import { base44 } from '@/api/base44Client';
import { useT } from '@/lib/i18n';
import { cn } from '@/lib/utils';
import { computeClubScore, levelFromPoints, levelAccent } from '@/lib/clubScoring';
import AnnouncementBoard from '@/components/club/AnnouncementBoard';
import ClubChat from '@/components/club/ClubChat';
import ClubLeaderboard from '@/components/club/ClubLeaderboard';
import ClubCustomRoutes from '@/components/club/ClubCustomRoutes';
import ClubEvents from '@/components/club/ClubEvents';
import ClubDetailHeader from '@/components/club/ClubDetailHeader';

export default function ClubDetail() {
  const { clubId } = useParams();
  const navigate = useNavigate();
  const { t } = useT();
  const [club, setClub] = useState(null);
  const [members, setMembers] = useState([]);
  const [results, setResults] = useState([]);
  const [me, setMe] = useState(null);
  const [tab, setTab] = useState('chat');
  const [seasonHistory, setSeasonHistory] = useState([]);

  const reloadMembers = () => base44.entities.ClubMember.filter({ club_id: clubId, status: 'joined' }, '-created_date', 100).then(setMembers).catch(() => {});

  useEffect(() => {
    base44.entities.Club.get(clubId).then(setClub).catch(() => {});
    base44.auth.me().then(setMe).catch(() => {});
    reloadMembers();
    base44.entities.RaceResult.filter({ club_id: clubId }, '-created_date', 500).then(setResults).catch(() => {});
    base44.entities.ClubSeasonHistory.filter({ club_id: clubId }, '-created_date', 20).then(setSeasonHistory).catch(() => {});
  }, [clubId]);

  if (!club) return <div className="h-[100dvh] flex items-center justify-center bg-black"><div className="w-8 h-8 border-4 border-primary/30 border-t-primary rounded-full animate-spin" /></div>;

  const myMember = members.find((m) => m.created_by_id === me?.id);
  const isMember = !!myMember;
  const effectiveRole = myMember?.role || 'member';
  const isAdmin = effectiveRole === 'admin';
  const userNickname = me?.nickname || me?.full_name || 'RACER';

  const { strongestTrackCode } = computeClubScore(results);
  const totalPoints = club.total_points || 0;
  const level = levelFromPoints(totalPoints);
  const accent = levelAccent(level);

  const tabs = [
    { id: 'chat', label: t('club_chat'), Icon: MessageCircle },
    { id: 'ranking', label: t('weekly_ranking'), Icon: BarChart3 },
    { id: 'routes', label: t('club_routes'), Icon: RouteIcon },
    { id: 'events', label: t('club_events'), Icon: Trophy },
  ];

  return (
    <div className="min-h-[100dvh] bg-background pb-20 flex flex-col">
      <ClubDetailHeader
        club={club}
        level={level}
        totalPoints={totalPoints}
        strongestTrackCode={strongestTrackCode}
        membersCount={club.member_count || members.length}
        accent={accent}
        isAdmin={isAdmin}
        onBack={() => navigate('/club')}
        onUpdated={() => base44.entities.Club.get(clubId).then(setClub).catch(() => {})}
      />

      {seasonHistory.length > 0 && (
        <div className="px-4 mt-2 shrink-0">
          <div className="flex gap-1.5 flex-wrap">
            {seasonHistory.map((h, i) => (
              <span key={i} className="inline-flex items-center gap-1.5 px-2 py-1 rounded bg-primary/15 border border-primary/40 text-primary text-[9px] font-heading font-bold uppercase tracking-wide2"><span className="font-data">{h.placement ? `#${h.placement}` : '·'}</span> {h.season_name} — {h.honor}</span>
            ))}
          </div>
        </div>
      )}

      <AnnouncementBoard clubId={clubId} isCaptain={isAdmin} captainNickname={club.captain_nickname || userNickname} />

      {/* segmented tab bar */}
      <div className="px-4 mt-3 shrink-0">
        <div className="flex bg-card border border-border rounded-lg p-0.5">
          {tabs.map(({ id, label, Icon }) => (
            <button key={id} onClick={() => setTab(id)} className={cn('flex-1 flex flex-col items-center gap-0.5 py-2 rounded-md transition-colors', tab === id ? 'bg-primary text-white' : 'text-muted-foreground')}>
              <Icon className="w-4 h-4" />
              <span className="text-[8px] font-heading font-bold uppercase tracking-wide2">{label}</span>
            </button>
          ))}
        </div>
      </div>

      <div className="flex-1 mt-2">
        {tab === 'chat' && <ClubChat clubId={clubId} userNickname={userNickname} isMember={isMember} />}
        {tab === 'ranking' && <ClubLeaderboard results={results} />}
        {tab === 'routes' && <ClubCustomRoutes clubId={clubId} />}
        {tab === 'events' && <ClubEvents clubId={clubId} isMember={isMember} isAdmin={isAdmin} userNickname={userNickname} />}
      </div>
    </div>
  );
}