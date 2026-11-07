import { useT, type TranslationKey } from '../i18n/useT';

/** The alert carries only the stable message; the changing wait hint stays outside it so it is not re-announced. */
export function ProblemAlert({
    messageKey,
    waitSeconds,
}: Readonly<{ messageKey: TranslationKey; waitSeconds: number }>) {
    const t = useT();
    return (
        <p>
            <span role="alert">{t(messageKey)}</span>
            {waitSeconds > 0 && <span> {t('problem.retryAfter').replace('{seconds}', String(waitSeconds))}</span>}
        </p>
    );
}
