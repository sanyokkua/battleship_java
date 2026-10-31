import { Dialog } from '../components/Dialog';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { preferenceStore, usePreferences } from '../preferences/preferences';
import { useT } from '../i18n/useT';
import { useState } from 'react';
import { Button } from '../components/Button';
import './SettingsDialog.css';

interface Props {
    open: boolean;
    installAvailable?: boolean;
    onInstall?: () => void;
    canResign?: boolean;
    resignDisabled?: boolean;
    onResign?: () => Promise<void>;
    onClose: () => void;
}

export function SettingsDialog({
    installAvailable = false,
    onInstall,
    open,
    canResign = false,
    resignDisabled = false,
    onResign,
    onClose,
}: Props) {
    const preferences = usePreferences();
    const t = useT();
    const [confirmResign, setConfirmResign] = useState(false);
    const [resignFailure, setResignFailure] = useState(false);
    const update = (value: Parameters<typeof preferenceStore.update>[0]) => {
        preferenceStore.update(value);
    };
    return [
        <Dialog key="settings" open={open} onClose={onClose} titleId="settings-title">
            <div className="dialog-head">
                <h2 id="settings-title">{t('settings.title')}</h2>
                <Button variant="ghost" aria-label={t('app.close')} onClick={onClose}>
                    ×
                </Button>
            </div>
            <div className="dialog-body">
                <p className="settings-build">
                    {t('settings.build')}: {import.meta.env.VITE_BUILD_ID ?? 'local'}
                </p>
                {installAvailable && onInstall && (
                    <Button variant="secondary" onClick={onInstall}>
                        {t('pwa.install')}
                    </Button>
                )}
                <fieldset className="settings-fieldset">
                    <legend>{t('settings.theme')}</legend>
                    <div className="settings-themes">
                        {(['dark', 'light', 'system'] as const).map((theme) => (
                            <Button
                                key={theme}
                                aria-pressed={preferences.theme === theme}
                                variant={preferences.theme === theme ? 'primary' : 'secondary'}
                                onClick={() => {
                                    update({ theme });
                                }}
                            >
                                {t(`settings.${theme}`)}
                            </Button>
                        ))}
                    </div>
                </fieldset>
                <label className="settings-row" aria-label={t('settings.emoji')}>
                    <span>
                        <strong>{t('settings.emoji')}</strong>
                        <small>{t('settings.emojiHelp')}</small>
                    </span>
                    <input
                        type="checkbox"
                        checked={preferences.emoji}
                        onChange={(event) => {
                            update({ emoji: event.currentTarget.checked });
                        }}
                    />
                </label>
                {canResign && onResign && (
                    <Button
                        variant="danger"
                        disabled={resignDisabled}
                        onClick={() => {
                            setConfirmResign(true);
                        }}
                    >
                        {t('leave.resign')}
                    </Button>
                )}
                {resignFailure && <p role="alert">{t('problem.serviceUnavailable')}</p>}
                <label className="settings-language" htmlFor="settings-language">
                    {t('settings.language')}
                    <select
                        id="settings-language"
                        value={preferences.locale}
                        onChange={(event) => {
                            update({ locale: event.currentTarget.value as 'en' | 'uk' });
                        }}
                    >
                        <option value="en">{t('settings.english')}</option>
                        <option value="uk">{t('settings.ukrainian')}</option>
                    </select>
                </label>
                <label className="settings-row" aria-label={t('settings.sound')}>
                    <span>
                        <strong>{t('settings.sound')}</strong>
                        <small>{t('settings.soundHelp')}</small>
                    </span>
                    <input
                        type="checkbox"
                        checked={preferences.sound}
                        onChange={(event) => {
                            update({ sound: event.currentTarget.checked });
                        }}
                    />
                </label>
                <label className="settings-row" aria-label={t('settings.haptics')}>
                    <span>
                        <strong>{t('settings.haptics')}</strong>
                        <small>{t('settings.hapticsHelp')}</small>
                    </span>
                    <input
                        type="checkbox"
                        checked={preferences.haptics}
                        onChange={(event) => {
                            update({ haptics: event.currentTarget.checked });
                        }}
                    />
                </label>
            </div>
            <div className="dialog-actions">
                <Button variant="primary" onClick={onClose}>
                    {t('settings.done')}
                </Button>
            </div>
        </Dialog>,
        <ConfirmDialog
            key="resign"
            open={confirmResign}
            title={t('leave.resignTitle')}
            message={t('leave.resignWarning')}
            confirmLabel={t('leave.resign')}
            confirmDisabled={resignDisabled}
            onClose={() => {
                setConfirmResign(false);
            }}
            onConfirm={() => {
                if (resignDisabled) return;
                setResignFailure(false);
                if (!onResign) return;
                void onResign().then(
                    () => {
                        setConfirmResign(false);
                        onClose();
                    },
                    () => {
                        setConfirmResign(false);
                        setResignFailure(true);
                    },
                );
            }}
        />,
    ];
}
