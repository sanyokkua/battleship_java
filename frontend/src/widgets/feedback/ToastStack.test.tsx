import {act, render, screen} from '@testing-library/react';
import '../../i18n';
import type {ToastData} from './ToastContext';
import {ToastProvider, useToastContext} from './ToastContext';
import {ToastStack} from './ToastStack';

function Harness({onReady}: { onReady: (push: (t: Omit<ToastData, 'id'>) => void) => void }) {
    const {push} = useToastContext();
    onReady(push);
    return <ToastStack/>;
}

function renderStack() {
    let push!: (t: Omit<ToastData, 'id'>) => void;
    render(
        <ToastProvider>
            <Harness
                onReady={(p) => {
                    push = p;
                }}
            />
        </ToastProvider>,
    );
    return {push: (t: Omit<ToastData, 'id'>) => act(() => push(t))};
}

describe('ToastStack', () => {
    beforeEach(() => {
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('renders multiple queued toasts', () => {
        const {push} = renderStack();

        push({variant: 'ok', title: 'First', message: 'A'});
        push({variant: 'info', title: 'Second', message: 'B'});

        expect(screen.getByText('First')).toBeInTheDocument();
        expect(screen.getByText('Second')).toBeInTheDocument();
    });

    it('gives err-variant toasts role="alert" and others role="status"', () => {
        const {push} = renderStack();

        push({variant: 'err', title: 'Bad', message: 'Something broke'});
        push({variant: 'ok', title: 'Good', message: 'All fine'});

        expect(screen.getByRole('alert')).toHaveTextContent('Bad');
        expect(screen.getByRole('status')).toHaveTextContent('Good');
    });

    it('auto-dismisses a toast after the timeout', () => {
        const {push} = renderStack();

        push({variant: 'ok', title: 'Ephemeral', message: 'Bye soon'});
        expect(screen.getByText('Ephemeral')).toBeInTheDocument();

        act(() => {
            vi.advanceTimersByTime(4000);
        });

        expect(screen.queryByText('Ephemeral')).not.toBeInTheDocument();
    });

    it('caps the rendered stack at 3, dropping the oldest toast first', () => {
        const {push} = renderStack();

        push({variant: 'ok', title: 'First', message: 'A'});
        push({variant: 'ok', title: 'Second', message: 'B'});
        push({variant: 'ok', title: 'Third', message: 'C'});
        push({variant: 'ok', title: 'Fourth', message: 'D'});

        expect(screen.queryByText('First')).not.toBeInTheDocument();
        expect(screen.getByText('Second')).toBeInTheDocument();
        expect(screen.getByText('Third')).toBeInTheDocument();
        expect(screen.getByText('Fourth')).toBeInTheDocument();
    });

    it('does not render a container when there are no toasts', () => {
        const {container} = render(
            <ToastProvider>
                <ToastStack/>
            </ToastProvider>,
        );
        expect(container.querySelector('.toast-stack')).toBeNull();
    });
});
