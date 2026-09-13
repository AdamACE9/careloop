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

## 1b. Session log

**Day 4 (frontend):** both surfaces built on mock data, integration points stubbed.

**Day 7 (wiring, verification):** the app was compiled for the first time, on
CI, because Gradle genuinely cannot run on this machine (see §9, the earlier
diagnosis was wrong). Three compile errors, all fixed. Found and fixed the worst
bug in the project: `cara.ts` was dead code, so Cara had no persona and no tools.
Wired the live call screen to a real Gemini session. Added agent memory, an
evaluation harness which immediately found two more real bugs, a splash screen,
and removed em dashes from all copy.

**Day 6 (backend):** the full backend, wired into that frontend. Firestore schema
and security rules, Cloud Functions v2, real Gemini Live integration, real FCM
push-calls, real interaction checking, the reasoning and escalation engine, refill
planning. Web dashboard rebuilt with real auth and tabs. Website motion upgraded.
See §12 for what changed and §13 for what is still open.

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

### Android app — **COMPILES** on CI as of Day 7
- ✅ Theme / design system (Compose)
- ✅ Domain model + `MockCareLoopRepository` + `AppContainer`
- ✅ Navigation graph (flat, 5 tabs, always-visible labels)
- ✅ **Incoming-call notification + full-screen activity**
- ✅ **Live call screen** (scripted call, live interaction catch)
- ✅ Home screen + demo call trigger
- ✅ Onboarding flow *(agent)*
- ✅ Medication list + refill planning *(agent)*
- ✅ Vitals + hand-drawn Canvas chart *(agent)*
- ✅ Call history + "What I shared" *(agent)*
- ✅ Settings + permission education *(agent)*
- ✅ **Compiles, and produces a debug APK** (`.github/workflows/android.yml`)
- ✅ **Live call screen wired to a real Gemini Live session** (`LiveCallViewModel`)
- ✅ Splash screen (the Loop on navy, via `core-splashscreen`)
- ⚠️ Emulator verification + screenshots — **still not done.** It compiles; it has
  never been run. Nothing below the compiler has been exercised.

### Website
- ✅ Design tokens / Tailwind 4 theme + editorial serif display type
- ✅ Landing page + WebGL hero (lazy, with designed static fallback)
- ✅ APK download + unknown-sources explainer
- ✅ Caretaker login (UI only, honestly labelled as a demo)
- ✅ Dashboard: today's status + stat tiles
- ✅ Dashboard: vitals trend (Recharts, validated palette)
- ✅ Dashboard: escalation reasoning view
- ✅ Dashboard: manual "check on them now"
- ✅ **Verified**: `next build` compiles, `tsc --noEmit` clean (0 errors), all four
  routes return HTTP 200 with correct content server-rendered.

**Visual verification was partial.** The in-app browser pane ran with
`document.visibilityState === "hidden"`, which suspends painting, so only the hero could
be screenshotted. Everything else was verified through the DOM (computed styles, rendered
HTML) rather than pixels. **Open the site in a real browser and look at it** before
relying on it on stage — particularly the 3D hero's inner ring, which I could only see
once.

### Known gaps / next session
- The download page has **no APK to link** — blocked on the Gradle issue. It says so
  plainly rather than faking a download.
- Onboarding completion is not persisted (deliberate — re-runnable on stage).
- No medication *detail* screen; `onMedicationClick` is wired but lands nowhere.
- Android code has never been compiled. Expect a real debugging pass on first build.

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

**Root cause (diagnosed precisely on Day 7 — the earlier note was WRONG):**

`Selector.open()` fails. `Pipe.open()` does **not** — that was the mistake in the
first diagnosis, and it sent a whole session down the wrong path. On JBR 21,
`Pipe.open()` succeeds 10/10 while `Selector.open()` fails 10/10, deterministically.

The real chain:

```
Selector.open()
  -> WEPollSelectorProvider.openSelector()
  -> WEPollSelectorImpl.<init>
  -> PipeImpl.<init>            (the selector's internal wakeup pipe)
  -> UnixDomainSockets.connect0 -> SocketException: Invalid argument: connect
```

So it is **AF_UNIX specifically**, not loopback generally and not pipes generally.
Plain TCP loopback works fine in *both* address families — a `ServerSocket` on
127.0.0.1 accepts a connection, and so does one on `::1`.

