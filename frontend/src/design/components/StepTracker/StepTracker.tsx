import './StepTracker.css';

export type Step = {
  key: string;
  label: string;
};

export type StepStatus = 'done' | 'active' | 'upcoming';

export type StepTrackerProps = {
  steps: Step[];
  currentIndex: number;
};

function statusFor(index: number, currentIndex: number): StepStatus {
  if (index < currentIndex) return 'done';
  if (index === currentIndex) return 'active';
  return 'upcoming';
}

export function StepTracker({ steps, currentIndex }: StepTrackerProps) {
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
