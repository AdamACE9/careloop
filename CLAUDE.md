# CareLoop — Project Guide

> **This file is a living document.** It is the handoff surface between sessions.
> If you are a future session: read this first, then check **§7 Status Board** to see
> exactly where things stand. Update it as you work — especially §8 (Decision Log)
> and §9 (Things That Bit Me). Those two sections are what actually compound.

---

## 1. What CareLoop Is

An agentic AI companion that **phones elderly users** to check on their medications.
Not a reminder app — it calls, listens to *how* they answer, checks drug interactions
live mid-call, reasons across days of check-ins, and decides autonomously when to
alert family, explaining its reasoning in plain language.

Built for the DDS Agentic AI Application Building Challenge (13th ed., JetBrains/DDS).
Judges score explicitly for **real autonomous decision-making with visible reasoning** —
so every surface that shows agent behaviour must make the reasoning legible, never a
black box. That is a product requirement, not a nice-to-have.

**The core loop:** scheduled push-call → real incoming-call screen → live voice
conversation → live interaction check → pattern reasoning across days → autonomous
escalation to family → caretaker dashboard.

---

## 2. Tonight's Goal & Scope Boundary

**Goal:** frontend-complete for both the Android app and the website/dashboard, running
on realistic mock data, looking genuinely excellent — good enough to hold up on a stage
next to funded competitors.

### In scope (frontend)
- Entire Android app UI/UX, fully navigable
- **The native incoming-call screen** (`CallStyle` notification + full-screen intent) —
  counts as frontend because it *is* a UI surface, and it is the product's signature
- Live call screen (waveform, transcript, mid-call interaction flag) on scripted data
- Entire website: landing, APK download + "unknown sources" explainer, caretaker login
  (UI only), full dashboard (history, vitals trends, escalation reasoning, manual call)
- Realistic mock data throughout: believable persona, meds, multi-day history with a
  real simulated escalation

### Explicitly NOT tonight — leave stubbed with `// TODO:` markers
- Gemini Live API wiring (use scripted conversation data)
- Real FCM delivery / Firebase backend logic
- Real openFDA/RxNorm calls (mock the *response shape*, not the network call)
- Real auth logic (a login *screen* is frontend; the auth backend is not)

### Deliberate scope trade-off (agreed with Adam)
The brief asks for three surfaces at "genuinely excellent". That is not evenly
achievable in one session. Agreed priority:
1. **Website** — highest polish. First thing judges click.
2. **Incoming-call + live-call experience** — highest polish. The signature interaction.
3. Rest of the Android app — high quality, but not equally lavish.

---

## 3. Stack (and why)

| Layer | Choice | Why |
|---|---|---|
| Android | **Kotlin + Jetpack Compose** | `CallStyle` is a first-party API. The Flutter (`flutter_callkit_incoming`) and RN (`react-native-callkeep`) wrappers both have documented Android 14+ breakage on *the answer-call transition* — the exact moment our demo lives or dies. Also cleanest path for raw PCM audio over WebSocket later. |
| Web | **Next.js 16 + React 19 + Tailwind 4**, deploy on Vercel | One repo, one deploy, push-to-ship. Marketing pages static, dashboard interactive, same codebase. Simplest story for a non-professional deployer. |
| Charts | **Recharts** | Standard, composable, restyles cleanly to our palette. |
| Web motion | **Motion** (framer-motion) + **one** lazy-loaded three.js hero | See §8-D2. |
| Android DI | Hilt | Lets `MockRepository` swap to `FirebaseRepository` with no UI changes. |
| Android state | ViewModel + `StateFlow` + `collectAsStateWithLifecycle()` | Idiomatic; survives config change. |

**Local toolchain (verified present):** JDK 17 Temurin, Android SDK platforms 34/35/36,
build-tools 36.1, `adb`, AVD `Medium_Phone` API 36.1, Node 22.15, npm 10.9, gh authed.
**Absent:** `cmdline-tools`/`sdkmanager` — cannot install new SDK components, so target
only what is already installed.

---

## 4. THE CRITICAL TECHNICAL CONSTRAINT (read before touching the call flow)

**On Android 14+ (API 34), `USE_FULL_SCREEN_INTENT` is NOT auto-granted to us.**

