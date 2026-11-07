import type { components } from '../contract/generated/api';

type ShotResult = components['schemas']['Shot']['result'];

const patterns: Record<ShotResult, number | number[]> = {
    MISS: 18,
    HIT: 38,
    SUNK: [38, 45, 70],
};

export function playHaptics(result: ShotResult): void {
    try {
        (navigator as { vibrate?: (pattern: number | number[]) => boolean }).vibrate?.(patterns[result]);
    } catch {
        // Haptics are optional and must not affect gameplay.
    }
}
