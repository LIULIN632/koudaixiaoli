/**
 * ============================================================================
 * src/ui.js —— 参数面板：滑块 + 实时读数 + 反力分配条 + 状态栏
 * ============================================================================
 *
 * 约定：这个文件不碰 Three.js，只负责 DOM。
 *   - 按 PARAM_DEFS 自动生成滑块（要加参数只改 params.js，不用动这里）
 *   - 拖动滑块 → 写入 params → 回调 onInput()（main.js 里会重算并刷新 3D）
 *   - render(d) 把派生量写进读数和反力条
 */

import { PARAM_DEFS, formatDerived } from './params.js';

/**
 * @param {{
 *   params: Record<string, number>,
 *   onInput: () => void,
 * }} opts
 */
export function mountPanel({ params, onInput }) {
  const sliderHost = document.getElementById('sliders');
  const readoutHost = document.getElementById('readouts');
  const statusText = document.getElementById('status-text');
  const statusDot = document.getElementById('status-dot');

  /** @type {Record<string, HTMLInputElement>} */
  const inputs = {};
  /** @type {Record<string, HTMLElement>} */
  const outputs = {};

  // --------------------------------------------------------------
  // 1. 滑块
  // --------------------------------------------------------------
  for (const def of PARAM_DEFS) {
    const row = document.createElement('label');
    row.className = 'row';
    row.setAttribute('for', `in-${def.key}`);

    const head = document.createElement('div');
    head.className = 'row-head';

    const name = document.createElement('span');
    name.textContent = def.label;

    const out = document.createElement('output');
    out.id = `out-${def.key}`;
    out.innerHTML = `${Number(params[def.key] ?? def.value).toFixed(def.decimals)}${
      def.unit ? `<small>${def.unit}</small>` : ''
    }`;

    head.append(name, out);

    const input = document.createElement('input');
    input.type = 'range';
    input.id = `in-${def.key}`;
    input.min = String(def.min);
    input.max = String(def.max);
    input.step = String(def.step);
    input.value = String(params[def.key] ?? def.value);
    if (def.hint) input.title = def.hint;

    // 用户拖动的唯一入口：写参数 → 交给 main.js 重算
    input.addEventListener('input', () => {
      params[def.key] = Number(input.value);
      onInput();
    });

    row.append(head, input);
    sliderHost.append(row);

    inputs[def.key] = input;
    outputs[def.key] = out;
  }

  // --------------------------------------------------------------
  // 2. 读数区（含反力分配条）
  // --------------------------------------------------------------
  readoutHost.innerHTML = `
    <h2>计算结果</h2>
    <div class="grid">
      <div class="metric"><span>左支座反力 R<sub>A</sub></span><b id="m-RA">–</b></div>
      <div class="metric"><span>右支座反力 R<sub>B</sub></span><b id="m-RB">–</b></div>
      <div class="metric hl"><span>最大弯矩 M<sub>max</sub></span><b id="m-Mmax">–</b></div>
      <div class="metric hl"><span>最大挠度 v<sub>max</sub></span><b id="m-vmax">–</b></div>
      <div class="metric"><span>荷载点挠度 v(a)</span><b id="m-vload">–</b></div>
      <div class="metric"><span>最大挠度位置 x</span><b id="m-xv">–</b></div>
      <div class="metric"><span>荷载距 A 端 a</span><b id="m-a">–</b></div>
      <div class="metric"><span>荷载距 B 端 b</span><b id="m-b">–</b></div>
    </div>
    <div class="forcebar" title="两根支座反力的分配比例（RA : RB）">
      <div class="fb-a" id="fb-a">RA</div>
      <div class="fb-b" id="fb-b">RB</div>
    </div>
    <div class="forcebar-cap"><span id="fb-a-t">RA 50.0 kN</span><span id="fb-b-t">RB 50.0 kN</span></div>
  `;

  const cells = {
    RA: document.getElementById('m-RA'),
    RB: document.getElementById('m-RB'),
    Mmax: document.getElementById('m-Mmax'),
    vmax: document.getElementById('m-vmax'),
    vload: document.getElementById('m-vload'),
    xv: document.getElementById('m-xv'),
    a: document.getElementById('m-a'),
    b: document.getElementById('m-b'),
  };
  const fbA = document.getElementById('fb-a');
  const fbB = document.getElementById('fb-b');
  const fbAT = document.getElementById('fb-a-t');
  const fbBT = document.getElementById('fb-b-t');

  // --------------------------------------------------------------
  // 3. render：把一次计算结果刷到界面上
  // --------------------------------------------------------------
  /**
   * @param {ReturnType<import('./params.js').computeDerived>} d
   */
  function render(d) {
    const f = formatDerived(d);

    // 滑块旁的数值
    for (const def of PARAM_DEFS) {
      const v = Number(params[def.key] ?? def.value);
      outputs[def.key].innerHTML = `${v.toFixed(def.decimals)}${
        def.unit ? `<small>${def.unit}</small>` : ''
      }`;
      // 外部改了 params（如 window.BEAM.setParam）时也要把滑块同步过来
      if (inputs[def.key].value !== String(v)) inputs[def.key].value = String(v);
    }

    // 关键指标
    cells.RA.innerHTML = `${f.RA}<small>kN</small>`;
    cells.RB.innerHTML = `${f.RB}<small>kN</small>`;
    cells.Mmax.innerHTML = `${f.Mmax}<small>kN·m</small>`;
    cells.vmax.innerHTML = `${f.vmax}<small>mm</small>`;
    cells.vload.innerHTML = `${f.vAtLoad}<small>mm</small>`;
    cells.xv.innerHTML = `${f.xVmax}<small>m</small>`;
    cells.a.innerHTML = `${f.a}<small>m</small>`;
    cells.b.innerHTML = `${f.b}<small>m</small>`;

    // 反力分配条：用 flex-grow 表示比例
    const total = d.RA + d.RB;
    const ra = total > 1e-9 ? d.RA / total : 0.5;
    fbA.style.flexGrow = String(ra);
    fbB.style.flexGrow = String(1 - ra);
    fbA.textContent = ra > 0.16 ? `${(ra * 100).toFixed(0)}%` : '';
    fbB.textContent = 1 - ra > 0.16 ? `${((1 - ra) * 100).toFixed(0)}%` : '';
    fbAT.textContent = `RA ${f.RA} kN`;
    fbBT.textContent = `RB ${f.RB} kN`;

    // 状态栏：加一句人话解释
    const atMid = Math.abs(d.aRel - 0.5) < 1e-6;
    if (statusText) {
      statusText.textContent = atMid
        ? `跨中 · RA=RB · vmax ${f.vmax} mm`
        : `a/L ${d.aRel.toFixed(2)} · RA:RB ${f.ratio} · vmax ${f.vmax} mm`;
    }
    if (statusDot) statusDot.title = `${f.vmax} mm · ${f.Mmax} kN·m`;
  }

  return { render, outputs, inputs };
}
