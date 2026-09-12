import { primaryEscalation } from "@/lib/demo-data";

/**
 * The single piece of proof the landing page leans on.
 *
 * This is not the dashboard's reasoning view. That surface (ReasoningCard, used
 * on /dashboard/reasoning) is built for a caretaker who wants the full working:
 * every signal, a confidence word, what was weighed and rejected. It is right
 * for that audience because they asked for depth.
 *
 * A first-time visitor deciding whether to trust this product has not asked for
 * that. Shown a confidence label or a list of rejected alternatives, a person
 * meeting the product for the first time reads it as a machine showing its
 * uncertainty, not its competence. So this component tells the same real
 * escalation, the one in demo-data.ts, as what it actually was: four things
 * that happened, in order, on real calls, ending in a decision and the exact
 * words Cara used to explain it. No score. No "alternatives considered." Just
 * what she noticed, what she checked, and what she said.
 */
export default function EscalationNarrative() {
  const e = primaryEscalation;

  const beats = [
    {
      day: "Tuesday",
      body: "Margaret's morning call. She hadn't taken her warfarin, her heart-rhythm medication, and wasn't certain whether she'd simply forgotten to say so or forgotten to take it.",
    },
    {
      day: "Thursday",
      body: "Warfarin missed again. Margaret said, unprompted, “I’m not sure if I took it or not, love”, the same uncertainty as Tuesday, not a clean miss either time.",
    },
    {
      day: "Same call",
      body: "She also mentioned taking ibuprofen for her knee. Cara checked it against her medication list while the conversation continued, ibuprofen and warfarin together raise the risk of bleeding, and flagged it rather than waiting for the call to end.",
    },
  ];

  return (
    <div className="overflow-hidden rounded-3xl border border-ink/10 bg-white shadow-sm">
      <ol className="grid gap-px bg-ink/8 sm:grid-cols-3">
        {beats.map((beat, i) => (
          <li key={beat.day} data-reveal-item className="bg-white p-7 md:p-8">
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-navy text-sm font-semibold text-white">
              {i + 1}
            </span>
            <p className="mt-4 text-xs font-semibold tracking-[0.16em] text-gold-ink uppercase">
              {beat.day}
            </p>
            <p className="mt-2 leading-relaxed text-ink">{beat.body}</p>
          </li>
        ))}
      </ol>

      <div className="border-t border-ink/10 bg-navy px-8 py-9 md:px-10 md:py-10">
        <p className="text-xs font-semibold tracking-[0.16em] text-gold uppercase">
          What Cara decided, and told Sarah that morning
        </p>
        <blockquote className="mt-5 font-display text-2xl leading-snug text-white md:text-3xl">
          &ldquo;{e.headline}.&rdquo;
        </blockquote>
        <div className="mt-6 space-y-4 text-white/75">
          <p className="leading-relaxed">
            One missed dose, on its own, would not have been worth a message. Two,
            with the same uncertainty both times, on the medication that matters
            most for her heart rhythm, was. She didn&apos;t wait to see if it
            happened a third time, warfarin is the one medication where waiting
            carries real risk, so she told Sarah straight after the Thursday call.
          </p>
          <p className="leading-relaxed">
            She told Margaret first that she was going to. Margaret agreed, and
            confirmed the message was right when she saw it in her own app.
          </p>
        </div>
      </div>
    </div>
  );
}
