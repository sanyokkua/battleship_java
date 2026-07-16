import {GameAdapterError} from '../../adapters/AdapterErrors';
import {resolveErrorMessageKey} from './errorMapping';

const KNOWN_CODES = [
    'COORDINATE_INVALID',
    'EDITION_INVALID',
    'PLAYER_ID_INVALID',
    'PLAYER_NAME_INVALID',
    'PLAYER_NOT_ACTIVE',
    'CELL_ALREADY_SHOT',
    'SESSION_NOT_FOUND',
    'SHIP_DIRECTION_INVALID',
    'SHIP_ID_INVALID',
    'STAGE_INVALID',
];

describe('resolveErrorMessageKey', () => {
    it.each(KNOWN_CODES)('maps errorCode=%s to itself', (code) => {
        const err = new GameAdapterError('boom', {errorCode: code});
        expect(resolveErrorMessageKey(err)).toBe(code);
    });

    it('maps errorCode=INTERNAL to itself', () => {
        const err = new GameAdapterError('boom', {errorCode: 'INTERNAL'});
        expect(resolveErrorMessageKey(err)).toBe('INTERNAL');
    });

    it('falls back to "INTERNAL" when errorCode is absent and httpStatus is 500', () => {
        const err = new GameAdapterError('boom', {httpStatus: 500});
        expect(resolveErrorMessageKey(err)).toBe('INTERNAL');
    });

    it('falls back to "generic" when errorCode is absent and httpStatus is some other value', () => {
        const err = new GameAdapterError('boom', {httpStatus: 400});
        expect(resolveErrorMessageKey(err)).toBe('generic');
    });

    it('falls back to "generic" when neither errorCode nor httpStatus is present', () => {
        const err = new GameAdapterError('boom');
        expect(resolveErrorMessageKey(err)).toBe('generic');
    });

    it('falls back to "generic" for an unrecognized errorCode with no httpStatus', () => {
        const err = new GameAdapterError('boom', {errorCode: 'SOME_UNKNOWN_CODE'});
        expect(resolveErrorMessageKey(err)).toBe('generic');
    });

    it('falls back based on httpStatus when errorCode is unrecognized', () => {
        const err = new GameAdapterError('boom', {errorCode: 'SOME_UNKNOWN_CODE', httpStatus: 500});
        expect(resolveErrorMessageKey(err)).toBe('INTERNAL');
    });

    it('accepts an optional context param without affecting the result', () => {
        const err = new GameAdapterError('boom', {errorCode: 'STAGE_INVALID'});
        expect(resolveErrorMessageKey(err, 'preparation.addShip')).toBe('STAGE_INVALID');
    });
});
