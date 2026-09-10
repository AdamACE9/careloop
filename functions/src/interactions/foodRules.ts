import type { FoodInteraction } from '../types.js';

/**
 * Curated food–drug interaction ruleset.
 *
 * ## Why this is hand-curated rather than fetched
 *
 * There is no clean public API for food–drug interactions, unlike drug–drug. So
 * this is a deliberately small, well-established set rather than an exhaustive
 * one: 30 interactions that are clinically real, weighted toward the medications
 * elderly people actually take. A short list that is right beats a long list that
 * is padded.
 *
 * ## The two rules that shaped every entry
 *
 * 1. **Every entry explains its mechanism.** "Avoid grapefruit" is an instruction
 *    people forget. "Grapefruit blocks the liver enzyme that clears this drug, so
 *    more of it stays in your blood" is a reason people remember. Explaining the
 *    why is what earns adherence, and it is also what distinguishes an agent that
 *    understands the interaction from a lookup table that matched two strings.
 *
 * 2. **Where the popular advice is wrong, we say so.** `commonMisconception`
 *    exists because several of these are routinely mis-stated in ways that cause
 *    harm. The worst offender is warfarin: telling someone to "avoid greens" is
 *    both wrong and dangerous, because it is *sudden change* that destabilises
 *    INR, not vegetables. An agent that repeats folk advice confidently is worse
 *    than one that says nothing.
 *
 * ## Safety framing
 *
 * Every `advice` string stays educational and routes to a clinician. It never
 * tells someone to start, stop, or change a dose. That boundary is deliberate:
 * it is the line between a wellness/informational tool and regulated clinical
 * decision support, and it is also simply the right thing for software that
 * cannot see someone's chart.
 *
 * Severity is our own three-level scale, not a clinical grading system.
 */

