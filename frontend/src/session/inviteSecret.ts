let invitationSecret: string | undefined;

export function stripInvitationSecret(): void {
    if (!window.location.pathname.startsWith('/join/') || !window.location.hash.startsWith('#invite=')) return;
    invitationSecret = window.location.hash.slice('#invite='.length);
    window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}`);
}

export function readInvitationSecret(): string | undefined {
    return invitationSecret;
}

export function clearInvitationSecret(): void {
    invitationSecret = undefined;
}
