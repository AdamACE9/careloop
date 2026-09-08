import Link from "next/link";
import { LoopGlyph } from "./SiteHeader";

export default function SiteFooter() {
  return (
    <footer className="border-t border-white/10 bg-navy-deep">
      <div className="mx-auto max-w-7xl px-6 py-14 md:px-10">
        <div className="flex flex-col gap-10 md:flex-row md:items-start md:justify-between">
          <div className="max-w-sm">
            <div className="flex items-center gap-3 text-white">
              <span className="text-gold">
                <LoopGlyph />
              </span>
              <span className="font-display text-xl">CareLoop</span>
            </div>
            <p className="mt-4 text-sm leading-relaxed text-white/55">
              An agentic care companion that calls, listens, and knows the difference
              between a bad day and a pattern.
            </p>
          </div>

          <div className="flex gap-14">
            <div>
              <p className="text-xs tracking-[0.16em] text-white/40 uppercase">
                Product
              </p>
              <ul className="mt-4 space-y-3 text-sm">
                <li>
                  <Link href="/#how" className="text-white/70 transition hover:text-gold">
                    How it works
                  </Link>
                </li>
                <li>
                  <Link
                    href="/dashboard"
                    className="text-white/70 transition hover:text-gold"
                  >
                    Family dashboard
                  </Link>
                </li>
                <li>
                  <Link
                    href="/download"
                    className="text-white/70 transition hover:text-gold"
                  >
                    Download
                  </Link>
                </li>
              </ul>
            </div>

            <div>
              <p className="text-xs tracking-[0.16em] text-white/40 uppercase">
                Access
              </p>
              <ul className="mt-4 space-y-3 text-sm">
                <li>
                  <Link href="/login" className="text-white/70 transition hover:text-gold">
                    Family sign in
                  </Link>
                </li>
              </ul>
            </div>
          </div>
        </div>

        <div className="mt-12 border-t border-white/10 pt-8">
          <p className="text-xs leading-relaxed text-white/40">
            CareLoop is a prototype and is not a medical device. It does not provide
            medical advice, diagnosis, or emergency response, and it is not a substitute
            for professional care. In an emergency, call your local emergency number.
          </p>
        </div>
      </div>
    </footer>
  );
}
