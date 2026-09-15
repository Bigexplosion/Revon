import React, { useMemo } from 'react';
import { posAtProgress } from '@/lib/raceSim';

// Stylized dark map: route as a blue polyline, S / numbered checkpoints / E
// markers, and the live vehicle marker animating along the route.
// The viewBox is fitted to the full route bounds so the entire course
// (start, all checkpoints, finish) is visible at a glance.
export default function RaceMap({ path, arc, checkpoints, progress, phase }) {
  const polyline = useMemo(() => path.map((p) => `${p.x},${p.y}`).join(' '), [path]);
  const vehicle = useMemo(() => posAtProgress(path, arc, progress), [path, arc, progress]);
  const cpPts = useMemo(
    () => checkpoints.map((c) => ({ ...c, pos: posAtProgress(path, arc, c.fraction) })),
    [path, arc, checkpoints]
  );
  const start = path[0];
  const finish = path[path.length - 1];
  const showVehicle = phase !== 'approach';

  const viewBox = useMemo(() => {
    const xs = path.map((p) => p.x);
    const ys = path.map((p) => p.y);
    const minX = Math.min(...xs);
    const maxX = Math.max(...xs);
    const minY = Math.min(...ys);
    const maxY = Math.max(...ys);
    const pad = 40;
    return `${minX - pad} ${minY - pad} ${maxX - minX + pad * 2} ${maxY - minY + pad * 2}`;
  }, [path]);

  return (
    <div className="absolute inset-0 grid-bg bg-[#0a0f0c]">
      <svg className="absolute inset-0 w-full h-full" preserveAspectRatio="xMidYMid meet" viewBox={viewBox}>
        <polyline points={polyline} fill="none" stroke="#0c3a2a" strokeWidth="16" strokeLinecap="round" strokeLinejoin="round" />
        <polyline points={polyline} fill="none" stroke="#3b82f6" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" opacity="0.9" />

        {/* start */}
        <g>
          <circle cx={start.x} cy={start.y} r="15" fill="#E10600" />
          <text x={start.x} y={start.y + 5} textAnchor="middle" fontSize="15" fontWeight="800" fill="#fff" fontFamily="Saira Condensed, sans-serif">S</text>
        </g>

        {/* checkpoints */}
        {cpPts.map((c) => (
          <g key={c.index}>
            <circle cx={c.pos.x} cy={c.pos.y} r="13" fill={c.cleared ? '#39FF14' : '#f59e0b'} opacity={c.cleared ? 1 : 0.85} />
            <text x={c.pos.x} y={c.pos.y + 5} textAnchor="middle" fontSize="14" fontWeight="800" fill="#000" fontFamily="Saira Condensed, sans-serif">{c.index}</text>
          </g>
        ))}

        {/* finish */}
        <g>
          <circle cx={finish.x} cy={finish.y} r="15" fill="#39FF14" />
          <text x={finish.x} y={finish.y + 5} textAnchor="middle" fontSize="15" fontWeight="800" fill="#000" fontFamily="Saira Condensed, sans-serif">E</text>
        </g>

        {/* vehicle */}
        {showVehicle && (
          <g>
            <circle cx={vehicle.x} cy={vehicle.y} r="18" fill="#E10600" opacity="0.25" />
            <circle cx={vehicle.x} cy={vehicle.y} r="9" fill="#E10600" stroke="#fff" strokeWidth="2.5" />
          </g>
        )}
      </svg>
    </div>
  );
}