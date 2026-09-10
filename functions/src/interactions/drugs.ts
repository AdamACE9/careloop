import { getFirestore } from 'firebase-admin/firestore';
import type { DrugInteraction, InteractionSeverity } from '../types.js';
import { INTERACTION_CACHE_TTL_MS } from '../lib/config.js';
import { logEvent, logWarn } from '../lib/logging.js';

/**
 * Drug–drug interaction checking.
 *
 * ## Why this is not a single API call
 *
 * The obvious design would be "ask an API whether drug A interacts with drug B".
 * That API no longer exists in free form: the NLM retired RxNav's
 * `/interaction/` endpoints in January 2024, and DrugBank's free interaction
 * checker follows in March 2026. Anything claiming otherwise is describing a
 * dead endpoint.
 *
 * So this module layers three sources, strongest first:
 *
 *   1. **A curated ruleset** of high-severity interactions that genuinely matter
 *      for this population. These must never depend on a network call or on text
 *      matching succeeding — warfarin plus ibuprofen has to be caught every
 *      single time, offline, deterministically.
 *
 *   2. **openFDA label text**, searched for the *other* medications the person
 *      actually takes. The label's interaction section is free prose rather than
 *      structured data, so this is a text search, not a lookup. It catches real
 *      interactions the curated list does not cover, at the cost of occasionally
 *      matching loosely.
 *
 *   3. **RxNorm name resolution**, because people say drug names imprecisely over
 *      a phone. `getApproximateMatch` turns "the water tablet, frusemide" into a
 *      concept we can actually look up.
 *
 * ## Caching
 *
 * Interaction data is effectively static, and both APIs are rate-limited (RxNav
 * around 20 requests/second; openFDA 240/minute unauthenticated). Every network
 * result is cached in Firestore for a week. During a live call this matters: a
 * cache hit is the difference between Cara answering naturally and Cara pausing.
 */

const RXNAV_BASE = 'https://rxnav.nlm.nih.gov/REST';
const OPENFDA_BASE = 'https://api.fda.gov/drug/label.json';

/** Network calls during a live call must fail fast rather than stall the conversation. */
const FETCH_TIMEOUT_MS = 4000;

// =============================================================================
// Curated high-severity interactions
// =============================================================================

/**
 * Interactions serious enough that we refuse to depend on a network call or on
 * label prose to catch them.
 *
 * Kept small on purpose. This is not an attempt to replicate a clinical database;
 * it is a floor beneath the other two layers, covering combinations that are both
 * genuinely dangerous and genuinely common in elderly polypharmacy.
 */
