import { Card } from './Card';
import './StatCard.css';

export function StatCard({ title, rows }: { title: string; rows: readonly { label: string; value: string }[] }) {
    return (
        <Card className="stat-card" role="region" aria-label={title}>
            <h2>{title}</h2>
            <dl className="stat-list">
                {rows.map(({ label, value }) => (
                    <div className="stat-row" key={label}>
                        <dt>{label}</dt>
                        <dd>{value}</dd>
                    </div>
                ))}
            </dl>
        </Card>
    );
}
