import type { components } from '../contract/generated/api';

export type GameSnapshot = components['schemas']['GameSnapshot'];

/** Applies the contract's caller-relative monotonic version rule. */
export const snapshotGate = {
    accept(current: GameSnapshot | undefined, next: GameSnapshot): GameSnapshot {
        if (current === undefined || next.version > current.version) return next;
        if (next.version < current.version) return current;

        const merged: GameSnapshot = { ...current, expiresAt: next.expiresAt, serverTime: next.serverTime };
        if (next.invitationUrl === undefined) delete merged.invitationUrl;
        else merged.invitationUrl = next.invitationUrl;
        if (next.invitationExpiresAt === undefined) delete merged.invitationExpiresAt;
        else merged.invitationExpiresAt = next.invitationExpiresAt;
        return merged;
    },
};
