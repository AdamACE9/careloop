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

### Android app — code complete, **UNCOMPILED** (see §9 blocker)
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
- ⚠️ Emulator verification + screenshots — **BLOCKED**, Gradle cannot build here

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
- **D14 — Both clients degrade to demo data rather than erroring.** No Firebase
  config, or an empty account, shows the example household. A freshly deployed
  dashboard showing a blank page looks broken; showing Margaret communicates the
  product instantly. Real data replaces it the moment any exists.

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
- **The Android app has still never been compiled.** The Gradle blocker in §9
  persists. Retried this session including a new hypothesis (that the daemon's
  temp directory was being blocked); disproven, it is not the temp dir. Two real
  bugs were found by reading and fixed (`Flow.map` called as a non-extension, and
  a non-local `return@` from inside an inline function). **Assume more remain.
  Treat the first compile as a debugging session.**
- The Gemini Live wire format is written from documentation, not from a successful
  handshake. The setup frame, tool-call and tool-response shapes are the most
  likely places to need adjustment.

### Open
- No medication detail screen; `onMedicationClick` is wired but lands nowhere.
- Elder-side onboarding does not yet include the linking-code entry step. The
  backend endpoint exists and works; the screen does not.
- Escalations are not yet pushed to the caretaker (no email or web push). They
  appear on the dashboard when it is open.
- No rules unit tests. `@firebase/rules-unit-testing` is in devDependencies and
  the emulator is configured in `firebase.json`, so the setup cost is small.