**Ruled out by direct experiment (do not retry these):**

| Hypothesis | Result |
|---|---|
| Bash tool sandbox blocking sockets | Ruled out — fails identically with the sandbox disabled |
| `java.io.tmpdir` being unwritable/odd | Ruled out — overriding it changes nothing |
| `jdk.nio.channels.unixdomain.tmpdir` | Ruled out — overriding it changes nothing |
| IPv6 loopback unavailable | Ruled out — `::1` connects fine; `preferIPv4Stack` changes nothing |
| Alternate `SelectorProvider` | Ruled out — `WindowsSelectorProvider` behaves identically, `PollSelectorProvider` is worse (breaks `Pipe` too) |
| JDK version | Partially — JDK 17 also fails `Pipe.open()`; JBR 21 fixes the pipe but not the selector |
| Daemon temp directory | Ruled out on Day 6 |

Almost certainly endpoint-security software hooking AF_UNIX socket connects. It is
a machine policy, not a project problem, and **nothing in this repo can fix it.**

**The answer: build on CI.** `.github/workflows/android.yml` compiles the app on
an Ubuntu runner and uploads the debug APK as an artifact. That is where the
Kotlin is actually verified, and where the download page's APK comes from. Trigger
it with `gh workflow run android.yml --ref main`.

Also tried and unavailable: WSL (no distro installed), and driving Android Studio
via computer-use (IDEs are restricted to click-only and the grant would not open).

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

### The AF_UNIX fault is machine-wide, not a Gradle problem

The Firestore emulator fails the **same way** Gradle does, with the same stack:
`PipeImpl$Initializer$LoopbackConnector` -> `SocketChannel.open` ->
`UnixDomainSockets.connect0`. So `firebase emulators:start` cannot run here
either, on JDK 21 (Adoptium 21.0.12 at
`/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot`).

**Do not spend time on this again.** Anything that needs a JVM to open a socket
pair runs on CI. The Firestore rules tests are wired into
`.github/workflows/backend.yml` for exactly this reason and pass there (31 cases).

### Two things this file used to say that were WRONG

- **"The transcript never appears; the model emits no transcription frames."**
  False. Logging frame shapes on a real call shows one
  `serverContent=[outputTranscription]` frame per turn, carrying the whole
  utterance, and it renders correctly on the call screen. The setup fields are
  in the right place and are honoured.
- **"Rules are fine because a linked caretaker can read the patient."** `allow
  get` passing says nothing about `allow list`. A list rule cannot call `get()`
  on each candidate document, so the dashboard's `array-contains` query was
  refused for every correctly linked caretaker. Test the query the app runs,
  not a document read that stands in for it.

### The bug that made the whole thing feel like a prototype

The only way to start a call posted the incoming-call notification **locally**
and never told the server. Cloud Functions logs for a real answered call showed
exactly one invocation: `mintLiveSessionToken`. No `triggerCall`, no
`submitCheckIn`.

No server call attempt means no attempt id, so `endCall` took a branch that
returned **without logging anything**, and the conversation was discarded. Cara
rang, spoke, the transcript appeared, the call ended, and nothing was written.
The app's own Calls tab said "No check-ins yet" straight after a call.

Three lessons worth keeping:

1. **A ring must come from the server.** `requestManualCheckIn()` ->
   `triggerCall` is the only correct way to make the phone ring.
   `startIncomingCallDemo()` is deleted; see the note where it used to live.
2. **Never return silently on a path that discards user data.** That single
   missing log line is why this survived several "the call works" sessions.
3. **A write at the end of a call cannot live on `viewModelScope`.** The call
   Activity finishes 0.45s after the socket closes; two callables to
   europe-west1 do not finish in that window. Use `AppContainer.applicationScope`.

### Verifying the loop: check the server, not the screen

The screen lies by omission. The authoritative check is:

```bash
npx firebase-tools@latest functions:log -n 120 --project careloop-adam
```

A healthy answered call logs, in order: `call.deliver.sent` ->
`gemini.token.minted` -> `checkin.submitted` -> `call.outcome.reported`.
Anything missing from that chain is a broken loop no matter what the app shows.

### Field names: one shape, derived at the seam

