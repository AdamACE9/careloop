# What only you can do

Everything else is built. This is the list of things gated behind your identity,
your accounts, or your billing, in the order you should do them. Each item says
where to go, roughly how long it takes, and what it unlocks.

Nothing below requires writing code.

---

## The short version

| # | What | Time | Cost |
|---|---|---|---|
| 1 | Create the Firebase project | 3 min | Free |
| 2 | Upgrade it to the Blaze plan | 3 min | Pay-as-you-go, effectively £0 at this scale |
| 3 | Enable Firestore, Auth, Cloud Messaging | 5 min | Free |
| 4 | Get a Gemini API key | 2 min | Free tier |
| 5 | Download `google-services.json` into the Android app | 2 min | Free |
| 6 | Put the web config into `web/.env.local` | 3 min | Free |
| 7 | Set the secrets and deploy the backend | 10 min | Free |
| 8 | Get an openFDA key (optional but recommended) | 2 min | Free |
| 9 | Verify the Gemini Live model id | 2 min | Free |

Roughly 30 minutes end to end.

---

## 1. Create the Firebase project

**Where:** <https://console.firebase.google.com> → **Add project**

Name it `careloop` (or anything, the id just has to be consistent afterwards).
Google Analytics is not needed. Turning it off actually reduces how much data
leaves the project, which suits a health-adjacent app.

**Unlocks:** everything else. Nothing works without this.

---

## 2. Upgrade to the Blaze plan

**Where:** Firebase console → ⚙️ → **Usage and billing** → **Modify plan** → Blaze

**This one is a real decision, so here is the honest version.**

Cloud Functions (2nd gen) cannot be deployed on the free Spark plan. The entire
backend is Cloud Functions, so without Blaze there is no scheduled calling, no
Gemini token minting, no interaction checking and no escalation engine.

Blaze is pay-as-you-go with a free monthly allowance on top of it. At the scale
of a demo and a handful of real users, the expected bill is **essentially zero**:
the free tier alone covers around 2 million function invocations a month, and
CareLoop makes a few dozen a day.

**Set a budget alert while you are in there.** Billing → Budgets and alerts → set
something like £5/month. It will never fire, but a runaway loop at 3am is exactly
the thing you want to hear about.

**Unlocks:** the entire backend.

---

## 3. Enable the three services

All in the Firebase console, left sidebar.

**a) Firestore Database** → Create database → **Production mode** → pick a
location near you. This project uses `eur3` (Europe multi-region), and the Cloud Functions region in `functions/src/lib/config.ts` is set to match it. If you pick a different location, change that constant and the two clients that name it.

Production mode matters: test mode leaves the database world-readable for 30
days, which for health data is not acceptable even briefly. The real rules are
already written in `firestore.rules` and get deployed in step 7.

**b) Authentication** → Get started → enable **BOTH**:

- **Email/Password** - the caretaker's dashboard sign-in.
- **Anonymous** - the elder's phone.

Anonymous is not optional and it is easy to miss. The phone signs in anonymously
so that a 78-year-old never has to invent or remember a password in order to
receive a phone call, and that anonymous uid *is* their patient id. Without this
provider enabled every single backend call from the phone fails with
`ADMIN_ONLY_OPERATION`: no linking code, no voice session, no interaction check,
no check-in. The app degrades quietly to demo data, so it looks like it is
working when it is not.

That is the caretaker sign-in. The elder never uses it; their device links with a
one-time code instead.

**c) Cloud Messaging** — no setup needed, it activates with the project. This is
what makes the phone ring.

**Unlocks:** data storage, caretaker accounts, and push-calls.

---

## 4. Get a Gemini API key

**Where:** <https://aistudio.google.com/apikey> → **Create API key**

Pick the same Google Cloud project Firebase created, so quota and billing sit in
one place.

**Copy it somewhere safe for a moment.** You will paste it in step 7 and then it
lives in Secret Manager, never in the repo.

**Unlocks:** Cara being able to speak. Without it the app rings, the call screen
appears, and there is silence.

> **Note on the free tier.** Free-tier Live API quota is tight, in the region of
> ten requests a minute. That is comfortably enough for a demo and a real
> household. It is not enough for many simultaneous users, and the backend is
> written to degrade honestly when it runs out (it says so, rather than failing
> silently).

---

## 5. Connect the Android app

**Where:** Firebase console → ⚙️ → Project settings → **Your apps** → Add app →
Android

