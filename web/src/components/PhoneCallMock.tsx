"use client";

import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { useEffect, useRef, useState } from "react";
import { callScript, liveInteraction } from "@/lib/demo-data";

/**
 * The product, running, in a phone.
 *
 * This is the single most important object on the landing page. Everything else
 * describes CareLoop; this shows it. So it plays the whole signature interaction
 * as a three-act loop rather than a static screenshot:
 *
 *   1. **Ringing.** A real incoming call, not a notification. Breathing Loop,
 *      labelled answer and decline controls.
 *   2. **Connecting.** A brief beat where the ring contracts. Short on purpose;
 *      the interesting part is on either side of it.
 *   3. **Live.** Waveform, transcript arriving line by line, and the moment that
 *      matters: Margaret mentions ibuprofen, a second arc starts counter-rotating
 *      on the Loop, and the interaction surfaces while the conversation carries on
 *      underneath. That concurrency is the entire argument for calling this
 *      agentic rather than scripted, and it is worth showing rather than claiming.
 *
 * Then it loops, because a visitor who arrives mid-cycle should still see the
 * beginning within a few seconds.
 *
 * ## Motion notes
 *
 * The waveform is driven by a sine sum rather than randomness. Random bar heights
 * read as a loading spinner; a waveform with structure reads as a voice. Amplitude
 * is tied to who is speaking, so it visibly quietens while Cara listens.
 *
 * Under `prefers-reduced-motion` it holds the live state fully populated, with no
 * cycling, no waveform animation and no typing.
 */

type Phase = "ringing" | "connecting" | "live";

const RING_MS = 3200;
const CONNECT_MS = 900;
const LINE_MS = 1700;
const HOLD_MS = 3400;

export default function PhoneCallMock() {
  const reduced = useReducedMotion();
  const [phase, setPhase] = useState<Phase>("ringing");
  const [visibleLines, setVisibleLines] = useState(0);
  const [checking, setChecking] = useState(false);
  const [showInteraction, setShowInteraction] = useState(false);
  const [started, setStarted] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // Start only once seen, so someone scrolling past does not arrive mid-call.
  //
  // Carries a failsafe because Chrome suspends IntersectionObserver on pages it
  // is not painting, and without it this would sit frozen on frame one forever.
  // Playing unseen is a far cheaper failure than never playing.
  useEffect(() => {
    const node = containerRef.current;
    if (!node || typeof IntersectionObserver === "undefined") {
      setStarted(true);
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          setStarted(true);
          observer.disconnect();
        }
      },
      { threshold: 0 },
    );
    observer.observe(node);
    const failsafe = window.setTimeout(() => setStarted(true), 2500);
    return () => {
      observer.disconnect();
      clearTimeout(failsafe);
    };
  }, []);

  // The timeline.
  useEffect(() => {
    if (!started) return;

    if (reduced) {
      setPhase("live");
      setVisibleLines(callScript.length);
      setShowInteraction(true);
      return;
    }

    const timers: number[] = [];
    let cancelled = false;

    function schedule(fn: () => void, delay: number) {
      timers.push(window.setTimeout(() => !cancelled && fn(), delay));
    }

    function runCycle() {
      if (cancelled) return;

      setPhase("ringing");
      setVisibleLines(0);
      setChecking(false);
      setShowInteraction(false);

      schedule(() => setPhase("connecting"), RING_MS);
      schedule(() => setPhase("live"), RING_MS + CONNECT_MS);

      const liveStart = RING_MS + CONNECT_MS + 400;

      callScript.forEach((line, i) => {
        schedule(() => {
          setVisibleLines(i + 1);
          if (line.flag === "checking") {
            setChecking(true);
            schedule(() => setShowInteraction(true), 1400);
          }
        }, liveStart + i * LINE_MS);
      });

      const cycleLength = liveStart + callScript.length * LINE_MS + HOLD_MS;
      schedule(runCycle, cycleLength);
    }

    runCycle();
    return () => {
      cancelled = true;
      timers.forEach(clearTimeout);
    };
  }, [started, reduced]);

  const caraSpeaking =
    phase === "live" && callScript[visibleLines - 1]?.speaker === "cara";

  return (
    <div ref={containerRef} className="w-full max-w-[330px] [perspective:1400px]">
      <motion.div
        // A slow drift, so the phone sits in the page rather than on it.
        animate={reduced ? undefined : { y: [0, -10, 0], rotateZ: [0, 0.4, 0] }}
        transition={{ duration: 8, repeat: Infinity, ease: "easeInOut" }}
        whileHover={reduced ? undefined : { rotateY: -6, rotateX: 3, scale: 1.02 }}
        className="relative [transform-style:preserve-3d]"
      >
        {/* Glow beneath, so it reads as lit rather than pasted on. */}
        <div
          className="pointer-events-none absolute -inset-8 rounded-[3rem] opacity-70 blur-3xl"
          style={{
            background:
              "radial-gradient(circle at 50% 40%, rgba(201,162,39,0.22), transparent 70%)",
          }}
          aria-hidden
        />

        {/* Bezel */}
        <div className="relative rounded-[2.75rem] border border-white/15 bg-gradient-to-b from-[#2a2f3d] to-[#12151d] p-[3px] shadow-[0_40px_80px_-20px_rgba(0,0,0,0.7)]">
          <div className="rounded-[2.6rem] bg-black p-[2px]">
            <div className="relative overflow-hidden rounded-[2.5rem] bg-gradient-to-b from-navy to-navy-deep">
              {/* Screen glare, fixed to the glass rather than the content. */}
              <div
                className="pointer-events-none absolute -top-1/4 -left-1/4 z-30 h-[150%] w-[70%] rotate-12 opacity-[0.07]"
                style={{
                  background:
                    "linear-gradient(105deg, transparent 30%, white 50%, transparent 70%)",
                }}
                aria-hidden
              />

              <StatusBar />
              <Notch />

              <div className="relative h-[520px]">
                <AnimatePresence mode="wait">
                  {phase === "ringing" && <RingingScreen key="ringing" />}
                  {phase === "connecting" && <ConnectingScreen key="connecting" />}
                  {phase === "live" && (
                    <LiveScreen
                      key="live"
                      visibleLines={visibleLines}
                      checking={checking}
                      showInteraction={showInteraction}
                      caraSpeaking={caraSpeaking}
                      reduced={Boolean(reduced)}
                    />
                  )}
                </AnimatePresence>
              </div>
            </div>
          </div>
        </div>
      </motion.div>

      <p className="mt-6 text-center text-xs leading-relaxed text-white/40">
        A real check-in. Cara checks the interaction in the background while the
        conversation keeps going.
      </p>
    </div>
  );
}

