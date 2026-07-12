import './TurnBanner.css';

export type TurnBannerProps = {
  isYourTurn: boolean;
  text: string; // pre-localized/interpolated message from the screen
};

/**
 * Ported from MOCKUP.html's `.turn-banner`/`.turn-banner.yours` (pink vs green
 * theme). `text` is supplied by the caller — this component never calls
 * useTranslation() itself.
 */
export function TurnBanner({ isYourTurn, text }: TurnBannerProps) {
  return (
    <div className={`turn-banner${isYourTurn ? ' yours' : ''}`}>
      <span aria-hidden="true">{isYourTurn ? '🎯' : '⏳'}</span>
      {text}
    </div>
  );
}