- **Package name:** `com.careloop.app` — this must match exactly or nothing works
- Nickname and debug signing certificate can be skipped for now

Download **`google-services.json`** and put it at:

```
android/app/google-services.json
```

It is already gitignored, so it will not be committed.

**Unlocks:** the phone being able to receive calls and talk to the backend.

---

## 6. Configure the website

**Where:** Firebase console → ⚙️ → Project settings → **Your apps** → Add app →
Web (`</>`)

Copy the config values it shows you into a new file at `web/.env.local`:

```bash
NEXT_PUBLIC_FIREBASE_API_KEY=...
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=careloop-xxxx.firebaseapp.com
NEXT_PUBLIC_FIREBASE_PROJECT_ID=careloop-xxxx
NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET=careloop-xxxx.appspot.com
NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=...
NEXT_PUBLIC_FIREBASE_APP_ID=...
```

These are safe to be public. A Firebase web config is an address, not a
credential; access is decided entirely by the security rules. **The Gemini key is
not like this and must never go in a `NEXT_PUBLIC_` variable.**

When you deploy to Vercel, add the same six values under Project Settings →
Environment Variables.

**Unlocks:** the dashboard reading live data instead of the demo household.

---

## 7. Deploy the backend

In a terminal, from the project root:

```bash
npm install -g firebase-tools
firebase login
firebase use --add
```

Pick your project when prompted, and give it the alias `default`.

Then set the secrets. It will prompt you to paste each value:

```bash
firebase functions:secrets:set GEMINI_API_KEY
```

Then deploy everything:

```bash
firebase deploy --only firestore:rules,firestore:indexes,functions
```

First deploy takes a few minutes and may ask permission to enable some Google
Cloud APIs. Say yes.

**Unlocks:** all of it. After this the scheduler is live and calls will fire at
the configured time.

---

## 8. openFDA API key (optional, recommended)

**Where:** <https://open.fda.gov/apis/authentication/> → fill in the form, the key
arrives by email immediately.

```bash
firebase functions:secrets:set OPENFDA_API_KEY
```

openFDA works without a key, but the unauthenticated limit is 1,000 requests a
day per IP, shared across everything on that IP. A key raises it to 120,000.

**Unlocks:** not hitting a rate limit in the middle of a live demo.

---

## 9. Verify the Gemini Live model id

**Where:** <https://ai.google.dev/gemini-api/docs/models>

Check that the Live model id in `functions/src/lib/config.ts` still exists. Live
model names carry preview suffixes and are renamed periodically, and a retired id
fails at connect time.

It is a deploy-time parameter rather than hardcoded, so changing it needs no code
edit:

```bash
firebase deploy --only functions
```

and set `GEMINI_LIVE_MODEL` when prompted, or edit the default in `config.ts`.

**Pick a native-audio model.** Those are the ones supporting asynchronous function
calling, which is what lets Cara check a drug interaction without the call going
silent. On a model without it, the interaction check still works, it just pauses
the conversation while it runs.

---

## After all that: a five-minute smoke test

1. `cd web && npm run dev`, open <http://localhost:3000/login>, create an account.
   The dashboard should say **Connected** under Settings → Connection rather than
   "Running on demo data".
2. Open `android/` in Android Studio and run the app on a device or emulator.
3. Link the two sides. The direction matters: the code is generated on the PHONE,
   during onboarding ("Would you like someone to see how you are getting on?"), and
   typed into the web dashboard under Settings → Connect to a phone. Only the
   elder's own device can mint a code; the backend refuses it from anyone else.
4. Press **Check on her now** on the dashboard. The phone should ring within a few
   seconds with a full-screen call from Cara.
5. Answer it. Cara should speak.

If step 4 rings but step 5 is silent, it is the Gemini key or the model id, in
that order. If step 4 does not ring at all, check that the app has notification
permission and that `google-services.json` is in place.

---

## Things deliberately left for you to decide

- **Whether to enable App Check.** It stops someone hammering your Cloud Functions
  from a script and spending your Gemini quota. Worth doing before the app is
  public; unnecessary while it is only on your own phone.
- **Data retention.** Nothing currently deletes old check-ins. For real users you
  would want a policy, and that is a product decision rather than a technical one.
- **Whether to keep the demo household.** The dashboard falls back to it whenever
  a real account has no data, which keeps a fresh deploy from looking broken. If
  you would rather show a genuine empty state to real users, it is one flag in
  `web/src/lib/careloop-service.ts`.