const CURATED_DDI: Array<{
  a: string[];
  b: string[];
  severity: InteractionSeverity;
  whatItMeans: string;
  mechanism: string;
  advice: string;
}> = [
  {
    a: ['warfarin', 'coumadin', 'acenocoumarol'],
    b: ['ibuprofen', 'naproxen', 'diclofenac', 'aspirin', 'nsaid', 'nurofen', 'advil'],
    severity: 'severe',
    whatItMeans:
      'Taken together, these make bleeding much more likely, including bleeding in the stomach that can be hard to notice at first.',
    mechanism:
      'Anti-inflammatory painkillers irritate the stomach lining and also stop platelets clumping properly. Warfarin is already slowing your clotting. The two effects stack.',
    advice:
      'Paracetamol is usually the safer choice for pain alongside warfarin. Worth checking with your GP or pharmacist before taking any more.',
  },
  {
    a: ['warfarin', 'coumadin'],
    b: ['amiodarone', 'fluconazole', 'metronidazole', 'trimethoprim', 'ciprofloxacin'],
    severity: 'severe',
    whatItMeans:
      'This combination can raise your warfarin level sharply and increase bleeding risk.',
    mechanism:
      'These medicines slow the liver enzyme that clears warfarin, so more of it stays active than your dose assumes.',
    advice:
      'Your doctor will usually want to check your INR within a few days of starting this. Do not skip that test.',
  },
  {
    a: ['ace inhibitor', 'lisinopril', 'ramipril', 'enalapril', 'perindopril',
        'losartan', 'candesartan', 'valsartan'],
    b: ['spironolactone', 'amiloride', 'triamterene', 'potassium'],
    severity: 'severe',
    whatItMeans:
      'Both of these make your body hold on to potassium, and too much affects your heart rhythm.',
    mechanism:
      'They act on the same system from different angles, and neither lets your kidneys get rid of potassium normally.',
    advice:
      'This combination is sometimes prescribed deliberately, but it needs regular blood tests. Keep those appointments.',
  },
  {
    a: ['ace inhibitor', 'lisinopril', 'ramipril', 'enalapril', 'perindopril',
        'losartan', 'candesartan', 'valsartan'],
    b: ['ibuprofen', 'naproxen', 'diclofenac', 'nsaid'],
    severity: 'moderate',
    whatItMeans:
      'Regular anti-inflammatory painkillers can reduce how well your blood pressure medicine works and put strain on your kidneys.',
    mechanism:
      'Both affect blood flow through the kidney, from opposite directions. Together they can reduce filtration, especially if you are also on a water tablet or are dehydrated.',
    advice:
      'Occasional use is usually manageable. Regular use is worth a conversation with your GP, and paracetamol is often a better option.',
  },
  {
    a: ['simvastatin', 'atorvastatin'],
    b: ['clarithromycin', 'erythromycin', 'itraconazole', 'ketoconazole', 'amiodarone'],
    severity: 'severe',
    whatItMeans:
      'This combination can raise your statin level enough to cause muscle damage.',
    mechanism:
      'These medicines block the liver enzyme that clears the statin, so it builds up well beyond the intended dose.',
    advice:
      'Doctors often pause the statin for the few days of an antibiotic course. Do not stop anything on your own, but do flag it.',
  },
  {
    a: ['metformin'],
    b: ['contrast', 'iodinated contrast'],
    severity: 'severe',
    whatItMeans:
      'Metformin is usually paused around scans that use contrast dye.',
    mechanism:
      'Contrast dye can temporarily reduce kidney function, and metformin needs working kidneys to clear safely.',
    advice:
      'If you have a CT scan booked, make sure the team knows you take metformin.',
  },
  {
    a: ['digoxin'],
    b: ['amiodarone', 'verapamil', 'spironolactone', 'clarithromycin'],
    severity: 'severe',
    whatItMeans:
      'These can push your digoxin level up into the range where it causes harm.',
    mechanism:
      'They reduce how much digoxin your body clears. Digoxin has a narrow window between working and being toxic.',
    advice:
      'Nausea, visual disturbance, or a slow pulse are worth reporting promptly. Your doctor may check a digoxin level.',
  },
  {
    a: ['ssri', 'sertraline', 'fluoxetine', 'citalopram', 'escitalopram', 'paroxetine'],
    b: ['warfarin', 'ibuprofen', 'naproxen', 'aspirin', 'diclofenac'],
    severity: 'moderate',
    whatItMeans:
      'This combination raises the risk of stomach bleeding, particularly in older people.',
    mechanism:
      'These antidepressants reduce platelets\' ability to clot, which adds to the stomach irritation from anti-inflammatories.',
    advice:
      'Often managed by adding a stomach-protecting tablet. Worth raising with your GP rather than stopping anything yourself.',
  },
  {
    a: ['tramadol', 'ssri', 'sertraline', 'fluoxetine', 'citalopram'],
    b: ['tramadol', 'sumatriptan', 'linezolid', 'st johns wort'],
    severity: 'severe',
    whatItMeans:
      'Together these can cause a rare but serious reaction with agitation, sweating, tremor and a racing heart.',
    mechanism:
      'Each raises serotonin levels, and combined they can push it too high.',
    advice:
      'If you feel agitated, shivery and your heart is racing after starting a new medicine, seek medical advice the same day.',
  },
];

// =============================================================================
// Name resolution (RxNorm)
// =============================================================================

