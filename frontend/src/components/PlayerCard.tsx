import type { components } from '../contract/generated/api';
import { useT } from '../i18n/useT';
import './PlayerCard.css';

export function PlayerCard({
    player,
    board,
    fleet,
    opponent = false,
}: Readonly<{
    player: components['schemas']['Player'];
    board: components['schemas']['Board'];
    fleet: components['schemas']['Ruleset']['fleet'];
    opponent?: boolean;
}>) {
    const t = useT();
    const totalShips = fleet.reduce((sum, ship) => sum + ship.count, 0);
    const totalCells = fleet.reduce((sum, ship) => sum + ship.length * ship.count, 0);
    const cellsLeft = totalCells - board.grid.flat().filter((cell) => cell === 'HIT' || cell === 'SUNK').length;
    return (
        <section
            className={`match-player${opponent ? ' opponent' : ''}`}
            aria-label={`${player.displayName} ${t('play.fleet')}`}
        >
            <div className="match-player-head">
                <strong>{player.displayName}</strong>
            </div>
            <div className="player-metrics">
                <div className="metric-line" role="group" aria-label={t('play.shipsLeft')}>
                    <span>{t('play.shipsLeft')}</span>
                    <progress
                        className="health"
                        aria-label={t('play.shipsLeft')}
                        max={totalShips}
                        value={player.shipsRemaining}
                    />
                    <b>
                        {player.shipsRemaining}/{totalShips}
                    </b>
                </div>
                <div className="metric-line" role="group" aria-label={t('play.cellsLeft')}>
                    <span>{t('play.cellsLeft')}</span>
                    <progress
                        className="health cells"
                        aria-label={t('play.cellsLeft')}
                        max={totalCells}
                        value={cellsLeft}
                    />
                    <b>
                        {cellsLeft}/{totalCells}
                    </b>
                </div>
            </div>
        </section>
    );
}
