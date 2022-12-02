import {ResponseCreatedPlayerDto} from "../../../logic/ApplicationTypes";

export type GameCreatedOrJoinedResult = {
    sessionId: string,
    player: ResponseCreatedPlayerDto
}