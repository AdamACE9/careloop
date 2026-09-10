"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";

/**
 * Scroll-reveal wrapper.
 *
 * ## Why this is more defensive than a typical reveal component
 *
 * The failure mode of scroll-reveal is uniquely bad: if the trigger never fires, the
 * content is not merely un-animated, it is **permanently invisible**. A missing animation
 * is a cosmetic problem; a blank page is a total one. So this component treats "reveal"
 * as the safe default and animation as the enhancement, with three independent paths to
 * becoming visible:
 *
 * 1. **Already-in-view check on mount.** Anything on screen at first paint reveals
 *    immediately, without waiting for an observer callback.
 * 2. **IntersectionObserver** for everything below the fold, the normal path.
 * 3. **A failsafe timer.** If neither of the above has fired within
 *    [FAILSAFE_MS], reveal anyway.
 *
 * Path 3 is not paranoia. Chrome suspends IntersectionObserver callbacks for pages that
 * are not being painted, an occluded window, some headless and preview contexts, certain
 * background tabs. This was observed directly while building this page: a freshly
 * constructed observer on an element sitting in the viewport received no callback at all.
 * Without the failsafe, that renders the entire site blank below the hero.
 *
 * Visibility is held in React state rather than toggled via `classList`, so it survives
 * re-renders (including Fast Refresh in development, which otherwise resets an
 * imperatively-added class and makes content vanish mid-session).
 *
 * `prefers-reduced-motion` is handled in CSS, `.reveal` resets to fully visible, so a
 * reduced-motion visitor never depends on any of this running.
 */

const FAILSAFE_MS = 2000;

export default function Reveal({
  children,
  delay = 0,
  className = "",
}: {
  children: ReactNode;
  delay?: number;
  className?: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const node = ref.current;
    if (!node) return;

    let cancelled = false;
    const timers: number[] = [];

    const reveal = () => {
      if (cancelled) return;
      if (delay > 0) {
        timers.push(window.setTimeout(() => !cancelled && setVisible(true), delay));
      } else {
        setVisible(true);
      }
    };

    // 1. Already on screen at mount.
    const rect = node.getBoundingClientRect();
    if (rect.top < window.innerHeight && rect.bottom > 0) {
      reveal();
    }

    // 2. The normal path.
    let observer: IntersectionObserver | undefined;
    if (typeof IntersectionObserver !== "undefined") {
      observer = new IntersectionObserver(
        (entries) => {
          if (entries.some((entry) => entry.isIntersecting)) {
            reveal();
            observer?.disconnect();
          }
        },
        // threshold 0 rather than a fraction: for an element taller than the viewport a
        // fractional threshold can be mathematically unreachable and never fire.
        { threshold: 0, rootMargin: "0px 0px -60px 0px" },
      );
      observer.observe(node);
    }

    // 3. Failsafe, content must never stay hidden.
    timers.push(window.setTimeout(reveal, FAILSAFE_MS));

    return () => {
      cancelled = true;
      observer?.disconnect();
      timers.forEach(clearTimeout);
    };
  }, [delay]);

  return (
    <div ref={ref} className={`reveal ${visible ? "is-visible" : ""} ${className}`}>
      {children}
    </div>
  );
}
