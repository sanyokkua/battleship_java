import type {CellDto, ResponseGameplayStateDto} from './ApplicationTypes';
import {computeMoatCellKeys, computeSunkShipIds} from './boardState';

export type BattleOutcome = 'MISS' | 'HIT' | 'DESTROYED';

export type GameplayFeedbackEvent = {
    outcome: BattleOutcome;
    board: 'player' | 'opponent';
    cellKey: string;
};

type FeedbackCell = Pick<CellDto, 'row' | 'col' | 'hasShot' | 'ship'>;

function projectField(field: CellDto[][]): FeedbackCell[][] {
    return field.map(row => row.map(cell => ({
        row: cell.row,
        col: cell.col,
        hasShot: cell.hasShot,
        ship: cell.ship == null ? null : {
            shipId: cell.ship.shipId,
            shipSize: cell.ship.shipSize,
        },
    })));
}

function feedbackProjection(state: ResponseGameplayStateDto): string {
    return JSON.stringify({
        playerField: projectField(state.playerField),
        opponentField: projectField(state.opponentField),
        playerNumberOfAliveShips: state.playerNumberOfAliveShips,
        opponentNumberOfAliveShips: state.opponentNumberOfAliveShips,
    });
}

function isNewShot(previous: CellDto | undefined, current: CellDto): boolean {
    return previous?.hasShot === false && current.hasShot === true;
}

function cellKey(cell: CellDto): string {
    return `${cell.row},${cell.col}`;
}

function classifyPlayerField(
    previousField: CellDto[][],
    currentField: CellDto[][],
): GameplayFeedbackEvent[] {
    const previousSunkShipIds = computeSunkShipIds(previousField);
    const currentSunkShipIds = computeSunkShipIds(currentField);
    const newlySunkShipIds = new Set(
        [...currentSunkShipIds].filter(shipId => !previousSunkShipIds.has(shipId)),
    );
    // The unchanged DTO has no shot-origin marker, so water adjacent to a newly
    // sunk ship is intentionally treated as automatic moat rather than a MISS.
    const moatCellKeys = computeMoatCellKeys(currentField, newlySunkShipIds);
    const events: GameplayFeedbackEvent[] = [];

    for (let row = 0; row < currentField.length; row++) {
        for (let col = 0; col < currentField[row].length; col++) {
            const currentCell = currentField[row][col];
            const previousCell = previousField[row]?.[col];
            if (!isNewShot(previousCell, currentCell)) continue;

            const key = cellKey(currentCell);
            if (currentCell.ship == null && moatCellKeys.has(key)) continue;

            const outcome: BattleOutcome = currentCell.ship == null
                ? 'MISS'
                : newlySunkShipIds.has(currentCell.ship.shipId) ? 'DESTROYED' : 'HIT';
            events.push({outcome, board: 'player', cellKey: key});
        }
    }
    return events;
}

function classifyOpponentField(
    previousField: CellDto[][],
    currentField: CellDto[][],
    previousAliveShips: number,
    currentAliveShips: number,
): GameplayFeedbackEvent[] {
    const candidates: Array<{ cell: CellDto; isShip: boolean }> = [];
    for (let row = 0; row < currentField.length; row++) {
        for (let col = 0; col < currentField[row].length; col++) {
            const currentCell = currentField[row][col];
            const previousCell = previousField[row]?.[col];
            if (!isNewShot(previousCell, currentCell)) continue;
            candidates.push({cell: currentCell, isShip: currentCell.ship != null});
        }
    }

    const destroyedCandidate = currentAliveShips < previousAliveShips
        ? candidates.find(candidate => candidate.isShip)
        : undefined;
    // A ship-count drop identifies destruction, but not which same-update water
    // cells were real shots versus automatic moat reveals.
    const destroyedMoatCellKeys = destroyedCandidate?.cell.ship
        ? computeMoatCellKeys(currentField, new Set([destroyedCandidate.cell.ship.shipId]))
        : new Set<string>();

    return candidates
        .filter(candidate => candidate.isShip || !destroyedMoatCellKeys.has(cellKey(candidate.cell)))
        .map(candidate => ({
            outcome: candidate.isShip
                ? candidate === destroyedCandidate ? 'DESTROYED' : 'HIT'
                : 'MISS',
            board: 'opponent' as const,
            cellKey: cellKey(candidate.cell),
        }));
}

/** Classifies only genuinely new shot outcomes in a full gameplay snapshot. */
export function classifyGameplayFeedback(
    previous: ResponseGameplayStateDto | null,
    current: ResponseGameplayStateDto,
): GameplayFeedbackEvent[] {
    if (previous == null || feedbackProjection(previous) === feedbackProjection(current)) {
        return [];
    }

    return [
        ...classifyPlayerField(previous.playerField, current.playerField),
        ...classifyOpponentField(
            previous.opponentField,
            current.opponentField,
            previous.opponentNumberOfAliveShips,
            current.opponentNumberOfAliveShips,
        ),
    ];
}
