import { Component, type ReactNode } from 'react';
import { Button } from '../components/Button';
import { useT } from '../i18n/useT';
import '../screens/StatusScreen.css';

interface Props {
    children: ReactNode;
    onHome?: () => void;
    onRefresh?: () => void;
}

function Fallback({ onHome, onRefresh }: Readonly<{ onHome: () => void; onRefresh: () => void }>) {
    const t = useT();
    return (
        <main className="app-content">
            <section className="home-screen">
                <div className="status-screen-content" role="alert">
                    <span className="status-badge">{t('error.title')}</span>
                    <h1>{t('error.body')}</h1>
                    <Button variant="primary" onClick={onRefresh}>
                        {t('game.refresh')}
                    </Button>
                    <Button onClick={onHome}>{t('app.returnHome')}</Button>
                </div>
            </section>
        </main>
    );
}

/** Top-level boundary: an unexpected render failure shows a recovery screen instead of a blank page. */
export class ErrorBoundary extends Component<Props, { failed: boolean }> {
    override state = { failed: false };

    static getDerivedStateFromError() {
        return { failed: true };
    }

    override render() {
        if (!this.state.failed) return this.props.children;
        return (
            <Fallback
                onHome={
                    this.props.onHome ??
                    (() => {
                        globalThis.location.assign('/');
                    })
                }
                onRefresh={
                    this.props.onRefresh ??
                    (() => {
                        globalThis.location.reload();
                    })
                }
            />
        );
    }
}