export const FOOD_INTERACTIONS: FoodInteraction[] = [
  // ---------------------------------------------------------------------------
  // Anticoagulants
  // ---------------------------------------------------------------------------
  {
    id: 'warfarin-vitamin-k',
    drugNames: ['warfarin', 'coumadin', 'jantoven'],
    drugClass: 'anticoagulant',
    food: 'Leafy greens (spinach, kale, broccoli, Brussels sprouts)',
    severity: 'moderate',
    whatItMeans:
      'Big changes in how many greens you eat can push your warfarin level up or down.',
    mechanism:
      'These are high in vitamin K, which is exactly what warfarin works against. Your dose was set around how much you normally eat, so it is a sudden change that unsettles it, not the greens themselves.',
    advice:
      'Keep your greens roughly steady week to week rather than avoiding them. If your diet changes a lot, mention it to your doctor so they can check your INR.',
    source: 'FDA label; US Pharmacist review of warfarin food interactions',
    commonMisconception:
      'People are often told to avoid green vegetables entirely on warfarin. That is wrong and can be harmful. Consistency is what matters, and greens are good for you.',
  },
  {
    id: 'warfarin-alcohol',
    drugNames: ['warfarin', 'coumadin'],
    drugClass: 'anticoagulant',
    food: 'Alcohol',
    severity: 'severe',
    whatItMeans:
      'Drinking heavily, or drinking much more than usual, can make bleeding more likely.',
    mechanism:
      'A large amount of alcohol at once slows the liver clearing warfarin, so more stays in your blood. Long-term heavy drinking does the opposite. Either way it moves your levels away from where your doctor set them.',
    advice:
      'A steady, modest amount is usually manageable. Sudden heavy drinking is the risky pattern. Tell your doctor what you actually drink so your dose fits your life.',
    source: 'NIAAA; FDA label',
    commonMisconception: null,
  },
  {
    id: 'warfarin-cranberry',
    drugNames: ['warfarin', 'coumadin'],
    drugClass: 'anticoagulant',
    food: 'Cranberry juice (large amounts)',
    severity: 'minor',
    whatItMeans:
      'Drinking a lot of cranberry juice every day may slightly increase warfarin\'s effect.',
    mechanism:
      'Cranberry appears to slow one of the liver enzymes that clears warfarin. The effect is small and the evidence is mixed.',
    advice:
      'An occasional glass is not a concern. If you drink it daily and in quantity, worth mentioning at your next INR check.',
    source: 'MHRA drug safety update',
    commonMisconception:
      'Often listed as a serious interaction. The evidence is actually weak and inconsistent; it does not warrant alarm.',
  },

  // ---------------------------------------------------------------------------
  // Grapefruit / CYP3A4
  // ---------------------------------------------------------------------------
  {
    id: 'statin-grapefruit',
    drugNames: ['atorvastatin', 'simvastatin', 'lipitor', 'zocor'],
    drugClass: 'statin',
    food: 'Grapefruit and grapefruit juice',
    severity: 'moderate',
    whatItMeans:
      'Grapefruit can raise the amount of this medicine in your blood more than intended, which makes muscle aches and, rarely, muscle damage more likely.',
    mechanism:
      'Grapefruit blocks a liver enzyme called CYP3A4 that normally clears the drug, so more of it stays in your system than the dose assumes.',
    advice:
      'Other citrus is fine. Oranges do not do this. If you love grapefruit, ask your doctor whether a different statin would suit you better, as some are unaffected.',
    source: 'FDA consumer guidance on grapefruit interactions',
    commonMisconception:
      'Applies to some statins, not all. Pravastatin and rosuvastatin are not cleared this way and are generally unaffected.',
  },
  {
    id: 'ccb-grapefruit',
    drugNames: ['amlodipine', 'felodipine', 'nifedipine', 'verapamil', 'diltiazem'],
    drugClass: 'calcium channel blocker',
    food: 'Grapefruit and grapefruit juice',
    severity: 'moderate',
    whatItMeans:
      'Grapefruit can increase the effect of this blood pressure medicine, which may leave you dizzy or light-headed.',
    mechanism:
      'Same liver enzyme, CYP3A4. Grapefruit blocks it, so more of the medicine stays active than your dose was set for.',
    advice:
      'Avoid grapefruit juice with this one. If you feel unusually dizzy when standing up, mention it to your doctor.',
    source: 'FDA consumer guidance',
    commonMisconception: null,
  },

  // ---------------------------------------------------------------------------
  // Thyroid
  // ---------------------------------------------------------------------------
  {
    id: 'levothyroxine-food-general',
    drugNames: ['levothyroxine', 'synthroid', 'levoxyl', 'eltroxin'],
    drugClass: 'thyroid hormone',
    food: 'Any food, especially breakfast',
    severity: 'moderate',
    whatItMeans:
      'Taking this with food means you absorb noticeably less of it, and your dose stops matching what you actually get.',
    mechanism:
      'Levothyroxine is absorbed in a narrow window in the small intestine. Food in the way physically blocks part of it.',
    advice:
      'Take it on an empty stomach, ideally 30 to 60 minutes before breakfast, with plain water. Being consistent day to day matters more than being perfect once.',
    source: 'FDA label; NIH review of levothyroxine absorption',
    commonMisconception: null,
  },
  {
    id: 'levothyroxine-calcium-iron',
    drugNames: ['levothyroxine', 'synthroid', 'levoxyl', 'eltroxin'],
    drugClass: 'thyroid hormone',
    food: 'Calcium supplements, iron supplements, dairy',
    severity: 'moderate',
    whatItMeans:
      'Calcium and iron can cut how much thyroid medicine you absorb, sometimes substantially.',
    mechanism:
      'They bind to levothyroxine in the gut and form a compound your body cannot take up, so it passes straight through.',
    advice:
      'Leave about four hours between your thyroid tablet and any calcium or iron. Morning tablet, supplements later in the day, works well.',
    source: 'NIH; FDA label',
    commonMisconception: null,
  },
  {
    id: 'levothyroxine-coffee',
    drugNames: ['levothyroxine', 'synthroid', 'eltroxin'],
    drugClass: 'thyroid hormone',
    food: 'Coffee',
    severity: 'minor',
    whatItMeans: 'Coffee taken straight after can reduce how much you absorb.',
    mechanism:
      'Coffee appears to speed things through the gut and bind some of the drug, shortening the window it has to be absorbed.',
    advice:
      'Wait 30 to 60 minutes after your tablet before your first cup. If that is hard, take the tablet earlier instead.',
    source: 'NIH review of levothyroxine absorption',
    commonMisconception: null,
  },

  // ---------------------------------------------------------------------------
  // Antibiotics — chelation
  // ---------------------------------------------------------------------------
  {
    id: 'tetracycline-dairy',
    drugNames: ['doxycycline', 'tetracycline', 'minocycline', 'lymecycline'],
    drugClass: 'tetracycline antibiotic',
    food: 'Milk, cheese, yoghurt, calcium or iron supplements, antacids',
    severity: 'moderate',
    whatItMeans:
      'Taken together, much less of the antibiotic gets into you, so the infection may not clear properly.',
    mechanism:
      'Calcium, iron and magnesium latch onto the antibiotic in your stomach and form a clump too large to be absorbed.',
    advice:
      'Leave at least two hours either side. Finish the full course even once you feel better.',
    source: 'FDA label; NIH review of cation chelation',
    commonMisconception: null,
  },
  {
    id: 'fluoroquinolone-dairy',
    drugNames: ['ciprofloxacin', 'levofloxacin', 'moxifloxacin', 'ofloxacin'],
    drugClass: 'fluoroquinolone antibiotic',
    food: 'Milk, yoghurt, calcium-fortified juice, iron or zinc supplements, antacids',
    severity: 'moderate',
    whatItMeans:
      'These can stop the antibiotic being absorbed properly, which risks the infection not clearing.',
    mechanism:
      'The same binding problem as with tetracyclines. Minerals in dairy and supplements bind the drug in the gut.',
    advice:
      'Take it two hours before, or six hours after, anything containing calcium, iron or magnesium.',
    source: 'FDA label',
    commonMisconception: null,
  },

  // ---------------------------------------------------------------------------
  // Iron
  // ---------------------------------------------------------------------------
  {
    id: 'iron-calcium',
    drugNames: ['ferrous sulfate', 'ferrous fumarate', 'ferrous gluconate', 'iron'],
    drugClass: 'iron supplement',
    food: 'Milk, cheese, yoghurt, calcium supplements',
    severity: 'moderate',
    whatItMeans: 'Your iron tablet works much less well if you take it with dairy.',
    mechanism:
      'Calcium and iron are absorbed through the same route in your gut, so they compete. Calcium usually wins.',
    advice:
      'Leave about two hours either side. A lunchtime tablet is already well away from a milky morning cup of tea.',
    source: 'NIH Office of Dietary Supplements',
    commonMisconception: null,
  },
  {
    id: 'iron-tea-coffee',
    drugNames: ['ferrous sulfate', 'ferrous fumarate', 'ferrous gluconate', 'iron'],
    drugClass: 'iron supplement',
    food: 'Tea and coffee',
    severity: 'moderate',
    whatItMeans:
      'A cup of tea with your iron tablet can cut how much iron you absorb by more than half.',
    mechanism:
      'Tannins in tea and coffee bind to iron and form a compound your body cannot take up. Black tea has the strongest effect.',
    advice:
      'Leave a couple of hours between your iron and your tea. Decaf still contains tannins, so it makes no difference.',
    source: 'NIH Office of Dietary Supplements',
    commonMisconception: null,
  },
  {
    id: 'iron-vitamin-c',
    drugNames: ['ferrous sulfate', 'ferrous fumarate', 'ferrous gluconate', 'iron'],
    drugClass: 'iron supplement',
    food: 'Orange juice and other vitamin C sources',
    severity: 'minor',
    whatItMeans:
      'This one is helpful rather than harmful. Vitamin C helps you absorb more iron.',
    mechanism:
      'Vitamin C converts iron into a form your gut takes up more easily.',
    advice:
      'A glass of orange juice with your iron tablet is a reasonable habit if it suits you.',
    source: 'NIH Office of Dietary Supplements',
    commonMisconception: null,
  },

  // ---------------------------------------------------------------------------
  // Blood pressure / potassium
  // ---------------------------------------------------------------------------
  {
    id: 'ace-potassium',
    drugNames: ['lisinopril', 'ramipril', 'enalapril', 'perindopril', 'captopril'],
    drugClass: 'ACE inhibitor',
    food: 'Salt substitutes (LoSalt and similar), potassium supplements',
    severity: 'severe',
    whatItMeans:
      'These medicines already make your body hold on to potassium. Adding more can push it too high, which affects your heart rhythm.',
    mechanism:
      'ACE inhibitors reduce how much potassium your kidneys get rid of. Most "low sodium" salt substitutes are mostly potassium chloride, so they add a lot without it being obvious.',
    advice:
      'Avoid potassium-based salt substitutes and potassium supplements unless your doctor has specifically told you to take them. Ordinary amounts of bananas and potatoes are usually fine.',
    source: 'FDA label; NHS guidance on ACE inhibitors',
    commonMisconception:
      'People often worry about bananas while missing salt substitutes, which contribute far more potassium and are the real risk.',
  },
  {
    id: 'arb-potassium',
    drugNames: ['losartan', 'candesartan', 'valsartan', 'irbesartan'],
    drugClass: 'ARB',
    food: 'Salt substitutes, potassium supplements',
    severity: 'severe',
    whatItMeans:
      'Like ACE inhibitors, these make your body retain potassium, and too much affects your heart rhythm.',
    mechanism:
      'They block the hormone signal that tells your kidneys to excrete potassium.',
    advice:
      'Avoid potassium-based salt substitutes and supplements unless prescribed. Your doctor will usually check your potassium with a blood test.',
    source: 'FDA label',
    commonMisconception: null,
  },
  {
    id: 'potassium-sparing-diuretic',
    drugNames: ['spironolactone', 'amiloride', 'triamterene', 'eplerenone'],
    drugClass: 'potassium-sparing diuretic',
    food: 'Salt substitutes, potassium supplements, very large amounts of high-potassium food',
    severity: 'severe',
    whatItMeans:
      'This type of water tablet deliberately keeps potassium in your body, so extra potassium can build up to a dangerous level.',
    mechanism:
      'It blocks the channel your kidneys use to excrete potassium. Anything that adds potassium has nowhere to go.',
    advice:
      'Avoid potassium supplements and salt substitutes. Regular blood tests matter with this medicine, so keep those appointments.',
    source: 'FDA label',
    commonMisconception: null,
  },

  // ---------------------------------------------------------------------------
  // Diabetes
  // ---------------------------------------------------------------------------
  {
    id: 'metformin-alcohol',
    drugNames: ['metformin', 'glucophage'],
    drugClass: 'biguanide',
    food: 'Alcohol',
    severity: 'severe',
    whatItMeans:
      'Drinking heavily while taking metformin can cause a rare but serious build-up of acid in the blood.',
    mechanism:
      'Both alcohol and metformin interfere with how your body clears lactic acid. Together, and especially if your kidneys are not at their best, it can accumulate.',
    advice:
      'Avoid heavy or binge drinking. If you feel very sick, breathless, or unusually weak and cold after drinking, seek medical help promptly. Tell your doctor honestly what you drink.',
    source: 'FDA label; case literature on metformin-associated lactic acidosis',
    commonMisconception: null,
  },
  {
    id: 'sulfonylurea-alcohol',
    drugNames: ['gliclazide', 'glipizide', 'glimepiride', 'glibenclamide', 'glyburide'],
    drugClass: 'sulfonylurea',
    food: 'Alcohol, especially without food',
    severity: 'severe',
    whatItMeans:
      'Alcohol on an empty stomach with these tablets can drop your blood sugar dangerously low.',
    mechanism:
      'These tablets lower blood sugar, and alcohol stops your liver releasing stored sugar to correct it. The two effects stack.',
    advice:
      'If you drink, always eat with it, and never on an empty stomach. Know your low-blood-sugar signs: shakiness, sweating, confusion.',
    source: 'FDA label',
    commonMisconception: null,
  },
  {
    id: 'metformin-b12',
    drugNames: ['metformin', 'glucophage'],
    drugClass: 'biguanide',
    food: 'Vitamin B12 (long-term monitoring, not an interaction to avoid)',
    severity: 'minor',
    whatItMeans:
      'Taking metformin for many years can gradually lower your vitamin B12 level.',
    mechanism:
      'Metformin interferes with how B12 is absorbed at the end of the small intestine.',
    advice:
      'Nothing to change day to day. Worth asking your doctor to check your B12 occasionally if you have taken metformin for several years, particularly if your hands or feet feel numb or tingly.',
    source: 'FDA label; NIH',
    commonMisconception: null,
  },

  // ---------------------------------------------------------------------------
  // Bone
  // ---------------------------------------------------------------------------
  {
    id: 'bisphosphonate-food',
    drugNames: ['alendronate', 'alendronic acid', 'risedronate', 'ibandronate', 'fosamax'],
    drugClass: 'bisphosphonate',
    food: 'Any food or drink other than plain water, including coffee and juice',
    severity: 'severe',
    whatItMeans:
      'Taken with anything but plain water, almost none of this medicine is absorbed, so it simply does not work.',
    mechanism:
      'Bisphosphonates are very poorly absorbed at the best of times. Any food, and particularly calcium, binds what little would get through.',
    advice:
      'Take it first thing with a full glass of plain tap water, then stay upright and eat nothing for at least 30 minutes. Staying upright also protects your gullet from irritation.',
    source: 'FDA label',
    commonMisconception:
      'Mineral water and coffee both count as "not plain water" here. People often assume any drink is fine.',
  },

  // ---------------------------------------------------------------------------
  // Heart
  // ---------------------------------------------------------------------------
  {
    id: 'digoxin-fibre',
    drugNames: ['digoxin', 'lanoxin'],
    drugClass: 'cardiac glycoside',
    food: 'High-fibre foods and fibre supplements (bran, psyllium)',
    severity: 'moderate',
    whatItMeans:
      'A sudden increase in fibre can reduce how much digoxin you absorb, and digoxin has a narrow safe range.',
    mechanism:
      'Fibre binds digoxin in the gut and carries part of it through before it can be absorbed.',
    advice:
      'Keep your fibre intake fairly steady rather than avoiding it. If you start a fibre supplement, mention it so your levels can be rechecked.',
    source: 'FDA label',
    commonMisconception: null,
  },
  {
    id: 'digoxin-st-johns-wort',
    drugNames: ['digoxin', 'lanoxin'],
    drugClass: 'cardiac glycoside',
    food: "St John's wort (herbal supplement)",
    severity: 'severe',
    whatItMeans:
      "St John's wort can lower your digoxin level enough that it stops controlling your heart properly.",
    mechanism:
      "It speeds up the transporter that pumps digoxin back out of your gut cells, so much less reaches your bloodstream.",
    advice:
      "Do not take St John's wort with digoxin. If you already are, do not stop suddenly without telling your doctor, because your levels will then rise.",
    source: 'FDA label; NIH herb-drug interaction literature',
    commonMisconception:
      'Herbal supplements are widely assumed to be harmless alongside prescriptions. This one is a genuinely significant interaction.',
  },

  // ---------------------------------------------------------------------------
  // Psychiatric
  // ---------------------------------------------------------------------------
  {
    id: 'maoi-tyramine',
    drugNames: ['phenelzine', 'tranylcypromine', 'isocarboxazid', 'nardil', 'parnate'],
    drugClass: 'MAOI',
    food: 'Aged cheese, cured and smoked meats, soy sauce, miso, draught beer, yeast extract (Marmite)',
    severity: 'severe',
    whatItMeans:
      'These foods can cause a sudden, dangerous rise in blood pressure with this medicine.',
    mechanism:
      'These foods contain tyramine. This medicine blocks the enzyme that normally breaks tyramine down, so it builds up and triggers a surge in blood pressure.',
    advice:
      'This is one of the few genuine "avoid entirely" situations. A sudden severe headache, chest pain, or pounding heartbeat after eating needs urgent medical attention.',
    source: 'FDA label; psychiatric prescribing guidance',
    commonMisconception:
      'Fresh cheese and fresh meat are fine. It is ageing, curing and fermenting that create tyramine, not the food category.',
  },
  {
    id: 'selegiline-patch-tyramine',
    drugNames: ['selegiline', 'emsam'],
    drugClass: 'MAO-B inhibitor',
    food: 'Aged and fermented foods (high-tyramine)',
    severity: 'minor',
    whatItMeans:
      'At the low doses used in the skin patch, the strict diet needed with older MAOIs generally does not apply.',
    mechanism:
      'At low dose this medicine mainly blocks a different enzyme subtype from the one that handles tyramine in the gut, so tyramine is still broken down normally.',
    advice:
      'Follow whatever your own prescriber told you, since it depends on your dose. Do not assume the strict older MAOI diet applies without asking.',
    source: 'FDA label for transdermal selegiline',
    commonMisconception:
      'Often lumped in with older MAOIs and their strict diet. At low transdermal doses the risk is substantially lower.',
  },

  // ---------------------------------------------------------------------------
  // Pain and stomach
  // ---------------------------------------------------------------------------
  {
    id: 'nsaid-alcohol',
    drugNames: ['ibuprofen', 'naproxen', 'diclofenac', 'aspirin'],
    drugClass: 'NSAID',
    food: 'Alcohol',
    severity: 'moderate',
    whatItMeans:
      'Together these make stomach bleeding more likely, and it can start without obvious pain.',
    mechanism:
      'Both irritate the stomach lining. NSAIDs also reduce the protective mucus your stomach normally makes.',
    advice:
      'Take painkillers with food, and go easy on alcohol while you need them. Black or tarry stools, or vomiting that looks like coffee grounds, need urgent attention.',
    source: 'FDA label',
    commonMisconception: null,
  },
  {
    id: 'ppi-b12',
    drugNames: ['omeprazole', 'lansoprazole', 'pantoprazole', 'esomeprazole'],
    drugClass: 'proton pump inhibitor',
    food: 'Vitamin B12 from food (long-term monitoring)',
    severity: 'minor',
    whatItMeans:
      'Taking these for years can slowly reduce how much B12 you get from food.',
    mechanism:
      'Stomach acid is needed to release B12 from food. These medicines deliberately reduce that acid.',
    advice:
      'No day-to-day change needed. Worth mentioning at a review if you have taken it for several years, especially alongside metformin.',
    source: 'NIH',
    commonMisconception: null,
  },
  {
    id: 'ppi-timing',
    drugNames: ['omeprazole', 'lansoprazole', 'pantoprazole', 'esomeprazole'],
    drugClass: 'proton pump inhibitor',
    food: 'Meal timing',
    severity: 'minor',
    whatItMeans:
      'These work considerably better taken before food rather than after.',
    mechanism:
      'They only switch off acid pumps that are actively working, and a meal is what activates them. Taken after eating, the moment has passed.',
    advice: 'Take it about 30 minutes before your first meal of the day.',
    source: 'FDA label',
    commonMisconception:
      'Frequently taken at bedtime or after meals, which meaningfully reduces how well it works.',
  },

  // ---------------------------------------------------------------------------
  // Other
  // ---------------------------------------------------------------------------
  {
    id: 'statin-alcohol',
    drugNames: ['atorvastatin', 'simvastatin', 'rosuvastatin', 'pravastatin'],
    drugClass: 'statin',
    food: 'Alcohol (heavy use)',
    severity: 'moderate',
    whatItMeans:
      'Heavy drinking alongside a statin raises the risk of liver irritation and muscle problems.',
    mechanism:
      'Both are processed by the liver, and heavy drinking makes that processing less predictable.',
    advice:
      'Moderate drinking is generally fine. Unexplained muscle aching or weakness, or dark urine, is worth reporting promptly.',
    source: 'FDA label',
    commonMisconception: null,
  },
  {
    id: 'antihistamine-alcohol',
    drugNames: ['chlorphenamine', 'diphenhydramine', 'promethazine', 'hydroxyzine'],
    drugClass: 'sedating antihistamine',
    food: 'Alcohol',
    severity: 'moderate',
    whatItMeans:
      'Together these cause much more drowsiness than either alone, which raises the risk of a fall.',
    mechanism: 'Both suppress the central nervous system, and the effects add together.',
    advice:
      'Avoid alcohol while taking these, particularly at night. Falls are the real risk here rather than the drowsiness itself.',
    source: 'FDA label',
    commonMisconception: null,
  },
  {
    id: 'benzodiazepine-alcohol',
    drugNames: ['diazepam', 'lorazepam', 'temazepam', 'zopiclone', 'zolpidem'],
    drugClass: 'sedative',
    food: 'Alcohol',
    severity: 'severe',
    whatItMeans:
      'This combination can slow your breathing and badly affect balance and memory.',
    mechanism:
      'Both act on the same calming system in the brain, and the combined effect is greater than adding them up would suggest.',
    advice:
      'Avoid alcohol entirely with these. If someone is unusually hard to rouse after both, treat that as an emergency.',
    source: 'FDA boxed warning',
    commonMisconception: null,
  },
  {
    id: 'levodopa-protein',
    drugNames: ['levodopa', 'co-careldopa', 'sinemet', 'madopar'],
    drugClass: 'dopamine precursor',
    food: 'High-protein meals',
    severity: 'moderate',
    whatItMeans:
      'A protein-heavy meal can blunt the effect of a dose, so symptoms return sooner.',
    mechanism:
      'Levodopa uses the same transporter as protein building blocks to cross from gut to blood and into the brain, so they compete for the same doorway.',
    advice:
      'Take it 30 minutes before meals where you can. If protein clearly affects you, a dietitian can help redistribute it across the day.',
    source: 'FDA label; Parkinson\'s clinical guidance',
    commonMisconception: null,
  },
];

