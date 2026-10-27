import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { useT } from '../i18n/useT';
import { navigate } from '../app/router';

export function SessionUnavailable({ expired }: { expired: boolean }) {
    const t = useT();
    return (
        <section className="home-screen">
            <Card className="card-pad">
                <span className="status-badge">{t('session.unavailable')}</span>
                <h1>{expired ? t('session.expired') : t('session.ended')}</h1>
                <Button
                    variant="primary"
                    onClick={() => {
                        navigate('/create');
                    }}
                >
                    {t('session.createNew')}
                </Button>
                <Button
                    onClick={() => {
                        navigate('/');
                    }}
                >
                    {t('app.returnHome')}
                </Button>
            </Card>
        </section>
    );
}
