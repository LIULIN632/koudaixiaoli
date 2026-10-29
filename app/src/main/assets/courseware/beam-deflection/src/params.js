/**
 * ============================================================================
 * src/params.js —— 简支梁集中荷载：物理模型（唯一数据源）
 * ============================================================================
 *
 * 模型：跨度 L 的简支梁，距左端 a 处作用竖向集中荷载 P。
 *   ↑RA                    ↓P                     ↑RB
 *   ●──────────────────────┴───────────────────────●
 *   A                                              B
 *   |←──── a ────→|←──── b = L − a ────→|
 *
 * 平衡条件（静定梁，两个方程解两个未知力）：
 *   ΣM_A = 0 → RB·L = P·a      → RB = P·a/L
 *   ΣF_y = 0 → RA + RB = P     → RA = P·b/L
 *
 * 弯矩（取左段为隔离体，正弯矩画在受拉侧）：
 *   M(x) = RA·x          (0 ≤ x ≤ a)
 *   M(x) = RB·(L − x)    (a ≤ x ≤ L)
 *   峰值       Mmax = P·a·b / L       （恰在集中荷载作用点 x = a 处）
 *
 * 挠度（等截面直梁，EI 为常数；向下为正）：
 *   x ≤ a：v(x) = P·b·x·(L² − b² − x²) / (6·L·EI)
 *   x ≥ a：v(x) = P·a·(L − x)·(L² − a² − (L − x)²) / (6·L·EI)
 *   荷载作用点处：v(a) = P·a²·b² / (3·L·EI)
 *
 * 注意一个容易讲错的地方：**最大挠度并不一定发生在荷载作用点**。
 * 只有 a = L/2（跨中）时两者重合；a ≠ L/2 时最大挠度偏向跨中一侧。
 * 所以这里用数值搜索求真正的 v_max 与它的位置 xVmax（采样 + 黄金分割细化），
 * 这样「拖动荷载位置，最大弯曲点沿梁长移动」在画面上才是真的。
 *
 * 两条性质是课件要讲的重点，也是自检脚本断言的依据：
 *   1. 同一条挠度曲线的形状只由 a 决定，v 与 P 严格成正比；
 *   2. 两端 v(0) = v(L) = 0，两端 M(0) = M(L) = 0（简支端不能传弯矩）。
 */

/** 结构常数：改这里就等于改课件里的"模型设定" */
export const MODEL = {
  L: 8, // 跨度（m）—— 课本里固定跨度，只让荷载动
  EI: 20000, // 抗弯刚度（kN·m²），常数，说明只研究几何与荷载的影响
  amp: 10, // 变形放大系数：真实挠度是毫米级，放大 10 倍才看得出来
  momentScale: 0.0048, // 弯矩图纵坐标比例：kN·m × 这个数 = 世界单位（200 kN·m → 0.96）
  segments: 96, // 梁的离散段数（也是弯矩图采样点数）
};

/** 面板滑块定义：ui.js 按这个数组自动生成控件 */
export const PARAM_DEFS = [
  {
    key: 'P',
    label: '荷载大小 P',
    min: 10,
    max: 100,
    step: 5,
    value: 50,
    unit: ' kN',
    decimals: 0,
    hint: '竖向集中荷载，作用于梁上 a 处',
  },
  {
    key: 'aRel',
    label: '荷载位置 a/L',
    min: 0.1,
    max: 0.9,
    step: 0.05,
    value: 0.5,
    unit: '',
    decimals: 2,
    hint: '集中荷载距左端 A 的相对位置（0.5 = 跨中）',
  },
];

/** 当前参数（与 ui.js / window.BEAM 共享同一个对象引用） */
export const params = { P: 50, aRel: 0.5 };

/** 把参数夹到合法区间（URL 预设、外部脚本都可能传越界值） */
export function sanitize(p) {
  for (const def of PARAM_DEFS) {
    const raw = Number(p[def.key]);
    const v = Number.isFinite(raw) ? raw : def.value;
    p[def.key] = Math.min(def.max, Math.max(def.min, v));
  }
  return p;
}

/**
 * 任意截面的挠度（m，向下为正）
 * @param {number} x 距左端距离（m）
 * @param {ReturnType<typeof computeDerived>} d
 */
export function deflectionAt(x, d) {
  const { L, EI, P, a, b } = d;
  const xx = Math.min(Math.max(x, 0), L);

  if (xx <= a) {
    // 左段公式：v = P·b·x·(L² − b² − x²) / (6·L·EI)
    return (P * b * xx * (L * L - b * b - xx * xx)) / (6 * L * EI);
  }
  // 右段公式（把 x 换成 L−x、b 换成 a）：v = P·a·(L−x)·(L² − a² − (L−x)²) / (6·L·EI)
  const u = L - xx;
  return (P * a * u * (L * L - a * a - u * u)) / (6 * L * EI);
}

