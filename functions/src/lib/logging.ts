import { createHash } from 'node:crypto';
import { logger } from 'firebase-functions/v2';

/**
 * Safe structured logging.
 *
 * CareLoop handles health-adjacent personal data, so the logging rule is
 * inverted from a normal app: **nothing identifying or clinical goes into logs at
 * all.** Cloud Logging is retained, exportable, and readable by anyone with
 * project access. A medication name in a log line is a data leak that no amount
 * of Firestore rule-writing undoes.
 *
 * NEVER log, under any circumstances:
 *   - medication names or doses
 *   - vitals readings
 *   - transcripts, summaries, or anything the person said
 *   - escalation text or reasoning
 *   - raw uids, emails, names, phone numbers
 *   - API keys or ephemeral tokens
 *
 * DO log: stable event names, hashed ids, durations, counts, error codes, and
 * boolean flags. That is enough to debug a flow without ever knowing who it was
 * about or what was wrong with them.
 *
 * The helpers below make the safe path the easy path. If you find yourself
 * wanting to log something not expressible through them, the answer is almost
 * always that it should not be logged.
 */

/**
 * One-way hash of an identifier, so log lines can be correlated across a request
 * without the logs containing the identity itself.
 *
 * Truncated to 12 hex chars: enough to distinguish users in practice, short
 * enough to stay readable, and not reversible.
 */
export function hashId(id: string): string {
  return createHash('sha256').update(id).digest('hex').slice(0, 12);
}

/** Values permitted in a log payload. Deliberately narrow. */
type SafeValue = string | number | boolean | null | undefined;

export interface SafeLogFields {
  /** Always a hashed id, never a raw uid. */
  patientHash?: string;
  callerHash?: string;
  callAttemptId?: string;
  checkInId?: string;
  durationMs?: number;
  count?: number;
  errorCode?: string;
  /** Free-form booleans/enums that carry no personal information. */
  [key: string]: SafeValue;
}

/**
 * Belt-and-braces filter.
 *
 * Even with the typed interface above, a caller can pass a string field whose
 * *value* is sensitive. We cannot detect that in general, but we can reject the
 * field names people reach for when they are about to leak something, and drop
 * anything long enough to be prose (a transcript or summary) rather than a code.
 */
const FORBIDDEN_KEY = /(name|email|phone|token|key|secret|transcript|summary|note|text|medication|dose|reading|value|address)/i;
const MAX_VALUE_LENGTH = 80;

function scrub(fields: SafeLogFields): Record<string, SafeValue> {
  const out: Record<string, SafeValue> = {};
  for (const [key, value] of Object.entries(fields)) {
    if (value === undefined) continue;

    if (FORBIDDEN_KEY.test(key)) {
      out[key] = '[redacted-key]';
      continue;
    }
    if (typeof value === 'string' && value.length > MAX_VALUE_LENGTH) {
      out[key] = '[redacted-long]';
      continue;
    }
    out[key] = value;
  }
  return out;
}

export function logEvent(event: string, fields: SafeLogFields = {}): void {
  logger.info(event, scrub(fields));
}

export function logWarn(event: string, fields: SafeLogFields = {}): void {
  logger.warn(event, scrub(fields));
}

/**
 * Log a failure without leaking its contents.
 *
 * Deliberately does NOT log the error message or stack. A thrown error from a
 * Firestore write or an HTTP client routinely contains the document path, the
 * request body, or the response payload — any of which can carry health data
 * straight into Cloud Logging. We record the error's *class* and a stable code,
 * which is what actually helps debugging.
 */
export function logError(
  event: string,
  errorCode: string,
  fields: SafeLogFields = {},
  error?: unknown,
): void {
  logger.error(event, {
    ...scrub(fields),
    errorCode,
    errorType: error instanceof Error ? error.constructor.name : typeof error,
  });
}

/** Times an operation and logs only its duration and success flag. */
export async function timed<T>(
  event: string,
  fields: SafeLogFields,
  fn: () => Promise<T>,
): Promise<T> {
  const started = Date.now();
  try {
    const result = await fn();
    logEvent(event, { ...fields, durationMs: Date.now() - started, ok: true });
    return result;
  } catch (error) {
    logError(event, 'OPERATION_FAILED', {
      ...fields,
      durationMs: Date.now() - started,
      ok: false,
    }, error);
    throw error;
  }
}