// -----------------------------------------------------------------------------
// Chrome
// -----------------------------------------------------------------------------

function Notch() {
  return (
    <div
      className="absolute top-0 left-1/2 z-30 h-6 w-32 -translate-x-1/2 rounded-b-2xl bg-black"
      aria-hidden
    />
  );
}

function StatusBar() {
  return (
    <div className="relative z-20 flex items-center justify-between px-7 pt-3 pb-1 text-[10px] font-medium text-white/70">
      <span>9:00</span>
      <div className="flex items-center gap-1" aria-hidden>
        {/* Signal */}
        <div className="flex items-end gap-[2px]">
          {[3, 5, 7, 9].map((h) => (
            <span
              key={h}
              className="w-[2px] rounded-sm bg-white/70"
              style={{ height: h }}
            />
          ))}
        </div>
        {/* Battery */}
        <div className="ml-1 flex h-[9px] w-[18px] items-center rounded-[3px] border border-white/60 p-[1px]">
          <span className="h-full w-[70%] rounded-[1px] bg-white/70" />
        </div>
      </div>
    </div>
  );
}

// -----------------------------------------------------------------------------
// Act 1: ringing
// -----------------------------------------------------------------------------

function RingingScreen() {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0, scale: 1.04 }}
      transition={{ duration: 0.4 }}
      className="absolute inset-0 flex flex-col items-center px-7 pt-10 pb-9"
    >
      <motion.p
        className="text-[10px] tracking-[0.22em] text-gold uppercase"
        animate={{ opacity: [0.5, 1, 0.5] }}
        transition={{ duration: 2.2, repeat: Infinity, ease: "easeInOut" }}
      >
        Incoming check-in
      </motion.p>

      <div className="relative mt-14 flex h-36 w-36 items-center justify-center">
        {/* Expanding rings, the visual equivalent of a ringtone. */}
        {[0, 1, 2].map((i) => (
          <motion.span
            key={i}
            className="absolute rounded-full border border-gold/40"
            style={{ width: 110, height: 110 }}
            animate={{ scale: [1, 1.85], opacity: [0.55, 0] }}
            transition={{
              duration: 2.4,
              repeat: Infinity,
              delay: i * 0.8,
              ease: "easeOut",
            }}
            aria-hidden
          />
        ))}

        <motion.div
          animate={{ scale: [1, 1.045, 1] }}
          transition={{ duration: 2.6, repeat: Infinity, ease: "easeInOut" }}
        >
          <LoopMarkSvg size={110} />
        </motion.div>
      </div>

      <motion.p
        className="mt-9 font-display text-4xl text-white"
        initial={{ y: 10, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ delay: 0.15 }}
      >
        Cara
      </motion.p>
      <p className="mt-1.5 text-sm text-white/55">Your daily check-in</p>

      <div className="mt-auto flex w-full items-start justify-around">
        <CallAction label="Decline" tone="decline" />
        <CallAction label="Answer" tone="answer" pulse />
      </div>
    </motion.div>
  );
}

