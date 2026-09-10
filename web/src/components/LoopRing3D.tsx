"use client";

import { Canvas, useFrame, useThree } from "@react-three/fiber";
import { useRef, useMemo, useState, useEffect } from "react";
import * as THREE from "three";

/**
 * The volumetric Loop: the website's one WebGL moment.
 *
 * ## Why 3D here and nowhere else
 *
 * Every AI product site has the same drifting WebGL blob, so heavy 3D reads as
 * more templated, not less. This earns its place because the ring is the actual
 * brand mark and the actual product metaphor, the loop that never breaks. It is
 * the thing being explained, not decoration around it.
 *
 * ## The composition
 *
 * Five layers, each doing a job rather than adding noise:
 *
 *   1. **The core ring**, solid gold, the mark itself.
 *   2. **Two counter-rotating inner rings** at different tilts. They echo the
 *      in-app "checking" state, where a second arc spins the other way to show
 *      background work happening during a conversation.
 *   3. **An orbiting particle field**, drifting slowly around the axis. Reads as
 *      the days of check-ins accumulating.
 *   4. **A pulse ring** that expands and fades on a slow cycle, once every few
 *      seconds, the visual rhythm of a daily call.
 *   5. **Parallax on pointer movement**, so the whole group leans toward the
 *      cursor. Subtle enough to feel like depth rather than a toy.
 *
 * ## Engineering constraints that are deliberate
 *
 * - **No `<Environment>` or HDRI.** drei's environment presets fetch an HDR map
 *   from a CDN at runtime. On conference wifi that is a silent failure leaving
 *   the ring flat black on stage. Lighting is done with plain lights so the scene
 *   has zero network dependencies.
 * - **DPR capped at 1.75.** Uncapped device pixel ratio on a 3x screen renders
 *   about nine times the pixels for no visible gain.
 * - **Geometry and materials built once** via useMemo. Recreating them per frame
 *   is the classic r3f performance mistake.
 * - **Damped parallax.** Following the pointer exactly feels twitchy; easing
 *   toward the target reads as weight.
 */

const GOLD = "#c9a227";
const GOLD_BRIGHT = "#e8c65a";
const BLUE = "#3d5da8";

