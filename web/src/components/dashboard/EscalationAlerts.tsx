'use client';

import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { useRouter } from 'next/navigation';
import { useEscalations } from '@/lib/careloop-service';
import type { Escalation } from '@/lib/demo-data';

/**
 * Two values that exist only in the browser, read without a hydration mismatch
 * and without setting state from an effect.
 *
 * `useSyncExternalStore` is the right tool rather than the clever one: it is
 * built for exactly this, a value the server cannot know and the client must
 * read before first paint. Reading them in an effect instead would render one
 * frame of the wrong answer and trip React's own rule against setting state
 * synchronously in an effect body.
 *
 * `subscribe` does nothing. Neither value changes underneath us: permission
 * changes because the button below asked for it, and dismissal changes because
 * the button below wrote it, and both paths update React state directly. The
 * store is the initial read, not the source of truth thereafter.
 */
const noSubscribe = () => () => {};

function readPermission(): NotificationPermission | 'unsupported' {
  if (typeof window === 'undefined' || !('Notification' in window)) return 'unsupported';
  return Notification.permission;
}

function readDismissed(): boolean {
  try {
    return window.localStorage.getItem('careloop.alertsDismissed') === '1';
  } catch {
    // A browser that refuses storage simply gets the prompt again. Harmless.
    return false;
  }
}

/**
 * Raises a real browser notification the moment Cara escalates.
 *
 * ## Why this exists
 *
 * The product's central claim is that Cara decides on her own to tell the
 * family. Until now she wrote the escalation to Firestore and it appeared on
 * the dashboard, which means the family found out if and when they happened to
 * open the dashboard. "She told Sarah straight after the Thursday call" was
 * true of the record and not of Sarah.
 *
 * ## Why not push
 *
 * Real push, surviving a closed browser, needs an FCM web-push certificate that
 * is generated in the Firebase console and cannot be minted from here. Rather
 * than ship the whole path around a placeholder key that nobody could test,
 * this does the part that works with no credentials at all: the dashboard
 * already holds a live Firestore listener, so it can raise the notification
 * itself. That covers the tab being open in the background, which is the
 * ordinary case for someone who checks on a parent during the day, and it is
 * honest about not covering a closed browser. See CLAUDE.md for the one console
 * step that would upgrade this to real push.
 *
 * ## The two ways this could lie
 *
 * **Announcing history as news.** The first snapshot carries every escalation
 * ever raised. Firing on those would greet a caretaker with an alert about
 * something from last Tuesday, at which point no alert from this component can
 * be trusted again. So the first snapshot only ever seeds the baseline.
 *
 * **Announcing the same thing twice.** A reload produces a fresh first
 * snapshot, so the baseline is also written to localStorage, keyed per patient.
 * Storage can be unavailable or cleared, and every read and write here tolerates
 * that: the fallback is the in-memory baseline, which still holds for the life
 * of the tab.
 *
 * Permission is requested from an explicit button and never on load. An
 * unprompted permission dialog is refused by most people and by some browsers
 * outright, and this project's own rule is that permissions are asked for in
 * context, with the reason stated.
 */
