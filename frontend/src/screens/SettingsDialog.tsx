import { Button } from '../components/Button';
import { Dialog } from '../components/Dialog';
import { preferenceStore, usePreferences } from '../preferences/preferences';
import { useT } from '../i18n/useT';
import './SettingsDialog.css';

interface Props {
    open: boolean;
    onClose: () => void;
}

export function SettingsDialog({ open, onClose }: Props) {
    const preferences = usePreferences();
    const t = useT();
    const update = (value: Parameters<typeof preferenceStore.update>[0]) => {
        preferenceStore.update(value);
    };
    return (
        <Dialog open={open} onClose={onClose} titleId="settings-title">
            <div className="dialog-head">
                <h2 id="settings-title">{t('settings.title')}</h2>
                <Button variant="ghost" aria-label={t('app.close')} onClick={onClose}>
                    ×
                </Button>
            </div>
            <div className="dialog-body">
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
                <label className="settings-row">
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
                <label className="settings-row">
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
                <label className="settings-row">
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
        </Dialog>
    );
}
