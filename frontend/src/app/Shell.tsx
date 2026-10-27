import type { ReactNode } from 'react';
import { Header } from './Header';

interface Props {
    children: ReactNode;
    title?: string;
    refresh?: ReactNode;
    onSettings: () => void;
    onRules: () => void;
}

export function Shell({ children, title, refresh, onSettings, onRules }: Props) {
    return (
        <div className="app-frame">
            <Header
                {...(title === undefined ? {} : { title })}
                {...(refresh === undefined ? {} : { refresh })}
                onSettings={onSettings}
                onRules={onRules}
            />
            <main className="app-content">{children}</main>
        </div>
    );
}