export default function EscalationAlerts({
  patientId,
  patientName,
  isExample,
}: {
  patientId: string;
  patientName: string;
  isExample: boolean;
}) {
  const router = useRouter();
  const { data: escalations } = useEscalations(patientId);

  // Server renders "default"/false, so the card is absent in the HTML and
  // appears on hydration only for someone who has neither granted nor refused.
  const initialPermission = useSyncExternalStore(noSubscribe, readPermission, () => 'default' as const);
  const initialDismissed = useSyncExternalStore(noSubscribe, readDismissed, () => false);

  const [answered, setAnswered] = useState<NotificationPermission | null>(null);
  const [dismissedNow, setDismissedNow] = useState(false);

  const permission = answered ?? initialPermission;
  const dismissed = dismissedNow || initialDismissed;

  // Ids already accounted for. `null` means "no baseline yet", which is what
  // distinguishes the first snapshot from a later one carrying something new.
  const seenRef = useRef<Set<string> | null>(null);

  const storageKey = `careloop.seenEscalations.${patientId}`;

  // Client-side navigation, so clicking the notification does not reload the
  // whole dashboard and drop the live listeners it was notifying from.
  const openReasoning = useCallback(() => {
    window.focus();
    router.push('/dashboard/reasoning');
  }, [router]);

  // Reset the baseline when the patient changes, so switching between two
  // linked people cannot carry one person's ids over to the other.
  useEffect(() => {
    seenRef.current = null;
  }, [patientId]);

  useEffect(() => {
    // The example household must never raise an alert. A notification saying
    // Margaret missed her warfarin, on a machine belonging to someone who has
    // not linked to anybody, is the invented-record problem escaping the page
    // it was labelled on.
    if (isExample || !patientId) return;
    if (typeof window === 'undefined' || !('Notification' in window)) return;

    const ids = escalations.map((e) => e.id);

    if (seenRef.current === null) {
      let stored: string[] = [];
      try {
        stored = JSON.parse(window.localStorage.getItem(storageKey) ?? '[]') as string[];
      } catch {
        stored = [];
      }
      // Seed from whichever is larger: what this browser has already been told
      // about, plus everything present right now. Nothing in the first snapshot
      // is news.
      seenRef.current = new Set([...stored, ...ids]);
      persist(seenRef.current);
      return;
    }

    const fresh = escalations.filter((e) => !seenRef.current!.has(e.id));
    if (fresh.length === 0) return;

    for (const escalation of fresh) {
      seenRef.current.add(escalation.id);
      if (Notification.permission === 'granted') notify(escalation, patientName, openReasoning);
    }
    persist(seenRef.current);

    function persist(set: Set<string>) {
      try {
        // Bounded, so a long-running account cannot grow this without limit.
        window.localStorage.setItem(storageKey, JSON.stringify([...set].slice(-100)));
      } catch {
        // In-memory baseline still holds for this tab.
      }
    }
  }, [escalations, patientId, patientName, isExample, storageKey, openReasoning]);

  async function enable() {
    try {
      setAnswered(await Notification.requestPermission());
    } catch {
      setAnswered('denied');
    }
  }

  function dismiss() {
    setDismissedNow(true);
    try {
      window.localStorage.setItem('careloop.alertsDismissed', '1');
    } catch {
      // Dismissal not remembered across reloads. Mildly annoying, not broken.
    }
  }

  const shouldOffer =
    !isExample && !dismissed && permission === 'default' && Boolean(patientId);

  if (!shouldOffer) return null;

  return (
    <div className="mb-6 flex flex-col gap-3 rounded-2xl border border-navy/15 bg-white px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <p className="text-sm font-semibold text-ink">
          Get told the moment Cara decides something
        </p>
        <p className="mt-1 text-sm leading-relaxed text-slate-ink">
          A notification on this device when she escalates, so you do not have to
          keep this page open and watch it. It works while this tab is open, in
          the background included.
        </p>
      </div>
      <div className="flex shrink-0 gap-2">
        <button
          onClick={dismiss}
          className="rounded-xl px-4 py-2.5 text-sm font-medium text-slate-ink transition hover:bg-cloud"
        >
          Not now
        </button>
        <button
          onClick={enable}
          className="rounded-xl bg-navy px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-navy-deep"
        >
          Turn on
        </button>
      </div>
    </div>
  );
}

/**
 * The notification itself.
 *
 * It carries Cara's own headline rather than a generic "new alert", because a
 * notification that says nothing forces the person to open the page to find out
 * whether it was urgent, which defeats the point of sending one. `tag` is the
 * escalation id so that two tabs open on the same dashboard collapse into one
 * notification instead of firing twice.
 */
function notify(escalation: Escalation, patientName: string, onClick: () => void) {
  const urgent = escalation.severity === 'urgent';
  const title = urgent
    ? `Cara needs you to know about ${patientName}`
    : `Cara noticed something about ${patientName}`;

  try {
    const notification = new Notification(title, {
      body: escalation.headline,
      tag: escalation.id,
      // No `icon`. There is no PNG in `public/` to point at, and a notification
      // referencing a 404 renders a broken image next to a message about
      // somebody's medication. The browser's own default is better than that.
      requireInteraction: urgent,
    });
    notification.onclick = onClick;
  } catch {
    // Some browsers throw on the constructor outside a service worker. The
    // escalation is still on the dashboard; this was the extra, not the record.
  }
}
