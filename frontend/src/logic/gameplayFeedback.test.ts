import {describe, expect, it} from 'vitest';
import type {CellDto, ResponseGameplayStateDto} from './ApplicationTypes';
import {classifyGameplayFeedback} from './gameplayFeedback';

function emptyField(rows = 3, columns = 4): CellDto[][] {
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

function baseState(): ResponseGameplayStateDto {
    return {
        playerName: 'Player',
        isPlayerActive: true,
        isPlayerWinner: false,
        playerNumberOfAliveCells: 10,
        playerNumberOfAliveShips: 5,
        playerField: emptyField(),
        opponentName: 'Opponent',
        isOpponentReady: true,
        opponentNumberOfAliveCells: 10,
        opponentNumberOfAliveShips: 5,
        opponentField: emptyField(),
        hasWinner: false,
        winnerPlayerName: '',
    };
}

function cloneState(state: ResponseGameplayStateDto): ResponseGameplayStateDto {
    return JSON.parse(JSON.stringify(state)) as ResponseGameplayStateDto;
}

describe('classifyGameplayFeedback', () => {
    it('seeds the baseline without emitting events for a null previous state', () => {
        const current = baseState();
        current.playerField[0][0].hasShot = true;

        expect(classifyGameplayFeedback(null, current)).toEqual([]);
    });

    it('ignores metadata and unrelated cell availability in equivalent snapshots', () => {
        const previous = baseState();
        const current = cloneState(previous);
        current.playerName = 'Renamed player';
        current.isPlayerActive = false;
        current.isPlayerWinner = true;
        current.playerNumberOfAliveCells = 2;
        current.opponentNumberOfAliveCells = 0;
        current.isOpponentReady = false;
        current.winnerPlayerName = 'Renamed winner';
        current.playerField[0][0].isAvailable = false;

        expect(classifyGameplayFeedback(previous, current)).toEqual([]);
    });

    it('classifies newly shot fleet water, live ship, and sunk ship cells', () => {
        const previous = baseState();
        previous.playerField[0][1].ship = {shipId: 'live', shipSize: 2};
        previous.playerField[0][2].ship = {shipId: 'live', shipSize: 2};
        previous.playerField[2][2].ship = {shipId: 'sunk', shipSize: 1};
        const current = cloneState(previous);
        current.playerField[0][0].hasShot = true;
        current.playerField[0][1].hasShot = true;
        current.playerField[2][2].hasShot = true;

        expect(classifyGameplayFeedback(previous, current)).toEqual([
            {outcome: 'MISS', board: 'player', cellKey: '0,0'},
            {outcome: 'HIT', board: 'player', cellKey: '0,1'},
            {outcome: 'DESTROYED', board: 'player', cellKey: '2,2'},
        ]);
    });

    it('suppresses moat cells revealed by a newly destroyed fleet ship', () => {
        const previous = baseState();
        previous.playerField[1][1].ship = {shipId: 'sunk', shipSize: 1};
        const current = cloneState(previous);
        current.playerField[1][1].hasShot = true;
        for (const [row, col] of [[0, 0], [0, 1], [0, 2], [1, 0], [1, 2], [2, 0], [2, 1], [2, 2]]) {
            current.playerField[row][col].hasShot = true;
        }

        expect(classifyGameplayFeedback(previous, current)).toEqual([
            {outcome: 'DESTROYED', board: 'player', cellKey: '1,1'},
        ]);
    });

    it('suppresses ambiguous adjacent water while preserving distinguishable same-update shots', () => {
        const previous = baseState();
        previous.playerField[1][1].ship = {shipId: 'sunk', shipSize: 1};
        const current = cloneState(previous);
        current.playerField[1][1].hasShot = true;
        for (const [row, col] of [[0, 0], [0, 1], [0, 2], [1, 0], [1, 2], [2, 0], [2, 1], [2, 2]]) {
            current.playerField[row][col].hasShot = true;
        }
        current.playerField[0][3].hasShot = true;
        current.opponentField[2][3].hasShot = true;

        expect(classifyGameplayFeedback(previous, current)).toEqual([
            {outcome: 'MISS', board: 'player', cellKey: '0,3'},
            {outcome: 'DESTROYED', board: 'player', cellKey: '1,1'},
            {outcome: 'MISS', board: 'opponent', cellKey: '2,3'},
        ]);
    });

    it('treats a later adjacent water shot as a MISS after the destruction transition', () => {
        const previous = baseState();
        previous.playerField[1][1].ship = {shipId: 'sunk', shipSize: 1};
        previous.playerField[1][1].hasShot = true;
        const current = cloneState(previous);
        current.playerField[0][1].hasShot = true;

        expect(classifyGameplayFeedback(previous, current)).toEqual([
            {outcome: 'MISS', board: 'player', cellKey: '0,1'},
        ]);
    });

    it('classifies target water, visible hits, and target destruction', () => {
        const previous = baseState();
        previous.opponentField[0][0].ship = null;
        previous.opponentField[0][1].ship = null;
        const current = cloneState(previous);
        current.opponentField[0][0].hasShot = true;
        current.opponentField[0][1].ship = {shipId: 'target', shipSize: 2};
        current.opponentField[0][1].hasShot = true;

        const partial = classifyGameplayFeedback(previous, current);
        expect(partial).toEqual([
            {outcome: 'MISS', board: 'opponent', cellKey: '0,0'},
            {outcome: 'HIT', board: 'opponent', cellKey: '0,1'},
        ]);

        const destroyed = cloneState(current);
        destroyed.opponentNumberOfAliveShips = 4;
        expect(classifyGameplayFeedback(current, destroyed)).toEqual([]);
    });

    it('classifies a target ship as destroyed only when alive-ship count decreases', () => {
        const previous = baseState();
        const current = cloneState(previous);
        current.opponentField[1][2].ship = {shipId: 'target', shipSize: 2};
        current.opponentField[1][2].hasShot = true;
        current.opponentNumberOfAliveShips = 4;

        expect(classifyGameplayFeedback(previous, current)).toEqual([
            {outcome: 'DESTROYED', board: 'opponent', cellKey: '1,2'},
        ]);
    });

    it('suppresses target-board moat cells revealed by a destroyed ship', () => {
        const previous = baseState();
        previous.opponentField[1][1].ship = {shipId: 'target', shipSize: 2};
        previous.opponentField[1][1].hasShot = true;
        const current = cloneState(previous);
        current.opponentField[1][2].ship = {shipId: 'target', shipSize: 2};
        current.opponentField[1][2].hasShot = true;
        current.opponentNumberOfAliveShips = 4;
        for (const [row, col] of [[0, 0], [0, 1], [0, 2], [0, 3], [1, 0], [2, 0], [2, 1], [2, 2], [2, 3]]) {
            current.opponentField[row][col].hasShot = true;
        }

        expect(classifyGameplayFeedback(previous, current)).toEqual([
            {outcome: 'DESTROYED', board: 'opponent', cellKey: '1,2'},
        ]);
    });

    it('ignores target cells revealed without a shot, including final-state visibility changes', () => {
        const previous = baseState();
        const current = cloneState(previous);
        current.isPlayerWinner = true;
        current.opponentField[2][3].ship = {shipId: 'revealed', shipSize: 3};

        expect(classifyGameplayFeedback(previous, current)).toEqual([]);
    });

    it('emits each batched real shot in player then opponent row-major order', () => {
        const previous = baseState();
        const current = cloneState(previous);
        current.playerField[2][3].hasShot = true;
        current.playerField[0][2].hasShot = true;
        current.opponentField[2][0].hasShot = true;
        current.opponentField[0][1].hasShot = true;

        expect(classifyGameplayFeedback(previous, current)).toEqual([
            {outcome: 'MISS', board: 'player', cellKey: '0,2'},
            {outcome: 'MISS', board: 'player', cellKey: '2,3'},
            {outcome: 'MISS', board: 'opponent', cellKey: '0,1'},
            {outcome: 'MISS', board: 'opponent', cellKey: '2,0'},
        ]);
    });

    it('returns no events for duplicate or refetched snapshots', () => {
        const previous = baseState();
        const current = cloneState(previous);
        current.playerField[1][1].hasShot = true;
        const events = classifyGameplayFeedback(previous, current);

        expect(events).toEqual([{outcome: 'MISS', board: 'player', cellKey: '1,1'}]);
        expect(classifyGameplayFeedback(current, cloneState(current))).toEqual([]);
    });

    it('uses the first row-major target ship candidate as destroyed in a malformed batch', () => {
        const previous = baseState();
        const current = cloneState(previous);
        current.opponentField[2][0].ship = {shipId: 'later', shipSize: 2};
        current.opponentField[2][0].hasShot = true;
        current.opponentField[0][3].ship = {shipId: 'first', shipSize: 1};
        current.opponentField[0][3].hasShot = true;
        current.opponentNumberOfAliveShips = 4;

        expect(classifyGameplayFeedback(previous, current)).toEqual([
            {outcome: 'DESTROYED', board: 'opponent', cellKey: '0,3'},
            {outcome: 'HIT', board: 'opponent', cellKey: '2,0'},
        ]);
    });
});
