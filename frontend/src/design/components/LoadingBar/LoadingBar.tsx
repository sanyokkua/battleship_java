import './LoadingBar.css';

export type LoadingBarProps = {
  label?: string;
};

export function LoadingBar({ label }: LoadingBarProps) {
  return (
    <div className="topbar-progress" role="progressbar" aria-label={label}>
      <i />
    </div>
  );
}
