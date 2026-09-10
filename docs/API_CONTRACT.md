# CareLoop API contract

The surface between the clients (Android app, web dashboard) and the backend
(Firebase Cloud Functions v2, Firestore).

Written down deliberately. Two clients in two languages talk to this, and the
failure mode of implicit coupling is a field renamed on one side that silently
produces a blank screen on the other rather than an error.

**Source of truth:** `functions/src/types.ts`. The Android model in
`data/model/Models.kt` mirrors it field for field. If you change one, change all three.

---

## Conventions

- **Timestamps** cross the wire as ISO-8601 strings, never Firestore `Timestamp`.
  Kotlin and TypeScript disagree about deserialising the native type; ISO-8601 is
  unambiguous in both.
- **Enums** cross the wire as lowercase snake_case strings (`missed_dose`,
  `blood_sugar`).
- **Every callable requires authentication.** Unauthenticated calls get
  `unauthenticated`.
- **Errors** use standard Firebase callable codes. The `message` is a stable
  machine-readable token (`QUOTA_EXHAUSTED`, `INVALID_CODE`), not prose. Clients
  translate those into human sentences; the server never writes user-facing copy.

### Error codes

| Code | Meaning | What the client should do |
|---|---|---|
| `unauthenticated` | Not signed in | Send to sign-in |
| `permission-denied` | Not this patient, or not linked | Show a generic refusal. Never confirm whether the record exists |
| `resource-exhausted` | Rate limit, daily call cap, or Gemini quota | Say so honestly and suggest waiting |
| `not-found` | Patient missing, or invalid linking code | Generic message. Deliberately identical to permission-denied for codes, so the endpoint cannot be used to enumerate them |
| `invalid-argument` | Failed validation | A bug. Log and show a generic failure |
| `internal` | Unexpected | Generic failure, retry once |

---

## Firestore layout

```
users/{uid}
  role                "elder" | "caretaker"
  displayName, email, createdAt

patients/{patientId}                 patientId is always the elder's auth uid
  profile             { firstName, lastName, preferredName, age, conditions[] }
  caretakerIds        string[]       SERVER-WRITTEN ONLY
  dailyCheckInTime    "HH:mm"        elder-owned
  timezone            IANA zone
  sharingPreferences  { enabledCategories[], alwaysShareUrgent, privacyHoldUntil }
  pendingRetryAt      ISO-8601 | absent

  medications/{id}    name, dose, purpose, schedule[], criticality,
                      dosesRemaining, dosesPerDay, refillLeadTimeDays,
                      foodGuidance, rxcui
  checkIns/{id}       startedAt, durationSeconds, status, medicationsConfirmed[],
                      medicationsMissed[], transcript[], toneSignals, caraSummary
  vitals/{id}         type, value, secondaryValue, recordedAt, source
  escalations/{id}    raisedAt, severity, headline, explanation, reasoning[],
                      confidence, alternativesConsidered[], concernScore,
                      elderResponse, elderNote, acknowledged
  sharedItems/{id}    sharedAt, category, whatCaraSaid, elderResponse, escalationId
  callAttempts/{id}   sentAt, trigger, outcome, attemptNumber, deliveryError
  refills/{medId}     medicationName, dosesRemaining, daysOfSupplyRemaining,
                      needsRefill, lastReminderAt

deviceTokens/{uid}    token, platform, updatedAt      NEVER client-readable
linkingCodes/{code}   patientUid, expiresAt, used     NEVER client-readable
```

### Who can do what

| Path | Elder | Linked caretaker | Anyone else |
|---|---|---|---|
| `patients/{self}` | read, limited update | read | nothing |
| `medications` | full | read | nothing |
| `checkIns` | read | read | nothing |
| `vitals` | read, create manual | read | nothing |
| `escalations` | read, respond | read, acknowledge | nothing |
| `sharedItems` | read, respond | read | nothing |
| `callAttempts` | read, report outcome | read | nothing |
| `deviceTokens/{self}` | write only | nothing | nothing |
| `linkingCodes` | nothing | nothing | nothing |

