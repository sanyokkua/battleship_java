import { afterEach, expect, it } from 'vitest';
import { clearInvitationSecret, readInvitationSecret, stripInvitationSecret } from '../src/session/inviteSecret';

function storageContents(storage: Storage) {
    return Object.keys(storage)
        .sort()
        .map((key) => [key, storage.getItem(key)]);
}

afterEach(() => {
    clearInvitationSecret();
    window.history.replaceState(null, '', '/');
});

it('removes an invitation fragment immediately while preserving the join path and query', () => {
    window.history.replaceState(null, '', '/join/game-123?source=share#invite=private-secret');
    const localBefore = storageContents(window.localStorage);
    const sessionBefore = storageContents(window.sessionStorage);

    stripInvitationSecret();

    expect(window.location.pathname).toBe('/join/game-123');
    expect(window.location.search).toBe('?source=share');
    expect(window.location.hash).toBe('');
    expect(window.location.href).not.toContain('private-secret');
    expect(readInvitationSecret()).toBe('private-secret');
    expect(storageContents(window.localStorage)).toEqual(localBefore);
    expect(storageContents(window.sessionStorage)).toEqual(sessionBefore);

    clearInvitationSecret();
    expect(readInvitationSecret()).toBeUndefined();
});

it('does not consume an invitation-shaped fragment outside the join route', () => {
    window.history.replaceState(null, '', '/create?source=share#invite=private-secret');

    stripInvitationSecret();

    expect(window.location.pathname).toBe('/create');
    expect(window.location.search).toBe('?source=share');
    expect(window.location.hash).toBe('#invite=private-secret');
    expect(readInvitationSecret()).toBeUndefined();
});
