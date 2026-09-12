"use client";

import { useState } from "react";
import Link from "next/link";

/**
 * Two-step install flow: explain, then download.
 *
 * Step one is not a formality. Android's "unknown sources" prompt is unskinnable and
 * alarming, and it is the highest-risk drop-off point in the product for this audience.
 * Showing exactly what is about to appear, including a mock of the real dialog, converts
 * it from a warning into a step someone expects.
 *
 * The copy avoids two failure modes:
 *  - it never says the warning is meaningless (it is not; it exists for good reason)
 *  - it never uses jargon like "sideloading" without saying what it means
 *
 * Calm on purpose: this route carries none of the landing page's Lenis/GSAP scroll
 * work. Someone here is mid-task, often installing this on a parent's phone while
 * standing next to them, and the right feeling is fast and certain, not cinematic.
 */

/**
 * Served from this site's own root rather than a third-party release host, so
 * there is exactly one place to look when the build changes. It is a debug
 * build, not a Play Store release, which is exactly why Android shows the
 * warning explained on the first screen.
 */
const APK_URL = "/careloop.apk";

export default function DownloadFlow() {
  const [step, setStep] = useState<"explain" | "download">("explain");

  return (
    <section className="mx-auto max-w-3xl px-6 py-20 md:px-10 md:py-28">
      {/* Progress */}
      <div className="mb-12 flex items-center gap-3">
        <StepDot active label="1" />
        <div className="h-px flex-1 bg-ink/15" />
        <StepDot active={step === "download"} label="2" />
      </div>

      {step === "explain" ? (
        <div>
          <p className="text-sm tracking-[0.18em] text-gold-ink uppercase">
            Before you download
          </p>
          <h1 className="mt-5 font-display text-4xl leading-tight text-ink md:text-5xl">
            Your phone is about to show you a warning. It is expected.
          </h1>

          <p className="mt-7 text-lg leading-relaxed text-slate-ink">
            In a moment, your phone will say this app is from an{" "}
            <strong className="text-ink">unknown source</strong>. That message means
            CareLoop is not on the Google Play Store yet, and is a debug build we
            publish directly.
          </p>
          <p className="mt-4 text-lg leading-relaxed text-slate-ink">
            It does <strong className="text-ink">not</strong> mean the app is unsafe.
          </p>

          {/* A mock of the real dialog, so it is recognised on sight. */}
          <div className="mt-10 rounded-3xl border border-ink/10 bg-white p-7 shadow-sm">
            <p className="text-xs font-semibold tracking-[0.16em] text-slate-ink uppercase">
              This is what you will see
            </p>
            <div className="mt-5 rounded-2xl bg-cloud p-6">
              <div className="flex gap-4">
                <div
                  className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-ink/10 text-xl"
                  aria-hidden
                >
                  ⚠
                </div>
                <div>
                  <p className="font-semibold text-ink">
                    For your security, your phone is not allowed to install unknown
                    apps from this source.
                  </p>
                  <p className="mt-3 text-sm text-slate-ink">
                    Tap <strong className="text-ink">Settings</strong>, then turn on{" "}
                    <strong className="text-ink">Allow from this source</strong>, then
                    press back.
                  </p>
                </div>
              </div>
            </div>
            <p className="mt-5 text-sm leading-relaxed text-slate-ink">
              You only have to do this once. After CareLoop is installed, you will never
              see this screen again.
            </p>
          </div>

          <div className="mt-10 rounded-3xl bg-navy p-7 text-white">
            <p className="font-display text-xl">Doing this for a parent?</p>
            <p className="mt-3 leading-relaxed text-white/70">
              Most people set CareLoop up sitting next to the person it is for. It is
              worth doing it together, the app will ask a few questions that only they
              can answer, like what time they want to be called.
            </p>
          </div>

          <button
            onClick={() => setStep("download")}
            className="mt-10 w-full rounded-2xl bg-gold px-8 py-5 text-lg font-semibold text-navy-deep transition hover:bg-gold-bright"
          >
            I understand, continue
          </button>
        </div>
      ) : (
        <div>
          <p className="text-sm tracking-[0.18em] text-gold-ink uppercase">
            Download
          </p>
          <h1 className="mt-5 font-display text-4xl leading-tight text-ink md:text-5xl">
            Ready when you are.
          </h1>
          <p className="mt-7 text-lg leading-relaxed text-slate-ink">
            CareLoop runs on Android phones running Android 8 or newer. This is a
            debug build while CareLoop is in early access, so the version number
            you see after installing may move faster than a normal app update.
          </p>

          <div className="mt-10 rounded-3xl border border-gold/40 bg-white p-8 text-center">
            <a
              href={APK_URL}
              className="inline-flex items-center justify-center gap-3 rounded-2xl bg-navy px-9 py-5 text-lg font-semibold text-white transition hover:bg-navy-soft"
            >
              <svg
                viewBox="0 0 24 24"
                className="h-6 w-6"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.2"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden
              >
                <path d="M12 3v12" />
                <path d="m7 12 5 5 5-5" />
                <path d="M5 21h14" />
              </svg>
              Download CareLoop for Android
            </a>
            <p className="mx-auto mt-4 max-w-md text-sm leading-relaxed text-slate-ink">
              Android 8 or newer. Downloads directly from this site, not a
              third-party store.
            </p>
          </div>

          <ol className="mt-12 space-y-6">
            {[
              {
                t: "Open the downloaded file",
                b: "Your phone will ask permission to install it. That is the screen we just showed you.",
              },
              {
                t: "CareLoop will introduce itself",
                b: "Five short screens. Large text, no typing beyond a name.",
              },
              {
                t: "Choose a time to be called",
                b: "This belongs to the person being called. They can change it whenever they like.",
              },
            ].map((s, i) => (
              <li key={s.t} className="flex gap-5">
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-navy text-sm font-semibold text-white">
                  {i + 1}
                </span>
                <div>
                  <p className="font-semibold text-ink">{s.t}</p>
                  <p className="mt-1 leading-relaxed text-slate-ink">{s.b}</p>
                </div>
              </li>
            ))}
          </ol>

          <div className="mt-12 flex flex-col gap-4 sm:flex-row">
            <button
              onClick={() => setStep("explain")}
              className="rounded-2xl border border-ink/20 px-7 py-4 font-semibold text-ink transition hover:bg-ink/5"
            >
              Back
            </button>
            <Link
              href="/dashboard"
              className="flex-1 rounded-2xl bg-navy px-7 py-4 text-center font-semibold text-white transition hover:bg-navy-soft"
            >
              I&apos;m the family member, show me the dashboard
            </Link>
          </div>
        </div>
      )}
    </section>
  );
}

function StepDot({ active, label }: { active: boolean; label: string }) {
  return (
    <span
      className={`flex h-9 w-9 items-center justify-center rounded-full text-sm font-semibold transition ${
        active ? "bg-navy text-white" : "bg-ink/10 text-ink/40"
      }`}
    >
      {label}
    </span>
  );
}
