import {describe, expect, it} from 'vitest';
import type {CellDto} from './ApplicationTypes';
import {computeMoatCellKeys, computeSunkShipIds} from './boardState';

function emptyField(rows = 4, columns = 4): CellDto[][] {
    return Array.from({length: rows}, (_, row) =>
        Array.from({length: columns}, (_, col) => ({
            row,
            col,
            ship: null,
            hasShot: false,
            isAvailable: true,
        })),
    );
}

describe('board state helpers', () => {
    it('computes sunk ship ids independently from the board component', () => {
        const field = emptyField();
        field[0][0] = {row: 0, col: 0, ship: {shipId: 'sunk', shipSize: 2}, hasShot: true, isAvailable: false};
        field[0][1] = {row: 0, col: 1, ship: {shipId: 'sunk', shipSize: 2}, hasShot: true, isAvailable: false};
        field[1][0] = {row: 1, col: 0, ship: {shipId: 'alive', shipSize: 2}, hasShot: true, isAvailable: false};
        field[1][1] = {row: 1, col: 1, ship: {shipId: 'alive', shipSize: 2}, hasShot: false, isAvailable: false};

        expect(computeSunkShipIds(field)).toEqual(new Set(['sunk']));
    });

    it('computes bounded eight-neighbor moat keys and excludes ship cells', () => {
        const field = emptyField();
        field[1][1] = {row: 1, col: 1, ship: {shipId: 'sunk', shipSize: 1}, hasShot: true, isAvailable: false};

        expect(computeMoatCellKeys(field, new Set(['sunk']))).toEqual(new Set([
            '0,0', '0,1', '0,2',
            '1,0', '1,2',
            '2,0', '2,1', '2,2',
        ]));
    });

    it('returns no moat cells when no ship was newly sunk', () => {
        expect(computeMoatCellKeys(emptyField(), new Set())).toEqual(new Set());
    });
});
