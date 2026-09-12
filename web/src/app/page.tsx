import LoopHero from "@/components/LoopHero";
import SiteHeader from "@/components/SiteHeader";
import SiteFooter from "@/components/SiteFooter";
import PhoneCallMock from "@/components/PhoneCallMock";
import EscalationNarrative from "@/components/EscalationNarrative";
import LenisScroll from "@/components/motion/LenisScroll";
import { Reveal, RevealGroup, SplitReveal, ParallaxLayer } from "@/components/motion/gsap-scroll";
import { PinnedSteps } from "@/components/motion/PinnedSteps";
import ScrollProgress from "@/components/motion/ScrollProgress";
import { MagneticButton, TiltCard, Marquee } from "@/components/motion/primitives";

/**
 * The landing page.
 *
 * ## Section order, and why
 *
 * Hero (the product, running) -> what CareLoop plainly is -> how it works, with
 * real specifics -> one real escalation, told straight -> the honest objection,
 * answered -> the action. That order was checked against how this category's
 * more credible sites are actually built: product visual first, plain language
 * before mechanism, mechanism before proof, one objection answered before the
 * ask. Two departures from that pattern, both deliberate:
 *
 * - The hero never uses the word "AI". Cara is described by what she does
 *   (calls, listens, checks, remembers), not by the category she belongs to.
 *   Most of the credible products in this space make the same choice, and the
 *   word adds nothing a worried adult child is looking for.
 * - The proof section shows exactly one escalation, as a plain narrative of
 *   what happened on real calls, with no confidence label and no list of
 *   rejected alternatives. Both of those are right for the caretaker who has
 *   opted into the dashboard's full reasoning view; on a first visit they read
 *   as a machine hedging rather than as a machine that noticed something real.
 *
 * ## Motion
 *
 * `LenisScroll` and the GSAP primitives in `motion/gsap-scroll.tsx` are the
 * only things doing scroll-linked work on this page: text splits and reveals
 * on entry, one scrubbed parallax layer, and a single pinned scene in "how it
 * works" (the phone mock pins while the four steps scroll past it, since
 * sequence is the entire point of that section). `MagneticButton`, `TiltCard`
 * and `Marquee`, used below, are the one exception: they answer to the cursor
 * or run continuously, not to scroll position, and rebuilding them in GSAP
 * would not have changed what they do, so they keep the spring-based
 * Framer Motion implementation already built for this page.
 *
 * Every section below is fully present, in its final layout, in the server-
 * rendered HTML. Nothing starts at `opacity: 0` in markup or CSS; see the note
 * at the top of gsap-scroll.tsx for exactly how that is enforced.
 */
