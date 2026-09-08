import type { Metadata } from "next";
import Link from "next/link";
import SiteHeader from "@/components/SiteHeader";
import { LoopGlyph } from "@/components/SiteHeader";

export const metadata: Metadata = {
  title: "Family sign in — CareLoop",
};

/**
 * Caretaker sign-in.
 *
 * UI only, by design for this build. There is no auth backend, and the form deliberately
 * does not pretend otherwise: the primary action goes straight to the dashboard and is
 * labelled as a demo, rather than staging a fake authentication that would mislead anyone
 * evaluating this.
 *
 * TODO(backend): wire to Firebase Auth (email link is a better fit than passwords for this
 * audience — the caretaker signs in rarely, and password reset flows are a common support
 * burden). On success, redirect to /dashboard.
 */
export default function LoginPage() {
  return (
    <main className="min-h-screen bg-navy-deep">
      <SiteHeader />

      <section className="mx-auto flex max-w-md flex-col justify-center px-6 py-20 md:py-28">
        <div className="mb-8 flex justify-center text-gold">
          <LoopGlyph className="h-12 w-12" />
        </div>

        <h1 className="text-center font-display text-4xl text-white">
          Welcome back
        </h1>
        <p className="mt-4 text-center leading-relaxed text-white/60">
          Sign in to see how your mother is doing.
        </p>

        <form className="mt-10 space-y-5" action="/dashboard">
          <div>
            <label
              htmlFor="email"
              className="block text-sm font-medium text-white/80"
            >
              Email address
            </label>
            <input
              id="email"
              name="email"
              type="email"
              autoComplete="email"
              placeholder="sarah@example.com"
              className="mt-2 w-full rounded-2xl border border-white/15 bg-white/5 px-5 py-4 text-white placeholder:text-white/30 focus:border-gold focus:outline-none"
            />
          </div>

          <div>
            <label
              htmlFor="password"
              className="block text-sm font-medium text-white/80"
            >
              Password
            </label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              placeholder="••••••••"
              className="mt-2 w-full rounded-2xl border border-white/15 bg-white/5 px-5 py-4 text-white placeholder:text-white/30 focus:border-gold focus:outline-none"
            />
          </div>

          <Link
            href="/dashboard"
            className="block w-full rounded-2xl bg-gold px-6 py-4 text-center text-base font-semibold text-navy-deep transition hover:bg-gold-glow"
          >
            Sign in
          </Link>
        </form>

        <p className="mt-6 text-center text-xs leading-relaxed text-white/40">
          Demo build — authentication is not connected. Signing in opens the dashboard
          with example data.
        </p>

        <p className="mt-10 text-center text-sm text-white/50">
          Setting CareLoop up for the first time?{" "}
          <Link href="/download" className="text-gold hover:underline">
            Start here
          </Link>
        </p>
      </section>
    </main>
  );
}
