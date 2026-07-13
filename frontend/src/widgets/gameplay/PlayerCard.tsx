import './PlayerCard.css';
import {Pill} from '../../design/components/Pill/Pill';

export type PlayerCardProps = {
    variant: 'you' | 'foe';
    name: string;
    isActiveTurn?: boolean; // shows a small turn-indicator pill on the foe card per mockup's ▶ badge
    cellsLabel: string;
    cellsValue: number;
    cellsPercent: number; // 0-100
    shipsLabel: string;
    shipsAliveCount: number;
    shipsTotal: number; // always 10 (every edition has exactly 10 ships)
    youLabel?: string; // shown as a badge on the 'you' card
};

/**
 * Mini-bar color: a simple three-tier health color (green ≥60%, gold ≥30%, red
 * below) rather than a single flat color — a reasonable UX addition beyond the
 * mockup's hard-coded per-instance colors, cheap to compute from `cellsPercent`
 * and consistent between the two cards.
 */
function healthColor(percent: number): string {
    if (percent >= 60) return 'var(--ok)';
    if (percent >= 30) return 'var(--gold)';
    return 'var(--hit)';
}

/**
 * Ported from MOCKUP.html's `.player-card.you`/`.foe` (teal/red left border): a
 * `who` row (name + badge/turn-pill) and a stat row with cells + ships mini-bars.
 */
export function PlayerCard({
                               variant,
                               name,
                               isActiveTurn,
                               cellsLabel,
                               cellsValue,
                               cellsPercent,
                               shipsLabel,
                               shipsAliveCount,
                               shipsTotal,
                               youLabel,
                           }: PlayerCardProps) {
    const clampedCellsPercent = Math.max(0, Math.min(100, cellsPercent));
    const shipsPercent = shipsTotal > 0 ? Math.max(0, Math.min(100, (shipsAliveCount / shipsTotal) * 100)) : 0;
    const barColor = healthColor(clampedCellsPercent);

    return (
        <div className={`player-card ${variant}`}>
            <div className="who">
                <b>{name}</b>
                {variant === 'you' && youLabel && <Pill variant="ok">{youLabel}</Pill>}
                {variant === 'foe' && isActiveTurn && <Pill variant="turn">▶</Pill>}
            </div>
            <div className="statrow">
                <div className="s">
                    <div className="lbl">{cellsLabel}</div>
                    <div className="val">{cellsValue}</div>
                    <div className="mini-bar">
                        <i style={{width: `${clampedCellsPercent}%`, background: barColor}}/>
                    </div>
                </div>
                <div className="s">
                    <div className="lbl">{shipsLabel}</div>
                    <div className="val">
                        {shipsAliveCount} / {shipsTotal}
                    </div>
                    <div className="mini-bar">
                        <i style={{width: `${shipsPercent}%`, background: barColor}}/>
                    </div>
                </div>
            </div>
        </div>
    );
}