function Scene() {
  const group = useRef<THREE.Group>(null);
  const core = useRef<THREE.Mesh>(null);
  const innerA = useRef<THREE.Mesh>(null);
  const innerB = useRef<THREE.Mesh>(null);
  const pulse = useRef<THREE.Mesh>(null);
  const particles = useRef<THREE.Points>(null);

  const { viewport } = useThree();
  const pointer = useRef({ x: 0, y: 0 });

  useEffect(() => {
    function handle(event: PointerEvent) {
      // Normalised to roughly -1..1 so the lean is screen-size independent.
      pointer.current = {
        x: (event.clientX / window.innerWidth) * 2 - 1,
        y: (event.clientY / window.innerHeight) * 2 - 1,
      };
    }
    window.addEventListener("pointermove", handle, { passive: true });
    return () => window.removeEventListener("pointermove", handle);
  }, []);

  const goldMaterial = useMemo(
    () =>
      new THREE.MeshStandardMaterial({
        color: new THREE.Color(GOLD),
        metalness: 0.94,
        roughness: 0.18,
        emissive: new THREE.Color(GOLD),
        emissiveIntensity: 0.06,
      }),
    [],
  );

  const faintMaterial = useMemo(
    () =>
      new THREE.MeshStandardMaterial({
        color: new THREE.Color(GOLD_BRIGHT),
        metalness: 0.7,
        roughness: 0.45,
        transparent: true,
        opacity: 0.38,
      }),
    [],
  );

  const pulseMaterial = useMemo(
    () =>
      new THREE.MeshBasicMaterial({
        color: new THREE.Color(GOLD_BRIGHT),
        transparent: true,
        opacity: 0.25,
        side: THREE.DoubleSide,
      }),
    [],
  );

  // A ring of points with slight radial and axial scatter, so it reads as a
  // volume rather than a wire circle.
  const particleGeometry = useMemo(() => {
    const count = 420;
    const positions = new Float32Array(count * 3);
    for (let i = 0; i < count; i += 1) {
      const angle = Math.random() * Math.PI * 2;
      const radius = 2.7 + (Math.random() - 0.5) * 1.5;
      positions[i * 3] = Math.cos(angle) * radius;
      positions[i * 3 + 1] = (Math.random() - 0.5) * 0.9;
      positions[i * 3 + 2] = Math.sin(angle) * radius;
    }
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
    return geometry;
  }, []);

  const particleMaterial = useMemo(
    () =>
      new THREE.PointsMaterial({
        color: new THREE.Color(GOLD_BRIGHT),
        size: 0.035,
        transparent: true,
        opacity: 0.6,
        sizeAttenuation: true,
      }),
    [],
  );

  useFrame((state, delta) => {
    const t = state.clock.elapsedTime;

    // Damped parallax. Easing toward the target rather than snapping to it is
    // what makes this feel like a heavy object rather than a cursor follower.
    if (group.current) {
      const targetY = pointer.current.x * 0.28;
      const targetX = pointer.current.y * 0.18;
      group.current.rotation.y += (targetY - group.current.rotation.y) * 0.04;
      group.current.rotation.x += (targetX - group.current.rotation.x) * 0.04;
    }

    if (core.current) {
      core.current.rotation.z += delta * 0.16;
      // A slow breath on the tilt, so it never reads as a rigid turntable spin.
      core.current.rotation.x = -0.42 + Math.sin(t * 0.32) * 0.07;
    }

    if (innerA.current) {
      innerA.current.rotation.z -= delta * 0.26;
      innerA.current.rotation.x = 0.55 + Math.cos(t * 0.24) * 0.05;
    }

    if (innerB.current) {
      innerB.current.rotation.z += delta * 0.4;
      innerB.current.rotation.y = 0.7;
    }

    if (particles.current) {
      particles.current.rotation.y += delta * 0.05;
    }

    // The pulse: expands and fades over a five second cycle, the rhythm of a
    // daily check-in going out.
    if (pulse.current) {
      const phase = (t % 5) / 5;
      const scale = 0.85 + phase * 0.9;
      pulse.current.scale.setScalar(scale);
      (pulse.current.material as THREE.MeshBasicMaterial).opacity =
        0.28 * (1 - phase) ** 2;
    }
  });

  // Scale the whole composition down on narrow viewports so it never crops.
  const scale = Math.min(1, viewport.width / 9);

  return (
    <group ref={group} scale={scale}>
      <mesh ref={core} material={goldMaterial}>
        <torusGeometry args={[2.1, 0.15, 32, 180]} />
      </mesh>

      <mesh ref={innerA} material={faintMaterial} scale={0.66}>
        <torusGeometry args={[2.1, 0.06, 20, 120]} />
      </mesh>

      <mesh ref={innerB} material={faintMaterial} scale={0.4}>
        <torusGeometry args={[2.1, 0.05, 16, 100]} />
      </mesh>

      <mesh ref={pulse} material={pulseMaterial}>
        <ringGeometry args={[2.3, 2.34, 128]} />
      </mesh>

      <points ref={particles} geometry={particleGeometry} material={particleMaterial} />
    </group>
  );
}

export default function LoopRing3D() {
  const [ready, setReady] = useState(false);

  return (
    <Canvas
      camera={{ position: [0, 0, 7.4], fov: 42 }}
      dpr={[1, 1.75]}
      gl={{ antialias: true, alpha: true, powerPreference: "high-performance" }}
      style={{
        background: "transparent",
        // Fade in once the first frame is painted, so there is no flash of an
        // empty canvas before the scene appears.
        opacity: ready ? 1 : 0,
        transition: "opacity 900ms cubic-bezier(0.22, 1, 0.36, 1)",
      }}
      onCreated={() => setReady(true)}
    >
      {/* Warm key from upper left, cool rim from lower right, so the ring reads
          as lit by a lamp in a room rather than by a studio rig. */}
      <ambientLight intensity={0.5} color="#8fa4d8" />
      <directionalLight position={[4, 6, 5]} intensity={2.6} color="#ffe6a8" />
      <directionalLight position={[-5, -2, 2]} intensity={1.0} color={BLUE} />
      <pointLight position={[0, 0, 3]} intensity={14} color={GOLD} distance={13} />
      <Scene />
    </Canvas>
  );
}
