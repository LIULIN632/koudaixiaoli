/**
 * ============================================================================
 * src/diag.js —— 运行时诊断
 * ============================================================================
 * 课件交付要求「无控制台错误」，所以把错误也收集到界面上：
 *   - window.onerror / unhandledrejection  → 记为错误
 *   - WebGL context lost                   → 记为错误
 *   - 每 500ms 统计一次 FPS
 *
 * 同时把结果写进 <html> 的 data-* 属性，无头浏览器 --dump-dom 就能读到：
 *   data-diag-ready="1"  data-diag-errors="0"  data-diag-fps="60"
 */

export function createDiagnostics(el, canvas) {
  const state = {
    errors: [],
    ready: false,
    frames: 0,
    fps: 0,
    startedAt: performance.now(),
    lastSampleAt: performance.now(),
    samplerFrames: 0,
  };

  function paint() {
    const count = state.errors.length;
    const last = count ? state.errors[count - 1].message : '';

    if (el) {
      el.dataset.hasError = count ? '1' : '0';
      el.textContent = `FPS ${state.fps || '--'} · 状态 ${state.ready ? 'ready' : 'booting'} · 错误 ${count}${
        count ? ` · ${last}` : ''
      }`;
      el.title = state.errors.map((e) => `[${e.kind}] ${e.message}`).join('\n');
    }

    const root = document.documentElement;
    root.dataset.diagErrors = String(count);
    root.dataset.diagReady = state.ready ? '1' : '0';
    root.dataset.diagFps = String(state.fps);
  }

  /** 记录一条错误（重复错误只保留最近 20 条） */
  function record(kind, message) {
    state.errors.push({ kind, message: String(message).slice(0, 300), at: Date.now() });
    if (state.errors.length > 20) state.errors.shift();
    document.body.dataset.status = 'error';
    paint();
  }

  window.addEventListener('error', (event) => {
    const where = event.filename ? ` @ ${event.filename}:${event.lineno}` : '';
    record('error', `${event.message}${where}`);
  });

  window.addEventListener('unhandledrejection', (event) => {
    const reason = event.reason;
    record('promise', reason && reason.message ? reason.message : String(reason));
  });

  if (canvas) {
    canvas.addEventListener('webglcontextlost', (event) => {
      event.preventDefault();
      record('webgl', 'WebGL 上下文丢失');
    });
  }

  function tick() {
    state.frames += 1;
    state.samplerFrames += 1;

    const now = performance.now();
    const dt = now - state.lastSampleAt;
    if (dt >= 500) {
      state.fps = Math.round((state.samplerFrames * 1000) / dt);
      state.samplerFrames = 0;
      state.lastSampleAt = now;
      paint();
    }
  }

  function markReady() {
    state.ready = true;
    if (!state.errors.length) document.body.dataset.status = 'ready';
    paint();
  }

  /** 初始化阶段抛错时调用 */
  function fail(error) {
    record('init', error && error.message ? error.message : String(error));
  }

  /** 供无头验收 / 外部脚本读取的快照 */
  function snapshot() {
    return {
      ready: state.ready,
      fps: state.fps,
      frames: state.frames,
      errorCount: state.errors.length,
      errors: state.errors.slice(),
      bodyStatus: document.body.dataset.status || '',
    };
  }

  paint();

  return { state, record, tick, markReady, fail, snapshot };
}
