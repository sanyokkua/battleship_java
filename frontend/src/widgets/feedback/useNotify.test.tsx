import { renderHook, act } from '@testing-library/react';
import { I18nextProvider } from 'react-i18next';
import type { ReactNode } from 'react';
import i18n from '../../i18n';
import { ToastProvider, useToastContext } from './ToastContext';
import { useNotify } from './useNotify';

function wrapper({ children }: { children: ReactNode }) {
  return (
    <I18nextProvider i18n={i18n}>
      <ToastProvider>{children}</ToastProvider>
    </I18nextProvider>
  );
}

function useCombined() {
  return { notify: useNotify(), toastCtx: useToastContext() };
}

describe('useNotify', () => {
  afterEach(async () => {
    await act(async () => {
      await i18n.changeLanguage('en');
    });
  });

  it('success() pushes an "ok" toast with the localized title/message (en)', () => {
    const { result } = renderHook(() => useCombined(), { wrapper });

    act(() => {
      result.current.notify.success('ship.placed');
    });

    expect(result.current.toastCtx.toasts).toHaveLength(1);
    expect(result.current.toastCtx.toasts[0]).toMatchObject({
      variant: 'ok',
      title: 'Ship placed',
      message: 'Ship positioned on the board.',
    });
  });

  it('info() pushes an "info" toast', () => {
    const { result } = renderHook(() => useCombined(), { wrapper });

    act(() => {
      result.current.notify.info('turn.notYours');
    });

    expect(result.current.toastCtx.toasts[0]).toMatchObject({
      variant: 'info',
      title: 'Not your turn',
      message: 'Wait for your opponent to take their shot.',
    });
  });

  it('warn() pushes a "warn" toast', () => {
    const { result } = renderHook(() => useCombined(), { wrapper });

    act(() => {
      result.current.notify.warn('shot.sunk');
    });

    expect(result.current.toastCtx.toasts[0]).toMatchObject({
      variant: 'warn',
      title: 'Ship sunk!',
      message: 'You sank an enemy ship!',
    });
  });

  it('error() pushes an "err" toast', () => {
    const { result } = renderHook(() => useCombined(), { wrapper });

    act(() => {
      result.current.notify.error('session.notFound');
    });

    expect(result.current.toastCtx.toasts[0]).toMatchObject({
      variant: 'err',
      title: 'Session not found',
      message: 'This game session no longer exists.',
    });
  });

  it('resolves localized text in Ukrainian after switching language', async () => {
    const { result } = renderHook(() => useCombined(), { wrapper });

    await act(async () => {
      await i18n.changeLanguage('uk');
    });

    act(() => {
      result.current.notify.success('ship.placed');
    });

    expect(result.current.toastCtx.toasts[0]).toMatchObject({
      variant: 'ok',
      title: 'Корабель розставлено',
      message: 'Корабель розміщено на полі.',
    });
  });
});