/** Index for fast case-insensitive lookup by drug name. */
const BY_DRUG_NAME = new Map<string, FoodInteraction[]>();
for (const rule of FOOD_INTERACTIONS) {
  for (const name of rule.drugNames) {
    const key = name.toLowerCase();
    const list = BY_DRUG_NAME.get(key) ?? [];
    list.push(rule);
    BY_DRUG_NAME.set(key, list);
  }
}

/**
 * Finds food interactions for a medication.
 *
 * Matching is deliberately forgiving in one direction only: we accept a stored
 * medication name that *contains* a known drug name ("Ferrous sulfate 200mg"
 * matches "ferrous sulfate"), because medication lists carry strengths and brand
 * suffixes. We do not do fuzzy or partial-word matching, because a false positive
 * here means telling someone their medicine interacts with something when it does
 * not, and unnecessary alarm about medication is its own harm.
 */
export function findFoodInteractions(medicationName: string): FoodInteraction[] {
  const needle = medicationName.toLowerCase().trim();
  if (!needle) return [];

  const exact = BY_DRUG_NAME.get(needle);
  if (exact) return exact;

  const matches: FoodInteraction[] = [];
  for (const [name, rules] of BY_DRUG_NAME) {
    if (needle.includes(name)) matches.push(...rules);
  }
  return [...new Set(matches)];
}

/** All food interactions relevant to a whole medication list, deduplicated. */
export function findFoodInteractionsForList(
  medicationNames: string[],
): FoodInteraction[] {
  const found = new Set<FoodInteraction>();
  for (const name of medicationNames) {
    for (const rule of findFoodInteractions(name)) found.add(rule);
  }
  return [...found];
}

/**
 * Matches a food the person mentioned against the rules that apply to their
 * medications. This is the call-time path: they say "I've been having a lot of
 * grapefruit", and we check that against what they actually take.
 */
export function findFoodInteractionsByFood(
  food: string,
  medicationNames: string[],
): FoodInteraction[] {
  const needle = food.toLowerCase().trim();
  if (!needle) return [];

  return findFoodInteractionsForList(medicationNames).filter((rule) => {
    const haystack = `${rule.food} ${rule.id}`.toLowerCase();
    return (
      haystack.includes(needle) ||
      needle.split(/\s+/).some((word) => word.length > 3 && haystack.includes(word))
    );
  });
}
