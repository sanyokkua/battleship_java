import { useEffect, useState } from 'react';
import { Button } from '../components/Button';
import { Dialog } from '../components/Dialog';
import { RulesetFacts } from '../components/RulesetFacts';
import { listRulesets, type Ruleset } from '../gateway/gameGateway';
import { useT } from '../i18n/useT';

export function RulesDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
    const t = useT();
    const [rulesets, setRulesets] = useState<Ruleset[]>([]);
    useEffect(() => {
        if (open)
            void listRulesets()
                .then(setRulesets)
                .catch(() => {
                    setRulesets([]);
                });
    }, [open]);
    return (
        <Dialog open={open} titleId="rules-title" onClose={onClose}>
            <div className="dialog-head">
                <h2 id="rules-title">{t('rules.title')}</h2>
            </div>
            <div
                className="dialog-body rules-dialog-body"
                tabIndex={0 /* NOSONAR: keyboard access to the scrollable region */}
                role="region"
                aria-labelledby="rules-title"
            >
                {rulesets.map((ruleset) => (
                    <section className="rules-dialog-card" key={ruleset.id}>
                        <h3>{t(`ruleset.${ruleset.id}`)}</h3>
                        <RulesetFacts ruleset={ruleset} />
                    </section>
                ))}
            </div>
            <div className="dialog-actions">
                <Button onClick={onClose}>{t('app.close')}</Button>
            </div>
        </Dialog>
    );
}
