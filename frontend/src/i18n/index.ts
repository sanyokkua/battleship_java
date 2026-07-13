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

export default i18n;
