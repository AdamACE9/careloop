"use client";

import { useEffect, useRef } from "react";

/**
 * Reading-progress bar across the top of the page.
 *
 * Written against `requestAnimationFrame` rather than updating state on every
 * scroll event. A React state update per scroll frame would re-render the whole
 * subtree sixty times a second; this writes a single CSS custom property that the
 * compositor can act on without touching layout.
 *
 * The scroll listener is passive, which lets the browser scroll without waiting
 * to find out whether we intend to call preventDefault.
 */
export default function ScrollProgress() {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let frame = 0;

    function update() {
      frame = 0;
      const node = ref.current;
      if (!node) return;

      const scrollable =
        document.documentElement.scrollHeight - window.innerHeight;
      const progress = scrollable > 0 ? window.scrollY / scrollable : 0;
      node.style.setProperty("--progress", String(Math.min(1, Math.max(0, progress))));
    }

    function onScroll() {
      // Coalesce bursts of scroll events into one write per animation frame.
      if (frame) return;
      frame = window.requestAnimationFrame(update);
    }

    update();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll, { passive: true });

    return () => {
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
      if (frame) window.cancelAnimationFrame(frame);
    };
  }, []);

  return <div ref={ref} className="scroll-progress" aria-hidden />;
}

/**
 * Splits a headline into words so each can rise independently.
 *
 * The stagger lives in CSS via a `--i` custom property per word rather than in
 * JavaScript timers, so it costs nothing at runtime and is switched off entirely
 * by the reduced-motion block.
 *
 * Wrapped in a single accessible label because a screen reader encountering
 * thirty separate word spans reads them as thirty fragments.
 */
export function WordRise({
  text,
  className = "",
  highlight,
}: {
  text: string;
  className?: string;
  /** Words rendered in gold, matched case-insensitively. */
  highlight?: string[];
}) {
  const words = text.split(" ");
  const highlightSet = new Set((highlight ?? []).map((w) => w.toLowerCase()));

  return (
    <span className={`word-rise ${className}`} aria-label={text}>
      {words.map((word, i) => {
        const bare = word.replace(/[.,]/g, "").toLowerCase();
        const isGold = highlightSet.has(bare);
        return (
          <span
            key={`${word}-${i}`}
            style={{ ["--i" as string]: i }}
            className={isGold ? "text-gold" : undefined}
            aria-hidden
          >
            {word}
            {i < words.length - 1 ? " " : ""}
          </span>
        );
      })}
    </span>
  );
}