The web dashboard read `label`, `time`, `confirmed`, `missed` on a check-in.
Firestore stores `startedAt`, `medicationsConfirmed`, `medicationsMissed`. The
example dataset had the first set, so everything looked right and real data
rendered blank dates and a permanently-zero missed count, meaning the overview
told real caretakers their parent was fine on a day a dose was missed.

Example data must be stored in **exactly** the shape Firestore uses, with
display strings derived once in `careloop-service.ts`. The Android repository
was already correct; only the web diverged.

### Timestamps are instants, not wall clocks

`parseDateTime` stripped the trailing `Z` and parsed a `LocalDateTime`, which
discards the offset. Everything the backend writes is UTC, so every timestamp
displayed four hours early on a UTC+4 phone, under a heading saying "Today".
Parse as `OffsetDateTime` and convert to `ZoneId.systemDefault()`.

### The mistake this codebase keeps making: counting calls where it means days

Three separate instances of one bug, found in a single night, all invisible until
real data went through:

1. **The reasoning engine** counted missed doses per check-in. Cara told a family
   "missed on 2 of the last 7 days" about a single morning, and wrote the
   sentence "Eleanor missed her warfarin today and today".
2. **The adherence strip** on the elder's health screen took the last seven
   check-ins. It drew two dots both labelled "Sat" with a gap where the rest of
   the week should have been.
3. **The web dashboard** counted "days with a missed dose" across every loaded
   check-in, up to sixty of them, under a label saying "this week".

They were all correct while there was one call a day. A retry after no answer, or
a tap on "have Cara call me now", breaks that assumption everywhere at once.

**Before writing anything that counts check-ins, ask whether the product means
days.** It almost always does, because the reasoning engine's entire claim is
that it weighs a pattern across days rather than reacting to a moment.

### A threshold that was decided by a millisecond

`recencyFactor` decayed off a continuous age in days. A single missed
anticoagulant scores exactly 1.0 against a threshold of exactly 1.0, so it
escalated only when the check-in's timestamp and the moment it was reasoned about
landed in the same millisecond.

Running the evaluation harness five times on unchanged code gave **two failures
and three passes.** In production that means whether a family is told about a
missed blood thinner could turn on how long the callable took to reach that line.

Recency now decays by whole days. Evidence from today is today's evidence.
Invariant R11 asserts the same evidence at different moments of the same day
produces the same decision and the same score.

The general lesson: **when a score is compared against a threshold, check what
happens when they are equal**, and whether anything in the inputs is continuous
and unstable.

### Composition-scoped coroutines lose writes, and this has now bitten three times

Disconnecting a caretaker failed on its first real run with
`ForgottenCoroutineScopeException`. The server log showed the unlink had actually
**succeeded**: the request landed, then the composable left composition and the
result had nowhere to return to, so the UI reported failure while the access was
already gone.

Previous instances: `LaunchedEffect(step)` cancelling account creation, and
`rememberCoroutineScope` cancelling it again.

**A network write that must not be lost cannot live on a scope owned by the thing
on screen.** Use `AppContainer.applicationScope` and hop back to
`Dispatchers.Main` for the state update. This applies to the end of a call, to
unlinking, and to minting a linking code.

### Tailwind display utilities do not resolve by class order

Adding `inline-flex` to a base class silently beat the `hidden` in a conditional,
because both are display utilities and which one wins is decided by their order
in the generated stylesheet, not their order in the `className` string. Three
items then fought over a 375px header and pushed the whole page sideways.

**Put display utilities in the conditional, never in the base**, when anything
about the element is responsive.

### Measuring contrast: the audit lied twice, in both directions

This cost two rounds and is the most transferable lesson of the night.

**Round one, false positives.** A naive script reported 47 failures. It took the
first non-transparent ancestor background, which for a semi-transparent overlay
is not what the eye sees, and it counted wrapper elements whose text lives in a
child. A corrected version, leaf text nodes only with `rgba` composited down the
chain, reported zero on the same page.

**Round two, false negatives, which was worse.** That "zero" was also wrong.
**Tailwind 4 emits `oklab()` for any colour with an opacity modifier**, which is
most of the semi-transparent surfaces here. The parser returned null for those
and fell back to assuming white, so it invented a failure on a dark header and
hid every real failure on a dark background.

With an oklab-aware parser the live landing page had **18 real failures**,
including the medical disclaimer in the footer at 3.76:1.

