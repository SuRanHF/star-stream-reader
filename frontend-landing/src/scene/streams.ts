import * as THREE from 'three';
import { CHAPTER_NAMES, FLASH, OTHER_TEXTS, READER_TEXTS } from '@/data/readers';
import { CHOICE_OPTIONS } from '@/data/firstChoice';

// —— 航线：所有故事从原点（第一章）出发，像旋臂一样盘旋着飞出去；从远处看，整片就是一条星河 ——
export const LUT_N = 512;
export const SEP = '　　　';
export const NODE_S = [0.28, 0.52, 0.76, 0.985];
export const UP = new THREE.Vector3(0, 1, 0);

export interface Row { pts: THREE.Vector3[]; ph: number; reader: number; rev?: boolean; text: string; size: number; speed: number }
export interface MeetPoint { a: number; b: number; s: number; name: string }

let seed = 20261104;
export const rand = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
export const rr = (a: number, b: number) => a + rand() * (b - a);
export const clamp01 = (x: number) => Math.min(1, Math.max(0, x));
export const smooth = (x: number) => { x = clamp01(x); return x * x * (3 - 2 * x); };
export const lerp = (a: number, b: number, f: number) => a + (b - a) * f;

function spiral(th0: number, R: number, turn: number, lift: number, wob: number, ph: number) {
  const pts: THREE.Vector3[] = [];
  for (let i = 0; i < LUT_N; i++) {
    const s = i / (LUT_N - 1);
    const r = R * Math.pow(s, 0.85);
    const th = th0 + turn * s + wob * 0.12 * Math.sin(s * 9 + ph);
    const y = lift * Math.sin(s * Math.PI * 0.9 + ph * 0.3) * Math.min(1, s * 3) + wob * 2.5 * Math.sin(s * 13 + ph * 1.7) * s;
    pts.push(new THREE.Vector3(Math.cos(th) * r, y, Math.sin(th) * r));
  }
  return pts;
}
// 同一位读者的几行文字并排飞：离开原点后慢慢分开一点
function lane(base: THREE.Vector3[], ox: number, oy: number) {
  return base.map((p, i) => {
    const a = base[Math.max(0, i - 1)], b = base[Math.min(LUT_N - 1, i + 1)];
    const tan = b.clone().sub(a).normalize(), side = tan.clone().cross(UP).normalize(), up = side.clone().cross(tan);
    const k = Math.min(1, (i / (LUT_N - 1)) * 8);
    return p.clone().addScaledVector(side, ox * k).addScaledVector(up, oy * k);
  });
}

// reader：0~3 前四位，4~15 快闪十二位，16~18 访客的三种选择（只显示选中的那一种），-1 背景
export function buildRows(mobile: boolean) {
  const rows: Row[] = [], readerRow: number[] = [], flashRow: number[] = [], mineRow: number[] = [], meets: MeetPoint[] = [];
  for (let k = 0; k < 4; k++) {
    // 面板在右边的读者（甲、丙）顺时针盘旋，文字流落在画面左半边；面板在左边的（乙、丁）反过来
    const base = spiral(k * Math.PI / 2 + 0.3, 92 + k * 5, (k % 2 ? 1 : -1) * 2.1, (k % 2 ? -1 : 1) * rr(10, 15), 1, rr(0, 9));
    const ph = rr(0, 9), rev = k % 2 === 1;
    readerRow.push(rows.length);
    rows.push({ pts: base, ph, reader: k, rev, text: READER_TEXTS[k][0].join(SEP), size: 1, speed: 0.006 });
    rows.push({ pts: lane(base, 0.5, 1.6), ph, reader: k, rev, text: READER_TEXTS[k][1].join(SEP), size: 0.6, speed: 0.0085 });
    rows.push({ pts: lane(base, -0.5, -1.5), ph, reader: k, rev, text: READER_TEXTS[k][2].join(SEP), size: 0.6, speed: 0.0048 });
  }
  // 快闪的十二位都顺时针盘旋，中段依次排在星河一圈上。相遇、同行的那段航线被拉弯了，镜头停在拐回来之后
  const fStopS = FLASH.map((f) => (f.follow ? 0.8 : f.meet ? 0.72 : 0.5));
  const fBase = FLASH.map((_, j) => {
    const phi = 0.8 + j * (Math.PI * 2 * 0.85 / FLASH.length);
    return spiral(phi + 2 * fStopS[j], j === FLASH.length - 1 ? 38 : rr(82, 110), -2, rr(-12, 12), rr(0.6, 1.2), rr(0, 9));
  });
  FLASH.forEach((f, j) => {
    const B = fBase[j];
    if (f.meet) {
      // 两个人在同一章相遇：B 的航线在 sb 附近被拉到 A 的 sa 那一点
      const A = fBase[f.meet.with], sa = 0.62, sb = 0.42, ia = Math.round(sa * (LUT_N - 1)), ib = Math.round(sb * (LUT_N - 1));
      const d = A[ia].clone().sub(B[ib]);
      B.forEach((p, i) => p.addScaledVector(d, Math.exp(-Math.pow((i / (LUT_N - 1) - sb) / 0.18, 2))));
      meets.push({ a: f.meet.with, b: j, s: sa, name: f.meet.at });
    }
    if (f.follow) {
      // 结伴同行：前半段贴着 A 走，过了岔路口再拐回自己的方向
      const A = fBase[f.follow.with];
      B.forEach((p, i) => {
        const u = i / (LUT_N - 1), w = smooth((u - 0.08) / 0.08) * (1 - smooth((u - 0.45) / 0.25));
        p.lerp(A[i].clone().addScaledVector(UP, 2.4), w);
      });
      meets.push({ a: f.follow.with, b: j, s: 0.5, name: f.follow.at });
    }
  });
  FLASH.forEach((f, j) => {
    const ph = rr(0, 9);
    flashRow.push(rows.length);
    rows.push({ pts: fBase[j], ph, reader: 4 + j, text: f.text.join(SEP), size: 0.85, speed: rr(0.005, 0.008) });
    rows.push({ pts: lane(fBase[j], 0.4, -1.4), ph, reader: 4 + j, text: [f.tag].concat(f.meta.split(' · ')).join(SEP), size: 0.55, speed: rr(0.004, 0.006) });
  });
  // 访客自己的文字流：三种选择走同一条航线，字比别人的大一点，走得也快一点
  const mineBase = spiral(1.75, 96, -2.1, 9, 0.8, 3.1);
  CHOICE_OPTIONS.forEach((o, k) => {
    mineRow.push(rows.length);
    rows.push({ pts: mineBase, ph: 2.2, reader: 16 + k, text: o.text.join(SEP), size: 1.05, speed: 0.012 });
    rows.push({ pts: lane(mineBase, 0.4, -1.7), ph: 2.2, reader: 16 + k, text: o.lane.join(SEP), size: 0.6, speed: 0.009 });
  });
  for (let i = 0, n = mobile ? 8 : 14; i < n; i++) {
    const phrases = OTHER_TEXTS.slice().sort(() => rand() - 0.5).slice(0, 9);
    rows.push({ pts: spiral(rr(0, Math.PI * 2), rr(45, 125), (rand() < 0.5 ? -1 : 1) * rr(1.4, 2.7), rr(-18, 18), rr(0.5, 1.6), rr(0, 9)), ph: rr(0, 9), reader: -1, text: phrases.join(SEP), size: rr(0.6, 0.9), speed: rr(0.004, 0.009) });
  }
  return { rows, readerRow, flashRow, mineRow, meets, fStopS };
}

