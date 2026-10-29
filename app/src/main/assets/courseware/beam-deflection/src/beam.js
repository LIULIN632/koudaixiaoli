/**
 * ============================================================================
 * src/beam.js —— 简支梁集中荷载：三维模型与实时更新
 * ============================================================================
 *
 * 场景里有这些可动的东西（全部由 update(d) 一处驱动）：
 *
 *   1. 梁          —— InstancedMesh：整根梁切成 N 段小方块，每段按挠度曲线
 *                    平移 + 旋转，拼起来就是一条光滑的弯曲梁（无需顶点着色器）
 *   2. 未变形参考线 —— 虚线，标出梁原来的位置，方便看出"下沉了多少"
 *   3. 支座        —— 左端固定铰（三角），右端滑动铰（三角 + 下方短横线）
 *   4. 荷载箭头    —— 红色向下，x 随 a 移动，箭头尖端始终贴住梁的（变形后）顶面
 *   5. 支座反力    —— 两端青绿色向上箭头，长度与反力大小成正比
 *   6. 弯矩图      —— 梁下方一条橙色带状图（三角弯矩图），峰值随荷载位置移动
 *   7. 三块文字标签 —— 荷载值 / 弯矩峰值 / 最大挠度，跟着对应位置走
 *
 * 坐标约定：
 *   模型在局部坐标里沿 x 从 0 排到 L（L = 8），y = 0 是梁的**中性轴**（未变形位置），
 *   挠度向下为正 ⇒ 世界坐标 y = −v(x) × 放大系数。
 *   整个 group 最后整体平移 −L/2，让模型在场景里居中。
 */

import * as THREE from 'three';
import { MODEL, deflectionAt, slopeAt, momentAt } from './params.js';

/** 梁的几何尺寸（世界单位，1 单位 ≈ 1 m） */
export const DIM = {
  L: MODEL.L, // 跨度
  depth: 0.42, // 截面高度
  width: 0.30, // 截面宽度
  N: MODEL.segments, // 分段数
  momentBaseline: -1.85, // 弯矩图基线的 y
};

const FONT_STACK = '"PingFang SC", "Microsoft YaHei", "Noto Sans SC", sans-serif';

/** 确定性伪随机（暂时没用到随机，但保留给后续加颗粒/纹理） */
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
 * 生成一块随文本更新的精灵标签
 * 画布用 320×80（比例 4:1），字号按画布宽度取，保证在屏幕上读得清
 * @param {number} w 世界宽度 @param {number} h 世界高度
 */
function createLabel(w = 2.55, h = 0.6375) {
  const W = 320;
  const H = 80;
  const canvas = document.createElement('canvas');
  canvas.width = W;
  canvas.height = H;
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
  sprite.scale.set(w, h, 1);
  sprite.renderOrder = 20;

  /**
   * 找到能塞进指定宽度的最大字号（标签宽度固定，文字长度会变）
   * @param {string} text @param {number} maxWidth @param {number} base @param {number} weight
   */
  function fitFont(text, maxWidth, base, weight) {
    let size = base;
    ctx.font = `${weight} ${size}px ${FONT_STACK}`;
    while (size > 9 && ctx.measureText(text).width > maxWidth) {
      size -= 1;
      ctx.font = `${weight} ${size}px ${FONT_STACK}`;
    }
  }

  /**
   * @param {string} title 主文字 @param {string} detail 副文字 @param {string} accent 强调色
   */
  function draw(title, detail, accent) {
    ctx.clearRect(0, 0, W, H);

    ctx.fillStyle = 'rgba(10, 15, 21, 0.78)';
    ctx.beginPath();
    const r = 15;
    ctx.moveTo(r, 4);
    ctx.arcTo(W - 4, 4, W - 4, H - 4, r);
    ctx.arcTo(W - 4, H - 4, 4, H - 4, r);
    ctx.arcTo(4, H - 4, 4, 4, r);
    ctx.arcTo(4, 4, W - 4, 4, r);
    ctx.closePath();
    ctx.fill();

    ctx.fillStyle = accent;
    ctx.fillRect(4, 4, 3, H - 8);

    ctx.textBaseline = 'middle';
    ctx.fillStyle = accent;
    fitFont(title, W - 26, 30, 600);
    ctx.fillText(title, 16, detail ? 29 : H / 2);

    if (detail) {
      ctx.fillStyle = '#c9dae8';
      fitFont(detail, W - 26, 20, 400);
      ctx.fillText(detail, 16, 58);
    }

    texture.needsUpdate = true;
  }

  return { sprite, draw };
}