Three rules that follow:

1. An audit that cannot parse the colour space its own framework emits will
   report whatever you hoped for. Check `unparsedColors` is empty.
2. Composite alpha down the full ancestor chain; never take the first opaque one.
3. Measure leaf text nodes, not wrappers.

The working script converts oklab to linear sRGB via the standard matrices and is
worth keeping; an earlier version of it is preserved in this session's scratchpad.
`text-white/40` on navy is about 3.5:1, not the 4.5:1 it looks like by eye.

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
- **Compose APIs that bite when you cannot compile.** Fixed already, but they recur:
  - `LinearProgressIndicator(progress = someFloat)` — that overload is deprecated since
    Material3 1.2 and error-level now. Use `progress = { someFloat }`.
  - `Icons.Rounded.ArrowForward` / `ArrowBack` — use
    `Icons.AutoMirrored.Rounded.*`. Not only a deprecation: the manifest sets
    `supportsRtl="true"`, and only the AutoMirrored variants flip in RTL.
  - `Divider` was renamed `HorizontalDivider`. Avoided entirely rather than guessed.
- **`npm run build` is very slow here (10 min+).** The project lives in a
  OneDrive-synced folder, so every file write is intercepted. Not a code problem. If it
  matters, move the repo outside OneDrive.
- **Brand colours are not chart colours.** Validated, not eyeballed: navy `#16264D`
  fails the lightness band *and* chroma floor as a data mark (it reads grey), and gold
  `#C9A227` is 2.36:1 against a light surface, under the 3:1 mark floor. Charts use
  brand-adjacent `#2E56B0` / `#B07D0C`, which pass every check. Re-run the validator if
  you add a series; do not pick by eye.

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

---

## 12. Backend (Day 6)

The backend is built and wired. `docs/API_CONTRACT.md` is the full surface;
`docs/MORNING_CHECKLIST.md` is what Adam has to do personally.

### Shape

```
functions/src/
  types.ts              contract, mirrors Android Models.kt field for field
  lib/config.ts         secrets (defineSecret) + tunable constants
  lib/logging.ts        redacting logger, see below
  lib/validate.ts       input guards, auth guards, rate limiting
  calls/deliver.ts      FCM data-only high-priority push
  gemini/cara.ts        the persona and tool declarations
  gemini/liveToken.ts   ephemeral token minting
  interactions/         curated food ruleset + layered drug checking
  reasoning/engine.ts   pattern detection, weighting, escalation text
  index.ts              callables + schedulers
firestore.rules         two-sided access model
```

### Decisions worth not relitigating

- **D8 — Ephemeral tokens, not a relay and not a shipped key.** The app opens its
  own Live WebSocket with a single-use token minted server-side. Shipping the API
  key in an APK is a non-starter (an APK is a zip). Relaying audio through a
  function would double latency on a real-time call and turn one flaky connection
  into two.
- **D9 — Drug-drug checking is layered, not a lookup.** There is no free pairwise
  DDI API any more: NLM retired RxNav's `/interaction/` endpoints in January 2024
  and DrugBank's free checker goes in March 2026. So: curated rules first
  (deterministic, offline, never missed on a timeout), then openFDA label prose
  searched against the patient's real medication list, with RxNorm approximate
  matching for names said imprecisely aloud. Anything claiming a single free DDI
  endpoint is describing something dead.
- **D10 — `check_interaction` is NON_BLOCKING.** With blocking behaviour the model
  goes silent mid-sentence while the lookup runs, and a silent gap on a phone call
  reads as the line dropping. Tool responses are scheduled `WHEN_IDLE` so a result
  lands between sentences rather than cutting Cara off.
- **D11 — Escalation explanations are templated, not model-generated.** This text
  is the audit trail for an autonomous decision about someone's health and must
  state what the engine actually weighed. A model paraphrasing could produce
  something fluent that misstates the reasoning, which is the exact failure this
  product claims to avoid. (A model *is* used for conversational summaries; the
  difference is description versus justification.)
- **D12 — Escalation and the elder's "what I shared" entry are one batch write.**
  If an escalation could exist without the elder being told, the dignity position
  collapses. Keep them together.
