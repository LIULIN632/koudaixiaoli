/**
 * ============================================================================
 * src/main.js —— 入口：装配各模块 + 动画循环 + 对外调试句柄
 * ============================================================================
 *
 * 数据流始终是单向的：
 *
 *   滑块 input ─┐
 *   URL 预设 ──┼→ params（唯一数据源）→ computeDerived(params) → derived
 *   外部脚本 ──┘                                               │
 *                                                              ├→ beam.update(derived)   刷新 3D
 *                                                              └→ panel.render(derived)  刷新面板
 *
 * 动画循环只做三件事：controls.update()、render()、diag.tick()。
 * 3D 只在参数变化时重算（不需要每帧重算力学量）。
 *
 * 调试句柄（浏览器控制台里可直接用）：
 *   BEAM.setParam('P', 80)         // 改参数并立即刷新
 *   BEAM.derived                   // 当前全部派生量
 *   BEAM.beam.group               // 梁模型 Group，可配合 TransformControls 调试
 *   BEAM.beam.meshes.momentRibbon // 弯矩图网格
 *   BEAM.beam.labels              // 三块 canvas 文字标签
 *   BEAM.stage.camera / scene / controls
 *   BEAM.diag.snapshot()          // 运行时诊断快照
 */

import * as THREE from 'three';
import { params, PARAM_DEFS, MODEL, sanitize, computeDerived, deflectionAt, slopeAt, momentAt } from './params.js';
import { createScene } from './scene.js';
import { buildBeam, DIM } from './beam.js';
import { mountPanel } from './ui.js';
import { createDiagnostics } from './diag.js';

const canvas = document.getElementById('view');
const diagEl = document.getElementById('diag');

const diag = createDiagnostics(diagEl, canvas);

/** @type {ReturnType<typeof createScene>} */
let stage;
/** @type {ReturnType<typeof buildBeam>} */
let beam;
/** @type {ReturnType<typeof mountPanel>} */
let panel;
/** @type {ReturnType<typeof computeDerived>} */
let derived;

/**
 * 读取 URL 预设，例如 ?P=80&aRel=0.35
 * 方便老师直接把某个工况的链接发给学生（也方便无头截图验证）
 * @returns {string[]} 实际生效的参数名
 */
function readUrlPreset() {
  const applied = [];
  const search = new URLSearchParams(window.location.search);
  for (const def of PARAM_DEFS) {
    if (!search.has(def.key)) continue;
    const raw = Number(search.get(def.key));
    if (!Number.isFinite(raw)) continue;
    params[def.key] = raw;
    applied.push(def.key);
  }
  sanitize(params);
  return applied;
}

/** 参数变了之后重新算一遍并刷新 3D 与面板 */
function applyParams() {
  sanitize(params);
  derived = computeDerived(params);
  beam.update(derived);
  panel.render(derived);
}

/** 窗口尺寸变化：画布 + 相机 + 渲染器一起跟进 */
function syncLayout() {
  stage.resize();
}

function boot() {
  const urlPreset = readUrlPreset();

  stage = createScene(canvas);
  beam = buildBeam(stage.scene);
  panel = mountPanel({ params, onInput: applyParams });

  applyParams();
  syncLayout();
  window.addEventListener('resize', syncLayout);

  let frames = 0;

  stage.renderer.setAnimationLoop(() => {
    // 动画循环：目前只有 OrbitControls 的惯性阻尼需要每帧更新，
    // 力学量在参数变化时才重算（见 applyParams），不必每帧算
    stage.controls.update();
    stage.renderer.render(stage.scene, stage.camera);

    frames += 1;
    if (frames === 3) {
      // 说明前 3 帧都渲染成功了，可以判定"场景已就绪"
      diag.markReady();
      document.documentElement.dataset.beamReady = '1';
    }
    diag.tick();
  });

  // 对外句柄
  window.BEAM = {
    THREE,
    params,
    PARAM_DEFS,
    MODEL,
    DIM,
    computeDerived,
    deflectionAt,
    slopeAt,
    momentAt,
    applyParams,
    /** 改一个参数并立刻刷新（面板滑块会同步） */
    setParam(key, value) {
      params[key] = Number(value);
      applyParams();
      return derived[key] ?? value;
    },
    beam,
    stage,
    panel,
    diag,
    urlPreset,
    /** 当前派生量（每次调用取最新对象） */
    get derived() {
      return derived;
    },
    /** 便捷方法：拿到某个 three.js 对象，方便 TransformControls 之类的外部调试 */
    getObject(name) {
      return stage.scene.getObjectByName(name) ?? null;
    },
  };
}

try {
  boot();
} catch (error) {
  diag.fail(error);
  document.body.dataset.status = 'error';
  // 出错时也在界面上留一句，避免"白屏无提示"
  const statusText = document.getElementById('status-text');
  if (statusText) statusText.textContent = `初始化失败：${error.message}`;
  throw error;
}
