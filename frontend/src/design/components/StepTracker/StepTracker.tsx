import './StepTracker.css';

/** One step in a {@link StepTracker}. */
export type Step = {
    /** Stable identifier used as the React list key. */
    key: string;
    /** Display text for the step. */
    label: string;
};

/** Computed state of a step relative to the tracker's current position. */
export type StepStatus = 'done' | 'active' | 'upcoming';

export type StepTrackerProps = {
    /** Ordered list of steps to display. */
    steps: Step[];
    /** Index of the currently active step; steps before it are marked `'done'`, after it `'upcoming'`. */
    currentIndex: number;
};

function statusFor(index: number, currentIndex: number): StepStatus {
    if (index < currentIndex) return 'done';
    if (index === currentIndex) return 'active';
    return 'upcoming';
}

/**
 * Horizontal progress indicator showing a sequence of {@link Step}s, each
 * rendered as a numbered dot (or checkmark once done) plus its label. Step
 * status (`done`/`active`/`upcoming`) is derived from comparing each step's
 * index to {@link StepTrackerProps.currentIndex}.
 */
export function StepTracker({steps, currentIndex}: StepTrackerProps) {
    return (
        <div className="step-track">
            {steps.map((step, index) => {
                const status = statusFor(index, currentIndex);
                const dotContent = status === 'done' ? '✓' : index + 1;

                return (
                    <div key={step.key} className={`st ${status !== 'upcoming' ? status : ''}`.trim()}>
                        <div className="dot">{dotContent}</div>
                        {step.label}
                    </div>
                );
            })}
        </div>
    );
}
