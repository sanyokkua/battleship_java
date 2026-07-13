/**
 * Checks whether a string is non-null and longer than 2 characters.
 * <p>
 * Used for lightweight form-field validation (e.g. player name) before a request
 * is sent to the backend.
 *
 * @param stringValue the string to validate, or null
 * @returns true if stringValue is non-null and has a length greater than 2, false otherwise
 */
function isValidString(stringValue: string | null): boolean {
    return Boolean(stringValue && stringValue.length > 2);
}

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Checks whether a string is a well-formed UUID (case-insensitive, surrounding
 * whitespace is trimmed before validation).
 * <p>
 * Used to validate a session ID entered on the join-game screen before it is
 * submitted to the backend.
 *
 * @param value the string to validate, or null
 * @returns true if value (after trimming) matches the canonical 8-4-4-4-12 hex UUID format, false otherwise
 */
function isValidUuid(value: string | null): boolean {
    return Boolean(value && UUID_REGEX.test(value.trim()));
}

export {isValidString, isValidUuid};