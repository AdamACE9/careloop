import { primaryEscalation, confidencePhrase } from "@/lib/demo-data";

/**
 * The "why did it alert me" view.
 *
 * Design rules here come straight from explainability research for non-technical readers:
 *
 * - **Three reasons, not thirty.** Showing every signal considered reads as evasive and
 *   overwhelms a lay reader. Three, each tied to specific evidence, reads as reasoning.
 * - **Confidence in words, not percentages.** "I'm quite sure" is trusted; "78.3%
 *   confident" reads as false precision and lowers trust.
 * - **Each step names its evidence** — a date, a call, a direct quote. A reasoning trace
 *   that cannot be traced back to something that actually happened is just a plausible
 *   story.
 * - **What it decided *against* is shown too.** Rejected alternatives are the clearest
 *   evidence that a judgement was made rather than a threshold tripped. This is the part
 *   most products omit, and it is the most convincing part.
 *
 * Uses `<details>` for the alternatives so progressive disclosure works with no JavaScript.
 */
export default function ReasoningCard() {
  const e = primaryEscalation;

  return (
    <article className="overflow-hidden rounded-3xl border border-ink/10 bg-white shadow-sm">
      {/* Header */}
      <div className="border-b border-ink/10 bg-navy px-8 py-7 md:px-10">
        <div className="flex flex-wrap items-center gap-3">
          <span className="rounded-full bg-concern-surface px-3 py-1 text-xs font-semibold tracking-wide text-concern uppercase">
            Needs attention
          </span>
          <span className="text-sm text-white/50">{e.raisedAt}</span>
        </div>
        <h3 className="mt-4 font-display text-2xl leading-snug text-white md:text-3xl">
          {e.headline}
        </h3>
      </div>

      <div className="grid gap-10 px-8 py-9 md:grid-cols-5 md:px-10">
        {/* Cara's explanation, in her own voice */}
        <div className="md:col-span-3">
          <p className="text-xs font-semibold tracking-[0.16em] text-slate-ink uppercase">
            What Cara told Sarah
          </p>
          <div className="mt-5 space-y-4">
            {e.explanation.split("\n\n").map((para, i) => (
              <p key={i} className="leading-relaxed text-ink">
                {para}
              </p>
            ))}
          </div>

          <div className="mt-8 rounded-2xl bg-good-surface px-5 py-4">
            <p className="text-sm font-semibold text-good">
              Margaret confirmed this
            </p>
            <p className="mt-1 text-sm leading-relaxed text-ink/70">
              She saw the same message in her app and agreed with it. If she had
              disagreed, her words would appear here beside Cara&apos;s.
            </p>
          </div>
        </div>

        {/* The working */}
        <div className="md:col-span-2">
          <p className="text-xs font-semibold tracking-[0.16em] text-slate-ink uppercase">
            How she got there
          </p>

          <ol className="mt-5 space-y-5">
            {e.reasoning.map((step, i) => (
              <li key={i} className="relative pl-8">
                <span className="absolute top-0.5 left-0 flex h-5 w-5 items-center justify-center rounded-full bg-navy text-[11px] font-semibold text-white">
                  {i + 1}
                </span>
                <p className="font-medium text-ink">{step.observation}</p>
                <p className="mt-1 text-sm text-slate-ink">{step.evidence}</p>
              </li>
            ))}
          </ol>

          <div className="mt-7 border-t border-ink/10 pt-5">
            <p className="text-sm text-slate-ink">
              Cara&apos;s confidence:{" "}
              <span className="font-semibold text-ink">
                {confidencePhrase[e.confidence]}
              </span>
            </p>
          </div>

          {e.alternativesConsidered.length > 0 && (
            <details className="group mt-5">
              <summary className="cursor-pointer list-none text-sm font-semibold text-navy underline decoration-gold decoration-2 underline-offset-4">
                What she considered instead
                <span className="ml-1 inline-block transition group-open:rotate-90">
                  ›
                </span>
              </summary>
              <ul className="mt-4 space-y-3">
                {e.alternativesConsidered.map((alt, i) => (
                  <li
                    key={i}
                    className="rounded-xl bg-cloud px-4 py-3 text-sm leading-relaxed text-ink/80"
                  >
                    {alt}
                  </li>
                ))}
              </ul>
            </details>
          )}
        </div>
      </div>
    </article>
  );
}
