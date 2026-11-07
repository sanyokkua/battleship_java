import './Pill.css';

export function Pill({
    children,
    tone = 'neutral',
    role,
}: Readonly<{
    children: string;
    tone?: 'neutral' | 'good' | 'warning';
    role?: 'status';
}>) {
    return (
        <span className={`pill pill-${tone}`} role={role}>
            {children}
        </span>
    );
}
