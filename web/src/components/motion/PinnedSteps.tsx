"use client";

/**
 * The landing page's one pinned scene.
 *
 * The whole two-column block (four steps on the left, the phone mock on the
 * right) pins in place for a fixed scroll distance, and the active step
 * highlights in sequence as the visitor scrolls through that distance. This is
 * the single pinned moment the brief allows, used here because it is the
 * section where sequence matters more than anywhere else on the page: the four
 * things Cara does on a call happen in a fixed order, and pinning is what lets
 * a reader track which step the phone mock is currently illustrating without
 * it just being four cards in a row.
 *
 * Earlier draft pinned the phone mock alone while the step list scrolled past
 * it (a different trigger element than the pinned target, `pinSpacing: false`).
 * That is a real GSAP recipe, but it is also the one most likely to drift: the
 * moment another ScrollTrigger on the page recalculates page height after this
 * one, the un-reserved space it left behind stops lining up with where the
 * content actually paints. Pinning the single element that is also the trigger,
 * with `pinSpacing: true`, is the boring version of this effect, and boring is
 * right for the one pin this page gets.
 *
 * Reduced motion, or a narrow viewport, gets no pin at all: the plain, static
 * two-column grid everyone would see with JavaScript disabled.
 */

import { useRef, type ReactNode } from "react";
import { useGSAP } from "@gsap/react";
import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

gsap.registerPlugin(ScrollTrigger, useGSAP);

export interface Step {
  n: string;
  title: string;
  body: string;
}

export function PinnedSteps({
  steps,
  visual,
}: {
  steps: Step[];
  visual: ReactNode;
}) {
  const root = useRef<HTMLDivElement>(null);

  useGSAP(
    () => {
      const rootEl = root.current;
      if (!rootEl) return;

      const mm = gsap.matchMedia();

      mm.add("(prefers-reduced-motion: no-preference) and (min-width: 1024px)", () => {
        const stepEls = Array.from(rootEl.querySelectorAll<HTMLElement>("[data-step]"));
        if (!stepEls.length) return;

        const trigger = ScrollTrigger.create({
          trigger: rootEl,
          start: "top top+=96",
          end: `+=${stepEls.length * 480}`,
          pin: true,
          pinSpacing: true,
          scrub: 0.4,
          onUpdate: (self) => {
            const active = Math.min(
              stepEls.length - 1,
              Math.floor(self.progress * stepEls.length),
            );
            stepEls.forEach((el, i) => el.classList.toggle("step-active", i === active));
          },
          onLeave: () => {
            stepEls.forEach((el, i) =>
              el.classList.toggle("step-active", i === stepEls.length - 1),
            );
          },
          onEnterBack: () => {
            stepEls[0]?.classList.add("step-active");
          },
        });

        // The first step reads as active from the moment the scene pins,
        // rather than waiting for the first scroll tick inside it.
        stepEls[0]?.classList.add("step-active");

        return () => trigger.kill();
      });

      return () => mm.revert();
    },
    { scope: root, dependencies: [steps.length] },
  );

  return (
    <div ref={root} className="grid gap-12 lg:grid-cols-[1fr_auto] lg:gap-16">
      <div className="space-y-5">
        {steps.map((step) => (
          <div
            key={step.n}
            data-step
            className="step-block flex gap-6 rounded-2xl border border-white/10 bg-white/[0.03] p-6"
          >
            <span className="step-number font-display text-2xl text-gold">{step.n}</span>
            <div>
              <h3 className="text-lg font-semibold text-white">{step.title}</h3>
              <p className="mt-2 leading-relaxed text-white/60">{step.body}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="flex justify-center lg:justify-end">{visual}</div>
    </div>
  );
}
