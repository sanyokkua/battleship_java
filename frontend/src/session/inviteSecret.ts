let invitationSecret: string | undefined;

export function stripInvitationSecret(): void {
    if (!globalThis.location.pathname.startsWith('/join/') || !globalThis.location.hash.startsWith('#invite=')) return;
    invitationSecret = globalThis.location.hash.slice('#invite='.length);
    globalThis.history.replaceState(null, '', `${globalThis.location.pathname}${globalThis.location.search}`);
}

export function readInvitationSecret(): string | undefined {
    return invitationSecret;
}

export function clearInvitationSecret(): void {
    invitationSecret = undefined;
}
