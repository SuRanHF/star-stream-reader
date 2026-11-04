import * as THREE from 'three';
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/examples/jsm/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/examples/jsm/postprocessing/UnrealBloomPass.js';
import { OutputPass } from 'three/examples/jsm/postprocessing/OutputPass.js';
import { CHAPTER_NAMES, READERS } from '@/data/readers';
import { IGNITE_AT, MINE, PRO_DUR, setHover, story } from '@/store';
import { buildAtlas, buildRows, clamp01, lerp, LUT_N, NODE_S, pathAt, rr, rand, SEP, smooth, SWAY_GLSL, UP } from './streams';

// 星流广播的夜空：每位读者的故事是一股飞行的文字流，从同一个第一章出发，一直往外写
export async function createStarScene(canvas: HTMLCanvasElement) {
  const mobile = innerWidth < 760;
  const motion = story.reduce ? 0.15 : 1;
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
  let PIX = Math.min(devicePixelRatio, mobile ? 1.5 : 1.75);
  renderer.setPixelRatio(PIX);
  renderer.setSize(innerWidth, innerHeight);
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 0.9;

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(mobile ? 60 : 42, innerWidth / innerHeight, 0.1, 2400);
  const WHITE = 0xece9e2, GOLD = 0xd6bd87;

  const glow = (() => {
    const c = document.createElement('canvas'); c.width = c.height = 64;
    const g = c.getContext('2d')!, gr = g.createRadialGradient(32, 32, 0, 32, 32, 32);
    gr.addColorStop(0, 'rgba(255,255,255,1)'); gr.addColorStop(0.2, 'rgba(255,255,255,.5)'); gr.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = gr; g.fillRect(0, 0, 64, 64);
    return new THREE.CanvasTexture(c);
  })();
  const additive = { transparent: true, depthWrite: false, blending: THREE.AdditiveBlending };

  // —— 夜空：近黑的天球，斜斜一条很淡的光带 ——
  scene.add(new THREE.Mesh(new THREE.SphereGeometry(1200, 32, 16), new THREE.ShaderMaterial({
    side: THREE.BackSide, depthWrite: false,
    vertexShader: `varying vec3 vW; void main(){ vW = normalize(position); gl_Position = projectionMatrix*modelViewMatrix*vec4(position,1.); }`,
    fragmentShader: `varying vec3 vW; void main(){
      float b = dot(vW, normalize(vec3(.35, 1., .25)));
      vec3 c = vec3(.016,.016,.022);
      c += vec3(.09,.085,.08) * exp(-pow(b*4.,2.)) * .3;
      c += vec3(.06,.055,.05) * exp(-pow(b*13.,2.)) * .35;
      gl_FragColor = vec4(c,1.);
    }`,
  })));

  // —— 星流广播的观众：满天的星座，随着观看人数一点点亮起 ——
  const watchers = (() => {
    const n = mobile ? 900 : 1600, pos = new Float32Array(n * 3), order = new Float32Array(n);
    const v = new THREE.Vector3();
    for (let i = 0; i < n; i++) {
      v.set(rr(-1, 1), rr(-1, 1), rr(-1, 1)).normalize().multiplyScalar(rr(600, 900));
      pos.set([v.x, v.y, v.z], i * 3);
      order[i] = Math.pow(rand(), 2.2);
    }
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    g.setAttribute('aOrder', new THREE.BufferAttribute(order, 1));
    const mat = new THREE.ShaderMaterial({
      ...additive,
      uniforms: { uOn: { value: 0 }, uTime: { value: 0 }, uPix: { value: PIX } },
      vertexShader: `attribute float aOrder; uniform float uOn; uniform float uTime; uniform float uPix; varying float vA;
        void main(){ vA = smoothstep(aOrder, aOrder + .04, uOn) * (.55 + .45*sin(uTime*1.3 + aOrder*90.));
          vec4 mv = modelViewMatrix*vec4(position,1.); gl_PointSize = (aOrder < .02 ? 8. : 3.5) * uPix; gl_Position = projectionMatrix*mv; }`,
      fragmentShader: `varying float vA; void main(){ float d = length(gl_PointCoord-.5); float a = smoothstep(.5,0.,d); gl_FragColor = vec4(vec3(.95,.92,.85)*a*vA,1.); }`,
    });
    const points = new THREE.Points(g, mat); points.frustumCulled = false; scene.add(points);
    // 几组连线，让最早到场的几位「星座」看得出形状
    const lines: { m: THREE.LineBasicMaterial; sm: THREE.PointsMaterial; at: number }[] = [];
    for (let c = 0; c < 8; c++) {
      let p = new THREE.Vector3(rr(-1, 1), rr(-0.2, 1), rr(-1, 1)).normalize().multiplyScalar(700);
      const pts: THREE.Vector3[] = [];
      for (let k = 0; k < 5; k++) { pts.push(p.clone()); p = p.clone().add(new THREE.Vector3(rr(-40, 40), rr(-30, 30), rr(-40, 40))); }
      const m = new THREE.LineBasicMaterial({ color: WHITE, ...additive, opacity: 0 });
      scene.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(pts), m));
      const sm = new THREE.PointsMaterial({ size: 9, map: glow, color: 0xfff6e2, ...additive, opacity: 0 });
      scene.add(new THREE.Points(new THREE.BufferGeometry().setFromPoints(pts), sm));
      lines.push({ m, sm, at: 0.05 + c * 0.1 });
    }
    return { mat, lines };
  })();

  const { rows, readerRow, flashRow, mineRow, meets, fStopS } = buildRows(mobile);
  const atlas = await buildAtlas(rows, mobile);

  // 航线采样放进一张浮点贴图，着色器按行取
  const lutData = new Float32Array(LUT_N * rows.length * 4);
  rows.forEach((r, j) => r.pts.forEach((p, i) => lutData.set([p.x, p.y, p.z, r.ph], (j * LUT_N + i) * 4)));
  const lutTex = new THREE.DataTexture(lutData, LUT_N, rows.length, THREE.RGBAFormat, THREE.FloatType);
  lutTex.needsUpdate = true;

  // —— 文字流：每个字是一块朝向镜头的小方片，沿航线一直往外飞 ——
  const rowLv = new Float32Array(rows.length), rowSpread = new Float32Array(rows.length);
  const shared = {
    uLut: { value: lutTex }, uAtlas: { value: atlas.texture }, uTime: { value: 0 },
    uRowLv: { value: rowLv }, uRowSpread: { value: rowSpread }, uBase: { value: 0.35 }, uBg: { value: 0.2 },
    uN: { value: LUT_N }, uGrid: { value: new THREE.Vector2(atlas.cols, atlas.rows) },
  };
  {
    const aRow: number[] = [], aU: number[] = [], aGlyph: number[] = [], aSize: number[] = [], aSpeed: number[] = [], aReader: number[] = [];
    rows.forEach((r, j) => {
      let len = 0; for (let i = 1; i < LUT_N; i++) len += r.pts[i].distanceTo(r.pts[i - 1]);
      const n = Math.floor(len / (r.size * 1.15));
      const txt = r.text + SEP + SEP;   // 一轮念完空一大段，「待续」不会和下一轮的「第一章」连成一句
      for (let i = 0; i < n; i++) {
        // 文字流在画面右半边时，越远的字越靠左；倒过来排，句子从左往右读才是顺的
        const g = atlas.index.get(txt[(r.rev ? n - 1 - i : i) % txt.length]);
        if (g === undefined) continue;   // 空格只占位
        aRow.push(j); aU.push(i / n); aGlyph.push(g); aSize.push(r.size); aSpeed.push(r.speed); aReader.push(r.reader);
      }
    });
    const quad = new THREE.PlaneGeometry(1, 1);
    const g = new THREE.InstancedBufferGeometry();
    g.index = quad.index; g.setAttribute('position', quad.attributes.position); g.setAttribute('uv', quad.attributes.uv);
    const put = (name: string, arr: number[]) => g.setAttribute(name, new THREE.InstancedBufferAttribute(new Float32Array(arr), 1));
    put('aRow', aRow); put('aU', aU); put('aGlyph', aGlyph); put('aSize', aSize); put('aSpeed', aSpeed); put('aReader', aReader);
    g.instanceCount = aRow.length;
    const mat = new THREE.ShaderMaterial({
      ...additive, uniforms: shared,
      vertexShader: `#define ROWS ${rows.length}
        uniform sampler2D uLut; uniform float uTime; uniform float uRowLv[ROWS]; uniform float uRowSpread[ROWS]; uniform float uBase; uniform float uBg; uniform float uN; uniform vec2 uGrid;
        attribute float aRow; attribute float aU; attribute float aGlyph; attribute float aSize; attribute float aSpeed; attribute float aReader;
        varying vec2 vUv; varying float vA; varying float vGold;
        ${SWAY_GLSL}
        vec4 lut(float s){
          float x = s * (uN - 1.), i0 = floor(x);
          vec4 a = texelFetch(uLut, ivec2(int(i0), int(aRow)), 0), b = texelFetch(uLut, ivec2(int(min(i0 + 1., uN - 1.)), int(aRow)), 0);
          return mix(a, b, x - i0);
        }
        void main(){
          float s = fract(aU + uTime * aSpeed);
          vec4 L = lut(s);
          vec4 mv = modelViewMatrix * vec4(L.xyz + sway(s, uTime, L.w), 1.);
          int ri = int(aRow + .5);
          float lv = uRowLv[ri];
          float dist = -mv.z;
          float a = smoothstep(0., .015, s) * (1. - smoothstep(.965, 1., s));
          a *= 1. - smoothstep(uRowSpread[ri] - .03, uRowSpread[ri], s);   // 从原点一点点流出来
          a *= smoothstep(6., 14., dist) * exp(-dist * .0035);   // 贴脸的字淡掉，不然会糊满屏
          a *= mix(smoothstep(20., 55., dist), 1., lv);          // 别人的故事从镜头边上掠过时，不抢正在讲的那一股
          a *= (aReader < -.5 ? uBg : uBase) + lv * 1.1;
          vA = a; vGold = lv;
          mv.xy += position.xy * aSize;
          float col = mod(aGlyph, uGrid.x), row = floor(aGlyph / uGrid.x);
          vUv = vec2((col + uv.x) / uGrid.x, 1. - (row + 1. - uv.y) / uGrid.y);
          gl_Position = projectionMatrix * mv;
        }`,
      fragmentShader: `uniform sampler2D uAtlas; varying vec2 vUv; varying float vA; varying float vGold;
        void main(){
          float m = texture2D(uAtlas, vUv).a;
          vec3 col = mix(vec3(.92, .9, .86), vec3(1., .85, .58), vGold * .75);
          gl_FragColor = vec4(col * m * vA, 1.);
        }`,
    });
    const mesh = new THREE.Mesh(g, mat); mesh.frustumCulled = false; scene.add(mesh);
  }

  // —— 原点：所有人的第一章；开场时在这里点亮，文字流从这里涌出 ——
  const sprite = (color: number, map: THREE.Texture = glow) => { const s = new THREE.Sprite(new THREE.SpriteMaterial({ map, color, ...additive, opacity: 0 })); scene.add(s); return s; };
  const origin = sprite(0xffe2a8); origin.scale.set(6, 6, 1);
  const flash = sprite(0xfff1d6);
  const ringTex = (() => {
    const c = document.createElement('canvas'); c.width = c.height = 256;
    const g = c.getContext('2d')!; g.strokeStyle = '#fff'; g.lineWidth = 2;
    g.beginPath(); g.arc(128, 128, 120, 0, Math.PI * 2); g.stroke();
    return new THREE.CanvasTexture(c);
  })();
  const rings = [0, 0.35].map((d) => ({ sp: sprite(GOLD, ringTex), d }));

  // —— 每条读者航线上的几章：一颗星，加一个标签 ——
  function labelSprite(name: string, kind: 'end' | 'root' | 'meet' | 'node') {
    const c = document.createElement('canvas'); c.width = 512; c.height = 112;
    const g = c.getContext('2d')!; g.textBaseline = 'middle';
    g.fillStyle = kind === 'end' || kind === 'meet' ? '#d6bd87' : '#ece9e2';
    g.font = '400 34px "Noto Serif SC","PingFang SC","Microsoft YaHei",serif';
    g.fillRect(0, 40, 18, 1.5);
    g.fillText(name, 28, 40);
    g.font = '400 20px "IBM Plex Mono",Consolas,monospace';
    g.fillStyle = kind === 'end' ? '#d6bd87' : '#7d7a73';
    const sub = { end: '待续 · 还在写', root: 'START · 所有人的第一章', meet: '两个人的故事在这里交汇', node: '' }[kind];
    if (sub) g.fillText(sub, 30, 86);
    const tex = new THREE.CanvasTexture(c); tex.anisotropy = 4;
    const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true, depthWrite: false, opacity: 0 }));
    sp.center.set(0, 0.62); scene.add(sp); return sp;
  }
  const nodes = readerRow.flatMap((row, k) => NODE_S.map((s, n) => {
    const end = n === NODE_S.length - 1;
    const star = sprite(end ? 0xffd89a : 0xfff3da); star.scale.setScalar(end ? 3 : 1.3);
    return { k, row, s, end, star, label: labelSprite(CHAPTER_NAMES[k][n], end ? 'end' : 'node') };
  }));
  const meetMarks = meets.map((m) => {
    const star = sprite(0xffd89a); star.scale.setScalar(2.6);
    return { ...m, star, label: labelSprite(m.name, 'meet') };
  });
  const rootLabel = labelSprite('第一章 · 第一个痕迹', 'root');
  // 访客文字流的最前端：一颗一直在闪的星，还在写
  const pen = sprite(0xffd89a); pen.scale.setScalar(3.2);
  const penLabel = labelSprite('第二章 · 由你来写', 'end');
  const labelScale = (sp: THREE.Sprite) => { const s = camera.position.distanceTo(sp.position) * (mobile ? 0.17 : 0.13); sp.scale.set(s, s * 0.22, 1); };

  // —— 镜头：星河是大厅；选完或点进一位读者就贴着那股文字流；往下的几屏，各换一个角度看整片星河 ——
  const R = mobile ? 1.3 : 1;
  const P = new THREE.Vector3(), Q = new THREE.Vector3(), Q2 = new THREE.Vector3(), tan = new THREE.Vector3(), side = new THREE.Vector3(), tmp = new THREE.Vector3();
  interface Shot { pos: THREE.Vector3; look: THREE.Vector3; side: number }
  const mainRow = (i: number) => (i < 4 ? readerRow[i] : i < MINE ? flashRow[i - 4] : mineRow[Math.max(0, story.choice)]);
  function besideShot(row: number, s: number, time: number, out: Shot) {
    pathAt(rows[row], s, time, P); pathAt(rows[row], s + 0.015, time, Q); pathAt(rows[row], s - 0.015, time, Q2);
    tan.copy(Q).sub(Q2).normalize(); side.set(P.x, 0, P.z).normalize();
    out.pos.copy(P).addScaledVector(side, 17 * R).addScaledVector(UP, 5).addScaledVector(tan, -4);
    out.look.copy(P).addScaledVector(tan, 5);
    out.side = mobile ? 0 : -1;
  }
  function readerShot(i: number, time: number, out: Shot) {
    if (i < 4) {
      // 镜头在航线外侧（背对原点那一边）：前面的路往里拐，文字流一直留在面板对面
      const row = rows[readerRow[i]], s = 0.45;
      pathAt(row, s, time, P); pathAt(row, s + 0.02, time, Q);
      tan.copy(Q).sub(P).normalize(); side.set(P.x, 0, P.z).normalize();
      out.pos.copy(P).addScaledVector(tan, -9 * R).addScaledVector(side, 15 * R).addScaledVector(UP, 5);
      pathAt(row, s + 0.05, time, out.look);
      out.side = mobile ? 0 : (i % 2 ? 1 : -1);
    } else if (i < MINE) besideShot(flashRow[i - 4], fStopS[i - 4], time, out);
    else {
      besideShot(mineRow[Math.max(0, story.choice)], clamp01(mineSpread - 0.14) * 0.5 + 0.06, time, out);
      if (!mobile) out.side = -1.35;   // 他的字比别人大，再往左让一点，别压到旁边的卡片
    }
  }
  function nodeShot(n: number, time: number, out: Shot) {
    const a = time * 0.01;
    if (n === 0) {
      // 大厅：斜着俯看整条星河，星河在画面上半部，标题在下面
      const az = 1.2 + a, r = mobile ? 185 : 175;
      out.pos.set(Math.cos(az) * r, mobile ? 125 : 108, Math.sin(az) * r);
      out.look.set(0, mobile ? -48 : -34, 0); out.side = 0;
    } else if (n === 1) {
      const az = 0.6 + a, r = 210 * R;
      out.pos.set(Math.cos(az) * r, 34, Math.sin(az) * r);
      out.look.set(0, -40, 0); out.side = mobile ? 0 : 0.6;
    } else if (n === 2) {
      out.pos.set(Math.sin(a) * 30, 250 * R, Math.cos(a) * 30);
      out.look.set(0, 0, 0); out.side = 0;
    } else if (n === 3) {
      const az = 2.6 + a, r = 150 * R;
      out.pos.set(Math.cos(az) * r, -26, Math.sin(az) * r);
      out.look.set(0, 16, 0); out.side = 0;
    } else {
      // 最后一屏：镜头落到第一章旁边，所有文字流从身边涌出去
      const az = 4 + a * 2;
      out.pos.set(Math.cos(az) * 24, 7, Math.sin(az) * 24);
      out.look.set(0, 3, 0); out.side = 0;
    }
  }
  const cur: Shot = { pos: new THREE.Vector3(), look: new THREE.Vector3(), side: 0 }, tgt: Shot = { pos: new THREE.Vector3(), look: new THREE.Vector3(), side: 0 };
  let camReady = false;
  function placeCamera(dt: number, time: number, dolly: number) {
    if (story.phase === 'chosen') readerShot(MINE, time, tgt);
    else if (story.focus >= 0) readerShot(story.focus, time, tgt);
    else nodeShot(story.node, time, tgt);
    tgt.pos.multiplyScalar(dolly);
    // 飞向下一个机位：位置跟得慢一点、视线跟得快一点，转场是一段有弧度的飞行
    const snap = !camReady || story.reduce;
    const kp = snap ? 1 : 1 - Math.exp(-dt * 1.5), kl = snap ? 1 : 1 - Math.exp(-dt * 2.4);
    cur.pos.lerp(tgt.pos, kp); cur.look.lerp(tgt.look, kl); cur.side = lerp(cur.side, tgt.side, kp);
    camReady = true;
    camera.position.copy(cur.pos);
    camera.lookAt(cur.look);
    const w = innerWidth, hh = innerHeight, close = story.focus >= 0 || story.phase === 'chosen';
    camera.setViewOffset(w, hh, -cur.side * w * 0.2, mobile && close ? hh * 0.2 : 0, w, hh);
  }

  // —— 悬停拾取：把每位读者那股主文字流投到屏幕上，找离鼠标最近的一股 ——
  let elapsed = 0;
  function distTo(i: number, x: number, y: number) {
    const row = rows[mainRow(i)];
    let bd = Infinity;
    for (let s = 0.04; s < 0.98; s += 0.02) {
      pathAt(row, s, elapsed, tmp).project(camera);
      if (tmp.z > 1) continue;
      bd = Math.min(bd, Math.hypot((tmp.x + 1) / 2 * innerWidth - x, (1 - tmp.y) / 2 * innerHeight - y));
    }
    return bd;
  }
  // keep：正在亮着的那位。它的范围放宽一些，鼠标不动时卡片不会因为文字流漂动而闪
  function pick(x: number, y: number, keep = -1) {
    if (keep >= 0 && distTo(keep, x, y) < 48) return keep;
    let best = -1, bd = mobile ? 36 : 26;
    const n = READERS.length + (story.choice >= 0 ? 1 : 0);
    for (let i = 0; i < n; i++) {
      const d = distTo(i, x, y);
      if (d < bd) { bd = d; best = i; }
    }
    return best;
  }
  let mouseX = -1, mouseY = -1, frame = 0;
  addEventListener('pointermove', (e) => { if (e.pointerType === 'mouse') { mouseX = e.clientX; mouseY = e.clientY; } });
  document.documentElement.addEventListener('mouseleave', () => { mouseX = -1; setHover(-1, 0, 0); });

  const composer = new EffectComposer(renderer);
  composer.addPass(new RenderPass(scene, camera));
  composer.addPass(new UnrealBloomPass(new THREE.Vector2(innerWidth / 2, innerHeight / 2), 0.55, 0.5, 0.3));
  composer.addPass(new OutputPass());
  addEventListener('resize', () => {
    camera.aspect = innerWidth / innerHeight; camera.updateProjectionMatrix();
    renderer.setSize(innerWidth, innerHeight); composer.setSize(innerWidth, innerHeight);
  });

  // 相遇、同行的另一位跟着半亮
  const partner = new Map<number, number>();
  meets.forEach((m) => { partner.set(4 + m.a, 4 + m.b); partner.set(4 + m.b, 4 + m.a); });

  const clock = new THREE.Clock();
  // 直达某一屏、或选择「减少动态效果」时，一打开就是满的
  let sinceIgnite = story.phase === 'site' || story.phase === 'chosen' || story.reduce ? 99 : 0;
  let mineSpread = story.reduce ? 1.04 : 0, slowFrames = 0, measured = 0;
  function render() {
    const raw = clock.getDelta();
    // 开头几秒量一下帧率：设备吃力就把渲染分辨率降下来，保流畅
    if (measured < 90) {
      measured++; if (raw > 1 / 28) slowFrames++;
      if (measured === 90 && slowFrames > 45 && PIX > 1) { PIX = 1; renderer.setPixelRatio(1); composer.setPixelRatio(1); }
    }
    const dt = Math.min(raw, 0.05) * motion;
    elapsed += dt;
    const pt = story.proT, inOpening = story.phase === 'intro' || story.phase === 'prologue';

    // 开场时间线：观众进场 → 第一章点亮 → 文字流涌出
    const watchOn = inOpening ? smooth((pt - 1.8) / 3) : 1;
    // 点亮之后按自己的时钟走：跳过开场时也接得上，不会一下子跳满
    if (!inOpening || pt >= IGNITE_AT) sinceIgnite += Math.min(raw, 0.05);
    const ignite = smooth(sinceIgnite / 0.6);
    const sp = clamp01((sinceIgnite - 0.2) / 3);
    const spread = 1.04 * (1 - (1 - sp) * (1 - sp));
    const dolly = inOpening ? lerp(1.5, 1, smooth(pt / PRO_DUR)) : 1;
    if (story.choice >= 0) mineSpread = Math.min(1.04, mineSpread + Math.min(raw, 0.05) * 0.17);

    // 谁亮：选完时、进入时、悬停时那一位亮起来，其余压暗；访客自己的那一股在星河里一直比别人亮
    const chosen = story.phase === 'chosen', site = story.phase === 'site', hub = site && story.node === 0 && story.focus < 0;
    const lit = chosen ? MINE : story.focus >= 0 ? story.focus : hub ? story.hover : -1;
    const litReader = lit === MINE ? 16 + story.choice : lit, half = partner.get(lit) ?? -99;
    const mineReader = 16 + story.choice;
    const baseT = story.phase === 'choice' ? 0.2 : chosen || story.focus >= 0 ? 0.14 : hub ? (lit >= 0 ? 0.2 : 0.42) : 0.32;
    const bgT = story.phase === 'choice' ? 0.12 : chosen || story.focus >= 0 ? 0.08 : hub ? (lit >= 0 ? 0.12 : 0.26) : 0.24;
    const k = 1 - Math.exp(-dt * 5);
    shared.uBase.value += (baseT - shared.uBase.value) * k;
    shared.uBg.value += (bgT - shared.uBg.value) * k;
    rows.forEach((r, j) => {
      const isMine = r.reader >= 16;
      const t = lit >= 0 && r.reader === litReader ? 1 : r.reader === half ? 0.45 : isMine && r.reader === mineReader ? (hub ? 0.8 : 0.3) : 0;
      rowLv[j] += (t - rowLv[j]) * k;
      rowSpread[j] = !isMine ? spread : r.reader === mineReader ? mineSpread : 0;
    });
    shared.uTime.value = elapsed;

    placeCamera(dt, elapsed, dolly);

    watchers.mat.uniforms.uOn.value = watchOn;
    watchers.mat.uniforms.uTime.value = elapsed;
    watchers.lines.forEach((l) => { const o = smooth((watchOn - l.at) / 0.15); l.m.opacity = 0.25 * o; l.sm.opacity = 0.9 * o; });

    // 选完时镜头贴着原点出发，光团压暗一点，别盖住他刚写下的字
    origin.material.opacity = ignite * (0.75 + 0.25 * Math.sin(elapsed * 1.6)) * (chosen ? 0.4 : 1);
    const fl = clamp01(sinceIgnite / 1.2);
    flash.material.opacity = fl > 0 && fl < 1 ? Math.sin(fl * Math.PI) : 0;
    flash.scale.setScalar(6 + fl * 40);
    rings.forEach((rg) => {
      const q = clamp01((sinceIgnite - rg.d) / 2);
      rg.sp.scale.setScalar(2 + q * 70); rg.sp.material.opacity = q > 0 && q < 1 ? (1 - q) * 0.7 : 0;
    });

    // 章节标记只在进入那位读者时出现；最新一章那颗星一直在闪：故事还在写
    nodes.forEach((n) => {
      pathAt(rows[n.row], n.s, elapsed, n.star.position);
      n.label.position.copy(n.star.position).add(tmp.set(0.6, 0.5, 0));
      const reached = smooth((spread - n.s) / 0.03), lv = rowLv[n.row] * reached;
      n.star.material.opacity = ((n.end ? 0.35 : 0.15) * shared.uBase.value * 2 * reached + lv * 0.55) * (n.end ? 0.7 + 0.3 * Math.sin(elapsed * 3 + n.k) : 1);
      const want = story.focus === n.k ? smooth((lv - 0.3) / 0.6) : 0;
      n.label.material.opacity += (want - n.label.material.opacity) * k;
      labelScale(n.label);
    });
    meetMarks.forEach((m) => {
      pathAt(rows[flashRow[m.a]], m.s, elapsed, m.star.position);
      m.label.position.copy(m.star.position).add(tmp.set(0.6, 0.5, 0));
      const on = story.focus === 4 + m.a || story.focus === 4 + m.b ? 1 : 0;
      m.star.material.opacity += (on * 0.8 - m.star.material.opacity) * k;
      m.label.material.opacity += (on - m.label.material.opacity) * k;
      labelScale(m.label);
    });
    rootLabel.position.set(1.2, 1.2, 0);
    const rootWant = (hub ? 1 : 0) * ignite;
    rootLabel.material.opacity += (rootWant - rootLabel.material.opacity) * k;
    labelScale(rootLabel);
    if (story.choice >= 0) {
      pathAt(rows[mineRow[story.choice]], Math.min(0.985, mineSpread), elapsed, pen.position);
      pen.material.opacity = (0.7 + 0.3 * Math.sin(elapsed * 3)) * smooth(mineSpread / 0.05);
      penLabel.position.copy(pen.position).add(tmp.set(0.6, 0.5, 0));
      const penWant = (chosen || story.focus === MINE ? 1 : 0) * smooth((mineSpread - 0.4) / 0.2);
      penLabel.material.opacity += (penWant - penLabel.material.opacity) * k;
      labelScale(penLabel);
    }

    composer.render();

    // 鼠标停着不动时文字流也在动，所以每隔一帧重新拾取一次
    if (hub && !story.touch && mouseX >= 0 && (frame++ & 1) === 0) setHover(pick(mouseX, mouseY, story.hover), mouseX, mouseY);
    requestAnimationFrame(render);
  }
  requestAnimationFrame(render);
  return { pick };
}
export type StarScene = Awaited<ReturnType<typeof createStarScene>>;
