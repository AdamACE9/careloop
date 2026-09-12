"use client";

import { useMemo, useState } from "react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  Line,
  ReferenceArea,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { vitalRanges, type VitalReading } from "@/lib/demo-data";

/**
 * A run of readings of one type.
 *
 * This replaces BloodSugarChart, which imported the example dataset directly and
 * so could only ever draw the example household. It took no props at all, which
 * meant a real caretaker looking at their own mother's dashboard saw somebody
 * else's blood sugar drawn as though it were hers.
 *
 * ## Colour is computed, not chosen
 *
 * The brand navy (#16264D) and gold (#C9A227) both fail as data marks on a light
 * surface: navy sits outside the lightness band and below the chroma floor (it
 * reads grey as a mark), and gold measures 2.36:1 against the surface, under the
 * 3:1 minimum. Both were verified with a palette validator rather than judged by
 * eye. CHART_BLUE is the brand-adjacent replacement that passes every check.
 *
 * ## Other deliberate choices
 *
 * - The normal range is drawn, not implied. A reader should not have to already
 *   know that 4.0 to 7.8 is normal in order to read the line.
 * - Blood pressure draws both numbers. A systolic line alone is not a blood
 *   pressure, and a second y-axis for the diastolic would be worse than either.
 * - Fewer than two readings draws nothing. Two points joined by a line look like
 *   a trend and are not one.
 */

const CHART_BLUE = "#2E56B0";
const CHART_AMBER = "#B07D0C";

export interface VitalsChartProps {
  readings: VitalReading[];
  type: VitalReading["type"];
  /** How many days back to draw. */
  days?: number;
}

interface Point {
  label: string;
  value: number;
  secondary: number | null;
}

