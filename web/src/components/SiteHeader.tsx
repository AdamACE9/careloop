"use client";

import Link from "next/link";
import { motion, useScroll, useMotionValueEvent } from "motion/react";
import { useState } from "react";

/** The Loop, as a compact inline mark. Same geometry as the app icon. */
export function LoopGlyph({ className = "h-7 w-7" }: { className?: string }) {
  return (
    <svg viewBox="0 0 40 40" className={className} aria-hidden>
      <circle cx="20" cy="20" r="14" fill="none" stroke="currentColor" strokeWidth="4.5" />
    </svg>
  );
}

const NAV = [
  { href: "/#how", label: "How it works" },
  { href: "/dashboard", label: "Dashboard" },
];

/**
 * Sticky site header.
 *
 * Navy on every page rather than transparent-over-hero. A header that changes
 * colour by route is a small thing that goes wrong at exactly the wrong moment,
 * and a consistent dark bar reads more like a product than a landing-page trick.
 *
 * It does respond to scroll: the bar tightens and its border appears once you
 * leave the hero, which gives the page a sense of depth without the header ever
 * changing identity.
 *
 * Nav links use a shared layout indicator rather than per-link CSS underlines, so
 * the marker slides between items instead of fading in and out.
 */
export default function SiteHeader() {
  const { scrollY } = useScroll();
  const [scrolled, setScrolled] = useState(false);
  const [hovered, setHovered] = useState<string | null>(null);

  useMotionValueEvent(scrollY, "change", (latest) => {
    // Hysteresis, so a header that sits exactly on the threshold does not
    // flicker between states while the user makes small scroll adjustments.
    if (latest > 80 && !scrolled) setScrolled(true);
    else if (latest < 40 && scrolled) setScrolled(false);
  });

  return (
    <motion.header
      className="sticky top-0 z-50 backdrop-blur-md"
      animate={{
        backgroundColor: scrolled ? "rgba(15,26,56,0.92)" : "rgba(15,26,56,0.7)",
        borderBottomColor: scrolled ? "rgba(255,255,255,0.12)" : "rgba(255,255,255,0)",
      }}
      transition={{ duration: 0.35 }}
      style={{ borderBottomWidth: 1, borderBottomStyle: "solid" }}
    >
      <motion.div
        className="mx-auto flex max-w-7xl items-center justify-between px-6 md:px-10"
        animate={{ paddingTop: scrolled ? 10 : 16, paddingBottom: scrolled ? 10 : 16 }}
        transition={{ duration: 0.35 }}
      >
        <Link href="/" className="group flex items-center gap-3 text-white">
          <motion.span
            className="text-gold"
            whileHover={{ rotate: 180, scale: 1.1 }}
            transition={{ type: "spring", stiffness: 200, damping: 15 }}
          >
            <LoopGlyph />
          </motion.span>
          <span className="font-display text-xl tracking-tight transition-colors group-hover:text-gold">
            CareLoop
          </span>
        </Link>

        <nav
          className="flex items-center gap-1 sm:gap-2"
          onMouseLeave={() => setHovered(null)}
        >
          {NAV.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              onMouseEnter={() => setHovered(item.href)}
              className={`relative rounded-lg px-3 py-2 text-sm transition-colors ${
                item.href === "/#how" ? "hidden sm:block" : ""
              } ${hovered === item.href ? "text-white" : "text-white/65"}`}
            >
              {/* One shared pill that slides between items, rather than a
                  separate background fading in on each. */}
              {hovered === item.href && (
                <motion.span
                  layoutId="nav-pill"
                  className="absolute inset-0 rounded-lg bg-white/10"
                  transition={{ type: "spring", stiffness: 380, damping: 30 }}
                />
              )}
              <span className="relative">{item.label}</span>
            </Link>
          ))}

          <motion.div
            whileHover={{ scale: 1.04 }}
            whileTap={{ scale: 0.97 }}
            transition={{ type: "spring", stiffness: 400, damping: 22 }}
          >
            <Link
              href="/download"
              className="ml-2 block rounded-xl bg-gold px-5 py-2.5 text-sm font-semibold text-navy-deep shadow-[0_0_0_0_rgba(201,162,39,0.5)] transition-shadow duration-300 hover:shadow-[0_0_24px_-2px_rgba(201,162,39,0.6)]"
            >
              Download
            </Link>
          </motion.div>
        </nav>
      </motion.div>
    </motion.header>
  );
}
