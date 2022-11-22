import {PlayerDto} from "../../../logic/GameTypes";

export type GameCreatedOrJoinedResult = {
    sessionId: string,
    player: PlayerDto
}