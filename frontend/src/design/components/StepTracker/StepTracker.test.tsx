import {describe, expect, it} from 'vitest';
import {render, screen} from '@testing-library/react';
import {type Step, StepTracker} from './StepTracker';

const steps: Step[] = [
    {key: 'create', label: 'Create'},
    {key: 'wait', label: 'Waiting'},
    {key: 'prep', label: 'Prepare'},
    {key: 'battle', label: 'Battle'},
];

function stepEl(label: string) {
    return screen.getByText(label).closest('.st') as HTMLElement;
}

describe('StepTracker', () => {
    it('marks steps before currentIndex as done, current as active, rest as upcoming', () => {
        render(<StepTracker steps={steps} currentIndex={1}/>);

        expect(stepEl('Create')).toHaveClass('done');
        expect(stepEl('Waiting')).toHaveClass('active');
        expect(stepEl('Prepare')).not.toHaveClass('done');
        expect(stepEl('Prepare')).not.toHaveClass('active');
        expect(stepEl('Battle')).not.toHaveClass('done');
        expect(stepEl('Battle')).not.toHaveClass('active');
    });

    it('shows a checkmark dot for done steps and a number for others', () => {
        render(<StepTracker steps={steps} currentIndex={2}/>);

        expect(stepEl('Create').querySelector('.dot')).toHaveTextContent('✓');
        expect(stepEl('Waiting').querySelector('.dot')).toHaveTextContent('✓');
        expect(stepEl('Prepare').querySelector('.dot')).toHaveTextContent('3');
        expect(stepEl('Battle').querySelector('.dot')).toHaveTextContent('4');
    });

    it('marks the first step active when currentIndex is 0', () => {
        render(<StepTracker steps={steps} currentIndex={0}/>);

        expect(stepEl('Create')).toHaveClass('active');
        expect(stepEl('Waiting')).not.toHaveClass('done');
    });

    it('marks all steps done when currentIndex is past the last step', () => {
        render(<StepTracker steps={steps} currentIndex={steps.length}/>);

        for (const step of steps) {
            expect(stepEl(step.label)).toHaveClass('done');
        }
    });
});
