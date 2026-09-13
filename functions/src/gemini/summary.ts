import { GEMINI_API_HOST } from '../lib/config.js';
import { logWarn } from '../lib/logging.js';
import type { TranscriptLine } from '../types.js';

/**
 * Cara's memory of what a call was actually about.
 *
 * ## Why this exists
 *
 * Until now the only thing carried from one call to the next was a templated
 * line built from which medications were confirmed or missed. A person could
 * spend a call telling Cara their knee was bad and they were dreading a
 * hospital appointment, and the next day she knew only "Warfarin taken." She
 * had no memory of the person, only of the pill box.
 *
 * ## Why a model, when escalations are templated
 *
 * D11 keeps escalation text templated because it is the justification for an
 * autonomous decision and must state exactly what the engine weighed. This is
 * description, not justification: it decides nothing and nothing is decided
 * from it. The engine never reads it. It only gives Cara continuity on the next
 * call, which is the job a model is good at and a template cannot do.
 *
 * ## The privacy bargain
 *
 * The summary is visible to the person themselves and to anyone they have
 * linked, on the same record as the check-in. So it is written as something the
 * person would be comfortable reading about themselves: factual, short, no
 * clinical inference, no characterisation of their mood beyond what they said.
 *
 * Never logged. A failure returns null and the call record stands without it:
 * a missing summary costs Cara some memory, a failed check-in write would cost
 * the record itself.
 */
export async function summariseConversation(
  apiKey: string,
  model: string,
  preferredName: string,
  transcript: TranscriptLine[],
): Promise<string | null> {
  // A call too short to have been a conversation has nothing worth
  // remembering, and asking a model to summarise two lines invites it to pad.
  const spoken = transcript.filter((l) => l.text.trim().length > 0);
  if (spoken.filter((l) => l.speaker === 'elder').length < 2) return null;

  const dialogue = spoken
    .slice(-120)
    .map((l) => `${l.speaker === 'cara' ? 'Cara' : preferredName}: ${l.text.trim()}`)
    .join('\n');

  const instruction = `You write the memory note a caring companion called Cara keeps after a phone call with ${preferredName}, so that on tomorrow's call she remembers what they talked about.

Write at most three short plain sentences. Include only things ${preferredName} actually said that are worth remembering tomorrow: how they were feeling, any pain or symptom, plans or worries they mentioned, people or events in their life, anything Cara said she would follow up. Medication adherence is recorded separately, so mention it only if something notable was said about it.

Rules:
- Only what was said. No diagnosis, no guessing at causes, no clinical language.
- ${preferredName} and their family will both read this, so write it as something ${preferredName} would be comfortable reading about themselves.
- Refer to them as ${preferredName}. Do not use quotation marks. No preamble.
- If nothing beyond a routine check was discussed, reply with exactly: NOTHING`;

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15_000);

  try {
    const response = await fetch(
      `https://${GEMINI_API_HOST}/v1beta/models/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(apiKey)}`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        signal: controller.signal,
        body: JSON.stringify({
          systemInstruction: { parts: [{ text: instruction }] },
          contents: [{ role: 'user', parts: [{ text: dialogue }] }],
          generationConfig: {
            temperature: 0.2,
            maxOutputTokens: 200,
            // Summaries do not need extended thinking, and on flash models it
            // eats the output budget and returns an empty candidate.
            thinkingConfig: { thinkingBudget: 0 },
          },
        }),
      },
    );

    if (!response.ok) {
      logWarn('memory.summary_failed', { status: response.status });
      return null;
    }

    const data = (await response.json()) as {
      candidates?: Array<{ content?: { parts?: Array<{ text?: string }> } }>;
    };
    const text = (data.candidates?.[0]?.content?.parts ?? [])
      .map((p) => p.text ?? '')
      .join('')
      .trim();

    if (!text || text.toUpperCase().startsWith('NOTHING')) return null;
    // Bounded, in case the model ignores the length instruction.
    return text.length > 600 ? `${text.slice(0, 597)}...` : text;
  } catch {
    logWarn('memory.summary_failed', { status: 0 });
    return null;
  } finally {
    clearTimeout(timeout);
  }
}
