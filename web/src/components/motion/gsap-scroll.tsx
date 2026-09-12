"use client";

/**
 * GSAP + ScrollTrigger primitives for the landing page.
 *
 * The one rule every primitive here follows: an element's CSS resting state is
 * always its final, fully visible state. Nothing is ever hidden by a stylesheet
 * or by `gsap.set()`. The hidden starting point exists only inside `gsap.from()`,
 * which writes it at the moment the tween is created, after the component has
 * mounted and this file's JavaScript is actually running. If that JavaScript
 * never runs (disabled, blocked, throws before this point), the element was
 * never touched and stays exactly as the browser painted it: visible. Reversing
 * that order, hiding first in CSS or in `set()` and revealing later, is what
 * strands content invisible when a trigger never fires, and this file's whole
 * job is to make that failure mode structurally impossible.
 *
 * `gsap.matchMedia()` is the reduced-motion switch. Its `reduce` branch does not
 * animate at all, it just confirms the element is in its resting (visible)
 * state, because that state is already correct.
 */

import { useRef, type ReactNode } from "react";
import { useGSAP } from "@gsap/react";
import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

gsap.registerPlugin(ScrollTrigger, useGSAP);

// -----------------------------------------------------------------------------
// Reveal, single element, fades and rises into place as it enters the viewport
// -----------------------------------------------------------------------------

export function Reveal({
  children,
  className = "",
  y = 28,
  delay = 0,
  as: As = "div",
}: {
  children: ReactNode;
  className?: string;
  y?: number;
  delay?: number;
  as?: "div" | "span";
}) {
  const ref = useRef<HTMLDivElement>(null);

  useGSAP(
    () => {
      const el = ref.current;
      if (!el) return;

      const mm = gsap.matchMedia();
      mm.add("(prefers-reduced-motion: no-preference)", () => {
        gsap.from(el, {
          opacity: 0,
          y,
          duration: 0.9,
          delay: delay / 1000,
          ease: "power3.out",
          scrollTrigger: {
            trigger: el,
            start: "top 88%",
            toggleActions: "play none none none",
          },
        });
      });
      return () => mm.revert();
    },
    { scope: ref },
  );

  const Tag = As;
  return (
    <Tag ref={ref} className={className}>
      {children}
    </Tag>
  );
}

// -----------------------------------------------------------------------------
// RevealGroup, staggers direct children marked with data-reveal-item
// -----------------------------------------------------------------------------

export function RevealGroup({
  children,
  className = "",
  stagger = 0.1,
}: {
  children: ReactNode;
  className?: string;
  stagger?: number;
}) {
  const ref = useRef<HTMLDivElement>(null);

  useGSAP(
    () => {
      const el = ref.current;
      if (!el) return;
      const items = el.querySelectorAll<HTMLElement>("[data-reveal-item]");
      if (!items.length) return;

      const mm = gsap.matchMedia();
      mm.add("(prefers-reduced-motion: no-preference)", () => {
        gsap.from(items, {
          opacity: 0,
          y: 26,
          duration: 0.8,
          ease: "power3.out",
          stagger,
          scrollTrigger: {
            trigger: el,
            start: "top 85%",
            toggleActions: "play none none none",
          },
        });
      });
      return () => mm.revert();
    },
    { scope: ref, dependencies: [stagger] },
  );

  return (
    <div ref={ref} className={className}>
      {children}
    </div>
  );
}

// -----------------------------------------------------------------------------
// SplitReveal, a headline split into words, each rising in on its own beat
// -----------------------------------------------------------------------------

export function SplitReveal({
  text,
  className = "",
  highlight,
  onScroll = false,
}: {
  text: string;
  className?: string;
  /** Words rendered in gold, matched case-insensitively, punctuation ignored. */
  highlight?: string[];
  /** Tie the split to scroll position rather than playing once on mount. */
  onScroll?: boolean;
}) {
  const ref = useRef<HTMLSpanElement>(null);
  const words = text.split(" ");
  const normalise = (w: string) => w.replace(/[.,;:!?]/g, "").toLowerCase();
  const highlightSet = new Set((highlight ?? []).map(normalise));

  useGSAP(
    () => {
      const el = ref.current;
      if (!el) return;
      const spans = el.querySelectorAll<HTMLElement>("span[data-word]");
      if (!spans.length) return;

      const mm = gsap.matchMedia();
      mm.add("(prefers-reduced-motion: no-preference)", () => {
        gsap.from(spans, {
          opacity: 0,
          y: "0.7em",
          rotate: 1.5,
          duration: 0.7,
          ease: "power3.out",
          stagger: 0.045,
          scrollTrigger: onScroll
            ? { trigger: el, start: "top 85%", toggleActions: "play none none none" }
            : undefined,
        });
      });
      return () => mm.revert();
    },
    { scope: ref, dependencies: [text, onScroll] },
  );

  return (
    <span ref={ref} className={className} aria-label={text}>
      {words.map((word, i) => {
        const isGold = highlightSet.has(normalise(word));
        return (
          <span key={`${word}-${i}`} data-word className="inline-block" aria-hidden>
            <span className={`inline-block ${isGold ? "text-gold" : ""}`}>{word}</span>
            {i < words.length - 1 ? " " : ""}
          </span>
        );
      })}
    </span>
  );
}

// -----------------------------------------------------------------------------
// ParallaxLayer, scroll-scrubbed vertical drift, never a visibility concern
// -----------------------------------------------------------------------------

/**
 * Unlike the reveals above, parallax is a position offset applied while the
 * element is already fully visible throughout, so it carries none of the
 * stranded-invisible risk. `scrub: true` ties progress directly to scroll
 * position rather than to a duration, which is what makes it read as physical
 * depth instead of a delayed animation.
 */
export function ParallaxLayer({
  children,
  className = "",
  speed = 12,
}: {
  children: ReactNode;
  className?: string;
  /** Percent of the element's own height it drifts across its scroll range. */
  speed?: number;
}) {
  const ref = useRef<HTMLDivElement>(null);

  useGSAP(
    () => {
      const el = ref.current;
      if (!el) return;
      const mm = gsap.matchMedia();
      mm.add("(prefers-reduced-motion: no-preference)", () => {
        gsap.to(el, {
          yPercent: speed,
          ease: "none",
          scrollTrigger: {
            trigger: el,
            start: "top bottom",
            end: "bottom top",
            scrub: 0.6,
          },
        });
      });
      return () => mm.revert();
    },
    { scope: ref, dependencies: [speed] },
  );

  return (
    <div ref={ref} className={className}>
      {children}
    </div>
  );
}