Three things are load-bearing and worth restating:

1. **`caretakerIds` is frozen against all client writes.** A client that could
   edit it could grant itself read access to any health record by writing its own
   uid in. Only the linking function changes it.
2. **Agent output is read-only to everyone.** Escalation reasoning, adherence
   results and call outcomes are written by the server. If a client could edit
   them, the evidence behind an autonomous decision could be forged.
3. **Rules do not inherit.** Every subcollection has its own rules. A new
   subcollection with no rules is denied, which is the safe failure but looks like
   a bug.

---

## Callable functions

All at region `us-central1`.

### `registerDevice`

Registers this device for push-calls. Call on launch and from `onNewToken`.

```ts
Request  { token: string, platform: "android" | "ios" | "web" }
Response { ok: true }
```

Called more often than strictly necessary on purpose. A stale token fails
silently: FCM accepts the send and the phone never rings, which looks from the
outside like the person ignored their check-in.

---

### `mintLiveSessionToken`

Mints a short-lived token so the app can open its own Gemini Live WebSocket.

```ts
Request  { patientId: string }
Response { token: string, expiresAt: ISO8601, model: string, wsHost: string }
```

**Caller must be the patient.** Rate limited to 12/hour.

The token is single-use, expires in 15 minutes, and is constrained to one model
and audio-only. The API key never leaves the server. **Never log the token.**

Errors: `resource-exhausted` with `QUOTA_EXHAUSTED` when Gemini's free-tier limit
is hit, or `BUSY` when too many live sessions are already open. Both are expected
and the app says something honest rather than failing generically.

---

### `triggerCall`

Fires an incoming call. Used by the dashboard button and by the scheduler.

```ts
Request  { patientId: string, trigger: "scheduled" | "manual" | "retry" }
Response { callAttemptId: string, delivered: boolean, reason: string | null,
           patientName: string }
```

**Caller must be the patient or a linked caretaker.** Rate limited to 6/hour,
hard-capped at 4 calls per patient per day.

`delivered: false` is a normal outcome, not an error. `reason` is
`NO_DEVICE_TOKEN`, `DEVICE_UNREGISTERED` or `DELIVERY_FAILED`, and the retry logic
treats each differently. An undelivered call is evidence the reasoning engine
needs: a phone that has been off for two days is a different situation from
someone declining to answer.

---

### `reportCallOutcome`

The device reporting what happened.

```ts
Request  { patientId, callAttemptId, outcome: "answered" | "declined" | "missed",
           durationSeconds?: number }
Response { ok: true }
```

**Caller must be the patient.**

FCM cannot tell the server whether anyone answered. This is the only source of
that fact. If it is not called, a sweep marks the attempt `missed` after 45
seconds, which then feeds retry and escalation.

---

### `checkInteraction`

Checks a substance against the patient's medications. Called by Cara mid-call.

```ts
Request  { patientId, substance: string, kind: "drug" | "food" }
Response { found: boolean, resolvedName: string | null,
           drugInteractions: DrugInteraction[],
           foodInteractions: FoodInteraction[],
           spokenSummary: string }
```

**Caller must be the patient.**

`spokenSummary` is one or two sentences ready to say aloud. Reading five
interactions to an 84-year-old on a phone is useless; the most serious finding
with its mechanism is what lands.

Layered internally, strongest first: a curated ruleset (offline, deterministic,
never missed on a timeout), then openFDA label text searched against the patient's
real medication list, with RxNorm approximate matching for names said imprecisely.
There is no free pairwise drug-drug API any more, so a single lookup is not an
option. Never throws on network failure; degrades to what is known offline.

---

### `getDietGuidance`

Food interactions for the whole medication list.

```ts
Request  { patientId }
Response { interactions: FoodInteraction[] }
```

**Caller must be the patient or a linked caretaker.**

---

### `submitCheckIn`

Records a completed call and runs the reasoning engine.

