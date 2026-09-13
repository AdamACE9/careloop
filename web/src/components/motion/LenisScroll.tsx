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

    // ARRIVING with a hash is a different problem from clicking one, and the
    // handler above does nothing for it.
    //
    // The footer links to `/#how` from every page, so that is the normal way
    // in from /download or /login, as well as any shared or bookmarked link.
    // The browser performs its jump against the document as it measures at
    // first paint. The pinned section then creates its pin spacer, which adds
    // roughly 2400px of scroll height, and the offset the browser already
    // committed to now points somewhere else entirely. Measured on the live
    // site: #how sits at y=1567, and loading /#how left the visitor at 6346,
    // near the bottom of the page, looking at the final call to action.
    //
    // So re-aim after the layout has stopped moving, rather than before. Once
    // now, once when ScrollTrigger finishes its first refresh (when the pin
    // spacer exists), and once after load (when fonts and the hero canvas have
    // settled). `immediate` because this is arrival, not a journey: animating
    // a 6000px scroll on page load is not a nice touch, it is motion sickness.
    const requestedHash = window.location.hash.slice(1);
    let cancelHashScroll = () => {};

    if (requestedHash && document.getElementById(requestedHash)) {
      // Otherwise a back navigation restores the same wrong offset and undoes
      // all of this on the way in.
      if ("scrollRestoration" in history) history.scrollRestoration = "manual";

      // If the visitor has already started scrolling for themselves, they have
      // taken over and yanking them back to an anchor is the page fighting the
      // person using it.
      let userHasScrolled = false;
      const surrender = () => {
        userHasScrolled = true;
      };
      window.addEventListener("wheel", surrender, { passive: true, once: true });
      window.addEventListener("touchstart", surrender, { passive: true, once: true });
      window.addEventListener("keydown", surrender, { once: true });

      const settle = () => {
        if (userHasScrolled) return;
        const el = document.getElementById(requestedHash);
        if (el) lenis.scrollTo(el, { offset: -88, immediate: true });
      };

      const onRefresh = () => {
        settle();
        ScrollTrigger.removeEventListener("refresh", onRefresh);
      };

      settle();
      ScrollTrigger.addEventListener("refresh", onRefresh);
      window.addEventListener("load", settle, { once: true });

      cancelHashScroll = () => {
        ScrollTrigger.removeEventListener("refresh", onRefresh);
        window.removeEventListener("load", settle);
        window.removeEventListener("wheel", surrender);
        window.removeEventListener("touchstart", surrender);
        window.removeEventListener("keydown", surrender);
      };
    }

    return () => {
      cancelHashScroll();
      document.removeEventListener("click", onAnchorClick);
      if (rafCallbackRef.current) gsap.ticker.remove(rafCallbackRef.current);
      lenis.destroy();
      lenisRef.current = null;
    };
  }, []);

  return null;
}
