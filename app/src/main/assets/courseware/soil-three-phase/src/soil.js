/**
 * ============================================================================
 * src/soil.js —— 土的三相「立体三相图」
 * ============================================================================
 *
 * 建模思路（把课本上的二维三相草图竖起来做成 3D）：
 *
 *   ┌──────────────┐  ← 玻璃容器 = 土的总体积 V
 *   │   气相 Air   │     Vv 的上半部分，透明、越饱和越淡
 *   ├──────────────┤
 *   │   液相 Water │     Vv 中水的体积 Vw，蓝色半透明 + 焦散动画
 *   ├──────────────┤
 *   │   固相 Soil  │     土粒体积 Vs，棕色颗粒堆
 *   └──────────────┘
 *
 * 三层沿 +Y 方向堆叠，边界严格按 computeDerived() 算出的体积占比分配高度：
 *      h_层 = (V_层 / V) × HEIGHT
 * 因此拖动「孔隙比 / 含水率」滑块时，三层的相对高度、颜色、透明度会一起变化，
 * 这是本课件最核心的可观测视觉反馈。
 */

import * as THREE from 'three';

/** 模型在世界坐标里的尺寸（与真实体积无关，只是画框大小） */
export const DIM = {
  foot: 2.0, // 底面边长
  height: 3.0, // 总高度，对应总体积 V
};

const FONT_STACK = '"PingFang SC", "Microsoft YaHei", "Noto Sans SC", sans-serif';

/** 确定性伪随机：刷新后颗粒排布不变，便于课堂复现 */
function mulberry32(seed) {
  let a = seed >>> 0;
  return function random() {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * 程序化生成的「土粒」贴图：在画布上随机撒点 + 轻微明暗，
 * 贴到固相层上就能看出颗粒感，比纯色块更像土。
 * 用 canvas 生成可以避免引入外部图片资源（离线可用）。
 */
function createGrainTexture() {
  const size = 256;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');

  // 底色
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, size, size);

  // 撒 2600 颗深浅不一的"砂粒"
  const rand = mulberry32(20261028);
  for (let i = 0; i < 2600; i++) {
    const x = rand() * size;
    const y = rand() * size;
    const r = 0.7 + rand() * 2.6;
    const lum = 150 + Math.floor(rand() * 105); // 150..255
    const warm = Math.floor(lum * (0.86 + rand() * 0.1));
    ctx.fillStyle = `rgb(${lum}, ${warm}, ${Math.floor(warm * 0.82)})`;
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fill();
  }

  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.wrapS = THREE.RepeatWrapping;
  texture.wrapT = THREE.RepeatWrapping;
  texture.repeat.set(2, 3); // 贴图密度：底面 2 次、侧面 3 次
  texture.anisotropy = 4;
  return texture;
}

/** 创建一个随文本更新的精灵标签（canvas 贴图） */
function createLabel() {
  const canvas = document.createElement('canvas');
  canvas.width = 512;
  canvas.height = 128;
  const ctx = canvas.getContext('2d');

  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.minFilter = THREE.LinearFilter;
  texture.generateMipmaps = false;

  const sprite = new THREE.Sprite(
    new THREE.SpriteMaterial({
      map: texture,
      transparent: true,
      depthTest: false,
      depthWrite: false,
    })
  );
  sprite.scale.set(1.95, 0.4875, 1);
  sprite.renderOrder = 20;

  /** @param {string} title @param {string} detail @param {string} accent */
  function draw(title, detail, accent) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // 背景胶囊
    ctx.fillStyle = 'rgba(10, 15, 21, 0.72)';
    ctx.beginPath();
    const r = 26;
    ctx.moveTo(r, 8);
    ctx.arcTo(504, 8, 504, 120, r);
    ctx.arcTo(504, 120, 8, 120, r);
    ctx.arcTo(8, 120, 8, 8, r);
    ctx.arcTo(8, 8, 504, 8, r);
    ctx.closePath();
    ctx.fill();

    // 左侧强调色竖条
    ctx.fillStyle = accent;
    ctx.fillRect(8, 8, 5, 112);

    ctx.textBaseline = 'middle';
    ctx.fillStyle = accent;
    ctx.font = `600 42px ${FONT_STACK}`;
    ctx.fillText(title, 34, 48);

    ctx.fillStyle = '#c9dae8';
    ctx.font = `400 32px ${FONT_STACK}`;
    ctx.fillText(detail, 34, 96);

    texture.needsUpdate = true;
  }

  return { sprite, draw };
}

