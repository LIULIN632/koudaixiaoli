/* ── 手机兜底：WebGL 不可用 / 内核太旧时，别给用户一块黑屏 ────────────────
 *
 * 为什么需要它：课件的 3D 是 three.js + WebGL。手机上打不开的两种真实情形
 * 都**不会**抛到 App 层：
 *
 *   1. WebView 没给 WebGL（部分 ROM 省电模式下会关）→ three.js 里
 *      `getContext('webgl')` 返回 null，页面停在 booting，画面全黑；
 *   2. WebView 太旧，不认 `<script type="importmap">` → 模块根本没执行，
 *      同样停在 booting，控制台只有一句被忽略的语法/加载错误。
 *
 * 这两种只能由页面自己识别。识别到就换成一张静态图 + 一句人话，并把
 * `data-status` 改成 `no-webgl` / `stale-engine`，App 侧（CoursewareScreen
 * 的探针）读它就能给出对症提示。
 */

(function () {
  function probeWebgl() {
    try {
      var c = document.createElement('canvas');
      var gl = c.getContext('webgl') || c.getContext('experimental-webgl');
      return gl ? gl.getParameter(gl.VERSION) : '';
    } catch (e) {
      return '';
    }
  }

  function showFallback(title, detail) {
    if (document.querySelector('.fallback')) return;
    var wrap = document.createElement('div');
    wrap.className = 'fallback';
    wrap.innerHTML =
      '<img src="./poster.png" alt="课件示意图" />' +
      '<p><strong>' + title + '</strong></p>' +
      '<p>' + detail + '</p>';
    document.body.appendChild(wrap);
  }

  function settle() {
    var status = document.body.dataset.status || 'booting';
    if (status === 'ready') return;
    var gl = probeWebgl();
    if (!gl) {
      document.body.dataset.status = 'no-webgl';
      showFallback(
        '这台手机没给网页开 3D（WebGL）',
        '上面的图是课件的静态示意。想看能拖动的版本，用系统浏览器打开同一条链接，或在系统设置里允许「WebView 使用硬件加速」。'
      );
      return;
    }
    // 有 WebGL 但页面没跑起来 ⇒ 多半是内核太旧，模块脚本没执行
    if (status === 'booting') {
      document.body.dataset.status = 'stale-engine';
      showFallback(
        '浏览器内核太旧，课件脚本没跑起来',
        '上面的图是静态示意。用系统浏览器打开同一条链接通常能正常显示。'
      );
    }
  }

  // 给页面 2.2 秒：正常机器上 three.js 首帧在 300~800ms 内完成
  window.addEventListener('load', function () {
    setTimeout(settle, 2200);
  });
})();