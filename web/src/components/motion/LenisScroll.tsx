"use client";

import { useEffect, useRef } from "react";
import Lenis from "lenis";
import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

gsap.registerPlugin(ScrollTrigger);

/**
 * Smooth scroll, landing page only. Renders nothing.
 *
 * Deliberately the plain `Lenis` class driven from a side-effect, not the
 * `<ReactLenis root>` wrapper. The wrapper is the documented, idiomatic way to
 * do this, but it has to sit ABOVE the page content so it can decide whether to
 * render a real wrapper div or pass children straight through. Toggling that
 * decision at runtime for `prefers-reduced-motion` (mount for most visitors,
 * never mount for a visitor who asked for less motion) changes the component
 * type at that position in the tree, which makes React discard and rebuild the
 * entire landing page's DOM the moment the check resolves. A component that
 * renders `null` alongside the page, instead of wrapping it, cannot have that
 * failure mode: the page's DOM never depends on whether this ran.
 *
 * The sync with ScrollTrigger is the three lines the library docs specify:
 * feed Lenis's scroll event into `ScrollTrigger.update`, drive Lenis from
 * gsap's own ticker instead of its own rAF loop, and turn off gsap's lag
 * smoothing (which exists to hide dropped frames by slowing down time, and
 * fights a scroll library that is already smoothing time itself).
 */
export default function LenisScroll() {
  const lenisRef = useRef<Lenis | null>(null);
  const rafCallbackRef = useRef<((time: number) => void) | null>(null);

  useEffect(() => {
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduced) return;

    const lenis = new Lenis({
      duration: 1.1,
      easing: (t: number) => 1 - Math.pow(1 - t, 3),
      smoothWheel: true,
      touchMultiplier: 1.4,
    });
    lenisRef.current = lenis;

    lenis.on("scroll", ScrollTrigger.update);

    const rafCallback = (time: number) => {
      lenis.raf(time * 1000);
    };
    rafCallbackRef.current = rafCallback;
    gsap.ticker.add(rafCallback);
    gsap.ticker.lagSmoothing(0);

    // The header's "How it works" link is an in-page hash anchor. Native jump
    // scrolling fights Lenis, so anchor clicks are routed through it instead.
    function onAnchorClick(event: MouseEvent) {
      const target = (event.target as HTMLElement)?.closest("a[href^='/#'], a[href^='#']");
      if (!target) return;
      const hash = target.getAttribute("href")?.split("#")[1];
      if (!hash) return;
      const el = document.getElementById(hash);
      if (!el) return;
      event.preventDefault();
      lenis.scrollTo(el, { offset: -88 });
    }
    document.addEventListener("click", onAnchorClick);

    return () => {
      document.removeEventListener("click", onAnchorClick);
      if (rafCallbackRef.current) gsap.ticker.remove(rafCallbackRef.current);
      lenis.destroy();
      lenisRef.current = null;
    };
  }, []);

  return null;
}
