import React, { useState, useEffect, useRef, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ChevronLeft, Square, Locate, Car, Bike, Gauge } from 'lucide-react';
import { base44 } from '@/api/base44Client';
import { useT } from '@/lib/i18n';
import { formatTime, formatHMS } from '@/lib/format';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import {
  genRoutePath, buildArc, posAtProgress, checkpointFractions,
  speedAtProgress, formatRaceTimer, millisPart, SIM_DURATION_MS,
} from '@/lib/raceSim';
import RaceMap from '@/components/race/RaceMap';
import CheckpointToast from '@/components/race/CheckpointToast';
import AbortConfirm from '@/components/race/AbortConfirm';
import ResultsScreen from '@/components/race/ResultsScreen';

const VEHICLES = [
  { id: 'CAR', Icon: Car },
  { id: 'MOTOR', Icon: Bike },
  { id: 'OTHER', Icon: Gauge },
];

const CHECKPOINT_COUNT = 3;

function hashCode(s) {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  return Math.abs(h);
}

export default function Race() {
  const { trackId } = useParams();
  const navigate = useNavigate();
  const { t } = useT();
  const urlParams = new URLSearchParams(window.location.search);
  const eventId = urlParams.get('event');
  const clubEventId = urlParams.get('clubEvent');

  const [track, setTrack] = useState(null);
  const [me, setMe] = useState(null);
  const [step, setStep] = useState(0); // 0 vehicle, 1 race, 2 results
  const [vehicle, setVehicle] = useState('CAR');

  // race phase + sim state
  const [phase, setPhase] = useState('approach'); // approach | ready | racing | finished
  const [elapsed, setElapsed] = useState(0);
  const [progress, setProgress] = useState(0);
  const [speed, setSpeed] = useState(0);
  const [gpsSignal, setGpsSignal] = useState('red');
  const [distToStart, setDistToStart] = useState(800);
  const [distToFinish, setDistToFinish] = useState(null);
  const [checkpoints, setCheckpoints] = useState([]);
  const [toast, setToast] = useState(null);
  const [warning, setWarning] = useState(null);
  const [simSpeed, setSimSpeed] = useState(1);
  const [showAbort, setShowAbort] = useState(false);
  const [panelIndex, setPanelIndex] = useState(0);
  const [resultsData, setResultsData] = useState(null);

  const simSpeedRef = useRef(1);
  const virtualRef = useRef(0);
  const lastFrameRef = useRef(0);
  const rafRef = useRef(0);
  const checkpointsRef = useRef([]);

  const path = useMemo(() => genRoutePath(hashCode(track?.code || 'AKINA')), [track]);
  const arc = useMemo(() => buildArc(path), [path]);
  const cpFractions = useMemo(() => checkpointFractions(CHECKPOINT_COUNT), []);

  useEffect(() => { simSpeedRef.current = simSpeed; }, [simSpeed]);

  useEffect(() => {
    base44.entities.Track.get(trackId).then(setTrack).catch(() => navigate('/'));
    base44.auth.me().then(setMe).catch(() => {});
  }, [trackId]);

  // enter race screen
  useEffect(() => {
    if (step !== 1) return;
    const cps = cpFractions.map((f, i) => ({ fraction: f, index: i + 1, cleared: false, splitMs: null }));
    checkpointsRef.current = cps;
    setCheckpoints(cps);
    setPhase('approach');
    setGpsSignal('red');
    setDistToStart(800);
    setElapsed(0);
    setProgress(0);
    startApproach();
    return () => { if (rafRef.current) cancelAnimationFrame(rafRef.current); };
    // eslint-disable-next-line
  }, [step]);

  // SIMULATION: approach animation — GPS red→amber→green, distance to start →0.
  const startApproach = () => {
    const start = performance.now();
    const dur = 2600;
    const loop = (now) => {
      const p = Math.min(1, (now - start) / dur);
      setGpsSignal(p < 0.3 ? 'red' : p < 0.65 ? 'amber' : 'green');
      setDistToStart(Math.round(800 * (1 - p)));
      if (p < 1) {
        rafRef.current = requestAnimationFrame(loop);
      } else {
        setPhase('ready');
      }
    };
    rafRef.current = requestAnimationFrame(loop);
  };

  const skipApproach = () => {
    if (rafRef.current) cancelAnimationFrame(rafRef.current);
    setGpsSignal('green');
    setDistToStart(0);
    setPhase('ready');
  };

  const beginTiming = () => {
    setPhase('racing');
    virtualRef.current = 0;
    lastFrameRef.current = performance.now();
    rafRef.current = requestAnimationFrame(raceLoop);
  };

  // SIMULATION: race loop — advances virtual time by real delta * simSpeed.
  const raceLoop = (now) => {
    const delta = now - lastFrameRef.current;
    lastFrameRef.current = now;
    const virt = virtualRef.current + delta * simSpeedRef.current;
    virtualRef.current = virt;
    setElapsed(virt);
    const p = Math.min(1, virt / SIM_DURATION_MS);
    setProgress(p);
    setSpeed(speedAtProgress(p));
    const totalM = (track?.distance_km || 3) * 1000;
    setDistToFinish(Math.max(0, Math.round((1 - p) * totalM)));

    // sequential checkpoint clearance
    const cps = checkpointsRef.current;
    let changed = false;
    for (const c of cps) {
      if (!c.cleared && p >= c.fraction) {
        c.cleared = true;
        c.splitMs = virt;
        changed = true;
        setToast({ index: c.index, ms: virt, key: Math.random() });
      }
    }
    if (changed) setCheckpoints([...cps]);

    if (p >= 1) {
      const allCleared = cps.every((c) => c.cleared);
      if (allCleared) {
        finishRace(virt);
        return;
      }
      const missed = cps.find((c) => !c.cleared);
      if (missed) setWarning({ index: missed.index, key: Math.random() });
    }
    rafRef.current = requestAnimationFrame(raceLoop);
  };

  const finishRace = (finalMs) => {
    if (rafRef.current) cancelAnimationFrame(rafRef.current);
    setPhase('finished');
    setElapsed(finalMs);
    saveResult(finalMs);
  };

  const saveResult = async (finalMs) => {
    const nickname = me?.nickname || me?.full_name || 'RACER';
    let prevBest = null;
    let prevRank = null;
    try {
      const prev = await base44.entities.RaceResult.filter({ track_code: track.code }, '-finish_time_ms', 200);
      const mine = prev.filter((r) => r.player_nickname === nickname);
      prevBest = mine[0]?.finish_time_ms ?? null;
      if (prevBest != null) {
        const sorted = [...prev].sort((a, b) => a.finish_time_ms - b.finish_time_ms);
        prevRank = sorted.findIndex((r) => r.player_nickname === nickname) + 1;
      }
    } catch (e) {}

    let myClub = null;
    try {
      if (me?.id) {
        const myMembers = await base44.entities.ClubMember.filter({ status: 'joined', created_by_id: me.id }, '-created_date', 5);
        if (myMembers[0]) myClub = await base44.entities.Club.get(myMembers[0].club_id).catch(() => null);
      }
    } catch (e) {}

    const splits = checkpointsRef.current.map((c) => c.splitMs || 0);
    try {
      await base44.entities.RaceResult.create({
        track_code: track.code,
        track_name: track.name,
        player_nickname: nickname,
        vehicle_type: vehicle,
        finish_time_ms: finalMs,
        finish_time_display: formatTime(finalMs),
        recorded_at: new Date().toISOString(),
        region: track.region,
        club_id: myClub?.id || null,
        club_name: myClub?.name || null,
        is_custom_route: false,
        event_id: eventId || null,
        club_event_id: clubEventId || null,
        splits,
      });
    } catch (e) {}

    let newRank = null;
    try {
      const all = await base44.entities.RaceResult.filter({ track_code: track.code }, 'finish_time_ms', 200);
      const sorted = [...all].sort((a, b) => a.finish_time_ms - b.finish_time_ms);
      newRank = sorted.findIndex((r) => r.player_nickname === nickname) + 1;
    } catch (e) {}

    // R Points are NOT credited for completing a race. They come only from event
    // placement rewards (once an event is settled), top-up/purchase, or promo codes.
    let eventInfo = null;
    if (eventId || clubEventId) {
      try {
        let ev = null;
        let boardResults = [];
        if (clubEventId) {
          ev = await base44.entities.ClubEvent.get(clubEventId).catch(() => null);
          boardResults = await base44.entities.RaceResult.filter({ club_event_id: clubEventId }, 'finish_time_ms', 500).catch(() => []);
        } else {
          ev = await base44.entities.Event.get(eventId).catch(() => null);
          boardResults = await base44.entities.RaceResult.filter({ event_id: eventId }, 'finish_time_ms', 500).catch(() => []);
        }
        const best = {};
        boardResults.forEach((r) => { if (!best[r.player_nickname] || r.finish_time_ms < best[r.player_nickname].finish_time_ms) best[r.player_nickname] = r; });
        const board = Object.values(best).sort((a, b) => a.finish_time_ms - b.finish_time_ms);
        const pos = board.findIndex((r) => r.player_nickname === nickname) + 1;
        const reward = pos > 0 ? (ev?.placement_rewards || []).find((pr) => pr.place === pos) : null;
        eventInfo = { name: ev?.name || '', position: pos > 0 ? pos : null, potentialReward: reward ? reward.points : 0, hasReward: !!reward };
      } catch (e) {}
    }

    if (myClub) {
      try {
        let body;
        if (clubEventId) {
          const ce = await base44.entities.ClubEvent.get(clubEventId).catch(() => null);
          body = `${nickname} ${t('race_report_just_ran')} ${track.code} · ${formatTime(finalMs)}${ce ? ` — ${ce.name}` : ''}`;
        } else {
          body = `${nickname} ${t('race_report_just_ran')} ${track.code} · ${formatTime(finalMs)}${newRank > 0 ? ` — #${newRank} ${t('race_report_in_club')}` : ''}`;
        }
        await base44.entities.ClubChatMessage.create({ club_id: myClub.id, author_nickname: nickname, body, message_type: 'race_report' });
      } catch (e) {}
    }

    const isFirstRun = prevBest == null;
    const deltaMs = isFirstRun ? null : finalMs - prevBest;
    setResultsData({
      finalMs, track, vehicle, prevBest, isFirstRun, deltaMs,
      splits: checkpointsRef.current.map((c) => ({ index: c.index, ms: c.splitMs })),
      newRank, prevRank, eventInfo,
    });
    setStep(2);
  };

  const abort = () => {
    if (rafRef.current) cancelAnimationFrame(rafRef.current);
    navigate('/');
  };

  const signalColor = { green: 'bg-data', amber: 'bg-yellow-400', red: 'bg-primary' }[gpsSignal];

  if (!track) {
    return <div className="h-screen flex items-center justify-center bg-black"><div className="w-8 h-8 border-4 border-primary/30 border-t-primary rounded-full animate-spin" /></div>;
  }

  // ---- STEP 0: vehicle ----
  if (step === 0) {
    return (
      <div className="relative min-h-screen bg-black">
        <button onClick={() => navigate(-1)} className="absolute top-4 left-4 z-50 w-9 h-9 rounded-full bg-black/60 border border-border flex items-center justify-center text-white"><ChevronLeft className="w-5 h-5" /></button>
        <div className="min-h-screen bg-black flex flex-col items-center justify-center px-6">
          <div className="font-display text-7xl font-extrabold text-white text-glow-red mb-2">{track.code}</div>
          <div className="text-muted-foreground uppercase tracking-mega text-xs mb-12">{t('choose_vehicle')}</div>
          <div className="w-full max-w-sm space-y-3">
            {VEHICLES.map((v) => (
              <button key={v.id} onClick={() => setVehicle(v.id)} className={cn('w-full flex items-center gap-4 p-4 rounded-lg border-2 transition-all', vehicle === v.id ? 'border-primary red-glow bg-primary/10' : 'border-border bg-card')}>
                <v.Icon className="w-7 h-7 text-white" />
                <span className={cn('font-display font-bold uppercase tracking-mega text-lg', vehicle === v.id ? 'text-primary' : 'text-white')}>{t(v.id.toLowerCase())}</span>
                {vehicle === v.id && <span className="ml-auto w-3 h-3 rounded-full bg-primary" />}
              </button>
            ))}
          </div>
          <Button onClick={() => setStep(1)} className="mt-10 w-full max-w-sm h-14 bg-primary red-glow font-heading font-bold uppercase tracking-mega text-base">{t('start_engine')} ›</Button>
        </div>
      </div>
    );
  }

  // ---- STEP 1: race screen ----
  if (step === 1) {
    const cps = checkpoints;
    const clearedCount = cps.filter((c) => c.cleared).length;
    const allCleared = cps.length > 0 && clearedCount === cps.length;
    return (
      <div className="relative min-h-screen bg-black flex flex-col">
        {/* top telemetry */}
        <div className="relative z-20 px-4 pt-4 pb-3 bg-gradient-to-b from-black/95 via-black/70 to-transparent">
          <div className="flex items-center justify-between">
            <button onClick={() => setShowAbort(true)} className="w-9 h-9 rounded-full bg-black/60 border border-border flex items-center justify-center text-white"><ChevronLeft className="w-5 h-5" /></button>
            <div className="flex items-center gap-1.5">
              <span className={cn('w-2.5 h-2.5 rounded-full', signalColor)} />
              <span className="text-[10px] uppercase tracking-wide2 text-white/70 font-heading">{t('gps_label')}</span>
              <span className="text-[10px] uppercase tracking-wide2 text-primary font-heading font-bold ml-2">{t('sim_mode')}</span>
            </div>
            <div className="flex rounded-full border border-border bg-black/60 overflow-hidden">
              {[1, 2, 4].map((m) => (
                <button key={m} onClick={() => setSimSpeed(m)} className={cn('px-2.5 py-1 text-[10px] font-data font-bold', simSpeed === m ? 'bg-primary text-white' : 'text-muted-foreground')}>{m}x</button>
              ))}
            </div>
          </div>

          <div className="mt-2 flex items-end justify-center gap-0.5">
            <span className={cn('font-data font-bold tabular-nums leading-none', phase === 'racing' ? 'text-data text-6xl text-glow-green' : 'text-muted-foreground text-5xl')}>{formatRaceTimer(elapsed)}</span>
            <span className={cn('font-data font-bold tabular-nums leading-none mb-0.5', phase === 'racing' ? 'text-data text-2xl' : 'text-muted-foreground text-xl')}>.{millisPart(elapsed)}</span>
          </div>

          <div className="mt-2 flex items-center justify-center gap-8 text-center">
            <div>
              <div className="text-[9px] uppercase tracking-wide2 text-muted-foreground">{t('speed')}</div>
              <div className="font-data text-white text-lg">{Math.round(speed)} <span className="text-[10px]">km/h</span></div>
            </div>
            <div>
              <div className="text-[9px] uppercase tracking-wide2 text-muted-foreground">{phase === 'approach' ? t('distance_start') : t('distance_finish')}</div>
              <div className="font-data text-white text-lg">{phase === 'approach' ? `${distToStart}m` : (distToFinish != null ? `${distToFinish}m` : '—')}</div>
            </div>
          </div>
        </div>

        {/* map */}
        <div className="relative flex-1 min-h-0">
          <RaceMap path={path} arc={arc} checkpoints={cps} progress={progress} phase={phase} />

          {phase === 'racing' && (
            <div className="absolute top-3 left-1/2 -translate-x-1/2 bg-black/70 border border-border rounded-full px-3 py-1 text-[10px] uppercase tracking-wide2 font-heading font-bold text-white whitespace-nowrap">
              {t('checkpoint')} {Math.min(clearedCount + 1, cps.length)} {t('of')} {cps.length}
            </div>
          )}

          <CheckpointToast toast={toast} t={t} />

          {warning && (
            <div key={warning.key} className="absolute top-16 inset-x-4 bg-primary/20 border border-primary rounded-lg px-3 py-2 text-center animate-breath-text">
              <span className="font-heading font-bold uppercase tracking-wide2 text-primary text-xs">{t('missed_cp')} {warning.index} — {t('run_invalid')}</span>
            </div>
          )}

          {phase === 'ready' && (
            <div className="absolute inset-0 z-10 flex items-center justify-center pointer-events-none px-6">
              <div className="bg-black/80 border border-data rounded-xl px-5 py-4 text-center max-w-[85%] green-glow">
                <div className="font-display font-bold uppercase tracking-mega text-data text-sm text-glow-green animate-breath-text">{t('ready_prompt')}</div>
              </div>
            </div>
          )}

          {phase === 'racing' && allCleared && progress > 0.85 && progress < 1 && (
            <div className="absolute bottom-20 left-1/2 -translate-x-1/2 bg-data/15 border border-data rounded-full px-4 py-2 text-[11px] uppercase tracking-wide2 font-heading font-bold text-data green-glow whitespace-nowrap">{t('final_stretch')}</div>
          )}

          {phase === 'finished' && (
            <div className="absolute inset-0 z-30 flex items-center justify-center bg-black/60">
              <div className="font-display font-extrabold uppercase tracking-mega text-data text-3xl text-glow-green animate-breath-text">FINISH</div>
            </div>
          )}

          <button onClick={() => {}} className="absolute bottom-4 right-4 w-12 h-12 rounded-full bg-black/70 border border-border flex items-center justify-center text-white"><Locate className="w-5 h-5" /></button>
        </div>

        {/* bottom panels + controls */}
        <div className="relative z-20 px-4 pt-2 pb-[calc(1rem+env(safe-area-inset-bottom))] bg-black">
          <div
            className="overflow-x-auto no-scrollbar"
            style={{ scrollSnapType: 'x mandatory' }}
            onScroll={(e) => setPanelIndex(Math.round(e.target.scrollLeft / e.target.clientWidth))}
          >
            <div className="flex" style={{ width: '300%' }}>
              <div className="snap-card px-1" style={{ width: '33.333%', scrollSnapAlign: 'start' }}>
                <div className="text-[9px] uppercase tracking-mega text-muted-foreground mb-1">{t('telemetry')}</div>
                <div className="grid grid-cols-3 gap-2 text-center">
                  <div className="bg-card border border-border rounded-lg p-2"><div className="text-[8px] uppercase tracking-wide2 text-muted-foreground">{t('speed')}</div><div className="font-data text-white text-sm">{Math.round(speed)}</div></div>
                  <div className="bg-card border border-border rounded-lg p-2"><div className="text-[8px] uppercase tracking-wide2 text-muted-foreground">{t('gps_label')}</div><div className="flex justify-center mt-1"><span className={cn('w-2.5 h-2.5 rounded-full', signalColor)} /></div></div>
                  <div className="bg-card border border-border rounded-lg p-2"><div className="text-[8px] uppercase tracking-wide2 text-muted-foreground">{phase === 'approach' ? t('distance_start') : t('distance_finish')}</div><div className="font-data text-white text-sm">{phase === 'approach' ? distToStart : (distToFinish ?? 0)}</div></div>
                </div>
              </div>
              <div className="snap-card px-1" style={{ width: '33.333%', scrollSnapAlign: 'start' }}>
                <div className="text-[9px] uppercase tracking-mega text-muted-foreground mb-1">{t('splits')}</div>
                <div className="space-y-1">
                  {cps.map((c) => (
                    <div key={c.index} className="flex items-center justify-between bg-card border border-border rounded-lg px-2 py-1.5">
                      <span className="text-[10px] uppercase tracking-wide2 font-heading font-bold text-white">{t('checkpoint')} {c.index}</span>
                      <span className={cn('font-data text-sm', c.cleared ? 'text-data' : 'text-muted-foreground')}>{c.cleared ? formatTime(c.splitMs) : '—'}</span>
                    </div>
                  ))}
                </div>
              </div>
              <div className="snap-card px-1" style={{ width: '33.333%', scrollSnapAlign: 'start' }}>
                <div className="text-[9px] uppercase tracking-mega text-muted-foreground mb-1">{t('track_info')}</div>
                <div className="space-y-1.5 text-[10px]">
                  <div className="flex justify-between"><span className="text-muted-foreground uppercase tracking-wide2">{t('region')}</span><span className="text-white font-heading">{track.region}</span></div>
                  <div className="flex justify-between"><span className="text-muted-foreground uppercase tracking-wide2">DIST</span><span className="text-white font-data">{track.distance_km}km</span></div>
                  <div className="flex justify-between"><span className="text-muted-foreground uppercase tracking-wide2">CORNERS</span><span className="text-white font-data">{track.corners_count}</span></div>
                  <div className="flex justify-between">
                    <span className="text-muted-foreground uppercase tracking-wide2">DIFF</span>
                    {(() => {
                      const d = (track.difficulty || 'NORMAL').toUpperCase();
                      const normalized = d === 'ROOKIE' ? 'NORMAL' : (d === 'PRO' || d === 'EXPERT') ? 'HARD' : d === 'LEGEND' ? 'EXTREME' : d;
                      const colorClass = normalized === 'EXTREME' ? 'text-red-500 font-bold' : normalized === 'HARD' ? 'text-yellow-400 font-bold' : 'text-emerald-400 font-bold';
                      return <span className={`font-heading ${colorClass}`}>{normalized}</span>;
                    })()}
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="flex justify-center gap-1.5 mt-2">
            {[0, 1, 2].map((i) => (
              <span key={i} className={cn('h-1.5 rounded-full transition-all', panelIndex === i ? 'w-5 bg-primary' : 'w-1.5 bg-border')} />
            ))}
          </div>

          <div className="mt-3">
            {phase === 'approach' && (
              <Button onClick={skipApproach} className="w-full h-12 bg-primary red-glow font-heading font-bold uppercase tracking-mega">{t('navigate_start')} ›</Button>
            )}
            {phase === 'ready' && (
              <Button onClick={beginTiming} className="w-full h-12 bg-data text-black green-glow font-heading font-bold uppercase tracking-mega animate-breath">{t('begin_timing')}</Button>
            )}
            {phase === 'racing' && (
              <Button onClick={() => setShowAbort(true)} className="w-full h-12 bg-destructive text-white font-heading font-bold uppercase tracking-mega red-glow"><Square className="w-4 h-4 mr-2" />{t('stop_race')}</Button>
            )}
          </div>
        </div>

        <AbortConfirm open={showAbort} onConfirm={abort} onCancel={() => setShowAbort(false)} t={t} />
      </div>
    );
  }

  // ---- STEP 2: results ----
  if (step === 2 && resultsData) {
    return (
      <ResultsScreen
        data={resultsData}
        onAgain={() => { setResultsData(null); setStep(0); setPhase('approach'); }}
        onLeaderboard={() => navigate('/ranking')}
        onRoutes={() => navigate('/')}
        t={t}
      />
    );
  }

  return null;
}