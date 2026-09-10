"use client";

import dynamic from "next/dynamic";
import { useEffect, useState } from "react";

/**
 * Wrapper that decides whether the visitor should get the WebGL ring at all.
 *
 * The 3D bundle is only fetched when we have decided to render it, `dynamic` with
 * `ssr: false` means three.js never reaches a visitor who will not see it. That keeps the
 * cost off mobile, off reduced-motion users, and off the first paint.
 *
 * The static fallback is a real designed state, not a blank box: an SVG ring with the same
 * proportions and glow. Someone who never sees the WebGL version should not feel like they
 * are looking at a broken page.
 */

const LoopRing3D = dynamic(() => import("./LoopRing3D"), {
  ssr: false,
  loading: () => <StaticRing />,
});

export function StaticRing() {
  return (
    <div className="relative flex h-full w-full items-center justify-center">
      <div
        className="absolute h-[62%] w-[62%] rounded-full blur-3xl"
        style={{ background: "radial-gradient(circle, #c9a22755, transparent 70%)" }}
        aria-hidden
      />
      <svg
        viewBox="0 0 200 200"
        className="relative h-[78%] w-[78%]"
        role="img"
        aria-label="The CareLoop mark: a single unbroken gold ring"
      >
        <defs>
          <linearGradient id="ringGold" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#e8c65a" />
            <stop offset="55%" stopColor="#c9a227" />
            <stop offset="100%" stopColor="#8a6a00" />
          </linearGradient>
        </defs>
        <ellipse
          cx="100"
          cy="100"
          rx="68"
          ry="68"
          fill="none"
          stroke="url(#ringGold)"
          strokeWidth="11"
        />
        <ellipse
          cx="100"
          cy="100"
          rx="42"
          ry="42"
          fill="none"
          stroke="#e8c65a"
          strokeOpacity="0.32"
          strokeWidth="4"
        />
      </svg>
    </div>
  );
}

export default function LoopHero() {
  const [shouldRender3D, setShouldRender3D] = useState(false);

  useEffect(() => {
    // Decided on the client, after mount, so the server never guesses wrong.
    const prefersReducedMotion = window.matchMedia(
      "(prefers-reduced-motion: reduce)",
    ).matches;
    const isSmallScreen = window.matchMedia("(max-width: 768px)").matches;

    // Rough proxy for a low-powered device. Not exact, but cheap and directionally right.
    const lowCoreCount =
      typeof navigator.hardwareConcurrency === "number" &&
      navigator.hardwareConcurrency <= 4;

    setShouldRender3D(!prefersReducedMotion && !isSmallScreen && !lowCoreCount);
  }, []);

  return (
    <div className="h-full w-full">
      {shouldRender3D ? <LoopRing3D /> : <StaticRing />}
    </div>
  );
}
