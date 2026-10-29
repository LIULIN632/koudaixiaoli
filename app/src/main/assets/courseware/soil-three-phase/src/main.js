/**
 * ============================================================================
 * src/main.js —— 主入口：装配各模块 + 驱动动画循环
 * ============================================================================
 * 分工：
 *   scene.js  创建渲染器 / 相机 / 灯光 / OrbitControls
 *   soil.js   构建并更新三相土几何体
 *   params.js 三相物理模型（唯一数据源）
 *   ui.js     HTML 参数面板
 *   diag.js   运行时诊断
 * 本文件只做「接线」，不写具体几何或物理。
 */

import * as THREE from 'three';
import { createScene } from './scene.js';
import { buildSoil } from './soil.js';
import { params, computeDerived, sanitize, PARAM_DEFS } from './params.js';
import { mountPanel } from './ui.js';
import { createDiagnostics } from './diag.js';

const canvas = document.getElementById('view');
const diag = createDiagnostics(document.getElementById('diag'), canvas);

/** 全局引用，方便编辑器 / 控制台 / Agent 调试 */
let stage = null;
let soil = null;
let panel = null;

/** 参数 → 物理量 → 场景 + 面板（一次完整刷新） */
function applyParams() {
  sanitize(params);
  const derived = computeDerived(params);
  if (soil) soil.update(derived);
  if (panel) panel.render(derived);
  return derived;
}

/**
 * 从 URL 读取参数预设，例如 ?e=1.05&w=8&Gs=2.68
 * 课堂里可以把某个状态的链接直接发给学生（不需要手工再拖滑块）。
 * @returns {string[]} 实际生效的参数名
 */
function readUrlPreset() {
  const query = new URLSearchParams(location.search);
  const applied = [];
  for (const def of PARAM_DEFS) {
    if (!query.has(def.key)) continue;
    const value = Number(query.get(def.key));
    if (!Number.isFinite(value)) continue;
    params[def.key] = value;
    applied.push(def.key);
  }
  if (applied.length) sanitize(params);
  return applied;
}

function boot() {
  // ---- 0. URL 预设（必须在建面板之前，滑块才会停在正确位置） ----
  const urlPreset = readUrlPreset();

  // ---- 1. 场景 ----
  stage = createScene(canvas);

  // ---- 2. 模型 ----
  soil = buildSoil(stage.scene);

  // ---- 3. 参数面板 ----
  panel = mountPanel({ params, onInput: applyParams });

  // 首帧之前先算一次，避免出现"空壳"画面
  applyParams();

  // ---- 4. 尺寸自适应 ----
  /** 画面尺寸变化时同时调整相机视野与标签排版 */
  function syncLayout() {
    const size = stage.resize();
    if (size) soil.setLabelLayout(size.aspect);
  }

  syncLayout();
  window.addEventListener('resize', syncLayout);

  // ---- 5. 动画循环 ----
  const clock = new THREE.Clock();
  let firstFrames = 0;

  stage.renderer.setAnimationLoop(() => {
    // 水面焦散动画：只推进时间 uniform，不重建几何
    soil.uniforms.uTime.value = clock.getElapsedTime();

    // OrbitControls 的阻尼需要每帧 update
    stage.controls.update();

    stage.renderer.render(stage.scene, stage.camera);

    // 前几帧渲染成功后再宣布 ready（此时着色器一定编译通过）
    if (firstFrames < 3) {
      firstFrames += 1;
      if (firstFrames === 3) {
        diag.markReady();
        document.documentElement.dataset.soilReady = '1';
      }
    }

    diag.tick();
  });

  // ---- 6. 对外暴露，便于编辑器 / TransformControls / 脚本直接驱动 ----
  window.SOIL = {
    THREE,
    params,
    PARAM_DEFS,
    /** 本次页面加载时从 URL 预设里生效的参数名 */
    urlPreset,
    computeDerived,
    applyParams,
    /**
     * 改单个参数并立即刷新（编辑器里最常用的入口）
     * @example SOIL.setParam('w', 32)
     */
    setParam(key, value) {
      if (!(key in params)) throw new Error(`未知参数: ${key}`);
      params[key] = Number(value);
      return applyParams();
    },
    get soil() {
      return soil;
    },
    get stage() {
      return stage;
    },
    get panel() {
      return panel;
    },
    /** 水面着色器的 uniform，可直接改 .value 观察效果 */
    get uniforms() {
      return soil.uniforms;
    },
    derived: () => computeDerived(params),
    diag,
  };
}

try {
  boot();
} catch (error) {
  // 初始化失败也要有可见的诊断，而不是白屏
  diag.fail(error);
  console.error(error);
}
