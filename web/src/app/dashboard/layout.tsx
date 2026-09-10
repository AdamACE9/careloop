import type { Metadata } from 'next';
import DashboardShell from '@/components/dashboard/DashboardShell';

export const metadata: Metadata = {
  title: 'CareLoop dashboard',
  description: "How your mother is doing, and why Cara got in touch.",
};

/**
 * Dashboard layout.
 *
 * The shell (navigation, patient switcher, auth guard) is a client component so
 * it can hold live Firestore subscriptions and auth state. This server layout
 * exists only to own the metadata, which keeps the client bundle a little smaller
 * and the page title correct on first paint.
 */
export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  return <DashboardShell>{children}</DashboardShell>;
}