- **D13 — Logging redacts by construction.** No medication, vitals, transcript,
  name or uid ever reaches Cloud Logging. `lib/logging.ts` rejects suspicious field
  names and over-long values, and `logError` deliberately does not log the error
  message or stack, because a Firestore or HTTP error routinely contains the
  document path or response body.
- **D14 — REVISED. The two clients degrade differently, and the example
  household must always say that it is one.** The original decision was that
  both clients fall back to Margaret rather than show a blank page. Half of it
  is gone and the other half needed a condition attached.

  **The phone falls back to empty, never to demo data.** An elder's own record
  showing a stranger's medications is not a nice empty state, it is wrong about
  the one thing it exists to be right about.

  **The dashboard still shows the example household**, because a freshly
  deployed dashboard rendering a blank page looks broken and Margaret
  communicates the product in a second. But it renders a full-width banner
  above the content, at every screen width, saying she is not real. This was
  discovered switched off in production: the badge keyed on
  `!isFirebaseConfigured`, which is false on the deployed site, while the
  example data keyed on `uid === null`, which was true for every signed-out
  visitor. So an anonymous visitor saw a complete invented medical record,
  with the one element whose job was to say so suppressed by exactly the
  condition that produced it. A fallback that cannot be told apart from real
  data is not a fallback, it is a fabrication, and on a health product it is
  the most damaging thing on this list.

### The reasoning engine, briefly

Concern score, not a counter. Criticality is a multiplier; repetition is weighted
super-linearly (two misses is not twice one miss, it is where "forgot" stops being
the best explanation); uncertainty scores higher than a clean miss; evidence decays
by recency. Retry aggressiveness scales with what is at stake, so a possibly-missed
anticoagulant is chased sooner than a statin, and past four attempts it escalates
rather than continuing to call, because more calls become harassment.

---

## 13. Status after Day 6

### Verified
- `functions` typechecks clean (`tsc --noEmit`, 0 errors)
- `web` typechecks clean and builds
- All four public routes plus five dashboard routes render

### NOT verified, and why
- **The app compiles but has never run.** CI builds a debug APK on every push.
  That proves the Kotlin is valid; it proves nothing about behaviour. No screen
  has been rendered on a device or an emulator.
- **The Gemini Live handshake has still never succeeded.** The client is now
  actually invoked, which it was not before, but no real session has connected.

### Deployed (Day 7)

The backend is live in `careloop-adam`, region `europe-west1`. All 17 functions
deployed; every callable was probed and returns 401 to an anonymous request,
which confirms both that it exists and that requireAuth is doing its job.

Three things bit during the first deploy, all recorded in `functions/.env` and
here so nobody rediscovers them:

1. `firebase deploy` fails discovery with "Cannot determine backend
   specification. Timeout after 10000" on this machine. The module itself loads
   in 1.2s, so it is not the code. Set `FUNCTIONS_DISCOVERY_TIMEOUT=120`.
2. `--non-interactive` refuses to use a `defineString` default and demands a
   value, so the non-secret model ids live in a committed `functions/.env`.
   Secrets stay in Secret Manager.
3. Enabling the Cloud Functions API takes a few minutes to propagate. The deploy
   immediately after enabling it fails with "Failed to list functions"; simply
   running it again works.
- The Gemini Live wire format is written from documentation, not from a successful
  handshake. The setup frame, tool-call and tool-response shapes are the most
  likely places to need adjustment.

### Open
- ~~No medication detail screen.~~ Wrong, it exists and works: route, screen,
  and an edit path, all reachable from the medication list.
- ~~Elder-side linking~~ **Done (Day 7).** The elder generates a code during
  onboarding and the caretaker redeems it on the dashboard. This was wired
  backwards before: the dashboard called generateLinkingCode, which the backend
  only ever permits from the patient themselves, so it could not have worked.
- Escalations are not yet pushed to the caretaker (no email or web push). They
  appear on the dashboard when it is open.
- ~~Agent threads are rendered by neither dashboard.~~ Both render them now. The
  web dashboard already did, on `/dashboard/reasoning`; the elder's phone did
  not, which meant the agent kept notes on a person that only that person's
  family could see. There is now a screen for it in the app, reachable from
  Settings beside "What I share".
- ~~No rules unit tests.~~ Done: 31 cases in `functions/src/rules.test.mts`, run
  on CI by `firebase emulators:exec` because the emulator cannot start on this
  machine (see section 9).

