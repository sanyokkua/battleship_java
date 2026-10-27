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
            <h2 id="rules-title">{t('rules.title')}</h2>
            {rulesets.map((ruleset) => (
                <section key={ruleset.id}>
                    <h3>{t(`ruleset.${ruleset.id}`)}</h3>
                    <RulesetFacts ruleset={ruleset} />
                </section>
            ))}
            <Button onClick={onClose}>{t('app.close')}</Button>
        </Dialog>
    );
}