The auto-grant covers only apps whose *core function* is calling or alarms. A
medication/health app does not automatically qualify. Until the user grants it via
special-access settings, `NotificationManager.canUseFullScreenIntent()` returns `false`
and the incoming-call screen silently degrades to a heads-up banner.

There is a real argument that CareLoop *is* a calling app — it receives genuine
bidirectional voice calls. That is a Play-policy judgement call, unresolved. **We
engineer for the pessimistic case.**

Therefore the call flow MUST have all three of:
1. A runtime `canUseFullScreenIntent()` check before setting the full-screen intent.
2. A graceful heads-up fallback that still rings, vibrates, and turns the screen on.
3. An **onboarding step that earns the permission** in plain language ("let CareLoop
   ring like a real phone call"), deep-linking to
   `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`.

Other hard-won specifics:
- `setShowWhenLocked(true)` / `setTurnScreenOn(true)` must be called in Activity
  `onCreate()` **before** `setContent {}`. Setting them inside a `@Composable` does nothing.
- The **full-screen intent** launches the *ringing* UI. The **answer action** is a
  separate intent. Do not wire the full-screen intent to the answer action — they are
  different moments. (A research agent's sample code got this wrong.)
- Channel must be `IMPORTANCE_HIGH` or `IMPORTANCE_MAX`, else no full-screen, ever.
- `android.permission.TURN_SCREEN_ON` is signature-level. Declaring it does nothing
  for us. Use the Activity flags instead.

---

## 5. Design System

### Palette
| Token | Hex | Use |
|---|---|---|
| Navy | `#16264D` | Dominant |
| Navy Deep | `#0F1A38` | Depth, shadow |
| White | `#FFFFFF` | Surface |
| Gold | `#C9A227` | Accent |
| Yellow | `#FFD166` | Sparing highlight |

**Palette accessibility ruling (deviation — flagged):** research shows blue-yellow
discrimination is the *first* to degrade with age (not red-green), and names
yellow/white and dark-blue/black as confusing pairs. Our palette sits exactly on that
axis. We keep the brand, but on **elder-facing surfaces**:
- Never encode meaning in hue alone — always pair with icon, shape, or text label.
- Raw `#C9A227` on white is ~2.9:1 — **fails** the 4.5:1 floor. Use a darkened gold for
  text; reserve bright gold for large shapes and fills.
- Verify **luminance** contrast, never assume from hue.
The caretaker dashboard may use the palette more freely — different user, different eyes.

### Elder-facing rules (all evidence-backed — see §10)
- Body text ≥ 18sp (research floor is 16px; we exceed it deliberately)
- Touch targets ≥ 56dp (research floor is 48dp; we exceed it)
- Line spacing ≥ 1.5×
- Contrast ≥ 4.5:1, prefer 7:1 for anything under 24sp
- **Icons never appear without a text label** — semantic distance is a documented
  failure mode for this cohort
- **Tap only.** No swipe-to-act. Tremor causes failed touches that get misread as gestures
- Minimise text entry — it is a genuine onboarding blocker. Prefer selection lists
- Permissions requested **in-context**, never at launch
- Prefer one complete task per screen over scrolling

---

## 6. Cara — the agent identity

**Name: Cara.** Means "friend" (Irish) / "dear" (Italian); sits phonetically inside
*CareLoop*. Two syllables, hard opening consonant and open vowels — which matters,
because age-related hearing loss hits high frequencies first, so it stays intelligible
over a phone speaker where softer names blur.

**No face, no avatar.** Research is clear: avatars show no measured benefit for this
cohort and carry real uncanny-valley and infantilisation risk. ElliQ — the closest
shipped precedent — deliberately used abstract light and motion instead of a face.

**Visual identity: "the Loop."** A single continuous gold ring that breathes while
listening. One mark, three jobs: app icon, in-call listening indicator, call-history
avatar. It is also the website's hero motif — the motion *means* something (the loop
is the product), rather than being decoration.

**Voice/personality:** warm but professional. Never baby talk, never "sweetie."
Slow, clear, pauses between ideas. States what it is doing and why.
> ✅ "Good morning, Margaret. It's Cara. Have you taken your heart pill today?"
> ❌ "Hi sweetie! Time for meds! 💊"

---

## 7. Status Board

Legend: ✅ done · 🔨 in progress · ⬜ not started · ⚠️ blocked/flagged

### Foundations
- ✅ Repo inspected, toolchain verified, `gh` authed
- ✅ Research phase (3 Haiku agents: call UI, stack, elderly UX)
- ✅ 4 clarifying questions answered
- ✅ `CLAUDE.md` written
- ✅ Next.js scaffolded (`web/`)
- 🔨 Android project scaffold (`android/`)
- ⬜ Shared design tokens
- ⬜ Mock data layer (persona, meds, check-ins, escalation)

### Android app
- ⬜ Theme / design system (Compose)
- ⬜ Data models + `MockRepository`
- ⬜ Navigation graph
- ⬜ **Incoming-call notification + full-screen activity** ← signature, build myself
- ⬜ **Live call screen** ← signature, build myself
- ⬜ Onboarding flow *(agent)*
- ⬜ Medication list + detail *(agent)*
- ⬜ Vitals / conditions *(agent)*
- ⬜ Call history + "What I shared" *(agent)*
- ⬜ Settings + permission education *(agent)*
- ⬜ Emulator verification + screenshots

### Website
- ⬜ Design tokens / Tailwind theme
- ⬜ Landing page + WebGL hero
- ⬜ APK download + unknown-sources explainer
- ⬜ Caretaker login (UI only)
- ⬜ Dashboard: today's status
- ⬜ Dashboard: vitals trends (Recharts)
- ⬜ Dashboard: escalation reasoning view
- ⬜ Dashboard: manual "check on them now"

---

## 8. Decision Log

Decisions already made. **Do not relitigate these** without a real reason.

- **D1 — Native Kotlin over Flutter/RN.** The cross-platform call wrappers break on
  precisely the answer-call transition. Also: SDK + emulator already on this machine,
  so the signature feature can be verified, not just asserted.
- **D2 — One restrained WebGL hero, not 3D throughout.** *Adam's call.* Every AI
  startup site in 2026 has the same WebGL blob; heavy 3D can read as *more* templated.
  One lazy-loaded volumetric ring, static fallback for mobile and `prefers-reduced-motion`.
- **D3 — Cara, no avatar.** See §6.
- **D4 — Elder-control surface is first-class.** *Adam's call.* Research: 49% of older
  adults are uncomfortable with passive monitoring *even when the safety benefit is
  clear*, and that discomfort is resolved by **perceived control**, not better privacy
  terms. Symmetric transparency — the elder sees what the family sees, and can dispute
  the agent's read. Research found **no shipped product** solves this; it is a genuine
  differentiator for the ethics score.
- **D5 — Build for live-on-device.** Demo is probably emulator, possibly a phone.
  Device-robustness is a superset of both, so no wasted work.
- **D6 — Palette kept, contrast rules added.** See §5.
- **D7 — Single Next.js app, not Astro+Next hybrid.** Two pipelines is worse for a
  non-professional deployer than a few KB of JS on the marketing page.

---

## 9. Things That Bit Me

Environment quirks and gotchas. **Append here whenever something wastes your time.**

### ⚠️ BLOCKER: Gradle cannot run builds on this machine

**Symptom:** every Gradle build fails with `java.io.IOException: Unable to establish
loopback connection`.

**Root cause (diagnosed, not guessed):** Gradle's build execution opens an NIO
`Selector`, which on Windows is `WEPollSelectorImpl`, which internally calls
`Pipe.open()`. That call fails here with `SocketException: Invalid argument: connect`
inside `sun.nio.ch.UnixDomainSockets.connect`. Almost certainly endpoint-security
software blocking AF_UNIX/loopback socket creation for background JVM processes.

**Evidence trail:**
- Node binds loopback fine; `ping 127.0.0.1` fine → not the network.
- A standalone Java TCP `ServerSocket` test passes → not the JVM's networking generally.
- A standalone `Pipe.open()` test **fails on JDK 17** but **passes on Android Studio's
  JBR 21** → JDK-dependent.
- `gradlew --version` succeeds (never opens a Selector); `gradlew help` fails
  (does) → the boundary is precisely `Selector.open()`.
- Fails identically sandboxed and unsandboxed, in Bash and PowerShell, with and
  without `--no-daemon`, on JDK 17 and JBR 21, and with daemon JVM args matched
  exactly to what the daemon log requested. **Seven approaches; all fail.**

**Consequence:** the Android app could not be compiled, run, or screenshotted in the
session that wrote it. **The Kotlin source is unverified.** Treat first compile as a
debugging session, not a formality.

**What to try next (in order):**
1. Open `android/` in **Android Studio** and build there — the IDE launches Gradle
   differently and may not hit this.
2. `JAVA_HOME` must be a **JDK 21** (Android Studio ships one at `<studio>\jbr`);
   JDK 17 on this machine has a broken `Pipe.open()` independently of the above.
3. If it still fails, temporarily disable/exempt endpoint-security for the Gradle
   daemon process, or build on another machine / CI.

The Gradle wrapper **is** valid and committed — `gradle-wrapper.jar` was extracted
from `gradle-wrapper-main-8.14.3.jar` inside the cached distribution, and
`gradlew --version` was verified working. `gradlew`/`gradlew.bat` are compact
hand-written scripts (see comments in them) rather than Gradle's stock 250-line
versions, because those could not be generated or verified here.

### Other environment notes

- Android SDK is installed but **not on `PATH`** and `ANDROID_HOME` is unset. Point at
  it via `android/local.properties` (`sdk.dir=...`), which is gitignored.
- No `cmdline-tools`/`sdkmanager` → cannot install SDK components. Target only
  platforms 34/35/36 and build-tools ≤ 36.1.
- Gradle distributions already cached in `~/.gradle/wrapper/dists`: 8.10.2, **8.13**,
  **8.14.3**, 9.1.0. Pick a cached one to avoid a slow first-run download.
- `create-next-app` emits its own `CLAUDE.md` and `AGENTS.md` — deleted, they conflict
  with this file.
- MCP servers `chrome-devtools`, `firebase`, and `github` all failed to connect this
  session (timeouts; github had a malformed auth header). Not blocking — use `git`/`gh`
  CLI, which are authenticated and work.
- **Research agents are not oracles.** In this session one agent invented a
  non-existent `PERMISSION_INLINE` manifest flag and claimed we auto-qualify for
  full-screen intent — the opposite of the truth (§4). Another gave bundle sizes off by
  ~30×. Cross-check anything load-bearing against primary sources.

---

## 10. Research Provenance

Key evidence behind the design rules, so future sessions know what is grounded and
what is opinion.

**Well-supported:** 16px font floor and 48dp touch-target floor (NN/g, WCAG 2.1);
blue-yellow discrimination degrading first with age; icons needing text labels;
taps outperforming gestures (tremor → failed touches misread as gestures); in-context
permission requests outperforming upfront; assisted setup by an adult child as a
recognised pattern; 3–5 screen onboarding ceiling; explainability for lay users =
top-3 reasons in natural language, confidence as words not percentages.

**Explicitly flagged as folklore / unsupported:** "older adults resist technology"
(contradicted — resistance is to *bad* design); "gamification helps elderly users"
(minimal evidence); "a mascot/avatar is essential" (unsupported — this is why D3 went
the way it did).

**Open risk for the backend phase:** one forum report claims Gemini Live had high
mid-turn WebSocket disconnect rates on a preview model. Single-source and months old —
**verify before building on it**, do not treat as fact. Plan reconnection/backoff regardless.

---

## 11. Working Rules for This Project

- **Commit and push frequently**, with meaningful messages. If something breaks later
  there must be a known-good state to return to.
- **Stay inside the project folder.** Never modify anything elsewhere on the system.
- Every backend seam gets an explicit `// TODO:` naming what wires in there.
- Sub-agent budget: max 5 Sonnet agents (Android screens only) + max 5 high-code
  agents, separate pools from the research agents already spent.
- **The website is never delegated.** Build it directly.
- Foundations (design system, data models, call flow) are built directly, not delegated.
- If stuck: try two approaches, then flag it in §9 and move on. Do not burn the session
  on one blocker.
