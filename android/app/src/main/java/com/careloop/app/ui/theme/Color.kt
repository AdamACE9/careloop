package com.careloop.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * CareLoop palette v2: warm-dominant (gold / amber / brown), one electric-aqua accent,
 * navy kept as the dark anchor for the call screen and headers.
 *
 * ## Why warm, and why this specific axis is safe
 *
 * Age-related colour vision loss degrades **blue-yellow discrimination first**, not
 * red-green as commonly assumed. That sounds like it rules out a gold-forward palette,
 * but gold and its brown/amber neighbours sit on the **red-yellow** axis, not blue-yellow
 * — it is the *pairing* of yellow against blue (or against white, which reads as the cool
 * end of that same axis under this kind of vision loss) that is the documented failure
 * case, not gold as a surface fill in isolation. Gold-on-warm-neutral is fine. What is
 * still true, and still enforced below: **never encode meaning in hue alone** — every
 * status still carries an icon or a text label, gold or not.
 *
 * ## Measured contrast ratios (WCAG 2.1 relative luminance), not estimates
 *
 * Elder-facing floor used throughout this file: **7:1 for any text under 24sp**, and
 * 4.5:1 only once text is 24sp or larger (headline-scale or bigger). This is stricter
 * than WCAG AA (4.5:1 / 3:1) on purpose — see Type.kt for why 24sp is the line.
 *
 * | Pair                                  | Ratio     | Verdict                              |
 * |----------------------------------------|-----------|---------------------------------------|
 * | Ink `#2A2117` on Cream/Surface/Bone/Cloud | 11.9-15.6:1 | far past the floor, body text default |
 * | Slate `#4E4538` on Cloud (worst case)  | 7.10:1    | passes at the floor, secondary text   |
 * | GoldDeep `#554410` on Bone (worst case)| 8.04:1    | passes; the text-safe gold             |
 * | GoldBright `#E3B023` on White          | 2.00:1    | fails hard — **never text on light**   |
 * | GoldBright `#E3B023` on Navy           | 7.40:1    | passes; large shapes/fills on dark too |
 * | Gold `#C9A227` on White                | 2.42:1    | fails — **never text on light**        |
 * | Gold `#C9A227` on Navy                 | 6.12:1    | below the 7:1 floor at small sizes     |
 * | Amber `#C97C1E` on White               | ~3.3:1    | fails — decorative/large-shape only    |
 * | Brown `#332316` on GoldBright          | 7.54:1    | passes; the content colour FOR gold fills |
 * | Brown `#332316` on Amber               | 4.58:1    | only clears the 24sp+ floor, not body  |
 * | Aqua `#1FC2CE` on White                | 2.17:1    | fails hard — **never text on light**   |
 * | AquaDeep `#0E5A60` on AquaSurface (worst case) | 7.14:1 | passes; the text-safe aqua        |
 * | Good/Concern/Urgent on White (worst case) | 8.00-8.05:1 | darkened from v1 to clear 7:1, not just 4.5:1 |
 * | White on Navy/NavyDeep/NavySoft        | 11.6-17.1:1 | unchanged, still excellent           |
 *
 * So, the rule this file enforces by construction: **GoldBright, Gold-on-Navy-at-small-
 * size, Amber and raw Aqua are surface/shape colours. They are not text colours on light
 * ground**, full stop. Where gold or aqua text is needed on a light surface, reach for
 * [GoldDeep] or [AquaDeep]. [Brown] exists specifically to be the thing that sits legibly
 * on top of a [GoldBright] or [Amber] fill, since something has to.
 *
 * ## The one flagged, deliberate exception
 *
 * The "CareLoop" wordmark on the incoming-call screen (`IncomingCallScreen.kt`, out of
 * this file's scope) is set in [Gold] on [Navy] at label size — 6.12:1, under the 7:1
 * floor. This is treated as a logotype, which is the one category WCAG 1.4.3 itself
 * exempts from the contrast minimum: a brand mark is recognised by shape as much as by
 * reading it. Flagged here rather than silently accepted; not extended to any other text.
 *
 * ## Compatibility
 *
 * [Yellow] is kept only because several screens outside this file's scope
 * (`LiveCallScreen.kt`, `MedicationsScreen.kt`, `CareLoopNavigation.kt`, `LoopMark.kt`)
 * still reference `CareColors.Yellow` and cannot be edited alongside this change without
 * risking a merge conflict with whoever is working on them. It now resolves to
 * [GoldBright] — brought into the new palette rather than left pointing at the old
 * `#FFD166` — and every live usage of it found in this codebase is on a Navy/NavyDeep
 * background (the live-call screen), where GoldBright measures 7.40:1: still safe.
 */
object CareColors {

    // ---- Warm neutrals (surfaces) ----
    // Four steps, lightest to deepest, so hierarchy reads without relying on colour:
    // Cream (page) > Surface (card) sits *above* Cream in lightness by design (cards
    // should lift off the page slightly), then Bone > Cloud for progressively more
    // separated containers, chips and dividers.
    /** Page background. Warm ivory, not paper-white — less glare under reading light. */
    val Cream = Color(0xFFFBF6EA)
    /** Card / sheet surface. */
    val Surface = Color(0xFFFFFDF9)
    /** Secondary warm neutral — subtle containers, e.g. tertiaryContainer. */
    val Bone = Color(0xFFF3ECDD)
    /** Deepest neutral variant — chips, dividers, chart gridlines. */
    val Cloud = Color(0xFFE8DFCB)

    /** Primary body text. 11.9:1 even on Cloud, the deepest surface it sits on. */
    val Ink = Color(0xFF2A2117)
    /** Secondary / caption text. 7.10:1 on Cloud (worst case) — right at the floor,
     *  not above it by accident, because this colour was solved for exactly that. */
    val Slate = Color(0xFF4E4538)

    // ---- Gold ladder ----
    /** Vivid gold. Large shapes and fills ONLY — 2.00:1 on white, nowhere near text-safe. */
    val GoldBright = Color(0xFFE3B023)
    /** Workhorse gold — icons and accents on Navy/NavyDeep (6.12-7.09:1). Not text-safe
     *  on light ground (2.42:1) and not quite at the 7:1 floor on Navy specifically at
     *  small sizes; see the flagged exception above. */
    val Gold = Color(0xFFC9A227)
    /** Darkened gold. The text-safe member of this family: 7.11-9.46:1 across every
     *  light surface in this file, including Bone at 8.04:1. */
    val GoldDeep = Color(0xFF554410)
    /** Compatibility alias — see class doc. */
    val Yellow = GoldBright

    // ---- Amber / Brown (the "and brown" of gold/amber/brown) ----
    /** Secondary warm accent. Large shapes/fills only, like GoldBright — measured
     *  around 3.3:1 on white, well short of even the 4.5:1 large-text floor. */
    val Amber = Color(0xFFC97C1E)
    /** Deep warm neutral. Its job is narrow and specific: be the content colour that
     *  sits legibly on top of a GoldBright or Amber fill. 7.54:1 on GoldBright, 4.58:1
     *  on Amber — the Amber pairing clears the 24sp+/4.5:1 floor, not the small-text
     *  7:1 floor, so keep Brown-on-Amber to icons and headline-scale labels only. */
    val Brown = Color(0xFF332316)

    // ---- Aqua (the single accent) ----
    /** Electric aqua. Reads darker and less saturated to older eyes than to younger
     *  ones (documented ageing effect on this hue), and per the brief this is a large-
     *  shape accent, never a sole state indicator. 2.17:1 on white — not for text. */
    val Aqua = Color(0xFF1FC2CE)
    /** Darkened aqua, the text-safe member: 7.14-7.92:1 across White/Cream/AquaSurface. */
    val AquaDeep = Color(0xFF0E5A60)
    /** Light aqua-tinted container. */
    val AquaSurface = Color(0xFFE3F7F8)

    // ---- Navy (kept — the dark anchor for the call screen and headers) ----
    val Navy = Color(0xFF16264D)
    val NavyDeep = Color(0xFF0F1A38)
    val NavySoft = Color(0xFF243766)

    val White = Color(0xFFFFFFFF)

    // ---- Semantic ----
    // Always paired with an icon or label in the UI — never colour alone. Darkened from
    // v1 of this palette: those values (Good #1E7A5A, Concern #B4690E, Urgent #B3261E)
    // passed the old 4.5:1 floor but not the 7:1 floor this file now holds itself to,
    // because all three are used as small-size text (bodyMedium/labelMedium/titleMedium)
    // in screens outside this file's scope, not just as icon tint.
    /** 8.00-8.05:1 on White/Cream/GoodSurface. */
    val Good = Color(0xFF165B43)
    val GoodSurface = Color(0xFFE6F4EF)
    /** 7.42-8.00:1 on White/Cream/ConcernSurface. */
    val Concern = Color(0xFF764509)
    val ConcernSurface = Color(0xFFFDF0E0)
    /** 7.41-8.01:1 on White/Cream/UrgentSurface. */
    val Urgent = Color(0xFF9B211A)
    val UrgentSurface = Color(0xFFFCEEEE)
}
