import {useEffect, useRef, useState} from "react";
import {useTranslation} from "react-i18next";
import {Sheet} from "../../design/components/Sheet/Sheet";
import {Button} from "../../design/components/Button/Button";
import "./QrCodeSheet.css";

export type QrCodeSheetProps = {
    open: boolean;
    url: string;
    onClose: () => void;
};

type QrState = "idle" | "loading" | "ready" | "error";

export function QrCodeSheet({open, url, onClose}: QrCodeSheetProps) {
    if (!open) {
        return null;
    }

    return <OpenQrCodeSheet url={url} onClose={onClose}/>;
}

type OpenQrCodeSheetProps = Omit<QrCodeSheetProps, "open">;

function OpenQrCodeSheet({url, onClose}: OpenQrCodeSheetProps) {
    const {t} = useTranslation("screens");
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const [state, setState] = useState<QrState>("loading");

    useEffect(() => {
        let active = true;
        const canvas = canvasRef.current;

        async function generateQrCode() {
            try {
                const {toCanvas} = await import("qrcode");
                if (!active || !canvas) {
                    return;
                }

                await toCanvas(canvas, url, {
                    width: 300,
                    margin: 2,
                    errorCorrectionLevel: "M",
                });

                if (active) {
                    setState("ready");
                }
            } catch {
                if (active) {
                    setState("error");
                }
            }
        }

        void generateQrCode();

        return () => {
            active = false;
        };
    }, [url]);

    const canvasReady = state === "ready";

    return (
        <Sheet open={true} title={t("screens:qr.title")} onClose={onClose}>
            <div className="qr-sheet-content">
                {state === "loading" && <p className="qr-sheet-status" role="status">{t("screens:qr.loading")}</p>}
                {state === "error" && <p className="qr-sheet-status" role="alert">{t("screens:qr.error")}</p>}
                <canvas
                    ref={canvasRef}
                    className="qr-sheet-canvas"
                    hidden={!canvasReady}
                    role={canvasReady ? "img" : undefined}
                    aria-label={canvasReady ? t("screens:qr.description") : undefined}
                />
                {canvasReady && <p className="qr-sheet-instruction">{t("screens:qr.scan")}</p>}
                <Button type="button" variant="ghost" size="sm" className="qr-sheet-close" onClick={onClose}>
                    {t("screens:qr.close")}
                </Button>
            </div>
        </Sheet>
    );
}
