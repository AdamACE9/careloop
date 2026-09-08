import type { Metadata, Viewport } from "next";
import { Inter, Instrument_Serif } from "next/font/google";
import "./globals.css";

const inter = Inter({
  variable: "--font-inter",
  subsets: ["latin"],
  display: "swap",
});

/*
 * Editorial serif for display type.
 *
 * A deliberate departure from the sans-everywhere convention: a warm serif reads as
 * human and considered, which is the register a care product needs. It also makes the
 * site look hand-built rather than generated from a SaaS template — the specific failure
 * mode the brief calls out.
 */
const instrumentSerif = Instrument_Serif({
  variable: "--font-instrument-serif",
  subsets: ["latin"],
  weight: "400",
  display: "swap",
});

export const metadata: Metadata = {
  title: "CareLoop — the companion that actually checks in",
  description:
    "CareLoop calls your parent, listens to how they answer, checks their medications for dangerous interactions, and tells you when something is genuinely wrong.",
  openGraph: {
    title: "CareLoop — the companion that actually checks in",
    description:
      "An AI companion that phones your parent, listens, and reasons across days to decide when family needs to know.",
    type: "website",
  },
};

export const viewport: Viewport = {
  themeColor: "#16264D",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body className={`${inter.variable} ${instrumentSerif.variable} antialiased`}>
        {children}
      </body>
    </html>
  );
}
