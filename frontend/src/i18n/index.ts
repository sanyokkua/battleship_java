import i18n from 'i18next';
import {initReactI18next} from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import enCommon from './en/common.json';
import enScreens from './en/screens.json';
import enNotifications from './en/notifications.json';
import enErrors from './en/errors.json';
import ukCommon from './uk/common.json';
import ukScreens from './uk/screens.json';
import ukNotifications from './uk/notifications.json';
import ukErrors from './uk/errors.json';

// Module side effect: configures and initializes the shared i18next singleton
// on import. Every screen/widget that needs translations imports the default
// export below rather than constructing its own i18next instance.
i18n
    .use(LanguageDetector)
    .use(initReactI18next)
    .init({
        resources: {
            en: {common: enCommon, screens: enScreens, notifications: enNotifications, errors: enErrors},
            uk: {common: ukCommon, screens: ukScreens, notifications: ukNotifications, errors: ukErrors},
        },
        ns: ['common', 'screens', 'notifications', 'errors'],
        defaultNS: 'common',
        fallbackLng: 'en',
        interpolation: {escapeValue: false},
        detection: {order: ['localStorage', 'navigator'], caches: ['localStorage']},
    });

/**
 * The app-wide, already-initialized i18next instance, wired to `react-i18next`
 * and preloaded with both locales' `common`/`screens`/`notifications`/`errors`
 * namespaces (see `en/` and `uk/` JSON files in this directory). Language is
 * auto-detected (`localStorage`, then browser `navigator`) and persisted back
 * to `localStorage` on change, falling back to `en` when undetected or
 * untranslated. Import this instance directly (e.g. for `i18n.t`,
 * `i18n.changeLanguage`) or rely on `react-i18next`'s hooks, which pick it up
 * automatically once this module has been imported anywhere in the app.
 */
export default i18n;
