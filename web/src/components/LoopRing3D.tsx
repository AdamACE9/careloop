"use client";

import { Canvas, useFrame } from "@react-three/fiber";
import { useRef, useMemo } from "react";
import * as THREE from "three";

/**
 * The volumetric Loop — the website's one WebGL moment.
 *
 * ## Why this is the only 3D on the site
 *
 * Every AI product site in 2026 has the same drifting WebGL blob, so heavy 3D reads as
 * *more* templated, not less. This earns its place because the ring is the actual brand
 * mark and the actual product metaphor — the loop that never breaks — rather than
 * decoration bolted onto a hero.
 *
 * ## Deliberate engineering constraints
 *
 * - **No `<Environment>` / HDRI.** drei's environment presets fetch an HDR map from a CDN
 *   at runtime. On a conference wifi that is a silent failure that leaves the ring flat
 *   black on stage. Lighting is done with plain lights so the scene has zero network
 *   dependencies.
 * - **`dpr` capped at 1.5.** Uncapped device pixel ratio on a 3x phone screen renders
 *   ~9x the pixels for no visible gain and drains battery.
 * - **`frameloop="demand"` is NOT used** — the ring rotates continuously, so it genuinely
 *   needs every frame. Where it is paused (reduced motion) the parent renders a static
 *   fallback instead of this component at all.
 */

function Ring() {
  const outer = useRef<THREE.Mesh>(null);
  const inner = useRef<THREE.Mesh>(null);

  // Built once. Recreating geometry per frame is the classic r3f performance mistake.
  const goldMaterial = useMemo(
    () =>
      new THREE.MeshStandardMaterial({
        color: new THREE.Color("#c9a227"),
        metalness: 0.92,
        roughness: 0.22,
      }),
    [],
  );

  const faintMaterial = useMemo(
    () =>
      new THREE.MeshStandardMaterial({
        color: new THREE.Color("#e8c65a"),
        metalness: 0.7,
        roughness: 0.5,
        transparent: true,
        opacity: 0.35,
      }),
    [],
  );

  useFrame((state, delta) => {
    if (outer.current) {
      outer.current.rotation.y += delta * 0.22;
      // A slow breath on the tilt, so it never looks like a rigid turntable spin.
      outer.current.rotation.x =
        -0.42 + Math.sin(state.clock.elapsedTime * 0.35) * 0.06;
    }
    if (inner.current) {
      // Counter-rotation echoes the app's "checking" state: two things at once.
      inner.current.rotation.y -= delta * 0.14;
      inner.current.rotation.x = 0.5;
    }
  });

  return (
    <group>
      <mesh ref={outer} material={goldMaterial}>
        {/* radius, tube, radialSegments, tubularSegments */}
        <torusGeometry args={[2.1, 0.16, 32, 160]} />
      </mesh>
      <mesh ref={inner} material={faintMaterial} scale={0.62}>
        <torusGeometry args={[2.1, 0.07, 24, 120]} />
      </mesh>
    </group>
  );
}

export default function LoopRing3D() {
  return (
    <Canvas
      camera={{ position: [0, 0, 7], fov: 42 }}
      dpr={[1, 1.5]}
      gl={{ antialias: true, alpha: true }}
      style={{ background: "transparent" }}
    >
      {/* Keyed from upper-left, warm, to read as lamplight rather than a studio rig. */}
      <ambientLight intensity={0.55} color="#8fa4d8" />
      <directionalLight position={[4, 6, 5]} intensity={2.4} color="#ffe6a8" />
      <directionalLight position={[-5, -2, 2]} intensity={0.9} color="#3d5da8" />
      <pointLight position={[0, 0, 3]} intensity={12} color="#c9a227" distance={12} />
      <Ring />
    </Canvas>
  );
}
