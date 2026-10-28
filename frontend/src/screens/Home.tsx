import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { useT } from '../i18n/useT';
import { navigate } from '../app/router';

export function Home({ onRules }: { onRules: () => void }) {
    const t = useT();
    return (
        <section className="home-screen">
            <Card className="card-pad home-card">
                <span className="eyebrow">{t('app.name')}</span>
                <h1>{t('app.homeTitle')}</h1>
                <Button
                    variant="primary"
                    onClick={() => {
                        navigate('/create');
                    }}
                >
                    {t('app.newGame')}
                </Button>
                <Button onClick={onRules}>{t('home.rules')}</Button>
            </Card>
        </section>
    );
}
