/**
 * ============================================================================
 * src/ui.js —— HTML 参数面板：滑块生成 + 实时读数 + 三相占比条
 * ============================================================================
 * 面板完全由 params.js 里的 PARAM_DEFS 驱动，加一个参数只需要改那一处。
 * 这里的任何一次拖动都会回调 onInput()，由 main.js 触发场景重建。
 */

import { PARAM_DEFS, formatDerived } from './params.js';

/**
 * @param {{ params: Record<string, number>, onInput: () => void }} options
 */
export function mountPanel({ params, onInput }) {
  const slidersEl = document.getElementById('sliders');
  const readoutsEl = document.getElementById('readouts');
  const statusText = document.getElementById('status-text');
  const barSolid = document.getElementById('bar-solid');
  const barWater = document.getElementById('bar-water');
  const barAir = document.getElementById('bar-air');
  const barSpans = {
    solid: barSolid.querySelector('span'),
    water: barWater.querySelector('span'),
    air: barAir.querySelector('span'),
  };

  slidersEl.innerHTML = '';
  const outputs = {};
  const inputs = {};

  // ---------------- 1. 滑块 ----------------
  for (const def of PARAM_DEFS) {
    const row = document.createElement('label');
    row.className = 'row';
    row.title = def.hint || '';

    const head = document.createElement('div');
    head.className = 'row-head';

    const name = document.createElement('span');
    name.textContent = def.label;

    const out = document.createElement('output');
    out.id = `out-${def.key}`;
    outputs[def.key] = out;

    head.append(name, out);

    const input = document.createElement('input');
    input.type = 'range';
    input.id = `in-${def.key}`;
    input.min = String(def.min);
    input.max = String(def.max);
    input.step = String(def.step);
    // 以 params 里的当前值为准（可能是通过 URL 预设或 SOIL.setParam 改过的）
    input.value = String(params[def.key] ?? def.value);
    input.setAttribute('aria-label', def.label);
    inputs[def.key] = input;

    // 拖动时立即写入参数并触发场景更新
    input.addEventListener('input', () => {
      params[def.key] = Number(input.value);
      onInput();
    });

    row.append(head, input);
    slidersEl.append(row);

    // 点击整行任意处也能聚焦滑块，键盘方向键可微调
    row.addEventListener('click', () => input.focus({ preventScroll: true }));
  }

  // ---------------- 2. 读数 ----------------
  const METRICS = [
    { id: 'Sr', label: '饱和度 Sr', unit: '%' },
    { id: 'n', label: '孔隙率 n', unit: '%' },
    { id: 'rho', label: '天然密度 ρ', unit: 'g/cm³' },
    { id: 'rhod', label: '干密度 ρd', unit: 'g/cm³' },
    { id: 'V', label: '总体积 V', unit: 'cm³' },
    { id: 'wSat', label: '饱和含水率', unit: '%' },
  ];

  readoutsEl.innerHTML = '<h2>派生指标</h2>';
  const grid = document.createElement('div');
  grid.className = 'grid';
  const metricValues = {};

  for (const m of METRICS) {
    const cell = document.createElement('div');
    cell.className = 'metric';

    const label = document.createElement('span');
    label.textContent = m.label;

    const value = document.createElement('b');
    const num = document.createTextNode('—');
    const unit = document.createElement('small');
    unit.textContent = m.unit;
    value.append(num, unit);

    cell.append(label, value);
    grid.append(cell);
    metricValues[m.id] = num;
  }

  readoutsEl.append(grid);

  const badge = document.createElement('div');
  badge.className = 'badge';
  badge.id = 'sat-badge';
  badge.hidden = true;
  badge.textContent = '已饱和：多余的水无法进入孔隙';
  readoutsEl.append(badge);

  // ---------------- 3. 渲染 ----------------
  /** @param {ReturnType<import('./params.js').computeDerived>} d */
  function render(d) {
    // 滑块位置 + 右侧数值
    for (const def of PARAM_DEFS) {
      const v = params[def.key];
      // 保持滑块与参数同步（URL 预设 / SOIL.setParam 也能反映到 UI 上）
      if (inputs[def.key].value !== String(v)) inputs[def.key].value = String(v);
      outputs[def.key].innerHTML = `${v.toFixed(def.decimals)}<small>${def.unit}</small>`;
    }

    // 派生指标
    const text = formatDerived(d);
    for (const id of Object.keys(metricValues)) {
      metricValues[id].nodeValue = text[id] ?? '—';
    }

    // 饱和提示
    badge.hidden = !d.saturated;
    const effW = (d.wActual * 100).toFixed(1);
    statusText.textContent = d.saturated
      ? `已饱和 · 实际含水率被截断为 ${effW}%`
      : `未饱和 · 饱和度 ${(d.Sr * 100).toFixed(0)}%`;

    // 三相占比条（flex-grow 直接吃体积占比）
    const setBar = (el, span, frac, label) => {
      const pct = Math.max(frac * 100, 0);
      el.style.flexGrow = String(Math.max(frac, 0.0001));
      el.style.opacity = pct < 0.6 ? '0.25' : '1';
      span.textContent = pct < 7 ? '' : `${pct.toFixed(0)}%`;
      span.title = label;
    };
    setBar(barSolid, barSpans.solid, d.fVs, '固相');
    setBar(barWater, barSpans.water, d.fVw, '液相');
    setBar(barAir, barSpans.air, d.fVa, '气相');
  }

  return { render, outputs };
}
