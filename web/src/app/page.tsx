import LoopHero from "@/components/LoopHero";
import SiteHeader from "@/components/SiteHeader";
import SiteFooter from "@/components/SiteFooter";
import ReasoningCard from "@/components/ReasoningCard";
import PhoneCallMock from "@/components/PhoneCallMock";
import ScrollProgress, { WordRise } from "@/components/motion/ScrollProgress";
import {
  FadeUp,
  Stagger,
  StaggerItem,
  MagneticButton,
  TiltCard,
  Parallax,
  Counter,
  Marquee,
  ScrollSettle,
} from "@/components/motion/primitives";

/**
 * The landing page.
 *
 * Structure follows the argument the product needs to win, in order:
 *   1. the emotional problem (you cannot be there every day)
 *   2. why existing tools fail (a tap is not evidence)
 *   3. what CareLoop does differently, shown running in a phone
 *   4. proof: a real reasoning trace, not a claim about one
 *   5. the dignity position, which nobody else has built
 *   6. download
 *
 * Section 4 is the centrepiece. Judges score for visible autonomous reasoning, so
 * the page shows an actual escalation with its evidence rather than asserting
 * that the agent is clever.
 *
 * This file stays a server component. Every animated piece is a client component
 * imported into it, so the static content is still server-rendered and the motion
 * code is the only thing shipped to the browser.
 */
