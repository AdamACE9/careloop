"use client";

import { useEffect, useRef, type ReactNode } from "react";

/**
 * Scroll-reveal wrapper.
 *
 * Uses IntersectionObserver rather than a scroll listener — a scroll handler firing on
 * every frame is one of the commonest causes of jank on otherwise well-built sites.
 *
 * The corresponding CSS (`.reveal` in globals.css) resets to fully visible under
 * `prefers-reduced-motion`, so this degrades to plain content rather than to content that
 * never appears. Elements are also unobserved once revealed; leaving observers attached to
 * every section on a long page is needless work.
 */
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

  useEffect(() => {
    const node = ref.current;
    if (!node) return;

    // If the browser lacks IntersectionObserver, show the content immediately rather
    // than leaving it invisible forever.
    if (typeof IntersectionObserver === "undefined") {
      node.classList.add("is-visible");
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            window.setTimeout(() => node.classList.add("is-visible"), delay);
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.12, rootMargin: "0px 0px -60px 0px" },
    );

    observer.observe(node);
    return () => observer.disconnect();
  }, [delay]);

  return (
    <div ref={ref} className={`reveal ${className}`}>
      {children}
    </div>
  );
}
