import './Stepper.css';
import { useT } from '../i18n/useT';

const steps = ['Created', 'Waiting', 'Placement', 'Battle'] as const;
export type WaitingStep = (typeof steps)[number];

export function Stepper({ current }: { current: WaitingStep }) {
    const t = useT();
    const keys: Record<WaitingStep, string> = {
        Created: 'game.createdStep',
        Waiting: 'game.waitingStep',
        Placement: 'game.placementStep',
        Battle: 'game.battleStep',
    };
    return (
        <ol className="game-stepper" aria-label={t('game.progress')}>
            {steps.map((step) => (
                <li key={step} aria-current={current === step ? 'step' : undefined}>
                    {t(keys[step])}
                </li>
            ))}
        </ol>
    );
}
