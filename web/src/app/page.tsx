import Link from "next/link";
import LoopHero from "@/components/LoopHero";
import Reveal from "@/components/Reveal";
import SiteHeader from "@/components/SiteHeader";
import SiteFooter from "@/components/SiteFooter";
import ReasoningCard from "@/components/ReasoningCard";
import PhoneCallMock from "@/components/PhoneCallMock";

/**
 * The landing page.
 *
 * Structure follows the argument the product actually needs to win:
 *   1. the emotional problem (you cannot be there every day)
 *   2. why existing tools fail (a tap is not evidence)
 *   3. what CareLoop does differently (it listens, and it reasons across days)
 *   4. proof — the real reasoning trace, not a claim about one
 *   5. the dignity position (the part nobody else has built)
 *   6. download
 *
 * Section 4 is the centrepiece. Judges are scoring for visible autonomous reasoning, so
 * the page shows an actual escalation with its evidence rather than asserting that the
 * agent is clever.
 */
export default function Home() {
  return (
    <main className="min-h-screen bg-bone">
      <SiteHeader />

      {/* ---------------------------------------------------------------- Hero */}
      <section className="relative grain overflow-hidden bg-navy-deep">
        <div
          className="pointer-events-none absolute inset-0 opacity-70"
          style={{
            background:
              "radial-gradient(60% 50% at 70% 35%, #24376688 0%, transparent 70%)",
          }}
          aria-hidden
        />

        <div className="relative mx-auto grid max-w-7xl gap-12 px-6 pt-28 pb-24 md:grid-cols-2 md:items-center md:gap-8 md:px-10 md:pt-36 md:pb-32">
          <div>
            <p className="mb-6 text-sm tracking-[0.18em] text-gold uppercase">
              Agentic care companion
            </p>

            <h1 className="font-display text-5xl leading-[1.05] text-white md:text-7xl">
              Someone checks on your mother
              <span className="text-gold"> every morning.</span>
            </h1>

            <p className="mt-8 max-w-xl text-lg leading-relaxed text-white/70 md:text-xl">
              CareLoop phones her, listens to how she answers, and tells you when
              something is genuinely wrong — not every time she is five minutes late.
            </p>

            <div className="mt-10 flex flex-col gap-4 sm:flex-row">
              <Link
                href="/download"
                className="inline-flex items-center justify-center rounded-2xl bg-gold px-8 py-4 text-base font-semibold text-navy-deep transition hover:bg-gold-glow"
              >
                Set it up for a parent
              </Link>
              <Link
                href="/dashboard"
                className="inline-flex items-center justify-center rounded-2xl border border-white/25 px-8 py-4 text-base font-semibold text-white transition hover:bg-white/10"
              >
                See the family dashboard
              </Link>
            </div>

            <p className="mt-6 text-sm text-white/45">
              No call charges. It rings over wifi, like a video call.
            </p>
          </div>

          <div className="h-[340px] md:h-[520px]">
            <LoopHero />
          </div>
        </div>
      </section>

      {/* ------------------------------------------------------------- Problem */}
      <section className="mx-auto max-w-7xl px-6 py-24 md:px-10 md:py-32">
        <Reveal>
          <p className="text-sm tracking-[0.18em] text-gold-ink uppercase">
            The gap
          </p>
          <h2 className="mt-5 max-w-3xl font-display text-4xl leading-tight text-ink md:text-5xl">
            One missed dose is normal. Three in a week is a warning sign.
            Nothing currently notices the difference.
          </h2>
        </Reveal>

        <div className="mt-16 grid gap-10 md:grid-cols-3">
          {[
            {
              title: "Reminder apps verify nothing",
              body: "They log a tap. A tap is not evidence that a tablet was swallowed, and it cannot tell you she sounded confused while she did it.",
            },
            {
              title: "Nobody is watching for patterns",
              body: "A daughter three hours away hears about a problem after it becomes a crisis. The signal was there for a week; there was just no one to see it.",
            },
            {
              title: "The alternative costs a salary",
              body: "Full-time human care is out of reach for most families. The choice today is between a dumb reminder and a carer you cannot afford.",
            },
          ].map((item, i) => (
            <Reveal key={item.title} delay={i * 90}>
              <div className="border-t border-ink/12 pt-6">
                <h3 className="text-xl font-semibold text-ink">{item.title}</h3>
                <p className="mt-3 leading-relaxed text-slate-ink">{item.body}</p>
              </div>
            </Reveal>
          ))}
        </div>
      </section>

      {/* ------------------------------------------------------------ The loop */}
      <section id="how" className="bg-navy py-24 md:py-32">
        <div className="mx-auto max-w-7xl px-6 md:px-10">
          <Reveal>
            <p className="text-sm tracking-[0.18em] text-gold uppercase">
              How it works
            </p>
            <h2 className="mt-5 max-w-3xl font-display text-4xl leading-tight text-white md:text-5xl">
              A loop that closes itself.
            </h2>
          </Reveal>

          <div className="mt-16 grid gap-8 md:grid-cols-2">
            <div className="space-y-6">
              {[
                {
                  n: "01",
                  title: "It calls, properly",
                  body: "At a time she chooses, her phone rings like a real call — full screen, on the lock screen. Not a notification she will scroll past.",
                },
                {
                  n: "02",
                  title: "It listens to the answer",
                  body: "A real conversation. Cara hears hesitation, confusion, and the difference between “I took it” and “I think I took it.”",
                },
                {
                  n: "03",
                  title: "It checks while it talks",
                  body: "Mention a new painkiller mid-sentence and Cara checks it against everything else she takes — without the call going quiet.",
                },
                {
                  n: "04",
                  title: "It decides when to worry",
                  body: "Not on one miss. On a pattern, weighted by how serious that specific medication is. Then it explains itself, in plain English.",
                },
              ].map((step, i) => (
                <Reveal key={step.n} delay={i * 80}>
                  <div className="flex gap-6 rounded-2xl border border-white/10 bg-white/[0.03] p-6">
                    <span className="font-display text-2xl text-gold">{step.n}</span>
                    <div>
                      <h3 className="text-lg font-semibold text-white">
                        {step.title}
                      </h3>
                      <p className="mt-2 leading-relaxed text-white/65">
                        {step.body}
                      </p>
                    </div>
                  </div>
                </Reveal>
              ))}
            </div>

            <Reveal delay={120} className="flex items-center justify-center">
              <PhoneCallMock />
            </Reveal>
          </div>
        </div>
      </section>

      {/* --------------------------------------------------- Reasoning (proof) */}
      <section className="mx-auto max-w-7xl px-6 py-24 md:px-10 md:py-32">
        <Reveal>
          <p className="text-sm tracking-[0.18em] text-gold-ink uppercase">
            Why it alerted you
          </p>
          <h2 className="mt-5 max-w-3xl font-display text-4xl leading-tight text-ink md:text-5xl">
            Most AI tells you what it decided. Cara shows you the working.
          </h2>
          <p className="mt-6 max-w-2xl text-lg leading-relaxed text-slate-ink">
            This is a real escalation from the demo data — the reasoning, the evidence
            behind each step, and the options Cara weighed and rejected.
          </p>
        </Reveal>

        <Reveal delay={100}>
          <div className="mt-14">
            <ReasoningCard />
          </div>
        </Reveal>
      </section>

      {/* ------------------------------------------------------------- Dignity */}
      <section className="bg-cloud py-24 md:py-32">
        <div className="mx-auto max-w-7xl px-6 md:px-10">
          <div className="grid gap-14 md:grid-cols-2 md:items-center">
            <Reveal>
              <p className="text-sm tracking-[0.18em] text-gold-ink uppercase">
                Dignity
              </p>
              <h2 className="mt-5 font-display text-4xl leading-tight text-ink md:text-5xl">
                She sees everything you see.
              </h2>
              <p className="mt-6 text-lg leading-relaxed text-slate-ink">
                Nearly half of older adults are uncomfortable being monitored — even
                when the safety benefit is obvious. What resolves that discomfort is not
                a better privacy policy. It is genuine control.
              </p>
              <p className="mt-4 text-lg leading-relaxed text-slate-ink">
                So CareLoop is symmetrical. Every time Cara tells you something, she
                tells your mother she told you — and gives her the chance to correct the
                record. She can mute routine categories. Emergencies always reach you,
                and she knows that too, because hiding it would be the exact
                condescension we are trying to avoid.
              </p>
            </Reveal>

            <Reveal delay={120}>
              <div className="rounded-3xl border border-ink/10 bg-white p-8 shadow-sm">
                <p className="text-sm font-medium tracking-wide text-slate-ink uppercase">
                  What Margaret sees
                </p>
                <p className="mt-5 text-lg leading-relaxed text-ink">
                  “I told Sarah that you&apos;d missed your warfarin on Tuesday and
                  Thursday, and that you weren&apos;t sure whether you&apos;d taken it.”
                </p>
                <div className="mt-7 flex flex-wrap gap-3">
                  <span className="rounded-xl bg-good-surface px-5 py-3 text-sm font-semibold text-good">
                    That&apos;s right
                  </span>
                  <span className="rounded-xl border border-ink/15 px-5 py-3 text-sm font-semibold text-ink">
                    I&apos;d add something
                  </span>
                </div>
                <p className="mt-6 text-sm leading-relaxed text-slate-ink">
                  If she disputes it, her words appear on your dashboard beside Cara&apos;s
                  — not buried in a log.
                </p>
              </div>
            </Reveal>
          </div>
        </div>
      </section>

      {/* ------------------------------------------------------------ Download */}
      <section className="relative grain overflow-hidden bg-navy-deep py-24 md:py-32">
        <div className="relative mx-auto max-w-3xl px-6 text-center md:px-10">
          <Reveal>
            <h2 className="font-display text-4xl leading-tight text-white md:text-5xl">
              It takes about four minutes to set up.
            </h2>
            <p className="mt-6 text-lg leading-relaxed text-white/70">
              Most people set it up sitting next to their parent, once. After that it
              runs on its own.
            </p>
            <div className="mt-10 flex justify-center">
              <Link
                href="/download"
                className="inline-flex items-center justify-center rounded-2xl bg-gold px-10 py-5 text-lg font-semibold text-navy-deep transition hover:bg-gold-glow"
              >
                Download CareLoop
              </Link>
            </div>
            <p className="mt-5 text-sm text-white/45">
              Android · free while in early access
            </p>
          </Reveal>
        </div>
      </section>

      <SiteFooter />
    </main>
  );
}
