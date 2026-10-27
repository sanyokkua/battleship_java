import type { ReactNode } from 'react';
import { Header } from './Header';

interface Props {
    children: ReactNode;
    title?: string;
    refresh?: ReactNode;
    onSettings: () => void;
}

export function Shell({ children, title, refresh, onSettings }: Props) {
    return (
        <div className="app-frame">
            <Header
                {...(title === undefined ? {} : { title })}
                {...(refresh === undefined ? {} : { refresh })}
                onSettings={onSettings}
            />
            <main className="app-content">{children}</main>
        </div>
    );
}
