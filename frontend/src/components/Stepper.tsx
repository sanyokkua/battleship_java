import './Stepper.css';
import type { MessageKey } from '../i18n/translate';
import { useT } from '../i18n/useT';

const steps = ['Created', 'Waiting', 'Placement', 'Battle'] as const;
export type WaitingStep = (typeof steps)[number];

export function Stepper({ current }: Readonly<{ current: WaitingStep }>) {
    const t = useT();
    const keys: Record<WaitingStep, MessageKey> = {
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
