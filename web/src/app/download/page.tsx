import type { Metadata } from "next";
import SiteHeader from "@/components/SiteHeader";
import SiteFooter from "@/components/SiteFooter";
import DownloadFlow from "@/components/DownloadFlow";

export const metadata: Metadata = {
  title: "Download CareLoop",
  description:
    "Install CareLoop on an Android phone. Takes about four minutes, and we explain every screen before it appears.",
};

/**
 * The install flow.
 *
 * The explainer *precedes* the download deliberately. Android will show a genuine
 * "unknown sources" security warning that we cannot skin. For a non-technical user, or
 * an anxious adult child installing this on their parent's phone, that warning is the
 * single most likely point of abandonment in the entire product.
 *
 * Priming it first turns a scary interruption into an expected step. The warning still
 * appears; it just no longer reads as evidence that something is wrong.
 *
 * We are careful not to tell anyone that Android's warning is meaningless. It exists for
 * good reason. What we say is narrower and true: it means we are not on the Play Store
 * yet, not that the app is unsafe.
 */
export default function DownloadPage() {
  return (
    <main className="min-h-screen bg-bone">
      <SiteHeader />
      <DownloadFlow />
      <SiteFooter />
    </main>
  );
}