/**
 * 构建三相土模型
 * @param {THREE.Scene} scene
 */
export function buildSoil(scene) {
  const foot = DIM.foot;
  const H = DIM.height;

  const group = new THREE.Group();
  group.name = 'SoilThreePhase';
  scene.add(group);

  // ======================================================================
  // 1. 玻璃容器：可视化「总体积 V」的边界
  // ======================================================================
  const glassGeo = new THREE.BoxGeometry(foot, H, foot);

  const glassBox = new THREE.Mesh(
    glassGeo,
    new THREE.MeshStandardMaterial({
      color: 0x8fd8ff,
      transparent: true,
      opacity: 0.035,
      roughness: 0.25,
      metalness: 0,
      depthWrite: false,
      side: THREE.DoubleSide,
    })
  );
  glassBox.position.y = H / 2;
  group.add(glassBox);

  const glassEdges = new THREE.LineSegments(
    new THREE.EdgesGeometry(glassGeo),
    new THREE.LineBasicMaterial({
      color: 0x5fd0ff,
      transparent: true,
      opacity: 0.42,
    })
  );
  glassEdges.position.y = H / 2;
  group.add(glassEdges);

  // ======================================================================
  // 2. 固相：土粒骨架（一块基体 + 一堆实例化小球做颗粒质感）
  // ======================================================================
  const solidMaterial = new THREE.MeshStandardMaterial({
    color: 0xb4854e,
    map: createGrainTexture(),
    roughness: 0.96,
    metalness: 0.02,
    flatShading: true,
  });

  const solidBody = new THREE.Mesh(
    new THREE.BoxGeometry(foot * 0.996, 1, foot * 0.996),
    solidMaterial
  );
  solidBody.castShadow = true;
  solidBody.receiveShadow = true;

  const grainMaterial = new THREE.MeshStandardMaterial({
    color: 0x8a5c2c,
    roughness: 0.9,
    metalness: 0.02,
    flatShading: true,
  });

  const rand = mulberry32(20261028);
  const dummy = new THREE.Object3D();

  // 固相层容器：整体 scale.y = 层高，里面只放单位高度的实心块
  const solidGroup = new THREE.Group();
  solidGroup.name = 'SolidPhase';
  solidGroup.add(solidBody);
  group.add(solidGroup);

  // ======================================================================
  // 2b. 表面碎石：撒在固相层顶面的一层小颗粒
  //     注意：它们挂在 group 上而不是 solidGroup 上 —— 否则会被 scale.y
  //     拉成竖长的椭球；现在每个实例只改 y，尺寸保持不变。
  // ======================================================================
  const PEBBLE_COUNT = 90;
  const pebbles = new THREE.InstancedMesh(
    new THREE.SphereGeometry(1, 6, 4),
    grainMaterial,
    PEBBLE_COUNT
  );
  pebbles.name = 'SoilSurfacePebbles';
  pebbles.castShadow = false;
  pebbles.receiveShadow = true;
  group.add(pebbles);

  // 预先算好的确定性布局（刷新后颗粒位置不变，方便课堂截图对照）
  const pebbleData = [];
  for (let i = 0; i < PEBBLE_COUNT; i++) {
    const r = 0.030 + rand() * 0.045; // 世界坐标半径
    pebbleData.push({
      x: (rand() - 0.5) * (foot * 0.94 - r * 2),
      z: (rand() - 0.5) * (foot * 0.94 - r * 2),
      r,
      flat: 0.55 + rand() * 0.3, // 竖向压扁，像被压实过的碎石
      rx: rand() * Math.PI,
      ry: rand() * Math.PI,
    });
  }

  // ======================================================================
  // 3. 液相：自定义着色器水面（uniforms 直接暴露给外部编辑器）
  // ======================================================================
  const waterUniforms = {
    uTime: { value: 0 }, // 动画时间
    uHeight: { value: 1.0 }, // 水体高度（世界单位）
    uSat: { value: 0.5 }, // 饱和度 Sr，0..1
    uOpacity: { value: 0.7 }, // 整体不透明度
    uDeep: { value: new THREE.Color(0x0b3f79) }, // 水体深处颜色
    uShallow: { value: new THREE.Color(0x6fc3ff) }, // 水面附近颜色
  };

  const waterMaterial = new THREE.ShaderMaterial({
    uniforms: waterUniforms,
    transparent: true,
    depthWrite: false,
    side: THREE.DoubleSide,
    toneMapped: false,
    vertexShader: /* glsl */ `
      varying vec2 vUv;

      void main() {
        vUv = uv;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }
    `,
    fragmentShader: /* glsl */ `
      uniform float uTime;
      uniform float uSat;
      uniform float uOpacity;
      uniform vec3  uDeep;
      uniform vec3  uShallow;

      varying vec2 vUv;

      // 两组正弦叠加，模拟水体内部的焦散光斑（高频 => 细碎水纹而不是大片暗斑）
      float caustic(vec2 p, float t) {
        float a = sin(p.x * 26.0 + t * 1.3) * cos(p.y * 21.0 - t * 1.1);
        float b = sin(p.x * 41.0 - t * 0.85) * sin(p.y * 37.0 + t * 1.7);
        return 0.5 + 0.22 * a + 0.16 * b;
      }

      void main() {
        // vUv.y：0 = 水体底部，1 = 水面，越靠上越亮
        vec3 color = mix(uDeep, uShallow, smoothstep(0.02, 1.0, vUv.y));

        float c = caustic(vUv, uTime);
        color += c * 0.11 * (0.4 + 0.6 * uSat);

        // 饱和度越高，水体越"实"（不透明度上升）
        float alpha = uOpacity * (0.5 + 0.5 * uSat);
        // 水面附近加一道亮边
        alpha *= 0.82 + 0.18 * smoothstep(0.85, 1.0, vUv.y);

        gl_FragColor = vec4(color, alpha);
      }
    `,
  });

  const waterMesh = new THREE.Mesh(
    new THREE.BoxGeometry(foot * 0.996, 1, foot * 0.996),
    waterMaterial
  );
  waterMesh.name = 'WaterPhase';
  group.add(waterMesh);

  // ======================================================================
  // 4. 气相：空气层（高度 = Va，越通透代表气越多）
  // ======================================================================
  const airMaterial = new THREE.MeshStandardMaterial({
    color: 0x86b6dd,
    transparent: true,
    opacity: 0.05,
    roughness: 1,
    metalness: 0,
    depthWrite: false,
    side: THREE.DoubleSide,
  });

  const airMesh = new THREE.Mesh(
    new THREE.BoxGeometry(foot * 0.996, 1, foot * 0.996),
    airMaterial
  );
  airMesh.name = 'AirPhase';
  group.add(airMesh);

  // ======================================================================
  // 5. 层名标签（Sprite 始终面向相机）
  // ======================================================================
  const labelX = -(foot / 2 + 1.18);

  const solidLabel = createLabel();
  solidLabel.sprite.position.set(labelX, 0.6, 0);
  group.add(solidLabel.sprite);

  const waterLabel = createLabel();
  waterLabel.sprite.position.set(labelX, 1.2, 0);
  group.add(waterLabel.sprite);

  const airLabel = createLabel();
  airLabel.sprite.position.set(labelX, 1.8, 0);
  group.add(airLabel.sprite);

  // ======================================================================
  // 6. 更新：把物理量映射到几何 / 材质 / 标签
  // ======================================================================
  const tmpColor = new THREE.Color();
  const SOLID_DRY = new THREE.Color(0xc0965c);
  const SOLID_WET = new THREE.Color(0x4a2a10);
  const SHALLOW_DRY = new THREE.Color(0x8fd9ff);
  const SHALLOW_WET = new THREE.Color(0x2f8fe0);

  /**
   * 按画面宽高比摆放三块层名标签：
   * 竖屏 / 窄屏时把标签往容器边上收、并缩小，避免被画面裁掉。
   * @param {number} aspect 画布宽 / 高
   */
  function setLabelLayout(aspect) {
    const narrow = aspect < 1.05;
    const scaleX = narrow ? 1.46 : 1.95;
    const x = narrow ? -(foot / 2 + 0.7) : -(foot / 2 + 1.18);
    for (const label of [solidLabel, waterLabel, airLabel]) {
      label.sprite.scale.set(scaleX, scaleX * 0.25, 1);
      label.sprite.position.x = x;
    }
  }

  /**
   * @param {ReturnType<import('./params.js').computeDerived>} d 派生量
   */
  function update(d) {
    // 世界高度与体积成正比：k = HEIGHT / V
    const k = H / d.V;
    const hS = d.Vs * k;
    const hW = d.Vw * k;
    const hA = d.Va * k;

    // ---- 固相：底部一段 ----
    solidGroup.scale.y = hS;
    solidGroup.position.y = hS / 2;

    // 顶面碎石跟着固相层表面走（只改 y，其余不变）
    for (let i = 0; i < PEBBLE_COUNT; i++) {
      const p = pebbleData[i];
      dummy.position.set(p.x, hS - p.r * p.flat * 0.35, p.z);
      dummy.rotation.set(p.rx, p.ry, 0);
      dummy.scale.set(p.r, p.r * p.flat, p.r);
      dummy.updateMatrix();
      pebbles.setMatrixAt(i, dummy.matrix);
    }
    pebbles.instanceMatrix.needsUpdate = true;

    // ---- 液相：紧贴固相上方 ----
    const waterVisible = hW > 0.004;
    waterMesh.visible = waterVisible;
    waterMesh.scale.y = Math.max(hW, 0.001);
    waterMesh.position.y = hS + hW / 2;

    // ---- 气相：最上面一段 ----
    const airVisible = hA > 0.004;
    airMesh.visible = airVisible;
    airMesh.scale.y = Math.max(hA, 0.001);
    airMesh.position.y = hS + hW + hA / 2;

    // ---- 颜色 / 透明度反馈 ----
    const sat = THREE.MathUtils.clamp(d.Sr, 0, 1);

    // 土越湿颜色越深（干土发黄、湿土发褐）
    tmpColor.copy(SOLID_DRY).lerp(SOLID_WET, sat * 0.9);
    solidMaterial.color.copy(tmpColor);
    grainMaterial.color.copy(tmpColor).multiplyScalar(0.78);

    // 水越满越蓝、越不透明
    waterUniforms.uSat.value = sat;
    waterUniforms.uHeight.value = hW;
    waterUniforms.uOpacity.value = 0.5 + 0.42 * sat;
    waterUniforms.uShallow.value.copy(SHALLOW_DRY).lerp(SHALLOW_WET, sat);

    // 气越少越"看不见"（饱和土几乎没有气相）；整体保持很淡，避免遮挡水体
    const airRatio = d.Vv > 1e-9 ? d.Va / d.Vv : 0;
    airMaterial.opacity = 0.012 + 0.055 * airRatio;

    // ---- 标签 ----
    const showS = hS > 0.16;
    solidLabel.sprite.visible = showS;
    if (showS) {
      solidLabel.sprite.position.y = hS / 2;
      solidLabel.draw('固相 Solid', `Vs = ${d.Vs.toFixed(3)} cm³ · ${(d.fVs * 100).toFixed(0)}%`, '#ffc47a');
    }

    const showW = hW > 0.16 && waterVisible;
    waterLabel.sprite.visible = showW;
    if (showW) {
      waterLabel.sprite.position.y = hS + hW / 2;
      waterLabel.draw('液相 Water', `Vw = ${d.Vw.toFixed(3)} cm³ · Sr ${(sat * 100).toFixed(0)}%`, '#7fd0ff');
    }

    const showA = hA > 0.16 && airVisible;
    airLabel.sprite.visible = showA;
    if (showA) {
      airLabel.sprite.position.y = hS + hW + hA / 2;
      airLabel.draw('气相 Air', `Va = ${d.Va.toFixed(3)} cm³ · ${(d.fVa * 100).toFixed(0)}%`, '#dbe8f3');
    }

    // 让标签整体不遮挡容器边缘
    const labelAlpha = 1.0;
    solidLabel.sprite.material.opacity = labelAlpha;
    waterLabel.sprite.material.opacity = labelAlpha;
    airLabel.sprite.material.opacity = labelAlpha;
  }

  return {
    group,
    update,
    setLabelLayout,
    /** 暴露给编辑器 / 外部脚本的 uniform（可直接改 .value） */
    uniforms: waterUniforms,
    /** 需要单独调用的材质句柄 */
    materials: { solidMaterial, grainMaterial, waterMaterial, airMaterial, glass: glassBox.material },
  };
}
