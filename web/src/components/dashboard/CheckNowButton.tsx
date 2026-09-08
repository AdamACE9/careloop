"use client";

import { useState } from "react";

type State = "idle" | "sending" | "sent";

/**
 * Manual "check on her now".
 *
 * Fires the same push-call flow a scheduled check-in uses. The confirmation copy is
 * deliberately about *Margaret's* experience — "her phone will ring" — rather than about
 * the system's ("request queued"). The caretaker is picturing their mother's kitchen, not
 * a message bus.
 *
 * It also sets an honest expectation: she may not answer immediately, and Cara will decide
 * whether to retry. Promising an instant answer would make a normal outcome feel like a
 * failure.
 *
 * TODO(backend): POST to a Cloud Function that sends a high-priority FCM data message to
 * the elder's device, which triggers the CallStyle notification. Handle the failure case
 * visibly — a silent failure here is worse than an error, because the caretaker will
 * believe a call is coming.
 */
export default function CheckNowButton() {
  const [state, setState] = useState<State>("idle");

  async function handleClick() {
    setState("sending");
    // Stand-in for the network round trip.
    await new Promise((resolve) => setTimeout(resolve, 1100));
    setState("sent");
    window.setTimeout(() => setState("idle"), 6000);
  }

  return (
    <div className="flex flex-col items-start gap-3 sm:items-end">
      <button
        onClick={handleClick}
        disabled={state !== "idle"}
        className="w-full rounded-2xl bg-gold px-7 py-4 font-semibold text-navy-deep transition hover:bg-gold-glow disabled:cursor-not-allowed disabled:opacity-70 sm:w-auto"
      >
        {state === "idle" && "Check on her now"}
        {state === "sending" && "Calling…"}
        {state === "sent" && "Cara is calling Margaret"}
      </button>

      {state === "sent" && (
        <p className="max-w-xs text-sm leading-relaxed text-white/60 sm:text-right">
          Her phone is ringing now. If she doesn&apos;t pick up, Cara will decide
          whether to try again shortly.
        </p>
      )}
    </div>
  );
}
