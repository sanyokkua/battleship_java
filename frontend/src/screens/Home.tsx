import './HomeCreate.css';
import { useState } from 'react';
import { Button } from '../components/Button';
import { useT } from '../i18n/useT';
import { navigate } from '../app/router';
import { readCurrentGame } from '../session/currentGame';

export function Home({ onRules }: Readonly<{ onRules: () => void }>) {
    const t = useT();
    const [rememberedGame] = useState(readCurrentGame);
    return (
        <section className="home-screen">
            <div className="home-card">
                <svg className="home-illustration" viewBox="0 0 300 170" aria-hidden="true" focusable="false">
                    <circle className="sun" cx="150" cy="69" r="53" />
                    <path className="wave-back" d="M0 118Q25 101 50 118t50 0t50 0t50 0t50 0t50 0v52H0z" />
                    <path className="ship-hull" d="M55 104h191l-22 25q-73 10-142 0z" strokeWidth="2" />
                    <path className="ship-top" d="M94 104V90h23V76h29V62h36v14h18v14h19v14z" />
                    <path d="M164 62V35m-12 12h24" fill="none" stroke="#092431" strokeWidth="4" />
                    <path className="wave-mid" d="M0 135Q30 116 60 135t60 0t60 0t60 0t60 0v35H0z" />
                    <path className="wave-front" d="M0 152Q25 137 50 152t50 0t50 0t50 0t50 0t50 0v18H0z" />
                    <path className="foam" d="M0 135Q30 116 60 135t60 0t60 0t60 0t60 0" strokeWidth="2" />
                </svg>
                <h1>{t('app.homeTitle')}</h1>
                {rememberedGame && (
                    <Button
                        onClick={() => {
                            navigate(`/games/${encodeURIComponent(rememberedGame)}`);
                        }}
                    >
                        {t('home.returnToGame')}
                    </Button>
                )}
                <Button
                    variant="primary"
                    onClick={() => {
                        navigate('/create');
                    }}
                >
                    {t('app.newGame')}
                </Button>
                <Button onClick={onRules}>{t('home.rules')}</Button>
            </div>
        </section>
    );
}
