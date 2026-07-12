import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import '../i18n';
import { MockGameAdapter } from '../adapters/MockGameAdapter';
import { GameAdapterProvider } from '../adapters/GameAdapterContext';
import { ToastProvider } from '../widgets/feedback/ToastContext';
import { ToastStack } from '../widgets/feedback/ToastStack';
import { JoinGameScreen } from './JoinGameScreen';

function renderJoinScreen(adapter: MockGameAdapter) {
  return render(
    <GameAdapterProvider adapter={adapter}>
      <ToastProvider>
        <ToastStack />
        <MemoryRouter initialEntries={['/join']}>
          <Routes>
            <Route path="/join" element={<JoinGameScreen />} />
            <Route path="/game/wait" element={<div>Wait route</div>} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </GameAdapterProvider>,
  );
}

describe('JoinGameScreen', () => {
  it('shows a validation error when the name is too short', async () => {
    const user = userEvent.setup();
    renderJoinScreen(new MockGameAdapter());

    const nameInput = screen.getByLabelText('Player name');
    await user.type(nameInput, 'A');
    await user.tab();

    expect(await screen.findByText('Name must be at least 2 characters.')).toBeInTheDocument();
  });

  it('does not show the green checkmark for a malformed Game ID', async () => {
    const user = userEvent.setup();
    renderJoinScreen(new MockGameAdapter());

    const idInput = screen.getByLabelText('Game ID');
    await user.type(idInput, 'not-a-uuid');
    await user.tab();

    expect(screen.queryByText('Valid game code')).not.toBeInTheDocument();
  });

  it('shows the green checkmark for a well-formed UUID (format-only check)', async () => {
    const user = userEvent.setup();
    renderJoinScreen(new MockGameAdapter());

    const idInput = screen.getByLabelText('Game ID');
    await user.type(idInput, '8f4c23c4-f86e-465c-a208-65f70281bfcb');

    expect(await screen.findByText('Valid game code')).toBeInTheDocument();
  });

  it('disables submit while either field is invalid', async () => {
    const user = userEvent.setup();
    renderJoinScreen(new MockGameAdapter());

    const submitButton = screen.getByRole('button', { name: 'Join the battle' });
    expect(submitButton).toBeDisabled();

    await user.type(screen.getByLabelText('Player name'), 'Batman');
    expect(submitButton).toBeDisabled();

    await user.type(screen.getByLabelText('Game ID'), '8f4c23c4-f86e-465c-a208-65f70281bfcb');
    expect(submitButton).toBeEnabled();
  });

  it('joins an existing session and navigates to /game/wait on success', async () => {
    // MockGameAdapter.createSession() returns ids like "session-1" (not UUID-shaped),
    // but the screen's own Game ID field only accepts 36-char UUID format (per spec).
    // Seed a real session/player via the mock, then redirect createPlayer/getStage
    // calls made with the UUID typed into the field to that real, seeded session id
    // — this still exercises the full seeded MockGameAdapter session underneath.
    const user = userEvent.setup();
    const adapter = new MockGameAdapter();
    const realSessionId = await adapter.createSession('UKRAINIAN');
    await adapter.createPlayer(realSessionId, 'Player1');

    const uuidGameId = '8f4c23c4-f86e-465c-a208-65f70281bfcb';
    const realCreatePlayer = adapter.createPlayer.bind(adapter);
    const realGetStage = adapter.getStage.bind(adapter);
    const createPlayerSpy = vi
      .spyOn(adapter, 'createPlayer')
      .mockImplementation((_sessionId, name) => realCreatePlayer(realSessionId, name));
    const getStageSpy = vi.spyOn(adapter, 'getStage').mockImplementation(() => realGetStage(realSessionId));

    renderJoinScreen(adapter);

    await user.type(screen.getByLabelText('Player name'), 'Batman');
    await user.type(screen.getByLabelText('Game ID'), uuidGameId);
    await user.click(screen.getByRole('button', { name: 'Join the battle' }));

    expect(await screen.findByText('Wait route')).toBeInTheDocument();
    expect(createPlayerSpy).toHaveBeenCalledWith(uuidGameId, 'Batman');
    expect(getStageSpy).toHaveBeenCalledWith(uuidGameId);
  });

  it('surfaces an error toast and does not navigate for a well-formed but unknown session id', async () => {
    const user = userEvent.setup();
    renderJoinScreen(new MockGameAdapter());

    await user.type(screen.getByLabelText('Player name'), 'Batman');
    await user.type(screen.getByLabelText('Game ID'), '8f4c23c4-f86e-465c-a208-65f70281bfcb');
    await user.click(screen.getByRole('button', { name: 'Join the battle' }));

    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
    expect(screen.queryByText('Wait route')).not.toBeInTheDocument();
  });
});
