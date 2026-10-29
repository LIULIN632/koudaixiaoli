/**
 * ============================================================================
 * src/scene.js —— 场景初始化：渲染器 / 相机 / 灯光 / 地面 / OrbitControls
 * ============================================================================
 * 只负责「舞台」，不关心课的物理内容；土壤模型在 src/soil.js 里单独构建。
 */

import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

/**
 * @param {HTMLCanvasElement} canvas
 */
export function createScene(canvas) {
  // ---------------- 渲染器 ----------------
  const renderer = new THREE.WebGLRenderer({
    canvas,
    antialias: true,
    powerPreference: 'high-performance',
  });
  // 限制 DPR，避免 4K 屏上像素量爆炸
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.02;

  // ---------------- 场景与雾 ----------------
  const scene = new THREE.Scene();
  scene.background = new THREE.Color('#0b0f14');
  // 远处淡出，避免地面网格硬边
  scene.fog = new THREE.Fog('#0b0f14', 13, 27);

  // ---------------- 相机 ----------------
  const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 120);
  camera.position.set(5.4, 3.9, 6.6);
  camera.lookAt(0, 1.45, 0);

  // ---------------- 交互：左键旋转 / 滚轮缩放 / 右键平移 ----------------
  const controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.075;
  controls.rotateSpeed = 0.85;
  controls.zoomSpeed = 0.9;
  controls.panSpeed = 0.7;
  controls.minDistance = 3.4;
  controls.maxDistance = 20;
  controls.minPolarAngle = 0.15;
  // 稍微留一点余量，防止转到地面以下看不到模型
  controls.maxPolarAngle = Math.PI * 0.492;
  controls.target.set(0, 1.45, 0);
  controls.update();

  // ---------------- 灯光 ----------------
  // 半球光：天空色 / 地面反弹色，给整体一点环境层次
  const hemi = new THREE.HemisphereLight(0x9fc4ff, 0x0a0f14, 0.6);
  scene.add(hemi);

  // 主光：负责投影，压出体积感
  const key = new THREE.DirectionalLight(0xffffff, 2.4);
  key.position.set(6.5, 9.5, 5.5);
  key.castShadow = true;
  key.shadow.mapSize.set(2048, 2048);
  key.shadow.camera.near = 1;
  key.shadow.camera.far = 30;
  key.shadow.camera.left = -7;
  key.shadow.camera.right = 7;
  key.shadow.camera.top = 8;
  key.shadow.camera.bottom = -3;
  key.shadow.bias = -0.0006;
  key.shadow.normalBias = 0.02;
  scene.add(key);

  // 侧逆光：勾一点边缘，冷色调
  const rim = new THREE.PointLight(0x4cc9f0, 26, 18, 2);
  rim.position.set(-5.2, 2.8, -4.6);
  scene.add(rim);

  // 补光：让阴面不至于死黑
  const fill = new THREE.DirectionalLight(0x91b7ff, 0.55);
  fill.position.set(-4, 3.2, 5);
  scene.add(fill);

  // ---------------- 地面 ----------------
  const ground = new THREE.Mesh(
    new THREE.CircleGeometry(9.5, 72),
    new THREE.MeshStandardMaterial({
      color: 0x121a24,
      roughness: 1,
      metalness: 0,
    })
  );
  ground.rotation.x = -Math.PI / 2;
  ground.receiveShadow = true;
  scene.add(ground);

  const grid = new THREE.PolarGridHelper(9.5, 12, 8, 72, 0x22303f, 0x18222d);
  grid.position.y = 0.002;
  grid.material.transparent = true;
  grid.material.opacity = 0.75;
  scene.add(grid);

  // ---------------- 自适应尺寸 ----------------
  function resize() {
    const w = canvas.clientWidth || window.innerWidth;
    const h = canvas.clientHeight || window.innerHeight;
    if (w === 0 || h === 0) return null;
    renderer.setSize(w, h, false); // false：不覆盖 CSS 尺寸
    const aspect = w / h;
    camera.aspect = aspect;
    // 竖屏 / 窄屏时放大竖直视野，保证模型左右不被裁掉
    camera.fov = aspect < 1 ? 42 + (1 - aspect) * 16 : 42;
    camera.updateProjectionMatrix();
    return { w, h, aspect };
  }

  return { renderer, scene, camera, controls, resize };
}
