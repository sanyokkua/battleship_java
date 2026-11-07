import { useEffect, useRef, useState } from 'react';
import { useT } from '../i18n/useT';

export function Qr({ value, label }: Readonly<{ value: string; label: string }>) {
    const t = useT();
    const canvas = useRef<HTMLCanvasElement>(null);
    const [failedValue, setFailedValue] = useState<string>();
    const failed = failedValue === value;
    useEffect(() => {
        let active = true;
        void import('qrcode')
            .then(({ toCanvas }) =>
                canvas.current && active
                    ? toCanvas(canvas.current, value, { errorCorrectionLevel: 'M', margin: 2, width: 256 })
                    : undefined,
            )
            .catch(() => {
                if (active) setFailedValue(value);
            });
        return () => {
            active = false;
        };
    }, [value]);
    return (
        <>
            <canvas ref={canvas} aria-label={label} role="img" hidden={failed} />
            {failed && <p role="alert">{t('game.qrFailure')}</p>}
        </>
    );
}
