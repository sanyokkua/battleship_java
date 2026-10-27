export function Meter({ value, label }: { value: number; label: string }) {
    const clamped = Math.max(0, Math.min(1, value));
    return (
        <div className="invitation-meter">
            <label>
                {label}
                <progress max={1} value={clamped} />
            </label>
        </div>
    );
}
