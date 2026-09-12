'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useState, type ReactNode } from 'react';
import { useAuth } from '@/lib/auth-context';
import { useLinkedPatients } from '@/lib/careloop-service';
import { LoopGlyph } from '@/components/SiteHeader';
import CheckNowButton from './CheckNowButton';

/**
 * The dashboard shell: navigation, patient context, and the one action that
 * matters most.
 *
 * ## Layout reasoning
 *
 * The person using this is an adult child between other things, checking whether
 * their parent is alright. Two consequences drove the structure:
 *
 *   1. **The answer comes before the navigation.** The patient's name and status
 *      sit at the top, above the tabs, so the primary question is answered
 *      without a click. Tabs are for going deeper, not for finding the point.
 *
 *   2. **"Check on her now" is always reachable.** It lives in the header on
 *      every tab rather than only on the overview, because the moment someone
 *      wants it is the moment they have just read something worrying, whichever
 *      page that happened on.
 *
 * Navigation is real routes rather than in-page tab state, so each view is
 * linkable, back works, and a worried person can bookmark the reasoning page.
 */

const TABS = [
  { href: '/dashboard', label: 'Overview', exact: true },
  { href: '/dashboard/reasoning', label: 'Why Cara called' },
  { href: '/dashboard/health', label: 'Health' },
  { href: '/dashboard/calls', label: 'Check-ins' },
  { href: '/dashboard/settings', label: 'Settings' },
];

export default function DashboardShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { displayName, isDemo, signOut } = useAuth();
  // Renamed: useAuth already exports an isDemo meaning demo AUTH, which is a
  // different question from whether the DATA on screen is the example household.
  const { patients, loading, isDemo: showingExampleData } = useLinkedPatients();
  const [menuOpen, setMenuOpen] = useState(false);

  const patient = patients[0];

  return (
    <div className="min-h-screen bg-cream">
      {/* ---------------------------------------------------------- Top bar */}
      <header className="sticky top-0 z-40 border-b border-white/10 bg-brown/95 backdrop-blur-md">
        <div className="mx-auto max-w-7xl px-6 md:px-10">
          <div className="flex h-16 items-center justify-between gap-6">
            <Link href="/" className="flex items-center gap-3 text-white">
              <span className="text-gold">
                <LoopGlyph className="h-6 w-6" />
              </span>
              <span className="font-display text-lg">CareLoop</span>
            </Link>

            <div className="flex items-center gap-3">
              {isDemo && (
                <span className="hidden rounded-full border border-gold/40 px-3 py-1 text-xs font-medium text-gold sm:inline">
                  Demo data
                </span>
              )}

              <div className="relative">
                <button
                  onClick={() => setMenuOpen((v) => !v)}
                  className="flex items-center gap-2 rounded-xl px-3 py-2 text-sm text-white/80 transition hover:bg-white/10"
                  aria-expanded={menuOpen}
                  aria-haspopup="menu"
                >
                  <span
                    className="flex h-7 w-7 items-center justify-center rounded-full bg-gold text-xs font-semibold text-navy-deep"
                    aria-hidden
                  >
                    {initials(displayName)}
                  </span>
                  <span className="hidden sm:inline">{displayName}</span>
                </button>

                {menuOpen && (
                  <div
                    role="menu"
                    className="absolute right-0 mt-2 w-52 overflow-hidden rounded-2xl border border-ink/10 bg-white py-1 shadow-xl"
                  >
                    <Link
                      href="/dashboard/settings"
                      className="block px-4 py-3 text-sm text-ink transition hover:bg-cloud"
                      onClick={() => setMenuOpen(false)}
                    >
                      Settings
                    </Link>
                    <button
                      onClick={() => { setMenuOpen(false); void signOut(); }}
                      className="block w-full px-4 py-3 text-left text-sm text-ink transition hover:bg-cloud"
                    >
                      Sign out
                    </button>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* ------------------------------------------------- Patient + action */}
      <div className="border-b border-ink/10 bg-white">
        <div className="mx-auto max-w-7xl px-6 md:px-10">
          <div className="flex flex-col gap-5 py-7 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-4">
              <span
                className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-navy font-display text-xl text-gold"
                aria-hidden
              >
                {initials(`${patient?.firstName ?? ''} ${patient?.lastName ?? ''}`)}
              </span>
              <div>
                <h1 className="font-display text-2xl text-ink md:text-3xl">
                  {patient?.firstName} {patient?.lastName}
                </h1>
                <p className="mt-0.5 text-sm text-slate-ink">
                  {patient?.age} years old · Cara calls at {patient?.checkInTime}
                </p>
              </div>
            </div>

            {patient && <CheckNowButton patientId={patient.id} patientName={patient.firstName} />}
          </div>

          {/* ------------------------------------------------------- Tabs */}
          <nav className="-mb-px flex gap-1 overflow-x-auto" aria-label="Dashboard sections">
            {TABS.map((tab) => {
              const active = tab.exact
                ? pathname === tab.href
                : pathname.startsWith(tab.href);
              return (
                <Link
                  key={tab.href}
                  href={tab.href}
                  aria-current={active ? 'page' : undefined}
                  className={`relative whitespace-nowrap px-4 py-3 text-sm font-medium transition ${
                    active
                      ? 'text-navy'
                      : 'text-slate-ink hover:text-ink'
                  }`}
                >
                  {tab.label}
                  <span
                    className={`absolute inset-x-2 bottom-0 h-0.5 rounded-full transition ${
                      active ? 'bg-gold' : 'bg-transparent'
                    }`}
                    aria-hidden
                  />
                </Link>
              );
            })}
          </nav>
        </div>
      </div>

      <main className="mx-auto max-w-7xl px-6 py-10 md:px-10">
        {!loading && !showingExampleData && patients.length === 0 ? (
          <NotLinkedYet />
        ) : (
          children
        )}
      </main>
    </div>
  );
}

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');
}

/**
 * Shown to a caretaker who has an account but is not linked to anyone.
 *
 * This used to be the example household, which meant somebody who had just
 * signed up was shown a stranger's medication list and blood sugar as though it
 * were their mother's. It looked like the product working and was the exact
 * opposite, so this says plainly that there is nobody connected yet and gives
 * the one instruction that fixes it.
 *
 * The direction of the code matters and is stated here because it is the thing
 * people get wrong: it is generated on the ELDER's phone and read out to you.
 * Only their own device can mint one.
 */
function NotLinkedYet() {
  return (
    <div className="mx-auto max-w-2xl rounded-3xl border border-ink/10 bg-white p-10 text-center">
      <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-gold/15 text-gold-ink">
        <svg
          viewBox="0 0 24 24"
          className="h-8 w-8"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden
        >
          <rect x="6" y="2" width="12" height="20" rx="2.5" />
          <path d="M11 18h2" />
        </svg>
      </div>

      <h2 className="mt-6 font-display text-3xl text-ink">
        You are not connected to anyone yet
      </h2>

      <p className="mx-auto mt-4 max-w-lg leading-relaxed text-slate-ink">
        Ask them to open CareLoop on their phone and read you the code it shows.
        Type it in under Settings and their check-ins will appear here. The code
        comes from their phone, not from this page, because it is theirs to give.
      </p>

      <Link
        href="/dashboard/settings"
        className="mt-8 inline-flex items-center justify-center rounded-xl bg-navy px-7 py-4 font-semibold text-white transition hover:bg-navy-soft"
      >
        Enter their code
      </Link>
    </div>
  );
}
