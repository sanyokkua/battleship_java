import { createStorageGateway } from '../preferences/storageGateway';
import type { GameId } from './gameStore';

const key = 'battleship.currentGameId';
const storage = createStorageGateway();

export function readCurrentGame(): GameId | undefined {
    const result = storage.read(key);
    return result.ok && result.value ? result.value : undefined;
}

export function rememberCurrentGame(gameId: GameId | undefined): void {
    if (gameId === undefined) storage.remove(key);
    else storage.write(key, gameId);
}
