import type { components } from '../contract/generated/api';
import type { MessageKey } from '../i18n/translate';
import { TransportFailure } from './decode';

export type ProblemCode = components['schemas']['ProblemCode'];
export interface GatewayProblem {
    status: number;
    code: string;
    messageKey: MessageKey;
    retryAfterSeconds?: number;
}

const messages: Record<ProblemCode, MessageKey> = {
    'malformed-request': 'problem.malformed',
    'session-required': 'problem.sessionRequired',
    'request-security-rejected': 'problem.securityRejected',
    'game-unavailable': 'problem.gameUnavailable',
    'invitation-unavailable': 'problem.invitationUnavailable',
    'browser-game-limit': 'problem.browserGameLimit',
    'action-not-allowed': 'problem.actionNotAllowed',
    'placement-out-of-bounds': 'problem.placementInvalid',
    'placement-overlap': 'problem.placementInvalid',
    'placement-touching': 'problem.placementTouching',
    'random-arrangement-failed': 'problem.placementInvalid',
    'target-already-fired': 'problem.actionNotAllowed',
    'game-expired': 'problem.gameExpired',
    'payload-too-large': 'problem.malformed',
    'unsupported-media-type': 'problem.malformed',
    'validation-failed': 'problem.validationFailed',
    'rate-limit-exceeded': 'problem.rateLimited',
    'internal-error': 'problem.serverError',
    'service-unavailable': 'problem.serviceUnavailable',
};

export function problemMessageKey(code: string, status: number): MessageKey {
    if (Object.hasOwn(messages, code)) return messages[code as ProblemCode];
    if (status === 401) return 'problem.sessionRequired';
    if (status === 403) return 'problem.securityRejected';
    if (status === 404) return 'problem.gameUnavailable';
    if (status === 410) return 'problem.gameExpired';
    if (status === 422) return 'problem.validationFailed';
    if (status === 429) return 'problem.rateLimited';
    if (status === 503) return 'problem.serviceUnavailable';
    if (status >= 500) return 'problem.serverError';
    return 'problem.malformed';
}

export function decodeProblem(status: number, body: string, contentType: string): GatewayProblem {
    const mediaType = contentType.split(';', 1)[0]?.trim().toLowerCase();
    if (mediaType !== 'application/problem+json') throw new TransportFailure();

    let parsed: unknown;
    try {
        parsed = JSON.parse(body);
    } catch {
        throw new TransportFailure();
    }

    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) throw new TransportFailure();
    const value = parsed as Record<string, unknown>;
    const code = value.code;
    if (
        typeof value.title !== 'string' ||
        typeof value.status !== 'number' ||
        value.status !== status ||
        typeof code !== 'string' ||
        code.length === 0
    ) {
        throw new TransportFailure();
    }
    const retryAfterSeconds = value.retryAfterSeconds;
    if (
        retryAfterSeconds !== undefined &&
        (typeof retryAfterSeconds !== 'number' || !Number.isInteger(retryAfterSeconds) || retryAfterSeconds < 1)
    ) {
        throw new TransportFailure();
    }
    return {
        status,
        code,
        messageKey: problemMessageKey(code, status),
        ...(typeof retryAfterSeconds === 'number' ? { retryAfterSeconds } : {}),
    };
}