/** 单个字母的圆角标（用于支座 A / B） */
function createLetter(text, color) {
  const S = 96;
  const canvas = document.createElement('canvas');
  canvas.width = S;
  canvas.height = S;
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
  sprite.scale.set(0.46, 0.46, 1);
  sprite.renderOrder = 20;

  ctx.fillStyle = 'rgba(10, 15, 21, 0.8)';
  ctx.beginPath();
  ctx.arc(S / 2, S / 2, S / 2 - 4, 0, Math.PI * 2);
  ctx.fill();
  ctx.strokeStyle = color;
  ctx.lineWidth = 3;
  ctx.stroke();

  ctx.fillStyle = color;
  ctx.font = `700 52px ${FONT_STACK}`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(text, S / 2, S / 2 + 2);
  texture.needsUpdate = true;

  return sprite;
}

/** 三角形棱柱（支座用）：底边在 y=−h，顶点在原点 */
function makeTrianglePrism(base, height, depth) {
  const shape = new THREE.Shape();
  shape.moveTo(-base / 2, -height);
  shape.lineTo(base / 2, -height);
  shape.lineTo(0, 0); // 顶点朝上，正好顶住梁底
  shape.closePath();
  const geo = new THREE.ExtrudeGeometry(shape, { depth, bevelEnabled: false });
  geo.translate(0, 0, -depth / 2);
  return geo;
}

/**
 * 箭头：尖端固定在原点，杆件长度可随反力大小变化
 * @param {'up'|'down'} dir 指向
 * @param {number} color
 * @param {number} shaftLen 初始杆长（不含箭头）
 * @param {number} shaftRadius
 * @param {number} headLen
 * @param {number} headRadius
 */
function makeArrow(dir, color, shaftLen, shaftRadius = 0.042, headLen = 0.26, headRadius = 0.11) {
  const group = new THREE.Group();
  const sign = dir === 'up' ? 1 : -1; // 尖端在原点，杆体朝 −sign 方向延伸

  const mat = new THREE.MeshStandardMaterial({
    color,
    roughness: 0.55,
    metalness: 0.1,
    emissive: new THREE.Color(color).multiplyScalar(0.18),
  });

  // 箭头（圆锥）：尖端在原点
  const head = new THREE.Mesh(new THREE.ConeGeometry(headRadius, headLen, 18), mat);
  head.rotation.z = dir === 'up' ? 0 : Math.PI; // 默认圆锥朝 +y
  head.position.y = (-sign * headLen) / 2;
  group.add(head);

  // 杆：单位高度圆柱，靠 scale.y 伸缩
  const shaft = new THREE.Mesh(new THREE.CylinderGeometry(shaftRadius, shaftRadius, 1, 14), mat);
  shaft.position.y = -sign * (headLen + shaftLen / 2);
  shaft.scale.y = shaftLen;
  group.add(shaft);

  /**
   * 改杆长：只拉伸杆，箭头大小保持不变
   * @param {number} len 总长（含箭头）
   */
  function setLength(len) {
    const body = Math.max(len - headLen, 0.02);
    shaft.scale.y = body;
    shaft.position.y = -sign * (headLen + body / 2);
  }

  setLength(headLen + shaftLen);
  return { group, setLength, material: mat };
}

/**
 * 构建整套梁模型
 * @param {THREE.Scene} scene
 */