export default function Home() {
  return (
    <main className="min-h-screen bg-cream">
      <LenisScroll />
      <ScrollProgress />
      <SiteHeader />

      {/* ================================================================ Hero */}
      <section className="relative grain overflow-hidden bg-navy-deep">
        <div
          className="pointer-events-none absolute inset-0 opacity-80"
          style={{
            background:
              "radial-gradient(55% 45% at 72% 32%, rgba(36,55,102,0.65) 0%, transparent 70%)",
          }}
          aria-hidden
        />
        <div
          className="drift pointer-events-none absolute -top-40 -left-40 h-[36rem] w-[36rem] rounded-full opacity-40 blur-3xl"
          style={{
            background: "radial-gradient(circle, rgba(201,162,39,0.16), transparent 65%)",
          }}
          aria-hidden
        />

        <div className="relative mx-auto grid max-w-7xl gap-14 px-6 pt-28 pb-20 md:grid-cols-[1.05fr_0.95fr] md:items-center md:gap-10 md:px-10 md:pt-36 md:pb-28">
          <div>
            <p className="mb-6 flex items-center gap-3 text-sm tracking-[0.18em] text-gold uppercase">
              <span className="breathe inline-block h-1.5 w-1.5 rounded-full bg-gold" aria-hidden />
              Not a reminder app
            </p>

            <h1 className="font-display text-5xl leading-[1.04] text-white md:text-[4.6rem]">
              <SplitReveal
                text="Someone checks on your mother every morning."
                highlight={["every", "morning."]}
              />
            </h1>

            <Reveal delay={420}>
              <p className="mt-8 max-w-xl text-lg leading-relaxed text-white/65 md:text-xl">
                CareLoop calls her, has an actual conversation, and tells you when
                something is genuinely wrong, not every time she is five minutes late.
              </p>
            </Reveal>

            <Reveal delay={540}>
              <div className="mt-10 flex flex-col gap-4 sm:flex-row">
                <MagneticButton href="/download" variant="gold">
                  Set it up for a parent
                </MagneticButton>
                <MagneticButton href="/dashboard" variant="ghost">
                  See the family dashboard
                </MagneticButton>
              </div>
            </Reveal>

            <Reveal delay={640}>
              <p className="mt-7 text-sm text-white/40">
                No call charges. It rings over wifi, like a video call.
              </p>
            </Reveal>
          </div>

          <ParallaxLayer speed={-8} className="h-[340px] md:h-[540px]">
            <LoopHero />
          </ParallaxLayer>
        </div>

        <div className="relative border-t border-white/10 py-5">
          <div className="mx-auto max-w-7xl px-6 md:px-10">
            <Marquee
              items={[
                "Rings like a real phone call",
                "A real conversation, not a script",
                "Checks new medications while she's still talking",
                "Remembers the last several days, not just today",
                "Explains what she decided, in plain language",
                "She sees everything you see",
              ]}
            />
          </div>
        </div>
      </section>

      {/* ==================================================== What it plainly is */}
      <section className="mx-auto max-w-4xl px-6 py-24 text-center md:px-10 md:py-32">
        <Reveal>
          <p className="text-sm tracking-[0.18em] text-gold-ink uppercase">
            What CareLoop actually is
          </p>
          <h2 className="mt-5 font-display text-4xl leading-tight text-ink md:text-5xl">
            Once a day, at a time she picked, her phone rings. Really rings.
          </h2>
          <p className="mx-auto mt-7 max-w-2xl text-lg leading-relaxed text-slate-ink">
            Full screen, on the lock screen, like a call from a person, because
            that is what it is meant to feel like. Cara talks with her, checks
            whatever medication comes up against everything else she takes, and
            remembers what happened yesterday and the day before, not just today.
          </p>
          <p className="mx-auto mt-4 max-w-2xl text-lg leading-relaxed text-slate-ink">
            Most days end with nothing to report. That is the point: the calls
            that matter are the ones that would otherwise have gone unnoticed.
          </p>
        </Reveal>
      </section>

      {/* ============================================================ The loop */}
      <section id="how" className="relative overflow-hidden bg-brown py-24 md:py-32">
        <div
          className="drift pointer-events-none absolute -right-40 bottom-0 h-[30rem] w-[30rem] rounded-full opacity-30 blur-3xl"
          style={{
            background: "radial-gradient(circle, rgba(201,162,39,0.2), transparent 65%)",
          }}
          aria-hidden
        />

        <div className="relative mx-auto max-w-7xl px-6 md:px-10">
          <Reveal>
            <p className="text-sm tracking-[0.18em] text-gold uppercase">How it works</p>
            <h2 className="mt-5 max-w-3xl font-display text-4xl leading-tight text-white md:text-5xl">
              Four things happen on every call, in this order.
            </h2>
          </Reveal>

          <div className="mt-16">
            <PinnedSteps
              steps={[
                {
                  n: "01",
                  title: "It calls, properly",
                  body: "At a time she chose, her phone rings like a real call. Full screen, on the lock screen. Not a notification she has to notice and open.",
                },
                {
                  n: "02",
                  title: "It listens to how she answers",
                  body: "A real conversation, not a script. Cara notices hesitation and uncertainty, the difference between “I took it” and “I think I took it.”",
                },
                {
                  n: "03",
                  title: "It checks while it's still talking",
                  body: "Mention a new medication mid-sentence and Cara checks it against everything else she takes, out loud, without the call going quiet.",
                },
                {
                  n: "04",
                  title: "It weighs the pattern, not the moment",
                  body: "One missed dose is not a pattern. A repeat, on a medication that matters, is. Cara decides on days of calls, not a single one, then says why.",
                },
              ]}
              visual={<PhoneCallMock />}
            />
          </div>
        </div>
      </section>

      {/* ================================================================ Proof */}
      <section className="mx-auto max-w-6xl px-6 py-24 md:px-10 md:py-32">
        <Reveal className="mx-auto max-w-3xl text-center">
          <p className="text-sm tracking-[0.18em] text-gold-ink uppercase">
            One real week
          </p>
          <h2 className="mt-5 font-display text-4xl leading-tight text-ink md:text-5xl">
            Most companion apps say they check in. Here is what Cara actually
            noticed, and what she did about it.
          </h2>
        </Reveal>

        <RevealGroup className="mt-14" stagger={0.12}>
          <EscalationNarrative />
        </RevealGroup>
      </section>

      {/* ================================================== The honest question */}
      <section className="bg-bone py-24 md:py-32">
        <div className="mx-auto max-w-7xl px-6 md:px-10">
          <div className="grid gap-14 md:grid-cols-2 md:items-center">
            <Reveal>
              <p className="text-sm tracking-[0.18em] text-gold-ink uppercase">
                The honest question
              </p>
              <h2 className="mt-5 font-display text-4xl leading-tight text-ink md:text-5xl">
                Isn&apos;t this just watching her?
              </h2>
              <p className="mt-6 text-lg leading-relaxed text-slate-ink">
                Nearly half of older adults are uncomfortable being monitored, even
                when the safety benefit is obvious. What resolves that is not a
                longer privacy policy. It is genuine control over what gets shared.
              </p>
              <p className="mt-4 text-lg leading-relaxed text-slate-ink">
                So CareLoop is symmetrical. Every time Cara tells Sarah something,
                she tells Margaret first, in the same words, and Margaret can
                correct the record. She can mute the routine stuff. A real safety
                concern still reaches Sarah, and Margaret knows that too, because
                hiding it would be exactly the condescension this is meant to avoid.
              </p>
            </Reveal>

            <Reveal delay={150}>
              <TiltCard>
                <div className="rounded-3xl border border-ink/10 bg-white p-8 shadow-sm">
                  <p className="text-sm font-medium tracking-wide text-slate-ink uppercase">
                    What Margaret sees, the same morning
                  </p>
                  <p className="mt-5 text-lg leading-relaxed text-ink">
                    &ldquo;I told Sarah you&apos;d missed your warfarin on Tuesday
                    and Thursday, and that you weren&apos;t sure whether
                    you&apos;d taken it.&rdquo;
                  </p>
                  <div className="mt-7 flex flex-wrap gap-3">
                    <span className="cursor-default rounded-xl bg-good-surface px-5 py-3 text-sm font-semibold text-good">
                      That&apos;s right
                    </span>
                    <span className="cursor-default rounded-xl border border-ink/15 px-5 py-3 text-sm font-semibold text-ink">
                      I&apos;d add something
                    </span>
                  </div>
                  <p className="mt-6 text-sm leading-relaxed text-slate-ink">
                    If she disagreed, her own words would appear here beside
                    Cara&apos;s, not buried in a log only Sarah can read.
                  </p>
                </div>
              </TiltCard>
            </Reveal>
          </div>
        </div>
      </section>

      {/* ============================================================ Download */}
      <section className="relative grain overflow-hidden bg-brown py-24 md:py-32">
        <div
          className="drift pointer-events-none absolute top-0 left-1/2 h-[28rem] w-[28rem] -translate-x-1/2 rounded-full opacity-40 blur-3xl"
          style={{
            background: "radial-gradient(circle, rgba(201,162,39,0.22), transparent 65%)",
          }}
          aria-hidden
        />
        <div className="relative mx-auto max-w-3xl px-6 text-center md:px-10">
          <Reveal>
            <h2 className="font-display text-4xl leading-tight text-white md:text-5xl">
              It takes about four minutes to set up.
            </h2>
            <p className="mt-6 text-lg leading-relaxed text-white/65">
              Most people do it sitting next to their parent, once, so the two of
              them can pick the call time together. After that it runs on its own.
            </p>
            <div className="mt-10 flex justify-center">
              <MagneticButton href="/download" variant="gold" className="px-10 py-5 text-lg">
                Download CareLoop
              </MagneticButton>
            </div>
            <p className="mt-5 text-sm text-white/40">
              Android, free while in early access
            </p>
          </Reveal>
        </div>
      </section>

      <SiteFooter />
    </main>
  );
}