function CallAction({
  label,
  tone,
  pulse = false,
}: {
  label: string;
  tone: "answer" | "decline";
  pulse?: boolean;
}) {
  const isAnswer = tone === "answer";
  return (
    <div className="flex flex-col items-center gap-2.5">
      <motion.div
        className={`relative flex h-14 w-14 items-center justify-center rounded-full ${
          isAnswer ? "bg-[#1E7A5A]" : "bg-[#B3261E]"
        }`}
        animate={pulse ? { scale: [1, 1.07, 1] } : undefined}
        transition={{ duration: 1.4, repeat: Infinity, ease: "easeInOut" }}
      >
        {pulse && (
          <motion.span
            className="absolute inset-0 rounded-full bg-[#1E7A5A]"
            animate={{ scale: [1, 1.7], opacity: [0.5, 0] }}
            transition={{ duration: 1.8, repeat: Infinity, ease: "easeOut" }}
            aria-hidden
          />
        )}
        <svg viewBox="0 0 24 24" className="relative h-6 w-6 fill-white" aria-hidden>
          <path
            d="M6.6 10.8c1.4 2.8 3.8 5.1 6.6 6.6l2.2-2.2c.3-.3.7-.4 1-.2 1.1.4 2.4.6 3.6.6.6 0 1 .4 1 1V20c0 .6-.4 1-1 1-9.4 0-17-7.6-17-17 0-.6.4-1 1-1h3.5c.6 0 1 .4 1 1 0 1.3.2 2.5.6 3.6.1.4 0 .7-.2 1l-2.3 2.2z"
            transform={isAnswer ? undefined : "rotate(133 12 12)"}
          />
        </svg>
      </motion.div>
      {/* Labelled, always. Icon-only call controls are exactly the convention
          that fails older users. */}
      <span className="text-[11px] font-medium text-white/85">{label}</span>
    </div>
  );
}

// -----------------------------------------------------------------------------
// Act 2: connecting
// -----------------------------------------------------------------------------