export function buildBeam(scene) {
  const { L, depth, width, N } = DIM;

  const group = new THREE.Group();
  group.name = 'BeamModel';
  scene.add(group);

  // ======================================================================
  // 1. 梁本体：沿挠度曲线扫掠出的长方体（自定义 BufferGeometry）
  //    每帧按 v(x)、θ(x) 重写顶点，所以是真正光滑的弯梁，没有分段棱面
  // ======================================================================
  const beamMaterial = new THREE.MeshStandardMaterial({
    color: 0xb9c2cc, // 浅灰
    roughness: 0.52,
    metalness: 0.06,
    side: THREE.DoubleSide,
  });

  const stations = N + 1; // 沿梁长的采样站数
  const PER_STATION = 8; // 每站 4 个面 × 2 个顶点
  const CAP_VERTS = 8; // 两端各 4 个封口顶点
  const vertCount = stations * PER_STATION + CAP_VERTS;

  const beamPositions = new Float32Array(vertCount * 3);
  const beamNormals = new Float32Array(vertCount * 3);

  const beamGeo = new THREE.BufferGeometry();
  beamGeo.setAttribute('position', new THREE.BufferAttribute(beamPositions, 3));
  beamGeo.setAttribute('normal', new THREE.BufferAttribute(beamNormals, 3));
  beamGeo.attributes.position.setUsage(THREE.DynamicDrawUsage);
  beamGeo.attributes.normal.setUsage(THREE.DynamicDrawUsage);

  // 顶点编号：strip(0顶/1底/2前/3后) · 站 s · 边 side(0/1)
  const vIdx = (strip, s, side) => strip * stations * 2 + s * 2 + side;

  const beamIndices = [];
  for (let strip = 0; strip < 4; strip++) {
    for (let s = 0; s < N; s++) {
      const a0 = vIdx(strip, s, 0);
      const a1 = vIdx(strip, s, 1);
      const b0 = vIdx(strip, s + 1, 0);
      const b1 = vIdx(strip, s + 1, 1);
      beamIndices.push(a0, a1, b1, a0, b1, b0);
    }
  }

  // 两端封口（各 4 个顶点，2 个三角面）
  const capBase0 = stations * PER_STATION;
  const capBase1 = capBase0 + 4;
  const capOrder = [0, 1, 2, 0, 2, 3];
  for (const k of capOrder) beamIndices.push(capBase0 + k);
  for (const k of capOrder) beamIndices.push(capBase1 + k);

  beamGeo.setIndex(beamIndices);

  const beamMesh = new THREE.Mesh(beamGeo, beamMaterial);
  beamMesh.name = 'BeamMesh';
  beamMesh.castShadow = true;
  beamMesh.receiveShadow = true;
  beamMesh.frustumCulled = false; // 顶点每帧变，别让它被错误剔除
  group.add(beamMesh);

  // ======================================================================
  // 2. 未变形参考：中性轴虚线
  // ======================================================================
  const refGeo = new THREE.BufferGeometry().setFromPoints([
    new THREE.Vector3(0, 0, 0),
    new THREE.Vector3(L, 0, 0),
  ]);
  const refLine = new THREE.Line(
    refGeo,
    new THREE.LineDashedMaterial({
      color: 0x8fa5b8,
      dashSize: 0.22,
      gapSize: 0.16,
      transparent: true,
      opacity: 0.55,
    })
  );
  refLine.computeLineDistances();
  refLine.name = 'UndeformedAxis';
  group.add(refLine);

  // ======================================================================
  // 3. 支座：左端固定铰（三角）、右端滑动铰（三角 + 下方短横线）
  // ======================================================================
  const supportMaterial = new THREE.MeshStandardMaterial({
    color: 0x7e8b99,
    roughness: 0.75,
    metalness: 0.15,
  });

  const triBase = 0.62;
  const triHeight = 0.56;
  const triDepth = width * 1.25;

  // 左端 A：固定铰
  const supportA = new THREE.Mesh(makeTrianglePrism(triBase, triHeight, triDepth), supportMaterial);
  supportA.position.set(0, -depth / 2, 0);
  supportA.name = 'SupportA';
  supportA.castShadow = true;
  supportA.receiveShadow = true;
  group.add(supportA);

  // 右端 B：滑动铰 = 三角 + 两个小滚轮 + 短横线
  const supportB = new THREE.Mesh(makeTrianglePrism(triBase, triHeight, triDepth), supportMaterial);
  supportB.position.set(L, -depth / 2, 0);
  supportB.name = 'SupportB';
  supportB.castShadow = true;
  supportB.receiveShadow = true;
  group.add(supportB);

  const rollerGeo = new THREE.CylinderGeometry(0.075, 0.075, triDepth, 14);
  for (const dx of [-0.16, 0.16]) {
    const roller = new THREE.Mesh(rollerGeo, supportMaterial);
    roller.rotation.x = Math.PI / 2; // 圆柱轴向 → z
    roller.position.set(L + dx, -depth / 2 - triHeight - 0.05, 0);
    group.add(roller);
  }

  // 滑动支座下方的短横线
  const baseLine = new THREE.Mesh(
    new THREE.BoxGeometry(triBase * 1.24, 0.045, triDepth * 1.08),
    supportMaterial
  );
  baseLine.position.set(L, -depth / 2 - triHeight - 0.13, 0);
  baseLine.name = 'RollerBaseLine';
  group.add(baseLine);

  // ======================================================================
  // 4. 荷载箭头（红）＋ 支座反力箭头（青）
  // ======================================================================
  const loadArrow = makeArrow('down', 0xe8556a, 0.92, 0.048, 0.3, 0.135);
  loadArrow.group.name = 'LoadArrow';
  group.add(loadArrow.group);

  const reactionA = makeArrow('up', 0x45d6b0, 0.5, 0.034, 0.2, 0.085);
  // 放在支座前方（+z）而不是插在三角支座里，否则会被支座挡住看不见
  reactionA.group.position.set(0, -depth / 2 - 0.01, width / 2 + 0.42);
  group.add(reactionA.group);

  const reactionB = makeArrow('up', 0x45d6b0, 0.5, 0.034, 0.2, 0.085);
  reactionB.group.position.set(L, -depth / 2 - 0.01, width / 2 + 0.42);
  group.add(reactionB.group);

  // ======================================================================
  // 5. 弯矩图：带状网格（三角弯矩图）+ 上边缘亮线
  // ======================================================================
  const yBase = DIM.momentBaseline;
  const ribbonCount = 2 * (N + 1); // 每个采样点两个顶点：曲线点 + 基线点
  const ribbonPositions = new Float32Array(ribbonCount * 3);
  const ribbonGeo = new THREE.BufferGeometry();
  ribbonGeo.setAttribute('position', new THREE.BufferAttribute(ribbonPositions, 3));
  ribbonGeo.attributes.position.setUsage(THREE.DynamicDrawUsage);

  const indices = [];
  for (let i = 0; i < N; i++) {
    const c0 = i * 2;
    const c1 = (i + 1) * 2;
    indices.push(c0, c1, c1 + 1, c0, c1 + 1, c0 + 1);
  }
  ribbonGeo.setIndex(indices);

  const momentMaterial = new THREE.MeshBasicMaterial({
    color: 0xf0a35e,
    transparent: true,
    opacity: 0.30,
    side: THREE.DoubleSide,
    depthWrite: false,
  });
  const momentRibbon = new THREE.Mesh(ribbonGeo, momentMaterial);
  momentRibbon.name = 'MomentDiagram';
  momentRibbon.renderOrder = 2;
  group.add(momentRibbon);

  // 弯矩图曲线（亮线）
  const momentCurvePos = new Float32Array((N + 1) * 3);
  const momentCurveGeo = new THREE.BufferGeometry();
  momentCurveGeo.setAttribute('position', new THREE.BufferAttribute(momentCurvePos, 3));
  momentCurveGeo.attributes.position.setUsage(THREE.DynamicDrawUsage);
  const momentLine = new THREE.Line(
    momentCurveGeo,
    new THREE.LineBasicMaterial({ color: 0xffc07a, transparent: true, opacity: 0.95 })
  );
  momentLine.name = 'MomentCurve';
  group.add(momentLine);

  // 弯矩图基线（虚线）
  const momentBaseGeo = new THREE.BufferGeometry().setFromPoints([
    new THREE.Vector3(0, yBase, 0),
    new THREE.Vector3(L, yBase, 0),
  ]);
  const momentBaseLine = new THREE.Line(
    momentBaseGeo,
    new THREE.LineDashedMaterial({
      color: 0x8fa5b8,
      dashSize: 0.14,
      gapSize: 0.1,
      transparent: true,
      opacity: 0.4,
    })
  );
  momentBaseLine.computeLineDistances();
  group.add(momentBaseLine);

  // 弯矩峰值标记点
  const momentPeakDot = new THREE.Mesh(
    new THREE.SphereGeometry(0.055, 12, 8),
    new THREE.MeshBasicMaterial({ color: 0xffd7a8 })
  );
  momentPeakDot.name = 'MomentPeak';
  group.add(momentPeakDot);

  // ======================================================================
  // 6. 最大挠度标记：竖虚线 + 小点
  // ======================================================================
  const deflGuidePos = new Float32Array(6);
  const deflGuideGeo = new THREE.BufferGeometry();
  deflGuideGeo.setAttribute('position', new THREE.BufferAttribute(deflGuidePos, 3));
  deflGuideGeo.attributes.position.setUsage(THREE.DynamicDrawUsage);
  const deflGuide = new THREE.Line(
    deflGuideGeo,
    new THREE.LineDashedMaterial({
      color: 0x7fd8ff,
      dashSize: 0.09,
      gapSize: 0.07,
      transparent: true,
      opacity: 0.7,
    })
  );
  deflGuide.name = 'MaxDeflectionGuide';
  group.add(deflGuide);
  const deflDot = new THREE.Mesh(
    new THREE.SphereGeometry(0.05, 12, 8),
    new THREE.MeshBasicMaterial({ color: 0x9fe4ff })
  );
  group.add(deflDot);

  // ======================================================================
  // 7. 文字标签
  // ======================================================================
  const loadLabel = createLabel(2.6, 0.65);
  group.add(loadLabel.sprite);

  const momentLabel = createLabel(3.05, 0.7625);
  group.add(momentLabel.sprite);

  const deflLabel = createLabel(3.15, 0.7875);
  group.add(deflLabel.sprite);

  // 支座编号 A / B：放在支座外侧，避免压住三角支座
  const labelA = createLetter('A', '#9fb3c6');
  labelA.position.set(-0.88, -depth / 2 - triHeight * 0.72, 0);
  group.add(labelA);

  const labelB = createLetter('B', '#9fb3c6');
  labelB.position.set(L + 0.88, -depth / 2 - triHeight * 0.72, 0);
  group.add(labelB);

  // 整体居中：模型建在 x ∈ [0, L]，平移后关于原点对称
  group.position.x = -L / 2;

  // ======================================================================
  // 8. 核心更新
  // ======================================================================
  const amp = MODEL.amp; // 挠度放大系数
  const mScale = MODEL.momentScale; // 弯矩图纵坐标比例

  /**
   * @param {ReturnType<import('./params.js').computeDerived>} d
   */
  function update(d) {
    // ---------------- 8.1 梁：沿挠度曲线扫掠出长方体 ----------------
    // 每个采样点先求"中性轴上的点 + 切线方向"，再沿法向拉出截面高度。
    // 这样 96 个站点的数据在视觉上是一条连续光滑的弯梁。
    const halfDepth = depth / 2;
    const halfWidth = width / 2;
    const w2 = halfWidth;

    for (let s = 0; s <= N; s++) {
      const x = (s / N) * L;
      const sag = -deflectionAt(x, d) * amp; // 中性轴的世界 y（向下为负）
      const slope = -slopeAt(x, d) * amp; // 世界坐标里的 dy/dx

      // 切线单位向量
      const tlen = Math.hypot(1, slope);
      const tx = 1 / tlen;
      const ty = slope / tlen;
      // 法向（截面的高度方向）= 切线逆时针转 90°
      const nx = -ty;
      const ny = tx;

      const topX = x + nx * halfDepth;
      const topY = sag + ny * halfDepth;
      const botX = x - nx * halfDepth;
      const botY = sag - ny * halfDepth;

      // 顶面（strip 0）
      let i0 = vIdx(0, s, 0) * 3;
      beamPositions[i0] = topX;
      beamPositions[i0 + 1] = topY;
      beamPositions[i0 + 2] = w2;
      beamPositions[i0 + 3] = topX;
      beamPositions[i0 + 4] = topY;
      beamPositions[i0 + 5] = -w2;
      beamNormals[i0] = nx;
      beamNormals[i0 + 1] = ny;
      beamNormals[i0 + 2] = 0;
      beamNormals[i0 + 3] = nx;
      beamNormals[i0 + 4] = ny;
      beamNormals[i0 + 5] = 0;

      // 底面（strip 1）
      i0 = vIdx(1, s, 0) * 3;
      beamPositions[i0] = botX;
      beamPositions[i0 + 1] = botY;
      beamPositions[i0 + 2] = w2;
      beamPositions[i0 + 3] = botX;
      beamPositions[i0 + 4] = botY;
      beamPositions[i0 + 5] = -w2;
      beamNormals[i0] = -nx;
      beamNormals[i0 + 1] = -ny;
      beamNormals[i0 + 2] = 0;
      beamNormals[i0 + 3] = -nx;
      beamNormals[i0 + 4] = -ny;
      beamNormals[i0 + 5] = 0;

      // 前立面 z = +w2（strip 2）
      i0 = vIdx(2, s, 0) * 3;
      beamPositions[i0] = topX;
      beamPositions[i0 + 1] = topY;
      beamPositions[i0 + 2] = w2;
      beamPositions[i0 + 3] = botX;
      beamPositions[i0 + 4] = botY;
      beamPositions[i0 + 5] = w2;
      beamNormals[i0] = 0;
      beamNormals[i0 + 1] = 0;
      beamNormals[i0 + 2] = 1;
      beamNormals[i0 + 3] = 0;
      beamNormals[i0 + 4] = 0;
      beamNormals[i0 + 5] = 1;

      // 后立面 z = −w2（strip 3）
      i0 = vIdx(3, s, 0) * 3;
      beamPositions[i0] = topX;
      beamPositions[i0 + 1] = topY;
      beamPositions[i0 + 2] = -w2;
      beamPositions[i0 + 3] = botX;
      beamPositions[i0 + 4] = botY;
      beamPositions[i0 + 5] = -w2;
      beamNormals[i0] = 0;
      beamNormals[i0 + 1] = 0;
      beamNormals[i0 + 2] = -1;
      beamNormals[i0 + 3] = 0;
      beamNormals[i0 + 4] = 0;
      beamNormals[i0 + 5] = -1;

      // 两端封口（只在第一站和最后一站写）
      if (s === 0 || s === N) {
        const cx = s === 0 ? capBase0 : capBase1;
        const cx3 = cx * 3;
        // 顶点顺序：顶+、顶−、底−、底+
        beamPositions[cx3] = topX;
        beamPositions[cx3 + 1] = topY;
        beamPositions[cx3 + 2] = w2;
        beamPositions[cx3 + 3] = topX;
        beamPositions[cx3 + 4] = topY;
        beamPositions[cx3 + 5] = -w2;
        beamPositions[cx3 + 6] = botX;
        beamPositions[cx3 + 7] = botY;
        beamPositions[cx3 + 8] = -w2;
        beamPositions[cx3 + 9] = botX;
        beamPositions[cx3 + 10] = botY;
        beamPositions[cx3 + 11] = w2;

        const sgn = s === 0 ? -1 : 1; // 左端朝 −x，右端朝 +x
        for (let k = 0; k < 4; k++) {
          const n3 = (cx + k) * 3;
          beamNormals[n3] = sgn * tx;
          beamNormals[n3 + 1] = sgn * ty;
          beamNormals[n3 + 2] = 0;
        }
      }
    }

    beamGeo.attributes.position.needsUpdate = true;
    beamGeo.attributes.normal.needsUpdate = true;

    // ---------------- 8.2 荷载箭头：贴住变形后的梁顶面 ----------------
    const loadX = d.a;
    const loadSag = -deflectionAt(loadX, d) * amp; // 荷载点梁中性轴的世界 y
    const tipY = loadSag + depth / 2; // 梁顶面
    loadArrow.group.position.set(loadX, tipY, 0);

    loadLabel.sprite.position.set(loadX, tipY + 1.68, 0);
    loadLabel.draw('P 荷载', `${d.P.toFixed(0)} kN · a = ${d.a.toFixed(2)} m`, '#ff8a98');

    // ---------------- 8.3 支座反力：长度 ∝ 反力大小 ----------------
    // 以 100 kN 对应 1.15 世界单位做统一标尺（两端可以直观比大小）
    const rScale = 1.15 / 100;
    reactionA.setLength(0.16 + d.RA * rScale);
    reactionB.setLength(0.16 + d.RB * rScale);

    // ---------------- 8.4 弯矩图 ----------------
    for (let i = 0; i <= N; i++) {
      const x = (i / N) * L;
      const m = momentAt(x, d);
      const yCurve = yBase - m * mScale; // 正弯矩画在下方（受拉侧）
      const c = i * 2;
      ribbonPositions[c * 3] = x;
      ribbonPositions[c * 3 + 1] = yCurve;
      ribbonPositions[c * 3 + 2] = 0;
      ribbonPositions[(c + 1) * 3] = x;
      ribbonPositions[(c + 1) * 3 + 1] = yBase;
      ribbonPositions[(c + 1) * 3 + 2] = 0;

      momentCurvePos[i * 3] = x;
      momentCurvePos[i * 3 + 1] = yCurve;
      momentCurvePos[i * 3 + 2] = 0;
    }
    ribbonGeo.attributes.position.needsUpdate = true;
    ribbonGeo.computeBoundingSphere();
    momentCurveGeo.attributes.position.needsUpdate = true;
    momentCurveGeo.computeBoundingSphere();

    // 峰值点 + 标签
    const peakY = yBase - d.Mmax * mScale;
    momentPeakDot.position.set(d.xMmax, peakY, 0);
    momentLabel.sprite.position.set(d.xMmax, Math.max(peakY - 0.52, -3.28), 0);
    momentLabel.draw(
      `M max = ${d.Mmax.toFixed(1)} kN·m`,
      `x = ${d.xMmax.toFixed(2)} m · 恒在荷载作用点`,
      '#ffb877'
    );

    // ---------------- 8.5 最大挠度标记 ----------------
    const vx = d.xVmax;
    const vy = -d.vmax * amp; // 中性轴处
    deflGuidePos[0] = vx;
    deflGuidePos[1] = 0; // 从原位置
    deflGuidePos[2] = 0;
    deflGuidePos[3] = vx;
    deflGuidePos[4] = vy;
    deflGuidePos[5] = 0;
    deflGuideGeo.attributes.position.needsUpdate = true;
    deflGuide.computeLineDistances();
    deflDot.position.set(vx, vy - depth / 2 - 0.06, 0);

    deflLabel.sprite.position.set(vx, vy - depth / 2 - 0.66, 0);
    deflLabel.draw(
      `v max = ${d.vmaxMm.toFixed(2)} mm`,
      `x = ${d.xVmax.toFixed(2)} m · 变形放大 ${amp}×`,
      '#8fdcff'
    );
  }

  return {
    group,
    update,
    /** 梁的分段实例网格（外部可以直接改 instanceMatrix 做实验） */
    meshes: {
      beam: beamMesh,
      loadArrow: loadArrow.group,
      reactionA: reactionA.group,
      reactionB: reactionB.group,
      momentRibbon,
      momentCurve: momentLine,
      momentPeak: momentPeakDot,
      deflectionDot: deflDot,
    },
    materials: {
      beam: beamMaterial,
      support: supportMaterial,
      moment: momentMaterial,
      load: loadArrow.material,
      reaction: reactionA.material,
    },
    labels: { load: loadLabel, moment: momentLabel, deflection: deflLabel },
  };
}