/**
 * 任意截面的转角（rad，挠度对 x 的一阶导）—— 梁每一小段的倾斜角用它
 * @param {number} x @param {ReturnType<typeof computeDerived>} d
 */
export function slopeAt(x, d) {
  const { L, EI, P, a, b } = d;
  const xx = Math.min(Math.max(x, 0), L);

  if (xx <= a) {
    // d/dx [ P·b·x·(L²−b²−x²) / (6LEI) ] = P·b·(L²−b²−3x²) / (6LEI)
    return (P * b * (L * L - b * b - 3 * xx * xx)) / (6 * L * EI);
  }
  const u = L - xx;
  // 右段：令 u = L−x，v 对 x 求导 = −d/du = −P·a·(L²−a²−3u²) / (6LEI)
  return -(P * a * (L * L - a * a - 3 * u * u)) / (6 * L * EI);
}

/**
 * 任意截面的弯矩（kN·m，正弯矩）
 * @param {number} x @param {ReturnType<typeof computeDerived>} d
 */
export function momentAt(x, d) {
  const { L, RA, RB, a } = d;
  const xx = Math.min(Math.max(x, 0), L);
  return xx <= a ? RA * xx : RB * (L - xx);
}

/**
 * 数值求最大挠度：粗采样定位，再用黄金分割在邻域内细化。
 * （不用闭式解，是因为 a ≠ L/2 时最大挠度截面没有中学阶段的好公式）
 * @param {ReturnType<typeof computeDerived>} d
 */
function findMaxDeflection(d) {
  const { L } = d;
  const N = 400;
  let bestX = 0;
  let bestV = -Infinity;

  for (let i = 0; i <= N; i++) {
    const x = (i / N) * L;
    const v = deflectionAt(x, d);
    if (v > bestV) {
      bestV = v;
      bestX = x;
    }
  }

  // 黄金分割细化（区间 ±L/N），把位置精度拉到 1e-6 量级
  const gr = 0.6180339887498949;
  let lo = Math.max(0, bestX - L / N);
  let hi = Math.min(L, bestX + L / N);
  for (let k = 0; k < 60; k++) {
    const m1 = hi - (hi - lo) * gr;
    const m2 = lo + (hi - lo) * gr;
    if (deflectionAt(m1, d) > deflectionAt(m2, d)) hi = m2;
    else lo = m1;
  }
  const xStar = (lo + hi) / 2;
  return { x: xStar, v: deflectionAt(xStar, d) };
}

/**
 * 参数 → 全部派生量
 * @param {{ P: number, aRel: number }} p
 */
export function computeDerived(p) {
  const L = MODEL.L;
  const EI = MODEL.EI;

  const P = p.P;
  const a = p.aRel * L; // 荷载到左端的距离
  const b = L - a; // 荷载到右端的距离

  const RA = (P * b) / L; // 左支座反力（kN）
  const RB = (P * a) / L; // 右支座反力（kN）
  const Mmax = (P * a * b) / L; // 最大弯矩（kN·m），位置 x = a

  const derived = {
    L,
    EI,
    P,
    a,
    b,
    aRel: p.aRel,
    RA,
    RB,
    Mmax,
    xMmax: a, // 弯矩峰值位置，正好在荷载点
    vAtLoad: 0, // 荷载点挠度（m）
  };

  // 最大挠度（数值解）——必须在 derived 建好之后才能算，因为要用到 a/b/P
  const peak = findMaxDeflection(derived);
  derived.vmax = peak.v; // m
  derived.vmaxMm = peak.v * 1000; // mm
  derived.xVmax = peak.x; // 最大挠度截面
  derived.vAtLoad = deflectionAt(a, derived);

  return derived;
}

/** 面板读数格式化（返回字符串表，ui.js 只负责塞进 DOM） */
export function formatDerived(d) {
  const ratio = d.RB > 1e-9 ? d.RA / d.RB : Infinity;
  return {
    RA: d.RA.toFixed(1),
    RB: d.RB.toFixed(1),
    Mmax: d.Mmax.toFixed(1),
    vmax: d.vmaxMm.toFixed(2),
    vAtLoad: (d.vAtLoad * 1000).toFixed(2),
    ratio: Number.isFinite(ratio) ? ratio.toFixed(2) : '∞',
    a: d.a.toFixed(2),
    b: d.b.toFixed(2),
    xVmax: d.xVmax.toFixed(2),
  };
}
