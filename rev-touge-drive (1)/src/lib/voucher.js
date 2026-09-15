const CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

function block() {
  let s = '';
  for (let i = 0; i < 4; i++) s += CHARS[Math.floor(Math.random() * CHARS.length)];
  return s;
}

export function genVoucherCode() {
  return `RVN-${block()}-${block()}`;
}