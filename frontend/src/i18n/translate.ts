import { en } from './en';
import { uk } from './uk';

export type Locale = 'en' | 'uk';
export type MessageKey = keyof typeof en;

export function translate(locale: Locale, key: string): string {
    const catalog: Readonly<Record<string, string>> = locale === 'uk' ? uk : en;
    const message = catalog[key];
    if (message !== undefined) return message;
    if (key.startsWith('ruleset.') || key.startsWith('ship.')) return key.slice(key.indexOf('.') + 1);
    return key;
}
