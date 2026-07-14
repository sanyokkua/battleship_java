import './TurnBanner.css';

export type TurnBannerProps = {
    isYourTurn: boolean;
    text: string; // pre-localized/interpolated message from the screen
};

/**
 * Banner with a pink vs green theme depending on whose turn it is. `text` is
 * supplied by the caller — this component never calls useTranslation() itself.
 */
export function TurnBanner({isYourTurn, text}: TurnBannerProps) {
    return (
        <div className={`turn-banner${isYourTurn ? ' yours' : ''}`}>
            <span aria-hidden="true">{isYourTurn ? '🎯' : '⏳'}</span>
            {text}
        </div>
    );
}
