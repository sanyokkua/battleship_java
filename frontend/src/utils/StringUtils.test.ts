import { describe, expect, it } from 'vitest';
import { isValidString, isValidUuid } from './StringUtils';

describe('isValidString', () => {
  it('rejects null', () => {
    expect(isValidString(null)).toBe(false);
  });

  it('rejects the empty string', () => {
    expect(isValidString('')).toBe(false);
  });

  it('rejects a 1-character string', () => {
    expect(isValidString('A')).toBe(false);
  });

  it('rejects a 2-character string (exact lower boundary, still too short)', () => {
    expect(isValidString('AB')).toBe(false);
  });

  it('accepts a 3-character string (exact lower boundary, now long enough)', () => {
    expect(isValidString('ABC')).toBe(true);
  });

  it('accepts a string well above the boundary', () => {
    expect(isValidString('Alice')).toBe(true);
  });

  it('does not trim whitespace: a 3-space string counts as length 3 and passes', () => {
    expect(isValidString('   ')).toBe(true);
  });

  it('does not trim whitespace: a 2-space string counts as length 2 and fails', () => {
    expect(isValidString('  ')).toBe(false);
  });

  it('counts leading/trailing whitespace toward the length (no implicit trim)', () => {
    expect(isValidString(' A')).toBe(false); // length 2 -> fails
    expect(isValidString(' Al')).toBe(true); // length 3 -> passes
  });
});

describe('isValidUuid', () => {
  const validLower = '8f4c23c4-f86e-465c-a208-65f70281bfcb';
  const validUpper = '8F4C23C4-F86E-465C-A208-65F70281BFCB';
  const validMixed = '8f4C23c4-F86e-465C-a208-65F70281bFCb';

  it('rejects null', () => {
    expect(isValidUuid(null)).toBe(false);
  });

  it('rejects the empty string', () => {
    expect(isValidUuid('')).toBe(false);
  });

  it('accepts a well-formed lowercase UUID', () => {
    expect(isValidUuid(validLower)).toBe(true);
  });

  it('is case-insensitive: accepts a well-formed uppercase UUID', () => {
    expect(isValidUuid(validUpper)).toBe(true);
  });

  it('is case-insensitive: accepts a well-formed mixed-case UUID', () => {
    expect(isValidUuid(validMixed)).toBe(true);
  });

  it('trims surrounding whitespace before validating', () => {
    expect(isValidUuid(`  ${validLower}  `)).toBe(true);
    expect(isValidUuid(`\t${validLower}\n`)).toBe(true);
  });

  it('rejects internal whitespace even if overall length matches', () => {
    expect(isValidUuid('8f4c23c4-f86e-465c-a208-65f70281bfc ')).toBe(false);
    expect(isValidUuid('8f4c23c4-f86e-465c-a208- 65f70281bfcb')).toBe(false);
  });

  it('rejects a UUID with a too-short first segment (7 hex chars instead of 8)', () => {
    expect(isValidUuid('8f4c23c-f86e-465c-a208-65f70281bfcb')).toBe(false);
  });

  it('rejects a UUID with a too-long first segment (9 hex chars instead of 8)', () => {
    expect(isValidUuid('8f4c23c40-f86e-465c-a208-65f70281bfcb')).toBe(false);
  });

  it('rejects a UUID with a too-short last segment (11 hex chars instead of 12)', () => {
    expect(isValidUuid('8f4c23c4-f86e-465c-a208-65f70281bfc')).toBe(false);
  });

  it('rejects a UUID with a too-long last segment (13 hex chars instead of 12)', () => {
    expect(isValidUuid('8f4c23c4-f86e-465c-a208-65f70281bfcb1')).toBe(false);
  });

  it('rejects a UUID with a malformed middle segment length', () => {
    expect(isValidUuid('8f4c23c4-f86-465c-a208-65f70281bfcb')).toBe(false); // 3 chars instead of 4
    expect(isValidUuid('8f4c23c4-f86ee-465c-a208-65f70281bfcb')).toBe(false); // 5 chars instead of 4
  });

  it('rejects missing hyphens even with the right total character count', () => {
    expect(isValidUuid('8f4c23c4f86e465ca20865f70281bfcb')).toBe(false);
  });

  it('rejects hyphens in the wrong positions', () => {
    expect(isValidUuid('8f4c23c4f-86e-465c-a208-65f70281bfcb')).toBe(false);
  });

  it('rejects non-hex characters in a segment', () => {
    expect(isValidUuid('8f4c23c4-f86e-465c-a208-65f70281bfcg')).toBe(false); // 'g' is not hex
    expect(isValidUuid('zzzzzzzz-f86e-465c-a208-65f70281bfcb')).toBe(false);
  });

  it('rejects a plain non-UUID string', () => {
    expect(isValidUuid('not-a-uuid')).toBe(false);
  });
});
