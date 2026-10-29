/**
 * ============================================================================
 * src/scene.js —— 舞台：渲染器 / 相机 / 灯光 / 地面 / OrbitControls
 * ============================================================================
 *
 * 这个文件只负责"看得见、转得动"，不掺任何力学逻辑。
 * 相机按固定视角方向 + 自适应距离来摆位：窗口变窄时自动后退，
 * 保证 8 m 长的梁在任何屏幕比例下都完整落进画面。
 */

import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { MODEL } from './params.js';

/** 观察目标：梁略偏下，因为下方还有弯矩图要一起看 */
/** 观察目标：梁的中心（模型 group 已居中在原点附近） */
const TARGET = new THREE.Vector3(0, -0.75, 0);
/** 相机视线方向（从目标指向相机）：从右前上方看，能同时看到梁、支座和弯矩图 */
const VIEW_DIR = new THREE.Vector3(0.46, 0.34, 0.92).normalize();

/**
 * 构图包围盒（世界坐标）——相机自动缩放到刚好装下这个盒子。
 * 包含：梁 ±L/2、两端 A/B 圆标、弯矩图下探、三块文字标签的高度余量。
 */
const FOCUS_BOX = new THREE.Box3(
  new THREE.Vector3(-5.2, -3.55, -0.35),
  new THREE.Vector3(5.2, 2.35, 0.35)
);

/** 右侧参数面板占掉的宽度（CSS 里 .panel 是 326px + 20px 边距） */
const PANEL_WIDTH = 348;

/**
 * 创建舞台
 * @param {HTMLCanvasElement} canvas
 */
