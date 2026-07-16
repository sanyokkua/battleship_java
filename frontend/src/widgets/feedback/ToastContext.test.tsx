import {act, renderHook} from '@testing-library/react';
import {ToastProvider, useToastContext} from './ToastContext';

describe('ToastContext', () => {
    it('throws when used outside a ToastProvider', () => {
        const {result} = renderHook(() => {
            try {
                return useToastContext();
            } catch (e) {
                return e as Error;
            }
        });
        expect(result.current).toBeInstanceOf(Error);
        expect((result.current as Error).message).toMatch(/ToastProvider/);
    });

    it('starts with an empty toast queue', () => {
        const {result} = renderHook(() => useToastContext(), {wrapper: ToastProvider});
        expect(result.current.toasts).toEqual([]);
    });

    it('push adds a toast with a generated id', () => {
        const {result} = renderHook(() => useToastContext(), {wrapper: ToastProvider});

        act(() => {
            result.current.push({variant: 'ok', title: 'Title', message: 'Message'});
        });

        expect(result.current.toasts).toHaveLength(1);
        expect(result.current.toasts[0]).toMatchObject({variant: 'ok', title: 'Title', message: 'Message'});
        expect(result.current.toasts[0].id).toBeDefined();
    });

    it('push appends multiple toasts with distinct ids in order', () => {
        const {result} = renderHook(() => useToastContext(), {wrapper: ToastProvider});

        act(() => {
            result.current.push({variant: 'ok', title: 'First', message: 'A'});
        });
        act(() => {
            result.current.push({variant: 'err', title: 'Second', message: 'B'});
        });

        expect(result.current.toasts).toHaveLength(2);
        expect(result.current.toasts[0].title).toBe('First');
        expect(result.current.toasts[1].title).toBe('Second');
        expect(result.current.toasts[0].id).not.toBe(result.current.toasts[1].id);
    });

    it('dismiss removes only the matching toast', () => {
        const {result} = renderHook(() => useToastContext(), {wrapper: ToastProvider});

        act(() => {
            result.current.push({variant: 'ok', title: 'First', message: 'A'});
        });
        act(() => {
            result.current.push({variant: 'err', title: 'Second', message: 'B'});
        });

        const idToRemove = result.current.toasts[0].id;

        act(() => {
            result.current.dismiss(idToRemove);
        });

        expect(result.current.toasts).toHaveLength(1);
        expect(result.current.toasts[0].title).toBe('Second');
    });

    it('dismiss with an unknown id is a no-op', () => {
        const {result} = renderHook(() => useToastContext(), {wrapper: ToastProvider});

        act(() => {
            result.current.push({variant: 'ok', title: 'First', message: 'A'});
        });
        act(() => {
            result.current.dismiss('does-not-exist');
        });

        expect(result.current.toasts).toHaveLength(1);
    });

    it('push evicts the oldest toast once more than MAX_TOASTS are live', () => {
        const {result} = renderHook(() => useToastContext(), {wrapper: ToastProvider});

        act(() => {
            result.current.push({variant: 'ok', title: 'First', message: 'A'});
        });
        act(() => {
            result.current.push({variant: 'ok', title: 'Second', message: 'B'});
        });
        act(() => {
            result.current.push({variant: 'ok', title: 'Third', message: 'C'});
        });
        act(() => {
            result.current.push({variant: 'ok', title: 'Fourth', message: 'D'});
        });

        expect(result.current.toasts).toHaveLength(3);
        expect(result.current.toasts.map((t) => t.title)).toEqual(['Second', 'Third', 'Fourth']);
    });
});