// 航线上随时间缓缓摆动：着色器和 JS 用同一个公式
export const SWAY_GLSL = `vec3 sway(float s, float t, float ph){ return vec3(sin(s*7.+t*.35+ph), sin(s*5.3-t*.28+ph*1.3)*.8, cos(s*6.1+t*.31+ph*.7)) * 1.6 * smoothstep(0., .1, s); }`;
const tmpSway = new THREE.Vector3();
export function pathAt(row: Row, s: number, t: number, out: THREE.Vector3) {
  const c = clamp01(s), x = c * (LUT_N - 1), i0 = Math.floor(x), i1 = Math.min(LUT_N - 1, i0 + 1);
  out.copy(row.pts[i0]).lerp(row.pts[i1], x - i0);
  const e = smooth(c / 0.1) * 1.6;
  tmpSway.set(Math.sin(c * 7 + t * 0.35 + row.ph) * e, Math.sin(c * 5.3 - t * 0.28 + row.ph * 1.3) * 0.8 * e, Math.cos(c * 6.1 + t * 0.31 + row.ph * 0.7) * e);
  return out.add(tmpSway);
}

// 字形图集：先等宋体加载（最多 2.5 秒），再把用到的字画进一张贴图
export async function buildAtlas(rows: Row[], mobile: boolean) {
  const chars = [...new Set(rows.map((r) => r.text).join('') + CHAPTER_NAMES.flat().join(''))].filter((c) => c.trim());
  await Promise.race([document.fonts.load('400 50px "Noto Serif SC"', chars.join('')).catch(() => {}), new Promise((r) => setTimeout(r, 2500))]);
  // 电脑上字形画大一点，贴近文字流时不糊
  const CELL = mobile ? 64 : 96, COLS = 32, AROWS = Math.ceil(chars.length / COLS);
  const c = document.createElement('canvas'); c.width = CELL * COLS; c.height = CELL * AROWS;
  const g = c.getContext('2d')!;
  g.fillStyle = '#fff'; g.textAlign = 'center'; g.textBaseline = 'middle';
  g.font = `400 ${Math.round(CELL * 0.78)}px "Noto Serif SC","Songti SC","SimSun",serif`;
  chars.forEach((ch, i) => g.fillText(ch, (i % COLS + 0.5) * CELL, (Math.floor(i / COLS) + 0.53) * CELL));
  const texture = new THREE.CanvasTexture(c); texture.anisotropy = 4;
  return { texture, index: new Map(chars.map((ch, i) => [ch, i])), cols: COLS, rows: AROWS };
}
