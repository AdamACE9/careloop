import Link from "next/link";

/** The Loop, as a compact inline mark. Same geometry as the app icon. */
export function LoopGlyph({ className = "h-7 w-7" }: { className?: string }) {
  return (
    <svg viewBox="0 0 40 40" className={className} aria-hidden>
      <circle
        cx="20"
        cy="20"
        r="14"
        fill="none"
        stroke="currentColor"
        strokeWidth="4.5"
      />
    </svg>
  );
}

/**
 * Sticky site header.
 *
 * Navy on every page rather than transparent-over-hero: a header that changes colour by
 * route is a small thing that goes wrong at exactly the wrong moment, and a consistent
 * dark bar reads more like a product and less like a landing-page trick.
 */
export default function SiteHeader() {
  return (
    <header className="sticky top-0 z-50 border-b border-white/10 bg-navy-deep/85 backdrop-blur-md">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4 md:px-10">
        <Link
          href="/"
          className="flex items-center gap-3 text-white transition hover:text-gold"
        >
          <span className="text-gold">
            <LoopGlyph />
          </span>
          <span className="font-display text-xl tracking-tight">CareLoop</span>
        </Link>

        <nav className="flex items-center gap-2 sm:gap-6">
          <Link
            href="/#how"
            className="hidden rounded-lg px-2 py-1 text-sm text-white/70 transition hover:text-white sm:block"
          >
            How it works
          </Link>
          <Link
            href="/dashboard"
            className="rounded-lg px-2 py-1 text-sm text-white/70 transition hover:text-white"
          >
            Dashboard
          </Link>
          <Link
            href="/download"
            className="rounded-xl bg-gold px-5 py-2.5 text-sm font-semibold text-navy-deep transition hover:bg-gold-glow"
          >
            Download
          </Link>
        </nav>
      </div>
    </header>
  );
}