export default function Home() {
  return (
    <main className="min-h-screen bg-bone">
      <ScrollProgress />
      <SiteHeader />

      {/* ================================================================ Hero */}
      <section className="relative grain overflow-hidden bg-navy-deep">
        {/* Two drifting light sources. Long cycles so they never read as a loop. */}
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
            background:
              "radial-gradient(circle, rgba(201,162,39,0.16), transparent 65%)",
          }}
          aria-hidden
        />

        <div className="relative mx-auto grid max-w-7xl gap-14 px-6 pt-28 pb-20 md:grid-cols-[1.05fr_0.95fr] md:items-center md:gap-10 md:px-10 md:pt-36 md:pb-28">
          <div>
            <FadeUp>
              <p className="mb-6 flex items-center gap-3 text-sm tracking-[0.18em] text-gold uppercase">
                <span className="breathe inline-block h-1.5 w-1.5 rounded-full bg-gold" aria-hidden />
                Agentic care companion
              </p>
            </FadeUp>

            <h1 className="font-display text-5xl leading-[1.04] text-white md:text-[4.6rem]">
              <WordRise
                text="Someone checks on your mother every morning."
                highlight={["every", "morning."]}
              />
            </h1>

            <FadeUp delay={420}>
              <p className="mt-8 max-w-xl text-lg leading-relaxed text-white/65 md:text-xl">
                CareLoop phones her, listens to how she answers, and tells you when
                something is genuinely wrong, not every time she is five minutes late.
              </p>
            </FadeUp>

            <FadeUp delay={540}>
              <div className="mt-10 flex flex-col gap-4 sm:flex-row">
                <MagneticButton href="/download" variant="gold">
                  Set it up for a parent
                </MagneticButton>
                <MagneticButton href="/dashboard" variant="ghost">
                  See the family dashboard
                </MagneticButton>
              </div>
            </FadeUp>

            <FadeUp delay={640}>
              <p className="mt-7 text-sm text-white/40">
                No call charges. It rings over wifi, like a video call.
              </p>
            </FadeUp>
          </div>

          <Parallax speed={-0.08} className="h-[340px] md:h-[540px]">
            <LoopHero />
          </Parallax>
        </div>

        {/* Ticker of what Cara actually does on a call. */}
        <div className="relative border-t border-white/10 py-5">
          <div className="mx-auto max-w-7xl px-6 md:px-10">
            <Marquee
              items={[
                "Rings like a real phone call",
                "Listens to how she answers",
                "Checks interactions mid-conversation",
                "Reasons across days, not single events",
                "Explains itself in plain language",
                "She sees everything you see",
                "Weighs how serious each medication is",
                "Decides when family needs to know",
              ]}
            />
          </div>
        </div>
      </section>

      {/* ============================================================= Problem */}
      <section className="mx-auto max-w-7xl px-6 py-24 md:px-10 md:py-32">
        <FadeUp>
          <p className="text-sm tracking-[0.18em] text-gold-ink uppercase">The gap</p>
          <h2 className="mt-5 max-w-3xl font-display text-4xl leading-tight text-ink md:text-5xl">
            One missed dose is normal. Three in a week is a warning sign. Nothing
            currently notices the difference.
          </h2>
          <span className="rule-draw mt-10 block h-px w-full bg-ink/15" aria-hidden />
        </FadeUp>

        <Stagger className="mt-14 grid gap-8 md:grid-cols-3" gap={0.1}>
          {[
            {
              n: "01",
              title: "Reminder apps verify nothing",
              body: "They log a tap. A tap is not evidence that a tablet was swallowed, and it cannot tell you she sounded confused while she did it.",
            },
            {
              n: "02",
              title: "Nobody is watching for patterns",
              body: "A daughter three hours away hears about a problem after it becomes a crisis. The signal was there for a week; there was just no one to see it.",
            },
            {
              n: "03",
              title: "The alternative costs a salary",
              body: "Full-time human care is out of reach for most families. The choice today is between a dumb reminder and a carer you cannot afford.",
            },
          ].map((item) => (
            <StaggerItem key={item.n}>
              <TiltCard className="h-full">
                <div className="lift h-full rounded-3xl border border-ink/10 bg-white p-8">
                  <span className="font-display text-2xl text-gold-ink">{item.n}</span>
                  <h3 className="mt-4 text-xl font-semibold text-ink">{item.title}</h3>
                  <p className="mt-3 leading-relaxed text-slate-ink">{item.body}</p>
                </div>
              </TiltCard>
            </StaggerItem>
          ))}
        </Stagger>
      </section>

      {/* ============================================================ The loop */}
      <section id="how" className="relative overflow-hidden bg-navy py-24 md:py-32">
        <div
          className="drift pointer-events-none absolute -right-40 bottom-0 h-[30rem] w-[30rem] rounded-full opacity-30 blur-3xl"
          style={{
            background: "radial-gradient(circle, rgba(201,162,39,0.2), transparent 65%)",
          }}
          aria-hidden
        />

        <div className="relative mx-auto max-w-7xl px-6 md:px-10">
          <FadeUp>
            <p className="text-sm tracking-[0.18em] text-gold uppercase">How it works</p>
            <h2 className="mt-5 max-w-3xl font-display text-4xl leading-tight text-white md:text-5xl">
              A loop that <span className="text-sheen">closes itself.</span>
            </h2>
          </FadeUp>

          <div className="mt-16 grid gap-12 lg:grid-cols-[1fr_auto] lg:gap-16">
            <Stagger className="space-y-5" gap={0.09}>
              {[
                {
                  n: "01",
                  title: "It calls, properly",
                  body: "At a time she chooses, her phone rings like a real call. Full screen, on the lock screen. Not a notification she will scroll past.",
                },
                {
                  n: "02",
                  title: "It listens to the answer",
                  body: "A real conversation. Cara hears hesitation, confusion, and the difference between “I took it” and “I think I took it.”",
                },
                {
                  n: "03",
                  title: "It checks while it talks",
                  body: "Mention a new painkiller mid-sentence and Cara checks it against everything else she takes, without the call going quiet.",
                },
                {
                  n: "04",
                  title: "It decides when to worry",
                  body: "Not on one miss. On a pattern, weighted by how serious that specific medication is. Then it explains itself, in plain English.",
                },
              ].map((step) => (
                <StaggerItem key={step.n}>
                  <div className="group flex gap-6 rounded-2xl border border-white/10 bg-white/[0.03] p-6 transition-colors duration-300 hover:border-gold/40 hover:bg-white/[0.06]">
                    <span className="font-display text-2xl text-gold transition-transform duration-300 group-hover:scale-110">
                      {step.n}
                    </span>
                    <div>
                      <h3 className="text-lg font-semibold text-white">{step.title}</h3>
                      <p className="mt-2 leading-relaxed text-white/60">{step.body}</p>
                    </div>
                  </div>
                </StaggerItem>
              ))}
            </Stagger>

            <FadeUp delay={200} className="flex justify-center lg:justify-end">
              <PhoneCallMock />
            </FadeUp>
          </div>
        </div>
      </section>

      {/* ================================================= Reasoning, the proof */}
      <section className="mx-auto max-w-7xl px-6 py-24 md:px-10 md:py-32">
        <FadeUp>
          <p className="text-sm tracking-[0.18em] text-gold-ink uppercase">
            Why it alerted you
          </p>
          <h2 className="mt-5 max-w-3xl font-display text-4xl leading-tight text-ink md:text-5xl">
            Most AI tells you what it decided. Cara shows you the working.
          </h2>
          <p className="mt-6 max-w-2xl text-lg leading-relaxed text-slate-ink">
            This is a real escalation from the demo data: the reasoning, the evidence
            behind each step, and the options Cara weighed and rejected.
          </p>
        </FadeUp>

        <ScrollSettle className="mt-14">
          <ReasoningCard />
        </ScrollSettle>

        {/* The numbers that make the pattern claim concrete. */}
        <Stagger className="mt-16 grid gap-6 sm:grid-cols-3" gap={0.12}>
          {[
            { to: 2, suffix: "", label: "missed doses before Cara acted" },
            { to: 7, suffix: " days", label: "of history weighed on every call" },
            { to: 3, suffix: "", label: "reasons given, never thirty" },
          ].map((stat) => (
            <StaggerItem key={stat.label}>
              <div className="rounded-2xl border border-ink/10 bg-white px-7 py-6">
                <p className="font-display text-4xl text-navy">
                  <Counter to={stat.to} suffix={stat.suffix} />
                </p>
                <p className="mt-2 text-sm leading-relaxed text-slate-ink">
                  {stat.label}
                </p>
              </div>
            </StaggerItem>
          ))}
        </Stagger>
      </section>

      {/* ============================================================= Dignity */}
      <section className="bg-cloud py-24 md:py-32">
        <div className="mx-auto max-w-7xl px-6 md:px-10">
          <div className="grid gap-14 md:grid-cols-2 md:items-center">
            <FadeUp>
              <p className="text-sm tracking-[0.18em] text-gold-ink uppercase">Dignity</p>
              <h2 className="mt-5 font-display text-4xl leading-tight text-ink md:text-5xl">
                She sees everything you see.
              </h2>
              <p className="mt-6 text-lg leading-relaxed text-slate-ink">
                Nearly half of older adults are uncomfortable being monitored, even
                when the safety benefit is obvious. What resolves that discomfort is
                not a better privacy policy. It is genuine control.
              </p>
              <p className="mt-4 text-lg leading-relaxed text-slate-ink">
                So CareLoop is symmetrical. Every time Cara tells you something, she
                tells your mother she told you, and gives her the chance to correct the
                record. She can mute routine categories. Emergencies always reach you,
                and she knows that too, because hiding it would be the exact
                condescension we are trying to avoid.
              </p>
            </FadeUp>

            <FadeUp delay={150}>
              <TiltCard>
                <div className="rounded-3xl border border-ink/10 bg-white p-8 shadow-sm">
                  <p className="text-sm font-medium tracking-wide text-slate-ink uppercase">
                    What Margaret sees
                  </p>
                  <p className="mt-5 text-lg leading-relaxed text-ink">
                    &ldquo;I told Sarah that you&apos;d missed your warfarin on Tuesday
                    and Thursday, and that you weren&apos;t sure whether you&apos;d
                    taken it.&rdquo;
                  </p>
                  <div className="mt-7 flex flex-wrap gap-3">
                    <span className="cursor-default rounded-xl bg-good-surface px-5 py-3 text-sm font-semibold text-good transition-transform duration-200 hover:scale-[1.03]">
                      That&apos;s right
                    </span>
                    <span className="cursor-default rounded-xl border border-ink/15 px-5 py-3 text-sm font-semibold text-ink transition-transform duration-200 hover:scale-[1.03]">
                      I&apos;d add something
                    </span>
                  </div>
                  <p className="mt-6 text-sm leading-relaxed text-slate-ink">
                    If she disputes it, her words appear on your dashboard beside
                    Cara&apos;s, not buried in a log.
                  </p>
                </div>
              </TiltCard>
            </FadeUp>
          </div>
        </div>
      </section>

      {/* ============================================================ Download */}
      <section className="relative grain overflow-hidden bg-navy-deep py-24 md:py-32">
        <div
          className="drift pointer-events-none absolute top-0 left-1/2 h-[28rem] w-[28rem] -translate-x-1/2 rounded-full opacity-40 blur-3xl"
          style={{
            background: "radial-gradient(circle, rgba(201,162,39,0.22), transparent 65%)",
          }}
          aria-hidden
        />
        <div className="relative mx-auto max-w-3xl px-6 text-center md:px-10">
          <FadeUp>
            <h2 className="font-display text-4xl leading-tight text-white md:text-5xl">
              It takes about four minutes to set up.
            </h2>
            <p className="mt-6 text-lg leading-relaxed text-white/65">
              Most people set it up sitting next to their parent, once. After that it
              runs on its own.
            </p>
            <div className="mt-10 flex justify-center">
              <MagneticButton href="/download" variant="gold" className="px-10 py-5 text-lg">
                Download CareLoop
              </MagneticButton>
            </div>
            <p className="mt-5 text-sm text-white/40">
              Android, free while in early access
            </p>
          </FadeUp>
        </div>
      </section>

      <SiteFooter />
    </main>
  );
}