export function createScene(canvas) {
  const renderer = new THREE.WebGLRenderer({
    canvas,
    antialias: true,
    alpha: false,
    powerPreference: 'high-performance',
  });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.02;

  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0x0b0f14);
  scene.fog = new THREE.Fog(0x0b0f14, 18, 42);

  const camera = new THREE.PerspectiveCamera(40, 1, 0.1, 200);
  camera.position.copy(TARGET).add(VIEW_DIR.clone().multiplyScalar(14));
  camera.lookAt(TARGET);

  const controls = new OrbitControls(camera, canvas);
  controls.enableDamping = true;
  controls.dampingFactor = 0.075;
  controls.target.copy(TARGET);
  controls.minDistance = 6;
  controls.maxDistance = 34;
  controls.maxPolarAngle = Math.PI * 0.495; // 不允许转到地平线以下
  controls.update();

  // ------------------------------------------------------------------
  // 灯光：一盏主光投影 + 半球环境光 + 一盏冷色补光
  // ------------------------------------------------------------------
  const hemi = new THREE.HemisphereLight(0xa8c8ff, 0x0a0f14, 0.72);
  scene.add(hemi);

  const key = new THREE.DirectionalLight(0xffffff, 2.1);
  key.position.set(7.5, 13.5, 8.5);
  key.castShadow = true;
  key.shadow.mapSize.set(2048, 2048);
  key.shadow.camera.near = 1;
  key.shadow.camera.far = 45;
  key.shadow.camera.left = -11;
  key.shadow.camera.right = 11;
  key.shadow.camera.top = 9;
  key.shadow.camera.bottom = -9;
  key.shadow.bias = -0.0012;
  key.shadow.normalBias = 0.02;
  scene.add(key);

  const rim = new THREE.PointLight(0x4cc9f0, 26, 26, 2);
  rim.position.set(-7, 3.5, 7);
  scene.add(rim);

  const fill = new THREE.DirectionalLight(0x9fc4ff, 0.5);
  fill.position.set(-8, 4, -7);
  scene.add(fill);

  // ------------------------------------------------------------------
  // 地面：一块接收阴影的暗面 + 工程网格（帮助判断梁的位置与尺度）
  // ------------------------------------------------------------------
  const FLOOR_Y = -3.25;
  const shadowPlane = new THREE.Mesh(
    new THREE.CircleGeometry(16, 64),
    new THREE.ShadowMaterial({ color: 0x000000, opacity: 0.34 })
  );
  shadowPlane.rotation.x = -Math.PI / 2;
  shadowPlane.position.y = FLOOR_Y;
  shadowPlane.receiveShadow = true;
  scene.add(shadowPlane);

  const grid = new THREE.GridHelper(26, 26, 0x22303d, 0x141c24);
  grid.position.y = FLOOR_Y + 0.002;
  grid.material.transparent = true;
  grid.material.opacity = 0.55;
  scene.add(grid);

  // ------------------------------------------------------------------
  // 相机自适应：把 FOCUS_BOX 投影到屏幕上迭代调整距离与左右偏移，
  // 让整个模型正好落进"未被右侧面板遮挡"的那部分画面。
  // （纯数学反算在斜视角 + 透视下不好写，直接投影几次最稳）
  // ------------------------------------------------------------------
  const projTmp = new THREE.Vector3();
  const rightDir = new THREE.Vector3();
  const corners = [];
  for (let i = 0; i < 8; i++) {
    corners.push(
      new THREE.Vector3(
        i & 1 ? FOCUS_BOX.max.x : FOCUS_BOX.min.x,
        i & 2 ? FOCUS_BOX.max.y : FOCUS_BOX.min.y,
        i & 4 ? FOCUS_BOX.max.z : FOCUS_BOX.min.z
      )
    );
  }

  /** 把 8 个角点投到屏幕，返回像素包围盒 */
  function projectBox(w, h) {
    let minX = Infinity;
    let maxX = -Infinity;
    let minY = Infinity;
    let maxY = -Infinity;
    for (const c of corners) {
      projTmp.copy(c).project(camera);
      const px = (projTmp.x * 0.5 + 0.5) * w;
      const py = (-projTmp.y * 0.5 + 0.5) * h;
      if (px < minX) minX = px;
      if (px > maxX) maxX = px;
      if (py < minY) minY = py;
      if (py > maxY) maxY = py;
    }
    return { minX, maxX, minY, maxY };
  }

  function fitCamera(w, h) {
    const aspect = h > 0 ? w / h : 1;
    // 竖屏时把 FOV 放大一点，避免长条形的梁被压得太小
    camera.fov = aspect < 1 ? 40 + (1 - aspect) * 14 : 40;
    camera.aspect = aspect;
    camera.updateProjectionMatrix();

    // 右侧面板会盖住画面，构图时把可用宽度缩小
    const narrow = window.innerWidth <= 860;
    const usableW = narrow ? w : Math.max(w - PANEL_WIDTH, w * 0.5);
    const usableH = h;

    let dist = 13;
    const focus = TARGET.clone();

    for (let iter = 0; iter < 4; iter++) {
      camera.position.copy(focus).add(VIEW_DIR.clone().multiplyScalar(dist));
      camera.lookAt(focus);
      camera.updateMatrixWorld();

      const box = projectBox(w, h);
      const spanX = box.maxX - box.minX;
      const spanY = box.maxY - box.minY;
      // 需要放大/缩小多少倍才能填满可用区域（留 6% 边距）
      const scale = Math.max((spanX * 1.06) / usableW, (spanY * 1.06) / usableH);

      // 距离按比例修正（透视下近似成立，多迭代几次就收敛）
      dist *= scale;

      // 水平居中到可用区域：把模型中心从画面中心推到 usableW/2
      const centerPx = (box.minX + box.maxX) / 2;
      const shiftPx = centerPx - (narrow ? w / 2 : usableW / 2);
      // 每像素对应多少世界单位（用相机在目标距离处的视口宽度换算）
      const halfH = Math.tan((camera.fov * Math.PI) / 360) * dist;
      const worldPerPx = (halfH * 2) / h;
      camera.getWorldDirection(rightDir);
      rightDir.cross(camera.up).normalize(); // 相机右向量
      focus.addScaledVector(rightDir, shiftPx * worldPerPx);
    }

    camera.position.copy(focus).add(VIEW_DIR.clone().multiplyScalar(dist));
    controls.target.copy(focus);
    controls.update();
    camera.updateProjectionMatrix();
  }

  /**
   * 尺寸变化：渲染器跟着画布，相机重新适配
   * @returns {{w:number,h:number,aspect:number}}
   */
  function resize() {
    const w = canvas.clientWidth || window.innerWidth;
    const h = canvas.clientHeight || window.innerHeight;
    const aspect = h > 0 ? w / h : 1;

    renderer.setSize(w, h, false);
    fitCamera(w, h);

    return { w, h, aspect };
  }

  return {
    renderer,
    scene,
    camera,
    controls,
    resize,
    /** 供外部（诊断/自检）判断场景规模 */
    meta: { L: MODEL.L, FLOOR_Y, FOCUS_BOX },
  };
}