```ts
Request  { patientId, callAttemptId, durationSeconds,
           medicationsConfirmed: string[], medicationsMissed: string[],
           transcript: TranscriptLine[],
           toneSignals: { confusion: 0..1, hesitation: 0..1, note: string|null },
           vitals: Array<{ type, value, secondaryValue? }> }

Response { checkInId: string,
           action: "no_action" | "retry_soon" | "retry_later" | "escalate",
           escalationId: string | null }
```

**Caller must be the patient.**

This is where autonomy happens. The engine reads a week of history, weighs it by
medication criticality, and decides independently whether to say nothing, call
back, or tell the family.

When it escalates, the escalation and the elder's "what I shared" entry are
written in **one batch**. If an escalation could exist without the elder being
told, the entire dignity position collapses.

---

### `generateLinkingCode` / `redeemLinkingCode`

```ts
generateLinkingCode
Request  { patientId }              caller must be the patient
Response { code: string, expiresAt: ISO8601 }

redeemLinkingCode
Request  { code: string }           caller becomes a linked caretaker
Response { patientId: string, patientName: string }
```

Rate limited: 5 generations/hour, 8 redemptions/hour.

Codes are 8 characters from a 31-symbol alphabet excluding `O`, `0`, `I`, `1` and
`L`. Somebody reads this aloud to a 78-year-old over the telephone, and "was that
an O or a zero" is a real failure rather than a hypothetical one.

Invalid, expired and already-used codes all return the same `INVALID_CODE`.
Distinguishing them would let an attacker learn which codes exist.

---

## Scheduled functions

| Function | Schedule | What it does |
|---|---|---|
| `scheduledCheckInCalls` | every 5 min | Fires calls for patients whose local check-in time has arrived. Timezone via `Intl` so daylight saving is the platform's problem, not ours |
| `sweepStaleCalls` | every 5 min | Marks attempts with no reported outcome as `missed` after 45s, then runs the reasoning engine on that fact |
| `fireDueRetries` | every 5 min | Fires retries whose moment has come. Retries are written as intent and picked up on the next tick; no function ever sleeps waiting to call back |
| `updateRefillPlanning` | daily 08:00 UTC | Recomputes days of supply and raises a low-severity note before a medication runs out. Reminds once per week per medication, not daily, because a daily nag is how people learn to ignore an app |

---

## The FCM message

Sent by `deliverCall`. Three things about it are load-bearing.

```json
{
  "token": "<device token>",
  "data": {
    "type": "incoming_call",
    "callAttemptId": "...",
    "patientId": "...",
    "callerName": "Cara",
    "attemptNumber": "1",
    "initiatedAt": "2026-09-10T09:00:00.000Z"
  },
  "android": {
    "priority": "high",
    "ttl": 300000,
    "collapseKey": "careloop_call_<patientId>"
  }
}
```

1. **Data-only. There is no `notification` block, deliberately.** With one, the
   system tray handles the message while the app is backgrounded and
   `onMessageReceived` never fires, which is where the call UI is built. Adding a
   `notification` block silently reduces the incoming call to a banner, and the
   bug is invisible until you test on a backgrounded device.

2. **`priority: high`** is the only thing that gets a message through Doze. Note
   that FCM may de-prioritise an app whose high-priority messages consistently
   produce no interaction, so a run of unanswered calls can itself degrade
   delivery. That is one reason the retry logic is conservative.

3. **Short TTL.** A check-in arriving 90 minutes late is worse than one that never
   arrives: the person is confused by a call about a dose they already took, and
   the agent records a bogus outcome. The TTL drops a stale call rather than
   delivering it.

---

## What is deliberately not here

- **No endpoint returns another patient's data**, in any shape, to any caller.
- **No endpoint accepts a `patientId` without checking the caller against it.**
  The Admin SDK bypasses Firestore rules entirely, so a callable that skipped that
  check would be a hole straight through the whole access model no matter how
  careful the rules file is.
- **No user-facing prose comes from the server**, except Cara's own words in
  escalations and check-in summaries, which are the product. Error text is a
  token; the client writes the sentence.
