import type { GameAdapterError } from '../../adapters/AdapterErrors';

/**
 * The 10 backend error codes known to the `errors` i18n namespace (see
 * frontend/src/i18n/en/errors.json). Each maps to itself as the i18n key.
 */
const KNOWN_ERROR_CODES = new Set([
  'COORDINATE_INVALID',
  'EDITION_INVALID',
  'PLAYER_ID_INVALID',
  'PLAYER_NAME_INVALID',
  'PLAYER_NOT_ACTIVE',
  'SESSION_NOT_FOUND',
  'SHIP_DIRECTION_INVALID',
  'SHIP_ID_INVALID',
  'STAGE_INVALID',
  'INTERNAL',
]);

/**
 * Resolves a `GameAdapterError` to a key inside the `errors` i18n namespace.
 *
 * Primary: `err.errorCode`, when it's one of the known backend codes, IS the key
 * (errors.json uses the raw errorCode strings as its keys).
 *
 * Fallback (no errorCode, e.g. talking to an un-upgraded backend, or a network
 * failure): derive from `err.httpStatus` — 500 maps to "INTERNAL", anything else
 * (or no status at all) maps to "generic".
 *
 * `context` is reserved for future context-specific fallback keys (e.g. mapping the
 * same generic error differently depending on which action triggered it). errors.json
 * currently has no context-specific keys, so `context` is accepted but unused for now.
 */
export function resolveErrorMessageKey(err: GameAdapterError, context?: string): string {
  void context;

  if (err.errorCode && KNOWN_ERROR_CODES.has(err.errorCode)) {
    return err.errorCode;
  }

  if (err.httpStatus === 500) {
    return 'INTERNAL';
  }

  return 'generic';
}