async function fetchJson(url: string): Promise<unknown | null> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
  try {
    const response = await fetch(url, {
      signal: controller.signal,
      headers: { 'User-Agent': 'CareLoop/1.0 (medication adherence companion)' },
    });
    if (!response.ok) return null;
    return await response.json();
  } catch {
    // Timeouts and network faults are expected during a live call. The caller
    // degrades to the curated ruleset rather than failing the conversation.
    return null;
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Resolves a spoken drug name to a canonical RxNorm concept.
 *
 * Tries exact match first, then RxNav's approximate matcher. The approximate
 * path is the important one: over a phone, an 84-year-old says "my water tablet,
 * furosemide" or "frusemide" (the old British name), and an exact match returns
 * nothing.
 */
export async function resolveDrugName(
  spokenName: string,
): Promise<{ rxcui: string; name: string } | null> {
  const query = spokenName.trim();
  if (!query) return null;

  const cached = await readCache(`rxnorm:${query.toLowerCase()}`);
  if (cached) return cached as { rxcui: string; name: string } | null;

  // Exact.
  const exact = await fetchJson(
    `${RXNAV_BASE}/rxcui.json?name=${encodeURIComponent(query)}&search=1`,
  ) as { idGroup?: { rxnormId?: string[] } } | null;

  const exactId = exact?.idGroup?.rxnormId?.[0];
  if (exactId) {
    const resolved = { rxcui: exactId, name: query };
    await writeCache(`rxnorm:${query.toLowerCase()}`, resolved);
    return resolved;
  }

  // Approximate.
  const approx = await fetchJson(
    `${RXNAV_BASE}/approximateTerm.json?term=${encodeURIComponent(query)}&maxEntries=1`,
  ) as {
    approximateGroup?: { candidate?: Array<{ rxcui?: string; name?: string; score?: string }> };
  } | null;

  const candidate = approx?.approximateGroup?.candidate?.[0];
  if (candidate?.rxcui) {
    // Reject weak matches. A bad resolution is worse than no resolution: it means
    // checking interactions for a drug the person does not take.
    const score = Number(candidate.score ?? '0');
    if (score >= 50) {
      const resolved = { rxcui: candidate.rxcui, name: candidate.name ?? query };
      await writeCache(`rxnorm:${query.toLowerCase()}`, resolved);
      return resolved;
    }
  }

  await writeCache(`rxnorm:${query.toLowerCase()}`, null);
  return null;
}

// =============================================================================
// openFDA label lookup
// =============================================================================

/**
 * Fetches a drug's official label interaction text from openFDA.
 *
 * Returns free prose, not structured data. That is a real limitation and the
 * reason this is one layer of three rather than the whole answer.
 */
async function fetchLabelInteractionText(
  drugName: string,
  apiKey: string | null,
): Promise<string | null> {
  const cacheKey = `openfda:${drugName.toLowerCase()}`;
  const cached = await readCache(cacheKey);
  if (cached !== undefined) return cached as string | null;

  const search = encodeURIComponent(
    `openfda.generic_name:"${drugName}" OR openfda.brand_name:"${drugName}"`,
  );
  const keyParam = apiKey ? `&api_key=${apiKey}` : '';
  const data = await fetchJson(
    `${OPENFDA_BASE}?search=${search}&limit=1${keyParam}`,
  ) as { results?: Array<{ drug_interactions?: string[] }> } | null;

  const text = data?.results?.[0]?.drug_interactions?.join(' ') ?? null;
  await writeCache(cacheKey, text);
  return text;
}

// =============================================================================
// The check
// =============================================================================

/**
 * Checks one substance against a person's existing medication list.
 *
 * Layer order matters. Curated rules run first and unconditionally, so a severe
 * interaction is never missed because a network call timed out.
 */
export async function checkDrugInteractions(params: {
  substance: string;
  currentMedications: string[];
  openFdaApiKey: string | null;
}): Promise<{ resolvedName: string | null; interactions: DrugInteraction[] }> {
  const { substance, currentMedications, openFdaApiKey } = params;
  const found: DrugInteraction[] = [];
  const seen = new Set<string>();

  const substanceLower = substance.toLowerCase().trim();

  // --- Layer 1: curated rules (offline, deterministic) ---
  for (const rule of CURATED_DDI) {
    const matchesA = rule.a.some((n) => substanceLower.includes(n));
    const matchesB = rule.b.some((n) => substanceLower.includes(n));
    if (!matchesA && !matchesB) continue;

    // The substance matched one side; does anything they already take match the other?
    const otherSide = matchesA ? rule.b : rule.a;
    for (const med of currentMedications) {
      const medLower = med.toLowerCase();
      if (!otherSide.some((n) => medLower.includes(n))) continue;

      const id = `curated:${rule.a[0]}:${rule.b[0]}`;
      if (seen.has(id)) continue;
      seen.add(id);

      found.push({
        id,
        drugA: substance,
        drugB: med,
        severity: rule.severity,
        whatItMeans: rule.whatItMeans,
        mechanism: rule.mechanism,
        advice: rule.advice,
        source: 'CareLoop reviewed interaction list',
      });
    }
  }

  // --- Layer 2: name resolution ---
  const resolved = await resolveDrugName(substance);

  // --- Layer 3: openFDA label text, searched for their actual medications ---
  const labelText = await fetchLabelInteractionText(
    resolved?.name ?? substance,
    openFdaApiKey,
  );

  if (labelText) {
    const haystack = labelText.toLowerCase();
    for (const med of currentMedications) {
      const medKey = med.toLowerCase().split(/\s+/)[0];
      if (!medKey || medKey.length < 4) continue;
      if (!haystack.includes(medKey)) continue;

      const id = `label:${medKey}`;
      if (seen.has(id)) continue;
      seen.add(id);

      found.push({
        id,
        drugA: resolved?.name ?? substance,
        drugB: med,
        // Label prose carries no severity grading, so we do not invent one.
        // Calling everything "severe" would train people to ignore warnings.
        severity: 'moderate',
        whatItMeans: `The official label for ${resolved?.name ?? substance} mentions ${med}.`,
        mechanism: extractRelevantSentence(labelText, medKey),
        advice:
          'Worth mentioning to your pharmacist or GP so they can tell you whether it matters for you specifically.',
        source: 'openFDA drug label',
      });
    }
  }

  logEvent('interactions.checked', {
    curatedHits: found.filter((f) => f.id.startsWith('curated')).length,
    labelHits: found.filter((f) => f.id.startsWith('label')).length,
    resolved: resolved !== null,
  });

  return { resolvedName: resolved?.name ?? null, interactions: found };
}

/**
 * Pulls the sentence mentioning a drug out of label prose.
 *
 * Label text runs to thousands of words. Handing all of it to a text-to-speech
 * agent, or showing it on a phone, is useless. One sentence is usually the
 * relevant claim.
 */
function extractRelevantSentence(text: string, keyword: string): string {
  const sentences = text.split(/(?<=[.!?])\s+/);
  const hit = sentences.find((s) => s.toLowerCase().includes(keyword));
  if (!hit) return 'See the medication label for details.';
  const trimmed = hit.trim();
  return trimmed.length > 320 ? `${trimmed.slice(0, 317)}...` : trimmed;
}

// =============================================================================
// Cache
// =============================================================================

async function readCache(key: string): Promise<unknown | undefined> {
  try {
    const snap = await getFirestore().doc(`_interactionCache/${encodeKey(key)}`).get();
    if (!snap.exists) return undefined;
    const data = snap.data() as { value: unknown; cachedAt: number } | undefined;
    if (!data) return undefined;
    if (Date.now() - data.cachedAt > INTERACTION_CACHE_TTL_MS) return undefined;
    return data.value;
  } catch {
    return undefined;
  }
}

async function writeCache(key: string, value: unknown): Promise<void> {
  try {
    await getFirestore()
      .doc(`_interactionCache/${encodeKey(key)}`)
      .set({ value: value ?? null, cachedAt: Date.now() });
  } catch (error) {
    logWarn('interactions.cache_write_failed');
  }
}

/** Firestore document ids may not contain '/'. */
function encodeKey(key: string): string {
  return key.replace(/\//g, '_').slice(0, 1400);
}
