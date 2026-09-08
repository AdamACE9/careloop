"use client";

import {
  Area,
  AreaChart,
  CartesianGrid,
  ReferenceArea,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { bloodSugar, bloodSugarNormalRange } from "@/lib/demo-data";

/**
 * Blood sugar over 14 days.
 *
 * ## Colour is computed, not chosen
 *
 * The brand navy (#16264D) and gold (#C9A227) both **fail** as data marks on a light
 * surface — navy sits outside the lightness band and below the chroma floor (it reads
 * grey as a mark), and gold measures 2.36:1 against the surface, under the 3:1 minimum.
 * Both were verified with a palette validator rather than judged by eye.
 *
 * `CHART_BLUE` is the brand-adjacent replacement that passes every check: lightness band,
 * chroma floor, CVD separation, and contrast. It reads as CareLoop navy at a glance while
 * actually being legible as a mark.
 *
 * ## Other deliberate choices
 *
 * - **No legend.** One series, and the heading names it. A legend box for a single line is
 *   noise.
 * - **The normal range is drawn, not implied.** A reader should not have to already know
 *   that 4.0–7.8 is normal to interpret the line.
 * - **2px line, 8px active dot, recessive grid** — marks carry the data, chrome recedes.
 * - **No dual axis, ever.** Blood pressure gets its own section rather than a second
 *   y-scale on this chart.
 */

const CHART_BLUE = "#2E56B0";

export default function BloodSugarChart() {
  return (
    <div className="h-[300px] w-full">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart
          data={bloodSugar}
          margin={{ top: 10, right: 12, bottom: 4, left: -12 }}
        >
          <defs>
            <linearGradient id="bsFill" x1="0" y1="0" x2="0" y2="1">
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

          {/* The normal range, shown so it need not be known in advance. */}
          <ReferenceArea
            y1={bloodSugarNormalRange.min}
            y2={bloodSugarNormalRange.max}
            fill="#1E7A5A"
            fillOpacity={0.07}
            stroke="none"
          />

          <XAxis
            dataKey="day"
            tick={{ fontSize: 12, fill: "#5a6173" }}
            tickLine={false}
            axisLine={{ stroke: "#1a1f2e", strokeOpacity: 0.12 }}
            interval="preserveStartEnd"
            minTickGap={24}
          />
          <YAxis
            domain={[4, 10]}
            ticks={[4, 6, 8, 10]}
            tick={{ fontSize: 12, fill: "#5a6173" }}
            tickLine={false}
            axisLine={false}
            width={44}
            label={undefined}
          />

          <Tooltip
            cursor={{ stroke: "#1a1f2e", strokeOpacity: 0.25, strokeWidth: 1 }}
            contentStyle={{
              borderRadius: 14,
              border: "1px solid rgba(26,31,46,0.12)",
              boxShadow: "0 8px 24px rgba(16,38,77,0.10)",
              fontSize: 13,
            }}
            formatter={(value: number) => [`${value} mmol/L`, "Blood sugar"]}
          />

          <Area
            type="monotone"
            dataKey="value"
            stroke={CHART_BLUE}
            strokeWidth={2}
            fill="url(#bsFill)"
            dot={{ r: 3, fill: CHART_BLUE, strokeWidth: 0 }}
            activeDot={{ r: 5, strokeWidth: 2, stroke: "#ffffff" }}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
