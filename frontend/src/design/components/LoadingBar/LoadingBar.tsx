import './LoadingBar.css';

export type LoadingBarProps = {
    /** Accessible label for the progress bar, exposed via `aria-label`. */
    label?: string;
};

/**
 * Thin animated progress indicator shown while a screen/action is loading.
 * Renders a `role="progressbar"` element; the actual animation lives in
 * `LoadingBar.css`.
 */
export function LoadingBar({label}: LoadingBarProps) {
    return (
        <div className="topbar-progress" role="progressbar" aria-label={label}>
            <i/>
        </div>
    );
}