function ConnectingScreen() {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="absolute inset-0 flex flex-col items-center justify-center"
    >
      <motion.div
        initial={{ scale: 1.25, opacity: 0.6 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
      >
        <LoopMarkSvg size={80} />
      </motion.div>
      <motion.p
        className="mt-6 text-sm text-white/50"
        animate={{ opacity: [0.35, 1, 0.35] }}
        transition={{ duration: 1.2, repeat: Infinity }}
      >
        Connecting
      </motion.p>
    </motion.div>
  );
}

// -----------------------------------------------------------------------------
// Act 3: live
// -----------------------------------------------------------------------------

function LiveScreen({
  visibleLines,
  checking,
  showInteraction,
  caraSpeaking,
  reduced,
}: {
  visibleLines: number;
  checking: boolean;
  showInteraction: boolean;
  caraSpeaking: boolean;
  reduced: boolean;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.45 }}
      className="absolute inset-0 flex flex-col px-5 pt-6 pb-6"
    >
      {/* Cara + state */}
      <div className="flex flex-col items-center">
        <div className="relative">
          <LoopMarkSvg size={64} checking={checking && !showInteraction} />
        </div>

        <p className="mt-3 font-display text-xl text-white">Cara</p>

        <motion.p
          key={checking && !showInteraction ? "checking" : caraSpeaking ? "speak" : "listen"}
          initial={{ opacity: 0, y: 4 }}
          animate={{ opacity: 1, y: 0 }}
          className={`mt-0.5 text-[11px] ${
            checking && !showInteraction ? "text-yellow" : "text-white/45"
          }`}
        >
          {checking && !showInteraction
            ? "Checking your medicines while we talk"
            : caraSpeaking
              ? "Speaking"
              : "Listening"}
        </motion.p>

        <Waveform active={caraSpeaking} reduced={reduced} />
      </div>

      {/* The interaction catch */}
      <AnimatePresence>
        {showInteraction && (
          <motion.div
            initial={{ opacity: 0, height: 0, y: 8 }}
            animate={{ opacity: 1, height: "auto", y: 0 }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ type: "spring", stiffness: 160, damping: 20 }}
            className="mt-4 overflow-hidden"
          >
            <div className="rounded-2xl border border-yellow/25 bg-yellow/10 px-4 py-3">
              <div className="flex items-center gap-2">
                <span className="h-1.5 w-1.5 rounded-full bg-yellow" aria-hidden />
                <p className="text-[11px] font-semibold text-yellow">
                  {liveInteraction.drugA} and {liveInteraction.drugB}
                </p>
              </div>
              <p className="mt-1.5 text-[10px] leading-relaxed text-white/75">
                Together these make bleeding more likely.
              </p>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Transcript */}
      <div className="mt-4 flex-1 space-y-2 overflow-hidden">
        <AnimatePresence initial={false}>
          {callScript.slice(0, visibleLines).map((line, i) => (
            <motion.div
              key={i}
              initial={{ opacity: 0, y: 10, scale: 0.97 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              transition={{ type: "spring", stiffness: 240, damping: 22 }}
              className={line.speaker === "cara" ? "text-left" : "text-right"}
            >
              <span
                className={`inline-block max-w-[86%] rounded-2xl px-3 py-2 text-[10.5px] leading-relaxed ${
                  line.speaker === "cara"
                    ? "rounded-bl-md bg-navy-soft text-white"
                    : "rounded-br-md bg-white/10 text-white/90"
                }`}
              >
                {line.text}
              </span>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>

      {/* End call */}
      <div className="mt-3 flex justify-center">
        <div className="flex h-11 w-11 items-center justify-center rounded-full bg-[#B3261E]">
          <svg viewBox="0 0 24 24" className="h-5 w-5 fill-white" aria-hidden>
            <path
              d="M6.6 10.8c1.4 2.8 3.8 5.1 6.6 6.6l2.2-2.2c.3-.3.7-.4 1-.2 1.1.4 2.4.6 3.6.6.6 0 1 .4 1 1V20c0 .6-.4 1-1 1-9.4 0-17-7.6-17-17 0-.6.4-1 1-1h3.5c.6 0 1 .4 1 1 0 1.3.2 2.5.6 3.6.1.4 0 .7-.2 1l-2.3 2.2z"
              transform="rotate(133 12 12)"
            />
          </svg>
        </div>
      </div>
    </motion.div>
  );
}

/**
 * Voice waveform.
 *
 * Heights come from a sum of two sines at different frequencies rather than from
 * randomness. Random bars read as a loading indicator; a waveform with structure
 * reads as a voice. Amplitude drops while Cara is listening, so the visual
 * genuinely tracks who is talking instead of animating regardless.
 */
function Waveform({ active, reduced }: { active: boolean; reduced: boolean }) {
  const bars = 26;
  const [tick, setTick] = useState(0);

  useEffect(() => {
    if (reduced) return;
    let frame = 0;
    let last = 0;
    function loop(now: number) {
      // ~24fps is plenty for this and costs a third of the work of 60.
      if (now - last > 42) {
        last = now;
        setTick((t) => t + 1);
      }
      frame = requestAnimationFrame(loop);
    }
    frame = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(frame);
  }, [reduced]);

  const amplitude = active ? 1 : 0.32;

  return (
    <div className="mt-4 flex h-8 items-center justify-center gap-[3px]" aria-hidden>
      {Array.from({ length: bars }).map((_, i) => {
        const phase = tick * 0.22;
        const height = reduced
          ? 8
          : 4 +
            Math.abs(
              Math.sin(i * 0.55 + phase) * 11 + Math.sin(i * 0.21 + phase * 0.6) * 7,
            ) *
              amplitude;

        return (
          <span
            key={i}
            className="w-[2.5px] rounded-full bg-gold"
            style={{
              height: Math.max(3, height),
              opacity: active ? 0.85 : 0.4,
              transition: "opacity 400ms ease",
            }}
          />
        );
      })}
    </div>
  );
}

/**
 * The Loop, as SVG.
 *
 * Identical geometry to the Android app's mark and the 3D hero, so Cara is the
 * same object everywhere. The counter-rotating inner arc appears only while a
 * lookup is running, which is the same signal the app uses.
 */
function LoopMarkSvg({
  size = 100,
  checking = false,
}: {
  size?: number;
  checking?: boolean;
}) {
  return (
    <div className="relative" style={{ width: size, height: size }}>
      <div
        className="absolute inset-0 rounded-full blur-xl"
        style={{
          background: "radial-gradient(circle, rgba(201,162,39,0.4), transparent 70%)",
        }}
        aria-hidden
      />
      <svg viewBox="0 0 100 100" className="relative h-full w-full">
        <defs>
          <linearGradient id="loopGold" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#e8c65a" />
            <stop offset="55%" stopColor="#c9a227" />
            <stop offset="100%" stopColor="#a8831a" />
          </linearGradient>
        </defs>

        <circle
          cx="50"
          cy="50"
          r="34"
          fill="none"
          stroke="url(#loopGold)"
          strokeWidth="7"
        />

        {checking && (
          <motion.circle
            cx="50"
            cy="50"
            r="21"
            fill="none"
            stroke="#ffd166"
            strokeWidth="3.5"
            strokeLinecap="round"
            strokeDasharray="24 108"
            animate={{ rotate: -360 }}
            transition={{ duration: 1.3, repeat: Infinity, ease: "linear" }}
            style={{ transformOrigin: "50px 50px" }}
          />
        )}
      </svg>
    </div>
  );
}
