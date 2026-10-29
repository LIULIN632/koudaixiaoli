/**
 * ============================================================================
 * src/params.js —— 三相土物理模型（整个课件的唯一数据源）
 * ============================================================================
 *
 * 【三相草图约定】
 * 取土粒（固相）的体积 Vs = 1 cm³ 作为基准，其余量都用它来表达，
 * 这样「孔隙比 e」在数值上就等于孔隙体积 Vv（因为 e = Vv / Vs）。
 *
 *      e  = Vv / Vs            孔隙比（无量纲）
 *      w  = Mw / Ms            含水率（界面用 %，计算用小数）
 *      Gs = Ms / (Vs · ρw)     土粒比重；取 ρw = 1 g/cm³ ⇒ Ms = Gs（克）
 *
 * 由 Vs = 1 直接得到：
 *      Vv = e                   孔隙体积
 *      V  = Vs + Vv = 1 + e     总体积
 *      Ms = Gs                  土粒质量
 *      Mw = w · Ms = w · Gs     水的质量
 *      Vw = Mw / ρw = w · Gs    水的体积（ρw 取 1，故数值相等）
 *      Va = Vv − Vw             气体体积
 *
 * 【关键约束】水的体积不可能超过孔隙体积。
 * 当 w · Gs > e 时，土已经饱和，多余的“水”在现实中排不出去，
 * 因此要把 Vw 截断到 Vv，同时把 Va 归零、Sr 记为 100%。
 * 界面上的红色「已饱和」提示就是由此而来。
 *
 * 推导出的工程指标：
 *      Sr = Vw / Vv                饱和度
 *      n  = Vv / V                 孔隙率
 *      ρ  = (Ms + Mw) / V          天然密度 (g/cm³)
 *      ρd = Ms / V                 干密度   (g/cm³)
 *      w_sat = e / Gs              饱和含水率（小数）
 */

/** 可调参数定义：UI 滑块、场景更新、外部编辑器都读这一份 */
export const PARAM_DEFS = [
  {
    key: 'e',
    label: '孔隙比 e',
    hint: 'Vv / Vs，土越松 e 越大',
    min: 0.3,
    max: 1.2,
    step: 0.01,
    value: 0.65,
    unit: '',
    decimals: 2,
  },
  {
    key: 'w',
    label: '含水率 w',
    hint: 'Mw / Ms，单位 %',
    min: 0,
    max: 40,
    step: 0.5,
    value: 18,
    unit: '%',
    decimals: 1,
  },
  {
    key: 'Gs',
    label: '土粒比重 Gs',
    hint: '砂土≈2.65，黏土≈2.72',
    min: 2.6,
    max: 2.75,
    step: 0.01,
    value: 2.7,
    unit: '',
    decimals: 2,
  },
];

/** 当前参数值（原地修改，供 UI 与外部脚本共同持有同一引用） */
export const params = Object.fromEntries(PARAM_DEFS.map((d) => [d.key, d.value]));

/** 把外部写入的参数夹到合法区间，避免 NaN / 越界把场景撑坏 */
export function sanitize(p = params) {
  for (const def of PARAM_DEFS) {
    const v = Number(p[def.key]);
    p[def.key] = Number.isFinite(v)
      ? Math.min(def.max, Math.max(def.min, v))
      : def.value;
  }
  return p;
}

/**
 * 由参数计算全部派生量。
 * @param {typeof params} p
 * @returns 派生量对象（体积 / 质量 / 指标 / 体积占比）
 */
export function computeDerived(p = params) {
  const e = p.e;
  const Gs = p.Gs;
  const wWanted = p.w / 100; // 界面是百分数，计算用小数的含水率

  // ---- 三相草图：Vs 取 1 ----
  const Vs = 1;
  const Vv = e * Vs;

  const Ms = Gs * Vs; // ρw = 1 g/cm³
  const VwWanted = wWanted * Gs; // = Mw / ρw

  // 水的体积被孔隙体积截断：超出部分不可能被土体容纳
  const saturated = VwWanted > Vv;
  const Vw = Math.min(VwWanted, Vv);
  const Va = Vv - Vw;

  const V = Vs + Vv;
  const Mw = Vw; // 水质量 = 水体积 × ρw
  const M = Ms + Mw;

  const Sr = Vv > 1e-9 ? Vw / Vv : 0;
  const n = Vv / V;
  const rho = M / V;
  const rhod = Ms / V;

  // 饱和含水率：孔隙刚好被水充满时的含水率（小数）
  const wSat = e / Gs;

  return {
    e,
    Gs,
    // 体积（cm³，以 Vs = 1 为基准）
    Vs,
    Vw,
    Va,
    Vv,
    V,
    // 质量（g）
    Ms,
    Mw,
    M,
    // 指标
    Sr,
    n,
    rho,
    rhod,
    // 状态
    saturated,
    wWanted,
    wActual: Vw / Gs, // 实际含水率（饱和后被截断）
    wSat,
    // 三相体积占比（0..1）—— 直接驱动 3D 层高与面板占比条
    fVs: Vs / V,
    fVw: Vw / V,
    fVa: Va / V,
  };
}

/** 格式化的读数文本，供 UI 直接使用 */
export function formatDerived(d) {
  return {
    Sr: `${(d.Sr * 100).toFixed(1)}%`,
    n: `${(d.n * 100).toFixed(1)}%`,
    rho: d.rho.toFixed(3),
    rhod: d.rhod.toFixed(3),
    Vs: d.Vs.toFixed(3),
    Vw: d.Vw.toFixed(3),
    Va: d.Va.toFixed(3),
    V: d.V.toFixed(3),
    wSat: `${(d.wSat * 100).toFixed(1)}%`,
  };
}
