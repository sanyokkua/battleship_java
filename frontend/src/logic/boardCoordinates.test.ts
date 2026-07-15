import {describe, expect, it} from 'vitest';
import {formatCoordinateLabel} from './boardCoordinates';

describe('formatCoordinateLabel', () => {
    it('formats the top-left cell as A1', () => {
        expect(formatCoordinateLabel(0, 0)).toBe('A1');
    });

    it('formats the bottom-right cell of a 10x10 board as J10', () => {
        expect(formatCoordinateLabel(9, 9)).toBe('J10');
    });

    it('formats a mid-board cell as B2', () => {
        expect(formatCoordinateLabel(1, 1)).toBe('B2');
    });
});
