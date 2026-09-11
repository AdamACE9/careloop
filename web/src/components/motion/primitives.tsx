"use client";

import {
  motion,
  useMotionValue,
  useSpring,
  useScroll,
  useTransform,
  useInView,
  useReducedMotion,
  type MotionValue,
} from "motion/react";
import Link from "next/link";
import {
  useRef,
  useEffect,
  useState,
  type ReactNode,
  type MouseEvent,
} from "react";

/**
 * Motion primitives.
 *
 * Spring-based rather than duration-based throughout. A duration says "take 400ms";
 * a spring says "have this much weight", and weight is what separates motion that
 * feels designed from motion that feels animated. It also means an interrupted
 * animation (a cursor leaving mid-transition) resolves naturally instead of
 * snapping or queueing.
 *
 * Every component here checks `useReducedMotion` and degrades to a static,
 * fully-visible state. That is not a nicety: a reveal that never fires leaves
 * content permanently invisible, which is a far worse failure than no animation.
 */

// -----------------------------------------------------------------------------
// Springs
// -----------------------------------------------------------------------------

/** Heavy and settled. For large elements where overshoot would look flippant. */
const SPRING_SOFT = { stiffness: 120, damping: 20, mass: 0.6 };

/** Responsive, slight overshoot. For things that follow a cursor. */
const SPRING_SNAPPY = { stiffness: 260, damping: 18, mass: 0.4 };

/**
 * In-view detection with a guaranteed escape hatch.
 *
 * `useInView` is IntersectionObserver underneath, and Chrome suspends observer
 * callbacks for pages it is not painting: an occluded window, a background tab,
 * some headless contexts. For an entrance animation that is catastrophic rather
 * than cosmetic, because the element's initial state is `opacity: 0` and it stays
 * there forever. The page renders blank.
 *
 * So: reveal on intersection, or after [REVEAL_FAILSAFE_MS] regardless. Animating
 * something nobody watched is free; hiding content permanently is not.
 */
const REVEAL_FAILSAFE_MS = 2200;

function useRevealed(ref: React.RefObject<Element | null>): boolean {
  const inView = useInView(ref, { once: true, margin: "0px 0px -70px 0px" });
  const [failsafe, setFailsafe] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => setFailsafe(true), REVEAL_FAILSAFE_MS);
    return () => clearTimeout(timer);
  }, []);

  return inView || failsafe;
}

// -----------------------------------------------------------------------------
// FadeUp
// -----------------------------------------------------------------------------

/**
 * Entrance on scroll.
 *
 * Replaces the CSS `.reveal` class with something spring-driven and staggerable.
 * `once: true` because re-animating on every scroll-past is the single most
 * common way a site with motion becomes annoying to actually read.
 */
