"use client";

import { useEffect, useRef, useState } from "react";
import { callScript, liveInteraction } from "@/lib/demo-data";

/**
 * An in-browser replay of the actual call.
 *
 * Shows, rather than claims, the product's key beat: Margaret mentions ibuprofen, and the
 * ring picks up a second counter-rotating arc while Cara checks it against her warfarin —
 * with the conversation continuing underneath. That concurrency is the whole argument for
 * calling this agentic rather than scripted.
 *
 * Only animates once scrolled into view, and holds a completed static state under
 * `prefers-reduced-motion` rather than looping.
 */
export default function PhoneCallMock() {
  const [visibleCount, setVisibleCount] = useState(0);
  const [checking, setChecking] = useState(false);
  const [showInteraction, setShowInteraction] = useState(false);
  const [started, setStarted] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // Start only when seen, so a visitor who scrolls down later still catches it from the top.
  useEffect(() => {
    const node = containerRef.current;
    if (!node || typeof IntersectionObserver === "undefined") {
      setStarted(true);
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setStarted(true);
          observer.disconnect();
        }
      },
      { threshold: 0.3 },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!started) return;

    const reduced =
      typeof window !== "undefined" &&
      window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    if (reduced) {
      // Show the finished conversation rather than animating it.
      setVisibleCount(callScript.length);
      setShowInteraction(true);
      return;
    }

    const timers: number[] = [];
    callScript.forEach((line, i) => {
      timers.push(
        window.setTimeout(() => {
          setVisibleCount(i + 1);
          if (line.flag === "checking") {
            setChecking(true);
            timers.push(
              window.setTimeout(() => setShowInteraction(true), 1600),
            );
          }
        }, 1000 + i * 1500),
      );
    });

    return () => timers.forEach(clearTimeout);
  }, [started]);

  return (
    <div ref={containerRef} className="w-full max-w-[320px]">
      {/* Phone shell */}
      <div className="relative rounded-[2.5rem] border border-white/15 bg-navy-deep p-3 shadow-2xl">
        <div className="overflow-hidden rounded-[2rem] bg-gradient-to-b from-navy to-navy-deep">
          {/* Cara */}
          <div className="flex flex-col items-center px-6 pt-9 pb-6">
            <div className="relative flex h-24 w-24 items-center justify-center">
              <div
                className="absolute inset-0 rounded-full blur-xl"
                style={{ background: "radial-gradient(circle,#c9a22766,transparent 70%)" }}
                aria-hidden
              />
              <svg viewBox="0 0 100 100" className="relative h-24 w-24">
                <circle
                  cx="50"
                  cy="50"
                  r="34"
                  fill="none"
                  stroke="#c9a227"
                  strokeWidth="7"
                  opacity="0.85"
                />
                {checking && (
                  <circle
                    cx="50"
                    cy="50"
                    r="22"
                    fill="none"
                    stroke="#ffd166"
                    strokeWidth="4"
                    strokeLinecap="round"
                    strokeDasharray="26 112"
                    className="origin-center animate-spin"
                    style={{ animationDuration: "1.4s" }}
                  />
                )}
              </svg>
            </div>

            <p className="mt-4 font-display text-2xl text-white">Cara</p>
            <p className="mt-1 h-5 text-xs text-gold">
              {checking && !showInteraction
                ? "Checking your medicines while we talk"
                : showInteraction
                  ? "Checked"
                  : "Listening"}
            </p>
          </div>

          {/* Interaction catch */}
          {showInteraction && (
            <div className="mx-4 mb-3 rounded-2xl bg-yellow/15 px-4 py-3">
              <p className="text-xs font-semibold text-yellow">
                {liveInteraction.drugA} + {liveInteraction.drugB}
              </p>
              <p className="mt-1 text-[11px] leading-relaxed text-white/75">
                Together these make bleeding more likely.
              </p>
            </div>
          )}

          {/* Transcript */}
          <div className="min-h-[190px] space-y-2.5 px-4 pb-6">
            {callScript.slice(0, visibleCount).map((line, i) => (
              <div
                key={i}
                className={line.speaker === "cara" ? "text-left" : "text-right"}
              >
                <span
                  className={`inline-block max-w-[85%] rounded-2xl px-3 py-2 text-[11px] leading-relaxed ${
                    line.speaker === "cara"
                      ? "bg-navy-soft text-white"
                      : "bg-white/10 text-white/90"
                  }`}
                >
                  {line.text}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <p className="mt-5 text-center text-xs leading-relaxed text-white/45">
        A real check-in. Cara checks the interaction in the background — the
        conversation never stops.
      </p>
    </div>
  );
}