---

## 14. Status board, current

### Verified end to end, on real accounts, against the deployed backend

- **The autonomous escalation fires.** Seeded a throwaway elder with warfarin,
  submitted one check-in where it was missed and she sounded unsure, and the
  engine escalated on its own: `action: escalate`, concern score 1.7, three
  reasoning steps each traceable to real evidence including a direct quote. A
  second missed dose escalated again at `urgent` with higher confidence, so the
  ladder works rather than just the trigger. The linked family member could read
  it and the elder was told in the same moment.
- **The call loop closes.** `call.deliver.sent` -> `gemini.token.minted` ->
  `checkin.submitted` -> `call.outcome.reported`, and the check-in then appears
  on the phone and on a separate caretaker's dashboard.
- **The scheduler fires.** Check-in time set through the app's own Settings; the
  scheduler sent at 11:16:06 UTC and the phone rang 1.3 seconds later.
  `sweepStaleCalls` then closed out the unanswered call.
- **Two accounts, linked and unlinked for real**, both directions, including the
  `array-contains` list query the rules used to refuse.
- **Recording a reading works**, persists, and renders with the right time and a
  plain-language reading of whether it is in range.
- **Agent threads** are created through the real callable, readable by both the
  elder and the family, and rendered on both surfaces.
- **The website is live** at https://careloop--careloop-adam.europe-west4.hosted.app
  with automatic builds from main, and `/careloop.apk` redirects to a real
  downloadable APK that needs no login.
- **Declining a call reaches the server.** Pressed Decline on the real
  notification; `reportCallOutcome` logged
  `{"outcome":"declined","callAttemptId":"3OXZk05eZEe5ACRPchiz"}` seven seconds
  later. Before this, declining was a local log line and nothing else: the
  attempt sat pending until the stale sweep eventually wrote it off as
  "missed", so choosing not to answer and not being there became the same fact.
- **Firestore rules**: 31 cases green on CI. **Evaluation harness**: 16 green.

### Not verified

- **Talking back to Cara.** The emulator cannot capture microphone audio, so she
  speaks and is transcribed but has never heard an answer. The call screen says
  so plainly rather than appearing broken. This is the single largest remaining
  gap and it needs ten minutes on a real handset.
- **The agent-threads screen with data in it.** The empty state is verified on
  device; the populated state is not, because threads can only be written for the
  account the phone is signed into and there is no way to seed that account from
  here. The mapper is the same shape as six collections that do work, and the
  identical data renders correctly on the web dashboard.

### Left behind by testing

- A throwaway elder, Eleanor, with two escalations and two agent threads, linked
  to `careloop-test-caretaker@example.com`. Useful as a populated demo account;
  delete both from the console when it stops being useful.
- Adam's own account has real check-ins from testing, correctly labelled
  "Medications not covered" because nothing was established on those calls. They
  are genuine records of calls that happened, so they were left alone rather than
  tidied away.

### The rule this pass kept proving

Several separate bugs this session shared one shape: **a screen that looked right
while the thing behind it had not happened.** The call that transcribed
beautifully and saved nothing. The dashboard that showed a name with no data. The
summary that said "All medications taken" after a call where nothing was
established. The disconnect that reported failure after succeeding.

The screen is not the evidence. For the call loop the evidence is the Cloud
Functions log chain; for the rules it is the exact query the app runs; for
adherence it is what was actually confirmed, not what was merely not missed.

### And the same rule pointed the other two ways

The later pass found the same fault rotated, twice, which is worth keeping
because neither looks like the original at first glance.

**A screen that reports a fault while the server is fine.** Every failed manual
call request said "Cara could not be reached just now". The failure actually
hit was the backend's own rate limit, six manual calls an hour, working exactly
as designed and deliberately holding the request off. The app announced an
outage. It is the same bug as a screen that looks right over a broken backend:
the screen inventing its own account of events rather than repeating the
server's.

**Blank space that reads as a fact.** The caretaker dashboard drew something
for a confirmed escalation, something for a disputed one that carried a note,
and nothing at all for the other two states. Nothing, rendered directly beneath
Cara's account of what happened, does not read as "no information yet". It
reads as nobody objected. An absence in a position where the reader expects an
answer is not neutral, and on this product the absent answer was whether the
person it is about agrees with what was said about her.
