import { Crown, Award, Trophy, Zap, Medal, Moon, Flag } from 'lucide-react';

const MAP = {
  gold: Crown,
  silver: Award,
  bronze: Medal,
  t21_king: Crown,
  season_champ: Trophy,
  fastest_season: Zap,
  club_hero: Medal,
  night_runner: Moon,
  first_run: Flag,
};

export default function BadgeIcon({ id, className }) {
  const Icon = MAP[id] || Award;
  return <Icon className={className} />;
}