import { useEffect, useRef } from 'react';

export function Qr({ value, label }: { value: string; label: string }) {
    const canvas = useRef<HTMLCanvasElement>(null);
    useEffect(() => {
        let active = true;
        void import('qrcode').then(({ toCanvas }) => {
            if (active && canvas.current)
                void toCanvas(canvas.current, value, { errorCorrectionLevel: 'M', margin: 2, width: 256 });
        });
        return () => {
            active = false;
        };
    }, [value]);
    return <canvas ref={canvas} aria-label={label} role="img" />;
}