export function FadeUp({
  children,
  delay = 0,
  y = 28,
  className = "",
}: {
  children: ReactNode;
  delay?: number;
  y?: number;
  className?: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const revealed = useRevealed(ref);
  const reduced = useReducedMotion();

  return (
    <motion.div
      ref={ref}
      className={className}
      initial={reduced ? false : { opacity: 0, y }}
      animate={revealed || reduced ? { opacity: 1, y: 0 } : undefined}
      transition={{ ...SPRING_SOFT, delay: delay / 1000 }}
    >
      {children}
    </motion.div>
  );
}

/**
 * Staggered children.
 *
 * The stagger is applied by the parent rather than by each child computing its
 * own delay, so inserting or removing an item cannot leave a gap in the rhythm.
 */
export function Stagger({
  children,
  className = "",
  gap = 0.08,
}: {
  children: ReactNode;
  className?: string;
  gap?: number;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const revealed = useRevealed(ref);
  const reduced = useReducedMotion();

  return (
    <motion.div
      ref={ref}
      className={className}
      initial={reduced ? false : "hidden"}
      animate={revealed || reduced ? "visible" : undefined}
      variants={{
        hidden: {},
        visible: { transition: { staggerChildren: gap } },
      }}
    >
      {children}
    </motion.div>
  );
}

export function StaggerItem({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <motion.div
      className={className}
      variants={{
        hidden: { opacity: 0, y: 24 },
        visible: { opacity: 1, y: 0, transition: SPRING_SOFT },
      }}
    >
      {children}
    </motion.div>
  );
}

// -----------------------------------------------------------------------------
// Magnetic button
// -----------------------------------------------------------------------------

/**
 * A control that leans toward the cursor as it approaches.
 *
 * The displacement is deliberately small (a fraction of the distance, capped) and
 * spring-damped. Magnetic buttons go wrong when they move far enough that the
 * user has to chase them; the effect should register as responsiveness, not as a
 * moving target. The label counter-moves slightly, which reads as parallax depth
 * within the button itself.
 */
export function MagneticButton({
  children,
  href,
  onClick,
  variant = "gold",
  className = "",
}: {
  children: ReactNode;
  href?: string;
  onClick?: () => void;
  variant?: "gold" | "ghost" | "navy";
  className?: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const reduced = useReducedMotion();

  const x = useMotionValue(0);
  const y = useMotionValue(0);
  const sx = useSpring(x, SPRING_SNAPPY);
  const sy = useSpring(y, SPRING_SNAPPY);

  // Label drifts slightly less than the button, creating depth.
  const labelX = useTransform(sx, (v) => v * 0.35);
  const labelY = useTransform(sy, (v) => v * 0.35);

  function handleMove(event: MouseEvent<HTMLDivElement>) {
    if (reduced) return;
    const node = ref.current;
    if (!node) return;
    const rect = node.getBoundingClientRect();
    const dx = event.clientX - (rect.left + rect.width / 2);
    const dy = event.clientY - (rect.top + rect.height / 2);
    // A quarter of the offset, capped, so it never outruns the cursor.
    x.set(Math.max(-14, Math.min(14, dx * 0.25)));
    y.set(Math.max(-10, Math.min(10, dy * 0.25)));
  }

  function reset() {
    x.set(0);
    y.set(0);
  }

  const base =
    "relative inline-flex items-center justify-center rounded-2xl px-8 py-4 text-base font-semibold overflow-hidden";
  const skin =
    variant === "gold"
      ? "bg-gold text-navy-deep"
      : variant === "navy"
        ? "bg-navy text-white"
        // 2px and a brighter stroke. At 1px white/25 on navy the border was
        // effectively invisible, so the secondary action read as bare text.
        : "border-2 border-white/35 text-white hover:border-gold/70 transition-colors duration-300";

  const inner = (
    <motion.div
      ref={ref}
      style={{ x: sx, y: sy }}
      onMouseMove={handleMove}
      onMouseLeave={reset}
      whileTap={{ scale: 0.96 }}
      className={`${base} ${skin} ${className} group`}
    >
      {/* Sheen that sweeps across on hover. Pure transform, so it composites. */}
      <span
        className="pointer-events-none absolute inset-0 -translate-x-full bg-gradient-to-r from-transparent via-white/25 to-transparent transition-transform duration-700 group-hover:translate-x-full"
        aria-hidden
      />
      <motion.span style={{ x: labelX, y: labelY }} className="relative">
        {children}
      </motion.span>
    </motion.div>
  );

  if (href) {
    return (
      <Link href={href} className="inline-block" onClick={onClick}>
        {inner}
      </Link>
    );
  }
  return (
    <button type="button" onClick={onClick} className="inline-block">
      {inner}
    </button>
  );
}

// -----------------------------------------------------------------------------
// Tilt card
// -----------------------------------------------------------------------------

/**
 * A card that tilts in 3D toward the cursor, with a glare that tracks it.
 *
 * Rotation is capped at a few degrees. The instinct is to push this further, but
 * past roughly 8 degrees text starts to visibly skew and the card reads as a
 * gimmick rather than as a physical object catching the light.
 */
export function TiltCard({
  children,
  className = "",
  glare = true,
}: {
  children: ReactNode;
  className?: string;
  glare?: boolean;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const reduced = useReducedMotion();

  const rx = useMotionValue(0);
  const ry = useMotionValue(0);
  const gx = useMotionValue(50);
  const gy = useMotionValue(50);

  const srx = useSpring(rx, SPRING_SOFT);
  const sry = useSpring(ry, SPRING_SOFT);

  function handleMove(event: MouseEvent<HTMLDivElement>) {
    if (reduced) return;
    const node = ref.current;
    if (!node) return;
    const rect = node.getBoundingClientRect();
    const px = (event.clientX - rect.left) / rect.width;
    const py = (event.clientY - rect.top) / rect.height;
    ry.set((px - 0.5) * 9);
    rx.set((0.5 - py) * 9);
    gx.set(px * 100);
    gy.set(py * 100);
  }

  function reset() {
    rx.set(0);
    ry.set(0);
  }

  const glarePosition = useTransform(
    [gx, gy] as MotionValue<number>[],
    ([lx, ly]: number[]) =>
      `radial-gradient(circle at ${lx}% ${ly}%, rgba(255,255,255,0.16), transparent 55%)`,
  );

  return (
    <motion.div
      ref={ref}
      onMouseMove={handleMove}
      onMouseLeave={reset}
      style={{
        rotateX: srx,
        rotateY: sry,
        transformPerspective: 900,
        transformStyle: "preserve-3d",
      }}
      className={`relative ${className}`}
    >
      {children}
      {glare && !reduced && (
        <motion.span
          className="pointer-events-none absolute inset-0 rounded-[inherit] opacity-0 transition-opacity duration-300 hover:opacity-100"
          style={{ backgroundImage: glarePosition }}
          aria-hidden
        />
      )}
    </motion.div>
  );
}

// -----------------------------------------------------------------------------
// Parallax
// -----------------------------------------------------------------------------

/**
 * Scroll-driven vertical offset.
 *
 * `speed` is a multiplier of the element's travel through the viewport. Keep it
 * under about 0.2 for text: anything more and the element visibly lags the page,
 * which reads as a rendering fault rather than as depth.
 */
export function Parallax({
  children,
  speed = 0.12,
  className = "",
}: {
  children: ReactNode;
  speed?: number;
  className?: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const reduced = useReducedMotion();
  const { scrollYProgress } = useScroll({
    target: ref,
    offset: ["start end", "end start"],
  });
  const y = useTransform(scrollYProgress, [0, 1], ["0%", `${speed * 100}%`]);

  return (
    <motion.div ref={ref} style={reduced ? undefined : { y }} className={className}>
      {children}
    </motion.div>
  );
}

// -----------------------------------------------------------------------------
// Counter
// -----------------------------------------------------------------------------

/**
 * Counts up when scrolled into view.
 *
 * Eases out rather than running linearly, so it decelerates into the final value
 * instead of stopping dead. Writes straight to `textContent` rather than through
 * React state: sixty state updates a second on a number would re-render the
 * surrounding section for no reason.
 */
export function Counter({
  to,
  suffix = "",
  duration = 1600,
  className = "",
}: {
  to: number;
  suffix?: string;
  duration?: number;
  className?: string;
}) {
  const ref = useRef<HTMLSpanElement>(null);
  // Same failsafe as the reveals. A counter that never fires does not just miss
  // an animation, it displays "0", which is actively wrong information rather
  // than merely unanimated.
  const revealed = useRevealed(ref);
  const reduced = useReducedMotion();

  useEffect(() => {
    const node = ref.current;
    if (!node) return;

    if (reduced || !revealed) {
      if (reduced) node.textContent = `${to}${suffix}`;
      return;
    }

    let frame = 0;
    const start = performance.now();

    function tick(now: number) {
      const t = Math.min(1, (now - start) / duration);
      // easeOutCubic
      const eased = 1 - Math.pow(1 - t, 3);
      if (node) node.textContent = `${Math.round(eased * to)}${suffix}`;
      if (t < 1) frame = requestAnimationFrame(tick);
    }

    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [revealed, to, suffix, duration, reduced]);

  return (
    <span ref={ref} className={className}>
      0{suffix}
    </span>
  );
}

// -----------------------------------------------------------------------------
// Marquee
// -----------------------------------------------------------------------------

/**
 * Continuous horizontal ticker.
 *
 * The content is rendered twice and the track translated by exactly -50%, which
 * makes the loop seamless without measuring anything. Pauses on hover so anyone
 * who wants to actually read an item can.
 */
export function Marquee({
  items,
  speed = 38,
}: {
  items: string[];
  speed?: number;
}) {
  const reduced = useReducedMotion();

  if (reduced) {
    return (
      <div className="flex flex-wrap gap-x-8 gap-y-2 text-sm text-white/45">
        {items.map((item) => (
          <span key={item}>{item}</span>
        ))}
      </div>
    );
  }

  return (
    <div className="group relative overflow-hidden">
      {/* Edges fade so items enter and leave rather than being clipped. */}
      <div className="pointer-events-none absolute inset-y-0 left-0 z-10 w-24 bg-gradient-to-r from-navy-deep to-transparent" />
      <div className="pointer-events-none absolute inset-y-0 right-0 z-10 w-24 bg-gradient-to-l from-navy-deep to-transparent" />

      <motion.div
        className="flex w-max gap-10 group-hover:[animation-play-state:paused]"
        animate={{ x: ["0%", "-50%"] }}
        transition={{ duration: speed, ease: "linear", repeat: Infinity }}
      >
        {[...items, ...items].map((item, i) => (
          <span
            key={`${item}-${i}`}
            className="flex shrink-0 items-center gap-3 text-sm whitespace-nowrap text-white/45"
          >
            <span className="h-1 w-1 rounded-full bg-gold" aria-hidden />
            {item}
          </span>
        ))}
      </motion.div>
    </div>
  );
}

// -----------------------------------------------------------------------------
// Scroll-linked section scale
// -----------------------------------------------------------------------------

/**
 * Settles a section into place as it arrives, then lets it recede slightly.
 *
 * Very restrained values. This is the effect most likely to make a page feel
 * cheap if overdone, and the goal is that a visitor notices the page feels alive
 * without being able to point at what is moving.
 */
export function ScrollSettle({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const reduced = useReducedMotion();
  const { scrollYProgress } = useScroll({
    target: ref,
    offset: ["start end", "end start"],
  });

  const scale = useTransform(scrollYProgress, [0, 0.35, 0.75, 1], [0.96, 1, 1, 0.985]);
  const opacity = useTransform(scrollYProgress, [0, 0.2, 0.85, 1], [0.4, 1, 1, 0.7]);

  return (
    <motion.div
      ref={ref}
      style={reduced ? undefined : { scale, opacity }}
      className={className}
    >
      {children}
    </motion.div>
  );
}

/** Client-only guard, so SSR and first paint agree before motion starts. */
export function useMounted(): boolean {
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  return mounted;
}