export default function VitalsChart({ readings, type, days = 14 }: VitalsChartProps) {
  const range = vitalRanges[type];

  // Read once, when the chart first mounts. Calling Date.now() inside the memo
  // makes it impure, and it also means the window silently slides under a
  // dashboard someone leaves open, so a point can vanish off the left edge
  // between one render and the next.
  const [mountedAt] = useState(() => Date.now());

  const points = useMemo<Point[]>(() => {
    const cutoff = mountedAt - days * 24 * 60 * 60 * 1000;

    return readings
      .filter((r) => r.type === type)
      .map((r) => ({ r, at: Date.parse(r.recordedAt) }))
      // A reading whose timestamp will not parse is dropped rather than drawn at
      // the epoch, which would stretch the axis across fifty-six years.
      .filter(({ at }) => Number.isFinite(at) && at >= cutoff)
      .sort((a, b) => a.at - b.at)
      .map(({ r, at }) => ({
        label: new Date(at).toLocaleDateString(undefined, {
          day: "numeric",
          month: "short",
        }),
        value: r.value,
        secondary: r.secondaryValue,
      }));
  }, [readings, type, days, mountedAt]);

  if (points.length < 2) {
    return (
      <div className="flex h-[300px] w-full flex-col items-center justify-center rounded-2xl bg-cloud px-6 text-center">
        <p className="font-display text-lg text-ink">Not enough readings yet</p>
        <p className="mt-2 max-w-sm text-sm leading-relaxed text-slate-ink">
          {points.length === 0
            ? `No ${range.label.toLowerCase()} has been recorded in the last ${days} days.`
            : `One reading so far. A line needs at least two to mean anything.`}
        </p>
      </div>
    );
  }

  // The axis fits the readings and the normal band together.
  //
  // It used to be a fixed wide range per reading type, so fourteen blood sugars
  // between 5.8 and 8.3 were drawn on a 3-to-11 axis: a nearly flat line across
  // the middle of the chart, with the whole point of the chart squeezed out.
  // Including the band means it is always visible; including the readings means
  // an alarming one is never clipped off the top.
  const values = points.flatMap((p) =>
    p.secondary === null ? [p.value] : [p.value, p.secondary],
  );
  const band = [range.min, range.max].filter((v): v is number => v !== null);
  const low = Math.min(...values, ...band);
  const high = Math.max(...values, ...band);
  // A flat series would otherwise collapse to a zero-height axis.
  const pad = Math.max((high - low) * 0.12, high * 0.02, 0.5);

  return (
    <div className="h-[300px] w-full">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={points} margin={{ top: 10, right: 12, bottom: 4, left: -12 }}>
          <defs>
            <linearGradient id={`fill-${type}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={CHART_BLUE} stopOpacity={0.22} />
              <stop offset="100%" stopColor={CHART_BLUE} stopOpacity={0.02} />
            </linearGradient>
          </defs>

          <CartesianGrid
            strokeDasharray="3 3"
            stroke="#1a1f2e"
            strokeOpacity={0.08}
            vertical={false}
          />

          {/* The normal range, shown so it need not be known in advance.
              Omitted where there is no such thing, rather than guessed. */}
          {range.min !== null && range.max !== null && (
            <ReferenceArea
              y1={range.min}
              y2={range.max}
              fill="#1E7A5A"
              fillOpacity={0.07}
              stroke="none"
            />
          )}

          <XAxis
            dataKey="label"
            tick={{ fontSize: 12, fill: "#5a6173" }}
            tickLine={false}
            axisLine={{ stroke: "#1a1f2e", strokeOpacity: 0.12 }}
            interval="preserveStartEnd"
            minTickGap={24}
          />
          <YAxis
            domain={[low - pad, high + pad]}
            tickCount={5}
            tick={{ fontSize: 12, fill: "#5a6173" }}
            tickLine={false}
            axisLine={false}
            width={44}
          />

          <Tooltip
            cursor={{ stroke: "#1a1f2e", strokeOpacity: 0.25, strokeWidth: 1 }}
            contentStyle={{
              borderRadius: 14,
              border: "1px solid rgba(26,31,46,0.12)",
              boxShadow: "0 8px 24px rgba(16,38,77,0.10)",
              fontSize: 13,
            }}
            // Recharts types the formatter value as ValueType (possibly
            // undefined), so annotating it as `number` does not typecheck.
            // Format defensively instead.
            formatter={(value, name) =>
              [
                `${value ?? "-"} ${range.unit}`,
                name === "secondary" ? "Diastolic" : range.label,
              ] as [string, string]
            }
          />

          <Area
            type="monotone"
            dataKey="value"
            stroke={CHART_BLUE}
            strokeWidth={2}
            fill={`url(#fill-${type})`}
            dot={{ r: 3, fill: CHART_BLUE, strokeWidth: 0 }}
            activeDot={{ r: 5, strokeWidth: 2, stroke: "#ffffff" }}
          />

          {/* Diastolic, drawn as a bare line so the filled area stays readable
              as the systolic. */}
          {type === "blood_pressure" && (
            <Line
              type="monotone"
              dataKey="secondary"
              stroke={CHART_AMBER}
              strokeWidth={2}
              strokeDasharray="4 3"
              dot={{ r: 3, fill: CHART_AMBER, strokeWidth: 0 }}
            />
          )}
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

/**
 * Plain language for what the readings actually did.
 *
 * This used to be a hardcoded paragraph in Cara's voice describing a mid-week
 * drift that had nothing to do with whoever was looking at the page. A sentence
 * presented as the agent's reading of a specific person has to be derived from
 * that person's numbers or it should not be on screen at all.
 *
 * Deliberately arithmetic rather than model-generated: this is a description of
 * data, and a fluent paraphrase that misstates the trend is worse than no
 * sentence. Judgement about what to do lives in the escalation engine, which
 * shows its working.
 */
export function describeTrend(
  readings: VitalReading[],
  type: VitalReading["type"],
  days = 14,
): string | null {
  const range = vitalRanges[type];
  const cutoff = Date.now() - days * 24 * 60 * 60 * 1000;

  // Nothing here is sayable without a normal range to compare against. Weight
  // has none, so weight gets no sentence rather than a made-up judgement.
  //
  // Pulled into locals because narrowing a property does not survive into the
  // closures below.
  const { min: normalMin, max: normalMax } = range;
  if (normalMin === null || normalMax === null) return null;

  const series = readings
    .filter((r) => r.type === type)
    .map((r) => ({ value: r.value, at: Date.parse(r.recordedAt) }))
    .filter(({ at }) => Number.isFinite(at) && at >= cutoff)
    .sort((a, b) => a.at - b.at);

  if (series.length < 4) return null;

  const half = Math.floor(series.length / 2);
  const mean = (xs: { value: number }[]) =>
    xs.reduce((sum, x) => sum + x.value, 0) / xs.length;

  const earlier = mean(series.slice(0, half));
  const later = mean(series.slice(-half));
  const latest = series[series.length - 1]!.value;
  const above = series.filter((x) => x.value > normalMax).length;

  const span = normalMax - normalMin;
  const shift = later - earlier;
  // A tenth of the normal band is the threshold for calling something a move
  // rather than noise.
  const moved = Math.abs(shift) > span * 0.1;

  const unit = `${latest} ${range.unit}`;
  const noun = range.label.toLowerCase();

  if (above === 0 && !moved) {
    return `Her ${noun} has stayed inside the usual range across these ${series.length} readings, most recently ${unit}.`;
  }
  if (above === 0 && moved) {
    return `Her ${noun} has ${shift > 0 ? "risen" : "fallen"} over these ${series.length} readings but stayed inside the usual range, most recently ${unit}.`;
  }
  if (moved && shift < 0) {
    return `Her ${noun} went above the usual range ${above === 1 ? "once" : `${above} times`} and has been coming back down since, most recently ${unit}.`;
  }
  if (moved && shift > 0) {
    return `Her ${noun} has been climbing and sat above the usual range ${above === 1 ? "once" : `${above} times`}, most recently ${unit}. Worth mentioning at her next appointment.`;
  }
  return `Her ${noun} has been above the usual range ${above === 1 ? "once" : `${above} times`} across these ${series.length} readings, most recently ${unit}.`;
}
